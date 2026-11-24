package com.etfmomentum.entity;

import java.time.LocalDate;

/** 评价时点实际持仓(现金不在此表,见 equity_point.cash),对应表 position_snapshot */
public class PositionSnapshot {

    private Long id;
    private String runId;
    private String seriesId;
    private LocalDate tradeDate;
    private String assetId;
    private Double quantity;
    private Double weight;

    public PositionSnapshot() {
    }

    public PositionSnapshot(String runId, String seriesId, LocalDate tradeDate, String assetId, Double quantity, Double weight) {
        this.runId = runId;
        this.seriesId = seriesId;
        this.tradeDate = tradeDate;
        this.assetId = assetId;
        this.quantity = quantity;
        this.weight = weight;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
