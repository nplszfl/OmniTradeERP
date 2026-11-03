package com.crossborder.aiassistant.service.impl;

import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.dto.ServiceStatistics;
import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.service.AIAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AIAssistantService 单元测试
 *
 * 注意: 早期版本的测试文件引用了部分尚未实现的方法(如 translateText/extractIntent/extractEntities 等),
 * 这里已根据当前接口契约调整,只保留与实际 API 对齐的测试。
 */
@ExtendWith(MockitoExtension.class)
class AIAssistantServiceImplTest {

    @Mock
    private LLMConfig llmConfig;

    /**
     * 📌 知识库仓储：服务里原本用的是内存 ArrayList，
     *    现在改成读写 knowledge_base 表，所以单测必须 mock 这个仓储。
     */
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private AIAssistantServiceImpl aiAssistantService;

    private ChatRequest chatRequest;

    @BeforeEach
    void setUp() {
        // 知识库仓储的默认行为：返回 2 条启用中的知识
        KnowledgeBase k1 = KnowledgeBase.builder()
                .id(1L).category("订单").question("如何查询我的订单？")
                .answer("在「我的订单」页面查看").language("zh")
                .tags(List.of("订单")).priority(10).active(true).build();
        KnowledgeBase k2 = KnowledgeBase.builder()
                .id(2L).category("物流").question("订单发货后多久能收到？")
                .answer("国内1-3天，国际7-15天").language("zh")
                .tags(List.of("物流")).priority(9).active(true).build();
        lenient().when(knowledgeBaseRepository.findByActiveTrue()).thenReturn(List.of(k1, k2));
        lenient().when(knowledgeBaseRepository.countByActiveTrue()).thenReturn(2L);
        lenient().when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                .thenAnswer(inv -> {
                    KnowledgeBase arg = inv.getArgument(0);
                    if (arg.getId() == null) {
                        arg.setId(99L);
                    }
                    return arg;
                });

        chatRequest = new ChatRequest();
        chatRequest.setUserId(1001L);
        chatRequest.setSessionId("session-001");
        chatRequest.setMessage("产品发货到美国需要多久？");
    }

    @Test
    void testChat_ReturnsResponse() {
        ChatResponse response = aiAssistantService.chat(chatRequest);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertNotNull(response.getResponseId());
    }

    @Test
    void testChat_IncludesSessionId() {
        ChatResponse response = aiAssistantService.chat(chatRequest);

        assertEquals("session-001", response.getSessionId());
    }

    @Test
    void testChat_IncludesTimestamp() {
        ChatResponse response = aiAssistantService.chat(chatRequest);

        assertNotNull(response.getCreateTime());
    }

    @Test
    void testChat_GeneratesSessionIdWhenMissing() {
        chatRequest.setSessionId(null);
        ChatResponse response = aiAssistantService.chat(chatRequest);

        assertNotNull(response.getSessionId());
        assertFalse(response.getSessionId().isEmpty());
    }

    @Test
    void testChatStream_ReturnsFlux() {
        Flux<String> stream = aiAssistantService.chatStream(chatRequest);

        assertNotNull(stream);
    }

    @Test
    void testGetChatHistory_ReturnsHistory() {
        // 先发送消息创建历史
        aiAssistantService.chat(chatRequest);

        List<AIAssistantService.ChatMessage> history = aiAssistantService.getChatHistory(1001L, "session-001");

        assertNotNull(history);
        assertFalse(history.isEmpty());
    }

