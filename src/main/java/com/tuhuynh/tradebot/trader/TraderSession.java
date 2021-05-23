package com.tuhuynh.tradebot.trader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Deque;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.gson.Gson;
import com.tuhuynh.tradebot.entities.binance.AggTradeStreamMsg;
import com.tuhuynh.tradebot.factory.AppFactory;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraderSession implements Runnable {
    private final TraderLogger traderLogger;
    private final Timer timer = new Timer();
    private final Gson gson = AppFactory.getGson();

    private WebSocket ws = null;

    private final String currency;

    private double price = 0.0F;
    private final Deque<Double> prices = new LinkedBlockingDeque<>();
    private double diffs = 0.0F;

    // Trade Vars
    private boolean isTradeTime = false;
    private boolean isHolding = false;
    private final double initialDollarBalance;
    private double dollarBalance;
    private double coinBalance = 0;

    // Risk/Leverage Vars
    private double leveragePoint = 1;
    private int stopLossContinue = 0;

    // Configs
    private final TraderConfig traderConfig = TraderFactory.getTraderConfig();

    public TraderSession(String currency, double balance) {
        this.traderLogger = new TraderLogger(currency);

        this.currency = currency;
        this.dollarBalance = balance;
        this.initialDollarBalance = balance;
        
        TraderFactory.modifyDollarBalance(balance);
    }

    @Override
    public void run() {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                traderLogger.logMsg("Connected to " + currency + " stream, started trading");
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                AggTradeStreamMsg aggTradeStreamMsg = gson.fromJson(data.toString(), AggTradeStreamMsg.class);
                price = Double.parseDouble(aggTradeStreamMsg.data.p);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                traderLogger.logMsg("Error on stream of " + currency + ", errMessage: " + error.getMessage());
                WebSocket.Listener.super.onError(webSocket, error);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                traderLogger.logMsg("Closed on stream of " + currency + ", reason: " + reason);
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }
        };

        String connectStr = currency.toLowerCase() + "usdt@aggTrade";
        ws = HttpClient
                .newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("wss://stream.binance.com:9443/stream?streams=" + connectStr),
                            listener)
                .join();

        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                prices.add(price);
                if (prices.size() > 10) {
                    prices.removeFirst();
                }
            }
        }, 0, 100);

        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (prices.size() < 10) {
                    return;
                }

                try {
                    double diff = ((prices.getLast() / prices.getFirst()) - 1);
                    if (Double.isInfinite(diff)) {
                        return;
                    }
                    diffs += diff;
                } catch (RuntimeException ignored) {
                }
            }
        }, 0, 100);

        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (prices.size() < 10) {
                    return;
                }

                if (stopLossContinue >= 3) {
                    // Risk
                    return;
                }

                if (isHolding) {
                    // If up 5%
                    // Take profit
                    if (diffs > (traderConfig.getTakeProfit() / 10)) {
                        String msg = "Take profit activated for " + currency;
                        traderLogger.logMsg(msg);

                        sellAll(price);
                        diffs = 0;
                        stopLossContinue = 0;
                    }

                    // If down 1%
                    // Stop loss
                    if (diffs < -(traderConfig.getStopLoss() * leveragePoint / 10)) {
                        String msg = "Stop loss activated for " + currency + ", it loss " + (stopLossContinue + 1) + " times in a row";
                        traderLogger.logMsg(msg);

                        stopLossContinue += 1;
                        sellAll(price);
                        diffs = 0;
                    }
                } else {
                    if (isTradeTime) {
                        // If up 2%
                        if (diffs > ((traderConfig.getBuyPoint() * leveragePoint) / 10)) {
                            buyAll(price);
                            isTradeTime = false;
                            diffs = 0;
                        }

                        // If still down 2%
                        if (diffs < -(traderConfig.getDenyThreshold() * leveragePoint / 10)) {
                            isTradeTime = false;
                            diffs = 0;

                            String msg = currency + " continue to down -" + traderConfig.getDenyThreshold() * leveragePoint
                                         + "%, close trading time";
                            traderLogger.logMsg(msg);
                        }
                    } else {
                        // If down 5%
                        if (diffs < -(traderConfig.getDownThreshold() * leveragePoint / 10)) {
                            isTradeTime = true;
                            diffs = 0;

                            String msg = currency + " has down -" + traderConfig.getDownThreshold() * leveragePoint + "%, open trading time";
                            traderLogger.logMsg(msg);
                        }

                        // If up 5%
                        if (diffs > (traderConfig.getUpThreshold() * leveragePoint / 10)) {
                            isTradeTime = true;
                            diffs = 0;

                            String msg = currency + " has up +" + traderConfig.getUpThreshold() * leveragePoint + "%, open trading time";
                            traderLogger.logMsg(msg);
                        }
                    }
                }
            }
        }, 0, 100);
    }

    public void buyAll(double price) {
        TraderFactory.modifyDollarBalance(-dollarBalance);
        coinBalance = (dollarBalance / price) - (dollarBalance / price) * 0.001;
        dollarBalance = 0;
        isHolding = true;
        String msg = "Bought " + coinBalance + " " + currency + " at price " + price;
        traderLogger.logMsg(msg);
    }

    public void sellAll(double price) {
        double numOfSold = coinBalance;
        dollarBalance = (coinBalance * price) - (coinBalance * price) * 0.001;
        TraderFactory.modifyDollarBalance(dollarBalance);
        coinBalance = 0;
        isHolding = false;
        String msg = "Sold " + numOfSold + " " + currency + " at price " + price + ", balance is " + dollarBalance + "USDT";
        traderLogger.logMsg(msg);

        // Notice profit
        double profit = dollarBalance - initialDollarBalance;
        double profitPercentage = (profit / initialDollarBalance) * 100;
        TraderFactory.setProfit(currency, profit);
        traderLogger.logMsg("Profit of " + currency +" for now: " + profit + "USDT" + " (" + (profitPercentage) + "%)");

        // Check profit threshold
        leveragePoint = 1;
        if (profitPercentage > 1) {
            leveragePoint = 0.75;
        }
        if (profitPercentage > 2.5) {
            leveragePoint = 0.5;
        }
        if (profitPercentage > 5) {
            leveragePoint = 0.3;
        }

        if (profitPercentage < -1) {
            leveragePoint = 1.5;
        }
        if (profitPercentage < -2.5) {
            leveragePoint = 2;
        }
        if (profitPercentage < -5) {
            leveragePoint = 2.5;
        }

        if (leveragePoint != 1) {
            traderLogger.logMsg("Leverage point for " + currency + " has just been adjusted to " + leveragePoint);
        }

        // Check risk
        if (stopLossContinue >= 3 || profitPercentage < -7.5) {
            stopLossContinue = 3;
            traderLogger.logMsg(currency + " has lost 3 times in a row, stopped trading for 2 minutes");
            // Too risk to continue, pause
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    stopLossContinue -= 1;
                    traderLogger.logMsg("Resume trading for " + currency);
                }
            }, 120_000); // 2 minutes
        }
    }

    public void stop() {
        ws.sendClose(0, "None");
        ws.abort();
        timer.cancel();
        timer.purge();
    }

    @Builder
    @Getter
    @Setter
    public static class TraderConfig {
        private double downThreshold;
        private double upThreshold;
        private double denyThreshold;
        private double buyPoint;
        private double stopLoss;
        private double takeProfit;
    }
}
