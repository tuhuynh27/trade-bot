package com.tuhuynh.tradebot.trader;

public class TraderApp implements Runnable {
    @Override
    public void run() {
        new Thread(new TraderSession("ADA")).start();
    }
}
