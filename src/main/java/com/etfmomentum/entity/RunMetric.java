package com.etfmomentum.entity;

/** 回测指标(metrics.csv 的持久化形式,definition 列写明口径),对应表 run_metric */
public class RunMetric {

    private Long id;
    private String runId;
    private String seriesId;
    private String metric;
    private Double metricValue;    // null = NA(无法计算);列名 metric_value

    private String unit;
    private String definition;

    public RunMetric() {
    }

    public RunMetric(String runId, String seriesId, String metric, Double metricValue, String unit, String definition) {
        this.runId = runId;
        this.seriesId = seriesId;
        this.metric = metric;
        this.metricValue = metricValue;
        this.unit = unit;
        this.definition = definition;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
}
