package com.crossborder.aiassistant.controller;

import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.service.AIAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI智能客服控制器
 */
@RestController
@RequestMapping("/api/v1/ai-assistant")
@RequiredArgsConstructor
@Tag(name = "AI智能客服", description = "AI智能客服相关接口")
public class AIAssistantController {

    private final AIAssistantService aiAssistantService;

    @Operation(summary = "AI对话（单次）")
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return aiAssistantService.chat(request);
    }

    @Operation(summary = "AI对话（流式响应）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return aiAssistantService.chatStream(request);
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/history/{userId}")
    public List<AIAssistantService.ChatMessage> getChatHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) String sessionId) {
        return aiAssistantService.getChatHistory(userId, sessionId);
    }

    @Operation(summary = "清空对话历史")
    @DeleteMapping("/history/{userId}")
    public String clearChatHistory(
            @PathVariable Long userId,
            @RequestParam String sessionId) {
        aiAssistantService.clearChatHistory(userId, sessionId);
        return "对话历史已清空！";
    }

    @Operation(summary = "分析情感")
    @PostMapping("/sentiment")
    public AIAssistantService.SentimentAnalysis analyzeSentiment(@RequestBody String text) {
        return aiAssistantService.analyzeSentiment(text);
    }

    @Operation(summary = "识别意图")
    @PostMapping("/intent")
    public AIAssistantService.IntentRecognition recognizeIntent(@RequestBody String text) {
        return aiAssistantService.recognizeIntent(text);
    }

    @Operation(summary = "添加知识库")
    @PostMapping("/knowledge")
    public String addKnowledge(@RequestBody AIAssistantService.Knowledge knowledge) {
        aiAssistantService.addKnowledge(knowledge);
        return "知识添加成功！";
    }

    @Operation(summary = "批量添加知识库")
    @PostMapping("/knowledge/batch")
    public String batchAddKnowledge(@RequestBody List<AIAssistantService.Knowledge> knowledges) {
        aiAssistantService.batchAddKnowledge(knowledges);
        return "批量知识添加成功！";
    }

    @Operation(summary = "检索知识库")
    @GetMapping("/knowledge/search")
    public List<AIAssistantService.Knowledge> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        return aiAssistantService.searchKnowledge(query, topK);
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public String health() {
        return "AI智能客服服务运行中！🔥";
    }
}
