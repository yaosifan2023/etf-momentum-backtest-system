package com.etfmomentum.service;

import java.util.Map;

/** 业务层接口:因子实时计算预览(与回测引擎共用 FactorMath) */
public interface FactorService {

    /** 计算某资产最近 N 期因子值(g01_mom_n / g01_ma_ratio / g01_vol_d) */
    Map<String, Object> values(String code, String factorId, Integer window, Integer limit);
}