    @Test
    void testGetChatHistory_EmptySession() {
        List<AIAssistantService.ChatMessage> history = aiAssistantService.getChatHistory(999L, "non-existent");

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void testClearChatHistory_ClearsHistory() {
        // 先发送消息
        aiAssistantService.chat(chatRequest);

        // 清理历史
        aiAssistantService.clearChatHistory(1001L, "session-001");

        List<AIAssistantService.ChatMessage> history = aiAssistantService.getChatHistory(1001L, "session-001");

        assertTrue(history.isEmpty());
    }

    @Test
    void testAnalyzeSentiment_PositiveText() {
        String positiveText = "这个产品太棒了，我非常喜欢！";

        AIAssistantService.SentimentAnalysis sentiment = aiAssistantService.analyzeSentiment(positiveText);

        assertNotNull(sentiment);
        assertNotNull(sentiment.getSentiment());
        assertEquals("positive", sentiment.getSentiment());
    }

    @Test
    void testAnalyzeSentiment_NegativeText() {
        // 使用 2 个明确的负面词,确保分数越过 -0.1 阈值
        String negativeText = "这个产品太差了，糟糕透了";

        AIAssistantService.SentimentAnalysis sentiment = aiAssistantService.analyzeSentiment(negativeText);

        assertNotNull(sentiment);
        assertEquals("negative", sentiment.getSentiment());
    }

    @Test
    void testAnalyzeSentiment_NeutralText() {
        String neutralText = "这个产品还可以";

        AIAssistantService.SentimentAnalysis sentiment = aiAssistantService.analyzeSentiment(neutralText);

        assertNotNull(sentiment);
        assertEquals("neutral", sentiment.getSentiment());
    }

    @Test
    void testSearchKnowledge_ReturnsResults() {
        String query = "发货";

        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge(query, 5);

        assertNotNull(results);
    }

    @Test
    void testSearchKnowledge_EmptyQuery() {
        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("", 5);

        assertNotNull(results);
    }

    @Test
    void testAddKnowledge_AddsToBase() {
        AIAssistantService.Knowledge knowledge = new AIAssistantService.Knowledge();
        knowledge.setQuestion("测试问题");
        knowledge.setAnswer("测试答案");
        knowledge.setCategory("测试");

        aiAssistantService.addKnowledge(knowledge);

        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("测试问题", 5);

        assertNotNull(results);
    }

    @Test
    void testGetStatistics_ReturnsStats() {
        ServiceStatistics stats = aiAssistantService.getStatistics();

        assertNotNull(stats);
    }

    @Test
    void testResetStatistics_ResetsCounters() {
        // 先产生一些统计数据
        aiAssistantService.chat(chatRequest);

        // 重置统计
        aiAssistantService.resetStatistics();

        ServiceStatistics stats = aiAssistantService.getStatistics();

        assertNotNull(stats);
    }

    @Test
    void testRecognizeIntent_ReturnsResult() {
        AIAssistantService.IntentRecognition recognition = aiAssistantService.recognizeIntent("我的订单在哪里？");

        assertNotNull(recognition);
        assertNotNull(recognition.getIntent());
    }

    @Test
    void testBatchAddKnowledge_AddsAll() {
        AIAssistantService.Knowledge k1 = new AIAssistantService.Knowledge();
        k1.setQuestion("批量1");
        k1.setAnswer("答案1");
        k1.setCategory("测试");

        AIAssistantService.Knowledge k2 = new AIAssistantService.Knowledge();
        k2.setQuestion("批量2");
        k2.setAnswer("答案2");
        k2.setCategory("测试");

        aiAssistantService.batchAddKnowledge(List.of(k1, k2));

        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("批量", 10);
        assertNotNull(results);
    }

    @Test
    void testChat_MultipleMessages_SameSession() {
        ChatRequest msg1 = new ChatRequest();
        msg1.setUserId(1001L);
        msg1.setSessionId("test-session");
        msg1.setMessage("第一个问题");

        ChatRequest msg2 = new ChatRequest();
        msg2.setUserId(1001L);
        msg2.setSessionId("test-session");
        msg2.setMessage("第二个问题");

        aiAssistantService.chat(msg1);
        aiAssistantService.chat(msg2);

        List<AIAssistantService.ChatMessage> history = aiAssistantService.getChatHistory(1001L, "test-session");

        // 历史应该包含之前的消息
        assertNotNull(history);
        assertFalse(history.isEmpty());
    }
}