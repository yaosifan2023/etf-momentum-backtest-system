package com.etfmomentum.entity;

import java.time.LocalDate;

/** ETF 基础信息(资产池),对应表 etf_info */
public class EtfInfo {

    private String code;
    private String name;
    private String market;       // SH / SZ / DEMO
    private String secid;        // 东方财富 secid,如 1.510300
    private String category;     // 宽基 / 跨境 / 商品 / 债券 / 虚构
    private Boolean defensive;   // 是否防御资产
    private Boolean benchmark;   // 是否默认基准
    private Boolean enabled;     // 是否进入轮动候选池
    private String source;       // EASTMONEY / MANUAL_CSV / DEMO
    private LocalDate latestDate;
    private Integer barCount;
    private String note;

    public EtfInfo() {
    }

    public EtfInfo(String code, String name, String market, String secid, String category,
                   Boolean defensive, Boolean benchmark, Boolean enabled, String source, String note) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.secid = secid;
        this.category = category;
        this.defensive = defensive;
        this.benchmark = benchmark;
        this.enabled = enabled;
        this.source = source;
        this.note = note;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getSecid() { return secid; }
    public void setSecid(String secid) { this.secid = secid; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Boolean getDefensive() { return defensive; }
    public void setDefensive(Boolean defensive) { this.defensive = defensive; }
    public Boolean getBenchmark() { return benchmark; }
    public void setBenchmark(Boolean benchmark) { this.benchmark = benchmark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getLatestDate() { return latestDate; }
    public void setLatestDate(LocalDate latestDate) { this.latestDate = latestDate; }
    public Integer getBarCount() { return barCount; }
    public void setBarCount(Integer barCount) { this.barCount = barCount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
