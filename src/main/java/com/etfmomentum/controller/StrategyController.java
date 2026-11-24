package com.etfmomentum.controller;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.entity.StrategyConfig;
import com.etfmomentum.dao.StrategyConfigMapper;
import com.etfmomentum.dto.StrategySaveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 策略配置管理与 B_主策略.json 生成 */
@RestController
public class StrategyController {

    private final StrategyConfigMapper strategyMapper;
    private final ObjectMapper om;

    public StrategyController(StrategyConfigMapper strategyMapper, ObjectMapper om) {
        this.strategyMapper = strategyMapper;
        this.om = om;
    }

    @GetMapping("/api/strategies")
    public List<StrategyConfig> list() {
        return strategyMapper.selectAllOrdered();
    }

    @GetMapping("/api/strategies/{id}")
    public StrategyConfig get(@PathVariable Long id) {
        StrategyConfig c = strategyMapper.selectById(id);
        if (c == null) {
            throw new BadRequestException("策略配置不存在:id=" + id);
        }
        return c;
    }

    @PostMapping("/api/strategies")
    public Map<String, Object> save(@RequestBody StrategySaveRequest req) {
        if (req.name == null || req.name.trim().isEmpty()) {
            throw new BadRequestException("请填写策略名称");
        }
        int w = req.momentumWindow != null ? req.momentumWindow : 20;
        int k = req.holdingCount != null ? req.holdingCount : 2;
        if (w < 2 || w > 500) {
            throw new BadRequestException("动量窗口应在 2~500 交易日之间");
        }
        if (k < 1 || k > 10) {
            throw new BadRequestException("持有数量应在 1~10 只之间");
        }
        double rate = req.commissionRate != null ? req.commissionRate : 0.001;
        double slip = req.slippageRate != null ? req.slippageRate : 0.0005;
        double cash = req.initialCash != null ? req.initialCash : 100000;
        if (rate < 0 || rate > 0.05) {
            throw new BadRequestException("手续费率应在 0~0.05 之间");
        }
        if (slip < 0 || slip > 0.02) {
            throw new BadRequestException("滑点率应在 0~0.02 之间");
        }
        if (cash < 1000) {
            throw new BadRequestException("初始资金应不小于 1000 元");
        }
        if (req.benchmarkAsset == null || req.benchmarkAsset.trim().isEmpty()) {
            throw new BadRequestException("请选择基准资产");
        }

        StrategyConfig c = new StrategyConfig();
        c.setStrategyCode("etf_mom_rotation");
        c.setName(req.name.trim());
        StrategyConfig top = strategyMapper.selectTopByNameOrderVersionDesc(req.name.trim());
        c.setVersion(top != null ? top.getVersion() + 1 : 1);
        c.setMomentumWindow(w);
        c.setHoldingCount(k);
        c.setRebalanceFreq("MONTHLY".equals(req.rebalanceFreq) ? "MONTHLY" : "WEEKLY");
        c.setDefensiveEnabled(!Boolean.FALSE.equals(req.defensiveEnabled));
        c.setDefensiveThreshold(req.defensiveThreshold != null ? req.defensiveThreshold : 0.0);
        c.setDefensiveMode("ASSET".equals(req.defensiveMode) ? "ASSET" : "CASH");
        c.setDefensiveAsset(req.defensiveAsset);
        c.setTrendFilterEnabled(Boolean.TRUE.equals(req.trendFilterEnabled));
        c.setTrendWindow(req.trendWindow != null ? req.trendWindow : 20);
        c.setTrendThreshold(req.trendThreshold != null ? req.trendThreshold : 1.0);
        c.setCommissionRate(rate);
        c.setMinimumCommission(req.minimumCommission != null ? req.minimumCommission : 0.0);
        c.setSlippageRate(slip);
        c.setInitialCash(cash);
        c.setBenchmarkAsset(req.benchmarkAsset.trim());
        c.setUniverseNote(req.universeNote);
        c.setNote(req.note);
        c.setParamsJson(toJson(Map.of(
                "momentum_window", w, "holding_count", k, "rebalance_freq", c.getRebalanceFreq(),
                "costs", Map.of("commission_rate", rate,
                        "minimum_commission", c.getMinimumCommission(), "slippage_rate", slip))));
        c.setCreatedAt(LocalDateTime.now());
        strategyMapper.insert(c);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("id", c.getId());
        res.put("name", c.getName());
        res.put("version", c.getVersion());
        res.put("message", "已保存:" + c.getName() + " v" + c.getVersion());
        return res;
    }

