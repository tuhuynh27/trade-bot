package com.tuhuynh.tradebot;

import com.google.gson.Gson;

import lombok.Setter;

@Setter
public class AppFactory {
    private static Gson gson;

    public synchronized static Gson getGson() {
        if (gson == null) {
            gson = new Gson();
        }

        return gson;
    }
}
