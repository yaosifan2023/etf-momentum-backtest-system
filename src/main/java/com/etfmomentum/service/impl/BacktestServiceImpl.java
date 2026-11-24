package com.etfmomentum.service.impl;
import com.etfmomentum.service.BacktestService;
import com.etfmomentum.service.ExportService;
import com.etfmomentum.service.FactorService;
import com.etfmomentum.service.MarketDataService;

import com.etfmomentum.core.BacktestResult;
import com.etfmomentum.core.Bar;
import com.etfmomentum.core.FactorMath;
import com.etfmomentum.core.MaRatioLongCashBacktest;
import com.etfmomentum.core.RotationBacktest;
import com.etfmomentum.core.RotationParams;
import com.etfmomentum.entity.BacktestRun;
import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.entity.DecisionRecord;
import com.etfmomentum.entity.EtfInfo;
import com.etfmomentum.entity.EquityPoint;
import com.etfmomentum.entity.PositionSnapshot;
import com.etfmomentum.entity.RunMetric;
import com.etfmomentum.entity.StrategyConfig;
import com.etfmomentum.entity.TradeRecord;
import com.etfmomentum.dao.BacktestRunMapper;
import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.dao.DecisionRecordMapper;
import com.etfmomentum.dao.EtfInfoMapper;
import com.etfmomentum.dao.EquityPointMapper;
import com.etfmomentum.dao.PositionSnapshotMapper;
import com.etfmomentum.dao.RunMetricMapper;
import com.etfmomentum.dao.StrategyConfigMapper;
import com.etfmomentum.dao.TradeRecordMapper;
import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.dto.RunRequest;
import com.etfmomentum.service.ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 回测编排:校验数据 → 运行引擎 → 持久化 → 导出结果文件 */
@Service
public class BacktestServiceImpl implements BacktestService {

    private final StrategyConfigMapper strategyMapper;
    private final EtfInfoMapper etfMapper;
    private final DailyBarMapper barMapper;
    private final BacktestRunMapper runMapper;
    private final EquityPointMapper equityMapper;
    private final PositionSnapshotMapper positionMapper;
    private final TradeRecordMapper tradeMapper;
    private final DecisionRecordMapper decisionMapper;
    private final RunMetricMapper metricMapper;
    private final ExportService exportService;
    private final ObjectMapper om;

    public BacktestServiceImpl(StrategyConfigMapper strategyMapper, EtfInfoMapper etfMapper, DailyBarMapper barMapper,
                           BacktestRunMapper runMapper, EquityPointMapper equityMapper, PositionSnapshotMapper positionMapper,
                           TradeRecordMapper tradeMapper, DecisionRecordMapper decisionMapper, RunMetricMapper metricMapper,
                           ExportService exportService, ObjectMapper om) {
        this.strategyMapper = strategyMapper;
        this.etfMapper = etfMapper;
        this.barMapper = barMapper;
        this.runMapper = runMapper;
        this.equityMapper = equityMapper;
        this.positionMapper = positionMapper;
        this.tradeMapper = tradeMapper;
        this.decisionMapper = decisionMapper;
        this.metricMapper = metricMapper;
        this.exportService = exportService;
        this.om = om;
    }

    // ==================== 历史回测 ====================

