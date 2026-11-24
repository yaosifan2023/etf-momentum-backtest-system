package com.etfmomentum.core;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * T03.01 相对动量轮动回测引擎(B_主策略 etf_mom_rotation 1.0.0):
 *  - 信号日:每周(或每月)最后一个交易日收盘后,计算候选池每只 ETF 的 W 日动量
 *  - 入选:动量降序取前 K 只(并列按代码升序);可选趋势过滤:均线比值 < 阈值才可入选
 *  - 防御:开启且(无可选资产 或 最强动量 < 防御阈值)时,目标转为现金或防御资产
 *  - 执行:T+1 09:30 开盘价含滑点成交;先卖出落选持仓,再买入选中且未持有资产
 *  - 缓冲:已持有且仍在目标内的资产不调仓(权重漂移不修正),降低换手
 *  - 成本:双边手续费按成交金额计(最低手续费保底),滑点计入成交价不重复扣费
 *  - 基准:benchmarkAsset 买入持有,首个评价日开盘成交,无法成交则逐日开盘重试
 *  - 净值:nav = 账户权益 / 评价起点交易前权益(=初始资金,初始持仓 0);初始净值 1 参与回撤
 */
public class RotationBacktest {

    public static BacktestResult run(RotationParams p, Map<String, List<Bar>> data) {
        BacktestResult r = new BacktestResult();

        // 1. 统一交易日历(全部涉及资产的日期并集,升序)
        TreeSet<LocalDate> cal = new TreeSet<>();
        for (List<Bar> bars : data.values()) {
            for (Bar b : bars) {
                cal.add(b.date);
            }
        }
        List<LocalDate> dates = new ArrayList<>(cal);
        int n = dates.size();

        Map<String, Map<LocalDate, Bar>> barMap = new LinkedHashMap<>();
        Map<String, double[]> closesArr = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bar>> e : data.entrySet()) {
            Map<LocalDate, Bar> m = new HashMap<>();
            for (Bar b : e.getValue()) {
                m.put(b.date, b);
            }
            barMap.put(e.getKey(), m);
            double[] c = new double[n];
            Arrays.fill(c, Double.NaN);
            for (int i = 0; i < n; i++) {
                Bar b = m.get(dates.get(i));
                if (b != null && FactorMath.valid(b.close)) {
                    c[i] = b.close;
                }
            }
            closesArr.put(e.getKey(), c);
        }

        Account acct = new Account(p.initialCash);
        Account bench = new Account(p.initialCash);
        boolean benchBought = false;
        boolean baselineRecorded = false;

        Map<String, Double> pendingTargets = null;   // 信号日收盘产生的目标(资产->权重)
        LocalDate signalDate = null;

