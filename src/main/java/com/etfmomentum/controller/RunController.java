package com.etfmomentum.controller;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.entity.BacktestRun;
import com.etfmomentum.entity.DecisionRecord;
import com.etfmomentum.entity.PositionSnapshot;
import com.etfmomentum.entity.TradeRecord;
import com.etfmomentum.dao.BacktestRunMapper;
import com.etfmomentum.dao.DecisionRecordMapper;
import com.etfmomentum.dao.EquityPointMapper;
import com.etfmomentum.dao.PositionSnapshotMapper;
import com.etfmomentum.dao.RunMetricMapper;
import com.etfmomentum.dao.TradeRecordMapper;
import com.etfmomentum.service.ExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 运行记录查询:结果详情、过程检查(信号/交易解释) */
@RestController
public class RunController {

    private final BacktestRunMapper runMapper;
    private final RunMetricMapper metricMapper;
    private final EquityPointMapper equityMapper;
    private final PositionSnapshotMapper positionMapper;
    private final TradeRecordMapper tradeMapper;
    private final DecisionRecordMapper decisionMapper;
    private final ExportService exportService;
    private final ObjectMapper om;

    public RunController(BacktestRunMapper runMapper, RunMetricMapper metricMapper, EquityPointMapper equityMapper,
                         PositionSnapshotMapper positionMapper, TradeRecordMapper tradeMapper,
                         DecisionRecordMapper decisionMapper, ExportService exportService, ObjectMapper om) {
        this.runMapper = runMapper;
        this.metricMapper = metricMapper;
        this.equityMapper = equityMapper;
        this.positionMapper = positionMapper;
        this.tradeMapper = tradeMapper;
        this.decisionMapper = decisionMapper;
        this.exportService = exportService;
        this.om = om;
    }

    @GetMapping("/api/runs")
    public List<BacktestRun> runs() {
        return runMapper.selectAllOrdered();
    }

    @GetMapping("/api/runs/{runId}")
    public Map<String, Object> detail(@PathVariable String runId) {
        BacktestRun run = load(runId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("run", run);
        res.put("config", parseJson(run.getConfigJson()));
        return res;
    }

    @GetMapping("/api/runs/{runId}/metrics")
    public Object metrics(@PathVariable String runId) {
        load(runId);
        return metricMapper.selectByRunId(runId);
    }

    @GetMapping("/api/runs/{runId}/equity")
    public Object equity(@PathVariable String runId) {
        load(runId);
        return equityMapper.selectByRunId(runId);
    }

    /** 不带 date:返回可选日期列表 + 最新一日持仓;带 date:返回该日持仓 */
    @GetMapping("/api/runs/{runId}/positions")
    public Map<String, Object> positions(@PathVariable String runId,
                                         @RequestParam(required = false) String date) {
        load(runId);
        List<PositionSnapshot> all = positionMapper.selectByRunId(runId);
        Map<String, Object> res = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>(new LinkedHashSet<>(all.stream()
                .filter(p -> "strategy".equals(p.getSeriesId()))
                .map(p -> p.getTradeDate().toString()).toList()));
        res.put("dates", dates);
        String target = date;
        if (target == null && !dates.isEmpty()) {
            target = dates.get(dates.size() - 1);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (target != null) {
            for (PositionSnapshot p : all) {
                if (target.equals(p.getTradeDate().toString())) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seriesId", p.getSeriesId());
                    row.put("date", p.getTradeDate().toString());
                    row.put("assetId", p.getAssetId());
                    row.put("quantity", p.getQuantity());
                    row.put("weight", p.getWeight());
                    rows.add(row);
                }
            }
        }
        res.put("date", target);
        res.put("rows", rows);
        return res;
    }

    @GetMapping("/api/runs/{runId}/trades")
    public Object trades(@PathVariable String runId) {
        load(runId);
        return tradeMapper.selectByRunId(runId);
    }

    @GetMapping("/api/runs/{runId}/decisions")
    public Object decisions(@PathVariable String runId) {
        load(runId);
        return decisionMapper.selectByRunId(runId);
    }

    /** 过程检查:某个信号日的决策明细 + 次日实际成交(为什么发生这笔交易) */
    @GetMapping("/api/runs/{runId}/explain")
    public Map<String, Object> explain(@PathVariable String runId, @RequestParam String date) {
        load(runId);
        LocalDate d;
        try {
            d = LocalDate.parse(date.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("日期格式错误,应为 YYYY-MM-DD");
        }
        DecisionRecord dec = decisionMapper.selectByRunIdAndSignalDate(runId, d);
        if (dec == null) {
            throw new BadRequestException("该日期无信号决策记录:" + date);
        }
        Map<String, Object> decision = parseJson(dec.getPayload());

        List<TradeRecord> trades = new ArrayList<>();
        Object execDate = decision.get("execution_date");
        for (TradeRecord t : tradeMapper.selectByRunId(runId)) {
            boolean hit;
            if (execDate != null) {
                // 轮动策略:取该成交日实际执行的交易
                hit = t.getExecutionTime() != null && t.getExecutionTime().startsWith(String.valueOf(execDate));
            } else {
                // 样本复算等不记录成交日的决策:取由该信号产生的交易
                hit = t.getSignalTime() != null && t.getSignalTime().startsWith(d.toString());
            }
            if (hit) {
                trades.add(t);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("decision", decision);
        res.put("trades", trades);
        res.put("defensiveTriggered", Boolean.TRUE.equals(dec.getDefensiveTriggered()));
        return res;
    }

    @Transactional
    @DeleteMapping("/api/runs/{runId}")
    public Map<String, Object> delete(@PathVariable String runId) {
        load(runId);
        metricMapper.deleteByRunId(runId);
        equityMapper.deleteByRunId(runId);
        positionMapper.deleteByRunId(runId);
        tradeMapper.deleteByRunId(runId);
        decisionMapper.deleteByRunId(runId);
        runMapper.deleteById(runId);
        exportService.deleteRunFiles(runId);
        return Map.of("ok", true, "message", "已删除运行 " + runId);
    }

    private BacktestRun load(String runId) {
        BacktestRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BadRequestException("运行记录不存在:" + runId);
        }
        return run;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return om.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
