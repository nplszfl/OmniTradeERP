package com.crossborder.pricing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.entity.CompetitorProduct;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.CompetitorProductMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.CompetitorScrapeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 竞品数据抓取服务实现
 *
 * <p>📌 **2026-11-14 重写**。原来这个类是「假数据 + 假存储」的组合：
 * <ul>
 *   <li>{@code generateMockCompetitor} 用 {@link java.util.Random} 生成
 *       <b>随机价格 / 随机评分 / 随机评论数 / 随机销量</b>，
 *       注释写着「模拟数据」「实际环境：使用 Jsoup 解析 HTML 或调用平台 API」；</li>
 *   <li>{@code scrapeFromPlatform} 生成完随机数就 {@code log.debug} 丢掉
 *       —— <b>一个字节都没落库</b>（{@code // TODO: 保存到数据库}）；</li>
 *   <li>{@code addCompetitor} 同样只打日志（{@code // TODO: 保存到数据库}）；</li>
 *   <li>{@code getCompetitorProducts} <b>不查库</b>，而是<b>每次现场生成 3 条新随机数</b>
 *       → 同一个 productId 连查两次返回<b>不同的价格</b>；</li>
 *   <li>{@code getCompetitorPriceStats} 的均价分母用了 {@code competitors.size()}
 *       而不是<b>有效价格条数</b> → 有一条缺价就把均价算低了。</li>
 * </ul>
 *
 * <p>后果：前端「竞品均价」显示的是<b>每次刷新都在变的随机数</b>；
 * 定价算法基于这个随机数算出的「建议价」也就<b>完全不可复现</b>；
 * 而且这些数据<b>服务一重启就全没了</b>。
 * → 这违反用户铁律「src/main 无 mock 代码」（内存 mock / hardcoded 全部禁止）。
 *
 * <p>📌 **现在的实现**：
 * <ul>
 *   <li><b>真抓取</b>：OkHttp 拉页面 → Jsoup 解析价格/评分/评论数；</li>
 *   <li><b>真落库</b>：写 {@code competitor_product} 表（自然唯一键 upsert）；</li>
 *   <li><b>真查询</b>：{@code getCompetitorProducts} 查库，不再现场造数；</li>
 *   <li><b>均价分母</b>改成有效价格条数。</li>
 * </ul>
 *
 * <p>📌 **抓取失败怎么办**：不静默吞掉 —— 记 error 日志并抛
 * {@link BizException}，让调用方知道「这次没抓到」。
 * （规则 11 / 43：降级必须是有意为之且有交代；空集合不是「无数据」的语义，
 * 是「我不知道」的语义 —— 同一个坑在 AmazonOrderSync 里已经踩过一次。）
 * 唯一例外：<b>手动添加</b>的竞品不需要抓取，直接入库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorScrapeServiceImpl implements CompetitorScrapeService {

    private final CompetitorProductMapper competitorProductMapper;
    private final ProductMapper productMapper;

    /** 抓取用的 HTTP 客户端（超时收紧，避免拖死定时任务线程池） */
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    /** 批量抓取时的请求间隔（毫秒）—— 对公开站点友好，也避免被限流 */
    private static final long SCRAPE_INTERVAL_MILLIS = 500L;

    // =========================================================================
    // 抓取
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scrapeCompetitorProducts(Long productId) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        Product product = requireProduct(productId);
        log.info("开始抓取产品 {} ({}) 的竞品数据...", productId, product.getSku());

        int saved = 0;
        for (String platform : List.of("Amazon", "eBay", "Shopee", "Lazada")) {
            String url = buildSearchUrl(platform, product.getProductName());
            try {
                CompetitorProduct competitor = scrapeFromPlatform(platform, url);
                if (competitor == null) {
                    continue;
                }
                competitor.setProductId(productId);
                competitor.setCompetitorName(buildCompetitorName(platform, competitor.getProductUrl()));
                upsert(competitor);
                saved++;
            } catch (BizException e) {
                // 📌 单个平台失败**不中断**整个产品（v2.0.0 的既有语义，保留），
                //    但**必须有交代** —— 记 error 而不是静默。
                log.error("平台 {} 竞品抓取失败 - 产品ID: {}", platform, productId, e);
            }
        }

        if (saved == 0) {
            // 📌 一个都没抓到 → 必须让调用方知道（规则 11 / 43）
            log.error("产品 {} 的竞品数据全部抓取失败 - {}", productId, product.getProductName());
            throw new BizException("竞品数据抓取失败：所有平台均未取得数据（产品 " + product.getSku() + "）");
        }
        log.info("竞品数据抓取完成 - 产品ID: {}, 成功入库: {}", productId, saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompetitorProduct scrapeFromPlatform(String platform, String productUrl) {
        if (platform == null || platform.isBlank()) {
            throw new BizException("平台不能为空");
        }
        if (productUrl == null || productUrl.isBlank()) {
            throw new BizException("商品URL不能为空");
        }
        log.info("从 {} 抓取竞品数据: {}", platform, productUrl);

        CompetitorProduct competitor = fetchAndParse(platform, productUrl);
        if (competitor == null) {
            throw new BizException("未能从 " + productUrl + " 解析出竞品价格");
        }
        log.info("竞品数据抓取成功 - 平台: {}, 价格: {}, 评分: {}",
                platform, competitor.getCurrentPrice(), competitor.getRating());
        return competitor;
    }

    @Override
    public void batchScrape(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            log.info("批量抓取竞品数据 - 空集合，直接返回");
            return;
        }
        log.info("批量抓取竞品数据 - 产品数量: {}", productIds.size());

        int succeeded = 0;
        int failed = 0;
        for (Long productId : productIds) {
            try {
                scrapeCompetitorProducts(productId);
                succeeded++;
            } catch (Exception e) {
                // v2.0.0: 单个产品失败不影响整个批次（保留），但必须记账并留痕
                failed++;
                log.error("产品 {} 抓取失败，继续下一个", productId, e);
            }
            sleepBetweenScrapes();
        }

        log.info("批量抓取完成 - 成功: {}, 失败: {}", succeeded, failed);
        if (succeeded == 0 && failed > 0) {
            // 📌 全军覆没 → 不能「静默成功」
            throw new BizException("批量抓取竞品数据全部失败（共 " + failed + " 个产品）");
        }
    }

    // =========================================================================
    // 查询
    // =========================================================================

    @Override
    public List<CompetitorProduct> getCompetitorProducts(Long productId) {
        if (productId == null) {
            return List.of();
        }
        log.info("获取产品的竞品数据 - 产品ID: {}", productId);
        return competitorProductMapper.selectList(
                new LambdaQueryWrapper<CompetitorProduct>()
                        .eq(CompetitorProduct::getProductId, productId)
                        .orderByDesc(CompetitorProduct::getScrapeTime)
                        .orderByDesc(CompetitorProduct::getId));
    }

    @Override
    public CompetitorPriceStats getCompetitorPriceStats(Long productId) {
        log.info("获取竞品价格统计 - 产品ID: {}", productId);
        if (productId == null) {
            return null;
        }

        List<CompetitorProduct> competitors = getCompetitorProducts(productId);
        if (competitors.isEmpty()) {
            log.warn("没有找到竞品数据 - 产品ID: {}", productId);
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        // 📌 **2026-11-14 修复**：原来分母用的是 `competitors.size()`（全部条数），
        //    但有 price 为 null 的记录被 `continue` 跳过了 →
        //    均价被算低（3 条里 1 条缺价 → 均价只有真值的 2/3）。
        //    分母必须是**参与统计的有效价格条数**。
        int validCount = 0;

        for (CompetitorProduct competitor : competitors) {
            BigDecimal price = competitor.getCurrentPrice();
            if (price == null) {
                continue;
            }
            validCount++;
            sum = sum.add(price);

            if (min == null || price.compareTo(min) < 0) {
                min = price;
            }
            if (max == null || price.compareTo(max) > 0) {
                max = price;
            }
        }

        if (validCount == 0 || min == null || max == null) {
            log.warn("竞品数据里没有有效价格 - 产品ID: {}", productId);
            return null;
        }

        BigDecimal avg = sum.divide(new BigDecimal(validCount), 2, RoundingMode.HALF_UP);

        CompetitorPriceStats stats = new CompetitorPriceStats();
        stats.setProductId(productId);
        stats.setAvgPrice(avg);
        stats.setMinPrice(min);
        stats.setMaxPrice(max);
        stats.setCompetitorCount((long) validCount);

        log.info("竞品价格统计 - 平均: {}, 最低: {}, 最高: {}, 有效条数: {}",
                avg, min, max, validCount);
        return stats;
    }

    // =========================================================================
    // 手动添加
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCompetitor(Long productId, CompetitorProduct competitor) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        if (competitor == null) {
            throw new BizException("竞品数据不能为空");
        }
        log.info("手动添加竞品 - 产品ID: {}, 竞品: {}", productId, competitor.getCompetitorName());

        requireProduct(productId);

        competitor.setProductId(productId);
        LocalDateTime now = LocalDateTime.now();
        competitor.setCreateTime(now);
        competitor.setUpdateTime(now);
        if (competitor.getScrapeTime() == null) {
            competitor.setScrapeTime(now);
        }
        if (competitor.getCurrentPrice() == null) {
            throw new BizException("竞品价格不能为空");
        }
        if (competitor.getCompetitorName() == null || competitor.getCompetitorName().isBlank()) {
            competitor.setCompetitorName(buildCompetitorName(
                    competitor.getPlatform(), competitor.getProductUrl()));
        }

        upsert(competitor);
        log.info("竞品添加成功 - 产品ID: {}, 竞品: {}", productId, competitor.getCompetitorName());
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    /**
     * 真抓取：OkHttp 拉 HTML → Jsoup 解析。
     *
     * <p>📌 解析策略（对公开商品页尽量通用）：
     * <ol>
     *   <li>价格：优先取带 {@code itemprop="price"} / {@code class~="price"} / {@code id~="price"} 的节点；</li>
     *   <li>评分：优先取带 {@code itemprop="ratingValue"} 的节点；</li>
     *   <li>评论数：匹配「N reviews / N 评价 / N ratings」这类文案。</li>
     * </ol>
     * 解析不到价格 → 返回 {@code null}（由调用方决定是跳过还是报错）。
     */
    private CompetitorProduct fetchAndParse(String platform, String productUrl) {
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(productUrl)
                .header("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build();

        String html;
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BizException("抓取失败，HTTP " + response.code() + " - " + productUrl);
            }
            okhttp3.ResponseBody body = response.body();
            if (body == null) {
                throw new BizException("抓取失败，响应体为空 - " + productUrl);
            }
            html = body.string();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("抓取异常 - " + productUrl + ": " + e.getMessage());
        }

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, productUrl);
        CompetitorProduct competitor = new CompetitorProduct();
        competitor.setPlatform(platform);
        competitor.setProductUrl(productUrl);
        competitor.setScrapeTime(LocalDateTime.now());
        competitor.setCreateTime(LocalDateTime.now());
        competitor.setUpdateTime(LocalDateTime.now());

        BigDecimal price = parsePrice(doc);
        if (price == null) {
            return null;
        }
        competitor.setCurrentPrice(price);
        competitor.setRating(parseRating(doc));
        competitor.setReviewCount(parseReviewCount(doc));
        return competitor;
    }

    /** 解析价格 —— 多个常见选择器依次尝试，都取不到就返回 null。 */
    private BigDecimal parsePrice(org.jsoup.nodes.Document doc) {
        String[] selectors = {
                "[itemprop=price]",
                "[data-price]",
                "[class~=(?i)price]",
                "[id~=(?i)price]",
                ".a-price .a-offscreen",
                ".price",
        };
        for (String selector : selectors) {
            for (org.jsoup.nodes.Element el : doc.select(selector)) {
                BigDecimal value = toBigDecimal(el.attr("content"));
                if (value == null) {
                    value = toBigDecimal(el.text());
                }
                if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                    return value.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    /** 解析评分（0-5）。 */
    private BigDecimal parseRating(org.jsoup.nodes.Document doc) {
        for (org.jsoup.nodes.Element el : doc.select("[itemprop=ratingValue], [class~=(?i)rating], [class~=(?i)stars]")) {
            BigDecimal value = toBigDecimal(el.attr("content"));
            if (value == null) {
                value = toBigDecimal(el.text());
            }
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(new BigDecimal("5")) <= 0) {
                return value.setScale(1, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    /** 解析评论数（如 "1,234 reviews" / "共 1234 条评价"）。 */
    private Long parseReviewCount(org.jsoup.nodes.Document doc) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9][0-9,._]*)\\s*(reviews?|ratings?|评价|评论)")
                .matcher(doc.text());
        if (matcher.find()) {
            String raw = matcher.group(1).replace(",", "").replace("_", "").replace(".", "");
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 从文本里抽第一个合法数字（支持 1,234.56 / $12.99 / 12,99）。 */
    private BigDecimal toBigDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9][0-9,_]*\\.?[0-9]*)")
                .matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).replace(",", "").replace("_", "");
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 按平台的公开搜索页构造搜索 URL。 */
    private String buildSearchUrl(String platform, String keyword) {
        String q = java.net.URLEncoder.encode(keyword == null ? "" : keyword,
                java.nio.charset.StandardCharsets.UTF_8);
        return switch (platform) {
            case "Amazon" -> "https://www.amazon.com/s?k=" + q;
            case "eBay" -> "https://www.ebay.com/sch/i.html?_nkw=" + q;
            case "Shopee" -> "https://shopee.com/search?keyword=" + q;
            case "Lazada" -> "https://www.lazada.com/catalog/?q=" + q;
            default -> throw new BizException("暂不支持的平台: " + platform);
        };
    }

    /** 自然唯一键是 (product_id, competitor_name) → 名字必须稳定，否则每次抓取都插新行。 */
    private String buildCompetitorName(String platform, String productUrl) {
        if (productUrl == null || productUrl.isBlank()) {
            return platform + " 竞品";
        }
        try {
            String path = java.net.URI.create(productUrl).getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = java.net.URI.create(productUrl).getQuery();
            }
            if (path == null || path.isBlank()) {
                path = productUrl;
            }
            // 取路径最后一段（去掉扩展名），并截断到 200 字符以内（DDL 是 VARCHAR(200)）
            String last = path.replaceAll("/+$", "");
            int idx = last.lastIndexOf('/');
            if (idx >= 0) {
                last = last.substring(idx + 1);
            }
            last = last.replaceAll("\\.(html?|php|aspx)$", "");
            if (last.length() > 180) {
                last = last.substring(0, 180);
            }
            if (last.isBlank()) {
                return platform + " 竞品";
            }
            return platform + " " + last;
        } catch (Exception e) {
            return platform + " 竞品";
        }
    }

    /**
     * 落库（upsert 语义）。
     *
     * <p>DDL 上有自然唯一键 {@code uk_cp_product_name (product_id, competitor_name)}，
     * 而竞品数据是<b>反复抓取</b>的 → 同一个竞品会撞 1062。
     * → 这里做的是「同键则更新价格/评分/抓取时间」，不是吞异常。</p>
     */
    private void upsert(CompetitorProduct competitor) {
        CompetitorProduct existing = competitorProductMapper.selectOne(
                new LambdaQueryWrapper<CompetitorProduct>()
                        .eq(CompetitorProduct::getProductId, competitor.getProductId())
                        .eq(CompetitorProduct::getCompetitorName, competitor.getCompetitorName())
                        .last("LIMIT 1"));
        try {
            if (existing != null) {
                competitor.setId(existing.getId());
                competitor.setCreateTime(existing.getCreateTime());
                competitorProductMapper.updateById(competitor);
            } else {
                competitorProductMapper.insert(competitor);
            }
        } catch (DuplicateKeyException e) {
            // 📌 并发抓取同一条 → 唯一键冲突 → 转成更新重试一次
            log.warn("竞品并发写入冲突，转为更新 - 产品ID: {}, 竞品: {}",
                    competitor.getProductId(), competitor.getCompetitorName());
            CompetitorProduct again = competitorProductMapper.selectOne(
                    new LambdaQueryWrapper<CompetitorProduct>()
                            .eq(CompetitorProduct::getProductId, competitor.getProductId())
                            .eq(CompetitorProduct::getCompetitorName, competitor.getCompetitorName())
                            .last("LIMIT 1"));
            if (again != null) {
                competitor.setId(again.getId());
                competitorProductMapper.updateById(competitor);
            } else {
                throw e;
            }
        }
    }

    /** 取商品（用于构造搜索关键词 + 校验 productId 真实存在）。 */
    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在: " + productId);
        }
        return product;
    }

    private void sleepBetweenScrapes() {
        try {
            Thread.sleep(SCRAPE_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("批量抓取被中断");
        }
    }

    /**
     * 竞品名兜底（供子类/测试复用）。
     *
     * @deprecated 保留是为了不破坏可能存在的子类覆写；新代码用 {@link #buildCompetitorName}。
     */
    @Deprecated
    protected String defaultCompetitorName(String platform) {
        return platform + " 竞品";
    }
}
