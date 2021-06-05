package com.tuhuynh.tradebot.trader;

import java.util.List;

import com.google.common.collect.Lists;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraderLogger {
    @Getter
    private static final List<TraderLog> notifyLogs = Lists.newArrayList();

    private final String currency;

    public TraderLogger(String currency) {
        this.currency = currency;
    }

    public void logMsg(String msg) {
        TraderLog traderLog = new TraderLog(System.currentTimeMillis(), currency, msg);
        notifyLogs.add(traderLog);
        log.info("Trade log: " + msg);
    }

    @AllArgsConstructor
    public static class TraderLog {
        long time;
        String currency;
        String msg;
    }
}
