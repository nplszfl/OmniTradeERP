package com.crossborder.anomaly.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * 极简 JSON 数组解析
     *
     * ⚠️ 之前手写的拆分逻辑有 bug：{@code substring(1, lastIndexOf(']') + 1)} 会把结尾的
     * {@code "]} 一起带进去，解析出 {@code 金额异常"]} 这种带尾巴的值。
     * 这里改用 Jackson（项目本来就有），不要自己拆 JSON。
     */
    static final class JsonUtil {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        static List<String> parseStringList(String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return MAPPER.readValue(json, new TypeReference<List<String>>() {
                });
            } catch (JsonProcessingException e) {
                // 存量脏数据不返回空列表（会变成「正常」误导人），而是降级成单个字符串
                return List.of(json.replace("\"", "").trim());
            }
        }
    }
}
