package com.etfmomentum;

import com.etfmomentum.core.FactorMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 因子计算基础测试 */
class FactorMathTest {

    @Test
    void momentumBasic() {
        double[] c = {100, 110, 120, 90};
        // close[3]/close[0]-1 = 0.9-1 = -0.1
        assertEquals(-0.1, FactorMath.momentum(c, 3, 3), 1e-12);
        // 窗口不足
        assertTrue(Double.isNaN(FactorMath.momentum(c, 2, 3)));
    }

    @Test
    void momentumWithInvalidPrice() {
        double[] c = {100, Double.NaN, 120, 130};
        // 动量只需两端价格:close[t] 与 close[t-W];窗口起点缺失 → NA
        assertTrue(Double.isNaN(FactorMath.momentum(c, 3, 2)));
        // 当前价缺失 → NA
        assertTrue(Double.isNaN(FactorMath.momentum(c, 1, 1)));
        // 两端有效时,中间缺失不影响(与 close[t]/close[t-W] 定义一致)
        assertEquals(0.3, FactorMath.momentum(c, 3, 3), 1e-12);
    }

    @Test
    void maRatioBasic() {
        // 指南 A 示例:10,10,10,10,12 → 均价10.4,比值 10.4/12
        double[] c = {10, 10, 10, 10, 12};
        assertEquals(10.4 / 12, FactorMath.maRatio(c, 4, 5), 1e-12);
        assertTrue(Double.isNaN(FactorMath.maRatio(c, 3, 5)));
        // 非正价格无效
        double[] bad = {10, 10, 10, 10, 0};
        assertTrue(Double.isNaN(FactorMath.maRatio(bad, 4, 5)));
    }

    @Test
    void volConstantSeriesIsZero() {
        double[] c = {100, 100, 100, 100, 100, 100};
        assertEquals(0.0, FactorMath.annualizedVol(c, 5, 5), 1e-12);
    }

    @Test
    void volAlternatingSeries() {
        // 涨10%、跌(1-1/1.1) 交替:窗口2(两个收益率)
        double[] c = {100, 110, 100, 110, 100};
        double r1 = 110.0 / 100 - 1;
        double r2 = 100.0 / 110 - 1;
        double mean = (r1 + r2) / 2;
        double var = ((r1 - mean) * (r1 - mean) + (r2 - mean) * (r2 - mean)) / (2 - 1);
        double expect = Math.sqrt(var) * Math.sqrt(252);
        assertEquals(expect, FactorMath.annualizedVol(c, 4, 2), 1e-9);
    }
}
