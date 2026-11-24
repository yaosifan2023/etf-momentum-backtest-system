package com.etfmomentum.controller;

import com.etfmomentum.service.AiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** AI 助手:前端 → 本接口(服务端持有密钥)→ 智谱 GLM;工具调用由前端执行后回传 */
@RestController
public class AiChatController {

    private final AiService aiService;

    public AiChatController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/api/ai/configured")
    public Map<String, Object> configured() {
        return Map.of("configured", aiService.configured());
    }

    @PostMapping("/api/ai/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        if (req.toolResults != null && !req.toolResults.isEmpty()) {
            return aiService.chatWithToolResults(req.question, req.context, req.history,
                    req.toolCalls, req.toolResults);
        }
        return aiService.chat(req.question, req.context, req.history,
                req.tools == null ? Boolean.TRUE : req.tools);
    }

    public static class ChatRequest {
        public String question;
        public String context;
        public List<Map<String, String>> history;
        public Boolean tools;                       // 是否允许模型调用页面工具
        public List<Map<String, Object>> toolCalls; // 上一轮模型返回的工具调用
        public List<Map<String, String>> toolResults; // 前端执行结果 [{id, content}]
    }
}
