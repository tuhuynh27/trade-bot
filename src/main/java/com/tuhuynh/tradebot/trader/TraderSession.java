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
import com.tuhuynh.tradebot.linebot.LINENotify;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraderSession implements Runnable {
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
    private double dollarBalance = 1000;
    private double coinBalance = 0;

    // Metrics
    private final double dipDownThreshold = 0.1D;
    private final double dipUpThreshold = 0.1D;
    private final double denyDipDown = 0.1D;
    private final double startToBuy = 0.05D;
    private final double stopLoss = 0.1D;
    private final double takeProfit = 0.5D;

    public TraderSession(String currency) {
        this.currency = currency;
    }

    @Override
    public void run() {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                log.info("Connected to " + currency + " stream, started trading");
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
                log.error("onError: " + webSocket.toString());
                WebSocket.Listener.super.onError(webSocket, error);
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
        }, 0, 1000);

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
        }, 0, 1000);

        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (prices.size() < 10) {
                    return;
                }

                if (isHolding) {
                    // If up 5%
                    if (diffs > (takeProfit / 10)) {
                        sellAll(price);
                        diffs = 0;

                        String msg = "Take profit activated for " + currency;
                        LINENotify.sendNotify(msg);
                    }

                    // If down 1%
                    if (diffs < -(stopLoss / 10)) {
                        sellAll(price);
                        diffs = 0;

                        String msg = "Stop loss activated for " + currency;
                        LINENotify.sendNotify(msg);
                    }

                } else {
                    if (isTradeTime) {
                        // If up 2%
                        if (diffs > (startToBuy / 10)) {
                            buyAll(price);
                            isTradeTime = false;
                            diffs = 0;
                        }

                        // If still down 2%
                        if (diffs < -(denyDipDown / 10)) {
                            isTradeTime = false;
                            diffs = 0;

                            String msg = currency + " has still down more -" + denyDipDown
                                         + "%, close trading time";
                            LINENotify.sendNotify(msg);
                        }
                    } else {
                        // If down 5%
                        if (diffs < -(dipDownThreshold / 10)) {
                            isTradeTime = true;
                            diffs = 0;

                            String msg = currency + " has down -" + dipDownThreshold + "%, it's trading time";
                            LINENotify.sendNotify(msg);
                        }

                        // If up 5%
                        if (diffs > (dipUpThreshold / 10)) {
                            isTradeTime = true;
                            diffs = 0;

                            String msg = currency + " has up +" + dipDownThreshold + "%, it's trading time";
                            LINENotify.sendNotify(msg);
                        }
                    }
                }
            }
        }, 0, 1000);
    }

    public void buyAll(double price) {
        coinBalance = (dollarBalance / price) - (dollarBalance / price) * 0.001;
        dollarBalance = 0;
        isHolding = true;
        String msg = "Bought " + coinBalance + currency + " at price " + price;
        LINENotify.sendNotify(msg);
    }

    public void sellAll(double price) {
        double numOfSold = coinBalance;
        dollarBalance = (coinBalance * price) - (coinBalance * price) * 0.001;
        coinBalance = 0;
        isHolding = false;
        String msg = "Sold " + numOfSold + currency + " at price " + price + ", balance is " + dollarBalance + "USDT";
        LINENotify.sendNotify(msg);

        // Notice profit
        double profit = dollarBalance - 1000;
        LINENotify.sendNotify("Total profit at now: " + profit + "USDT");
    }

    public void stop() {
        ws.sendClose(0, "None");
        ws.abort();
        timer.cancel();
        timer.purge();
    }
}
