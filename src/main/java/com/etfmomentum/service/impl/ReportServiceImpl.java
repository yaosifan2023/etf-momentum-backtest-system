package com.etfmomentum.service.impl;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.dao.BacktestRunMapper;
import com.etfmomentum.dao.DecisionRecordMapper;
import com.etfmomentum.dao.EquityPointMapper;
import com.etfmomentum.dao.PositionSnapshotMapper;
import com.etfmomentum.dao.RunMetricMapper;
import com.etfmomentum.dao.TradeRecordMapper;
import com.etfmomentum.entity.BacktestRun;
import com.etfmomentum.entity.DecisionRecord;
import com.etfmomentum.entity.EquityPoint;
import com.etfmomentum.entity.PositionSnapshot;
import com.etfmomentum.entity.RunMetric;
import com.etfmomentum.entity.TradeRecord;
import com.etfmomentum.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 研究报告与结构化诊断:全部数值来自数据库与程序计算(回撤区间、持仓统计、
 * 成本统计等),AI 只解释这些结果,不产生数字。报告落盘 results/{runId}/report.md。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private final BacktestRunMapper runMapper;
    private final RunMetricMapper metricMapper;
    private final EquityPointMapper equityMapper;
    private final PositionSnapshotMapper positionMapper;
    private final TradeRecordMapper tradeMapper;
    private final DecisionRecordMapper decisionMapper;
    private final ObjectMapper om;

    @Value("${app.results-dir:./results}")
    private String resultsDir;

    public ReportServiceImpl(BacktestRunMapper runMapper, RunMetricMapper metricMapper,
                             EquityPointMapper equityMapper, PositionSnapshotMapper positionMapper,
                             TradeRecordMapper tradeMapper, DecisionRecordMapper decisionMapper, ObjectMapper om) {
        this.runMapper = runMapper;
        this.metricMapper = metricMapper;
        this.equityMapper = equityMapper;
        this.positionMapper = positionMapper;
        this.tradeMapper = tradeMapper;
        this.decisionMapper = decisionMapper;
        this.om = om;
    }

    @Override
    public Map<String, Object> analyzeRun(String runId) {
        BacktestRun run = load(runId);
        List<RunMetric> metrics = metricMapper.selectByRunId(runId);
        Map<String, Double> sv = new LinkedHashMap<>();
        Map<String, Double> bv = new LinkedHashMap<>();
        for (RunMetric m : metrics) {
            if (m.getMetricValue() != null) {
                if ("strategy".equals(m.getSeriesId())) sv.put(m.getMetric(), m.getMetricValue());
                else if ("benchmark".equals(m.getSeriesId())) bv.put(m.getMetric(), m.getMetricValue());
            }
        }

        // 回撤区间定位(策略序列,含基线 1)
        List<EquityPoint> eq = equityMapper.selectByRunIdAndSeries(runId, "strategy");
        double peak = Double.NEGATIVE_INFINITY;
        LocalDate peakDate = null, troughDate = null, startDownDate = null, recoverDate = null;
        double maxDd = 0, ddPeak = 0, ddTrough = 0;
        LocalDate curPeakDate = null;
        for (EquityPoint e : eq) {
            double nav = e.getNav() == null ? 1.0 : e.getNav();
            if (nav >= peak) {
                peak = nav;
                curPeakDate = e.getTradeDate();
            } else {
                double dd = nav / peak - 1;
                if (dd < maxDd) {
                    maxDd = dd;
                    peakDate = curPeakDate;
                    troughDate = e.getTradeDate();
                    ddPeak = peak;
                    ddTrough = nav;
                    startDownDate = startDownDate == null ? e.getTradeDate() : startDownDate;
                }
                if (recoverDate == null && maxDd < 0 && nav >= peak) {
                    recoverDate = e.getTradeDate();
                }
            }
        }
        // 修复:回撤结束(净值重新回到峰值)的判定要在峰值更新处重置
        LocalDate recovery = null;
        double p2 = Double.NEGATIVE_INFINITY;
        boolean inDd = false;
        for (EquityPoint e : eq) {
            double nav = e.getNav() == null ? 1.0 : e.getNav();
            if (nav >= p2) {
                if (inDd && maxDd < 0 && nav >= ddPeak) {
                    recovery = e.getTradeDate();
                    inDd = false;
                }
                p2 = nav;
            } else {
                inDd = true;
            }
        }

        // 交易与持仓统计
        List<TradeRecord> trades = tradeMapper.selectByRunIdAndSeries(runId, "strategy");
        int buys = 0, sells = 0, unfilled = 0;
        double totalFee = 0, buyNotional = 0, sellNotional = 0;
        for (TradeRecord t : trades) {
            if (!"FILLED".equals(t.getStatus())) { unfilled++; continue; }
            totalFee += t.getFee() != null ? t.getFee() : 0;
            double notional = t.getQuantity() * (t.getPrice() != null ? t.getPrice() : 0);
            if ("buy".equals(t.getSide())) { buys++; buyNotional += notional; } else { sells++; sellNotional += notional; }
        }
        // 持有天数统计(按持仓快照出现次数近似)
        Map<String, Integer> holdDays = new LinkedHashMap<>();
        List<PositionSnapshot> pos = positionMapper.selectByRunIdAndSeries(runId, "strategy");
        for (PositionSnapshot p : pos) {
            holdDays.merge(p.getAssetId(), 1, Integer::sum);
        }
        List<Map<String, Object>> topHold = holdDays.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .map(en -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", en.getKey());
                    m.put("snapshotDays", en.getValue());
                    return m;
                }).collect(Collectors.toList());

        List<DecisionRecord> decs = decisionMapper.selectByRunId(runId);
        int defensiveCount = 0;
        for (DecisionRecord d : decs) {
            if (Boolean.TRUE.equals(d.getDefensiveTriggered())) defensiveCount++;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("runId", runId);
        res.put("purpose", run.getPurpose());
        res.put("dataType", run.getDataType());
        res.put("period", run.getStartDate() + "~" + run.getEndDate());
        res.put("benchmark", run.getBenchmark());
        res.put("strategyMetrics", sv);
        res.put("benchmarkMetrics", bv);
        Map<String, Object> dd = new LinkedHashMap<>();
        dd.put("maxDrawdown", maxDd);
        dd.put("peakDate", peakDate != null ? peakDate.toString() : "-");
        dd.put("troughDate", troughDate != null ? troughDate.toString() : "-");
        dd.put("recoveryDate", recovery != null ? recovery.toString() : "未修复");
        dd.put("peakNav", ddPeak);
        dd.put("troughNav", ddTrough);
        res.put("drawdownWindow", dd);
        res.put("trades", Map.of("buys", buys, "sells", sells, "unfilled", unfilled,
                "totalFee", totalFee, "buyNotional", buyNotional, "sellNotional", sellNotional));
        res.put("topHoldings", topHold);
        res.put("decisions", Map.of("total", decs.size(), "defensiveTriggered", defensiveCount));
        return res;
    }

    @Override
    public Map<String, Object> generateReport(String runId) {
        Map<String, Object> a = analyzeRun(runId);
        BacktestRun run = load(runId);

        @SuppressWarnings("unchecked")
        Map<String, Double> sv = (Map<String, Double>) a.get("strategyMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Double> bv = (Map<String, Double>) a.get("benchmarkMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> dd = (Map<String, Object>) a.get("drawdownWindow");
        @SuppressWarnings("unchecked")
        Map<String, Object> tr = (Map<String, Object>) a.get("trades");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) a.get("topHoldings");
        @SuppressWarnings("unchecked")
        Map<String, Object> dec = (Map<String, Object>) a.get("decisions");

        StringBuilder md = new StringBuilder();
        md.append("# ETF 动量轮动回测研究报告\n\n");
        md.append("> 运行编号:`").append(runId).append("`\n>\n> 本报告由系统根据真实运行数据自动生成;所有数值来自程序计算与数据库记录,可复核。\n\n");
        md.append("## 一、研究目的与研究问题\n\n");
        md.append("课题 T03.01 相对动量轮动:ETF 池内过去 W 日相对动量是否延续?扣除切换成本后,动量轮动相对单资产买入持有的收益、回撤与换手差异如何?防御开关能否降低回撤?\n\n");
        md.append("## 二、数据与实验设置\n\n");
        md.append("- 数据性质:").append("synthetic".equals(run.getDataType()) ? "虚构教学数据(DEMO_A,仅验证引擎口径)" : "真实历史数据(东方财富日线,前复权)").append("\n");
        md.append("- 评价区间:").append(a.get("period")).append("(预热数据不计入评价)\n");
        md.append("- 基准:").append(run.getBenchmark()).append("\n");
        md.append("- 初始资金:").append(run.getInitialCash()).append(" 元\n");
        md.append("- 成交假设:T 日 15:05 收盘信号,T+1 09:30 开盘价成交,滑点计入价格,双边手续费(详见 config.json)\n\n");
        md.append("## 三、核心指标对照\n\n");
        md.append("| 指标 | 策略 | 基准 |\n|---|---:|---:|\n");
        String[] keys = {"total_return", "annual_return", "annual_vol", "max_drawdown", "sharpe", "turnover", "trade_count", "total_cost"};
        String[] names = {"累计收益", "年化收益", "年化波动", "最大回撤", "夏普比率", "换手率(年)", "成交笔数", "总手续费(元)"};
        for (int i = 0; i < keys.length; i++) {
            md.append("| ").append(names[i]).append(" | ").append(fmt(sv.get(keys[i]), keys[i]))
              .append(" | ").append(fmt(bv.get(keys[i]), keys[i])).append(" |\n");
        }
        md.append("\n> 口径:净值=账户权益/评价起点交易前权益,初始净值 1 参与回撤;年化 252 交易日、简单收益;夏普无风险利率取 0(教学假设);换手=Σ成交金额/平均权益/年数。\n\n");
        md.append("## 四、最大回撤分析\n\n");
        md.append("- 峰值日期:").append(dd.get("peakDate")).append("(净值 ").append(num(dd.get("peakNav"))).append(")\n");
        md.append("- 谷底日期:").append(dd.get("troughDate")).append("(净值 ").append(num(dd.get("troughNav"))).append(")\n");
        md.append("- 最大回撤:").append(pct(dd.get("maxDrawdown"))).append("\n");
        md.append("- 修复日期:").append(dd.get("recoveryDate")).append("\n\n");
        md.append("## 五、交易、持仓与信号\n\n");
        md.append("- 成交:买入 ").append(tr.get("buys")).append(" 笔,卖出 ").append(tr.get("sells"))
          .append(" 笔,未成交尝试 ").append(tr.get("unfilled")).append(" 笔\n");
        md.append("- 成本:总手续费 ").append(num(tr.get("totalFee"))).append(" 元;买入金额 ")
          .append(num(tr.get("buyNotional"))).append(" 元,卖出金额 ").append(num(tr.get("sellNotional"))).append(" 元\n");
        md.append("- 信号决策:共 ").append(dec.get("total")).append(" 个调仓信号日,其中防御触发 ")
          .append(dec.get("defensiveTriggered")).append(" 次\n");
        md.append("- 持有时间最长(按持仓快照天数近似):");
        if (top.isEmpty()) {
            md.append("无持仓记录\n");
        } else {
            md.append("\n\n| ETF | 快照天数 |\n|---|---:|\n");
            for (Map<String, Object> t : top) {
                md.append("| ").append(t.get("code")).append(" | ").append(t.get("snapshotDays")).append(" |\n");
            }
            md.append("\n");
        }
        md.append("## 六、结果局限与说明\n\n");
        md.append("1. 历史回测不代表未来收益;样本区间单一,动量效应存在状态依赖。\n2. 日线数据无法重建盘中成交,以\"次日开盘价±滑点\"近似;前复权口径下因子分析与模拟成交共用同一价格序列。\n3. 参数为预先固定的教学约定,未做 Walk-Forward 样本外验证;建议结合参数敏感性与零成本对照解读。\n4. 本报告不构成投资建议。\n\n");
        md.append("## 附录:可追溯信息\n\n");
        md.append("- run_id:`").append(runId).append("`\n- 策略:").append(run.getStrategyCode()).append(" v").append(run.getStrategyVersion()).append("\n");
        md.append("- 配置快照与明细导出:results/").append(runId).append("/ 目录(config.json、run.json、metrics.csv、equity.csv、positions.csv、trades.csv、decisions.csv)\n");
        if (run.getNote() != null && !run.getNote().isEmpty()) {
            md.append("- 备注:").append(run.getNote()).append("\n");
        }

        String markdown = md.toString();
        Path file = null;
        try {
            Path dir = Paths.get(resultsDir, runId);
            Files.createDirectories(dir);
            file = dir.resolve("report.md");
            Files.writeString(file, markdown, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写出报告失败:" + e.getMessage(), e);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("runId", runId);
        res.put("file", file != null ? file.toString() : null);
        res.put("downloadPath", "/api/runs/" + runId + "/export/report.md");
        res.put("markdown", markdown);
        return res;
    }

    private BacktestRun load(String runId) {
        BacktestRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BadRequestException("运行记录不存在:" + runId);
        }
        return run;
    }

    private String fmt(Double v, String key) {
        if (v == null) return "NA";
        if (key.equals("trade_count")) return String.valueOf(Math.round(v));
        if (key.equals("total_cost")) return String.format("%.2f", v);
        if (key.equals("sharpe")) return String.format("%.3f", v);
        if (key.equals("turnover")) return String.format("%.2f", v);
        return String.format("%.2f%%", v * 100);
    }

    private String num(Object v) {
        if (v == null) return "-";
        return String.format("%.4f", ((Number) v).doubleValue());
    }

    private String pct(Object v) {
        if (v == null) return "-";
        return String.format("%.2f%%", ((Number) v).doubleValue() * 100);
    }
}
