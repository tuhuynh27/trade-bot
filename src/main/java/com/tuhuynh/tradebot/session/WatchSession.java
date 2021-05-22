package com.tuhuynh.tradebot.session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionStage;

import com.google.gson.Gson;
import com.tuhuynh.tradebot.entities.binance.AggTradeStreamMsg;
import com.tuhuynh.tradebot.linebot.LINENotify;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchSession implements Runnable {
    private final Gson gson = new Gson();

    private WebSocket ws = null;

    private final String currency;
    private final float threshold;

    private double price = 0.0F;
    private final Deque<Double> prices = new ArrayDeque<>();
    private double diffs = 0.0F;

    public WatchSession(String currency, float threshold) {
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

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                prices.add(price);
                if (prices.size() > 10) {
                    prices.removeFirst();
                }
            }
        }, 0, 1000);

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (prices.size() < 10) {
                    return;
                }

                try {
                    double diff = ((prices.getLast() / prices.getFirst()) - 1);
                    diffs += diff;
                } catch (RuntimeException ignored) {
                }
            }
        }, 0, 1000);

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (diffs > (threshold / 10) || diffs < -(threshold / 10)) {
                    String percentage = (diffs > 0 ? "+" : "-") + threshold + "%";
                    String msg = currency + " has just modified " + percentage + ", current price is " + price;
                    log.info(msg);
                    LINENotify.sendNotify(msg);
                    diffs = 0;
                }
            }
        }, 0, 1000);
    }

    public void stop() {
        ws.sendClose(0, "None");
        ws.abort();
    }
}
