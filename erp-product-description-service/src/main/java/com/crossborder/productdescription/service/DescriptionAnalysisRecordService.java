package com.crossborder.productdescription.service;

import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.ProductHighlight;
import com.crossborder.productdescription.entity.SeoOptimization;

import java.util.List;

/**
 * 描述分析结果落库服务（表 {@code seo_optimization} / {@code product_highlights} / {@code ab_test_result}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这三张表<b>早有 DDL、有索引</b>，但<b>全仓没有任何一行代码往里写</b>
 * → {@code optimizeSEO()} / {@code extractHighlights()} / {@code generateABTestVersions()}
 * 三个方法每次都<b>现算一遍就丢掉</b> → 运营事后回看「当时打了几分」「当时提炼了什么」，
 * <b>一个都查不到</b>；而 DDL 的注释里连「获胜版本（A、B、TIE）」和「置信度」
 * 都设计好了，<b>却没有任何代码去填</b>。</p>
 *
 * <p>📌 <b>写路径必须是真的落库</b>：本项目铁律「静默失败 = 最贵的 bug」——
 * 以前 {@code saveDescription} 就是「只打日志还返回 true」，
 * 页面提示「保存成功」、数据永远不存在。这里任何失败都<b>抛异常</b>。</p>
 *
 * <p>📌 <b>查不到就是空，不编</b>（规则 72）。</p>
 *
 * <p>📌 <b>方法参数用 DTO 而不是 Entity</b>：因为三张表里有 4 个 JSON 列
 * （{@code primary_keywords} / {@code long_tail_keywords} / {@code keyword_density} /
 * {@code suggestions}）—— 「DTO → JSON 字符串」的转换必须<b>只有一处实现</b>，
 * 否则每个调用方各转一遍、各错一遍（这正是本模块模板接线踩过的坑）。</p>
 */
public interface DescriptionAnalysisRecordService {

    /**
     * 落库一条 SEO 优化结果
     *
     * @param descriptionId 描述ID（NOT NULL）
     * @param productId     产品ID（NOT NULL）
     * @param seoResult     SEO 优化结果（标题 NOT NULL）
     * @param optimizeTimeMs 优化耗时（毫秒）；不知道就传 null
     * @return 落库后的记录（含自增 id）
     */
    SeoOptimization saveSeoOptimization(Long descriptionId,
                                        Long productId,
                                        DescriptionGenerationResponse.SEOResult seoResult,
                                        Long optimizeTimeMs);

    /**
     * 查某条描述的 SEO 优化历史（按创建时间倒序）
     *
     * @param descriptionId 描述ID
     * @param limit         返回条数上限
     * @return SEO 优化记录（查不到就是空列表）
     */
    List<SeoOptimization> listSeoOptimizations(Long descriptionId, int limit);

    /**
     * 落库一批产品亮点
     *
     * <p>📌 <b>亮点是一组</b>（同一条描述下的 N 条卖点）→ 一次写入，
     * 避免「写了一半」的半截数据。</p>
     *
     * @param descriptionId 描述ID（NOT NULL）
     * @param productId     产品ID（NOT NULL）
     * @param highlights    亮点列表（空列表 → 不写库，返回空列表）
     * @return 落库后的亮点列表
     */
    List<ProductHighlight> saveHighlights(Long descriptionId,
                                           Long productId,
                                           List<DescriptionGenerationResponse.Highlight> highlights);

    /**
     * 查某条描述的产品亮点（按排序号升序）
     *
     * @param descriptionId 描述ID
     * @return 亮点列表（查不到就是空列表）
     */
    List<ProductHighlight> listHighlights(Long descriptionId);

    /**
     * 开一个 A/B 测试（落库一条 {@code ab_test_result}）
     *
     * <p>📌 <b>刚开测时指标值一定是 null</b>：CTR / conversion_rate 这些指标
     * <b>要等真实流量跑出来才有</b> → 此刻<b>不编</b>（规则 72）。
     * {@code end_time} 留空 = 「还在跑」。</p>
     *
     * @param request 开测请求
     * @return 落库后的记录（含自增 id）
     */
    AbTestResult startAbTest(AbTestStartRequest request);

    /**
     * 回填 A/B 测试结果（指标值 + 胜者 + 结束时间）
     *
     * <p>📌 <b>「获胜版本」是算出来的，不是填进来的</b>：
     * 调用方只给<b>两个指标实测值</b>，A/B/TIE 由本方法判定 ——
     * 否则运营可能把「B 赢」写成「A 赢」，表里的结论和原始数据<b>对不上</b>。</p>
     *
     * @param abTestId A/B 测试记录ID
     * @param request  指标结果
     * @return 更新后的记录
     */
    AbTestResult recordAbTestOutcome(Long abTestId, AbTestOutcomeRequest request);

    /**
     * 查某个商品的 A/B 测试记录（按开始时间倒序）
     *
     * @param productId 商品ID
     * @param limit     返回条数上限
     * @return A/B 测试记录（查不到就是空列表）
     */
    List<AbTestResult> listAbTestResults(Long productId, int limit);
}
