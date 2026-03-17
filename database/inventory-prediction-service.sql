-- 库存预测服务数据库初始化脚本
-- 版本: v1.5.0
-- 作者: 火球鼠
-- 创建时间: 2026-03-17

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS erp_inventory_prediction_prediction_db
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE erp_inventory_prediction_db;

-- 产品表（复用定价服务的product表，或创建专门的视图）
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品ID',
    product_code VARCHAR(50) NOT NULL UNIQUE COMMENT '产品编码',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    cost_price DECIMAL(10,2) NOT NULL COMMENT '成本价',
    selling_price DECIMAL(10,2) NOT NULL COMMENT '当前售价',
    sku VARCHAR(100) COMMENT 'SKU',
    platform_id BIGINT COMMENT '平台ID',
    status TINYINT DEFAULT 1 COMMENT '状态（1-上架，0-下架）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_product_code (product_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';

-- 库存表
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '库存ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    quantity INT NOT NULL DEFAULT 0 COMMENT '当前库存数量',
    safety_stock INT DEFAULT 0 COMMENT '安全库存',
    available_quantity INT DEFAULT 0 COMMENT '可用库存（已锁定订单的库存）',
    locked_quantity INT DEFAULT 0 COMMENT '锁定数量（已下单但未出库）',
    last_replenish_date DATETIME COMMENT '上次补货日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_warehouse (product_id, warehouse_id),
    INDEX idx_product_id (product_id),
    INDEX idx_warehouse_id (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 销售历史表（用于预测模型训练）
CREATE TABLE IF NOT EXISTS sales_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    sales_date DATE NOT NULL COMMENT '销售日期',
    sales_quantity INT NOT NULL DEFAULT 0 COMMENT '销售数量',
    sales_amount DECIMAL(10,2) DEFAULT 0 COMMENT '销售金额',
    order_count INT DEFAULT 0 COMMENT '订单数量',
    avg_order_amount DECIMAL(10,2) DEFAULT 0 COMMENT '平均订单金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_product_date (product_id, sales_date),
    INDEX idx_product_id (product_id),
    INDEX idx_sales_date (sales_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售历史表';

-- 预测结果表
CREATE TABLE IF NOT EXISTS sales_prediction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '预测ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    prediction_date DATE NOT NULL COMMENT '预测日期',
    predicted_sales INT NOT NULL COMMENT '预测销量',
    lower_bound INT COMMENT '下限（置信区间）',
    upper_bound INT COMMENT '上限（置信区间）',
    confidence_level DECIMAL(5,4) COMMENT '置信度',
    model_type VARCHAR(50) COMMENT '模型类型（Prophet、ARIMA等）',
    prediction_accuracy DECIMAL(5,4) COMMENT '预测准确度（历史）',
    actual_sales INT COMMENT '实际销量（用于回测）',
    actual_sales_date DATETIME COMMENT '实际销量记录时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_product_date (product_id, prediction_date),
    INDEX idx_product_id (product_id),
    INDEX idx_prediction_date (prediction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预测结果表';

-- 补处建议表
CREATE TABLE IF NOT EXISTS replenishment_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '建议ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    current_stock INT NOT NULL COMMENT '当前库存',
    safety_stock INT NOT NULL COMMENT '安全库存',
    predicted_demand BIGINT NOT NULL COMMENT '预测需求',
    suggested_quantity INT NOT NULL COMMENT '建议补货数量',
    urgency VARCHAR(20) NOT NULL COMMENT '紧急程度（HIGH、MEDIUM、LOW）',
    reason TEXT COMMENT '建议原因',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING、APPROVED、REJECTED、COMPLETED）',
    approved_by VARCHAR(100) COMMENT '审批人',
    approved_time DATETIME COMMENT '审批时间',
    completed_time DATETIME COMMENT '完成时间',
    stockout_risk DECIMAL(5,4) COMMENT '缺货风险',
    overstock_risk DECIMAL(5,4) COMMENT '积压风险',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    INDEX idx_urgency (urgency),
    INDEX INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='补货建议表';

-- 库存周转率表
CREATE TABLE IF NOT EXISTS inventory_turnover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '周转率ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    calculation_period_days INT NOT NULL COMMENT '计算周期（天）',
    turnover_rate DECIMAL(10,4) NOT NULL COMMENT '周转率',
    avg_inventory DECIMAL(10,2) COMMENT '平均库存',
    total_sales_quantity BIGINT COMMENT '总销量',
    total_sales_amount DECIMAL(10,2) COMMENT '总销售额',
    inventory_days INT COMMENT '库存天数（可售天数）',
    start_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_start_end_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存周转率表';

-- 预测任务表
CREATE TABLE IF NOT EXISTS prediction_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型（SALES_PREDICTION、REPLENISHMENT_SUGGESTION等）',
    product_id BIGINT COMMENT '产品ID（NULL表示批量任务）',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING、RUNNING、SUCCESS、FAILED）',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '耗时（毫秒）',
    result_summary TEXT COMMENT '结果摘要',
    error_message TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_type (task_type),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预测任务表';

-- 插入示例数据
INSERT INTO product (product_code, product_name, cost_price, selling_price, sku) VALUES
('PROD-001', '无线蓝牙耳机', 80.00, 120.00, 'SKU-BLU-001'),
('PROD-002', '智能手表', 150.00, 220.00, 'SKU-WAT-002'),
('PROD-003', 'USB充电器', 20.00, 35.00, 'SKU-USB-003'),
('PROD-004', '手机支架', 15.00, 28.00, 'SKU-STN-004'),
('PROD-005', '便携充电宝', 60.00, 95.00, 'SKU-PWB-005');

-- 插入示例库存数据
INSERT INTO inventory (product_id, warehouse_id, quantity, safety_stock, available_quantity) VALUES
(1, 1, 150, 100, 150),
(2, 1, 80, 60, 80),
(3, 1, 300, 200, 300),
(4, 1, 250, 180, 250),
(5, 1, 120, 90, 120);

-- 插入示例销售历史（过去90天）
INSERT INTO sales_history (product_id, sales_date, sales_quantity, sales_amount, order_count)
SELECT 
    1, 
    DATE_SUB(CURDATE(), INTERVAL n DAY) as sales_date,
    10 + FLOOR(RAND() * 20) as sales_quantity,
    (10 + FLOOR(RAND() * 20)) * 120.00 as sales_amount,
    5 + FLOOR(RAND() * 10) as order_count
FROM (
    SELECT 0 as n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION 
    SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
) n
WHERE DATE_SUB(CURDATE(), INTERVAL n DAY) >= DATE_SUB(CURDATE(), INTERVAL 89 DAY);

-- 查看数据
SELECT '数据库初始化完成！' AS message;
SELECT '产品数量' AS info, COUNT(*) AS value FROM product;
SELECT '库存记录数量' AS info, COUNT(*) AS value FROM inventory;
SELECT '销售历史数量' AS info, COUNT(*) AS value FROM sales_history;
