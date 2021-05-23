package com.tuhuynh.tradebot.linebot;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LINENotify {
    @Getter
    private static final ArrayList<String> notifyLogs = new ArrayList<>();

    @SneakyThrows
    public static void sendNotify(String msg) {
        log.info("LINE Notify: " + msg);
        notifyLogs.add(msg);
        HttpClient httpClient = HttpClient.newBuilder()
                                          .version(Version.HTTP_1_1)
                                          .connectTimeout(Duration.ofSeconds(10))
                                          .build();
        String token = "MFm1y1zojjw2BP7SY8lTymGrcYclaJmWw5PwbYuJ60C";
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(new URI("https://notify-api.line.me/api/notify?message="
                                                      + URLEncoder.encode(msg, StandardCharsets.UTF_8)))
                                         .headers("Authorization", "Bearer " + token)
                                         .POST(HttpRequest.BodyPublishers.ofString(""))
                                         .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
