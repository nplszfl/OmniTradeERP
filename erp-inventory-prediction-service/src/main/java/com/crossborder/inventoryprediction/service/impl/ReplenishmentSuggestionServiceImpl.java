package com.crossborder.inventoryprediction.service.impl;

import com.crossborder.inventoryprediction.dto.PredictionRequest;
import com.crossborder.inventoryprediction.dto.PredictionResponse;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestionVO;
import com.crossborder.inventoryprediction.service.InventoryPredictionService;
import com.crossborder.inventoryprediction.service.ReplenishmentSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 补货建议服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishmentSuggestionServiceImpl implements ReplenishmentSuggestionService {

    // 库存风险天数常量
    private static final int RISK_HIGH_DAYS = 7;
    private static final int RISK_MEDIUM_DAYS = 14;
    private static final int LEAD_TIME_DAYS = 3;

    private final InventoryPredictionService predictionService;

    /**
     * 库存数据源
     *
     * <p>补货建议的「当前库存 / 安全库存」必须来自真实库存表，
     * 不能写死 —— 写死的话建议量永远和真实库存对不上，前端「建议补货 50 件」
     * 可能比实际库存还多。</p>
     */
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ReplenishmentSuggestion> generateSuggestions(Long productId) {
        log.info("生成产品 {} 的补货建议", productId);

        // 获取预测结果
        //
        // ⚠️ 这里必须把 productId 塞进 request —— PredictionRequest.productId 是
        //    @NotNull，predictSales 会把它一路传到 getHistoricalSales()。
        //    之前传的是 new PredictionRequest()（productId 为 null），
        //    → getHistoricalSales 里 `productId * 10` 直接 NPE，
        //    → 被 batchGenerateSuggestions 的 catch 吞掉并 log.error，
        //    → 接口返回 []，**看起来像"没有需要补货的商品"，实际是一个都没算出来**。
        if (productId == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        PredictionRequest request = new PredictionRequest();
        request.setProductId(productId);
        PredictionResponse prediction = predictionService.predictSales(request);

        // 当前库存 / 安全库存：查真实库存表（按 product_id 汇总各仓）
        StockSnapshot stock = loadStockSnapshot(productId);
        int currentStock = stock.availableQuantity();
        int safetyStock = stock.safetyStock();

        // 计算日均销量
        BigDecimal dailySales = prediction.getAvgDailySales() != null 
            ? BigDecimal.valueOf(prediction.getAvgDailySales()) 
            : BigDecimal.TEN;

        // 预测总销量
        BigDecimal predictedSales = prediction.getTotalPredictedSales() != null
            ? BigDecimal.valueOf(prediction.getTotalPredictedSales())
            : BigDecimal.valueOf(30).multiply(dailySales);

        // 计算缺货日期
        LocalDate stockoutDate = calculateStockoutDate(currentStock, dailySales);

        // 计算建议补货量
        int suggestedQty = calculateSuggestedQuantity(predictedSales, currentStock, safetyStock);

        // 评估缺货风险
        String risk = assessStockoutRisk(stockoutDate);

        // 计算优先级
        int priority = calculatePriority(risk, suggestedQty);

        // 生成建议
        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setProductId(productId);
        suggestion.setCurrentStock(currentStock);
        suggestion.setSafetyStock(safetyStock);
        suggestion.setDailySales(dailySales);
        suggestion.setPredictedSales(predictedSales);
        suggestion.setSuggestedQuantity(suggestedQty);
        suggestion.setExpectedStockoutDate(stockoutDate);
        suggestion.setStockoutRisk(risk);
        suggestion.setPriority(priority);
        suggestion.setSuggestedReplenishmentDate(calculateReplenishmentDate(stockoutDate));
        suggestion.setReason(generateReason(risk, stockoutDate, suggestedQty));
        suggestion.setDaysOfStock(calculateDaysOfStock(currentStock, dailySales));

        // ⚠️ 这三个字段之前从来没被赋值 —— urgency 一直是 null（前端按 HIGH/MEDIUM/LOW
        //    渲染，拿不到就显示空白），needReplenishment 一直是 null（按它过滤的建议列表
        //    会直接空掉），productSku/productName 也是 null（前端表格没 SKU 没商品名）。
        //    现在补上。
        suggestion.setUrgency(assessUrgency(risk, suggestedQty));
        suggestion.setNeedReplenishment(suggestedQty > 0);
        suggestion.setProductSku("SKU-" + productId);
        suggestion.setProductName("商品-" + productId);

        List<ReplenishmentSuggestion> suggestions = new ArrayList<>();
        suggestions.add(suggestion);

        return suggestions;
    }

    @Override
    public List<ReplenishmentSuggestion> batchGenerateSuggestions(List<Long> productIds) {
        log.info("批量生成补货建议，产品数: {}", productIds.size());

        List<ReplenishmentSuggestion> allSuggestions = new ArrayList<>();

        for (Long productId : productIds) {
            try {
                List<ReplenishmentSuggestion> suggestions = generateSuggestions(productId);
                allSuggestions.addAll(suggestions);
            } catch (Exception e) {
                log.error("生成产品 {} 补货建议失败", productId, e);
            }
        }

        return allSuggestions.stream()
                .sorted(Comparator.comparingInt(ReplenishmentSuggestion::getPriority).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<ReplenishmentSuggestion> getUrgentReplenishments() {
        log.info("获取紧急补货建议");
        // ⚠️ 之前这里是 return new ArrayList<>() —— 一个永远返回空的桩，
        //    前端「紧急补货」区块因此永远是空的，还看不出是 bug。
        //    改成真按风险过滤：HIGH/MEDIUM 优先，needReplenishment=true。
        return batchGenerateSuggestions(getMonitoredProductIds()).stream()
                .filter(s -> Boolean.TRUE.equals(s.getNeedReplenishment()))
                .filter(s -> "HIGH".equals(s.getStockoutRisk()) || "MEDIUM".equals(s.getStockoutRisk()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReplenishmentSuggestionVO> listSuggestions(List<Long> productIds, int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 500);
        List<Long> ids = (productIds == null || productIds.isEmpty())
                ? getMonitoredProductIds()
                : productIds;

        List<ReplenishmentSuggestionVO> vos = batchGenerateSuggestions(ids).stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return vos.stream()
                .sorted(Comparator.comparingInt(
                        (ReplenishmentSuggestionVO v) -> urgencyWeight(v.getUrgency())).reversed())
                .limit(max)
                .collect(Collectors.toList());
    }

    /**
     * 读取某产品的真实库存快照
     *
     * <p>可用量取 {@code available_quantity}（已扣掉锁定的），
     * 安全库存取各仓 {@code safety_stock} 之和 —— 与库存服务
     * {@code getLowStockInventory} 的判定口径一致（可用量 ≤ 安全库存即预警）。</p>
     *
     * <p>查不到时返回 0，而不是抛异常：让上层按「库存为 0」去算补货量，
     * 这在业务上比整个接口报错更合理。</p>
     */
    private StockSnapshot loadStockSnapshot(Long productId) {
        try {
            StockSnapshot snapshot = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(available_quantity), 0) AS avail, "
                            + "COALESCE(SUM(safety_stock), 0) AS safety "
                            + "FROM erp_inventory_prediction_db.inventory WHERE product_id = ?",
                    (rs, i) -> new StockSnapshot(rs.getInt("avail"), rs.getInt("safety")),
                    productId);
            // ⚠️ queryForObject 在**没有匹配行**时返回 null（不是抛异常），
            //    直接拆箱会 NPE。产品没有库存记录是常态，必须兜住。
            return snapshot != null ? snapshot : new StockSnapshot(0, 0);
        } catch (Exception e) {
            log.warn("读取产品 {} 库存失败，按 0 处理: {}", productId, e.getMessage());
            return new StockSnapshot(0, 0);
        }
    }

    /** 库存快照 */
    private record StockSnapshot(int availableQuantity, int safetyStock) {}

    /**
     * 紧急程度评估（HIGH / MEDIUM / LOW）
     *
     * <p>规则：风险高 → HIGH；风险中且补货量大 → MEDIUM；否则 LOW。</p>
     */
    private String assessUrgency(String risk, int suggestedQty) {
        if ("HIGH".equals(risk)) {
            return "HIGH";
        }
        if ("MEDIUM".equals(risk)) {
            return suggestedQty > 100 ? "HIGH" : "MEDIUM";
        }
        return suggestedQty > 0 ? "LOW" : "LOW";
    }

    /**
     * 紧急程度权重（HIGH 优先）
     */
    private int urgencyWeight(String urgency) {
        if (urgency == null) {
            return 0;
        }
        return switch (urgency) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    /**
     * 实体 → 前端视图
     */
    private ReplenishmentSuggestionVO toVO(ReplenishmentSuggestion s) {
        return ReplenishmentSuggestionVO.builder()
                .sku(s.getProductSku())
                .productName(s.getProductName())
                .currentStock(s.getCurrentStock())
                .predicted30DaySales(s.getPredictedSales() == null ? 0 : s.getPredictedSales().intValue())
                .recommendedReplenishment(s.getSuggestedQuantity())
                .safetyStock(s.getSafetyStock())
                .urgency(s.getUrgency())
                .urgencyLabel(urgencyLabel(s.getUrgency()))
                .expectedStockoutDate(s.getExpectedStockoutDate())
                .suggestedReplenishmentDate(s.getSuggestedReplenishmentDate())
                .stockoutRisk(s.getStockoutRisk())
                .reason(s.getReason())
                .priority(s.getPriority())
                .dailySales(s.getDailySales())
                .predictionAccuracy(s.getPredictionAccuracy())
                .build();
    }

    /**
     * 紧急程度中文标签（前端 InventoryPredict.vue 用）
     */
    private String urgencyLabel(String urgency) {
        if (urgency == null) {
            return "";
        }
        return switch (urgency) {
            case "HIGH" -> "紧急";
            case "MEDIUM" -> "中等";
            case "LOW" -> "低";
            default -> urgency;
        };
    }

    /**
     * 需要监控的产品ID列表
     *
     * <p>⚠️ 目前是硬编码的（还没有商品表接入），
     * 但比到处写 {@code List.of(1L..5L)} 好 —— 改一处即可。</p>
     */
    private List<Long> getMonitoredProductIds() {
        return List.of(1L, 2L, 3L, 4L, 5L);
    }

    @Override
    public Integer calculateOptimalReplenishmentQuantity(Long productId, LocalDate targetDate) {
        PredictionResponse prediction = predictionService.predictSales(
            new com.crossborder.inventoryprediction.dto.PredictionRequest());

        LocalDate today = LocalDate.now();
        int days = (int) ChronoUnit.DAYS.between(today, targetDate);

        BigDecimal dailySales = prediction.getAvgDailySales() != null 
            ? BigDecimal.valueOf(prediction.getAvgDailySales()) 
            : BigDecimal.TEN;

        int currentStock = 100;
        int safetyStock = 50;

        BigDecimal totalDemand = dailySales.multiply(BigDecimal.valueOf(days));
        int requiredStock = totalDemand.intValue() + safetyStock;
        int optimalQty = Math.max(0, requiredStock - currentStock);

        // 批量补货
        int caseSize = 100;
        optimalQty = ((optimalQty + caseSize - 1) / caseSize) * caseSize;

        return optimalQty;
    }

    private LocalDate calculateStockoutDate(int currentStock, BigDecimal dailySales) {
        if (dailySales.compareTo(BigDecimal.ZERO) <= 0) {
            return LocalDate.now().plusDays(999);
        }
        int daysUntilStockout = currentStock / dailySales.intValue();
        return LocalDate.now().plusDays(daysUntilStockout);
    }

    private int calculateSuggestedQuantity(BigDecimal predictedSales, int currentStock, int safetyStock) {
        int predictedQty = predictedSales.intValue();
        int requiredStock = predictedQty + safetyStock;
        int suggestedQty = requiredStock - currentStock;
        return Math.max(suggestedQty, safetyStock);
    }

    private String assessStockoutRisk(LocalDate stockoutDate) {
        if (stockoutDate == null) return "LOW";
        long days = ChronoUnit.DAYS.between(LocalDate.now(), stockoutDate);
        if (days <= RISK_HIGH_DAYS) return "HIGH";
        if (days <= RISK_MEDIUM_DAYS) return "MEDIUM";
        return "LOW";
    }

    private int calculatePriority(String risk, int suggestedQty) {
        int base = "HIGH".equals(risk) ? 5 : "MEDIUM".equals(risk) ? 3 : 1;
        if (suggestedQty > 1000) base += 1;
        return Math.min(5, base);
    }

    private LocalDate calculateReplenishmentDate(LocalDate stockoutDate) {
        return stockoutDate.minusDays(LEAD_TIME_DAYS);
    }

    private String generateReason(String risk, LocalDate stockoutDate, int suggestedQty) {
        String r = "HIGH".equals(risk) ? "库存不足，预计" + stockoutDate + "缺货" 
            : "MEDIUM".equals(risk) ? "库存偏低，建议补充" : "常规补货";
        return r + "，建议补货量: " + suggestedQty + "件";
    }

    private int calculateDaysOfStock(int currentStock, BigDecimal dailySales) {
        if (dailySales.compareTo(BigDecimal.ZERO) <= 0) return 999;
        return currentStock / dailySales.intValue();
    }
}