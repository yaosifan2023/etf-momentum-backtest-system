package com.etfmomentum.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B 阶段示例策略复算引擎:均线比值持有策略(单资产,现金/全仓两态)。
 * 规则与《实验要求》6.6 / 7.5 示例完全一致:
 *  - 因子 Mean(close,d)/close,窗口含当日;不足窗口或含无效价不产生新目标(不用更早日期补足)
 *  - 因子 < threshold 时目标为持有;>= threshold 时目标为现金(恰好等于阈值即退出)
 *  - 信号于 T 日 15:05(日线可用后)产生,T+1 09:30 按开盘价含滑点成交,时区 +08:00
 *  - 买价上浮、卖价下调 slippageRate;手续费 = 成交金额 × commissionRate(最低 minimumCommission)
 *  - 买入数量 = floor(现金 / (含滑点买价 × (1+费率))),余款留现金,现金不计息
 *  - 状态不变不下单;评价区间开始前不建仓(账户自现金开始)
 *  - 基准:同一资产买入持有,首个评价日开盘按同样规则买入,无法成交则逐日开盘重试
 */
public class MaRatioLongCashBacktest {

    public static BacktestResult run(List<Bar> bars, LocalDate start, LocalDate end,
                                     int window, double threshold,
                                     double initialCash, double commissionRate, double minimumCommission,
                                     double slippageRate) {
        BacktestResult r = new BacktestResult();
        int n = bars.size();
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            closes[i] = bars.get(i).close;
        }

        Account acct = new Account(initialCash);
        Account bench = new Account(initialCash);
        boolean holding = false;
        boolean benchBought = false;
        boolean baselineRecorded = false;

        Integer pendingSide = null;     // +1 买 / -1 卖
        LocalDate signalDate = null;
        double pendingFactor = Double.NaN;

