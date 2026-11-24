package com.etfmomentum.dto;

/** POST /api/strategies 请求体(新建策略配置版本) */
public class StrategySaveRequest {
    public String name;
    public Integer momentumWindow;
    public Integer holdingCount;
    public String rebalanceFreq;
    public Boolean defensiveEnabled;
    public Double defensiveThreshold;
    public String defensiveMode;
    public String defensiveAsset;
    public Boolean trendFilterEnabled;
    public Integer trendWindow;
    public Double trendThreshold;
    public Double commissionRate;
    public Double minimumCommission;
    public Double slippageRate;
    public Double initialCash;
    public String benchmarkAsset;
    public String universeNote;
    public String note;
}
