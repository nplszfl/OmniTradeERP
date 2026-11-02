package com.crossborder.productdescription.service;

import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;

import java.util.List;
import java.util.Map;

/**
 * 产品描述生成服务
 *
 * 核心功能：
 * 1. AI多语言描述生成 - 自动生成中英文等语言的产品描述
 * 2. SEO优化 - 自动优化关键词、标题、描述
 * 3. 批量生成 - 支持批量处理多个产品
 * 4. 描述模板定制 - 支持不同平台和产品的模板
 * 5. 亮点提炼 - 自动提炼产品卖点
 * 6. A/B测试支持 - 生成多个版本供选择
 */
public interface ProductDescriptionService {

    /**
     * 生成产品描述（单次）
     *
     * @param request 生成请求
     * @return 生成结果
     */
    DescriptionGenerationResponse generateDescription(DescriptionGenerationRequest request);

    /**
     * 批量生成产品描述
     */
    List<DescriptionGenerationResponse> batchGenerateDescription(
            List<DescriptionGenerationRequest> requests);

    /**
     * 生成多语言描述
     *
     * @param productId 产品ID
     * @param languages 目标语言列表（zh、en、ja、de等）
     * @return 多语言描述
     */
    List<DescriptionGenerationResponse> generateMultiLanguageDescription(
            Long productId,
            List<String> languages);

    /**
     * SEO优化
     *
     * @param productId 产品ID
     * @param platform 目标平台
     * @return SEO优化结果
     */
    DescriptionGenerationResponse.SEOResult optimizeSEO(
            Long productId,
            String platform);

    /**
     * 提炼产品亮点
     *
     * @param productId 产品ID
     * @param maxHighlights 最大亮点数量
     * @return 亮点列表
     */
    List<DescriptionGenerationResponse.Highlight> extractHighlights(
            Long productId,
            Integer maxHighlights);

    /**
     * 生成A/B测试版本
     *
     * @param productId 产品ID
     * @param versionCount 版本数量
     * @return 多个版本的描述
     */
    List<DescriptionGenerationResponse> generateABTestVersions(
            Long productId,
            Integer versionCount);

    /**
     * 保存描述到数据库
     *
     * @param response 生成的描述
     * @return 是否保存成功
     */
    Boolean saveDescription(DescriptionGenerationResponse response);

    /**
     * 获取产品的所有描述
     *
     * @param productId 产品ID
     * @return 描述列表
     */
    List<DescriptionGenerationResponse> getProductDescriptions(Long productId);

    /**
     * 批量导入产品描述
     *
     * @param productIds 产品ID列表
     * @return 导入结果
     */
    String batchImportProducts(List<Long> productIds);

    /**
     * 获取可用描述模板列表
     *
     * <p>前端 erp-web/src/api/ai.ts 的 {@code descriptionApi.templates()}
     * 调用 {@code GET /api/description/templates}，之前后端没有这个接口 → 404。</p>
     *
     * @return 模板列表
     */
    List<Map<String, Object>> listTemplates();

    /**
     * 获取描述生成历史
     *
     * <p>前端 {@code descriptionApi.history(params)} 调用
     * {@code GET /api/description/history}，之前后端没有这个接口 → 404。</p>
     *
     * @param productId 产品ID（可空）
     * @param limit    返回条数上限
     * @return 历史记录（按生成时间倒序）
     */
    List<DescriptionGenerationResponse> listHistory(Long productId, int limit);

    /**
     * 按前端请求格式生成描述
     *
     * <p>前端 erp-web/src/api/ai.ts 传的是 {@code {sku, language, tone, keywords}}，
     * 并期望拿到 {@code {title, subtitle, body, bulletPoints, seoKeywords}}。</p>
     *
     * <p>内部转成 {@link DescriptionGenerationRequest} 走同一套生成逻辑，
     * 再把结果整理成前端要的形状。</p>
     */
    FrontendDescriptionResponse generateForFrontend(FrontendDescriptionRequest request);
}
