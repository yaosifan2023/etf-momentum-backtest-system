package com.etfmomentum.dto;

/** POST /api/backtest/run 请求体 */
public class RunRequest {
    public Long configId;
    public String start;
    public String end;
    public String purpose;                 // historical_backtest(默认)
    public Boolean zeroCost;               // 附加对照:零费率零滑点
    public String note;
    public Integer overrideMomentumWindow; // 参数敏感性附加实验(可选)
    public Integer overrideHoldingCount;
    public String overrideRebalanceFreq;   // WEEKLY / MONTHLY
    public Boolean overrideDefensiveEnabled;
}
