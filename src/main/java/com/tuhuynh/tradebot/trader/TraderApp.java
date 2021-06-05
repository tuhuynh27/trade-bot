package com.tuhuynh.tradebot.trader;

import java.util.List;

import com.google.gson.Gson;
import com.tuhuynh.tradebot.factory.AppFactory;
import com.tuhuynh.tradebot.trader.TraderLogger.TraderLog;
import com.tuhuynh.tradebot.trader.TraderSession.TraderConfig;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

public class TraderApp implements Runnable {
    private final Gson gson = AppFactory.getGson();

    @Override
    public void run() {
        // Init set defaultTraderConfig or read from configuration or persis file
        TraderConfig traderConfig = TraderConfig.builder()
                                                .downThreshold(0.12) // 1D == 1%
                                                .upThreshold(0.12)
                                                .denyThreshold(0.2)
                                                .buyPoint(0.225)
                                                .stopLoss(0.25)
                                                .takeProfit(0.8)
                                                .build();
        TraderFactory.setTraderConfig(traderConfig);

        long startTime = System.currentTimeMillis();

        new Thread(new TraderPair("BTC", 1000)).start();
        new Thread(new TraderPair("ETH", 1000)).start();
        new Thread(new TraderPair("ADA", 1000)).start();
        new Thread(new TraderPair("BNB", 1000)).start();
        new Thread(new TraderPair("XRP", 1000)).start();

        double initialBalance = TraderFactory.getDollarBalance();

        HttpServer.create().port(6789).route(routes -> {
            routes.get("/trade/config/get", (req, res) ->
                    res.sendString(Mono.just(gson.toJson(TraderFactory.getTraderConfig()))));

            routes.post("/trade/config/set", (req, res) ->
                    res.sendString(req.receive().asString().map(body -> {
                        TraderConfig t = gson.fromJson(body, TraderConfig.class);
                        TraderConfig s = TraderFactory.getTraderConfig();
                        s.setDownThreshold(t.getDownThreshold());
                        s.setUpThreshold(t.getUpThreshold());
                        s.setDenyThreshold(t.getDenyThreshold());
                        s.setBuyPoint(t.getBuyPoint());
                        s.setStopLoss(t.getStopLoss());
                        s.setTakeProfit(t.getTakeProfit());
                        return gson.toJson(s);
                    }))
            );

            routes.get("/trade/logs/get", (req, res) -> {
                List<TraderLog> logTail = TraderLogger.getNotifyLogs()
                                                      .subList(Math.max(
                                                              TraderLogger.getNotifyLogs().size() - 50, 0),
                                                               TraderLogger.getNotifyLogs().size());
                return res.sendString(Mono.just(gson.toJson(logTail)));
            });

            routes.get("/trade/logs/rate", (req, res) -> {
                int win = (int) TraderLogger.getNotifyLogs().stream()
                                            .filter(l -> l.msg.startsWith("Take profit"))
                                            .count();
                int lose = (int) TraderLogger.getNotifyLogs().stream()
                                             .filter(l -> l.msg.startsWith("Stop loss"))
                                             .count();
                String winRate = gson.toJson(new Rate(win, lose));
                return res.sendString(Mono.just(winRate));
            });

            routes.get("/trade/info/get", (req, res) -> {
                List<TraderLog> notifyLogs = TraderLogger.getNotifyLogs();
                long txCount = notifyLogs.stream()
                                         .filter(l -> l.msg.startsWith("Bought ") || l.msg.startsWith("Sold "))
                                         .count();
                InvestmentData result = new InvestmentData(startTime, initialBalance,
                                                           TraderFactory.getDollarBalance(),
                                                           txCount, TraderFactory.getProfit());
                return res.sendString(Mono.just(gson.toJson(result)));
            });

            routes.get("/trade/profit-table/get", (req, res) ->
                    res.sendString(Mono.just(gson.toJson(TraderFactory.getProfitTable()))));

        }).bindNow();
    }

    @AllArgsConstructor
    static class InvestmentData {
        long startTime;
        double initialBalance;
        double balance;
        long txCount;
        double profit;
    }

    @AllArgsConstructor
    static class Rate {
        int win;
        int lose;
    }
}
