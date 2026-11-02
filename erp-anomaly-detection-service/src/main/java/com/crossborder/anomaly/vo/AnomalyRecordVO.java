package com.crossborder.anomaly.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常检测记录 VO（对外输出）
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class AnomalyRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderId;

    private String orderNo;

    private String platform;

    private BigDecimal amount;

    /** 兼容前端字段：异常分数 */
    private Integer anomalyScore;

    /** 兼容前端字段：异常类型（取第一个） */
    private String anomalyType;

    /** 完整异常类型列表 */
    private List<String> anomalyTypes;

    private Integer riskScore;

    private String riskLevel;

    private Boolean anomaly;

    private String recommendation;

    private String explanation;

    private String status;

    private String confirmedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime detectedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;

    private String confirmRemark;

    /**
     * 实体 → VO
     */
    public static AnomalyRecordVO from(com.crossborder.anomaly.entity.AnomalyRecord entity) {
        if (entity == null) {
            return null;
        }
        AnomalyRecordVO vo = new AnomalyRecordVO();
        vo.setId(entity.getId());
        vo.setOrderId(entity.getOrderId());
        vo.setOrderNo(entity.getOrderNo());
        vo.setPlatform(entity.getPlatform());
        vo.setAmount(entity.getOrderAmount());
        vo.setAnomalyScore(entity.getRiskScore());
        vo.setRiskScore(entity.getRiskScore());
        vo.setRiskLevel(entity.getRiskLevel());
        vo.setAnomaly(entity.getAnomalyFlag());
        vo.setRecommendation(entity.getRecommendation());
        vo.setExplanation(entity.getExplanation());
        vo.setStatus(entity.getStatus());
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setDetectedAt(entity.getCreateTime());
        vo.setConfirmedAt(entity.getConfirmedAt());
        vo.setConfirmRemark(entity.getConfirmRemark());

        List<String> types = JsonUtil.parseStringList(entity.getAnomalyTypes());
        vo.setAnomalyTypes(types);
        vo.setAnomalyType(types.isEmpty() ? "正常" : types.get(0));
        return vo;
    }

    /**
     * 分页转换
     */
    public static IPage<AnomalyRecordVO> from(IPage<com.crossborder.anomaly.entity.AnomalyRecord> page) {
        return page.convert(AnomalyRecordVO::from);
    }

    /**
     * 极简 JSON 数组解析（避免为一个字段引入整个 Jackson 依赖链上的歧义）
     */
    static final class JsonUtil {
        static List<String> parseStringList(String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                trimmed = trimmed.substring(1, trimmed.lastIndexOf(']') + 1);
            }
            java.util.List<String> list = new java.util.ArrayList<>();
            for (String part : trimmed.split(",")) {
                String v = part.trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!v.isEmpty()) {
                    list.add(v);
                }
            }
            return list;
        }
    }
}
