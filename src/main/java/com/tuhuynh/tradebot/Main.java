package com.tuhuynh.tradebot;

import com.tuhuynh.tradebot.trader.TraderApp;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // new Thread(new WatcherApp()).start();
        new Thread(new TraderApp()).start();
        Thread.currentThread().join();
    }
}
