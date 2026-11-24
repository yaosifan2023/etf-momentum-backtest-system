package com.etfmomentum.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** 业务层接口:行情数据抓取与管理(东财接口 / CSV 导入) */
public interface MarketDataService {

    /** 下载/更新单只 ETF 全部日线(东方财富,日频前复权) */
    Map<String, Object> refresh(String code);

    /** 更新候选池全部 ETF(逐只容错) */
    List<Map<String, Object>> refreshAll();

    /** 添加任意 ETF 代码并抓取其历史行情(自动识别沪/深市场) */
    Map<String, Object> addEtf(String code);

    /** 手动导入 CSV(格式:code,date,open,high,low,close,volume,amount) */
    Map<String, Object> importCsv(InputStream in, String fileName);

    /** 落盘 data/{code}.csv(全量,UTF-8) */
    void writeCsvFile(String code);
}
