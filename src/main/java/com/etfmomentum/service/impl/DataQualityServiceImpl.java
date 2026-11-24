package com.etfmomentum.service.impl;

import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.dao.EtfInfoMapper;
import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.entity.EtfInfo;
import com.etfmomentum.service.DataQualityService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 行情数据质量检查(确定性规则):
 * 覆盖/滞后、缺失交易日(以候选池并集日历为基准,不按自然日误判休市)、
 * 重复记录、价格合法性、OHLC 关系、异常涨跌幅、数据口径一致性。
 * 所有结论由程序产生,AI 仅负责解释。
 */
@Service
public class DataQualityServiceImpl implements DataQualityService {

    private final EtfInfoMapper etfMapper;
    private final DailyBarMapper barMapper;

    public DataQualityServiceImpl(EtfInfoMapper etfMapper, DailyBarMapper barMapper) {
        this.etfMapper = etfMapper;
        this.barMapper = barMapper;
    }

    @Override
    public Map<String, Object> check(double abnormalReturnThreshold) {
        List<Map<String, Object>> findings = new ArrayList<>();
        List<EtfInfo> etfs = etfMapper.selectAllOrderByCode();
        List<EtfInfo> real = new ArrayList<>();
        for (EtfInfo e : etfs) {
            if (!"DEMO".equals(e.getSource())) {
                real.add(e);
            }
        }

        // 交易日历 = 候选池全部 ETF 交易日并集(规避周末/节假日误判)
        Map<String, Set<LocalDate>> calendars = new LinkedHashMap<>();
        Set<LocalDate> union = new HashSet<>();
        Map<String, List<DailyBar>> barsByCode = new LinkedHashMap<>();
        for (EtfInfo e : real) {
            List<DailyBar> bars = barMapper.selectByCodeOrderByDateAsc(e.getCode());
            barsByCode.put(e.getCode(), bars);
            Set<LocalDate> set = new HashSet<>();
            for (DailyBar b : bars) {
                set.add(b.getTradeDate());
            }
            calendars.put(e.getCode(), set);
            union.addAll(set);
        }

        LocalDate newest = null;
        for (LocalDate d : union) {
            if (newest == null || d.isAfter(newest)) {
                newest = d;
            }
        }
        int checked = 0;
        Set<String> sources = new HashSet<>();

        for (EtfInfo e : real) {
            List<DailyBar> bars = barsByCode.get(e.getCode());
            if (bars == null || bars.isEmpty()) {
                findings.add(finding("SEVERE", e.getCode(), null, "coverage", "-",
                        "无任何行情记录,无法参与回测"));
                continue;
            }
            checked++;
            sources.add(e.getSource() == null ? "-" : e.getSource());

            // 1) 覆盖与滞后
            LocalDate first = bars.get(0).getTradeDate();
            LocalDate last = bars.get(bars.size() - 1).getTradeDate();
            if (newest != null && ChronoUnit.DAYS.between(last, newest) >= 7) {
                findings.add(finding("WARN", e.getCode(), last, "latest_date", last.toString(),
                        "最新行情落后候选池最新交易日 " + ChronoUnit.DAYS.between(last, newest) + " 天,建议更新"));
            }

            // 2) 缺失交易日(以并集日历为基准,仅统计该 ETF 已上市之后的日子)
            int missing = 0;
            List<String> missingDates = new ArrayList<>();
            for (LocalDate d : union) {
                if (!d.isBefore(first) && !d.isAfter(last) && !calendars.get(e.getCode()).contains(d)) {
                    missing++;
                    if (missingDates.size() < 8) {
                        missingDates.add(d.toString());
                    }
                }
            }
            if (missing > 0) {
                findings.add(finding(missing > 20 ? "WARN" : "INFO", e.getCode(), null, "missing_days",
                        String.valueOf(missing), "交易日历内缺失 " + missing + " 天(可能停牌):"
                                + String.join(",", missingDates) + (missing > 8 ? "…" : "")));
            }

            // 3) 逐条记录检查:重复/价格合法/OHLC/异常涨跌
            LocalDate prevDate = null;
            Double prevClose = null;
            int dup = 0;
            Set<LocalDate> seen = new HashSet<>();
            for (DailyBar b : bars) {
                if (!seen.add(b.getTradeDate())) {
                    dup++;
                    continue;
                }
                double o = nz(b.getOpen()), h = nz(b.getHigh()), l = nz(b.getLow()), c = nz(b.getClose());
                if (o <= 0 || h <= 0 || l <= 0 || c <= 0) {
                    findings.add(finding("SEVERE", e.getCode(), b.getTradeDate(), "price",
                            o + "/" + h + "/" + l + "/" + c, "价格存在非正数,记录无效"));
                } else if (h < Math.max(o, c) || l > Math.min(o, c) || h < l) {
                    findings.add(finding("WARN", e.getCode(), b.getTradeDate(), "ohlc",
                            o + "/" + h + "/" + l + "/" + c, "OHLC 关系异常(高/低价与开/收盘矛盾)"));
                } else if (prevClose != null && prevClose > 0) {
                    double chg = c / prevClose - 1;
                    if (Math.abs(chg) > abnormalReturnThreshold) {
                        findings.add(finding("INFO", e.getCode(), b.getTradeDate(), "return",
                                String.format("%.2f%%", chg * 100),
                                "单日涨跌幅超过阈值 " + (abnormalReturnThreshold * 100) + "%,请人工复核"));
                    }
                }
                prevDate = b.getTradeDate();
                prevClose = c;
            }
            if (dup > 0) {
                findings.add(finding("WARN", e.getCode(), null, "duplicate", String.valueOf(dup),
                        "存在 " + dup + " 条重复日期记录"));
            }
            if (prevDate != null && newest != null && prevDate.isBefore(newest)
                    && ChronoUnit.DAYS.between(prevDate, newest) < 7) {
                // 最新 7 天内略旧,大多为正常休市节奏,不告警
            }
        }

        if (sources.size() > 1) {
            findings.add(finding("INFO", "-", null, "source", String.join(",", sources),
                    "候选池存在多种数据来源,口径可能不一致,比较时需注意"));
        }

        // 固定评分规则:100 - 严重*10 - 警告*3 - 提示*0.5,下限 0
        int severe = 0, warn = 0, info = 0;
        for (Map<String, Object> f : findings) {
            String lv = String.valueOf(f.get("level"));
            if ("SEVERE".equals(lv)) severe++;
            else if ("WARN".equals(lv)) warn++;
            else info++;
        }
        int score = Math.max(0, (int) Math.round(100 - severe * 10 - warn * 3 - info * 0.5));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("score", score);
        res.put("checked", checked);
        res.put("calendarDays", union.size());
        res.put("newestTradeDate", newest != null ? newest.toString() : null);
        res.put("abnormalReturnThreshold", abnormalReturnThreshold);
        res.put("counts", Map.of("SEVERE", severe, "WARN", warn, "INFO", info));
        res.put("findings", findings);
        res.put("summary", "体检 " + checked + " 只 ETF:严重 " + severe + " 项,警告 " + warn
                + " 项,提示 " + info + " 项;健康评分 " + score + "/100(规则:100-严重×10-警告×3-提示×0.5)");
        res.put("note", "以上结果来自程序固定规则(交易日历=候选池并集,不将休市判为缺失);不是对未来行情的预测");
        return res;
    }

    private Map<String, Object> finding(String level, String code, LocalDate date, String field,
                                        String value, String rule) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("level", level);
        f.put("code", code);
        f.put("date", date != null ? date.toString() : "-");
        f.put("field", field);
        f.put("value", value);
        f.put("rule", rule);
        return f;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }
}
