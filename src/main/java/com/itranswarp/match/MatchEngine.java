package com.itranswarp.match;

import java.math.BigDecimal;

import com.itranswarp.order.Direction;
import com.itranswarp.order.Order;
import com.itranswarp.order.OrderStatus;

public class MatchEngine {

    public final OrderBook buyBook = new OrderBook(Direction.BUY);
    public final OrderBook sellBook = new OrderBook(Direction.SELL);
    public BigDecimal marketPrice = BigDecimal.ZERO; // 最新市场价

    public MatchResult processOrder(Order order) {
        return switch (order.direction) {
        case BUY -> processOrder(order, this.sellBook, this.buyBook);
        case SELL -> processOrder(order, this.buyBook, this.sellBook);
        default -> throw new IllegalArgumentException("Invalid direction.");
        };
    }

    /**
     * @param takerOrder  输入订单
     * @param makerBook   尝试匹配成交的OrderBook
     * @param anotherBook 未能完全成交后挂单的OrderBook
     * @return 成交结果
     */
    MatchResult processOrder(Order takerOrder, OrderBook makerBook, OrderBook anotherBook) {
        MatchResult matchResult = new MatchResult(takerOrder);
        for (;;) {
            Order makerOrder = makerBook.getFirst();
            if (makerOrder == null) {
                // 对手盘不存在:
                break;
            }
            if (takerOrder.direction == Direction.BUY && takerOrder.price.compareTo(makerOrder.price) < 0) {
                // 买入订单价格比卖盘第一档价格低:
                break;
            } else if (takerOrder.direction == Direction.SELL && takerOrder.price.compareTo(makerOrder.price) > 0) {
                // 卖出订单价格比卖盘第一档价格高:
                break;
            }
            // 以Maker价格成交:
            this.marketPrice = makerOrder.price;
            // 待成交数量为两者较小值:
            BigDecimal matchedAmount = takerOrder.unfilledAmount.min(makerOrder.unfilledAmount);
            // 成交记录:
            matchResult.add(makerOrder.price, matchedAmount, makerOrder);
            // 更新成交后的订单数量:
            takerOrder.unfilledAmount = takerOrder.unfilledAmount.subtract(matchedAmount);
            makerOrder.unfilledAmount = makerOrder.unfilledAmount.subtract(matchedAmount);
            // 对手盘完全成交后，从订单簿中删除:
            if (makerOrder.unfilledAmount.signum() == 0) {
                makerOrder.status = OrderStatus.FULLY_FILLED;
                makerBook.remove(makerOrder);
            } else {
                // 对手盘部分成交:
                makerOrder.status = OrderStatus.PARCIAL_FILLED;
            }
            // Taker订单完全成交后，退出循环:
            if (takerOrder.unfilledAmount.signum() == 0) {
                takerOrder.status = OrderStatus.FULLY_FILLED;
                break;
            }
        }
        // Taker订单未完全成交时，放入订单簿:
        if (takerOrder.unfilledAmount.signum() > 0) {
            anotherBook.add(takerOrder);
            if (takerOrder.unfilledAmount.compareTo(takerOrder.amount) < 0) {
                // 有部分成交:
                takerOrder.status = OrderStatus.PARCIAL_FILLED;
            }
        }
        return matchResult;
    }

    public void cancel(Order order) {
        OrderBook book = order.direction == Direction.BUY ? this.buyBook : this.sellBook;
        if (!book.remove(order)) {
            throw new IllegalArgumentException("Order not found in order book.");
        }
        order.status = OrderStatus.CANCELLED;
    }

    public void debug() {
        System.out.println("---------- match engine ----------");
        System.out.println(this.sellBook);
        System.out.println("----------");
        System.out.println(this.marketPrice);
        System.out.println("----------");
        System.out.println(this.buyBook);
        System.out.println("---------- // match engine ----------");
    }
}
