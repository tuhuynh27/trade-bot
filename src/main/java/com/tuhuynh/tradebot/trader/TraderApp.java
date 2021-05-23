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
                .dipDownThreshold(0.4) // 1D == 1%
                .dipUpThreshold(0.4)
                .denyDipDown(0.25)
                .startToBuy(0.35)
                .stopLoss(0.25)
                .takeProfit(1.5)
                .build();
        new Thread(new TraderSession("ADA", 1000, tradeConfig)).start();
        new Thread(new TraderSession("BTC", 1000, tradeConfig)).start();
        new Thread(new TraderSession("ETH", 1000, tradeConfig)).start();
        new Thread(new TraderSession("DOGE", 1000, tradeConfig)).start();
        new Thread(new TraderSession("ICP", 1000, tradeConfig)).start();

        double initialBalance = TraderFactory.getDollarBalance();

        Server server = Server.create("localhost", 6789);
        server.use("/trade/config/get", (Context context) -> HttpResponse.builder().body(tradeConfig).code(200).build());
        server.use("/trade/logs/get", (Context context) -> HttpResponse.builder().body(LINENotify.getNotifyLogs()).code(200).build());
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
}
