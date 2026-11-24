package com.etfmomentum.service;

import java.util.Map;

/** 业务层接口:回测研究报告与结构化诊断(全部数值来自数据库与程序计算) */
public interface ReportService {

    /** 生成某次运行的 Markdown 研究报告,并落盘 results/{runId}/report.md */
    Map<String, Object> generateReport(String runId);

    /** 结构化诊断:指标 + 回撤区间 + 成本 + 交易/持仓统计 + 防御触发统计 */
    Map<String, Object> analyzeRun(String runId);
}
