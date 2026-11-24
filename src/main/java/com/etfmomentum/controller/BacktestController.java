package com.etfmomentum.controller;

import com.etfmomentum.service.BacktestService;
import com.etfmomentum.dto.RunRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 回测执行入口 */
@RestController
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /** 历史回测(真实数据);支持零成本/参数覆盖的附加实验 */
    @PostMapping("/api/backtest/run")
    public Map<String, Object> run(@RequestBody RunRequest req) {
        return backtestService.runBacktest(req);
    }

    /** B 样本复算(虚构教学数据 DEMO_A,复现指南 6.6 演算) */
    @PostMapping("/api/backtest/sample-check")
    public Map<String, Object> sampleCheck() {
        return backtestService.runSampleCheck();
    }
}
