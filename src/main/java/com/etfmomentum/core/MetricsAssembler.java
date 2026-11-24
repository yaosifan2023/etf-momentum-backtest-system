package com.etfmomentum.core;

import java.util.List;

/** 指标装配:从净值序列与交易记录生成统一口径的指标行 */
public final class MetricsAssembler {

    private MetricsAssembler() {
    }

    public static void append(BacktestResult r, String seriesId, double initialCash) {
        List<BacktestResult.Eq> eq = new java.util.ArrayList<>();
        for (BacktestResult.Eq e : r.equity) {
            if (seriesId.equals(e.seriesId)) {
                eq.add(e);
            }
        }
        List<BacktestResult.Trade> ts = new java.util.ArrayList<>();
        for (BacktestResult.Trade t : r.trades) {
            if (seriesId.equals(t.seriesId) && "FILLED".equals(t.status)) {
                ts.add(t);
            }
        }

        int points = eq.size();
        int tradingDays = Math.max(points - 1, 0);
        double[] nav = new double[points];
        for (int i = 0; i < points; i++) {
            nav[i] = eq.get(i).nav;
        }
        double lastNav = points > 0 ? nav[points - 1] : Double.NaN;
        Double totalReturn = points > 0 ? lastNav - 1.0 : null;
        Double annualReturn = points > 1 ? nanToNull(MetricsMath.annualReturn(lastNav, tradingDays)) : null;
        Double annualVol = points > 2 ? nanToNull(MetricsMath.annualVol(nav)) : null;
        Double maxDd = points > 0 ? MetricsMath.maxDrawdown(nav) : null;
        Double sharpe = (annualReturn != null && annualVol != null)
                ? nanToNull(MetricsMath.sharpe(annualReturn, annualVol, 0.0)) : null;

        int tradeCount = ts.size();
        double totalFee = 0;
        double notional = 0;
        for (BacktestResult.Trade t : ts) {
            totalFee += t.fee != null ? t.fee : 0;
            notional += t.quantity * (t.price != null ? t.price : 0);
        }
        double meanEquity = 0;
        for (double v : nav) {
            meanEquity += v * initialCash;
        }
        meanEquity = points > 0 ? meanEquity / points : 0;
        double years = tradingDays / 252.0;
        Double turnover = (meanEquity > 0 && years > 0) ? notional / meanEquity / years : null;

        r.metrics.add(new BacktestResult.Metric(seriesId, "total_return", totalReturn, "ratio",
                "累计收益=期末净值-1;净值=账户权益/评价起点交易前权益(指南7.3口径,首日损益与费用计入)"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "annual_return", annualReturn, "ratio",
                "年化收益=期末净值^(252/交易日数)-1;年化天数252,简单收益口径"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "annual_vol", annualVol, "ratio",
                "年化波动=日简单收益率样本标准差×sqrt(252);逐期收益采用简单收益形式"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "max_drawdown", maxDd, "ratio",
                "最大回撤=min(nav_t/历史峰值-1);初始净值1参与峰值与回撤计算,负值表示亏损幅度"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "sharpe", sharpe, "ratio",
                "夏普比率=(年化收益-无风险利率)/年化波动;无风险利率取0(教学假设),可选指标"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "turnover", turnover, "ratio_per_year",
                "换手率=Σ成交金额(买+卖)/平均账户权益/年数;仅统计成交记录"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "trade_count", (double) tradeCount, "count",
                "成交笔数(未成交尝试不计,见trades.csv的UNFILLED记录)"));
        r.metrics.add(new BacktestResult.Metric(seriesId, "total_cost", totalFee, "CNY",
                "总成本=Σ手续费;滑点已计入成交价不重复扣费,规模另见trades.csv的slippage_cost分析列"));
    }

    private static Double nanToNull(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
    }
}
