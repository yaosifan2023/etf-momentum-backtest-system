package com.etfmomentum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ETF 动量轮动:因子 / 策略 / 历史回测与交互展示系统(课程作业 C 阶段)。
 * 课题:T03.01 相对动量轮动 —— ETF 池内按 W 日动量排名,持有前 K 只,
 * 防御开关触发时转入现金/防御资产;与单资产买入持有对照。
 */
@SpringBootApplication
public class EtfMomentumApplication {
    public static void main(String[] args) {
        SpringApplication.run(EtfMomentumApplication.class, args);
    }
}