        for (int i = 0; i < n; i++) {
            Bar day = bars.get(i);
            boolean inEval = !day.date.isBefore(start) && !day.date.isAfter(end);

            // 评价首个交易日交易前:记录初始净值 1(参与历史峰值与回撤)
            if (inEval && !baselineRecorded) {
                r.equity.add(new BacktestResult.Eq("strategy", day.date, 1.0, acct.cash, 0.0));
                r.equity.add(new BacktestResult.Eq("benchmark", day.date, 1.0, bench.cash, 0.0));
                baselineRecorded = true;
            }

            // ---- 09:30 开盘:执行前一交易日收盘产生的信号 ----
            if (pendingSide != null) {
                if (inEval) {
                    String sigT = signalDate + "T15:05:00+08:00";
                    String exeT = day.date + "T09:30:00+08:00";
                    if (pendingSide > 0) {
                        double fill = ExecutionMath.buyFillPrice(day.open, slippageRate);
                        long shares = ExecutionMath.maxAffordableShares(acct.cash, fill, commissionRate);
                        if (shares >= 1) {
                            double fee = ExecutionMath.fee(shares * fill, commissionRate, minimumCommission);
                            acct.buy("DEMO_A", shares, fill, fee);
                            holding = true;
                            r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, "DEMO_A", "buy",
                                    shares, fill, fee, Math.abs(fill - day.open) * shares, "FILLED",
                                    String.format("均线比值 %.6f < %.6f,目标转为持有;开盘价 %.6f,含滑点买价 %.6f,买入 %d 份,手续费 %.6f 元,余现金 %.6f 元",
                                            pendingFactor, threshold, day.open, fill, shares, fee, acct.cash)));
                        }
                    } else {
                        double q = acct.quantity.getOrDefault("DEMO_A", 0.0);
                        if (q > 0) {
                            double fill = ExecutionMath.sellFillPrice(day.open, slippageRate);
                            double fee = ExecutionMath.fee(q * fill, commissionRate, minimumCommission);
                            acct.sellAll("DEMO_A", fill, fee);
                            r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, "DEMO_A", "sell",
                                    q, fill, fee, Math.abs(day.open - fill) * q, "FILLED",
                                    String.format("均线比值 %.6f >= %.6f,目标转为现金;开盘价 %.6f,含滑点卖价 %.6f,卖出 %.0f 份,手续费 %.6f 元,现金 %.6f 元",
                                            pendingFactor, threshold, day.open, fill, q, fee, acct.cash)));
                        }
                        holding = false;
                    }
                }
                pendingSide = null;   // 成交日早于评价起点:不执行(账户自评价起点的现金状态开始)
            }

            // ---- 基准买入持有:首个评价日开盘买入,失败顺延 ----
            if (inEval && !benchBought) {
                double fill = ExecutionMath.buyFillPrice(day.open, slippageRate);
                long shares = ExecutionMath.maxAffordableShares(bench.cash, fill, commissionRate);
                if (shares >= 1) {
                    double fee = ExecutionMath.fee(shares * fill, commissionRate, minimumCommission);
                    bench.buy("DEMO_A", shares, fill, fee);
                    benchBought = true;
                    r.trades.add(new BacktestResult.Trade("benchmark",
                            day.date + "T00:00:00+08:00", day.date + "T09:30:00+08:00", "DEMO_A", "buy",
                            shares, fill, fee, Math.abs(fill - day.open) * shares, "FILLED",
                            String.format("基准建仓:期初开盘买入并持有;开盘价 %.6f,含滑点买价 %.6f,买入 %d 份,手续费 %.6f 元",
                                    day.open, fill, shares, fee)));
                }
            }

            // ---- 收盘估值 ----
            acct.lastPrice.put("DEMO_A", day.close);
            bench.lastPrice.put("DEMO_A", day.close);
            if (inEval) {
                double eqS = acct.equity();
                double eqB = bench.equity();
                r.equity.add(new BacktestResult.Eq("strategy", day.date, eqS / initialCash, acct.cash, eqS - acct.cash));
                r.equity.add(new BacktestResult.Eq("benchmark", day.date, eqB / initialCash, bench.cash, eqB - bench.cash));
                double q = acct.quantity.getOrDefault("DEMO_A", 0.0);
                if (q > 0) {
                    r.positions.add(new BacktestResult.Pos("strategy", day.date, "DEMO_A", q, q * day.close / eqS));
                }
                double qb = bench.quantity.getOrDefault("DEMO_A", 0.0);
                if (qb > 0) {
                    r.positions.add(new BacktestResult.Pos("benchmark", day.date, "DEMO_A", qb, qb * day.close / eqB));
                }
            }

            // ---- 收盘后:计算因子并生成次日信号(逐日记录决策,便于过程检查) ----
            double ratio = FactorMath.maRatio(closes, i, window);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("signal_date", day.date.toString());
            payload.put("signal_time", day.date + "T15:05:00+08:00");
            boolean hasFactor = !Double.isNaN(ratio);
            payload.put("factor_value", hasFactor ? ratio : null);
            payload.put("current_state", holding ? "hold" : "cash");
            if (hasFactor) {
                boolean targetHold = ratio < threshold;
                payload.put("target_state", targetHold ? "hold" : "cash");
                if (targetHold != holding) {
                    pendingSide = targetHold ? 1 : -1;
                    signalDate = day.date;
                    pendingFactor = ratio;
                    payload.put("state_change", true);
                    payload.put("note", targetHold ? "因子小于阈值,次日开盘买入" : "因子大于等于阈值,次日开盘卖出");
                } else {
                    payload.put("state_change", false);
                    payload.put("note", holding ? "已持有且目标不变,不下单" : "现金状态且目标不变,不下单");
                }
            } else {
                payload.put("target_state", null);
                payload.put("state_change", false);
                payload.put("note", "不足窗口或含无效价,不产生新目标");
            }
            r.decisions.add(new BacktestResult.Decision(day.date, inEval, false, null, payload));
        }

        MetricsAssembler.append(r, "strategy", initialCash);
        MetricsAssembler.append(r, "benchmark", initialCash);
        return r;
    }
}
