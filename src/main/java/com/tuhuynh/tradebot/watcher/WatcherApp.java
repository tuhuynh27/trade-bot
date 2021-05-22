package com.tuhuynh.tradebot.watcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.tuhuynh.tradebot.factory.AppFactory;
import com.tuhuynh.tradebot.core.server.Server;
import com.tuhuynh.tradebot.core.server.Server.Context;
import com.tuhuynh.tradebot.core.server.Server.HttpResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatcherApp implements Runnable {
    private final Gson gson = AppFactory.getGson();
    private ExecutorService executorService = null;
    private List<WatcherSession> watcherSessions;

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
        watcherSessions = listWatch.stream()
                                   .map(w -> new WatcherSession(w.currency, w.threshold))
                                   .collect(Collectors.toList());
        watcherSessions.forEach(executorService::submit);
    }

    public void terminateWatchers() {
        watcherSessions.forEach(WatcherSession::stop);
        executorService.shutdownNow();
    }

    @AllArgsConstructor
    static class WatchItem {
        String currency;
        float threshold;
    }
}
