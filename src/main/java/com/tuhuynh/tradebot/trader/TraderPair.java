package com.tuhuynh.tradebot.trader;

public class TraderPair implements Runnable {
    private final TraderSession up;
    private final TraderSession down;

    public TraderPair(String currency, double balance) {
        up = new TraderSession(currency + "UP",
                               balance / 2,
                               () -> losing(TrendType.UP));
        down = new TraderSession(currency + "DOWN",
                                 balance / 2,
                                 () -> losing(TrendType.DOWN));
        up.resumeTrade();
        down.stopTrade();
    }

    @Override
    public void run() {
        new Thread(up).start();
        new Thread(down).start();
    }

    public void losing(TrendType trendType) {
        if (trendType == TrendType.UP) {
            up.stopTrade();
            down.resumeTrade();
        } else {
            down.stopTrade();
            up.resumeTrade();
        }
    }

    enum TrendType {
        UP, DOWN
    }
}
