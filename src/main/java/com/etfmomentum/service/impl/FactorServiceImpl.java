package com.etfmomentum.service.impl;
import com.etfmomentum.service.BacktestService;
import com.etfmomentum.service.ExportService;
import com.etfmomentum.service.FactorService;
import com.etfmomentum.service.MarketDataService;

import com.etfmomentum.core.FactorMath;
import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 因子实时计算预览(与 A_因子搜集.xlsx 的公式一致) */
@Service
public class FactorServiceImpl implements FactorService {

    private final DailyBarMapper barMapper;

    public FactorServiceImpl(DailyBarMapper barMapper) {
        this.barMapper = barMapper;
    }

    @Override
    public Map<String, Object> values(String code, String factorId, Integer window, Integer limit) {
        int w = window != null && window > 0 ? window : defaultWindow(factorId);
        int lim = limit != null && limit > 0 && limit <= 500 ? limit : 30;

        List<DailyBar> bars = barMapper.selectByCodeOrderByDateAsc(code);
        if (bars.isEmpty()) {
            throw new BadRequestException("该资产暂无行情数据,请先在数据管理页下载");
        }
        int n = bars.size();
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            double c = bars.get(i).getClose() != null ? bars.get(i).getClose() : Double.NaN;
            closes[i] = FactorMath.valid(c) ? c : Double.NaN;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = Math.max(0, n - lim); i < n; i++) {
            double v = compute(factorId, closes, i, w);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", bars.get(i).getTradeDate().toString());
            row.put("close", closes[i]);
            row.put("value", Double.isNaN(v) ? null : v);
            rows.add(row);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("factorId", factorId);
        res.put("code", code);
        res.put("window", w);
        res.put("rows", rows);
        return res;
    }

    private double compute(String factorId, double[] closes, int i, int w) {
        switch (factorId) {
            case "g01_mom_n":
                return FactorMath.momentum(closes, i, w);
            case "g01_ma_ratio":
                return FactorMath.maRatio(closes, i, w);
            case "g01_vol_d":
                return FactorMath.annualizedVol(closes, i, w);
            default:
                throw new BadRequestException("未定义的因子:" + factorId);
        }
    }

    private int defaultWindow(String factorId) {
        switch (factorId) {
            case "g01_mom_n":
                return 20;
            case "g01_ma_ratio":
                return 20;
            case "g01_vol_d":
                return 20;
            default:
                throw new BadRequestException("未定义的因子:" + factorId);
        }
    }
}
