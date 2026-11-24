package com.etfmomentum.service;

import java.util.List;
import java.util.Map;

/** 业务层接口:行情数据质量检查(确定性规则计算,AI 只负责解释结果) */
public interface DataQualityService {

    /**
     * 对候选池全部真实 ETF 执行数据体检。
     * 返回:{score, checked, findings:[{level,code,date,field,value,rule}], summary}
     * level: SEVERE / WARN / INFO;评分与规则由程序固定,AI 不参与打分。
     */
    Map<String, Object> check(double abnormalReturnThreshold);
}
