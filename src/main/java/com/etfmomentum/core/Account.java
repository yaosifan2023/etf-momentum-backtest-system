package com.etfmomentum.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 回测账户:现金 + 持仓数量 + 各资产最新估值价(停牌时沿用最近收盘价) */
public class Account {

    public double cash;
    public final Map<String, Double> quantity = new LinkedHashMap<>();
    public final Map<String, Double> lastPrice = new LinkedHashMap<>();

    public Account(double cash) {
        this.cash = cash;
    }

    public void buy(String asset, long shares, double fillPrice, double fee) {
        cash -= shares * fillPrice + fee;
        quantity.merge(asset, (double) shares, Double::sum);
    }

    public void sellAll(String asset, double fillPrice, double fee) {
        double q = quantity.getOrDefault(asset, 0.0);
        cash += q * fillPrice - fee;
        quantity.put(asset, 0.0);
    }

    public double held(String asset) {
        double q = quantity.getOrDefault(asset, 0.0);
        return q > 0 ? q : 0.0;
    }

    public double marketValue() {
        double v = 0;
        for (Map.Entry<String, Double> e : quantity.entrySet()) {
            double q = e.getValue();
            if (q > 0) {
                v += q * lastPrice.getOrDefault(e.getKey(), 0.0);
            }
        }
        return v;
    }

    public double equity() {
        return cash + marketValue();
    }
}
