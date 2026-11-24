package com.etfmomentum.service;

import java.nio.file.Path;
import java.util.Set;

/** 业务层接口:运行结果导出(六件套 + decisions.csv + ZIP) */
public interface ExportService {

    /** 可导出的文件白名单 */
    Set<String> FILES = Set.of(
            "config.json", "run.json", "metrics.csv", "equity.csv",
            "positions.csv", "trades.csv", "decisions.csv", "report.md");

    Path runDir(String runId);

    /** 幂等写出该 run 的全部结果文件 */
    Path writeRunFiles(String runId);

    /** 打包 results/{runId}.zip */
    Path zipRun(String runId);

    Path exportFile(String runId, String name);

    void deleteRunFiles(String runId);
}
