package com.crossborder.erp.supplier.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.entity.SupplierEvaluation;
import com.crossborder.erp.supplier.mapper.SupplierEvaluationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 供应商绩效评估服务单元测试
 *
 * 📌 这个服务是「前端表单 ↔ 后端计算」契约最脆的地方：
 *   - totalScore 由后端按**加权平均**算（质量30 + 交付25 + 价格20 + 服务15 + 技术10），
 *     **不是**五项简单平均 —— 前端算错的话，页面显示的分和库里存的对不上
 *   - conclusion 也由后端按分数档位推导（≥4.5 EXCELLENT / ≥4.0 GOOD /
 *     ≥3.0 ACCEPTABLE / 否则 POOR），**没有 FAIR 这一档**
 *   - evaluateAt 由后端填当前时间 —— 前端传什么都不作数
 *   - rating = 最近 10 次评估 totalScore 的平均，会回写 t_supplier.rating
 *
 * 这些行为以前**完全没有测试**，所以前端写错了也没人发现。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("供应商绩效评估服务单元测试")
class SupplierEvaluationServiceTest {

    @Mock
    private SupplierEvaluationMapper evaluationMapper;

    @Mock
    private SupplierService supplierService;

    @InjectMocks
    private SupplierEvaluationService evaluationService;

    @BeforeAll
    static void initTableInfo() {
        // 📌 MyBatis-Plus 3.5.x 坑：断言 LambdaQueryWrapper 条件前必须初始化
        //    TableInfo，否则 eq(SFunction, ...) 会因找不到 lambda 缓存抛
        //    「can not find lambda cache for this entity」
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.crossborder.erp.supplier.mapper.SupplierEvaluationMapper");
        TableInfoHelper.initTableInfo(assistant, SupplierEvaluation.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(evaluationService, "baseMapper", evaluationMapper);
    }

    // ---------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------

    private SupplierEvaluation eval(Integer q, Integer d, Integer p, Integer s, Integer t) {
        SupplierEvaluation e = new SupplierEvaluation();
        e.setQualityScore(q);
        e.setDeliveryScore(d);
        e.setPriceScore(p);
        e.setServiceScore(s);
        e.setTechScore(t);
        e.setSupplierId(5001L);
        return e;
    }

    /** 构造一条已落库的评估记录 */
    private SupplierEvaluation stored(Long id, Long supplierId, BigDecimal total, String conclusion,
                                      LocalDateTime at) {
        SupplierEvaluation e = new SupplierEvaluation();
        e.setId(id);
        e.setSupplierId(supplierId);
        e.setTotalScore(total);
        e.setConclusion(conclusion);
        e.setEvaluateAt(at);
        return e;
    }

    // ---------------------------------------------------------------
    // calculateTotalScore —— 加权平均（锁死权重，这是最容易和前端不一致的地方）
    // ---------------------------------------------------------------

    @Test
    @DisplayName("综合得分 - 五项满分应为 5.00")
    void testTotalScore_AllPerfect() {
        SupplierEvaluation e = eval(5, 5, 5, 5, 5);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9001L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals(0, new BigDecimal("5.00").compareTo(e.getTotalScore()),
                "五项满分加权平均必须是 5.00，实际=" + e.getTotalScore());
    }

    @Test
    @DisplayName("综合得分 - 权重不是简单平均（质量占 30%）")
    void testTotalScore_WeightedNotSimpleAverage() {
        // 质量5、其余4：加权 = 5*0.3 + 4*0.25 + 4*0.2 + 4*0.15 + 4*0.1 = 4.30
        //                 简单平均 = 4.2  ← 两者必须不同，否则说明权重没生效
        SupplierEvaluation e = eval(5, 4, 4, 4, 4);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9002L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals(0, new BigDecimal("4.30").compareTo(e.getTotalScore()),
                "应按加权算 4.30，实际=" + e.getTotalScore());
        assertNotEquals(0, new BigDecimal("4.20").compareTo(e.getTotalScore()),
                "如果等于简单平均 4.20，说明权重没生效");
    }

    @Test
    @DisplayName("综合得分 - 空维度按 0 计（不是跳过该项）")
    void testTotalScore_NullDimensionTreatedAsZero() {
        // 只有质量4、服务4，其余 null → 4*0.3 + 0 + 0 + 4*0.15 + 0 = 1.80
        SupplierEvaluation e = eval(4, null, null, 4, null);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9003L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals(0, new BigDecimal("1.80").compareTo(e.getTotalScore()),
                "空维度按 0 计权，实际=" + e.getTotalScore());
    }

    @Test
    @DisplayName("综合得分 - 小数保留两位（HALF_UP）")
    void testTotalScore_ScaleIsTwoHalfUp() {
        // 4,3,3,3,3 → 4*0.3 + 3*0.25 + 3*0.2 + 3*0.15 + 3*0.1 = 3.30
        SupplierEvaluation e = eval(4, 3, 3, 3, 3);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9004L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals(2, e.getTotalScore().scale(), "必须保留两位小数");
        assertEquals(0, new BigDecimal("3.30").compareTo(e.getTotalScore()));
    }

    // ---------------------------------------------------------------
    // calculateConclusion —— 结论档位（锁死阈值和档位名）
    // ---------------------------------------------------------------

    @Test
    @DisplayName("评估结论 - 4.55 应为 EXCELLENT")
    void testConclusion_Excellent() {
        SupplierEvaluation e = eval(5, 4, 4, 5, 5); // → 4.55
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9010L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals("EXCELLENT", e.getConclusion());
    }

    @Test
    @DisplayName("评估结论 - 4.35 应为 GOOD")
    void testConclusion_Good() {
        SupplierEvaluation e = eval(5, 4, 4, 4, 4); // → 4.35
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9011L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals("GOOD", e.getConclusion());
    }

    @Test
    @DisplayName("评估结论 - 3.75 应为 ACCEPTABLE（没有 FAIR 这一档）")
    void testConclusion_AcceptableNotFair() {
        // 4,3,4,4,4 → 3.75
        SupplierEvaluation e = eval(4, 3, 4, 4, 4);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9012L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals("ACCEPTABLE", e.getConclusion());
        assertNotEquals("FAIR", e.getConclusion(), "后端没有 FAIR 档位");
    }

    @Test
    @DisplayName("评估结论 - 2.90 应为 POOR")
    void testConclusion_Poor() {
        // 3,3,3,3,2 → 2.90
        SupplierEvaluation e = eval(3, 3, 3, 3, 2);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9013L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertEquals("POOR", e.getConclusion());
    }

    @Test
    @DisplayName("评估结论 - 阈值边界：恰好 4.50 / 4.00 / 3.00 都应落到上一档")
    void testConclusion_ThresholdBoundaries() {
        // 4.50 → EXCELLENT（>= 4.5）
        // 4.00 → GOOD（>= 4.0）
        // 3.00 → ACCEPTABLE（>= 3.0）
        assertEquals("EXCELLENT", conclusionOf(5, 5, 5, 5, 5));   // 5.00
        assertEquals("GOOD", conclusionOf(4, 4, 4, 4, 4));       // 4.00
        assertEquals("ACCEPTABLE", conclusionOf(3, 3, 3, 3, 3));   // 3.00
        assertEquals("POOR", conclusionOf(2, 2, 2, 2, 2));       // 2.00
    }

    private String conclusionOf(Integer q, Integer d, Integer p, Integer s, Integer t) {
        SupplierEvaluation e = eval(q, d, p, s, t);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9999L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());
        evaluationService.createEvaluation(e);
        return e.getConclusion();
    }

