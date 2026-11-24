package com.etfmomentum.controller;

import com.etfmomentum.entity.BacktestRun;
import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.entity.EtfInfo;
import com.etfmomentum.dao.BacktestRunMapper;
import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.dao.EtfInfoMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OverviewController {

    private final EtfInfoMapper etfMapper;
    private final DailyBarMapper barMapper;
    private final BacktestRunMapper runMapper;

    public OverviewController(EtfInfoMapper etfMapper, DailyBarMapper barMapper, BacktestRunMapper runMapper) {
        this.etfMapper = etfMapper;
        this.barMapper = barMapper;
        this.runMapper = runMapper;
    }

    @GetMapping("/api/overview")
    public Map<String, Object> overview() {
        List<EtfInfo> etfs = etfMapper.selectAllOrderByCode();
        int realCount = 0;
        LocalDate first = null;
        LocalDate last = null;
        for (EtfInfo e : etfs) {
            if (!"DEMO".equals(e.getSource())) {
                realCount++;
                DailyBar f = barMapper.selectFirstByCodeOrderByDateAsc(e.getCode());
                DailyBar l = barMapper.selectFirstByCodeOrderByDateDesc(e.getCode());
                if (f != null && (first == null || f.getTradeDate().isBefore(first))) {
                    first = f.getTradeDate();
                }
                if (l != null && (last == null || l.getTradeDate().isAfter(last))) {
                    last = l.getTradeDate();
                }
            }
        }

        List<BacktestRun> recent = runMapper.selectAllOrdered();
        BacktestRun lastRun = recent.isEmpty() ? null : recent.get(0);
        boolean samplePassed = false;
        for (BacktestRun r : recent) {
            if ("sample_check".equals(r.getPurpose()) && "SUCCESS".equals(r.getStatus())) {
                samplePassed = true;
                break;
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("etfCount", etfs.size());
        res.put("realEtfCount", realCount);
        res.put("barCount", barMapper.countAll());
        res.put("coverageStart", first != null ? first.toString() : null);
        res.put("coverageEnd", last != null ? last.toString() : null);
        res.put("runCount", recent.size());
        res.put("lastRun", lastRun);
        res.put("sampleCheckPassed", samplePassed);
        res.put("dataReady", realCount >= 2 && last != null);
        return res;
    }
}
