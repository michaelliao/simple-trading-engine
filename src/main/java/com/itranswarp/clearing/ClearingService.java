package com.itranswarp.clearing;

import java.math.BigDecimal;

import com.itranswarp.assets.AssetService;
import com.itranswarp.assets.Transfer;
import com.itranswarp.match.MatchRecord;
import com.itranswarp.match.MatchResult;
import com.itranswarp.order.Order;
import com.itranswarp.order.OrderService;

public class ClearingService {

    final AssetService assetService;
    final OrderService orderService;

    public ClearingService(AssetService assetService, OrderService orderService) {
        this.assetService = assetService;
        this.orderService = orderService;
    }

    public void clearMatchResult(MatchResult result) {
        Order taker = result.takerOrder;
        switch (taker.direction) {
        case BUY -> {
            // 买入时，按Maker的价格成交：
            for (MatchRecord record : result.matchRecords) {
                Order maker = record.makerOrder;
                BigDecimal matched = record.amount;
                if (taker.price.compareTo(maker.price) > 0) {
                    // 实际买入价比报价低，部分金额退回账户:
                    BigDecimal unfreezeQuote = taker.price.subtract(maker.price).multiply(matched);
                    assetService.unfreeze(taker.userId, "FIAT", unfreezeQuote);
                }
                // 买方FIAT转入卖方账户:
                assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, taker.userId, maker.userId, "FIAT", maker.price.multiply(matched));
                // 卖方STOCK转入买方账户:
                assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, maker.userId, taker.userId, "STOCK", matched);
                // 删除完全成交的Maker:
                if (maker.unfilledAmount.signum() == 0) {
                    orderService.removeOrder(maker.sequenceId);
                }
            }
            // 删除完全成交的Taker:
            if (taker.unfilledAmount.signum() == 0) {
                orderService.removeOrder(taker.sequenceId);
            }
        }
        case SELL -> {
            for (MatchRecord record : result.matchRecords) {
                Order maker = record.makerOrder;
                BigDecimal matched = record.amount;
                // 卖方STOCK转入买方账户:
                assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, taker.userId, maker.userId, "STOCK", matched);
                // 买方FIAT转入卖方账户:
                assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, maker.userId, taker.userId, "FIAT", maker.price.multiply(matched));
                // 删除完全成交的Maker:
                if (maker.unfilledAmount.signum() == 0) {
                    orderService.removeOrder(maker.sequenceId);
                }
            }
            // 删除完全成交的Taker:
            if (taker.unfilledAmount.signum() == 0) {
                orderService.removeOrder(taker.sequenceId);
            }
        }
        default -> throw new IllegalArgumentException("Invalid direction.");
        }
    }

    public void clearCancelOrder(Order order) {
        switch (order.direction) {
        case BUY -> {
            // 解冻FIAT:
            assetService.unfreeze(order.userId, "FIAT", order.price.multiply(order.unfilledAmount));
        }
        case SELL -> {
            // 解冻STOCK:
            assetService.unfreeze(order.userId, "STOCK", order.unfilledAmount);
        }
        default -> throw new IllegalArgumentException("Invalid direction.");
        }
        // 从OrderService中删除订单:
        orderService.removeOrder(order.sequenceId);
    }
}