    @Override
    @Transactional
    public Map<String, Object> runBacktest(RunRequest req) {
        if (req.configId == null) {
            throw new BadRequestException("请选择策略配置");
        }
        StrategyConfig cfg = strategyMapper.selectById(req.configId);
        if (cfg == null) {
            throw new BadRequestException("策略配置不存在:id=" + req.configId);
        }
        if (req.start == null || req.end == null) {
            throw new BadRequestException("请填写回测起止日期(YYYY-MM-DD)");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.start.trim());
            end = LocalDate.parse(req.end.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("日期格式错误,应为 YYYY-MM-DD");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("开始日期必须早于结束日期");
        }

        // 候选池:启用且非虚构的资产;学术诚信:真实回测不得使用 DEMO 数据
        List<EtfInfo> poolEtfs = new ArrayList<>();
        for (EtfInfo e : etfMapper.selectEnabled()) {
            if (!"DEMO".equals(e.getSource())) {
                poolEtfs.add(e);
            }
        }
        if (poolEtfs.size() < 2) {
            throw new BadRequestException("候选池不足 2 只真实 ETF,请先在【数据管理】页下载行情");
        }

        RotationParams p = paramsFrom(cfg, req, start, end);
        List<String> poolCodes = new ArrayList<>();
        for (EtfInfo e : poolEtfs) {
            poolCodes.add(e.getCode());
        }
        p.pool = poolCodes;

        // 数据加载(含预热)
        int maxWin = Math.max(p.momentumWindow, p.trendFilterEnabled ? p.trendWindow : 0);
        LocalDate warmupStart = start.minusDays((maxWin + 20L) * 2L);
        LinkedHashSet<String> needCodes = new LinkedHashSet<>();
        for (EtfInfo e : poolEtfs) {
            needCodes.add(e.getCode());
        }
        needCodes.add(p.benchmarkAsset);
        if ("ASSET".equals(p.defensiveMode) && p.defensiveAsset != null) {
            needCodes.add(p.defensiveAsset);
        }

        Map<String, List<Bar>> data = new LinkedHashMap<>();
        LocalDate dataLatest = null;
        for (String code : needCodes) {
            List<Bar> bars = new ArrayList<>();
            for (DailyBar b : barMapper.selectByCodeBetween(code, warmupStart, end)) {
                if (b.getOpen() != null && b.getClose() != null) {
                    bars.add(new Bar(b.getTradeDate(), b.getOpen(), b.getClose()));
                    if (dataLatest == null || b.getTradeDate().isAfter(dataLatest)) {
                        dataLatest = b.getTradeDate();
                    }
                }
            }
            data.put(code, bars);
        }
        if (data.get(p.benchmarkAsset) == null || data.get(p.benchmarkAsset).isEmpty()) {
            throw new BadRequestException("基准资产 " + p.benchmarkAsset + " 无行情数据,请先在【数据管理】页下载");
        }
        int withData = 0;
        for (String code : p.pool) {
            List<Bar> bars = data.get(code);
            if (bars != null && !bars.isEmpty()) {
                withData++;
            }
        }
        if (withData < 2) {
            throw new BadRequestException("候选池中有行情数据的 ETF 不足 2 只,请先在【数据管理】页下载");
        }
        if (dataLatest == null || dataLatest.isBefore(start)) {
            throw new BadRequestException("行情数据未覆盖回测开始日期 " + start + ",请调整区间或更新数据");
        }

        // config.json 快照(程序实际读取的参数)
        String configJson = buildConfigJson(p, poolCodes, "fqt1_" + dataLatest);

        String runId = genId("run");
        BacktestRun run = new BacktestRun();
        run.setRunId(runId);
        run.setPurpose("historical_backtest");
        run.setStatus("RUNNING");
        run.setDataType("historical");
        run.setStrategyCode(p.strategyCode);
        run.setStrategyVersion(p.strategyVersion);
        run.setStrategyConfigId(cfg.getId());
        run.setConfigJson(configJson);
        run.setStartDate(start);
        run.setEndDate(end);
        run.setInitialCash(p.initialCash);
        run.setBenchmark("buy_and_hold(" + p.benchmarkAsset + ")");
        run.setNote(p.note);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        BacktestResult result;
        try {
            result = RotationBacktest.run(p, data);
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setMessage("引擎执行失败:" + ex.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateStatus(run);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("runId", runId);
            res.put("status", "FAILED");
            res.put("message", run.getMessage());
            return res;
        }

        persistRows(runId, result);

        int tradeCount = 0;
        double totalFee = 0;
        double lastNav = 1;
        double benchNav = 1;
        for (BacktestResult.Trade t : result.trades) {
            if ("strategy".equals(t.seriesId) && "FILLED".equals(t.status)) {
                tradeCount++;
                totalFee += t.fee != null ? t.fee : 0;
            }
        }
        for (BacktestResult.Eq e : result.equity) {
            if ("strategy".equals(e.seriesId)) {
                lastNav = e.nav;
            } else if ("benchmark".equals(e.seriesId)) {
                benchNav = e.nav;
            }
        }

        run.setStatus("SUCCESS");
        run.setMessage(String.format("回测完成:共 %d 个净值点;策略成交 %d 笔,总手续费 %.2f 元;期末策略净值 %.4f,基准 %.4f",
                result.equity.size() / 2, tradeCount, totalFee, lastNav, benchNav));
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateStatus(run);

        exportService.writeRunFiles(runId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("runId", runId);
        res.put("status", "SUCCESS");
        res.put("message", run.getMessage());
        res.put("resultUrl", "result.html?run_id=" + runId);
        return res;
    }

    // ==================== B 样本复算 ====================

    /** 复现《实验要求》6.6/7.5 的 DEMO_A 演算,并输出预期值对照 */
    @Override
    @Transactional
    public Map<String, Object> runSampleCheck() {
        List<Bar> bars = new ArrayList<>();
        for (DailyBar b : barMapper.selectByCodeOrderByDateAsc("DEMO_A")) {
            bars.add(new Bar(b.getTradeDate(), b.getOpen(), b.getClose()));
        }
        if (bars.size() < 8) {
            throw new BadRequestException("示例数据缺失:请重启应用以重新初始化 DEMO_A 教学样本");
        }

        LocalDate start = LocalDate.parse("2025-03-10");
        LocalDate end = LocalDate.parse("2025-03-12");
        double initialCash = 100000;
        double rate = 0.001;
        double min = 0;
        double slip = 0.0005;

        BacktestResult result = MaRatioLongCashBacktest.run(bars, start, end, 5, 1.0, initialCash, rate, min, slip);

        String runId = genId("sample");
        RotationParams p = new RotationParams();
        p.strategyCode = "demo_trend";
        p.strategyVersion = "1.0.0";
        p.start = start;
        p.end = end;
        p.initialCash = initialCash;
        p.commissionRate = rate;
        p.minimumCommission = min;
        p.slippageRate = slip;
        p.purpose = "sample_check";
        p.dataType = "synthetic";
        p.note = "B 阶段小样本复算(虚构教学数据 DEMO_A),验证引擎成交/成本/净值口径与手工演算一致";

        Map<String, Object> cfgMap = sampleConfigJson(start, end, initialCash, rate, min, slip);
        String configJson;
        try {
            configJson = om.writerWithDefaultPrettyPrinter().writeValueAsString(cfgMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        BacktestRun run = new BacktestRun();
        run.setRunId(runId);
        run.setPurpose("sample_check");
        run.setStatus("SUCCESS");
        run.setDataType("synthetic");
        run.setStrategyCode("demo_trend");
        run.setStrategyVersion("1.0.0");
        run.setConfigJson(configJson);
        run.setStartDate(start);
        run.setEndDate(end);
        run.setInitialCash(initialCash);
        run.setBenchmark("buy_and_hold(DEMO_A)");
        run.setNote(p.note);
        run.setCreatedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);

        persistRows(runId, result);
        exportService.writeRunFiles(runId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("runId", runId);
        res.put("status", "SUCCESS");
        res.put("resultUrl", "result.html?run_id=" + runId);
        res.put("checks", buildChecks(result, bars));
        res.put("message", "样本复算完成,详见对照表");
        return res;
    }

    /** 预期值来自《实验要求》6.6 的手工演算 */
    private List<Map<String, Object>> buildChecks(BacktestResult result, List<Bar> bars) {
        BacktestResult.Trade buy = null;
        BacktestResult.Trade sell = null;
        for (BacktestResult.Trade t : result.trades) {
            if (!"strategy".equals(t.seriesId) || !"FILLED".equals(t.status)) {
                continue;
            }
            if ("buy".equals(t.side) && buy == null) {
                buy = t;
            }
            if ("sell".equals(t.side) && sell == null) {
                sell = t;
            }
        }
        double nav0310 = Double.NaN;
        double cashEnd = Double.NaN;
        double navEnd = Double.NaN;
        double benchEnd = Double.NaN;
        BacktestResult.Eq last0310 = null;
        for (BacktestResult.Eq e : result.equity) {
            if (!"strategy".equals(e.seriesId)) {
                continue;
            }
            if (e.date.equals(LocalDate.parse("2025-03-10"))) {
                last0310 = e;
            }
            cashEnd = e.cash;
            navEnd = e.nav;
        }
        for (BacktestResult.Eq e : result.equity) {
            if ("benchmark".equals(e.seriesId)) {
                benchEnd = e.nav;
            }
        }
        if (last0310 != null) {
            nav0310 = last0310.nav;
        }

        double[] closes = new double[bars.size()];
        for (int i = 0; i < bars.size(); i++) {
            closes[i] = bars.get(i).close;
        }
        double f0307 = FactorMath.maRatio(closes, 4, 5);
        double f0311 = FactorMath.maRatio(closes, 6, 5);

        List<Map<String, Object>> checks = new ArrayList<>();
        add(checks, "因子值 2025-03-07(5日均线比值)", 0.866667, f0307, 1e-6, "");
        add(checks, "因子值 2025-03-11(恰好等于阈值)", 1.0, f0311, 1e-9, "边界情况:>=阈值退出");
        add(checks, "买入数量(份,2025-03-10)", 8320, buy != null ? buy.quantity : Double.NaN, 0.5, "");
        add(checks, "买入成交价(含滑点,元)", 12.006, buy != null ? buy.price : Double.NaN, 1e-9, "12×1.0005");
        add(checks, "买入手续费(元)", 99.88992, buy != null ? buy.fee : Double.NaN, 1e-6, "8320×12.006×0.001");
        add(checks, "买入后剩余现金(元)", 10.19008, buy != null ? initialCashAfterBuy(buy) : Double.NaN, 1e-6, "");
        add(checks, "2025-03-10 收盘净值(首日损益计入)", 0.9985019008, nav0310, 1e-9, "99850.19008/100000");
        add(checks, "卖出成交价(含滑点,元,2025-03-12)", 10.9945, sell != null ? sell.price : Double.NaN, 1e-9, "11×0.9995");
        add(checks, "卖出手续费(元)", 91.47424, sell != null ? sell.fee : Double.NaN, 1e-6, "8320×10.9945×0.001");
        add(checks, "卖出后现金(元)", 91392.95584, cashEnd, 1e-6, "");
        add(checks, "策略期末净值", 0.9139295584, navEnd, 1e-9, "");
        add(checks, "基准期末净值(买入持有)", 0.9153019008, benchEnd, 1e-9, "(10.19008+8320×11)/100000");
        return checks;
    }

    private double initialCashAfterBuy(BacktestResult.Trade buy) {
        return 100000 - buy.quantity * buy.price - buy.fee;
    }

    private void add(List<Map<String, Object>> checks, String name, double expected, Double actual,
                     double tol, String hint) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("expected", expected);
        double act = actual != null ? actual : Double.NaN;
        c.put("actual", Double.isNaN(act) ? null : act);
        c.put("pass", !Double.isNaN(act) && Math.abs(act - expected) <= tol);
        c.put("tolerance", tol);
        c.put("hint", hint);
        checks.add(c);
    }

    // ==================== 内部工具 ====================

    private RotationParams paramsFrom(StrategyConfig cfg, RunRequest req, LocalDate start, LocalDate end) {
        RotationParams p = new RotationParams();
        p.strategyVersion = cfg.getVersion() != null ? cfg.getVersion() + ".0.0" : "1.0.0";
        p.momentumWindow = nz(cfg.getMomentumWindow(), 20);
        p.holdingCount = Math.max(1, nz(cfg.getHoldingCount(), 2));
        p.rebalanceFreq = "MONTHLY".equals(cfg.getRebalanceFreq()) ? "MONTHLY" : "WEEKLY";
        p.defensiveEnabled = !Boolean.FALSE.equals(cfg.getDefensiveEnabled());
        p.defensiveThreshold = nd(cfg.getDefensiveThreshold(), 0.0);
        p.defensiveMode = "ASSET".equals(cfg.getDefensiveMode()) ? "ASSET" : "CASH";
        p.defensiveAsset = cfg.getDefensiveAsset();
        p.trendFilterEnabled = Boolean.TRUE.equals(cfg.getTrendFilterEnabled());
        p.trendWindow = nz(cfg.getTrendWindow(), 20);
        p.trendThreshold = nd(cfg.getTrendThreshold(), 1.0);
        p.commissionRate = nd(cfg.getCommissionRate(), 0.001);
        p.minimumCommission = nd(cfg.getMinimumCommission(), 0.0);
        p.slippageRate = nd(cfg.getSlippageRate(), 0.0005);
        p.initialCash = nd(cfg.getInitialCash(), 100000.0);
        p.benchmarkAsset = cfg.getBenchmarkAsset() != null ? cfg.getBenchmarkAsset() : "510300";
        p.start = start;
        p.end = end;
        p.note = req.note != null ? req.note : "";

        // 附加实验覆盖项(参数敏感性/防御开关对照)
        List<String> changes = new ArrayList<>();
        if (req.overrideMomentumWindow != null) {
            p.momentumWindow = req.overrideMomentumWindow;
            changes.add("动量窗口=" + req.overrideMomentumWindow);
        }
        if (req.overrideHoldingCount != null) {
            p.holdingCount = req.overrideHoldingCount;
            changes.add("持有数量=" + req.overrideHoldingCount);
        }
        if (req.overrideRebalanceFreq != null) {
            p.rebalanceFreq = "MONTHLY".equals(req.overrideRebalanceFreq) ? "MONTHLY" : "WEEKLY";
            changes.add("调仓频率=" + p.rebalanceFreq);
        }
        if (req.overrideDefensiveEnabled != null) {
            p.defensiveEnabled = req.overrideDefensiveEnabled;
            changes.add("防御开关=" + req.overrideDefensiveEnabled);
        }
        if (Boolean.TRUE.equals(req.zeroCost)) {
            p.commissionRate = 0;
            p.minimumCommission = 0;
            p.slippageRate = 0;
            changes.add("零费率零滑点");
        }
        if (!changes.isEmpty()) {
            p.note = ("附加实验:" + String.join(",", changes) + ";").concat(p.note == null ? "" : p.note);
        }
        return p;
    }

    private Map<String, Object> sampleConfigJson(LocalDate start, LocalDate end, double cash,
                                                 double rate, double min, double slip) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("purpose", "sample_check");
        cfg.put("strategy_id", "demo_trend");
        cfg.put("strategy_version", "1.0.0");
        cfg.put("factor_table_version", "v1");
        cfg.put("factors", List.of(Map.of("factor_id", "demo_ma_ratio", "parameters", Map.of("d", 5))));
        cfg.put("strategy", Map.of("implementation", "ma_ratio_long_cash", "threshold", 1.0));
        cfg.put("data", Map.of(
                "dataset_id", "demo_b_sample",
                "data_version", "v1",
                "data_type", "synthetic",
                "asset_ids", List.of("DEMO_A"),
                "source", "《实验要求》6.6 虚构教学数据,共8条日线"));
        cfg.put("period", Map.of("start", start.toString(), "end", end.toString(),
                "warmup_note", "2025-03-03~03-07 用于预热与首信号,不成交"));
        cfg.put("account", Map.of("initial_cash", cash, "initial_quantity", 0, "currency", "CNY", "lot_size", 1));
        cfg.put("execution", Map.of("timezone", "+08:00", "signal_time", "15:05:00",
                "fill", "next_open", "fill_time", "09:30:00"));
        cfg.put("costs", Map.of("commission_rate", rate, "minimum_commission", min, "slippage_rate", slip));
        cfg.put("benchmark", Map.of("implementation", "buy_and_hold"));
        return cfg;
    }

    private String buildConfigJson(RotationParams p, List<String> assetIds, String dataVersion) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("factor_id", "g01_mom_n");
        factors.put("parameters", Map.of("W", p.momentumWindow));
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("implementation", "etf_mom_rotation");
        strategy.put("momentum_window", p.momentumWindow);
        strategy.put("holding_count", p.holdingCount);
        strategy.put("rebalance_freq", p.rebalanceFreq);
        Map<String, Object> defensive = new LinkedHashMap<>();
        defensive.put("enabled", p.defensiveEnabled);
        defensive.put("threshold", p.defensiveThreshold);
        defensive.put("mode", p.defensiveMode);
        if (p.defensiveAsset != null) {
            defensive.put("asset", p.defensiveAsset);
        }
        strategy.put("defensive", defensive);
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("enabled", p.trendFilterEnabled);
        trend.put("window", p.trendWindow);
        trend.put("threshold", p.trendThreshold);
        strategy.put("trend_filter", trend);

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("purpose", "historical_backtest");
        cfg.put("strategy_id", p.strategyCode);
        cfg.put("strategy_version", p.strategyVersion);
        cfg.put("factor_table_version", p.factorTableVersion);
        cfg.put("factors", List.of(factors));
        cfg.put("strategy", strategy);
        cfg.put("data", Map.of(
                "dataset_id", "eastmoney_daily_kline",
                "data_version", dataVersion,
                "data_type", "historical",
                "adjust", "前复权(fqt=1)",
                "asset_ids", assetIds));
        cfg.put("period", Map.of("start", p.start.toString(), "end", p.end.toString()));
        cfg.put("account", Map.of("initial_cash", p.initialCash, "initial_quantity", 0,
                "currency", "CNY", "lot_size", 1));
        cfg.put("execution", Map.of("timezone", "+08:00", "signal_time", "15:05:00",
                "fill", "next_open", "fill_time", "09:30:00"));
        cfg.put("costs", Map.of("commission_rate", p.commissionRate,
                "minimum_commission", p.minimumCommission, "slippage_rate", p.slippageRate));
        cfg.put("benchmark", Map.of("implementation", "buy_and_hold", "asset", p.benchmarkAsset));
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void persistRows(String runId, BacktestResult result) {
        List<EquityPoint> eqs = new ArrayList<>();
        for (BacktestResult.Eq e : result.equity) {
            eqs.add(new EquityPoint(runId, e.seriesId, e.date, e.nav, e.cash, e.positionValue));
        }
        if (!eqs.isEmpty()) {
            equityMapper.insertBatch(eqs);
        }

        List<PositionSnapshot> pss = new ArrayList<>();
        for (BacktestResult.Pos ps : result.positions) {
            pss.add(new PositionSnapshot(runId, ps.seriesId, ps.date, ps.assetId, ps.quantity, ps.weight));
        }
        if (!pss.isEmpty()) {
            positionMapper.insertBatch(pss);
        }

        List<TradeRecord> trs = new ArrayList<>();
        for (BacktestResult.Trade t : result.trades) {
            trs.add(new TradeRecord(runId, t.seriesId, t.signalTime, t.executionTime, t.assetId, t.side,
                    t.quantity, t.price, t.fee, t.slippageCost, t.status, t.reason));
        }
        if (!trs.isEmpty()) {
            tradeMapper.insertBatch(trs);
        }

        List<DecisionRecord> dcs = new ArrayList<>();
        for (BacktestResult.Decision d : result.decisions) {
            dcs.add(new DecisionRecord(runId, d.signalDate, d.executed, d.defensiveTriggered,
                    d.defensiveReason, toJson(d.payload)));
        }
        if (!dcs.isEmpty()) {
            decisionMapper.insertBatch(dcs);
        }

        List<RunMetric> mts = new ArrayList<>();
        for (BacktestResult.Metric m : result.metrics) {
            mts.add(new RunMetric(runId, m.seriesId, m.metric, m.value, m.unit, m.definition));
        }
        if (!mts.isEmpty()) {
            metricMapper.insertBatch(mts);
        }
    }

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }

    private String genId(String prefix) {
        return prefix + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private static int nz(Integer v, int dft) {
        return v != null ? v : dft;
    }

    private static double nd(Double v, double dft) {
        return v != null ? v : dft;
    }
}
