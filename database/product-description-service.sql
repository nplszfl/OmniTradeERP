-- 产品描述生成服务数据库初始化脚本
-- 版本: v1.5.0
-- 作者: 火球鼠
-- 创建时间: 2026-03-17

-- 创建数据库（如果不存在）
-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS erp_product_description_db
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE erp_product_description_db;

-- 产品描述表
CREATE TABLE IF NOT EXISTS product_description (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '描述ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    content TEXT NOT NULL COMMENT '描述内容',
    language VARCHAR(10) DEFAULT 'zh' COMMENT '语言（zh、en、ja等）',
    platform VARCHAR(50) DEFAULT 'general' COMMENT '目标平台（Amazon、eBay、Shopee等）',
    version_number INT DEFAULT 1 COMMENT '版本号（A/B测试）',
    short_title VARCHAR(100) COMMENT '短标题',
    template_id VARCHAR(50) COMMENT '使用的模板ID',
    model VARCHAR(50) COMMENT '使用的AI模型',
    status VARCHAR(20) DEFAULT 'GENERATED' COMMENT '状态（GENERATED、APPROVED、PUBLISHED）',
    tokens_used INT COMMENT '使用的Token数',
    generation_time_ms BIGINT COMMENT '生成时长（毫秒）',
    approved_by VARCHAR(100) COMMENT '审批人',
    approved_time DATETIME COMMENT '审批时间',
    published_time DATETIME COMMENT '发布时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_product_id (product_id),
    INDEX idx_language (language),
    INDEX idx_platform (platform),
    INDEX idx_status (status),
    INDEX idx_version_number (version_number),
    FULLTEXT idx_content (content),
    -- 📌 **自然唯一键**（2026-11-04 加）：
    --    描述是**反复生成**的（A/B 测试会反复跑）→ 没有唯一键 → 每次多几行。
    UNIQUE KEY uk_pd_product_lang_platform (product_id, language, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品描述表';

-- 📌 先删掉已有的完全重复行，再加唯一键（老库可能已攒了重复行）
DELETE pd1 FROM product_description pd1
  JOIN product_description pd2
    ON pd1.product_id = pd2.product_id
   AND pd1.language = pd2.language
   AND pd1.platform = pd2.platform
   AND pd1.id > pd2.id;

SET @uk_pd := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_description'
                 AND INDEX_NAME = 'uk_pd_product_lang_platform');
SET @sql := IF(@uk_pd = 0,
    'ALTER TABLE product_description ADD UNIQUE KEY uk_pd_product_lang_platform (product_id, language, platform)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- SEO优化结果表
CREATE TABLE IF NOT EXISTS seo_optimization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'SEO优化ID',
    description_id BIGINT NOT NULL COMMENT '描述ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    optimized_title VARCHAR(200) COMMENT '优化后的标题',
    optimized_description TEXT COMMENT '优化后的描述',
    primary_keywords JSON COMMENT '主要关键词',
    long_tail_keywords JSON COMMENT '长尾关键词',
    keyword_density JSON COMMENT '关键词密度',
    readability_score DECIMAL(5,2) COMMENT '可读性评分（0-100）',
    seo_score DECIMAL(5,2) COMMENT 'SEO评分（0-100）',
    suggestions JSON COMMENT '改进建议',
    optimize_time_ms BIGINT COMMENT '优化时长（毫秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_description_id (description_id),
    INDEX idx_product_id (product_id),
    INDEX idx_seo_score (seo_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SEO优化结果表';

-- 产品亮点表
CREATE TABLE IF NOT EXISTS product_highlights (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '亮点ID',
    description_id BIGINT NOT NULL COMMENT '描述ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    highlight_title VARCHAR(200) NOT NULL COMMENT '亮点标题',
    highlight_description TEXT COMMENT '亮点描述',
    feature VARCHAR(200) COMMENT '对应的产品特性',
    importance INT DEFAULT 5 COMMENT '重要性（1-5）',
    order_number INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_description_id (description_id),
    INDEX idx_product_id (product_id),
    INDEX idx_importance (importance),
    INDEX idx_order_number (order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品亮点表';

-- 描述模板表
CREATE TABLE IF NOT EXISTS description_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
    template_id VARCHAR(50) NOT NULL UNIQUE COMMENT '模板标识',
    name VARCHAR(200) NOT NULL COMMENT '模板名称',
    platform VARCHAR(50) NOT NULL COMMENT '目标平台',
    language VARCHAR(10) DEFAULT 'zh' COMMENT '语言',
    template_content TEXT NOT NULL COMMENT '模板内容',
    variables JSON COMMENT '模板变量',
    max_length INT DEFAULT 2000 COMMENT '最大长度',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_platform (platform),
    INDEX idx_language (language),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='描述模板表';

-- A/B测试结果表
CREATE TABLE IF NOT EXISTS ab_test_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '测试ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    test_name VARCHAR(200) NOT NULL COMMENT '测试名称',
    version_a_id BIGINT COMMENT '版本A描述ID',
    version_b_id BIGINT COMMENT '版本B描述ID',
    metric_name VARCHAR(50) NOT NULL COMMENT '指标名称（CTR、conversion_rate等）',
    metric_a_value DECIMAL(10,4) COMMENT '版本A指标值',
    metric_b_value DECIMAL(10,4) COMMENT '版本B指标值',
    winner VARCHAR(10) COMMENT '获胜版本（A、B、TIE）',
    confidence DECIMAL(5,4) COMMENT '置信度',
    test_duration_days INT COMMENT '测试天数',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_test_name (test_name),
    INDEX idx_winner (winner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B测试结果表';

-- 描述生成任务表
CREATE TABLE IF NOT EXISTS description_generation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型（SINGLE、BATCH、MULTI_LANGUAGE、AB_TEST）',
    product_id BIGINT COMMENT '产品ID（NULL表示批量）',
    target_language VARCHAR(10) COMMENT '目标语言',
    target_platform VARCHAR(50) COMMENT '目标平台',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING、RUNNING、SUCCESS、FAILED）',
    total_count INT DEFAULT 0 COMMENT '总数量',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    failed_count INT DEFAULT 0 COMMENT '失败数量',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '耗时（毫秒）',
    error_message TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_type (task_type),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='描述生成任务表';

-- 插入示例模板
INSERT INTO description_template (template_id, name, platform, language, template_content, variables, max_length, enabled) VALUES
('amazon_template', 'Amazon产品模板', 'Amazon', 'zh', 
 '【产品名称】\n\n{产品特性}\n\n【产品亮点】\n{亮点列表}\n\n【适用场景】\n{适用场景}\n\n这是一个【产品名称】，{核心卖点}。',
 '["产品名称", "产品特性", "亮点列表", "适用场景", "核心卖点"]', 2000, 1),

('ebay_template', 'eBay产品模板', 'eBay', 'zh',
 '标题：【产品名称】\n\n描述：\n{产品特性}\n\n亮点：{亮点列表}\n\n{SEO关键词}',
 '["产品名称", "产品特性", "亮点列表", "SEO关键词"]', 1500, 1),

('shopee_template', 'Shopee产品模板', 'Shopee', 'zh',
 '【产品名称】\n\n✨ 产品特点：\n{产品特性}\n\n🌟 产品亮点：\n{亮点列表}\n\n💡 核心优势：{核心优势}',
 '["产品名称", "产品特性", "亮点列表", "核心优势"]', 2000, 1),

('general_template', '通用产品模板', 'general', 'zh',
 '产品名称：【产品名称】\n\n产品介绍：\n{产品介绍}\n\n产品特点：\n{产品特性}\n\n适用场景：\n{适用场景}',
 '["产品名称", "产品介绍", "产品特性", "适用场景"]', 2500, 1)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    name = VALUES(name), platform = VALUES(platform), language = VALUES(language),
    template_content = VALUES(template_content), variables = VALUES(variables),
    max_length = VALUES(max_length), enabled = VALUES(enabled);

-- 插入示例产品描述
INSERT INTO product_description (product_id, product_name, content, language, platform, short_title, status) VALUES
(1, '无线蓝牙耳机', '【无线蓝牙耳机】\n\n产品特点：\n1. 高保真音质\n2. 长续航时间\n3. 舒适佩戴体验\n4. 快速连接\n\n这是一个无线蓝牙耳机，具有优秀的音质和舒适的佩戴体验，是您日常生活和工作的理想选择。', 'zh', 'Amazon', '无线蓝牙耳机-高保真音质', 'GENERATED'),
(2, '智能手表', '【智能手表】\n\n产品特点：\n1. 健康监测\n2. 运动追踪\n3. 消息提醒\n4. 长续航\n\n这是一款智能手表，功能全面，续航持久，是您健康生活的智能助手。', 'zh', 'Amazon', '智能手表-健康生活助手', 'GENERATED'),
(3, '便携充电宝', '【便携充电宝】\n\n产品特点：\n1. 大容量\n2. 快速充电\n3. 多设备同时充\n4. 安全可靠\n\n这是一款便携充电宝，容量大，充电快，是您出门在外的必备神器。', 'zh', 'Amazon', '便携充电宝-随身电源', 'GENERATED')
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name), content = VALUES(content),
    language = VALUES(language), platform = VALUES(platform),
    short_title = VALUES(short_title), status = VALUES(status);

-- 查看数据
SELECT '数据库初始化完成！' AS message;
SELECT '产品描述数量' AS info, COUNT(*) AS value FROM product_description;
SELECT '模板数量' AS info, COUNT(*) AS value FROM description_template;
