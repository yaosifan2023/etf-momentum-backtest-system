package com.etfmomentum.service.impl;
import com.etfmomentum.service.BacktestService;
import com.etfmomentum.service.ExportService;
import com.etfmomentum.service.FactorService;
import com.etfmomentum.service.MarketDataService;

import com.etfmomentum.entity.BacktestRun;
import com.etfmomentum.entity.DecisionRecord;
import com.etfmomentum.entity.EquityPoint;
import com.etfmomentum.entity.PositionSnapshot;
import com.etfmomentum.entity.RunMetric;
import com.etfmomentum.entity.TradeRecord;
import com.etfmomentum.dao.BacktestRunMapper;
import com.etfmomentum.dao.DecisionRecordMapper;
import com.etfmomentum.dao.EquityPointMapper;
import com.etfmomentum.dao.PositionSnapshotMapper;
import com.etfmomentum.dao.RunMetricMapper;
import com.etfmomentum.dao.TradeRecordMapper;
import com.etfmomentum.common.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 运行结果导出:按作业指南第 08 节固定列写出六个文件(+decisions.csv)到 results/{run_id}/,
 * 并提供打包下载。CSV 为 UTF-8、首行英文表头、数值不带单位后缀。
 */
@Service
public class ExportServiceImpl implements ExportService {

    private final BacktestRunMapper runMapper;
    private final RunMetricMapper metricMapper;
    private final EquityPointMapper equityMapper;
    private final PositionSnapshotMapper positionMapper;
    private final TradeRecordMapper tradeMapper;
    private final DecisionRecordMapper decisionMapper;
    private final ObjectMapper om;

    @Value("${app.results-dir:./results}")
    private String resultsDir;

    public ExportServiceImpl(BacktestRunMapper runMapper, RunMetricMapper metricMapper, EquityPointMapper equityMapper,
                         PositionSnapshotMapper positionMapper, TradeRecordMapper tradeMapper,
                         DecisionRecordMapper decisionMapper, ObjectMapper om) {
        this.runMapper = runMapper;
        this.metricMapper = metricMapper;
        this.equityMapper = equityMapper;
        this.positionMapper = positionMapper;
        this.tradeMapper = tradeMapper;
        this.decisionMapper = decisionMapper;
        this.om = om;
    }

    @Override
    public Path runDir(String runId) {
        return Paths.get(resultsDir, runId);
    }

