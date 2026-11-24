package com.etfmomentum.core;

/** 组合指标计算(口径见 metrics.csv 的 definition 列) */
public final class MetricsMath {

    private MetricsMath() {
    }

    /** 最大回撤:min(nav_t / 历史峰值 - 1);初始净值 1 参与峰值计算 */
    public static double maxDrawdown(double[] nav) {
        double peak = Double.NEGATIVE_INFINITY;
        double mdd = 0;
        for (double v : nav) {
            if (v > peak) {
                peak = v;
            }
            double dd = v / peak - 1.0;
            if (dd < mdd) {
                mdd = dd;
            }
        }
        return mdd;
    }

    /** 年化收益:(期末净值)^(252/交易日数) - 1,简单收益口径 */
    public static double annualReturn(double lastNav, int tradingDays) {
        if (tradingDays <= 0 || lastNav <= 0) {
            return Double.NaN;
        }
        return Math.pow(lastNav, 252.0 / tradingDays) - 1.0;
    }

    /** 年化波动:日简单收益率样本标准差 × sqrt(252) */
    public static double annualVol(double[] nav) {
        if (nav.length < 3) {
            return Double.NaN;
        }
        int m = nav.length - 1;
        double[] r = new double[m];
        double mean = 0;
        for (int i = 0; i < m; i++) {
            if (!(nav[i] > 0)) {
                return Double.NaN;
            }
            r[i] = nav[i + 1] / nav[i] - 1.0;
            mean += r[i];
        }
        mean /= m;
        double var = 0;
        for (double v : r) {
            var += (v - mean) * (v - mean);
        }
        var /= (m - 1);
        return Math.sqrt(var) * Math.sqrt(252.0);
    }

    /** 夏普比率:(年化收益 - 无风险利率) / 年化波动;本课题无风险利率取 0(教学假设) */
    public static double sharpe(double annualReturn, double annualVol, double riskFree) {
        if (Double.isNaN(annualReturn) || Double.isNaN(annualVol) || annualVol <= 0) {
            return Double.NaN;
        }
        return (annualReturn - riskFree) / annualVol;
    }
}
