package com.crossborder.productdescription.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.entity.ProductDescription;
import com.crossborder.productdescription.mapper.ProductDescriptionMapper;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品描述落库服务实现
 *
 * <p>📌 <b>本项目铁律：静默失败 = 最贵的 bug。</b>
 * 这里的每一条失败路径都<b>抛异常</b>，绝不「catch 掉还返回 true」——
 * 之前 {@code saveDescription} 就是这么干的：什么都没写，却告诉调用方「保存成功」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDescriptionRecordServiceImpl implements ProductDescriptionRecordService {

    /** 默认语言 */
    private static final String DEFAULT_LANGUAGE = "zh";

    /** 默认平台 */
    private static final String DEFAULT_PLATFORM = "general";

    /** 新建记录的初始版本号 */
    private static final int INITIAL_VERSION = 1;

    private final ProductDescriptionMapper productDescriptionMapper;

    @Override
    public ProductDescription upsert(ProductDescription record) {
        if (record == null) {
            throw new BizException("待保存的产品描述不能为空");
        }
        if (record.getProductId() == null) {
            throw new BizException("产品ID不能为空，无法保存产品描述");
        }
        if (record.getContent() == null || record.getContent().isBlank()) {
            // 📌 content 是 NOT NULL 列 —— 空内容写进去必然炸库，
            //    与其让数据库报错（500 + SQL 原文），不如给一句人话
            throw new BizException("描述内容不能为空，无法保存产品描述");
        }

        String language = blankToDefault(record.getLanguage(), DEFAULT_LANGUAGE);
        String platform = blankToDefault(record.getPlatform(), DEFAULT_PLATFORM);
        record.setLanguage(language);
        record.setPlatform(platform);

        ProductDescription existing = findByProductLangPlatform(
                record.getProductId(), language, platform);

        if (existing != null) {
            return updateExisting(record, existing);
        }

        // 📌 并发场景：两个请求同时 insert → 唯一键 uk_pd_product_lang_platform 冲突
        //    → 冲突的那条退化成 update（否则会向调用方抛一个「看不懂」的 DuplicateKeyException）
        record.setVersionNumber(INITIAL_VERSION);
        try {
            productDescriptionMapper.insert(record);
            log.info("产品描述已落库 - productId: {}, language: {}, platform: {}, version: {}",
                    record.getProductId(), language, platform, record.getVersionNumber());
            return record;
        } catch (DuplicateKeyException e) {
            ProductDescription concurrent = findByProductLangPlatform(
                    record.getProductId(), language, platform);
            if (concurrent == null) {
                // 📌 唯一键冲突了却又查不到行 → 数据状态异常，不能装作没事
                throw new BizException("产品描述保存失败：唯一键冲突但未能定位到记录", e);
            }
            log.info("产品描述保存命中并发唯一键冲突，转为更新 - productId: {}", record.getProductId());
            return updateExisting(record, concurrent);
        }
    }

    /**
     * 更新已存在的记录：版本号 +1
     *
     * <p>📌 用 {@code updateById} 而不是 delete + insert：
     * ① 保留行 id（外部可能已经引用了这个 id）；
     * ② 不会和唯一键冲突；
     * ③ create_time 不被抹掉。</p>
     */
    private ProductDescription updateExisting(ProductDescription record, ProductDescription existing) {
        record.setId(existing.getId());
        record.setVersionNumber(existing.getVersionNumber() == null
                ? INITIAL_VERSION + 1
                : existing.getVersionNumber() + 1);

        int rows = productDescriptionMapper.updateById(record);
        if (rows <= 0) {
            // 📌 updateById 命中 0 行 = 那一行在并发里被删了 → 必须报错，不能假装保存成功
            throw new BizException(String.format(
                    "产品描述更新失败：未命中任何记录 (productId=%s, language=%s, platform=%s)",
                    record.getProductId(), record.getLanguage(), record.getPlatform()));
        }

        log.info("产品描述已更新 - productId: {}, language: {}, platform: {}, version: {}",
                record.getProductId(), record.getLanguage(), record.getPlatform(),
                record.getVersionNumber());
        return record;
    }

    @Override
    public List<ProductDescription> listByProductId(Long productId) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        return productDescriptionMapper.selectList(
                Wrappers.<ProductDescription>lambdaQuery()
                        .eq(ProductDescription::getProductId, productId)
                        .orderByAsc(ProductDescription::getLanguage)
                        .orderByAsc(ProductDescription::getPlatform));
    }

    @Override
    public ProductDescription findByProductLangPlatform(Long productId, String language, String platform) {
        if (productId == null) {
            return null;
        }
        return productDescriptionMapper.selectOne(
                Wrappers.<ProductDescription>lambdaQuery()
                        .eq(ProductDescription::getProductId, productId)
                        .eq(ProductDescription::getLanguage, blankToDefault(language, DEFAULT_LANGUAGE))
                        .eq(ProductDescription::getPlatform, blankToDefault(platform, DEFAULT_PLATFORM)));
    }

    private static String blankToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
