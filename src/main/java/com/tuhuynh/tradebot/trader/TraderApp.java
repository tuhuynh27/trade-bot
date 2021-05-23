package com.tuhuynh.tradebot.trader;

import java.util.List;

import com.google.gson.Gson;
import com.tuhuynh.tradebot.core.server.Server;
import com.tuhuynh.tradebot.core.server.Server.Context;
import com.tuhuynh.tradebot.core.server.Server.HttpResponse;
import com.tuhuynh.tradebot.factory.AppFactory;
import com.tuhuynh.tradebot.trader.TraderLogger.TraderLog;
import com.tuhuynh.tradebot.trader.TraderSession.TraderConfig;

import lombok.AllArgsConstructor;

public class TraderApp implements Runnable {
    private final Gson gson = AppFactory.getGson();

    @Override
    public void run() {
        // Init set defaultTraderConfig or read from configuration or persis file
        TraderConfig traderConfig = TraderConfig.builder()
                                                .downThreshold(0.45) // 1D == 1%
                                                .upThreshold(0.2)
                                                .denyThreshold(0.155)
                                                .buyPoint(0.2)
                                                .stopLoss(0.25)
                                                .takeProfit(0.85)
                                                .build();
        TraderFactory.setTraderConfig(traderConfig);

        long startTime = System.currentTimeMillis();

        new Thread(new TraderSession("ETHDOWN", 100)).start();
        new Thread(new TraderSession("ETHUP", 100)).start();
        new Thread(new TraderSession("BTCDOWN", 100)).start();
        new Thread(new TraderSession("BTCUP", 100)).start();
        new Thread(new TraderSession("BNBDOWN", 100)).start();
        new Thread(new TraderSession("BNBUP", 100)).start();
        new Thread(new TraderSession("XRPDOWN", 100)).start();
        new Thread(new TraderSession("XRPUP", 100)).start();
        new Thread(new TraderSession("ADADOWN", 100)).start();
        new Thread(new TraderSession("ADAUP", 100)).start();
        new Thread(new TraderSession("DOTUP", 100)).start();
        new Thread(new TraderSession("DOTDOWN", 100)).start();
        new Thread(new TraderSession("BCHDOWN", 100)).start();
        new Thread(new TraderSession("XLMDOWN", 100)).start();

        new Thread(new TraderSession("BTC", 100)).start();
        new Thread(new TraderSession("ETH", 100)).start();
        new Thread(new TraderSession("XRP", 100)).start();
        new Thread(new TraderSession("BNB", 100)).start();
        new Thread(new TraderSession("DOGE", 100)).start();

        new Thread(new TraderSession("XLM", 100)).start();
        new Thread(new TraderSession("ADA", 100)).start();
        new Thread(new TraderSession("BAKE", 100)).start();
        new Thread(new TraderSession("AXS", 100)).start();
        new Thread(new TraderSession("SHIB", 100)).start();
        new Thread(new TraderSession("ONT", 100)).start();
        new Thread(new TraderSession("MATIC", 100)).start();
        new Thread(new TraderSession("ICP", 100)).start();
        new Thread(new TraderSession("DOT", 100)).start();
        new Thread(new TraderSession("SOL", 100)).start();
        new Thread(new TraderSession("LTC", 100)).start();
        new Thread(new TraderSession("VET", 100)).start();
        new Thread(new TraderSession("ETC", 100)).start();
        new Thread(new TraderSession("BCH", 100)).start();
        new Thread(new TraderSession("LINK", 100)).start();
        new Thread(new TraderSession("XVS", 100)).start();
        new Thread(new TraderSession("NEAR", 100)).start();
        new Thread(new TraderSession("WIN", 100)).start();
        new Thread(new TraderSession("EOS", 100)).start();

        double initialBalance = TraderFactory.getDollarBalance();

        Server server = Server.create("localhost", 6789);

        server.use("/trade/config/get", (Context context) -> HttpResponse.builder()
                                                                         .body(TraderFactory.getTraderConfig())
                                                                         .code(200).build());

        server.use("/trade/config/set", (Context context) -> {
            String bodyString = context.getBody();
            TraderConfig t = gson.fromJson(bodyString, TraderConfig.class);
            TraderConfig s = TraderFactory.getTraderConfig();
            s.setDownThreshold(t.getDownThreshold());
            s.setUpThreshold(t.getUpThreshold());
            s.setDenyThreshold(t.getDenyThreshold());
            s.setBuyPoint(t.getBuyPoint());
            s.setStopLoss(t.getStopLoss());
            s.setTakeProfit(t.getTakeProfit());
            return HttpResponse.builder().body(s).code(200).build();
        });

        server.use("/trade/logs/get", (Context context) -> {
            List<TraderLog> logTail = TraderLogger.getNotifyLogs()
                                                  .subList(Math.max(TraderLogger.getNotifyLogs().size() - 50, 0),
                                                           TraderLogger.getNotifyLogs().size());
            return HttpResponse.builder().body(logTail).code(200).build();
        });

        server.use("/trade/logs/rate", (Context context) -> {
            int win = (int) TraderLogger.getNotifyLogs().stream().filter(l -> l.msg.startsWith("Take profit"))
                                          .count();
            int lose = (int) TraderLogger.getNotifyLogs().stream().filter(l -> l.msg.startsWith("Stop loss"))
                                          .count();
            return HttpResponse.builder().body(new Rate(win, lose)).code(200).build();
        });

        server.use("/trade/info/get", (Context context) -> {
            List<TraderLog> notifyLogs = TraderLogger.getNotifyLogs();
            long txCount = notifyLogs.stream()
                                             .filter(l -> l.msg.startsWith("Bought ") || l.msg.startsWith("Sold "))
                                             .count();
            InvestmentData result = new InvestmentData(startTime, initialBalance, TraderFactory.getDollarBalance(),
                                                       txCount, TraderFactory.getProfit());
            return HttpResponse.builder().body(result).code(200).build();
        });

        server.use("/trade/profit-table/get", (Context context) ->
                HttpResponse.builder().body(TraderFactory.getProfitTable()).code(200).build());

        server.start();
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
