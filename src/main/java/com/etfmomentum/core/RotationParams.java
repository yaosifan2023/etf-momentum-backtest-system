package com.etfmomentum.core;

import java.time.LocalDate;
import java.util.List;

/** 回测参数(由 StrategyConfig + 运行时覆盖项构成,即 config.json 的内容) */
public class RotationParams {

    public String strategyCode = "etf_mom_rotation";
    public String strategyVersion = "1.0.0";
    public String factorTableVersion = "v1";

    public List<String> pool;                 // 轮动候选池
    public String benchmarkAsset;             // 买入持有基准资产
    public LocalDate start;
    public LocalDate end;

    public double initialCash = 100000;

    public int momentumWindow = 20;           // W
    public int holdingCount = 2;              // K
    public String rebalanceFreq = "WEEKLY";   // WEEKLY / MONTHLY

    public boolean defensiveEnabled = true;
    public double defensiveThreshold = 0.0;   // 最强动量低于该值触发防御
    public String defensiveMode = "CASH";     // CASH / ASSET
    public String defensiveAsset;             // ASSET 模式使用的防御资产

    public boolean trendFilterEnabled = false;
    public int trendWindow = 20;              // d
    public double trendThreshold = 1.0;       // 均线比值 < 阈值才可入选

    public double commissionRate = 0.001;
    public double minimumCommission = 0.0;
    public double slippageRate = 0.0005;

    public String purpose = "historical_backtest";
    public String dataType = "historical";
    public String note = "";

    public String freqName() {
        return "MONTHLY".equals(rebalanceFreq) ? "每月" : "每周";
    }
}
