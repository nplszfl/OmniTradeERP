package com.crossborder.pricing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.mapper.PriceHistoryMapper;
import com.crossborder.pricing.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 价格历史服务实现
 *
 * <p>📌 **2026-11-14：这里原来是一个「内存存储」实现** ——
 * {@code private final List<PriceHistory> priceHistoryList = new CopyOnWriteArrayList<>();}
 * 注释写着「模拟数据库存储」+ 三个 {@code TODO: 从数据库查询}。
 *
 * <p>后果<b>不是「数据不准」，而是「服务重启即丢数据」</b>：
 * <ul>
 *   <li>{@code recordPriceChange} 写进内存 → 进程退出就没了 → <b>价格历史表永远是空的</b>；</li>
 *   <li>{@code getPriceHistory} 查的是内存 → 前端「价格变更历史」页<b>永远是空列表</b>；</li>
 *   <li>{@code getPriceTrendAnalysis} 基于空列表 → <b>永远返回 null</b>
 *       → 动态定价的需求因子项<b>永远是 0</b>；</li>
 *   <li>多副本部署时，每个副本各有一份内存 → <b>副本间数据互相看不见</b>。</li>
 * </ul>
 *
 * <p>📌 **规则 72 / 80 / 82**：数字必须来自真正用的那张表；
 * 写了「模拟数据库」的存储，不能当成真数据对外提供。
 * 实体（{@code PriceHistory}）和表（{@code price_history}）本来就在，
 * {@code PriceHistoryMapper} 也早就在 —— 只是<b>没人调用它</b>。
 * → 现在接上真持久层。</p>
 *
 * <p>📌 **DDL 有自然唯一键** {@code uk_ph_product_prices (product_id, old_price, new_price, adjustment_type)}
 * —— 定价是反复跑的，同一次调价重复写入会撞 1062。
 * → 这里<b>不是吞掉异常</b>，而是**把它当成幂等信号**：重复的调价记录没有新信息，
 * 记一条 warn 就够了（这是「预期失败路径」，不是静默失败）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private final PriceHistoryMapper priceHistoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPriceChange(PriceHistory history) {
        if (history == null) {
            throw new IllegalArgumentException("价格变更记录不能为空");
        }
        log.info("记录价格变更 - 产品ID: {}, 旧价格: {}, 新价格: {}",
                history.getProductId(), history.getOldPrice(), history.getNewPrice());

        if (history.getCreateTime() == null) {
            history.setCreateTime(LocalDateTime.now());
        }
        // 📌 product_code / old_price / new_price 在 DDL 里都是 NOT NULL，
        //    这里做一次前置校验，避免到数据库才炸（错误信息更贴近业务语义）。
        requireNonNull(history.getProductCode(), "产品编码");
        requireNonNull(history.getOldPrice(), "原价格");
        requireNonNull(history.getNewPrice(), "新价格");
        if (history.getAdjustmentType() == null || history.getAdjustmentType().isBlank()) {
            history.setAdjustmentType("AUTO");
        }

        try {
            priceHistoryMapper.insert(history);
            log.info("价格变更记录成功 - ID: {}", history.getId());
        } catch (DuplicateKeyException e) {
            // 📌 自然唯一键命中 = 同一次调价被重复记录（定价是反复跑的）。
            //    这是**预期内的幂等场景**，不是缺陷 → 记 warn 后正常返回，
            //    不往上抛（否则调用方的整个事务会被打断）。
            log.warn("价格变更记录重复（自然唯一键命中），已忽略 - 产品ID: {}, 旧价: {}, 新价: {}, 类型: {}",
                    history.getProductId(), history.getOldPrice(), history.getNewPrice(),
                    history.getAdjustmentType());
        }
    }

    @Override
    public List<PriceHistory> getPriceHistory(Long productId) {
        log.info("获取产品价格历史 - 产品ID: {}", productId);
        if (productId == null) {
            return List.of();
        }
        return priceHistoryMapper.selectList(
                new LambdaQueryWrapper<PriceHistory>()
                        .eq(PriceHistory::getProductId, productId)
                        .orderByDesc(PriceHistory::getCreateTime)
                        .orderByDesc(PriceHistory::getId));
    }

    @Override
    public List<PriceHistory> getPriceHistory(Long productId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("获取指定时间范围的价格历史 - 产品ID: {}, 开始时间: {}, 结束时间: {}",
                productId, startTime, endTime);
        if (productId == null) {
            return List.of();
        }
        return priceHistoryMapper.selectList(
                new LambdaQueryWrapper<PriceHistory>()
                        .eq(PriceHistory::getProductId, productId)
                        .ge(PriceHistory::getCreateTime, startTime)
                        .le(PriceHistory::getCreateTime, endTime)
                        .orderByDesc(PriceHistory::getCreateTime)
                        .orderByDesc(PriceHistory::getId));
    }

    @Override
    public List<PriceHistory> getRecentPriceChanges(Long productId, int limit) {
        log.info("获取最近N次价格变更 - 产品ID: {}, 数量: {}", productId, limit);
        if (productId == null) {
            return List.of();
        }
        int max = limit <= 0 ? 10 : Math.min(limit, 500);
        return priceHistoryMapper.selectList(
                new LambdaQueryWrapper<PriceHistory>()
                        .eq(PriceHistory::getProductId, productId)
                        .orderByDesc(PriceHistory::getCreateTime)
                        .orderByDesc(PriceHistory::getId)
                        .last("LIMIT " + max));
    }

    @Override
    public PriceTrendAnalysis getPriceTrendAnalysis(Long productId, int days) {
        log.info("获取价格趋势分析 - 产品ID: {}, 天数: {}", productId, days);
        if (productId == null) {
            return null;
        }

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days <= 0 ? 30 : days);

        List<PriceHistory> histories = getPriceHistory(productId, startDate, endDate);

        if (histories.isEmpty()) {
            // 📌 「没有数据」和「算不出来」是两回事 —— 但这里返回 null 是**接口契约**
            //    （DynamicPricingServiceImpl 对 null 有显式处理），不是静默失败：
            //    这里已经 warn 过了，调用方能看出「没数据」而不是「算错了」。
            log.warn("没有找到价格历史数据 - 产品ID: {}, 时间范围: {} ~ {}", productId, startDate, endDate);
            return null;
        }

        // 排序按时间升序（取最早 → 最晚）
        List<PriceHistory> ascending = histories.stream()
                .sorted((a, b) -> {
                    int cmp = a.getCreateTime().compareTo(b.getCreateTime());
                    return cmp != 0 ? cmp : a.getId().compareTo(b.getId());
                })
                .toList();

        PriceHistory first = ascending.get(0);
        PriceHistory last = ascending.get(ascending.size() - 1);

        BigDecimal startingPrice = first.getOldPrice();
        BigDecimal endingPrice = last.getNewPrice();

        BigDecimal priceChangeAmount = endingPrice.subtract(startingPrice);
        BigDecimal priceChangePercent = calculatePriceChangePercent(startingPrice, endingPrice);

        PriceTrendAnalysis analysis = new PriceTrendAnalysis();
        analysis.setProductId(productId);
        analysis.setStartingPrice(startingPrice);
        analysis.setEndingPrice(endingPrice);
        analysis.setPriceChangeAmount(priceChangeAmount);
        analysis.setPriceChangePercent(priceChangePercent);
        analysis.setTotalChanges((long) ascending.size());
        analysis.setStartDate(startDate);
        analysis.setEndDate(endDate);

        log.info("价格趋势分析 - 变动金额: {}, 变动百分比: {}, 总次数: {}",
                priceChangeAmount, priceChangePercent, ascending.size());

        return analysis;
    }

    // ========== 私有方法 ==========

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    /**
     * 计算价格变动百分比
     */
    private BigDecimal calculatePriceChangePercent(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return newPrice.subtract(oldPrice)
                .divide(oldPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
