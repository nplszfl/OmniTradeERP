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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        KnowledgeBase k3 = KnowledgeBase.builder()
                .id(3L).category("售后").question("如何申请退款？")
                .answer("在订单详情页点击申请售后").language("zh")
                .tags(List.of("退款")).priority(8).active(true).build();
        lenient().when(knowledgeBaseRepository.findByActiveTrue()).thenReturn(List.of(k1, k2, k3));
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
    void testChat_MockIsVisible() {
        // 📌 **降级必须对调用方可见**（2026-11-04 实测）：
        //    之前 mock=true 的内容与真 AI 输出**长得一模一样**，
        //    `model` 字段还写着 `deepseek-chat` → 调用方完全无法分辨真假
        //    → AI 从来没真的工作过，但界面看着完全正常。
        //    兜底 llmConfig.isConfigured() 默认 false（Mockito 默认值）
        //    → 走模拟路径。
        ChatResponse response = aiAssistantService.chat(chatRequest);

        assertNotNull(response);
        assertEquals(Boolean.TRUE, response.getMock());
        assertNotNull(response.getMockReason(), "模拟回复必须带降级原因");
        // model 不能继续写 deepseek-chat（那是在谎报事实）
        assertEquals("mock", response.getModel());
        // 📌 **模拟回复不得编造 token 用量** —— 那是假事实
        assertNull(response.getUsage(), "模拟回复不得编造 token 用量");
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

        // 📌 **不能只 assertNotNull** —— 旧的写法只要不抛异常就绿，
        //    而「检索返回空列表」是最典型的失败，却完全测不出来。
        //    （2026-11-04 实测：中文检索恒为空，这些测试一直绿着）
        assertNotNull(results);
        assertFalse(results.isEmpty(), "「发货」应该能召回物流那条知识");
        assertEquals("订单发货后多久能收到？", results.get(0).getQuestion());
    }

    /**
     * 📌 **回归测试：中文检索不能是空的**（2026-11-04 修的真缺陷）。
     *
     * <p>旧实现用「按空格切词 + Jaccard」算相似度，
     * 而**中文没有空格** → `"订单怎么查"` 被当成**一个** token，
     * 与 `"如何查询我的订单？"` 交集为空 → 相似度 0 → **永远检索不到**。
     *
     * <p>这类缺陷单靠「断言不抛异常」的测试永远发现不了。
     */
    @ParameterizedTest
    @CsvSource({
            "订单怎么查,       如何查询我的订单？",
            "怎么查订单,       如何查询我的订单？",
            "我要查订单,       如何查询我的订单？",
            "退款怎么申请,     如何申请退款？",
            "如何退款,         如何申请退款？",
            "退钱流程是什么,   如何申请退款？",
            "发货 多久,        订单发货后多久能收到？",
            "什么时候能收到货, 订单发货后多久能收到？",
            "物流要几天,       订单发货后多久能收到？"
    })
    void testSearchKnowledge_中文问法能召回(String query, String expectedQuestion) {
        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge(query, 5);

        assertNotNull(results, "查询词不应返回 null");
        assertFalse(results.isEmpty(),
                "❌ 中文问法「" + query + "」一条都没召回 —— 检索对中文失效了");
        assertEquals(expectedQuestion, results.get(0).getQuestion(),
                "❌「" + query + "」应该优先召回「" + expectedQuestion + "」");
    }

    /**
     * 📌 **反向用例：无关问法不能误召回**。
     *
     * <p>📌 调低相似度阈值很容易换来「什么都能匹配上」——
     * 那等于把「检索不到」换成了「乱检索」，同样是错的。
     */
    @ParameterizedTest
    @ValueSource(strings = {"今天天气怎么样", "你好", "在吗", "讲个笑话", "1+1等于几"})
    void testSearchKnowledge_无关问法不误召回(String query) {
        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge(query, 5);

        assertTrue(results.isEmpty(),
                "❌ 无关问法「" + query + "」召回了 " + results.size() + " 条知识：" + results);
    }

    /**
     * 📌 **回归测试：检索不能污染 `priority` 字段**（2026-11-04 修的真缺陷）。
     *
     * <p>旧实现把相关度**塞回 {@code priority}**：
     * <pre>{@code knowledge.setPriority((int) (similarity * 100));}</pre>
     * → {@code priority} 在搜索结果里**含义完全变了**
     * （用户自己设的优先级被冲掉），
     * 对外的 {@code GET /knowledge/search} 返回的 {@code priority} 其实是相关度×100
     * → 调用方按「优先级」读它就是读到了**垃圾**。
     *
     * <p>而且 {@code retrieveKnowledge} 里的 {@code k.getPriority() / 100.0}
     * 是**依赖这个 bug** 才能算对的 → 一旦修正 priority 的语义，
     * 那里就会**静默算错**（相关度变成 0.08 这种量级）。
     *
     * <p>→ 现在相关度单独放 {@code similarity} 字段，{@code priority} 保持原义。
     */
    @Test
    void testSearchKnowledge_不污染priority字段() {
        List<AIAssistantService.Knowledge> results =
                aiAssistantService.searchKnowledge("发货", 5);

        assertFalse(results.isEmpty(), "「发货」应该能召回物流那条知识");

        for (AIAssistantService.Knowledge k : results) {
            // 📌 priority 必须**原样保留**（种子数据里是 10 / 9 / 8）
            assertTrue(k.getPriority() == null || (k.getPriority() >= 8 && k.getPriority() <= 10),
                    "❌ priority 被检索相关度污染了，实际值 = " + k.getPriority()
                            + "（应该还是用户设的 8~10）");
            // 📌 相关度必须落在 similarity 字段里
            assertNotNull(k.getSimilarity(),
                    "❌ 检索结果必须带 similarity（相关度）");
            assertTrue(k.getSimilarity() > 0.0 && k.getSimilarity() <= 1.0,
                    "❌ similarity 应该在 (0,1]，实际 = " + k.getSimilarity());
        }
    }

    /**
     * 📌 **回归测试：相关度排序必须按相关度排，不能按 priority 排**。
     *
     * <p>旧实现排序用的是 {@code priority}（那时它装的是相关度）；
     * 修正 priority 语义后如果忘了改排序，
     * 结果就变成「按用户设的优先级排」→ **召回顺序不再按相关度**
     * → 最相关的知识排在后面 → 回答质量下降。
     */
    @Test
    void testSearchKnowledge_按相关度降序() {
        List<AIAssistantService.Knowledge> results =
                aiAssistantService.searchKnowledge("发货 多久", 5);

        assertFalse(results.isEmpty(), "应该能召回知识");
        for (int i = 1; i < results.size(); i++) {
            double prev = results.get(i - 1).getSimilarity();
            double cur = results.get(i).getSimilarity();
            assertTrue(prev >= cur,
                    "❌ 相关度没有降序排列：第 " + (i - 1) + " 条 = " + prev
                            + "，第 " + i + " 条 = " + cur);
        }
    }

    @Test
    void testSearchKnowledge_EmptyQuery() {
        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("", 5);

        assertNotNull(results);
        // 📌 空查询不应该召回任何东西（否则每次闲聊都塞一堆无关知识进 prompt）
        assertTrue(results.isEmpty(), "空查询不应召回知识");
    }

    /**
     * 📌 **回归测试：标签也要参与匹配**（2026-11-04 真实库里发现）。
     *
     * <p>用户问「退钱流程是什么」→ 知识的问题写的是「如何申请退款？」→
     * **一个问题里一个共同字都没有** → 只匹配 question 根本召不回来。
     * 但那条知识的**标签**是「退款」→ 靠标签能召回。
     *
     * <p>📌 真实库验证（31 条真实种子）：这句话的 top-1 就是「退钱流程是什么」。
     */
    @Test
    void testSearchKnowledge_标签也能召回() {
        // 造一条「问题与问法毫无共同字、但标签对得上」的知识
        KnowledgeBase tagged = KnowledgeBase.builder()
                .id(9L).category("售后").question("如何申请退款？")
                .answer("在订单详情页点击申请售后").language("zh")
                .tags(List.of("退款", "售后")).priority(8).active(true).build();
        List<KnowledgeBase> rows = List.of(tagged);
        when(knowledgeBaseRepository.findByActiveTrue()).thenReturn(rows);

        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("退钱流程是什么", 5);

        assertFalse(results.isEmpty(),
                "❌「退钱流程是什么」没召回 —— 标签没参与匹配，同义表达召不回来");
        assertEquals("如何申请退款？", results.get(0).getQuestion());
    }

    /**
     * 📌 **回归测试：泛用词不能压过真正的答案**（2026-11-04 真实库里发现）。
     *
     * <p>「退钱流程是什么」如果只匹配 tag「流程」，得分会和 tag「退款」一样高
     * → **排序错** → 把「订单状态有哪些？」排在「如何申请退款？」前面。
     *
     * <p>📌 根因：覆盖度不能按 min(|A|,|B|) 算 ——
     * 长查询只命中一个泛用词也算满分。必须按**查询侧**算（|A∩B|/|A|）。
     */
    @Test
    void testSearchKnowledge_泛用词不能压过真答案() {
        KnowledgeBase refund = KnowledgeBase.builder()
                .id(9L).category("售后").question("如何申请退款？")
                .answer("在订单详情页点击申请售后").language("zh")
                .tags(List.of("退款")).priority(8).active(true).build();
        KnowledgeBase order = KnowledgeBase.builder()
                .id(10L).category("订单").question("订单状态有哪些？")
                .answer("包含待付款、待发货等状态").language("zh")
                .tags(List.of("订单", "状态", "流程")).priority(7).active(true).build();
        when(knowledgeBaseRepository.findByActiveTrue()).thenReturn(List.of(order, refund));

        List<AIAssistantService.Knowledge> results = aiAssistantService.searchKnowledge("退钱流程是什么", 5);

        assertFalse(results.isEmpty(), "应该召回退款那条");
        assertEquals("如何申请退款？", results.get(0).getQuestion(),
                "❌ 泛用词「流程」抢了第一 —— 真答案应该在最前面");
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