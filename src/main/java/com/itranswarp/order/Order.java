package com.itranswarp.order;

import java.math.BigDecimal;

public class Order {

    public final Long sequenceId;
    public final Long userId;
    public final Direction direction;
    public final BigDecimal price;
    public final BigDecimal amount;

    public OrderStatus status;
    public BigDecimal unfilledAmount;

    public Order(Long sequenceId, Long userId, Direction direction, BigDecimal price, BigDecimal amount) {
        this.sequenceId = sequenceId;
        this.userId = userId;
        this.direction = direction;
        this.price = price;
        this.amount = amount;

        this.status = OrderStatus.PENDING;
        this.unfilledAmount = amount;
    }

    @Override
    public String toString() {
        return String.format("%04.2f %02.2f [sequenceId=%s, userId=%s, direction=%s, amount=%s, status=%s]", price, unfilledAmount, sequenceId, userId,
                direction, amount, status);
    }

}
