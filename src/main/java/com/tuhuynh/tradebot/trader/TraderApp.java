package com.tuhuynh.tradebot.trader;

import java.util.List;
import java.util.stream.Collectors;

import com.tuhuynh.tradebot.core.server.Server;
import com.tuhuynh.tradebot.core.server.Server.Context;
import com.tuhuynh.tradebot.core.server.Server.HttpResponse;
import com.tuhuynh.tradebot.linebot.LINENotify;
import com.tuhuynh.tradebot.trader.TraderSession.TradeConfig;

import lombok.AllArgsConstructor;

public class TraderApp implements Runnable {
    @Override
    public void run() {
        TradeConfig tradeConfig = TradeConfig.builder()
                .dipDownThreshold(0.5) // 1D == 1%
                .dipUpThreshold(0.25)
                .denyDipDown(0.2)
                .startToBuy(0.25)
                .stopLoss(0.25)
                .takeProfit(1)
                .build();
        new Thread(new TraderSession("ETHUP", 1000, tradeConfig)).start();
        new Thread(new TraderSession("BTC", 1000, tradeConfig)).start();
        new Thread(new TraderSession("DOGE", 1000, tradeConfig)).start();
        new Thread(new TraderSession("ADA", 1000, tradeConfig)).start();
        new Thread(new TraderSession("BNBUP", 1000, tradeConfig)).start();

        double initialBalance = TraderFactory.getDollarBalance();

        Server server = Server.create("localhost", 6789);
        server.use("/trade/config/get", (Context context) -> HttpResponse.builder().body(tradeConfig).code(200).build());
        server.use("/trade/logs/get", (Context context) -> {
            List<String> logTail = LINENotify.getNotifyLogs()
                                             .subList(Math.max(LINENotify.getNotifyLogs().size() - 50, 0),
                                                      LINENotify.getNotifyLogs().size());
            return HttpResponse.builder().body(logTail).code(200).build();
        });
        server.use("/trade/logs/rate", (Context context) -> {
            int win = (int) LINENotify.getNotifyLogs().stream().filter(l -> l.startsWith("Take profit"))
                                          .count();
            int lose = (int) LINENotify.getNotifyLogs().stream().filter(l -> l.startsWith("Stop loss"))
                                          .count();
            return HttpResponse.builder().body(new Rate(win, lose)).code(200).build();
        });
        server.use("/trade/info/get", (Context context) -> {
            List<String> notifyLogs = LINENotify.getNotifyLogs();
            List<String> txCount = notifyLogs.stream()
                                             .filter(l -> l.startsWith("Bought ") || l.startsWith("Sold "))
                                             .collect(Collectors.toList());
            InvestmentData result = new InvestmentData(initialBalance, TraderFactory.getDollarBalance(),
                                                       txCount.size(), TraderFactory.getProfit());
            return HttpResponse.builder().body(result).code(200).build();
        });
        server.use("/trade/profile-table/get", (Context context) ->
                HttpResponse.builder().body(TraderFactory.getProfitTable()).code(200).build());
        server.start();
    }

    @AllArgsConstructor
    static class InvestmentData {
        double initialBalance;
        double balance;
        int txCount;
        double profit;
    }

    @AllArgsConstructor
    static class Rate {
        int win;
        int lose;
    }
}
