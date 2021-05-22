package com.tuhuynh.tradebot;

import com.tuhuynh.tradebot.session.WatchSession;

public class App {
    public static void main(String[] args) throws InterruptedException {
        new Thread(new WatchSession("ADA", 0.1F)).start();
        new Thread(new WatchSession("ETH", 0.1F)).start();
        Thread.currentThread().join();
    }
}
