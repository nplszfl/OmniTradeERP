-- H2 schema（dev / 测试环境自动执行）
-- Spring Boot 对嵌入式数据库（h2）默认开启 sql.init，schema.sql 会自动跑

CREATE TABLE IF NOT EXISTS t_anomaly_record (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    order_id             VARCHAR(64)  NOT NULL,
    order_no             VARCHAR(64),
    platform             VARCHAR(30),
    order_amount         DECIMAL(18,2),
    risk_score           INT          DEFAULT 0,
    risk_level           VARCHAR(20),
    anomaly_flag         BOOLEAN      DEFAULT FALSE,
    anomaly_types        VARCHAR(500),
    triggered_rules      VARCHAR(1000),
    ai_score             INT          DEFAULT 0,
    rule_score           INT          DEFAULT 0,
    recommendation       VARCHAR(20),
    explanation          VARCHAR(1000),
    detection_latency_ms BIGINT,
    status               VARCHAR(20)  DEFAULT 'PENDING',
    confirmed_by         VARCHAR(50),
    confirmed_at         TIMESTAMP,
    confirm_remark       VARCHAR(500),
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_anomaly_status ON t_anomaly_record (status);
CREATE INDEX IF NOT EXISTS idx_anomaly_create_time ON t_anomaly_record (create_time);
