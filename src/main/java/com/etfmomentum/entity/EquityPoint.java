package com.etfmomentum.entity;

import java.time.LocalDate;

/** 净值曲线:nav = 账户权益 / 评价起点交易前权益(指南 7.3 口径,初始净值 1 参与回撤),对应表 equity_point */
public class EquityPoint {

    private Long id;
    private String runId;
    private String seriesId;     // strategy / benchmark
    private LocalDate tradeDate;
    private Double nav;
    private Double cash;
    private Double positionValue;

    public EquityPoint() {
    }

    public EquityPoint(String runId, String seriesId, LocalDate tradeDate, Double nav, Double cash, Double positionValue) {
        this.runId = runId;
        this.seriesId = seriesId;
        this.tradeDate = tradeDate;
        this.nav = nav;
        this.cash = cash;
        this.positionValue = positionValue;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public Double getNav() { return nav; }
    public void setNav(Double nav) { this.nav = nav; }
    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }
    public Double getPositionValue() { return positionValue; }
    public void setPositionValue(Double positionValue) { this.positionValue = positionValue; }
}
