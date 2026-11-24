package com.etfmomentum.entity;

/**
 * 因子定义 —— 按 A 阶段《因子清单》22 列规范整理(列名与作业指南第 04 节一致,
 * 页面按同一顺序展示,未采用的字段留空),对应表 factor_def。
 */
public class FactorDef {

    private String factorId;
    private String factorName;
    private String factorCategory;
    private String scope;             // 适用对象与计算范围
    private String meaning;           // 因子含义
    private String formula;           // 计算公式或步骤
    private String requiredData;      // 所需数据
    private String inputFreq;         // 输入数据频率
    private String calcFreq;          // 因子计算频率
    private String calcParams;        // 计算参数(统一五项格式)
    private String minHistory;        // 最少历史数据要求
    private String outputDesc;        // 输出名称、形式与单位
    private String direction;         // 方向假设
    private String dataCaliber;       // 数据口径
    private String missingPolicy;     // 缺失值处理
    private String outlierPolicy;     // 异常值处理
    private String standardization;   // 标准化处理
    private String neutralization;    // 中性化处理
    private String availability;      // 数据可用时间与滞后要求
    private String sourceName;        // 定义来源:资料名称
    private String sourceUrl;         // 定义来源:链接
    private String sourceDetail;      // 定义来源:章节/函数位置与核对日期
    private String candidateSource;   // 候选数据来源
    private String limits;            // 使用限制
    private String tableVersion;      // 因子表版本 v1

    public FactorDef() {
    }

    public String getFactorId() { return factorId; }
    public void setFactorId(String factorId) { this.factorId = factorId; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public String getFactorCategory() { return factorCategory; }
    public void setFactorCategory(String factorCategory) { this.factorCategory = factorCategory; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getMeaning() { return meaning; }
    public void setMeaning(String meaning) { this.meaning = meaning; }
    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }
    public String getRequiredData() { return requiredData; }
    public void setRequiredData(String requiredData) { this.requiredData = requiredData; }
    public String getInputFreq() { return inputFreq; }
    public void setInputFreq(String inputFreq) { this.inputFreq = inputFreq; }
    public String getCalcFreq() { return calcFreq; }
    public void setCalcFreq(String calcFreq) { this.calcFreq = calcFreq; }
    public String getCalcParams() { return calcParams; }
    public void setCalcParams(String calcParams) { this.calcParams = calcParams; }
    public String getMinHistory() { return minHistory; }
    public void setMinHistory(String minHistory) { this.minHistory = minHistory; }
    public String getOutputDesc() { return outputDesc; }
    public void setOutputDesc(String outputDesc) { this.outputDesc = outputDesc; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getDataCaliber() { return dataCaliber; }
    public void setDataCaliber(String dataCaliber) { this.dataCaliber = dataCaliber; }
    public String getMissingPolicy() { return missingPolicy; }
    public void setMissingPolicy(String missingPolicy) { this.missingPolicy = missingPolicy; }
    public String getOutlierPolicy() { return outlierPolicy; }
    public void setOutlierPolicy(String outlierPolicy) { this.outlierPolicy = outlierPolicy; }
    public String getStandardization() { return standardization; }
    public void setStandardization(String standardization) { this.standardization = standardization; }
    public String getNeutralization() { return neutralization; }
    public void setNeutralization(String neutralization) { this.neutralization = neutralization; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceDetail() { return sourceDetail; }
    public void setSourceDetail(String sourceDetail) { this.sourceDetail = sourceDetail; }
    public String getCandidateSource() { return candidateSource; }
    public void setCandidateSource(String candidateSource) { this.candidateSource = candidateSource; }
    public String getLimits() { return limits; }
    public void setLimits(String limits) { this.limits = limits; }
    public String getTableVersion() { return tableVersion; }
    public void setTableVersion(String tableVersion) { this.tableVersion = tableVersion; }
}
