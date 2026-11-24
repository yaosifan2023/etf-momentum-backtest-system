package com.etfmomentum;

import com.etfmomentum.core.BacktestResult;
import com.etfmomentum.core.Bar;
import com.etfmomentum.core.MaRatioLongCashBacktest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B 样本复算测试:验证引擎与《实验要求》6.6 手工演算完全一致。
 * 输入:DEMO_A 8 条虚构日线(3月3日~3月12日);
 * 预期(指南原文):3月10日开盘12元买入8320份,含滑点价12.006,手续费99.88992元,余现金10.19008元;
 * 3月11日因子恰等于1退出,3月12日开盘11元卖出,含滑点价10.9945,手续费91.47424元,现金91392.95584元;
 * 首日收盘净值0.9985019008;期末策略净值0.9139295584;基准净值0.9153019008。
 */
class SampleCheckTest {

    private static List<Bar> demoBars() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new Bar(LocalDate.parse("2025-03-03"), 10, 10));
        bars.add(new Bar(LocalDate.parse("2025-03-04"), 10, 10));
        bars.add(new Bar(LocalDate.parse("2025-03-05"), 10, 10));
        bars.add(new Bar(LocalDate.parse("2025-03-06"), 10, 10));
        bars.add(new Bar(LocalDate.parse("2025-03-07"), 10, 12));
        bars.add(new Bar(LocalDate.parse("2025-03-10"), 12, 12));
        bars.add(new Bar(LocalDate.parse("2025-03-11"), 12, 11));
        bars.add(new Bar(LocalDate.parse("2025-03-12"), 11, 11));
        return bars;
    }

    private static BacktestResult run() {
        return MaRatioLongCashBacktest.run(demoBars(),
                LocalDate.parse("2025-03-10"), LocalDate.parse("2025-03-12"),
                5, 1.0, 100000, 0.001, 0, 0.0005);
    }

    @Test
    void buyMatchesGuide() {
        BacktestResult r = run();
        BacktestResult.Trade buy = firstTrade(r, "buy");
        assertNotNull(buy, "应产生一笔买入");
        assertEquals(8320, (long) buy.quantity, "买入数量应为8320份");
        assertEquals(12.006, buy.price, 1e-9, "含滑点买价应为12.006");
        assertEquals(99.88992, buy.fee, 1e-6, "买入手续费应为99.88992元");
    }

    @Test
    void cashAfterBuyMatchesGuide() {
        BacktestResult r = run();
        BacktestResult.Trade buy = firstTrade(r, "buy");
        double cash = 100000 - buy.quantity * buy.price - buy.fee;
        assertEquals(10.19008, cash, 1e-6, "买入后现金应为10.19008元");
    }

    @Test
    void navFirstDayMatchesGuide() {
        BacktestResult r = run();
        BacktestResult.Eq lastOfDay = null;
        for (BacktestResult.Eq e : r.equity) {
            if ("strategy".equals(e.seriesId) && e.date.equals(LocalDate.parse("2025-03-10"))) {
                lastOfDay = e;   // 同日基线点之后是收盘点,取最后一个
            }
        }
        assertNotNull(lastOfDay);
        assertEquals(0.9985019008, lastOfDay.nav, 1e-9, "首日收盘净值应为0.9985019008(首日损益计入)");
        // 基线点(交易前)应为 1.0
        BacktestResult.Eq baseline = null;
        for (BacktestResult.Eq e : r.equity) {
            if ("strategy".equals(e.seriesId) && e.date.equals(LocalDate.parse("2025-03-10"))) {
                if (baseline == null) baseline = e;
            }
        }
        assertEquals(1.0, baseline.nav, 1e-12, "评价起点交易前净值应为1");
    }

    @Test
    void sellMatchesGuide() {
        BacktestResult r = run();
        BacktestResult.Trade sell = firstTrade(r, "sell");
        assertNotNull(sell, "应产生一笔卖出");
        assertEquals(8320, (long) sell.quantity, "卖出数量应为8320份");
        assertEquals(10.9945, sell.price, 1e-9, "含滑点卖价应为10.9945");
        assertEquals(91.47424, sell.fee, 1e-6, "卖出手续费应为91.47424元");
    }

    @Test
    void finalCashAndNavMatchGuide() {
        BacktestResult r = run();
        BacktestResult.Eq last = null;
        for (BacktestResult.Eq e : r.equity) {
            if ("strategy".equals(e.seriesId)) last = e;
        }
        assertNotNull(last);
        assertEquals(91392.95584, last.cash, 1e-6, "期末现金应为91392.95584元");
        assertEquals(0.9139295584, last.nav, 1e-9, "策略期末净值应为0.9139295584");

        BacktestResult.Eq benchLast = null;
        for (BacktestResult.Eq e : r.equity) {
            if ("benchmark".equals(e.seriesId)) benchLast = e;
        }
        assertEquals(0.9153019008, benchLast.nav, 1e-9, "基准期末净值应为0.9153019008");
    }

    @Test
    void factorValuesMatchGuide() {
        // 3月7日:10.4/12 ≈ 0.866667(指南A示例);3月11日恰好等于1(边界退出)
        List<Bar> bars = demoBars();
        double[] closes = new double[bars.size()];
        for (int i = 0; i < bars.size(); i++) closes[i] = bars.get(i).close;
        assertEquals(0.866667, com.etfmomentum.core.FactorMath.maRatio(closes, 4, 5), 1e-6);
        assertEquals(1.0, com.etfmomentum.core.FactorMath.maRatio(closes, 6, 5), 1e-12);
        // 3月6日:不足窗口 → NaN,不产生新目标
        assertTrue(Double.isNaN(com.etfmomentum.core.FactorMath.maRatio(closes, 3, 5)));
    }

    private static BacktestResult.Trade firstTrade(BacktestResult r, String side) {
        for (BacktestResult.Trade t : r.trades) {
            if ("strategy".equals(t.seriesId) && "FILLED".equals(t.status) && side.equals(t.side)) {
                return t;
            }
        }
        return null;
    }
}
