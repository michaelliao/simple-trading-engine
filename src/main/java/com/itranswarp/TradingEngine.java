package com.itranswarp;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

import com.itranswarp.assets.Asset;
import com.itranswarp.assets.AssetService;
import com.itranswarp.assets.Transfer;
import com.itranswarp.assets.Users;
import com.itranswarp.clearing.ClearingService;
import com.itranswarp.match.MatchEngine;
import com.itranswarp.match.MatchResult;
import com.itranswarp.order.Direction;
import com.itranswarp.order.Order;
import com.itranswarp.order.OrderService;

public class TradingEngine {

    final AssetService assetService;
    final OrderService orderService;
    final MatchEngine matchEngine;
    final ClearingService clearingService;

    public TradingEngine() {
        this.assetService = new AssetService();
        this.orderService = new OrderService(this.assetService);
        this.matchEngine = new MatchEngine();
        this.clearingService = new ClearingService(this.assetService, this.orderService);
    }

    public void deposit(Long userId, String assetId, BigDecimal amount) {
        if (userId == null || userId.longValue() < Users.TRADER) {
            throw new IllegalArgumentException("Invalid user id.");
        }
        if (!"FIAT".equals(assetId) && !"STOCK".equals(assetId)) {
            throw new IllegalArgumentException("Invalid asset id.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid amount.");
        }
        boolean ok = this.assetService.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, Users.DEBT, userId, assetId, amount, false);
        if (!ok) {
            throw new RuntimeException("deposit transfer failed.");
        }
    }

    /**
     * 创建订单
     */
    public Order createOrder(Long userId, Direction direction, BigDecimal price, BigDecimal amount) {
        if (userId == null || userId.longValue() < Users.TRADER) {
            throw new IllegalArgumentException("Invalid user id.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Invalid direction.");
        }
        if (price == null || price.signum() <= 0 || price.scale() > 2) {
            throw new IllegalArgumentException("Invalid price.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid amount.");
        }
        Order order = this.orderService.createOrder(userId, direction, price, amount);
        MatchResult result = this.matchEngine.processOrder(order);
        this.clearingService.clearMatchResult(result);
        return order;
    }

    /**
     * 撤销订单
     */
    public Order cancelOrder(Long userId, Long sequenceId) {
        Order order = this.orderService.getOrder(sequenceId);
        // 未找到活动订单或订单不属于该用户:
        if (order == null || order.userId.longValue() != userId.longValue()) {
            throw new IllegalArgumentException("Order not found by sequenceId: " + sequenceId);
        }
        this.matchEngine.cancel(order);
        this.clearingService.clearCancelOrder(order);
        return order;
    }

    public void debug() {
        System.out.println("========== trading engine ==========");
        this.assetService.debug();
        this.matchEngine.debug();
        System.out.println("========== // trading engine ==========");
    }

    public void validate() {
        validateAssets();
        validateOrders();
        validateMatchEngine();
    }

    void validateAssets() {
        // 验证系统资产完整性:
        BigDecimal totalFiat = BigDecimal.ZERO;
        BigDecimal totalStock = BigDecimal.ZERO;
        for (Entry<Long, ConcurrentMap<String, Asset>> userEntry : this.assetService.userAssets.entrySet()) {
            Long userId = userEntry.getKey();
            ConcurrentMap<String, Asset> assets = userEntry.getValue();
            for (Entry<String, Asset> entry : assets.entrySet()) {
                String assetId = entry.getKey();
                Asset asset = entry.getValue();
                if (userId.longValue() >= Users.TRADER) {
                    // 交易用户的available/frozen不允许为负数:
                    require(asset.getAvailable().signum() >= 0, "Trader has negative available: " + asset);
                    require(asset.getFrozen().signum() >= 0, "Trader has negative frozen: " + asset);
                }
                if (userId.longValue() == Users.DEBT) {
                    // 系统负债账户available不允许为正:
                    require(asset.getAvailable().signum() <= 0, "Debt has positive available: " + asset);
                    // 系统负债账户frozen必须为0:
                    require(asset.getFrozen().signum() == 0, "Debt has non-zero frozen: " + asset);
                }
                switch (assetId) {
                case "FIAT" -> {
                    totalFiat = totalFiat.add(asset.getTotal());
                }
                case "STOCK" -> {
                    totalStock = totalStock.add(asset.getTotal());
                }
                default -> throw new RuntimeException("Unexpected asset id: " + assetId);
                }
            }
        }
        // 各类别资产总额为0:
        require(totalFiat.signum() == 0, "Non zero fiat balance: " + totalFiat);
        require(totalStock.signum() == 0, "Non zero stock balance: " + totalStock);
    }

    void validateOrders() {
        // 验证订单:
        Map<Long, Map<String, BigDecimal>> userOrderFrozen = new HashMap<>();
        for (Entry<Long, Order> entry : this.orderService.activeOrders.entrySet()) {
            Order order = entry.getValue();
            require(order.unfilledAmount.signum() > 0, "Active order must have positive unfilled amount: " + order);
            switch (order.direction) {
            case BUY -> {
                // 订单必须在MatchEngine中:
                require(this.matchEngine.buyBook.exist(order), "order not found in buy book: " + order);
                // 累计冻结的FIAT:
                userOrderFrozen.putIfAbsent(order.userId, new HashMap<>());
                Map<String, BigDecimal> frozenAssets = userOrderFrozen.get(order.userId);
                frozenAssets.putIfAbsent("FIAT", BigDecimal.ZERO);
                BigDecimal frozen = frozenAssets.get("FIAT");
                frozenAssets.put("FIAT", frozen.add(order.price.multiply(order.unfilledAmount)));
            }
            case SELL -> {
                // 订单必须在MatchEngine中:
                require(this.matchEngine.sellBook.exist(order), "order not found in sell book: " + order);
                // 累计冻结的STOCK:
                userOrderFrozen.putIfAbsent(order.userId, new HashMap<>());
                Map<String, BigDecimal> frozenAssets = userOrderFrozen.get(order.userId);
                frozenAssets.putIfAbsent("STOCK", BigDecimal.ZERO);
                BigDecimal frozen = frozenAssets.get("STOCK");
                frozenAssets.put("STOCK", frozen.add(order.unfilledAmount));
            }
            default -> throw new RuntimeException("Unexpected direction.");
            }
        }
        // 订单冻结的累计金额必须和Asset冻结一致:
        for (Entry<Long, ConcurrentMap<String, Asset>> userEntry : this.assetService.userAssets.entrySet()) {
            Long userId = userEntry.getKey();
            ConcurrentMap<String, Asset> assets = userEntry.getValue();
            for (Entry<String, Asset> entry : assets.entrySet()) {
                String assetId = entry.getKey();
                Asset asset = entry.getValue();
                if (asset.getFrozen().signum() > 0) {
                    Map<String, BigDecimal> orderFrozen = userOrderFrozen.get(userId);
                    require(orderFrozen != null, "No order frozen found for user: " + userId + ", asset: " + asset);
                    BigDecimal frozen = orderFrozen.get(assetId);
                    require(frozen != null, "No order frozen found for asset: " + asset);
                    require(frozen.compareTo(asset.getFrozen()) == 0, "Order frozen " + frozen + " is not equals to asset frozen: " + asset);
                    // 从userOrderFrozen中删除已验证的Asset数据:
                    orderFrozen.remove(assetId);
                }
            }
        }
        // userOrderFrozen不存在未验证的Asset数据:
        for (Entry<Long, Map<String, BigDecimal>> userEntry : userOrderFrozen.entrySet()) {
            Long userId = userEntry.getKey();
            Map<String, BigDecimal> frozenAssets = userEntry.getValue();
            require(frozenAssets.isEmpty(), "User " + userId + " has unexpected frozen for order: " + frozenAssets);
        }
    }

    void validateMatchEngine() {
        // OrderBook的Order必须在ActiveOrders中:
        Map<Long, Order> copyOfActiveOrders = new HashMap<>(this.orderService.activeOrders);
        for (Order order : this.matchEngine.buyBook.book.values()) {
            require(copyOfActiveOrders.remove(order.sequenceId) == order, "Order in buy book is not in active orders: " + order);
        }
        for (Order order : this.matchEngine.sellBook.book.values()) {
            require(copyOfActiveOrders.remove(order.sequenceId) == order, "Order in sell book is not in active orders: " + order);
        }
        // activeOrders的所有Order必须在Order Book中:
        require(copyOfActiveOrders.isEmpty(), "Not all active orders are in order book.");
    }

    void require(boolean condition, String errorMessage) {
        if (!condition) {
            throw new RuntimeException(errorMessage);
        }
    }
}
