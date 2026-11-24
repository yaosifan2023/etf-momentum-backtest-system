package com.etfmomentum.service.impl;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 GLM 问答代理(支持工具调用):
 * 密钥仅保存在服务端(环境变量 ZHIPU_API_KEY 或 config/zhipu-key.txt),前端接触不到。
 * 工具执行发生在前端(改表单/跑回测等),后端只负责协议转发与结果汇总。
 */
@Service
public class AiServiceImpl implements AiService {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String SYSTEM_PROMPT =
            "你是「量化回测分析助手」,嵌在 ETF 动量轮动回测系统页面右下角,既能分析数据也能替用户执行操作。\n"
            + "分析时:基于页面数据摘要回答,数值引用准确、结论克制、指出局限(样本内表现不代表未来、成本口径、参数敏感性),不构成投资建议。\n"
            + "操作时:用户要求修改策略参数、设置回测区间、运行回测、抓取行情、添加ETF、切换页面时,调用对应工具而不是只给文字建议;"
            + "多个操作可在一次回复中并行调用;工具结果返回后,用一两句话确认做了什么(旧值→新值)。"
            + "回答用简体中文,简洁专业,适当用要点列表。";

    private final ObjectMapper om;
    private final HttpClient http;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.key-file:./config/zhipu-key.txt}")
    private String keyFile;

    @Value("${app.ai.model:glm-5.3}")
    private String model;

    public AiServiceImpl(ObjectMapper om) {
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public boolean configured() {
        return !resolveKey().isEmpty();
    }

    @Override
    public Map<String, Object> chat(String question, String context, List<Map<String, String>> history,
                                    boolean toolsEnabled) {
        String q = requireQuestion(question);
        List<Object> messages = baseMessages(context, history, q);
        Map<String, Object> body = baseBody();
        body.put("messages", messages);
        if (toolsEnabled) {
            body.put("tools", toolDefs());
        }
        JsonNode message = callZhipu(body);
        return parseAssistant(message);
    }

    @Override
    public Map<String, Object> chatWithToolResults(String question, String context,
                                                   List<Map<String, String>> history,
                                                   List<Map<String, Object>> toolCalls,
                                                   List<Map<String, String>> toolResults) {
        String q = requireQuestion(question);
        List<Object> messages = baseMessages(context, history, q);

        // assistant 的工具调用消息
        List<Map<String, Object>> calls = new ArrayList<>();
        if (toolCalls != null) {
            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", String.valueOf(tc.get("name")));
                fn.put("arguments", tc.get("arguments") == null ? "{}"
                        : om.valueToTree(tc.get("arguments")).toString());
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", String.valueOf(tc.get("id")));
                call.put("type", "function");
                call.put("function", fn);
                calls.add(call);
            }
        }
        if (calls.isEmpty()) {
            throw new BadRequestException("toolResults 回传时缺少 toolCalls");
        }
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("tool_calls", calls);
        messages.add(assistantMsg);

        // 工具结果消息
        if (toolResults != null) {
            for (Map<String, String> r : toolResults) {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("role", "tool");
                tm.put("tool_call_id", String.valueOf(r.get("id")));
                tm.put("content", clip(r.get("content"), 3000));
                messages.add(tm);
            }
        }

        Map<String, Object> body = baseBody();
        body.put("messages", messages);
        JsonNode message = callZhipu(body);
        return parseAssistant(message);
    }

    // ==================== 内部工具 ====================

    private String requireQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("请输入问题");
        }
        String q = question.trim();
        return q.length() > 1000 ? q.substring(0, 1000) : q;
    }

    private List<Object> baseMessages(String context, List<Map<String, String>> history, String q) {
        List<Object> messages = new ArrayList<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT + "\n\n[当前页面数据摘要]\n"
                + (context != null && !context.isBlank() ? clip(context, 6000) : "(无)"));
        messages.add(sys);
        if (history != null) {
            int from = Math.max(0, history.size() - 6);
            for (Map<String, String> h : history.subList(from, history.size())) {
                String role = "assistant".equals(h.get("role")) ? "assistant" : "user";
                String content = h.get("content");
                if (content != null && !content.isBlank()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", role);
                    m.put("content", clip(content, 2000));
                    messages.add(m);
                }
            }
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", q);
        messages.add(user);
        return messages;
    }

    private Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.5);
        body.put("max_tokens", 1600);
        body.put("thinking", Map.of("type", "disabled"));
        return body;
    }

    private JsonNode callZhipu(Map<String, Object> body) {
        String key = resolveKey();
        if (key.isEmpty()) {
            throw new BadRequestException("AI 功能未配置密钥:请在项目 config/zhipu-key.txt 写入智谱 API Key 后重启(详见 README)");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new BadRequestException("AI 接口返回 HTTP " + resp.statusCode() + ":" + clip(resp.body(), 300));
            }
            JsonNode root = om.readTree(resp.body());
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode()) {
                throw new BadRequestException("AI 接口返回异常:" + clip(resp.body(), 300));
            }
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("请求被中断");
        } catch (IOException e) {
            throw new BadRequestException("AI 网络请求失败:" + e.getMessage());
        }
    }

    private Map<String, Object> parseAssistant(JsonNode message) {
        // 工具调用分支
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && toolCalls.size() > 0) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (JsonNode tc : toolCalls) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", tc.path("id").asText(""));
                c.put("name", tc.path("function").path("name").asText(""));
                Object args = null;
                try {
                    args = om.readValue(tc.path("function").path("arguments").asText("{}"), Map.class);
                } catch (IOException ignore) {
                    args = new LinkedHashMap<>();
                }
                c.put("arguments", args);
                calls.add(c);
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("pending", true);
            res.put("toolCalls", calls);
            return res;
        }
        // 普通回答
        String content = message.path("content").asText("");
        if (content.isEmpty()) {
            content = message.path("reasoning_content").asText("");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("answer", content);
        res.put("model", model);
        return res;
    }

    /** 前端可执行的白名单工具(协议与 OpenAI function calling 兼容) */
    private List<Map<String, Object>> toolDefs() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("update_strategy_form", "修改策略配置表单(仅传入要改的字段)",
                prop("momentumWindow", "integer", "动量窗口W(2~500交易日)"),
                prop("holdingCount", "integer", "持有数量K(1~10)"),
                prop("rebalanceFreq", "string", "调仓频率:WEEKLY或MONTHLY"),
                prop("defensiveEnabled", "boolean", "防御开关"),
                prop("defensiveThreshold", "number", "防御阈值(最强动量低于此值触发)"),
                prop("defensiveMode", "string", "防御模式:CASH或ASSET"),
                prop("trendFilterEnabled", "boolean", "趋势过滤开关"),
                prop("trendWindow", "integer", "均线窗口d"),
                prop("trendThreshold", "number", "均线比值阈值"),
                prop("commissionRate", "number", "手续费率(小数,如0.001)"),
                prop("minimumCommission", "number", "最低手续费(元)"),
                prop("slippageRate", "number", "滑点率(小数,如0.0005)"),
                prop("initialCash", "number", "初始资金(元)"),
                prop("benchmarkAsset", "string", "基准资产代码"),
                prop("name", "string", "策略名称")));
        tools.add(tool("set_backtest_dates", "设置回测运行页的起止日期",
                prop("start", "string", "开始日期 YYYY-MM-DD"),
                prop("end", "string", "结束日期 YYYY-MM-DD")));
        tools.add(tool("save_and_run_backtest", "保存当前策略表单为新版本并立即运行历史回测,完成后跳转结果页",
                prop("note", "string", "运行备注(可选)")));
        tools.add(tool("run_sample_check", "运行B样本复算,验证引擎与指南手工演算一致"));
        tools.add(tool("refresh_market_data", "联网抓取行情数据;不传code则抓取全部候选池",
                prop("code", "string", "ETF代码(可选)")));
        tools.add(tool("add_etf", "添加一只新ETF并抓取其全部历史行情",
                prop("code", "string", "6位ETF代码")));
        tools.add(tool("navigate", "切换到指定页面",
                prop("page", "string", "页面:overview/data/factors/strategy/backtest/history/result")));
        tools.add(tool("get_run_detail", "查询某次回测运行的详细信息(不传runId则查最近一次)",
                prop("runId", "string", "运行ID(可选)")));
        return tools;
    }

    private Map<String, Object> tool(String name, String desc, Map<String, Object>... props) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        Map<String, Object> pm = new LinkedHashMap<>();
        for (Map<String, Object> p : props) {
            pm.put(String.valueOf(p.get("name")), p);
        }
        parameters.put("properties", pm);
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", desc);
        fn.put("parameters", parameters);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        t.put("function", fn);
        return t;
    }

    private Map<String, Object> prop(String name, String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("type", type);
        p.put("description", desc);
        return p;
    }

    /** 密钥解析顺序:application.yml 配置 > 环境变量 > 本地密钥文件 */
    private String resolveKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String env = System.getenv("ZHIPU_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        try {
            Path p = Paths.get(keyFile);
            if (Files.exists(p)) {
                String s = Files.readString(p, StandardCharsets.UTF_8).trim();
                s = s.replace("﻿", "").replace("\"", "").replaceAll("\\s+", "");
                return s;
            }
        } catch (IOException ignore) {
            // 读取失败视为未配置
        }
        return "";
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…(截断)";
    }
}
