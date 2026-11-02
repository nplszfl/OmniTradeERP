package com.crossborder.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.logistics.dto.LogisticsChannelVO;
import com.crossborder.logistics.dto.LogisticsRateVO;
import com.crossborder.logistics.dto.ShippingRateQuery;
import com.crossborder.logistics.dto.ShippingRateResult;
import com.crossborder.logistics.entity.LogisticsChannel;
import com.crossborder.logistics.entity.LogisticsRate;
import com.crossborder.logistics.mapper.LogisticsChannelMapper;
import com.crossborder.logistics.mapper.LogisticsRateMapper;
import com.crossborder.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 物流服务实现
 *
 * <p>计费规则：总价 = 基础价 + (计费重量 - 重量下限) × 每公斤加价。
 * 计费重量取「实重」与「体积重（长×宽×高÷6000）」的较大值 —— 快递行业惯例。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    /** 体积重除数（国际快递通用：长cm × 宽cm × 高cm ÷ 6000 = kg） */
    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("6000");

    private final LogisticsChannelMapper channelMapper;
    private final LogisticsRateMapper rateMapper;

    @Override
    public List<LogisticsChannelVO> listChannels() {
        return channelMapper.selectList(
                        new LambdaQueryWrapper<LogisticsChannel>()
                                .eq(LogisticsChannel::getEnabled, Boolean.TRUE)
                                .orderByAsc(LogisticsChannel::getId))
                .stream().map(this::toChannelVO).toList();
    }

    @Override
    public List<LogisticsRateVO> listRates(String channelCode, String fromCountry, String toCountry,
                                            int page, int size) {
        int p = Math.max(1, page);
        int s = size <= 0 ? 20 : Math.min(size, 200);
        Page<LogisticsRate> result = rateMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<LogisticsRate>()
                        .eq(channelCode != null && !channelCode.isBlank(),
                                LogisticsRate::getChannelCode, channelCode)
                        .eq(fromCountry != null && !fromCountry.isBlank(),
                                LogisticsRate::getFromCountry, fromCountry)
                        .eq(toCountry != null && !toCountry.isBlank(),
                                LogisticsRate::getToCountry, toCountry)
                        .orderByDesc(LogisticsRate::getCreateTime));
        return result.getRecords().stream().map(this::toRateVO).toList();
    }

    @Override
    public long countRates(String channelCode, String fromCountry, String toCountry) {
        return rateMapper.selectCount(
                new LambdaQueryWrapper<LogisticsRate>()
                        .eq(channelCode != null && !channelCode.isBlank(),
                                LogisticsRate::getChannelCode, channelCode)
                        .eq(fromCountry != null && !fromCountry.isBlank(),
                                LogisticsRate::getFromCountry, fromCountry)
                        .eq(toCountry != null && !toCountry.isBlank(),
                                LogisticsRate::getToCountry, toCountry));
    }

    @Override
    public Long createRate(LogisticsRateVO rate) {
        if (rate == null || rate.getChannelCode() == null || rate.getChannelCode().isBlank()) {
            throw new BizException("渠道编码不能为空");
        }
        if (rate.getBasePrice() == null) {
            throw new BizException("基础价不能为空");
        }
        LogisticsRate entity = new LogisticsRate();
        entity.setChannelCode(rate.getChannelCode());
        entity.setChannelName(rate.getChannelName());
        entity.setCarrier(rate.getCarrier());
        entity.setShippingMethod(rate.getShippingMethod());
        entity.setFromCountry(rate.getFromCountry());
        entity.setToCountry(rate.getToCountry());
        entity.setWeightRangeMin(rate.getWeightRangeMin() == null
                ? BigDecimal.ZERO : rate.getWeightRangeMin());
        entity.setWeightRangeMax(rate.getWeightRangeMax());
        entity.setBasePrice(rate.getBasePrice());
        entity.setPricePerKg(rate.getPricePerKg() == null
                ? BigDecimal.ZERO : rate.getPricePerKg());
        entity.setEstimatedDays(rate.getEstimatedDays());
        entity.setCurrency(rate.getCurrency() == null ? "USD" : rate.getCurrency());
        entity.setIsActive(rate.getIsActive() == null ? Boolean.TRUE : rate.getIsActive());
        rateMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateRate(Long id, LogisticsRateVO rate) {
        if (id == null) {
            throw new BizException("费率ID不能为空");
        }
        LogisticsRate entity = rateMapper.selectById(id);
        if (entity == null) {
            throw new BizException("费率不存在: " + id);
        }
        if (rate.getChannelCode() != null) {
            entity.setChannelCode(rate.getChannelCode());
        }
        if (rate.getChannelName() != null) {
            entity.setChannelName(rate.getChannelName());
        }
        if (rate.getCarrier() != null) {
            entity.setCarrier(rate.getCarrier());
        }
        if (rate.getShippingMethod() != null) {
            entity.setShippingMethod(rate.getShippingMethod());
        }
        if (rate.getFromCountry() != null) {
            entity.setFromCountry(rate.getFromCountry());
        }
        if (rate.getToCountry() != null) {
            entity.setToCountry(rate.getToCountry());
        }
        if (rate.getWeightRangeMin() != null) {
            entity.setWeightRangeMin(rate.getWeightRangeMin());
        }
        if (rate.getWeightRangeMax() != null) {
            entity.setWeightRangeMax(rate.getWeightRangeMax());
        }
        if (rate.getBasePrice() != null) {
            entity.setBasePrice(rate.getBasePrice());
        }
        if (rate.getPricePerKg() != null) {
            entity.setPricePerKg(rate.getPricePerKg());
        }
        if (rate.getEstimatedDays() != null) {
            entity.setEstimatedDays(rate.getEstimatedDays());
        }
        if (rate.getCurrency() != null) {
            entity.setCurrency(rate.getCurrency());
        }
        if (rate.getIsActive() != null) {
            entity.setIsActive(rate.getIsActive());
        }
        rateMapper.updateById(entity);
    }

    @Override
    public void deleteRate(Long id) {
        if (id == null) {
            throw new BizException("费率ID不能为空");
        }
        if (rateMapper.selectById(id) == null) {
            throw new BizException("费率不存在: " + id);
        }
        rateMapper.deleteById(id);
    }

    @Override
    public List<ShippingRateResult> calculateRates(ShippingRateQuery query) {
        if (query == null || query.getToCountry() == null || query.getToCountry().isBlank()) {
            throw new BizException("目的国不能为空");
        }
        if (query.getWeight() == null || query.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("重量必须大于 0");
        }

        BigDecimal chargeableWeight = calcChargeableWeight(query);

        List<LogisticsRate> rates = rateMapper.selectList(
                new LambdaQueryWrapper<LogisticsRate>()
                        .eq(LogisticsRate::getIsActive, Boolean.TRUE)
                        .eq(query.getFromCountry() != null && !query.getFromCountry().isBlank(),
                                LogisticsRate::getFromCountry, query.getFromCountry())
                        .eq(LogisticsRate::getToCountry, query.getToCountry())
                        .le(LogisticsRate::getWeightRangeMin, chargeableWeight)
                        .orderByAsc(LogisticsRate::getWeightRangeMin));

        List<ShippingRateResult> results = new ArrayList<>();
        for (LogisticsRate rate : rates) {
            // 重量上限为 null 表示不限；否则必须落在区间内
            if (rate.getWeightRangeMax() != null
                    && chargeableWeight.compareTo(rate.getWeightRangeMax()) > 0) {
                continue;
            }
            BigDecimal base = rate.getBasePrice() == null ? BigDecimal.ZERO : rate.getBasePrice();
            BigDecimal perKg = rate.getPricePerKg() == null ? BigDecimal.ZERO : rate.getPricePerKg();
            BigDecimal min = rate.getWeightRangeMin() == null
                    ? BigDecimal.ZERO : rate.getWeightRangeMin();
            BigDecimal overage = chargeableWeight.subtract(min).max(BigDecimal.ZERO);
            BigDecimal total = base.add(overage.multiply(perKg)).setScale(2, RoundingMode.HALF_UP);

            ShippingRateResult r = new ShippingRateResult();
            r.setChannelCode(rate.getChannelCode());
            r.setChannelName(rate.getChannelName());
            r.setCarrier(rate.getCarrier());
            r.setTotalPrice(total);
            r.setCurrency(rate.getCurrency() == null ? "USD" : rate.getCurrency());
            r.setEstimatedDays(rate.getEstimatedDays());
            r.setTransitTime(buildTransitTime(rate.getEstimatedDays()));
            r.setIsRecommended(Boolean.FALSE);
            results.add(r);
        }

        // 按总价升序
        results.sort(Comparator.comparing(ShippingRateResult::getTotalPrice));

        // 标记推荐：综合价格与时效，首选总价最低的
        if (!results.isEmpty()) {
            ShippingRateResult best = results.get(0);
            for (ShippingRateResult r : results) {
                if (score(r) < score(best)) {
                    best = r;
                }
            }
            best.setIsRecommended(Boolean.TRUE);
        }
        return results;
    }

    @Override
    public ShippingRateResult recommendChannel(ShippingRateQuery query) {
        List<ShippingRateResult> rates = calculateRates(query);
        if (rates.isEmpty()) {
            throw new BizException("没有匹配的物流渠道，请检查国家与重量区间");
        }
        return rates.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsRecommended()))
                .findFirst()
                .orElse(rates.get(0));
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 计费重量 = max(实重, 体积重)
     */
    private BigDecimal calcChargeableWeight(ShippingRateQuery query) {
        BigDecimal actual = query.getWeight();
        if (query.getLength() == null || query.getWidth() == null || query.getHeight() == null) {
            return actual;
        }
        BigDecimal volumetric = query.getLength()
                .multiply(query.getWidth())
                .multiply(query.getHeight())
                .divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
        return actual.max(volumetric);
    }

    /**
     * 综合评分（越小越优）：价格权重 0.7，时效权重 0.3
     *
     * <p>两个维度量纲不同，先各自归一化再加权，避免「天数」直接压过「价格」。</p>
     */
    private double score(ShippingRateResult r) {
        double price = r.getTotalPrice() == null ? 0 : r.getTotalPrice().doubleValue();
        double days = r.getEstimatedDays() == null ? 0 : r.getEstimatedDays();
        return price * 0.7 + days * 0.3 * 10;
    }

    private String buildTransitTime(Integer days) {
        if (days == null) {
            return "";
        }
        return days + "-" + (days + 3) + "天";
    }

    private LogisticsChannelVO toChannelVO(LogisticsChannel c) {
        LogisticsChannelVO vo = new LogisticsChannelVO();
        vo.setId(c.getId());
        vo.setCode(c.getChannelCode());
        vo.setName(c.getChannelName());
        vo.setCarrier(c.getCarrier());
        vo.setType(c.getType());
        vo.setCoverage(parseJsonArray(c.getCoverage()));
        vo.setFeatures(parseJsonArray(c.getFeatures()));
        vo.setAvgDeliveryDays(c.getAvgDeliveryDays());
        vo.setTrackingSupport(c.getTrackingSupport());
        vo.setEnabled(c.getEnabled());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private LogisticsRateVO toRateVO(LogisticsRate r) {
        LogisticsRateVO vo = new LogisticsRateVO();
        vo.setId(r.getId());
        vo.setChannelCode(r.getChannelCode());
        vo.setChannelName(r.getChannelName());
        vo.setCarrier(r.getCarrier());
        vo.setShippingMethod(r.getShippingMethod());
        vo.setFromCountry(r.getFromCountry());
        vo.setToCountry(r.getToCountry());
        vo.setWeightRangeMin(r.getWeightRangeMin());
        vo.setWeightRangeMax(r.getWeightRangeMax());
        vo.setBasePrice(r.getBasePrice());
        vo.setPricePerKg(r.getPricePerKg());
        vo.setEstimatedDays(r.getEstimatedDays());
        vo.setCurrency(r.getCurrency());
        vo.setIsActive(r.getIsActive());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    /**
     * 解析 JSON 数组字符串（["CN","US"]）→ List&lt;String&gt;
     *
     * <p>解析失败返回空列表，不让一条脏数据把渠道列表接口打挂。</p>
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> out = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String v = part.trim().replace("\"", "");
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }
}
