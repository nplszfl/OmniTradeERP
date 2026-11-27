package com.crossborder.productdescription.service;

import com.crossborder.productdescription.entity.DescriptionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 描述模板服务（表 {@code description_template}）
 *
 * <p>📌 <b>2026-11-24 新增</b>：表早就有 DDL 和种子数据，
 * 但**没有任何代码读它** → 模板内容从来没进过 prompt。
 * 这个服务把「模板」从「内存硬编码」变成「数据库里的事实」。
 */
public interface DescriptionTemplateService {

    /**
     * 列出**启用中**的模板（按平台 + 语言过滤，可全空 = 不过滤）
     *
     * @param platform 目标平台（空 = 不限）
     * @param language 目标语言（空 = 不限）
     * @return 模板列表（没有就是空列表，**不编**）
     */
    List<DescriptionTemplate> listEnabled(String platform, String language);

    /**
     * 按<b>业务模板标识</b>取模板（{@code template_id}，如 {@code amazon_template}）
     *
     * <p>📌 <b>注意按 {@code template_id} 而不是主键 id</b>：
     * {@code product_description.template_id} 这一列存的是
     * <b>业务标识</b>（{@code amazon_template}），
     * 不是自增主键 —— 之前代码硬编码的 {@code amazon_product_template}
     * 是个<b>库里根本不存在的值</b>。</p>
     *
     * @param templateId 模板标识
     * @return 模板（找不到 / 已停用 → {@link Optional#empty()}）
     */
    Optional<DescriptionTemplate> findByTemplateId(String templateId);

    /**
     * 按**目标平台**取默认模板（平台 + 语言匹配优先，其次同平台，再次任意启用模板）
     *
     * @param platform 目标平台（空 = 不限）
     * @param language 目标语言（空 = 不限）
     * @return 默认模板（没有就是空，**不编**）
     */
    Optional<DescriptionTemplate> findDefault(String platform, String language);
}
