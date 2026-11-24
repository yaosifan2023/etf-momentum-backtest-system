package com.etfmomentum.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

/** 信号决策记录(过程检查用:因子值、排名、目标与防御触发情况),对应表 decision_record */
public class DecisionRecord {

    private Long id;
    private String runId;
    private LocalDate signalDate;
    private Boolean executed;            // 次日是否可执行(末日信号不执行)
    private Boolean defensiveTriggered;
    private String defensiveReason;

    /** 完整决策明细 JSON:资产因子值/排名/目标权重/成交日 */
    @JsonIgnore
    private String payload;

    public DecisionRecord() {
    }

    public DecisionRecord(String runId, LocalDate signalDate, Boolean executed, Boolean defensiveTriggered,
                          String defensiveReason, String payload) {
        this.runId = runId;
        this.signalDate = signalDate;
        this.executed = executed;
        this.defensiveTriggered = defensiveTriggered;
        this.defensiveReason = defensiveReason;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public LocalDate getSignalDate() { return signalDate; }
    public void setSignalDate(LocalDate signalDate) { this.signalDate = signalDate; }
    public Boolean getExecuted() { return executed; }
    public void setExecuted(Boolean executed) { this.executed = executed; }
    public Boolean getDefensiveTriggered() { return defensiveTriggered; }
    public void setDefensiveTriggered(Boolean defensiveTriggered) { this.defensiveTriggered = defensiveTriggered; }
    public String getDefensiveReason() { return defensiveReason; }
    public void setDefensiveReason(String defensiveReason) { this.defensiveReason = defensiveReason; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
