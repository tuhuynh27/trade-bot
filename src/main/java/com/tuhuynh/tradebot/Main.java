package com.tuhuynh.tradebot;

import com.tuhuynh.tradebot.app.WatcherApp;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        new Thread(new WatcherApp()).start();
        Thread.currentThread().join();
    }
}
