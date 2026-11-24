package com.etfmomentum.entity;

/** 交易记录(成交与未成交尝试均记录;trades.csv 的持久化形式),对应表 trade_record */
public class TradeRecord {

    private Long id;
    private String runId;
    private String seriesId;
    private String signalTime;      // ISO 8601 带时区,如 2025-03-07T15:05:00+08:00
    private String executionTime;   // 如 2025-03-10T09:30:00+08:00;未下单为空
    private String assetId;
    private String side;            // buy / sell
    private Double quantity;
    private Double price;           // 含滑点成交价;滑点已计入价格,不重复扣费
    private Double fee;
    private Double slippageCost;    // 分析列:|成交价-开盘价|×数量
    private String status;          // FILLED / UNFILLED
    private String reason;

    public TradeRecord() {
    }

    public TradeRecord(String runId, String seriesId, String signalTime, String executionTime, String assetId,
                       String side, Double quantity, Double price, Double fee, Double slippageCost,
                       String status, String reason) {
        this.runId = runId;
        this.seriesId = seriesId;
        this.signalTime = signalTime;
        this.executionTime = executionTime;
        this.assetId = assetId;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.slippageCost = slippageCost;
        this.status = status;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public String getSignalTime() { return signalTime; }
    public void setSignalTime(String signalTime) { this.signalTime = signalTime; }
    public String getExecutionTime() { return executionTime; }
    public void setExecutionTime(String executionTime) { this.executionTime = executionTime; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getFee() { return fee; }
    public void setFee(Double fee) { this.fee = fee; }
    public Double getSlippageCost() { return slippageCost; }
    public void setSlippageCost(Double slippageCost) { this.slippageCost = slippageCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
