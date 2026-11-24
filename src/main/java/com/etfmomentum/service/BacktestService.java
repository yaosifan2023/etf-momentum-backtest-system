package com.etfmomentum.service;

import com.etfmomentum.dto.RunRequest;

import java.util.Map;

/** 业务层接口:回测编排(校验数据 → 运行引擎 → 持久化 → 导出) */
public interface BacktestService {

    /** 历史回测(真实数据);支持零成本/参数覆盖的附加实验 */
    Map<String, Object> runBacktest(RunRequest req);

    /** B 样本复算(虚构教学数据 DEMO_A,复现指南 6.6 演算) */
    Map<String, Object> runSampleCheck();
}