    /** 幂等写出该 run 的全部结果文件 */
    @Override
    public Path writeRunFiles(String runId) {
        BacktestRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BadRequestException("运行记录不存在:" + runId);
        }
        try {
            Path dir = runDir(runId);
            Files.createDirectories(dir);

            Files.writeString(dir.resolve("config.json"),
                    run.getConfigJson() != null ? run.getConfigJson() : "{}", StandardCharsets.UTF_8);

            Files.writeString(dir.resolve("run.json"), buildRunJson(run), StandardCharsets.UTF_8);

            StringBuilder sb = new StringBuilder("run_id,series_id,metric,value,unit,definition\n");
            for (RunMetric m : metricMapper.selectByRunId(runId)) {
                sb.append(join(runId, m.getSeriesId(), m.getMetric(),
                        m.getMetricValue() == null ? "NA" : num(m.getMetricValue(), 10), m.getUnit(), m.getDefinition()));
            }
            Files.writeString(dir.resolve("metrics.csv"), sb.toString(), StandardCharsets.UTF_8);

            sb = new StringBuilder("run_id,series_id,date,nav,cash,position_value\n");
            for (EquityPoint e : equityMapper.selectByRunId(runId)) {
                sb.append(join(runId, e.getSeriesId(), e.getTradeDate().toString(),
                        num(e.getNav(), 10), num(e.getCash(), 6), num(e.getPositionValue(), 6)));
            }
            Files.writeString(dir.resolve("equity.csv"), sb.toString(), StandardCharsets.UTF_8);

            sb = new StringBuilder("run_id,series_id,date,asset_id,quantity,weight\n");
            for (PositionSnapshot ps : positionMapper.selectByRunId(runId)) {
                sb.append(join(runId, ps.getSeriesId(), ps.getTradeDate().toString(),
                        ps.getAssetId(), num(ps.getQuantity(), 0), num(ps.getWeight(), 6)));
            }
            Files.writeString(dir.resolve("positions.csv"), sb.toString(), StandardCharsets.UTF_8);

            sb = new StringBuilder("run_id,series_id,signal_time,execution_time,asset_id,side,quantity,price,fee,slippage_cost,status,reason\n");
            for (TradeRecord t : tradeMapper.selectByRunId(runId)) {
                sb.append(join(runId, t.getSeriesId(), nz(t.getSignalTime()), nz(t.getExecutionTime()),
                        t.getAssetId(), t.getSide(), num(t.getQuantity(), 0), num(t.getPrice(), 6),
                        num(t.getFee(), 6), num(t.getSlippageCost(), 6), t.getStatus(), t.getReason()));
            }
            Files.writeString(dir.resolve("trades.csv"), sb.toString(), StandardCharsets.UTF_8);

            sb = new StringBuilder("run_id,signal_date,executed,defensive_triggered,defensive_reason,payload\n");
            for (DecisionRecord d : decisionMapper.selectByRunId(runId)) {
                sb.append(join(runId, d.getSignalDate().toString(),
                        Boolean.TRUE.equals(d.getExecuted()) ? "true" : "false",
                        Boolean.TRUE.equals(d.getDefensiveTriggered()) ? "true" : "false",
                        nz(d.getDefensiveReason()), d.getPayload()));
            }
            Files.writeString(dir.resolve("decisions.csv"), sb.toString(), StandardCharsets.UTF_8);

            return dir;
        } catch (IOException e) {
            throw new RuntimeException("导出结果文件失败:" + e.getMessage(), e);
        }
    }

    /** 打包 results/{runId}.zip */
    @Override
    public Path zipRun(String runId) {
        Path dir = writeRunFiles(runId);
        Path zip = Paths.get(resultsDir, runId + ".zip");
        try (OutputStream os = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.sorted().forEach(files::add);
            }
            for (Path f : files) {
                zos.putNextEntry(new ZipEntry(f.getFileName().toString()));
                zos.write(Files.readAllBytes(f));
                zos.closeEntry();
            }
            return zip;
        } catch (IOException e) {
            throw new RuntimeException("打包失败:" + e.getMessage(), e);
        }
    }

    @Override
    public Path exportFile(String runId, String name) {
        if (!FILES.contains(name)) {
            throw new BadRequestException("不支持的导出文件:" + name);
        }
        writeRunFiles(runId);
        Path p = runDir(runId).resolve(name);
        if (!Files.exists(p)) {
            throw new BadRequestException("文件不存在:" + name);
        }
        return p;
    }

    @Override
    public void deleteRunFiles(String runId) {
        try {
            Path dir = runDir(runId);
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path f : stream.toList()) {
                        Files.deleteIfExists(f);
                    }
                }
                Files.deleteIfExists(dir);
            }
            Files.deleteIfExists(Paths.get(resultsDir, runId + ".zip"));
        } catch (IOException e) {
            throw new RuntimeException("清理结果文件失败:" + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildRunJson(BacktestRun run) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (run.getConfigJson() != null) {
            try {
                cfg = om.readValue(run.getConfigJson(), Map.class);
            } catch (IOException ignore) {
                // 保底:空配置
            }
        }
        Map<String, Object> data = (Map<String, Object>) cfg.getOrDefault("data", new LinkedHashMap<>());
        Map<String, Object> period = (Map<String, Object>) cfg.getOrDefault("period", new LinkedHashMap<>());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema_version", "1.0");
        out.put("run_id", run.getRunId());
        out.put("purpose", run.getPurpose());
        out.put("data_type", run.getDataType());
        out.put("strategy_id", run.getStrategyCode());
        out.put("strategy_version", run.getStrategyVersion());
        out.put("config_path", "config.json");
        out.put("dataset_id", data.get("dataset_id"));
        out.put("data_version", data.get("data_version"));
        out.put("universe_snapshot", data.get("asset_ids"));
        out.put("period", period);
        out.put("split", mapOf(
                "parameter_selection_period", "NA,规则与参数为预先固定的教学约定,未做选择区间优化",
                "evaluation_period", (run.getStartDate() != null ? run.getStartDate().toString() : "")
                        + "~" + (run.getEndDate() != null ? run.getEndDate().toString() : "")));
        out.put("benchmark", run.getBenchmark());
        out.put("cost_model", "完整成本参数保存于config.json的costs;滑点计入成交价,不重复扣费");
        out.put("execution", "T日15:05收盘数据可用后产生信号,T+1 09:30开盘成交(时区+08:00);固定成交规则见策略代码");
        out.put("command", "启动系统.bat 或 mvn spring-boot:run(详见README.txt)");
        out.put("code_version", "1.0.0 源码(src/);提交时以源文件哈希为准");
        out.put("environment", mapOf(
                "runtime", "Java " + System.getProperty("java.version"),
                "dependencies_file", "pom.xml",
                "operating_system", System.getProperty("os.name") + " " + System.getProperty("os.version")));
        out.put("seed", "NA");
        out.put("status", run.getStatus());
        out.put("outputs", mapOf(
                "metrics", "metrics.csv", "equity", "equity.csv", "positions", "positions.csv",
                "trades", "trades.csv", "signals", "decisions.csv"));
        out.put("note", run.getNote() != null ? run.getNote() : "");
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String nz(String s) {
        return s == null ? "NA" : s;
    }

    private static String num(Double v, int dp) {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
            return "NA";
        }
        return new BigDecimal(v).setScale(dp, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String join(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i] == null ? "NA" : String.valueOf(cells[i])));
        }
        return sb.append('\n').toString();
    }

    private static String escape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
