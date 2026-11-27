package com.crossborder.productdescription.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.entity.DescriptionTemplate;
import com.crossborder.productdescription.mapper.DescriptionTemplateMapper;
import com.crossborder.productdescription.service.DescriptionTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 描述模板服务实现（真实查库）
 *
 * <p>📌 <b>2026-11-24 新增</b>：以前模板列表是
 * {@code ProductDescriptionServiceImpl} 里一个
 * {@code Map<String,String> templates = new HashMap<>()}
 * 硬编码出来的，<b>表 {@code description_template} 从来没人读过</b>。
 * 现在模板是<b>数据库里的事实</b>。</p>
 *
 * <p>📌 <b>查不到就返回空，不编</b>（规则 72）：
 * 库里没有启用中的模板 → 返回空列表 / {@link Optional#empty()}，
 * 绝不「兜底一个写死的模板」—— 那会让运营改了模板却看不出效果，
 * 还会让「模板到底生效没有」这个问题永远查不出来。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionTemplateServiceImpl implements DescriptionTemplateService {

    /** 「启用」的取值 —— DDL 里是 {@code TINYINT DEFAULT 1} */
    private static final int ENABLED = 1;

    private final DescriptionTemplateMapper templateMapper;

    @Override
    public List<DescriptionTemplate> listEnabled(String platform, String language) {
        try {
            // 📌 **2026-11-25 修：平台/语言的匹配从「SQL 层」改成「Java 层」**
            //    🔴 病灶：DDL 里种的是 `Amazon` / `eBay` / `Shopee`（**首字母大写**），
            //    而请求里传的是 `amazon` / `ebay` / `shopee`（**全小写**）。
            //    → 原来靠 MySQL 的 `utf8mb4_unicode_ci`（**大小写不敏感**）才碰巧匹配上。
            //    → 但这依赖了「排序规则恰好不敏感」这一隐式前提：
            //       · H2 测试库（默认 `CASE_SENSITIVE`）→ **匹配不上**；
            //       · 哪天 collation 改成 `utf8mb4_bin` → **线上直接匹配不上**。
            //    → 判据在 Java 里做（**显式**忽略大小写）→ 不依赖任何排序规则。
            // 📌 模板表极小（几条）→ 全量取回再在内存里筛，性能无关紧要。
            List<DescriptionTemplate> rows = templateMapper.selectList(
                    Wrappers.<DescriptionTemplate>lambdaQuery()
                            .eq(DescriptionTemplate::getEnabled, ENABLED)
                            .orderByAsc(DescriptionTemplate::getId));
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<DescriptionTemplate> out = new ArrayList<>(rows.size());
            for (DescriptionTemplate row : rows) {
                if (matchesIgnoreCase(row.getPlatform(), platform)
                        && matchesIgnoreCase(row.getLanguage(), language)) {
                    out.add(row);
                }
            }
            return out;
        } catch (Exception e) {
            // 📌 模板表不可用 → 明确报错，**不静默返回空**。
            //    静默返回空会让「模板没生效」和「模板表挂了」看起来一模一样。
            throw new BizException("描述模板表不可用，无法读取模板列表：" + e.getMessage(), e);
        }
    }

    /**
     * 忽略大小写比较；{@code expected} 为空 → **不限制**（不是「必须等于空」）。
     */
    private static boolean matchesIgnoreCase(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return actual.trim().equalsIgnoreCase(expected.trim());
    }

    @Override
    public Optional<DescriptionTemplate> findByTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return Optional.empty();
        }
        try {
            // 📌 **2026-11-25 修：原来用 `selectOne` + `eq`**
            //    🔴 病灶：`template_id` 上有 UNIQUE 约束，但 MySQL 的
            //    `utf8mb4_unicode_ci` 是**大小写不敏感**的 → `Amazon_Template`
            //    和 `amazon_template` **在 UNIQUE 索引里是同一个键**。
            //    → 真出现两条只差大小写的行时，`selectOne` 会抛
            //      "TooManyResultsException" → 整个查询失败（而不是返回一条）。
            //    → 而且它把「大小写」这件事**偷偷托付给排序规则**了。
            //    → 现在在 Java 层**显式**忽略大小写地找，取第一条 → 行为确定。
            String wanted = templateId.trim();
            List<DescriptionTemplate> rows = templateMapper.selectList(
                    Wrappers.<DescriptionTemplate>lambdaQuery()
                            .eq(DescriptionTemplate::getEnabled, ENABLED)
                            .orderByAsc(DescriptionTemplate::getId));
            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }
            for (DescriptionTemplate row : rows) {
                if (row.getTemplateId() != null
                        && row.getTemplateId().trim().equalsIgnoreCase(wanted)) {
                    return Optional.of(row);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new BizException("描述模板表不可用，无法读取模板 " + templateId + "：" + e.getMessage(), e);
        }
    }

    @Override
    public Optional<DescriptionTemplate> findDefault(String platform, String language) {
        if (isNotBlank(platform)) {
            // ① 平台 + 语言匹配
            Optional<DescriptionTemplate> exact = listEnabled(platform, language).stream()
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
            // ② 同平台、语言不限
            Optional<DescriptionTemplate> samePlatform = listEnabled(platform, null).stream()
                    .findFirst();
            if (samePlatform.isPresent()) {
                return samePlatform;
            }
            // 📌 **2026-11-25 修：原来这里有「③ 任意启用中的模板」兜底**
            //    🔴 病灶：请求 `targetPlatform=amazon` 但**库里没有 amazon 模板**时，
            //    旧逻辑会返回**别的平台**的模板（比如 shopee 的）→
            //    → 明明要 Amazon 格式，却按 Shopee 的模板写文案，
            //      而且响应里的 `templateId` 记的是 `shopee_template`
            //      → **追溯时完全看不出「用错模板了」**。
            //    → 现在：**指定了平台却匹配不上 → 返回空**（不编、不串台）。
            //      「没有模板」是可被看见的事实（响应里 templateId 为 null），
            //      比「悄悄用了别的平台的模板」诚实得多（规则 72）。
            log.warn("没有匹配平台的描述模板（platform={}，language={}）—— 本次不使用模板", platform, language);
            return Optional.empty();
        }
        // 没指定平台 → 任意启用中的模板（这是「不限平台」的语义，不算串台）
        return listEnabled(null, language).stream().findFirst();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
