package com.etfmomentum;

import com.etfmomentum.core.MetricsMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 指标计算测试(口径见 metrics.csv definition 列) */
class MetricsMathTest {

    @Test
    void maxDrawdownIncludesBaselineOne() {
        double[] nav = {1.0, 1.1, 0.99, 1.05};
        // 峰值1.1 → 谷0.99:0.99/1.1-1 = -0.1
        assertEquals(-0.1, MetricsMath.maxDrawdown(nav), 1e-12);
    }

    @Test
    void maxDrawdownFromInitialOne() {
        // 初始净值1本身是峰值:首日亏损计入回撤
        double[] nav = {1.0, 0.95, 0.97};
        assertEquals(-0.05, MetricsMath.maxDrawdown(nav), 1e-12);
    }

    @Test
    void annualReturnFormula() {
        // 净值2、252个交易日 → 年化100%
        assertEquals(1.0, MetricsMath.annualReturn(2.0, 252), 1e-12);
        // 净值1 → 0
        assertEquals(0.0, MetricsMath.annualReturn(1.0, 100), 1e-12);
        assertTrue(Double.isNaN(MetricsMath.annualReturn(1.5, 0)));
    }

    @Test
    void annualVolAndSharpe() {
        double[] nav = {1.0, 1.01, 1.02, 1.03};
        double vol = MetricsMath.annualVol(nav);
        assertTrue(vol > 0);
        double ar = MetricsMath.annualReturn(1.03, 3);
        assertEquals(ar / vol, MetricsMath.sharpe(ar, vol, 0.0), 1e-9);
    }
}
