package com.etfmomentum.controller;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.entity.EtfInfo;
import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.dao.EtfInfoMapper;
import com.etfmomentum.service.MarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据管理:行情下载 / 导入 / 预览 */
@RestController
public class DataController {

    private final EtfInfoMapper etfMapper;
    private final DailyBarMapper barMapper;
    private final MarketDataService marketDataService;

    public DataController(EtfInfoMapper etfMapper, DailyBarMapper barMapper, MarketDataService marketDataService) {
        this.etfMapper = etfMapper;
        this.barMapper = barMapper;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/api/etfs")
    public List<Map<String, Object>> etfs() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EtfInfo e : etfMapper.selectAllOrderByCode()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", e.getCode());
            row.put("name", e.getName());
            row.put("market", e.getMarket());
            row.put("category", e.getCategory());
            row.put("defensive", Boolean.TRUE.equals(e.getDefensive()));
            row.put("benchmark", Boolean.TRUE.equals(e.getBenchmark()));
            row.put("enabled", Boolean.TRUE.equals(e.getEnabled()));
            row.put("source", e.getSource());
            row.put("latestDate", e.getLatestDate() != null ? e.getLatestDate().toString() : null);
            row.put("barCount", e.getBarCount() != null ? e.getBarCount() : 0);
            DailyBar first = barMapper.selectFirstByCodeOrderByDateAsc(e.getCode());
            DailyBar last = barMapper.selectFirstByCodeOrderByDateDesc(e.getCode());
            row.put("coverageStart", first != null ? first.getTradeDate().toString() : null);
            row.put("coverageEnd", last != null ? last.getTradeDate().toString() : null);
            rows.add(row);
        }
        return rows;
    }

    @PostMapping("/api/data/refresh/{code}")
    public Map<String, Object> refresh(@PathVariable String code) {
        return marketDataService.refresh(code);
    }

    /** 添加任意 ETF 并联网抓取其历史行情 */
    @PostMapping("/api/data/add-etf")
    public Map<String, Object> addEtf(@RequestBody Map<String, String> body) {
        return marketDataService.addEtf(body != null ? body.get("code") : null);
    }

    @PostMapping("/api/data/refresh-all")
    public Map<String, Object> refreshAll() {
        List<Map<String, Object>> results = marketDataService.refreshAll();
        long ok = results.stream().filter(r -> Boolean.TRUE.equals(r.get("ok"))).count();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", ok > 0);
        res.put("results", results);
        res.put("message", String.format("更新完成:成功 %d / %d 只", ok, results.size()));
        return res;
    }

    @PostMapping("/api/data/import-csv")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择 CSV 文件");
        }
        try {
            return marketDataService.importCsv(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BadRequestException("读取上传文件失败:" + e.getMessage());
        }
    }

    @GetMapping("/api/etfs/{code}/bars")
    public Map<String, Object> bars(@PathVariable String code, @RequestParam(required = false) Integer limit) {
        int lim = limit != null && limit > 0 && limit <= 1000 ? limit : 60;
        List<DailyBar> desc = barMapper.selectTop60ByCodeOrderByDateDesc(code);
        List<DailyBar> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        if (asc.size() > lim) {
            asc = asc.subList(asc.size() - lim, asc.size());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DailyBar b : asc) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", b.getTradeDate().toString());
            row.put("open", b.getOpen());
            row.put("high", b.getHigh());
            row.put("low", b.getLow());
            row.put("close", b.getClose());
            row.put("volume", b.getVolume());
            row.put("amount", b.getAmount());
            rows.add(row);
        }
        EtfInfo etf = etfMapper.selectByCode(code);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", code);
        res.put("name", etf != null ? etf.getName() : code);
        res.put("bars", rows);
        return res;
    }
}
