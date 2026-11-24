package com.etfmomentum.controller;

import com.etfmomentum.service.DataQualityService;
import com.etfmomentum.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 分析入口(确定性计算 + AI 解释分离):
 * 数据体检、运行诊断、研究报告全部由 Java 业务层计算,AI 只负责解释这些结果。
 */
@RestController
public class AiAnalysisController {

    private final DataQualityService dataQualityService;
    private final ReportService reportService;

    public AiAnalysisController(DataQualityService dataQualityService, ReportService reportService) {
        this.dataQualityService = dataQualityService;
        this.reportService = reportService;
    }

    /** 行情数据体检(规则固定,评分由程序计算) */
    @PostMapping("/api/ai/data-quality/check")
    public Map<String, Object> checkDataQuality(@RequestParam(required = false, defaultValue = "0.15")
                                                double abnormalThreshold) {
        return dataQualityService.check(abnormalThreshold);
    }

    /** 运行结构化诊断:指标 + 回撤区间 + 成本 + 交易/持仓统计 */
    @GetMapping("/api/ai/analysis/run/{runId}")
    public Map<String, Object> analyzeRun(@PathVariable String runId) {
        return reportService.analyzeRun(runId);
    }

    /** 生成 Markdown 研究报告并落盘 results/{runId}/report.md */
    @PostMapping("/api/ai/reports/run/{runId}")
    public Map<String, Object> generateReport(@PathVariable String runId) {
        return reportService.generateReport(runId);
    }
}
