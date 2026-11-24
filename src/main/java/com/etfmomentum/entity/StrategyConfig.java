package com.etfmomentum.entity;

import java.time.LocalDateTime;

/** 策略配置(参数化版本,对应 B_主策略.json 的机器可读形式),对应表 strategy_config */
public class StrategyConfig {

    private Long id;
    private String strategyCode;     // etf_mom_rotation
    private String name;
    private Integer version;         // 同名递增
    private Integer momentumWindow;  // W:动量窗口(交易日)
    private Integer holdingCount;    // K:持有数量
    private String rebalanceFreq;    // WEEKLY / MONTHLY
    private Boolean defensiveEnabled;
    private Double defensiveThreshold; // 最强动量低于该值触发防御
    private String defensiveMode;    // CASH / ASSET
    private String defensiveAsset;   // defensiveMode=ASSET 时的防御资产
    private Boolean trendFilterEnabled;
    private Integer trendWindow;     // 均线比值窗口 d
    private Double trendThreshold;   // 均线比值需小于该值才可入选
    private Double commissionRate;   // 双边手续费率(成交金额比例)
    private Double minimumCommission;// 最低手续费(元)
    private Double slippageRate;     // 滑点率(计入成交价)
    private Double initialCash;      // 初始资金(元)
    private String benchmarkAsset;   // 买入持有基准
    private String universeNote;     // 交易对象说明
    private String note;
    private String paramsJson;       // config.json 的 strategy+costs 快照
    private LocalDateTime createdAt;

    public StrategyConfig() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStrategyCode() { return strategyCode; }
    public void setStrategyCode(String strategyCode) { this.strategyCode = strategyCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getMomentumWindow() { return momentumWindow; }
    public void setMomentumWindow(Integer momentumWindow) { this.momentumWindow = momentumWindow; }
    public Integer getHoldingCount() { return holdingCount; }
    public void setHoldingCount(Integer holdingCount) { this.holdingCount = holdingCount; }
    public String getRebalanceFreq() { return rebalanceFreq; }
    public void setRebalanceFreq(String rebalanceFreq) { this.rebalanceFreq = rebalanceFreq; }
    public Boolean getDefensiveEnabled() { return defensiveEnabled; }
    public void setDefensiveEnabled(Boolean defensiveEnabled) { this.defensiveEnabled = defensiveEnabled; }
    public Double getDefensiveThreshold() { return defensiveThreshold; }
    public void setDefensiveThreshold(Double defensiveThreshold) { this.defensiveThreshold = defensiveThreshold; }
    public String getDefensiveMode() { return defensiveMode; }
    public void setDefensiveMode(String defensiveMode) { this.defensiveMode = defensiveMode; }
    public String getDefensiveAsset() { return defensiveAsset; }
    public void setDefensiveAsset(String defensiveAsset) { this.defensiveAsset = defensiveAsset; }
    public Boolean getTrendFilterEnabled() { return trendFilterEnabled; }
    public void setTrendFilterEnabled(Boolean trendFilterEnabled) { this.trendFilterEnabled = trendFilterEnabled; }
    public Integer getTrendWindow() { return trendWindow; }
    public void setTrendWindow(Integer trendWindow) { this.trendWindow = trendWindow; }
    public Double getTrendThreshold() { return trendThreshold; }
    public void setTrendThreshold(Double trendThreshold) { this.trendThreshold = trendThreshold; }
    public Double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(Double commissionRate) { this.commissionRate = commissionRate; }
    public Double getMinimumCommission() { return minimumCommission; }
    public void setMinimumCommission(Double minimumCommission) { this.minimumCommission = minimumCommission; }
    public Double getSlippageRate() { return slippageRate; }
    public void setSlippageRate(Double slippageRate) { this.slippageRate = slippageRate; }
    public Double getInitialCash() { return initialCash; }
    public void setInitialCash(Double initialCash) { this.initialCash = initialCash; }
    public String getBenchmarkAsset() { return benchmarkAsset; }
    public void setBenchmarkAsset(String benchmarkAsset) { this.benchmarkAsset = benchmarkAsset; }
    public String getUniverseNote() { return universeNote; }
    public void setUniverseNote(String universeNote) { this.universeNote = universeNote; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
