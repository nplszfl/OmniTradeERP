package com.crossborder.aiassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI智能客服系统 - v1.5.0 核心AI模块
 *
 * 主要功能：
 * 1. 智能对话 - 基于LLM的智能问答
 * 2. 知识库 - 产品信息、政策、FAQ等
 * 3. 多语言支持 - 中英文无缝切换
 * 4. 对话历史 - 完整的对话追踪
 * 5. 智能路由 - 根据问题类型自动路由
 * 6. 情感分析 - 识别客户情绪
 *
 * 技术实现：
 * - LLM API集成（OpenAI/DeepSeek）
 * - 向量数据库（知识库检索）
 * - RAG（检索增强生成）
 * - 流式响应（SSE）
 *
 * @author 火球鼠
 * @since 2026-03-17
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class AIAssistantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIAssistantServiceApplication.class, args);
    }
}