    @DeleteMapping("/api/strategies/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (!strategyMapper.existsById(id)) {
            throw new BadRequestException("策略配置不存在:id=" + id);
        }
        strategyMapper.deleteById(id);
        return Map.of("ok", true, "message", "已删除 id=" + id);
    }

    /** 按作业指南 6.3 的 12 字段生成 B_主策略.json 内容 */
    @GetMapping("/api/strategies/{id}/b-json")
    public Map<String, Object> bJson(@PathVariable Long id) {
        StrategyConfig c = get(id);
        String freq = "MONTHLY".equals(c.getRebalanceFreq()) ? "每月" : "每周";
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("strategy_name", "ETF动量轮动策略(W=" + c.getMomentumWindow() + ",K=" + c.getHoldingCount() + ","
                + freq + "调仓)");
        json.put("universe", c.getUniverseNote() != null ? c.getUniverseNote()
                : "候选池为启用状态的ETF;样本内不变");
        json.put("factor_table_version", "v1");
        json.put("factors", List.of(
                Map.of("factor_id", "g01_mom_n", "parameters", Map.of("W", c.getMomentumWindow()))));
        json.put("entry_rule", "信号日(" + freq + "最后一个交易日)收盘后,计算候选池每只ETF的" + c.getMomentumWindow()
                + "日动量并降序排名,取前" + c.getHoldingCount() + "只为目标持仓(并列按代码升序);"
                + (Boolean.TRUE.equals(c.getTrendFilterEnabled())
                ? "启用趋势过滤:均线比值(d=" + c.getTrendWindow() + ")需小于" + c.getTrendThreshold() + "方可入选;"
                : "未启用趋势过滤;"));
        json.put("exit_rule", "已持有资产跌出目标(落选或防御触发)时于下一交易日开盘全部卖出;重新入选可再次买入;"
                + "恰好并列时按代码升序取舍;已持有且仍在目标内不调仓(缓冲)");
        json.put("position_rule", "仅多头,不借款不卖空;每只目标资产目标权重 1/" + c.getHoldingCount()
                + ",买入按扣费后可负担的最大整数份数,余款留现金;权重漂移不修正");
        json.put("rebalance", freq + "最后一个交易日收盘检查;仅目标组合与实际持仓不同时交易;状态不变不调整数量");
        json.put("execution", "T日15:05收盘数据可用后产生信号;T+1 09:30按开盘价计入滑点成交,时区+08:00;当日无行情则顺延,保留原持仓");
        json.put("costs", "买卖均按成交金额收取" + pct(c.getCommissionRate())
                + "手续费(最低" + c.getMinimumCommission() + "元);滑点为开盘价的" + pct(c.getSlippageRate())
                + ",买价上浮、卖价下调,计入成交价后不重复扣费");
        json.put("exceptions", "动量窗口不足或含无效价的资产当期不参与排名;买入资金不足1份、开盘缺价或停牌时订单顺延至下次调仓;"
                + "评价区间最后一个信号日若无下一交易日则不执行该信号");
        json.put("baseline", "基准资产(" + c.getBenchmarkAsset() + ")在共同起点开盘按同样的资金约束与费用买入后一直持有,"
                + "首次无法成交则逐日开盘重试;与主策略相同初始资金(" + c.getInitialCash() + "元)、区间、费用和交易约定,期末按收盘价估值、不强制卖出");
        return json;
    }

    private String pct(Double v) {
        if (v == null) {
            return "0%";
        }
        return String.format("%.4f%%", v * 100);
    }

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
