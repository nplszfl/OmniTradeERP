package com.crossborder.anomaly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异常检测记录实体
 *
 * <p>⚠️ 之前这个服务是「无状态」的：{@code detect()} 算完就返回，结果不落库，
 * 业务方根本没法查历史、没法人工复核、也没法统计异常率 ——
 * 一个检测服务不存检测结果，等于检测完就丢。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@TableName("t_anomaly_record")
public class AnomalyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单ID（业务侧传入） */
    private String orderId;

    /** 订单号（前端展示用） */
    private String orderNo;

    /** 平台 */
    private String platform;

    /** 订单金额 */
    private java.math.BigDecimal orderAmount;

    /** 综合风险评分 0-100 */
    private Integer riskScore;

    /** 风险等级 LOW/MEDIUM/HIGH/CRITICAL */
    private String riskLevel;

    /** 是否异常 */
    private Boolean anomalyFlag;

    /** 异常类型，JSON 数组字符串 */
    private String anomalyTypes;

    /** 命中规则，JSON 数组字符串 */
    private String triggeredRules;

    /** AI 评分 */
    private Integer aiScore;

    /** 规则引擎评分 */
    private Integer ruleScore;

    /** 处置建议 ALLOW/REVIEW/HOLD/REJECT */
    private String recommendation;

    /** 检测说明 */
    private String explanation;

    /** 检测耗时（毫秒） */
    private Long detectionLatencyMs;

    /** 复核状态 PENDING/CONFIRMED/REJECTED */
    private String status;

    /** 复核人 */
    private String confirmedBy;

    /** 复核时间 */
    private LocalDateTime confirmedAt;

    /** 复核备注 */
    private String confirmRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