        for (int i = 0; i < n; i++) {
            LocalDate t = dates.get(i);
            boolean inEval = !t.isBefore(p.start) && !t.isAfter(p.end);

            // 评价首个交易日交易前:记录初始净值 1
            if (inEval && !baselineRecorded) {
                r.equity.add(new BacktestResult.Eq("strategy", t, 1.0, acct.cash, 0.0));
                r.equity.add(new BacktestResult.Eq("benchmark", t, 1.0, bench.cash, 0.0));
                baselineRecorded = true;
            }

            // ---- 09:30 开盘:执行上一信号日的目标 ----
            if (pendingTargets != null) {
                if (inEval) {
                    executeAtOpen(r, p, acct, barMap, dates, i, pendingTargets, signalDate);
                }
                pendingTargets = null;   // 成交日早于评价起点:不执行
            }

            // ---- 基准买入持有 ----
            if (inEval && !benchBought) {
                Bar b = barMap.get(p.benchmarkAsset) != null ? barMap.get(p.benchmarkAsset).get(t) : null;
                if (b != null) {
                    double fill = ExecutionMath.buyFillPrice(b.open, p.slippageRate);
                    long shares = ExecutionMath.maxAffordableShares(bench.cash, fill, p.commissionRate);
                    if (shares >= 1) {
                        double fee = ExecutionMath.fee(shares * fill, p.commissionRate, p.minimumCommission);
                        bench.buy(p.benchmarkAsset, shares, fill, fee);
                        benchBought = true;
                        r.trades.add(new BacktestResult.Trade("benchmark",
                                t + "T00:00:00+08:00", t + "T09:30:00+08:00", p.benchmarkAsset, "buy",
                                shares, fill, fee, Math.abs(fill - b.open) * shares, "FILLED",
                                String.format("基准建仓:期初开盘买入并持有;开盘价 %.6f,含滑点买价 %.6f,买入 %d 份,手续费 %.6f 元",
                                        b.open, fill, shares, fee)));
                    }
                }
            }

            // ---- 收盘估值 ----
            for (Map.Entry<String, Map<LocalDate, Bar>> en : barMap.entrySet()) {
                Bar b = en.getValue().get(t);
                if (b != null) {
                    acct.lastPrice.put(en.getKey(), b.close);
                    bench.lastPrice.put(en.getKey(), b.close);
                }
            }
            if (inEval) {
                double eqS = acct.equity();
                double eqB = bench.equity();
                r.equity.add(new BacktestResult.Eq("strategy", t, eqS / p.initialCash, acct.cash, eqS - acct.cash));
                r.equity.add(new BacktestResult.Eq("benchmark", t, eqB / p.initialCash, bench.cash, eqB - bench.cash));
                for (Map.Entry<String, Double> qe : acct.quantity.entrySet()) {
                    double q = qe.getValue();
                    if (q > 0) {
                        double px = acct.lastPrice.getOrDefault(qe.getKey(), 0.0);
                        r.positions.add(new BacktestResult.Pos("strategy", t, qe.getKey(), q,
                                eqS > 0 ? q * px / eqS : 0.0));
                    }
                }
                double qb = bench.quantity.getOrDefault(p.benchmarkAsset, 0.0);
                if (qb > 0) {
                    double px = bench.lastPrice.getOrDefault(p.benchmarkAsset, 0.0);
                    r.positions.add(new BacktestResult.Pos("benchmark", t, p.benchmarkAsset, qb,
                            eqB > 0 ? qb * px / eqB : 0.0));
                }
            }

            // ---- 收盘后:信号日计算因子、排名与目标 ----
            if (isSignalDate(p, dates, i)) {
                SignalOutcome so = computeSignal(p, closesArr, dates, i);
                r.decisions.add(new BacktestResult.Decision(t, true, so.defensiveTriggered, so.defensiveReason, so.payload));
                pendingTargets = so.targets;
                signalDate = t;
            }
        }

