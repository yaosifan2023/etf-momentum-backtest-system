package com.etfmomentum.core;

/**
 * 因子计算(与 A_因子搜集.xlsx 中 22 列定义一一对应):
 *  - g01_mom_n   N日动量     MOM = close[t]/close[t-W] - 1
 *  - g01_ma_ratio 均线比值   MA_ratio = Mean(close, d) / close[t]
 *  - g01_vol_d   年化波动率   VOL = std(日简单收益率, d) * sqrt(252)
 * 缺失/异常处理:窗口不足、含无效价(非数字/非正数)时返回 NaN,不计算、不补值。
 */
public final class FactorMath {

    private FactorMath() {
    }

    public static boolean valid(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v > 0;
    }

    /** N 日动量:close[i]/close[i-W]-1。索引基于统一交易日历,窗口含两端共 W+1 条。 */
    public static double momentum(double[] closes, int i, int window) {
        int j = i - window;
        if (j < 0) {
            return Double.NaN;
        }
        double c0 = closes[j];
        double c1 = closes[i];
        if (!valid(c0) || !valid(c1)) {
            return Double.NaN;
        }
        return c1 / c0 - 1.0;
    }

    /** 均线比值:最近 d 个交易日(含当日)平均收盘价 / 当日收盘价。 */
    public static double maRatio(double[] closes, int i, int window) {
        int start = i - window + 1;
        if (start < 0) {
            return Double.NaN;
        }
        for (int k = start; k <= i; k++) {
            if (!valid(closes[k])) {
                return Double.NaN;
            }
        }
        double sum = 0;
        for (int k = start; k <= i; k++) {
            sum += closes[k];
        }
        return (sum / window) / closes[i];
    }

    /** 年化波动率:最近 window 日日简单收益率的样本标准差 × sqrt(252)。需要 window+1 条价格。 */
    public static double annualizedVol(double[] closes, int i, int window) {
        int first = i - window;
        if (first < 0 || window < 2) {
            return Double.NaN;
        }
        double[] r = new double[window];
        for (int k = 0; k < window; k++) {
            double a = closes[first + k];
            double b = closes[first + k + 1];
            if (!valid(a) || !valid(b)) {
                return Double.NaN;
            }
            r[k] = b / a - 1.0;
        }
        double mean = 0;
        for (double v : r) {
            mean += v;
        }
        mean /= window;
        double var = 0;
        for (double v : r) {
            var += (v - mean) * (v - mean);
        }
        var /= (window - 1);
        return Math.sqrt(var) * Math.sqrt(252.0);
    }
}
