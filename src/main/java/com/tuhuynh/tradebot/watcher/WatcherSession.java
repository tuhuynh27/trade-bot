package com.tuhuynh.tradebot.watcher;

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
public class WatcherSession implements Runnable {
    private final Timer timer = new Timer();
    private final Gson gson = AppFactory.getGson();

    private WebSocket ws = null;

    private final String currency;
    private final float threshold;

    private double price = 0.0F;
    private final Deque<Double> prices = new LinkedBlockingDeque<>();
    private double diffs = 0.0F;

    public WatcherSession(String currency, float threshold) {
        this.currency = currency;
        this.threshold = threshold;
    }

    @Override
    public void run() {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                log.info("Connected to " + currency + " stream, started watching at threshold " + threshold);
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

                if (diffs > (threshold / 10) || diffs < -(threshold / 10)) {
                    String percentage = (diffs > 0 ? "+" : "-") + threshold + "%";
                    String msg = currency + " has just modified " + percentage + ", current price is " + price;
                    LINENotify.sendNotify(msg);
                    diffs = 0;
                }
            }
        }, 0, 1000);
    }

    public void stop() {
        ws.sendClose(0, "None");
        ws.abort();
        timer.cancel();
        timer.purge();
    }
}
