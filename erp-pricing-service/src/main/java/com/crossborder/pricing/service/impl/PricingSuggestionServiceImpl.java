package com.crossborder.pricing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.dto.PricingSuggestionVO;
import com.crossborder.pricing.entity.AiPricingSuggestion;
import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.AiPricingSuggestionMapper;
import com.crossborder.pricing.mapper.PriceHistoryMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.PricingSuggestionService;
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
 * 定价建议服务实现
 *
 * <p>⚠️ 之前 pricing-service 整个模块没有持久层（实体和 SQL 表都在，但没有 Mapper），
 * Service 里全是内存 mock。前端的定价页因此永远拿不到真数据。
 * 这里把持久层接上，并按前端消费方式做字段映射。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingSuggestionServiceImpl implements PricingSuggestionService {

    private final AiPricingSuggestionMapper suggestionMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    private final ProductMapper productMapper;

    @Override
    public List<PricingSuggestionVO> listSuggestions(int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 500);
        List<AiPricingSuggestion> list = suggestionMapper.selectList(
                new LambdaQueryWrapper<AiPricingSuggestion>()
                        .orderByDesc(AiPricingSuggestion::getCreateTime)
                        .last("LIMIT " + max));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public PricingSuggestionVO getSuggestionBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new BizException("SKU 不能为空");
        }
        Product product = findProductBySku(sku);
        AiPricingSuggestion suggestion = findLatestSuggestion(product.getId());
        if (suggestion == null) {
            throw new BizException("商品 " + sku + " 暂无定价建议");
        }
        return toVO(suggestion, product);
    }

    @Override
    public List<PricingSuggestionVO> getHistoryBySku(String sku, int days) {
        if (sku == null || sku.isBlank()) {
            throw new BizException("SKU 不能为空");
        }
        int span = days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDateTime.now().minusDays(span);
        Product product = findProductBySku(sku);

        List<PriceHistory> history = priceHistoryMapper.selectList(
                new LambdaQueryWrapper<PriceHistory>()
                        .eq(PriceHistory::getProductId, product.getId())
                        .ge(PriceHistory::getCreateTime, since)
                        .orderByDesc(PriceHistory::getCreateTime));

        List<PricingSuggestionVO> out = new ArrayList<>();
        for (PriceHistory h : history) {
            PricingSuggestionVO vo = PricingSuggestionVO.builder()
                    .id(h.getId())
                    .productId(h.getProductId())
                    .sku(product.getSku())
                    .productName(product.getProductName())
                    .currentPrice(h.getOldPrice())
                    .suggestedPrice(h.getNewPrice())
                    .competitorAvg(h.getCompetitorAvgPrice())
                    .strategy(h.getPricingStrategy())
                    .reason(h.getAdjustmentReason())
                    .applied(Boolean.TRUE)
                    .createTime(h.getCreateTime())
                    .build();
            out.add(vo);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingSuggestionVO applySuggestion(String sku, BigDecimal finalPrice) {
        if (sku == null || sku.isBlank()) {
            throw new BizException("SKU 不能为空");
        }
        if (finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("价格不能为空且不能为负");
        }
        Product product = findProductBySku(sku);
        AiPricingSuggestion suggestion = findLatestSuggestion(product.getId());
        if (suggestion == null) {
            throw new BizException("商品 " + sku + " 暂无定价建议，无法采纳");
        }
        if (Boolean.TRUE.equals(suggestion.getApplied())) {
            throw new BizException("商品 " + sku + " 的建议已被采纳过，不能重复采纳");
        }

        suggestion.setApplied(Boolean.TRUE);
        suggestion.setApplyTime(LocalDateTime.now());
        suggestionMapper.updateById(suggestion);

        // 📌 **必须真的改商品价格** —— 原来这里**只**改了「建议」和「历史」，
        //    `product.selling_price` **从头到尾没人写过**（全仓 `setSellingPrice` 0 处调用，
        //    只有 seed SQL 和一个测试）→ 前端弹「已采纳建议价」、历史页多一条变更记录，
        //    而**商品的实际售价纹丝未动** = **假成功**（规则 80 / 82），
        //    而且 `price_history` 记下的是一次**根本没发生的变更**（规则 72：数字必须来自真正用的那张表）。
        BigDecimal oldPrice = product.getSellingPrice() != null
                ? product.getSellingPrice()
                : suggestion.getCurrentPrice();
        product.setSellingPrice(finalPrice);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        // 同步写入价格历史，前端「历史」页能立刻看到这次变更
        PriceHistory history = new PriceHistory();
        history.setProductId(product.getId());
        history.setProductCode(product.getProductCode());
        // 📌 旧价取**商品当前售价**（真实旧价），不是建议快照里的 currentPrice ——
        //    建议可能是什么时候生成的，那时的价格未必是现在的价格（规则 72）
        history.setOldPrice(oldPrice);
        history.setNewPrice(finalPrice);
        history.setAdjustmentType("MANUAL");
        history.setAdjustmentReason("采纳定价建议");
        history.setOperator("system");
        history.setCompetitorAvgPrice(suggestion.getFactors() == null
                ? null : parseCompetitorAvg(suggestion.getFactors()));
        history.setPricingStrategy(suggestion.getPricingStrategy());
        history.setCreateTime(LocalDateTime.now());
        priceHistoryMapper.insert(history);

        log.info("采纳定价建议 - SKU: {}, 原价: {}, 新价: {}",
                sku, suggestion.getCurrentPrice(), finalPrice);
        return toVO(suggestion, product);
    }

    // ---------------------------------------------------------------- 内部方法

    private Product findProductBySku(String sku) {
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getSku, sku)
                        .last("LIMIT 1"));
        if (product == null) {
            throw new BizException("商品不存在: " + sku);
        }
        return product;
    }

    private AiPricingSuggestion findLatestSuggestion(Long productId) {
        return suggestionMapper.selectOne(
                new LambdaQueryWrapper<AiPricingSuggestion>()
                        .eq(AiPricingSuggestion::getProductId, productId)
                        .orderByDesc(AiPricingSuggestion::getCreateTime)
                        .last("LIMIT 1"));
    }

    /**
     * 建议 → 前端视图
     *
     * <p>预期利润 = 建议价 - 成本价（前端 Pricing.vue 直接当金额显示）。</p>
     */
    private PricingSuggestionVO toVO(AiPricingSuggestion s) {
        return toVO(s, null);
    }

    private PricingSuggestionVO toVO(AiPricingSuggestion s, Product product) {
        Product p = product;
        if (p == null && s.getProductId() != null) {
            p = productMapper.selectById(s.getProductId());
        }
        BigDecimal cost = p == null ? null : p.getCostPrice();
        BigDecimal expectedProfit = (cost == null || s.getSuggestedPrice() == null)
                ? null
                : s.getSuggestedPrice().subtract(cost).setScale(2, RoundingMode.HALF_UP);

        return PricingSuggestionVO.builder()
                .id(s.getId())
                .productId(s.getProductId())
                .sku(p == null ? null : p.getSku())
                .productName(p == null ? null : p.getProductName())
                .currentPrice(s.getCurrentPrice())
                .suggestedPrice(s.getSuggestedPrice())
                .competitorAvg(parseCompetitorAvg(s.getFactors()))
                .expectedProfit(expectedProfit)
                .strategy(s.getPricingStrategy())
                .reason(s.getReason())
                .confidenceLevel(s.getConfidenceLevel())
                .applied(s.getApplied())
                .createTime(s.getCreateTime())
                .build();
    }

    /**
     * 从 factors JSON 里取竞品均价
     *
     * <p>factors 存的是 {@code {"competitorAvg": 312.5, ...}} 这种结构，
     * 解析失败就返回 null，不让一条脏数据把整个列表接口打挂。</p>
     */
    private BigDecimal parseCompetitorAvg(String factors) {
        if (factors == null || factors.isBlank()) {
            return null;
        }
        try {
            String marker = "\"competitorAvg\":";
            int idx = factors.indexOf(marker);
            if (idx < 0) {
                return null;
            }
            int start = idx + marker.length();
            // 跳过冒号后的空白（JSON 允许 "competitorAvg": 312.50 这种带空格的写法）
            while (start < factors.length() && Character.isWhitespace(factors.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < factors.length()
                    && (Character.isDigit(factors.charAt(end)) || factors.charAt(end) == '.'
                    || factors.charAt(end) == '-')) {
                end++;
            }
            if (end == start) {
                return null;
            }
            return new BigDecimal(factors.substring(start, end)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("解析 factors 中的竞品均价失败: {}", factors, e);
            return null;
        }
    }
}
