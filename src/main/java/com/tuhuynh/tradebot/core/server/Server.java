package com.tuhuynh.tradebot.core.server;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tuhuynh.tradebot.factory.AppFactory;

import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {
    private final Gson gson = AppFactory.getGson();
    private final HttpServer httpServer;

    @SneakyThrows
    private Server(String hostname, int port) {
        httpServer = HttpServer.create(new InetSocketAddress(hostname, port), 0);
    }

    public static Server create(String hostname, int port) {
        return new Server(hostname, port);
    }

    public void use(String path, Handler handler) {
        httpServer.createContext(path, (HttpExchange exchange) -> {
            InputStream inputStream = exchange.getRequestBody();
            String requestBody;
            try (Reader reader = new InputStreamReader(inputStream)) {
                requestBody = CharStreams.toString(reader);
            }

            Context context = new Context();
            context.method = exchange.getRequestMethod();
            context.body = requestBody;
            context.headers = exchange.getRequestHeaders();

            HttpResponse httpResponse = handler.handle(context);
            String bodyString = gson.toJson(httpResponse.body);
            OutputStream outputStream = exchange.getResponseBody();

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpResponse.code, bodyString.length());

            outputStream.write(bodyString.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        });
    }

    public void start() {
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
    }

    @Getter
    public static class Context {
        String method;
        String body;
        Headers headers;
    }

    @FunctionalInterface
    public interface Handler {
        HttpResponse handle(Context context);
    }

    @Builder
    public static class HttpResponse {
        int code;
        Object body;
    }
}
