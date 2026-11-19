package com.crossborder.inventoryprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销量预测结果（对应 {@code erp_inventory_prediction_db.sales_prediction} 表）
 *
 * <p>📌 **2026-11-19 新增**：这张表**早就存在**（还带 {@code uk_product_date} 唯一键），
 * 但整个模块**没有对应实体、没有 Repository、没有写路径** →
 * <b>表永远是空的</b> → 任何读这张表的看板/报表永远是空白，
 * 而 API（{@code /predict}）看上去完全正常 → 「功能看起来在，数据其实从没产生过」。
 * → 现在补上实体与落库（见 {@code PredictionPersistenceServiceImpl}）。</p>
 */
@Entity
@Table(name = "sales_prediction")
public class SalesPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 产品ID */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 预测日期 */
    @Column(name = "prediction_date", nullable = false)
    private LocalDate predictionDate;

    /** 预测销量 */
    @Column(name = "predicted_sales", nullable = false)
    private Integer predictedSales = 0;

    /** 下限（置信区间） */
    @Column(name = "lower_bound")
    private Integer lowerBound;

    /** 上限（置信区间） */
    @Column(name = "upper_bound")
    private Integer upperBound;

    /** 置信度 */
    @Column(name = "confidence_level")
    private BigDecimal confidenceLevel;

    /** 模型类型（这里是确定性规则模型，不是 Prophet/ARIMA —— 如实标注） */
    @Column(name = "model_type")
    private String modelType = "RULE_TREND_SEASONAL";

    /** 预测准确度（历史回测得出；没有回测数据时为 null —— 不编造） */
    @Column(name = "prediction_accuracy")
    private BigDecimal predictionAccuracy;

    /** 实际销量（用于回测；本模块不写，留给回测任务） */
    @Column(name = "actual_sales")
    private Integer actualSales;

    /** 实际销量记录时间 */
    @Column(name = "actual_sales_date")
    private LocalDateTime actualSalesDate;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public LocalDate getPredictionDate() {
        return predictionDate;
    }

    public void setPredictionDate(LocalDate predictionDate) {
        this.predictionDate = predictionDate;
    }

    public Integer getPredictedSales() {
        return predictedSales;
    }

    public void setPredictedSales(Integer predictedSales) {
        this.predictedSales = predictedSales;
    }

    public Integer getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(Integer lowerBound) {
        this.lowerBound = lowerBound;
    }

    public Integer getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(Integer upperBound) {
        this.upperBound = upperBound;
    }

    public BigDecimal getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(BigDecimal confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public BigDecimal getPredictionAccuracy() {
        return predictionAccuracy;
    }

    public void setPredictionAccuracy(BigDecimal predictionAccuracy) {
        this.predictionAccuracy = predictionAccuracy;
    }

    public Integer getActualSales() {
        return actualSales;
    }

    public void setActualSales(Integer actualSales) {
        this.actualSales = actualSales;
    }

    public LocalDateTime getActualSalesDate() {
        return actualSalesDate;
    }

    public void setActualSalesDate(LocalDateTime actualSalesDate) {
        this.actualSalesDate = actualSalesDate;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