        MetricsAssembler.append(r, "strategy", p.initialCash);
        MetricsAssembler.append(r, "benchmark", p.initialCash);
        return r;
    }

    /** 信号日:调仓频率边界日(下一交易日跨周/跨月),且次日落在评价区间内可执行 */
    private static boolean isSignalDate(RotationParams p, List<LocalDate> dates, int i) {
        if (i + 1 >= dates.size()) {
            return false;   // 无下一交易日,末日信号不执行(按 exceptions 约定)
        }
        LocalDate t = dates.get(i);
        LocalDate next = dates.get(i + 1);
        if (next.isAfter(p.end) || next.isBefore(p.start)) {
            return false;   // 成交日不在评价区间内:不产生信号(预热期数据仅用于因子计算)
        }
        boolean boundary;
        if ("MONTHLY".equals(p.rebalanceFreq)) {
            boundary = next.getYear() != t.getYear() || next.getMonthValue() != t.getMonthValue();
        } else {
            boundary = !isoWeek(t).equals(isoWeek(next));
        }
        return boundary;
    }

    private static String isoWeek(LocalDate d) {
        WeekFields wf = WeekFields.ISO;
        return d.getYear() + "-W" + d.get(wf.weekOfWeekBasedYear());
    }

    private static class SignalOutcome {
        Map<String, Double> targets;
        boolean defensiveTriggered;
        String defensiveReason;
        Map<String, Object> payload;
    }

    private static SignalOutcome computeSignal(RotationParams p, Map<String, double[]> closesArr,
                                               List<LocalDate> dates, int i) {
        LocalDate t = dates.get(i);
        LocalDate execDate = dates.get(i + 1);

        List<Map<String, Object>> assetRows = new ArrayList<>();
        List<String> eligible = new ArrayList<>();
        Map<String, Double> momMap = new HashMap<>();
        for (String code : p.pool) {
            double[] closes = closesArr.get(code);
            Double mom = null;
            Double ma = null;
            if (closes != null) {
                double m = FactorMath.momentum(closes, i, p.momentumWindow);
                if (!Double.isNaN(m)) {
                    mom = m;
                    momMap.put(code, m);
                }
                if (p.trendFilterEnabled) {
                    double v = FactorMath.maRatio(closes, i, p.trendWindow);
                    if (!Double.isNaN(v)) {
                        ma = v;
                    }
                }
            }
            boolean ok = mom != null;
            if (ok && p.trendFilterEnabled) {
                ok = ma != null && ma < p.trendThreshold;
            }
            if (ok) {
                eligible.add(code);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("momentum", mom);
            row.put("ma_ratio", ma);
            row.put("eligible", ok);
            assetRows.add(row);
        }

        // 动量降序,并列按代码升序
        eligible.sort((a, b) -> {
            int c = Double.compare(momMap.get(b), momMap.get(a));
            return c != 0 ? c : a.compareTo(b);
        });
        for (int k = 0; k < eligible.size(); k++) {
            for (Map<String, Object> row : assetRows) {
                if (row.get("code").equals(eligible.get(k))) {
                    row.put("rank", k + 1);
                }
            }
        }

        Map<String, Double> targets = new LinkedHashMap<>();
        boolean defensive = false;
        String defensiveReason = null;
        if (eligible.isEmpty()) {
            if (p.defensiveEnabled) {
                defensive = true;
                defensiveReason = "候选池内无可选资产(动量窗口不足或全部被趋势过滤),转入防御";
            }
        } else if (p.defensiveEnabled && momMap.get(eligible.get(0)) < p.defensiveThreshold) {
            defensive = true;
            defensiveReason = String.format("最强动量 %.6f 低于防御阈值 %.6f,转入防御",
                    momMap.get(eligible.get(0)), p.defensiveThreshold);
        }

        List<Map<String, Object>> targetRows = new ArrayList<>();
        if (defensive) {
            if ("ASSET".equals(p.defensiveMode) && p.defensiveAsset != null) {
                targets.put(p.defensiveAsset, 1.0);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", p.defensiveAsset);
                row.put("weight", 1.0);
                targetRows.add(row);
            }
        } else {
            int k = Math.min(p.holdingCount, eligible.size());
            for (int j = 0; j < k; j++) {
                String code = eligible.get(j);
                targets.put(code, 1.0 / k);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", code);
                row.put("weight", 1.0 / k);
                row.put("rank", j + 1);
                row.put("momentum", momMap.get(code));
                targetRows.add(row);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signal_date", t.toString());
        payload.put("signal_time", t + "T15:05:00+08:00");
        payload.put("execution_date", execDate.toString());
        payload.put("execution_time", execDate + "T09:30:00+08:00");
        payload.put("rebalance_freq", p.rebalanceFreq);
        payload.put("momentum_window", p.momentumWindow);
        payload.put("holding_count", p.holdingCount);
        payload.put("trend_filter", p.trendFilterEnabled);
        payload.put("defensive", mapOf("enabled", p.defensiveEnabled, "triggered", defensive, "reason", defensiveReason));
        payload.put("assets", assetRows);
        payload.put("targets", targetRows);

        SignalOutcome so = new SignalOutcome();
        so.targets = targets;
        so.defensiveTriggered = defensive;
        so.defensiveReason = defensiveReason;
        so.payload = payload;
        return so;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** T+1 开盘执行:先卖出落选持仓,再买入新入选资产(已持有且仍在目标内不调整) */
    private static void executeAtOpen(BacktestResult r, RotationParams p, Account acct,
                                      Map<String, Map<LocalDate, Bar>> barMap, List<LocalDate> dates, int i,
                                      Map<String, Double> targets, LocalDate signalDate) {
        LocalDate t = dates.get(i);
        String sigT = signalDate + "T15:05:00+08:00";
        String exeT = t + "T09:30:00+08:00";

        // 卖出:已持有但不在目标内(落选或防御切换)
        List<String> heldAssets = new ArrayList<>();
        for (Map.Entry<String, Double> e : acct.quantity.entrySet()) {
            if (e.getValue() > 0) {
                heldAssets.add(e.getKey());
            }
        }
        for (String a : heldAssets) {
            if (targets.containsKey(a)) {
                continue;
            }
            double q = acct.quantity.get(a);
            Bar b = barMap.get(a) != null ? barMap.get(a).get(t) : null;
            if (b == null) {
                r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, a, "sell",
                        q, null, 0.0, 0.0, "UNFILLED", "当日无行情(停牌/缺数据),卖出顺延,保留原持仓"));
                continue;
            }
            double fill = ExecutionMath.sellFillPrice(b.open, p.slippageRate);
            double fee = ExecutionMath.fee(q * fill, p.commissionRate, p.minimumCommission);
            acct.sellAll(a, fill, fee);
            r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, a, "sell",
                    q, fill, fee, Math.abs(b.open - fill) * q, "FILLED",
                    String.format("落选/防御切换:开盘价 %.6f,含滑点卖价 %.6f,卖出 %.0f 份,手续费 %.6f 元",
                            b.open, fill, q, fee)));
        }

        // 账户权益估计(开盘价口径,用于确定各新买入资产的预算)
        double equityEst = acct.cash;
        for (Map.Entry<String, Double> e : acct.quantity.entrySet()) {
            if (e.getValue() > 0) {
                Bar b = barMap.get(e.getKey()) != null ? barMap.get(e.getKey()).get(t) : null;
                double px = b != null ? b.open : acct.lastPrice.getOrDefault(e.getKey(), 0.0);
                equityEst += e.getValue() * px;
            }
        }

        // 买入:目标内且当前未持有(按排名顺序)
        for (Map.Entry<String, Double> te : targets.entrySet()) {
            String a = te.getKey();
            if (acct.quantity.getOrDefault(a, 0.0) > 0) {
                continue;   // 缓冲:已持有且仍在目标内,不调整数量
            }
            Bar b = barMap.get(a) != null ? barMap.get(a).get(t) : null;
            if (b == null) {
                r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, a, "buy",
                        0, null, 0.0, 0.0, "UNFILLED", "当日无行情(停牌/缺数据),买入顺延至下次调仓"));
                continue;
            }
            double fill = ExecutionMath.buyFillPrice(b.open, p.slippageRate);
            double budget = Math.min(equityEst * te.getValue(), acct.cash);
            long shares = ExecutionMath.maxAffordableShares(budget, fill, p.commissionRate);
            if (shares < 1) {
                r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, a, "buy",
                        0, null, 0.0, 0.0, "UNFILLED",
                        String.format("资金不足1份:预算 %.2f 元,含滑点买价 %.6f", budget, fill)));
                continue;
            }
            double fee = ExecutionMath.fee(shares * fill, p.commissionRate, p.minimumCommission);
            acct.buy(a, shares, fill, fee);
            r.trades.add(new BacktestResult.Trade("strategy", sigT, exeT, a, "buy",
                    shares, fill, fee, Math.abs(fill - b.open) * shares, "FILLED",
                    String.format("入选Top%d(目标权重 %.0f%%):开盘价 %.6f,含滑点买价 %.6f,买入 %d 份,手续费 %.6f 元",
                            p.holdingCount, te.getValue() * 100, b.open, fill, shares, fee)));
        }
    }
}
