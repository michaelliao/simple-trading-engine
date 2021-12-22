package com.itranswarp.order;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.itranswarp.assets.AssetService;

public class OrderService {

    final AssetService assetService;

    // 全局唯一递增序列号：
    private long sequenceId = 0;

    // 跟踪所有活动订单:
    public ConcurrentMap<Long, Order> activeOrders = new ConcurrentHashMap<>();

    public OrderService(AssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * 创建订单
     */
    public Order createOrder(Long userId, Direction direction, BigDecimal price, BigDecimal amount) {
        switch (direction) {
        case BUY -> {
            // 买入，需冻结法币：
            if (!assetService.tryFreeze(userId, "FIAT", price.multiply(amount))) {
                throw new RuntimeException("No enough FIAT currency.");
            }
        }
        case SELL -> {
            // 卖出，需冻结证券：
            if (!assetService.tryFreeze(userId, "STOCK", amount)) {
                throw new RuntimeException("No enough stock.");
            }
        }
        default -> throw new IllegalArgumentException("Invalid direction.");
        }
        sequenceId++;
        Order order = new Order(sequenceId, userId, direction, price, amount);
        this.activeOrders.put(order.sequenceId, order);
        return order;
    }

    public Order getOrder(Long sequenceId) {
        return this.activeOrders.get(sequenceId);
    }

    // 删除活动订单:
    public void removeOrder(Long sequenceId) {
        Order removed = this.activeOrders.remove(sequenceId);
        if (removed == null) {
            throw new IllegalArgumentException("Order not found by sequenceId: " + sequenceId);
        }
    }
}
