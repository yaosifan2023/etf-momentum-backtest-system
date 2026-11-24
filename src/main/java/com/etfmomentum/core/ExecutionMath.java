package com.etfmomentum.core;

/**
 * 成交与成本口径(与 B/C 阶段策略说明一致):
 *  - 买价 = 开盘价 × (1 + 滑点率),卖价 = 开盘价 × (1 - 滑点率),滑点计入价格后不再重复扣费
 *  - 手续费 = max(成交金额 × 费率, 最低手续费),买卖双边收取
 *  - 买入数量 = floor(可用资金 / (含滑点买价 × (1 + 费率))),余款留现金
 */
public final class ExecutionMath {

    private ExecutionMath() {
    }

    public static double buyFillPrice(double open, double slippageRate) {
        return open * (1.0 + slippageRate);
    }

    public static double sellFillPrice(double open, double slippageRate) {
        return open * (1.0 - slippageRate);
    }

    public static double fee(double notional, double commissionRate, double minimumCommission) {
        return Math.max(notional * commissionRate, minimumCommission);
    }

    /** 按可用资金计算可负担的最大整数份数 */
    public static long maxAffordableShares(double cash, double fillPrice, double commissionRate) {
        if (!(fillPrice > 0) || !(cash > 0)) {
            return 0;
        }
        double perShare = fillPrice * (1.0 + commissionRate);
        if (!(perShare > 0)) {
            return 0;
        }
        return (long) Math.floor(cash / perShare + 1e-9);
    }
}
