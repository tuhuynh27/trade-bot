package com.tuhuynh.tradebot.trader;

import com.tuhuynh.tradebot.trader.TraderSession.TradeConfig;

public class TraderApp implements Runnable {
    @Override
    public void run() {
        TradeConfig tradeConfig = TradeConfig.builder()
                .dipDownThreshold(3) // 1D == 1%
                .dipUpThreshold(2.5)
                .denyDipDown(1)
                .startToBuy(1)
                .stopLoss(0.5)
                .takeProfit(3.5)
                .build();
        new Thread(new TraderSession("ADA", 1000, tradeConfig)).start();
        new Thread(new TraderSession("BTC", 1000, tradeConfig)).start();
        new Thread(new TraderSession("ETH", 1000, tradeConfig)).start();
        new Thread(new TraderSession("DOGE", 1000, tradeConfig)).start();
    }
}
