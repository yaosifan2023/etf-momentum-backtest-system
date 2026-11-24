package com.etfmomentum.service;

import java.util.List;
import java.util.Map;

/** 业务层接口:AI 助手(智谱 GLM,后端代理,支持工具调用/页面操作) */
public interface AiService {

    /**
     * 智能问答;toolsEnabled=true 时允许模型返回工具调用(改表单/跑回测等),
     * 此时返回 {pending:true, toolCalls:[{id,name,arguments}]},由前端执行后回传。
     */
    Map<String, Object> chat(String question, String context, List<Map<String, String>> history, boolean toolsEnabled);

    /** 前端执行完工具后回传结果,获取最终回答 */
    Map<String, Object> chatWithToolResults(String question, String context, List<Map<String, String>> history,
                                            List<Map<String, Object>> toolCalls,
                                            List<Map<String, String>> toolResults);

    /** 是否已配置密钥 */
    boolean configured();
}
