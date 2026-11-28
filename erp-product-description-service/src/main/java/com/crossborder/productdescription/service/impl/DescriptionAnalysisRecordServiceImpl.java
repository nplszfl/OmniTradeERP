package com.crossborder.productdescription.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.ProductHighlight;
import com.crossborder.productdescription.entity.SeoOptimization;
import com.crossborder.productdescription.mapper.AbTestResultMapper;
import com.crossborder.productdescription.mapper.ProductHighlightMapper;
import com.crossborder.productdescription.mapper.SeoOptimizationMapper;
import com.crossborder.productdescription.service.DescriptionAnalysisRecordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述分析结果落库服务实现（真实写库）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这三张表（{@code seo_optimization} /
 * {@code product_highlights} / {@code ab_test_result}）<b>早有 DDL、有索引</b>，
 * 但<b>全仓没有任何一行代码往里写</b> → 三张表<b>永远是空的</b>，
 * 而 {@code optimizeSEO()} / {@code extractHighlights()} /
 * {@code generateABTestVersions()} 算出来的结果<b>只活在一次 HTTP 响应里</b>。</p>
 *
 * <p>📌 <b>本项目铁律：静默失败 = 最贵的 bug。</b>
 * 这里每一条失败路径都<b>抛异常</b>，绝不「catch 掉还返回成功」——
 * 之前 {@code saveDescription} 就是这么干的：什么都没写，却告诉调用方「保存成功」。</p>
 *
 * <p>📌 <b>JSON 列的编码只在这里做一次</b>：4 个 JSON 列（关键词 / 长尾词 /
 * 关键词密度 / 改进建议）如果每个调用方各转一遍，各错一遍 ——
 * 这正是本模块「模板接线」踩过的坑（同一个概念两套 ID）。
 * → 用 {@link ObjectMapper} 统一编码，编码失败<b>抛异常</b>（不写半截 JSON 进库）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionAnalysisRecordServiceImpl implements DescriptionAnalysisRecordService {

    /** 一次批量写亮点最多写多少条（防止前端传一个超大数组把库打满） */
    private static final int MAX_HIGHLIGHTS = 50;

    /** 查询条数上限 */
    private static final int MAX_LIMIT = 200;

    /** 默认查询条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** 胜者：版本 A */
    private static final String WINNER_A = "A";

    /** 胜者：版本 B */
    private static final String WINNER_B = "B";

    /** 胜者：平局 */
    private static final String WINNER_TIE = "TIE";

    private final SeoOptimizationMapper seoOptimizationMapper;
    private final ProductHighlightMapper productHighlightMapper;
    private final AbTestResultMapper abTestResultMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SeoOptimization saveSeoOptimization(Long descriptionId,
                                               Long productId,
                                               DescriptionGenerationResponse.SEOResult seoResult,
                                               Long optimizeTimeMs) {
        if (descriptionId == null) {
            // 📌 description_id 是 NOT NULL 列 —— 空值写进去必然炸库，
            //    与其让数据库报错（500 + SQL 原文），不如给一句人话。
            throw new BizException("描述ID不能为空，无法保存SEO优化结果");
        }
        if (productId == null) {
            throw new BizException("产品ID不能为空，无法保存SEO优化结果");
        }
        if (seoResult == null) {
            throw new BizException("SEO优化结果不能为空，无法保存");
        }
        if (seoResult.getTitle() == null || seoResult.getTitle().isBlank()) {
            // 📌 optimized_title 可空，但**空标题的 SEO 记录没有意义**
            //    → 不写（而不是写一行空的）。
            throw new BizException("优化标题不能为空，无法保存SEO优化结果");
        }

        SeoOptimization row = new SeoOptimization();
        row.setDescriptionId(descriptionId);
        row.setProductId(productId);
        row.setOptimizedTitle(seoResult.getTitle());
        row.setOptimizedDescription(seoResult.getDescription());
        row.setPrimaryKeywords(toJson(seoResult.getPrimaryKeywords()));
        row.setLongTailKeywords(toJson(seoResult.getLongTailKeywords()));
        row.setKeywordDensity(toJson(seoResult.getKeywordDensity()));
        row.setReadabilityScore(toScore(seoResult.getReadabilityScore()));
        row.setSeoScore(toScore(seoResult.getSeoscore()));
        row.setSuggestions(toJson(seoResult.getSuggestions()));
        row.setOptimizeTimeMs(optimizeTimeMs);

        int rows = seoOptimizationMapper.insert(row);
        if (rows <= 0) {
            // 📌 insert 返回 0 行 = 一行都没写进去 → 必须报错，不能假装保存成功
            throw new BizException(String.format(
                    "SEO优化结果保存失败：未写入任何记录 (productId=%s, descriptionId=%s)",
                    productId, descriptionId));
        }
        log.info("SEO优化结果已落库 - productId: {}, descriptionId: {}, seoScore: {}",
                productId, descriptionId, row.getSeoScore());
        return row;
    }

    @Override
    public List<SeoOptimization> listSeoOptimizations(Long descriptionId, int limit) {
        if (descriptionId == null) {
            throw new BizException("描述ID不能为空");
        }
        int max = normalizeLimit(limit);
        return seoOptimizationMapper.selectList(
                Wrappers.<SeoOptimization>lambdaQuery()
                        .eq(SeoOptimization::getDescriptionId, descriptionId)
                        .orderByDesc(SeoOptimization::getCreateTime)
                        .orderByDesc(SeoOptimization::getId)
                        .last("LIMIT " + max));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProductHighlight> saveHighlights(Long descriptionId,
                                                  Long productId,
                                                  List<DescriptionGenerationResponse.Highlight> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            // 📌 没有真实特性可提炼 → **不写库**（不是「写一条空亮点」）
            return List.of();
        }
        if (descriptionId == null) {
            throw new BizException("描述ID不能为空，无法保存产品亮点");
        }
        if (productId == null) {
            throw new BizException("产品ID不能为空，无法保存产品亮点");
        }
        if (highlights.size() > MAX_HIGHLIGHTS) {
            throw new BizException("单次最多保存 " + MAX_HIGHLIGHTS + " 条亮点，实际 " + highlights.size());
        }

        List<ProductHighlight> saved = new ArrayList<>(highlights.size());
        int order = 0;
        for (DescriptionGenerationResponse.Highlight highlight : highlights) {
            if (highlight == null) {
                throw new BizException("亮点列表里含有空项，无法保存");
            }
            ProductHighlight row = new ProductHighlight();
            row.setDescriptionId(descriptionId);
            row.setProductId(productId);
            // 📌 highlight_title 是 NOT NULL 列 → 标题用**真实特性**，
            //    没有真实特性就**不写这行**（绝不编「核心优势 1」这种占位标题）。
            String title = firstNonBlank(highlight.getTitle(), highlight.getFeature(),
                    highlight.getDescription());
            if (title == null) {
                throw new BizException("亮点标题不能为空，无法保存产品亮点");
            }
            row.setHighlightTitle(truncate(title, 200));
            row.setHighlightDescription(highlight.getDescription());
            row.setFeature(truncate(highlight.getFeature(), 200));
            row.setImportance(highlight.getImportance());
            row.setOrderNumber(order++);

            int written = productHighlightMapper.insert(row);
            if (written <= 0) {
                throw new BizException(String.format(
                        "产品亮点保存失败：未写入任何记录 (productId=%s, descriptionId=%s)",
                        productId, descriptionId));
            }
            saved.add(row);
        }
        log.info("产品亮点已落库 - productId: {}, descriptionId: {}, 条数: {}",
                productId, descriptionId, saved.size());
        return saved;
    }

    @Override
    public List<ProductHighlight> listHighlights(Long descriptionId) {
        if (descriptionId == null) {
            throw new BizException("描述ID不能为空");
        }
        return productHighlightMapper.selectList(
                Wrappers.<ProductHighlight>lambdaQuery()
                        .eq(ProductHighlight::getDescriptionId, descriptionId)
                        .orderByAsc(ProductHighlight::getOrderNumber)
                        .orderByAsc(ProductHighlight::getId));
    }

    @Override
    public AbTestResult startAbTest(AbTestStartRequest request) {
        if (request == null) {
            throw new BizException("A/B测试请求不能为空");
        }
        if (request.getProductId() == null) {
            throw new BizException("产品ID不能为空，无法开启A/B测试");
        }
        if (isBlank(request.getTestName())) {
            throw new BizException("测试名称不能为空，无法开启A/B测试");
        }
        if (isBlank(request.getMetricName())) {
            throw new BizException("指标名称不能为空，无法开启A/B测试");
        }

        AbTestResult row = new AbTestResult();
        row.setProductId(request.getProductId());
        row.setTestName(request.getTestName().trim());
        row.setMetricName(request.getMetricName().trim());
        row.setVersionAId(request.getVersionAId());
        row.setVersionBId(request.getVersionBId());
        // 📌 start_time 是 NOT NULL 列 —— 测试「开始」是事实，不能留空
        row.setStartTime(request.getStartTime() == null ? LocalDateTime.now() : request.getStartTime());
        // 📌 **指标值 / winner / confidence / end_time 一律留空**：
        //    刚开测时 CTR 这些指标**还没有**（要等真实流量）→ 不编（规则 72）。
        //    end_time 为 null = 「还在跑」。

        int rows = abTestResultMapper.insert(row);
        if (rows <= 0) {
            throw new BizException(String.format(
                    "A/B测试开启失败：未写入任何记录 (productId=%s, testName=%s)",
                    request.getProductId(), request.getTestName()));
        }
        log.info("A/B测试已开启 - productId: {}, testName: {}, metric: {}",
                request.getProductId(), row.getTestName(), row.getMetricName());
        return row;
    }

    @Override
    public AbTestResult recordAbTestOutcome(Long abTestId, AbTestOutcomeRequest request) {
        if (abTestId == null) {
            throw new BizException("A/B测试记录ID不能为空");
        }
        if (request == null) {
            throw new BizException("A/B测试指标结果不能为空");
        }
        if (request.getMetricAValue() == null || request.getMetricBValue() == null) {
            throw new BizException("版本A和版本B的指标值都不能为空，无法回填A/B测试结果");
        }

        AbTestResult row = abTestResultMapper.selectById(abTestId);
        if (row == null) {
            // 📌 记录不存在 → 明确报错（不静默成功：调用方会以为回填成功了）
            throw new BizException("A/B测试记录不存在: " + abTestId);
        }

        row.setMetricAValue(request.getMetricAValue());
        row.setMetricBValue(request.getMetricBValue());
        // 📌 **winner 由两个实测值算出来**，不是调用方填的
        //    （表上有 winner 索引 → 按胜者做的报表必须和原始指标自洽）
        row.setWinner(decideWinner(request.getMetricAValue(), request.getMetricBValue()));
        row.setConfidence(request.getConfidence());
        LocalDateTime endTime = request.getEndTime() == null ? LocalDateTime.now() : request.getEndTime();
        row.setEndTime(endTime);
        // 📌 test_duration_days = 实际跑了多少天（不是调用方说的）
        if (row.getStartTime() != null) {
            long days = java.time.Duration.between(row.getStartTime(), endTime).toDays();
            row.setTestDurationDays((int) Math.max(0, days));
        }

        int rows = abTestResultMapper.updateById(row);
        if (rows <= 0) {
            throw new BizException("A/B测试结果回填失败：未命中任何记录 (abTestId=" + abTestId + ")");
        }
        log.info("A/B测试结果已回填 - abTestId: {}, metric: {}, a={}, b={}, winner: {}",
                abTestId, row.getMetricName(), row.getMetricAValue(),
                row.getMetricBValue(), row.getWinner());
        return row;
    }

    @Override
    public List<AbTestResult> listAbTestResults(Long productId, int limit) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        int max = normalizeLimit(limit);
        return abTestResultMapper.selectList(
                Wrappers.<AbTestResult>lambdaQuery()
                        .eq(AbTestResult::getProductId, productId)
                        .orderByDesc(AbTestResult::getStartTime)
                        .orderByDesc(AbTestResult::getId)
                        .last("LIMIT " + max));
    }

    // ------------------------------------------------------------ 私有 helper

    /**
     * 判定胜者
     *
     * <p>📌 A &gt; B → {@code A}；A &lt; B → {@code B}；相等 → {@code TIE}。
     * 这是<b>纯函数</b>，不看表 —— 所以它可以被单独测。</p>
     */
    private static String decideWinner(BigDecimal a, BigDecimal b) {
        int cmp = a.compareTo(b);
        if (cmp > 0) {
            return WINNER_A;
        }
        if (cmp < 0) {
            return WINNER_B;
        }
        return WINNER_TIE;
    }

    /** 对象 → JSON 字符串；编码失败<b>抛异常</b>（不写半截 JSON 进库） */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("SEO优化结果序列化失败，无法保存: " + e.getOriginalMessage(), e);
        }
    }

    /** 0-100 的评分 → {@code DECIMAL(5,2)}；不知道就 null（不编 0 分） */
    private static BigDecimal toScore(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** 把调用方传的 limit 收敛到 {@code [1, MAX_LIMIT]} */
    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /** 按列宽截断（MySQL 严格模式下超长会直接报错） */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
