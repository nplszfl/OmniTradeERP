package com.crossborder.aiassistant.service.impl;

import com.crossborder.aiassistant.entity.IntentRecognitionLog;
import com.crossborder.aiassistant.entity.SentimentAnalysisLog;
import com.crossborder.aiassistant.entity.UserFeedback;
import com.crossborder.aiassistant.repository.IntentRecognitionLogRepository;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.repository.SentimentAnalysisLogRepository;
import com.crossborder.aiassistant.repository.UserFeedbackRepository;
import com.crossborder.aiassistant.service.AIAssistantService;
import com.crossborder.aiassistant.service.ChatSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 助手「表有 DDL 没写路径」的落库测试
 *
 * <p>📌 **2026-11-20 新增**：本文件钉住三个此前<b>完全没有写路径</b>的表：
 * <ul>
 *   <li>{@code sentiment_analysis_log} —— 情感分析只算不存 → 永远 0 行；</li>
 *   <li>{@code intent_recognition_log} —— 意图识别只算不存 → 永远 0 行；</li>
 *   <li>{@code user_feedback} —— 全仓<b>一行代码</b>都没有 → 永远 0 行。</li>
 * </ul>
 *
 * <p>📌 判据的方向很重要：这些测试<b>不能</b>断言「落库成功」就完事 ——
 * 还得钉住「落库失败时主流程不受影响」（写日志是副业，不能把主业带崩）。
 */