    // ---------------------------------------------------------------
    // evaluateAt —— 后端自己填，前端传什么都不作数
    // ---------------------------------------------------------------

    @Test
    @DisplayName("评估时间 - 由后端填当前时间，覆盖前端传入的值")
    void testEvaluateAt_OverriddenByServer() {
        SupplierEvaluation e = eval(4, 4, 4, 4, 4);
        e.setEvaluateAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9020L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        assertNotEquals(LocalDateTime.of(2000, 1, 1, 0, 0), e.getEvaluateAt(),
                "evaluateAt 必须被后端覆盖成当前时间");
        assertTrue(e.getEvaluateAt().isAfter(LocalDateTime.now().minusMinutes(1)),
                "evaluateAt 应接近当前时间");
    }

    // ---------------------------------------------------------------
    // updateSupplierRating —— 回写供应商评分
    // ---------------------------------------------------------------

    @Test
    @DisplayName("供应商评分 - 回写为最近评估的 totalScore 平均")
    void testUpdateSupplierRating_AverageOfRecent() {
        SupplierEvaluation e = eval(5, 4, 4, 4, 4); // 本次 4.35
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9030L);
            return 1;
        }).when(evaluationMapper).insert(any());
        // 历史两条：4.55 和 3.75 → 连同本次 4.35 共 3 条，平均 = 4.2166... → 4.22
        when(evaluationMapper.selectList(any())).thenReturn(Arrays.asList(
                stored(1L, 5001L, new BigDecimal("4.55"), "EXCELLENT", LocalDateTime.now().minusDays(1)),
                stored(2L, 5001L, new BigDecimal("3.75"), "ACCEPTABLE", LocalDateTime.now().minusDays(2)),
                stored(3L, 5001L, new BigDecimal("4.35"), "GOOD", LocalDateTime.now())
        ));

        Supplier supplier = new Supplier();
        supplier.setId(5001L);
        supplier.setName("测试供应商");
        when(supplierService.getById(5001L)).thenReturn(supplier);
        when(supplierService.updateById(any(Supplier.class))).thenReturn(true);

        evaluationService.createEvaluation(e);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierService).updateById(captor.capture());
        BigDecimal rating = captor.getValue().getRating();
        assertEquals(0, new BigDecimal("4.22").compareTo(rating),
                "rating 应为 (4.55+3.75+4.35)/3 = 4.2166.. → 4.22，实际=" + rating);
    }

    @Test
    @DisplayName("供应商评分 - 没有历史评估时不该回写")
    void testUpdateSupplierRating_NoEvaluations_NoUpdate() {
        SupplierEvaluation e = eval(4, 4, 4, 4, 4);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9031L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.createEvaluation(e);

        verify(supplierService, never()).updateById(any(Supplier.class));
    }

    @Test
    @DisplayName("供应商评分 - 供应商不存在时静默跳过，不抛异常")
    void testUpdateSupplierRating_SupplierMissing_NoThrow() {
        SupplierEvaluation e = eval(4, 4, 4, 4, 4);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9032L);
            return 1;
        }).when(evaluationMapper).insert(any());
        when(evaluationMapper.selectList(any())).thenReturn(Collections.singletonList(
                stored(1L, 5001L, new BigDecimal("4.00"), "GOOD", LocalDateTime.now())));
        when(supplierService.getById(5001L)).thenReturn(null);

        assertDoesNotThrow(() -> evaluationService.createEvaluation(e));
        verify(supplierService, never()).updateById(any(Supplier.class));
    }

    @Test
    @DisplayName("供应商评分 - 只取最近 10 次评估参与平均")
    void testUpdateSupplierRating_LimitsToTen() {
        SupplierEvaluation e = eval(4, 4, 4, 4, 4);
        doAnswer(inv -> {
            inv.getArgument(0, SupplierEvaluation.class).setId(9033L);
            return 1;
        }).when(evaluationMapper).insert(any());
        // 造 12 条历史 —— 查询应带 LIMIT 10
        List<SupplierEvaluation> many = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(stored((long) i, 5001L, new BigDecimal("5.00"), "EXCELLENT",
                    LocalDateTime.now().minusMinutes(i)));
        }
        when(evaluationMapper.selectList(any())).thenReturn(many);

        Supplier supplier = new Supplier();
        supplier.setId(5001L);
        when(supplierService.getById(5001L)).thenReturn(supplier);
        when(supplierService.updateById(any(Supplier.class))).thenReturn(true);

        evaluationService.createEvaluation(e);

        // 校验查询条件里确实带了 LIMIT 10
        ArgumentCaptor<Wrapper<SupplierEvaluation>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(evaluationMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.toUpperCase().contains("LIMIT 10"),
                "查询必须限制最近 10 次，实际 SQL 片段=" + sql);
    }

    // ---------------------------------------------------------------
    // 查询类方法
    // ---------------------------------------------------------------

    @Test
    @DisplayName("按供应商查历史 - 返回后端查到的结果")
    void testGetBySupplierId() {
        List<SupplierEvaluation> expected = Arrays.asList(
                stored(1L, 5001L, new BigDecimal("4.55"), "EXCELLENT", LocalDateTime.now()),
                stored(2L, 5001L, new BigDecimal("4.35"), "GOOD", LocalDateTime.now().minusDays(1))
        );
        when(evaluationMapper.selectList(any())).thenReturn(expected);

        List<SupplierEvaluation> actual = evaluationService.getBySupplierId(5001L);

        assertEquals(2, actual.size());
        assertEquals(1L, actual.get(0).getId());
    }

    @Test
    @DisplayName("最新评估 - 走 LIMIT 1 查询")
    void testGetLatestEvaluation() {
        SupplierEvaluation latest = stored(2L, 5001L, new BigDecimal("4.55"), "EXCELLENT", LocalDateTime.now());
        when(evaluationMapper.selectOne(any())).thenReturn(latest);

        SupplierEvaluation actual = evaluationService.getLatestEvaluation(5001L);

        assertNotNull(actual);
        assertEquals(2L, actual.getId());
    }

    @Test
    @DisplayName("分页查询 - 传 conclusion 时应带上等值条件")
    void testListEvaluations_FilterByConclusion() {
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.listEvaluations(5001L, "EXCELLENT");

        ArgumentCaptor<Wrapper<SupplierEvaluation>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(evaluationMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.toUpperCase().contains("SUPPLIER_ID"), "应按供应商过滤，实际=" + sql);
        assertTrue(sql.toUpperCase().contains("CONCLUSION"), "应按结论过滤，实际=" + sql);
    }

    @Test
    @DisplayName("分页查询 - conclusion 为空字符串时不过滤结论")
    void testListEvaluations_BlankConclusionNoFilter() {
        when(evaluationMapper.selectList(any())).thenReturn(Collections.emptyList());

        evaluationService.listEvaluations(5001L, "   ");

        ArgumentCaptor<Wrapper<SupplierEvaluation>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(evaluationMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.toUpperCase().contains("SUPPLIER_ID"), "仍应按供应商过滤，实际=" + sql);
        assertFalse(sql.toUpperCase().contains("CONCLUSION"), "空白结论不应加过滤条件，实际=" + sql);
    }
}
