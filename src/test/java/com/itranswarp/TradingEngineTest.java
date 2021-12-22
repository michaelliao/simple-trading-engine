package com.itranswarp;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.itranswarp.assets.Users;
import com.itranswarp.order.Direction;

public class TradingEngineTest {

    static final Long USER_A = Users.TRADER;
    static final Long USER_B = Users.TRADER + 1;
    static final Long USER_C = Users.TRADER + 2;
    static final Long USER_D = Users.TRADER + 3;

    @Test
    public void testTradingEngine() {
        TradingEngine engine = new TradingEngine();
        engine.deposit(USER_A, "FIAT", bd("58000"));
        engine.deposit(USER_B, "FIAT", bd("126700"));
        engine.deposit(USER_C, "STOCK", bd("5.5"));
        engine.deposit(USER_D, "STOCK", bd("8.6"));

        engine.debug();
        engine.validate();

        engine.createOrder(USER_A, Direction.BUY, bd("2207.33"), bd("1.2"));
        engine.createOrder(USER_C, Direction.SELL, bd("2215.6"), bd("0.8"));
        engine.createOrder(USER_C, Direction.SELL, bd("2221.1"), bd("0.3"));

        engine.debug();
        engine.validate();

        engine.createOrder(USER_D, Direction.SELL, bd("2206"), bd("0.3"));

        engine.debug();
        engine.validate();

        engine.createOrder(USER_B, Direction.BUY, bd("2219.6"), bd("2.4"));

        engine.debug();
        engine.validate();

        engine.cancelOrder(USER_A, 1L);

        engine.debug();
        engine.validate();
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
