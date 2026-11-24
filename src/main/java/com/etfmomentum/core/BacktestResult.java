package com.etfmomentum.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 引擎输出(由服务层持久化到数据库并导出 CSV/JSON) */
public class BacktestResult {

    public final List<Eq> equity = new ArrayList<>();
    public final List<Pos> positions = new ArrayList<>();
    public final List<Trade> trades = new ArrayList<>();
    public final List<Decision> decisions = new ArrayList<>();
    public final List<Metric> metrics = new ArrayList<>();

    public static class Eq {
        public final String seriesId;
        public final LocalDate date;
        public final double nav;
        public final double cash;
        public final double positionValue;

        public Eq(String seriesId, LocalDate date, double nav, double cash, double positionValue) {
            this.seriesId = seriesId;
            this.date = date;
            this.nav = nav;
            this.cash = cash;
            this.positionValue = positionValue;
        }
    }

    public static class Pos {
        public final String seriesId;
        public final LocalDate date;
        public final String assetId;
        public final double quantity;
        public final double weight;

        public Pos(String seriesId, LocalDate date, String assetId, double quantity, double weight) {
            this.seriesId = seriesId;
            this.date = date;
            this.assetId = assetId;
            this.quantity = quantity;
            this.weight = weight;
        }
    }

    public static class Trade {
        public final String seriesId;
        public final String signalTime;
        public final String executionTime;   // null = 未下单
        public final String assetId;
        public final String side;            // buy / sell
        public final double quantity;
        public final Double price;           // 含滑点成交价;null = NA
        public final Double fee;
        public final Double slippageCost;
        public final String status;          // FILLED / UNFILLED
        public final String reason;

        public Trade(String seriesId, String signalTime, String executionTime, String assetId, String side,
                     double quantity, Double price, Double fee, Double slippageCost, String status, String reason) {
            this.seriesId = seriesId;
            this.signalTime = signalTime;
            this.executionTime = executionTime;
            this.assetId = assetId;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.fee = fee;
            this.slippageCost = slippageCost;
            this.status = status;
            this.reason = reason;
        }
    }

    public static class Decision {
        public final LocalDate signalDate;
        public final boolean executed;
        public final boolean defensiveTriggered;
        public final String defensiveReason;
        public final Map<String, Object> payload;   // 完整决策明细,服务层序列化为 JSON

        public Decision(LocalDate signalDate, boolean executed, boolean defensiveTriggered,
                        String defensiveReason, Map<String, Object> payload) {
            this.signalDate = signalDate;
            this.executed = executed;
            this.defensiveTriggered = defensiveTriggered;
            this.defensiveReason = defensiveReason;
            this.payload = payload;
        }
    }

    public static class Metric {
        public final String seriesId;
        public final String metric;
        public final Double value;           // null = NA
        public final String unit;
        public final String definition;

        public Metric(String seriesId, String metric, Double value, String unit, String definition) {
            this.seriesId = seriesId;
            this.metric = metric;
            this.value = value;
            this.unit = unit;
            this.definition = definition;
        }
    }
}