@ExtendWith(MockitoExtension.class)
class AIAssistantServiceImplPersistenceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private SentimentAnalysisLogRepository sentimentAnalysisLogRepository;

    @Mock
    private IntentRecognitionLogRepository intentRecognitionLogRepository;

    @Mock
    private UserFeedbackRepository userFeedbackRepository;

    @Mock
    private ChatSessionStore chatSessionStore;

    @InjectMocks
    private AIAssistantServiceImpl aiAssistantService;

    @BeforeEach
    void setUp() {
        // 📌 落库要「表现得像真的存了」—— 回填自增主键，
        //    否则 submitFeedback 返回的 id 永远是 null（测不出真实行为）。
        // 📌 用 lenient()：不是每个用例都会用到全部三个仓储
        //    （比如「拒绝非法反馈类型」的用例根本不落库），
        //    Mockito 严格模式会因「多余的 stub」直接判失败。
        lenient().when(sentimentAnalysisLogRepository.save(any(SentimentAnalysisLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(intentRecognitionLogRepository.save(any(IntentRecognitionLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userFeedbackRepository.save(any(UserFeedback.class))).thenAnswer(inv -> {
            UserFeedback arg = inv.getArgument(0);
            if (arg.getId() == null) {
                arg.setId(1001L);
            }
            return arg;
        });
    }

    // ==================== 情感分析落库 ====================

    @Test
    @DisplayName("情感分析必须落库（表 sentiment_analysis_log 此前永远 0 行）")
    void analyzeSentiment_PersistsToLogTable() {
        aiAssistantService.analyzeSentiment("这个服务太差了，我很失望", 42L, "sess-1");

        ArgumentCaptor<SentimentAnalysisLog> captor =
                ArgumentCaptor.forClass(SentimentAnalysisLog.class);
        verify(sentimentAnalysisLogRepository, times(1)).save(captor.capture());

        SentimentAnalysisLog row = captor.getValue();
        assertNotNull(row.getSentiment(), "落库行必须有情感结论");
        assertEquals("negative", row.getSentiment(), "「太差/失望」应判为 negative");
        assertEquals(42L, row.getUserId());
        assertEquals("sess-1", row.getSessionId());
        assertNotNull(row.getScore(), "分数必须落库（运营看板要用）");
        assertNotNull(row.getConfidence(), "置信度必须落库");
    }

    @Test
    @DisplayName("无上下文的情感分析也要落库（userId/sessionId 可空，不能因此跳过写库）")
    void analyzeSentiment_WithoutContext_StillPersists() {
        aiAssistantService.analyzeSentiment("还行吧");

        ArgumentCaptor<SentimentAnalysisLog> captor =
                ArgumentCaptor.forClass(SentimentAnalysisLog.class);
        verify(sentimentAnalysisLogRepository, times(1)).save(captor.capture());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    @DisplayName("落库失败不能拖垮情感分析主流程（DB 抖一下不能让人工分析也失败）")
    void analyzeSentiment_DBFailure_StillReturnsResult() {
        when(sentimentAnalysisLogRepository.save(any(SentimentAnalysisLog.class)))
                .thenThrow(new RuntimeException("DB down"));

        AIAssistantService.SentimentAnalysis result =
                aiAssistantService.analyzeSentiment("很好，非常满意", 42L, "sess-1");

        assertNotNull(result, "落库失败时分析结果仍必须返回");
        assertEquals("positive", result.getSentiment());
    }

    // ==================== 意图识别落库 ====================

    @Test
    @DisplayName("意图识别必须落库（表 intent_recognition_log 此前永远 0 行）")
    void recognizeIntent_PersistsToLogTable() {
        aiAssistantService.recognizeIntent("我的订单在哪里？", 42L, "sess-2");

        ArgumentCaptor<IntentRecognitionLog> captor =
                ArgumentCaptor.forClass(IntentRecognitionLog.class);
        verify(intentRecognitionLogRepository, times(1)).save(captor.capture());

        IntentRecognitionLog row = captor.getValue();
        assertEquals("order_inquiry", row.getRecognizedIntent());
        assertEquals(42L, row.getUserId());
        assertEquals("sess-2", row.getSessionId());
        assertNotNull(row.getProcessingTimeMs(), "处理耗时必须落库");
        assertNotNull(row.getConfidence());
    }

    @Test
    @DisplayName("落库失败不能拖垮意图识别主流程")
    void recognizeIntent_DBFailure_StillReturnsResult() {
        when(intentRecognitionLogRepository.save(any(IntentRecognitionLog.class)))
                .thenThrow(new RuntimeException("DB down"));

        AIAssistantService.IntentRecognition result =
                aiAssistantService.recognizeIntent("我的订单在哪里？", 42L, "sess-2");

        assertNotNull(result);
        assertEquals("order_inquiry", result.getIntent());
    }

    // ==================== 意图识别本身的正确性 ====================

    @Test
    @DisplayName("「退货/退款」必须是独立意图 —— 此前被 product_inquiry 覆盖")
    void recognizeIntent_ReturnIsItsOwnIntent() {
        // 📌 真实缺陷：原代码连着两行 put("product_inquiry", ...)
        //    → 第二个覆盖第一个 → 「退货」被误判成 product_inquiry。
        AIAssistantService.IntentRecognition r = aiAssistantService.recognizeIntent("我要退货");

        assertEquals("return_inquiry", r.getIntent(),
                "「退货」必须归到 return_inquiry，而不是 product_inquiry");
    }

    @Test
    @DisplayName("「产品/商品」关键词不能被退货那组覆盖掉")
    void recognizeIntent_ProductKeywordsStillWork() {
        AIAssistantService.IntentRecognition r = aiAssistantService.recognizeIntent("这个商品怎么样");

        assertEquals("product_inquiry", r.getIntent(),
                "「商品」必须仍能命中 product_inquiry（此前被同名 key 覆盖 → 彻底失灵）");
    }

    @Test
    @DisplayName("意图识别结果必须可复现（此前 HashMap 无序 + 同分不覆盖 → 结果漂移）")
    void recognizeIntent_IsDeterministic() {
        String text = "订单物流价格";
        String first = aiAssistantService.recognizeIntent(text).getIntent();
        for (int i = 0; i < 20; i++) {
            assertEquals(first, aiAssistantService.recognizeIntent(text).getIntent(),
                    "同样的输入必须每次得到同样的意图");
        }
    }

    // ==================== 用户反馈 ====================

    @Test
    @DisplayName("用户反馈必须落库（表 user_feedback 此前全仓无写路径）")
    void submitFeedback_PersistsToTable() {
        AIAssistantService.Feedback fb = new AIAssistantService.Feedback();
        fb.setUserId(42L);
        fb.setSessionId("sess-3");
        fb.setMessageId(77L);
        fb.setFeedbackType("helpful");
        fb.setRating(5);
        fb.setComment("回答很准确");

        AIAssistantService.Feedback saved = aiAssistantService.submitFeedback(fb);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackRepository, times(1)).save(captor.capture());

        UserFeedback row = captor.getValue();
        assertEquals(42L, row.getUserId());
        assertEquals("sess-3", row.getSessionId());
        assertEquals(77L, row.getMessageId());
        assertEquals("helpful", row.getFeedbackType());
        assertEquals(5, row.getRating());
        assertNotNull(saved.getId(), "落库后必须回填主键");
    }

    @Test
    @DisplayName("反馈类型必须白名单校验 —— 否则统计里会冒出第 4、第 5 种类型")
    void submitFeedback_RejectsUnknownType() {
        AIAssistantService.Feedback fb = new AIAssistantService.Feedback();
        fb.setUserId(42L);
        fb.setFeedbackType("whatever");

        assertThrows(IllegalArgumentException.class, () -> aiAssistantService.submitFeedback(fb));
        verify(userFeedbackRepository, never()).save(any(UserFeedback.class));
    }

    @Test
    @DisplayName("反馈类型大小写/空格要归一化 —— 否则同一种反馈被拆成两种")
    void submitFeedback_NormalizesType() {
        AIAssistantService.Feedback fb = new AIAssistantService.Feedback();
        fb.setUserId(42L);
        fb.setFeedbackType("  HELPFUL  ");

        aiAssistantService.submitFeedback(fb);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackRepository).save(captor.capture());
        assertEquals("helpful", captor.getValue().getFeedbackType());
    }

    @Test
    @DisplayName("评分必须落在 1~5（DDL 是 INT，不校验就可能存 0 或 99 污染统计）")
    void submitFeedback_RejectsOutOfRangeRating() {
        AIAssistantService.Feedback tooHigh = new AIAssistantService.Feedback();
        tooHigh.setUserId(42L);
        tooHigh.setFeedbackType("helpful");
        tooHigh.setRating(99);

        assertThrows(IllegalArgumentException.class, () -> aiAssistantService.submitFeedback(tooHigh));

        AIAssistantService.Feedback tooLow = new AIAssistantService.Feedback();
        tooLow.setUserId(42L);
        tooLow.setFeedbackType("helpful");
        tooLow.setRating(0);

        assertThrows(IllegalArgumentException.class, () -> aiAssistantService.submitFeedback(tooLow));
        verify(userFeedbackRepository, never()).save(any(UserFeedback.class));
    }

    @Test
    @DisplayName("用户ID必须有 —— 否则反馈无法归因到人")
    void submitFeedback_RequiresUserId() {
        AIAssistantService.Feedback fb = new AIAssistantService.Feedback();
        fb.setFeedbackType("helpful");

        assertThrows(IllegalArgumentException.class, () -> aiAssistantService.submitFeedback(fb));
        verify(userFeedbackRepository, never()).save(any(UserFeedback.class));
    }

    @Test
    @DisplayName("空反馈对象不能被当成合法请求")
    void submitFeedback_RejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> aiAssistantService.submitFeedback(null));
    }

    @Test
    @DisplayName("评分可以不填（只点「有帮助」不评分是正常用法）")
    void submitFeedback_RatingIsOptional() {
        AIAssistantService.Feedback fb = new AIAssistantService.Feedback();
        fb.setUserId(42L);
        fb.setFeedbackType("not_helpful");

        aiAssistantService.submitFeedback(fb);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackRepository).save(captor.capture());
        assertNull(captor.getValue().getRating());
    }

    @Test
    @DisplayName("实体字段必须和 DDL 列名对得上（列名错位 → 运行时才炸）")
    void entityColumns_MatchDDL() {
        SentimentAnalysisLog s = new SentimentAnalysisLog();
        s.setUserId(1L);
        s.setSessionId("x");
        s.setText("t");
        s.setSentiment("positive");
        s.setScore(java.math.BigDecimal.valueOf(0.5));
        s.setConfidence(java.math.BigDecimal.valueOf(0.5));

        IntentRecognitionLog i = new IntentRecognitionLog();
        i.setUserId(1L);
        i.setSessionId("x");
        i.setText("t");
        i.setRecognizedIntent("order_inquiry");
        i.setProcessingTimeMs(5);
        i.setEntities("[]");

        UserFeedback u = new UserFeedback();
        u.setUserId(1L);
        u.setSessionId("x");
        u.setMessageId(2L);
        u.setFeedbackType("helpful");
        u.setRating(4);
        u.setComment("c");

        assertNotNull(s.getSentiment());
        assertNotNull(i.getRecognizedIntent());
        assertNotNull(u.getFeedbackType());
        assertTrue(List.of("helpful", "not_helpful", "incorrect")
                .contains(u.getFeedbackType()));
    }
}
