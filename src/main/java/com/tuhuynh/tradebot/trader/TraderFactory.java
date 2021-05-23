package com.tuhuynh.tradebot.trader;

import java.util.Map;
import java.util.TreeMap;

import lombok.Getter;

public class TraderFactory {
    @Getter
    private static double dollarBalance = 0;

    @Getter
    private static final Map<String, Double> profitTable = new TreeMap<>();

    public static void modifyDollarBalance(double toAdd) {
        dollarBalance += toAdd;
    }

    public static void setProfit(String currency, double profit) {
        profitTable.put(currency, profit);
    }

    public static double getProfit() {
        double total = 0;
        for (Map.Entry<String, Double> entry : profitTable.entrySet()) {
            double profit = entry.getValue();
            total += profit;
        }
        return total;
    }
}
