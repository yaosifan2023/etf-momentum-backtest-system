package com.etfmomentum.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 回测运行记录(run.json 的持久化形式;configJson 为程序实际读取的配置快照),对应表 backtest_run */
public class BacktestRun {

    private String runId;
    private String purpose;        // historical_backtest / sample_check
    private String status;         // SUCCESS / FAILED
    private String dataType;       // historical / synthetic
    private String strategyCode;
    private String strategyVersion;
    private Long strategyConfigId;

    @JsonIgnore
    private String configJson;     // config.json 快照(导出时原样写出)

    private LocalDate startDate;
    private LocalDate endDate;
    private Double initialCash;
    private String benchmark;      // 基准说明,如 buy_and_hold(510300)

    @JsonIgnore
    private String message;

    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public BacktestRun() {
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getStrategyCode() { return strategyCode; }
    public void setStrategyCode(String strategyCode) { this.strategyCode = strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public void setStrategyVersion(String strategyVersion) { this.strategyVersion = strategyVersion; }
    public Long getStrategyConfigId() { return strategyConfigId; }
    public void setStrategyConfigId(Long strategyConfigId) { this.strategyConfigId = strategyConfigId; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Double getInitialCash() { return initialCash; }
    public void setInitialCash(Double initialCash) { this.initialCash = initialCash; }
    public String getBenchmark() { return benchmark; }
    public void setBenchmark(String benchmark) { this.benchmark = benchmark; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
