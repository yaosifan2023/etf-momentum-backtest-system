package com.etfmomentum.service.impl;
import com.etfmomentum.service.BacktestService;
import com.etfmomentum.service.ExportService;
import com.etfmomentum.service.FactorService;
import com.etfmomentum.service.MarketDataService;

import com.etfmomentum.entity.DailyBar;
import com.etfmomentum.entity.EtfInfo;
import com.etfmomentum.dao.DailyBarMapper;
import com.etfmomentum.dao.EtfInfoMapper;
import com.etfmomentum.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行情数据服务:内置东方财富 K 线接口下载(日频、前复权 fqt=1),
 * 逐条解析入库并落盘 data/{code}.csv;同时支持手动导入同格式 CSV。
 * 数据口径:字段顺序 日期,开盘,收盘,最高,最低,成交量(手),成交额(元)。
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {

    private final EtfInfoMapper etfMapper;
    private final DailyBarMapper barMapper;
    private final ObjectMapper om;
    private final HttpClient http;

    @Value("${app.data-dir:./data}")
    private String dataDir;

    @Value("${app.fetch.read-timeout-ms:60000}")
    private long readTimeoutMs;

    public MarketDataServiceImpl(EtfInfoMapper etfMapper, DailyBarMapper barMapper, ObjectMapper om) {
        this.etfMapper = etfMapper;
        this.barMapper = barMapper;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** 下载/更新单只 ETF 全部日线 */
    @Override
    @Transactional
    public Map<String, Object> refresh(String code) {
        EtfInfo etf = etfMapper.selectByCode(code);
        if (etf == null) {
            throw new BadRequestException("未知 ETF:" + code);
        }
        if ("DEMO".equals(etf.getSource())) {
            throw new BadRequestException("虚构教学资产不支持联网更新");
        }
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=" + etf.getSecid()
                + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57&klt=101&fqt=1&beg=0&end=20500101";

        String body = httpGetWithRetry(url, 3);
        JsonNode data;
        try {
            JsonNode root = om.readTree(body);
            data = root.path("data");
        } catch (IOException e) {
            throw new BadRequestException("行情接口返回解析失败:" + e.getMessage());
        }
        if (data.isMissingNode() || data.isNull() || data.path("klines").isMissingNode()) {
            throw new BadRequestException("行情接口返回为空(secid=" + etf.getSecid() + "),请稍后重试");
        }
        String apiName = data.path("name").asText("");
        if (!apiName.isEmpty() && (etf.getName() == null || etf.getName().isEmpty())) {
            etf.setName(apiName);
        }

        Map<LocalDate, DailyBar> existing = new HashMap<>();
        for (DailyBar b : barMapper.selectByCodeOrderByDateAsc(code)) {
            existing.put(b.getTradeDate(), b);
        }

        int added = 0;
        int updated = 0;
        int skipped = 0;
        LocalDate latest = null;
        List<DailyBar> inserts = new ArrayList<>();
        List<DailyBar> updates = new ArrayList<>();
        for (JsonNode kn : data.path("klines")) {
            String line = kn.asText();
            String[] parts = line.split(",", -1);
            if (parts.length < 7) {
                skipped++;
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(parts[0].trim());
                double open = Double.parseDouble(parts[1].trim());
                double close = Double.parseDouble(parts[2].trim());
                double high = Double.parseDouble(parts[3].trim());
                double low = Double.parseDouble(parts[4].trim());
                double volume = Double.parseDouble(parts[5].trim());
                double amount = Double.parseDouble(parts[6].trim());
                if (!(open > 0) || !(close > 0) || !(high > 0) || !(low > 0)) {
                    skipped++;
                    continue;
                }
                DailyBar bar = existing.get(d);
                if (bar == null) {
                    bar = new DailyBar(code, d, open, high, low, close, volume, amount);
                    added++;
                    inserts.add(bar);
                } else {
                    bar.setOpen(open);
                    bar.setHigh(high);
                    bar.setLow(low);
                    bar.setClose(close);
                    bar.setVolume(volume);
                    bar.setAmount(amount);
                    updated++;
                    updates.add(bar);
                }
                if (latest == null || d.isAfter(latest)) {
                    latest = d;
                }
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        if (inserts.isEmpty() && updates.isEmpty()) {
            throw new BadRequestException("接口未返回有效K线(secid=" + etf.getSecid() + ")");
        }
        if (!inserts.isEmpty()) {
            barMapper.insertBatch(inserts);
        }
        for (DailyBar b : updates) {
            barMapper.update(b);
        }

        etf.setSource("EASTMONEY");
        etf.setLatestDate(latest);
        etf.setBarCount((int) barMapper.countByCode(code));
        etfMapper.updateInfo(etf);

        writeCsvFile(code);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("code", code);
        res.put("added", added);
        res.put("updated", updated);
        res.put("skipped", skipped);
        res.put("latestDate", latest != null ? latest.toString() : null);
        res.put("total", etf.getBarCount());
        res.put("message", String.format("%s 更新完成:新增 %d 条,更新 %d 条,共 %d 条(截至 %s)",
                code, added, updated, etf.getBarCount(), latest));
        return res;
    }

    /** 更新候选池全部 ETF(逐只容错) */
    @Override
    public List<Map<String, Object>> refreshAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (EtfInfo etf : etfMapper.selectEnabled()) {
            if ("DEMO".equals(etf.getSource())) {
                continue;
            }
            try {
                results.add(refresh(etf.getCode()));
            } catch (RuntimeException e) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("ok", false);
                fail.put("code", etf.getCode());
                fail.put("message", etf.getCode() + " 更新失败:" + e.getMessage());
                results.add(fail);
            }
        }
        return results;
    }

    @Override
    @Transactional
    public Map<String, Object> addEtf(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new BadRequestException("请输入 6 位 ETF 代码,例如 512690");
        }
        code = code.trim();
        EtfInfo exist = etfMapper.selectByCode(code);
        if (exist != null) {
            throw new BadRequestException("该 ETF 已存在(" + code + "),可直接点击行内「更新」抓取最新数据");
        }
        // 沪市 ETF 以 5 开头(东财 market=1),深市以 1 开头(market=0)
        String secid = (code.startsWith("5") ? "1." : "0.") + code;
        EtfInfo etf = new EtfInfo(code, "", code.startsWith("5") ? "SH" : "SZ", secid,
                "自选", false, false, true, "EASTMONEY", "用户添加并联网抓取");
        etfMapper.insert(etf);
        // refresh 会从接口回填名称并抓取全部日线
        Map<String, Object> res = refresh(code);
        EtfInfo saved = etfMapper.selectByCode(code);
        res.put("name", saved != null ? saved.getName() : code);
        res.put("message", String.format("已添加 %s %s 并抓取完成", code, saved != null && saved.getName() != null ? saved.getName() : ""));
        return res;
    }

    /** 手动导入 CSV(格式与导出一致:code,date,open,high,low,close,volume,amount) */
    @Override
    @Transactional
    public Map<String, Object> importCsv(InputStream in, String fileName) {
        List<DailyBar> toSave = new ArrayList<>();
        Map<String, LocalDate> latestByCode = new HashMap<>();
        Map<String, Integer> countByCode = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();   // 表头
            if (line != null && line.startsWith("﻿")) {
                line = line.substring(1);
            }
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 7) {
                    continue;
                }
                try {
                    String code = parts[0].trim();
                    LocalDate d = LocalDate.parse(parts[1].trim());
                    double open = Double.parseDouble(parts[2].trim());
                    double high = Double.parseDouble(parts[3].trim());
                    double low = Double.parseDouble(parts[4].trim());
                    double close = Double.parseDouble(parts[5].trim());
                    double volume = parts.length > 6 ? Double.parseDouble(parts[6].trim()) : 0;
                    double amount = parts.length > 7 ? Double.parseDouble(parts[7].trim()) : 0;
                    if (!(close > 0) || !(open > 0)) {
                        continue;
                    }
                    toSave.add(new DailyBar(code, d, open, high, low, close, volume, amount));
                    LocalDate cur = latestByCode.get(code);
                    if (cur == null || d.isAfter(cur)) {
                        latestByCode.put(code, d);
                    }
                    countByCode.merge(code, 1, Integer::sum);
                } catch (RuntimeException ignore) {
                    // 跳过无法解析的行
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("读取文件失败:" + e.getMessage());
        }
        if (toSave.isEmpty()) {
            throw new BadRequestException("未解析到有效数据行,请确认格式:code,date,open,high,low,close,volume,amount");
        }

        int upserted = 0;
        for (String code : countByCode.keySet()) {
            Map<LocalDate, DailyBar> existing = new HashMap<>();
            for (DailyBar b : barMapper.selectByCodeOrderByDateAsc(code)) {
                existing.put(b.getTradeDate(), b);
            }
            List<DailyBar> inserts = new ArrayList<>();
            List<DailyBar> updates = new ArrayList<>();
            for (DailyBar nb : toSave) {
                if (!nb.getCode().equals(code)) {
                    continue;
                }
                DailyBar old = existing.get(nb.getTradeDate());
                if (old != null) {
                    old.setOpen(nb.getOpen());
                    old.setHigh(nb.getHigh());
                    old.setLow(nb.getLow());
                    old.setClose(nb.getClose());
                    old.setVolume(nb.getVolume());
                    old.setAmount(nb.getAmount());
                    updates.add(old);
                } else {
                    inserts.add(nb);
                }
            }
            if (!inserts.isEmpty()) {
                barMapper.insertBatch(inserts);
            }
            for (DailyBar b : updates) {
                barMapper.update(b);
            }
            upserted += inserts.size() + updates.size();

            EtfInfo etf = etfMapper.selectByCode(code);
            if (etf == null) {
                etf = new EtfInfo(code, code, "MANUAL", "", "手动导入", false, false, true, "MANUAL_CSV", null);
                etfMapper.insert(etf);
            } else {
                if (!"EASTMONEY".equals(etf.getSource()) && !"DEMO".equals(etf.getSource())) {
                    etf.setSource("MANUAL_CSV");
                }
                etfMapper.updateInfo(etf);
            }
            EtfInfo saved = etfMapper.selectByCode(code);
            saved.setLatestDate(latestByCode.get(code));
            saved.setBarCount((int) barMapper.countByCode(code));
            etfMapper.updateInfo(saved);
            writeCsvFile(code);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("rows", upserted);
        res.put("codes", new ArrayList<>(countByCode.keySet()));
        res.put("message", String.format("导入 %s 完成:共 %d 行,涉及 %d 个代码", fileName, upserted, countByCode.size()));
        return res;
    }

    /** 落盘 data/{code}.csv(全量,UTF-8,无 BOM) */
    @Override
    public void writeCsvFile(String code) {
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(code + ".csv");
            List<DailyBar> bars = barMapper.selectByCodeOrderByDateAsc(code);
            StringBuilder sb = new StringBuilder("code,date,open,high,low,close,volume,amount\n");
            for (DailyBar b : bars) {
                sb.append(b.getCode()).append(',')
                        .append(b.getTradeDate()).append(',')
                        .append(num(b.getOpen())).append(',')
                        .append(num(b.getHigh())).append(',')
                        .append(num(b.getLow())).append(',')
                        .append(num(b.getClose())).append(',')
                        .append(num(b.getVolume())).append(',')
                        .append(num(b.getAmount())).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写出行情CSV失败:" + e.getMessage(), e);
        }
    }

    private String num(Double v) {
        if (v == null) {
            return "";
        }
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf(v.longValue());
        }
        return String.valueOf(v);
    }

    private String httpGetWithRetry(String url, int attempts) {
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://quote.eastmoney.com/")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    return resp.body();
                }
                last = new BadRequestException("行情接口 HTTP " + resp.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BadRequestException("请求被中断");
            } catch (IOException e) {
                last = new BadRequestException("网络请求失败:" + e.getMessage());
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw last != null ? last : new BadRequestException("网络请求失败");
    }
}
