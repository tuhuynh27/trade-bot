package com.tuhuynh.tradebot.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.tuhuynh.tradebot.AppFactory;
import com.tuhuynh.tradebot.server.Server;
import com.tuhuynh.tradebot.server.Server.Context;
import com.tuhuynh.tradebot.server.Server.HttpResponse;
import com.tuhuynh.tradebot.session.WatchSession;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatcherApp implements Runnable {
    private final Gson gson = AppFactory.getGson();
    private ExecutorService executorService = null;
    private List<WatchSession> watchSessions;

    private ArrayList<WatchItem> listWatch = new ArrayList<>(Arrays.asList(
            new WatchItem("ETH", 5F),
            new WatchItem("BTC", 5F),
            new WatchItem("ADA", 3F)));

    @Override
    public void run() {
        initWatchers();
        Server server = Server.create("localhost", 1234);

        server.use("/watch/list", (Context context) -> HttpResponse.builder().body(listWatch).code(200).build());

        server.use("/watch/setup", (Context context) -> {
            listWatch = gson.fromJson(context.getBody(), new TypeToken<List<WatchItem>>(){}.getType());
            terminateWatchers();
            initWatchers();
            return HttpResponse.builder().body(listWatch).code(200).build();
        });

        server.start();
    }

    public void initWatchers() {
        executorService = Executors.newCachedThreadPool();
        watchSessions = listWatch.stream()
                                 .map(w -> new WatchSession(w.currency, w.threshold))
                                 .collect(Collectors.toList());
        watchSessions.forEach(executorService::submit);
    }

    public void terminateWatchers() {
        watchSessions.forEach(WatchSession::stop);
        executorService.shutdownNow();
    }

    @AllArgsConstructor
    static class WatchItem {
        String currency;
        float threshold;
    }
}
