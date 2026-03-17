-- AI智能客服服务数据库初始化脚本
-- 版本: v1.5.0
-- 作者: 火球鼠
-- 创建时间: 2026-03-17

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS erp_ai_assistant_db
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE erp_ai_assistant_db;

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    session_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话唯一标识',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(200) COMMENT '会话标题（自动生成）',
    language VARCHAR(10) DEFAULT 'zh' COMMENT '语言',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE、CLOSED）',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    last_message_time DATETIME COMMENT '最后消息时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL COMMENT '角色（user、assistant、system）',
    content TEXT NOT NULL COMMENT '消息内容',
    language VARCHAR(10) DEFAULT 'zh' COMMENT '语言',
    model VARCHAR(50) COMMENT '使用的模型',
    tokens_used INT COMMENT '使用的Token数',
    thinking TEXT COMMENT '思考过程（RAG检索）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识ID',
    category VARCHAR(50) NOT NULL COMMENT '知识分类',
    question TEXT NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    language VARCHAR(10) DEFAULT 'zh' COMMENT '语言',
    tags JSON COMMENT '标签',
    priority INT DEFAULT 0 COMMENT '优先级',
    view_count INT DEFAULT 0 COMMENT '查看次数',
    helpful_count INT DEFAULT 0 COMMENT '有帮助次数',
    active TINYINT DEFAULT 1 COMMENT '是否启用',
    embedding BLOB COMMENT '向量嵌入（用于向量检索）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_language (language),
    INDEX idx_active (active),
    FULLTEXT idx_question_answer (question, answer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 意图识别历史表
CREATE TABLE IF NOT EXISTS intent_recognition_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT COMMENT '用户ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    text TEXT NOT NULL COMMENT '用户输入文本',
    recognized_intent VARCHAR(50) COMMENT '识别的意图',
    confidence DECIMAL(5,4) COMMENT '置信度',
    entities JSON COMMENT '提取的实体',
    processing_time_ms INT COMMENT '处理时间（毫秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_recognized_intent (recognized_intent),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意图识别日志表';

-- 情感分析历史表
CREATE TABLE IF NOT EXISTS sentiment_analysis_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
'    user_id BIGINT COMMENT '用户ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    text TEXT NOT NULL COMMENT '分析文本',
    sentiment VARCHAR(20) NOT NULL COMMENT '情感（positive、negative、neutral）',
    confidence DECIMAL(5,4) COMMENT '置信度',
    score DECIMAL(5,4) COMMENT '情感分数（-1到1）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_sentiment (sentiment),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情感分析日志表';

-- 用户反馈表
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '反馈ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    message_id BIGINT COMMENT '消息ID',
    feedback_type VARCHAR(20) NOT NULL COMMENT '反馈类型（helpful、not_helpful、incorrect）',
    rating INT COMMENT '评分（1-5）',
    comment TEXT COMMENT '评论',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_feedback_type (feedback_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表';

-- 插入示例知识库数据
INSERT INTO knowledge_base (category, question, answer, language, tags, priority) VALUES
('订单', '如何查询我的订单？', '您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。', 'zh', '["订单", "查询", "我的订单"]', 10),
('物流', '订单发货后多久能收到？', '一般情况下，国内订单1-3天，国际订单7-15天。具体时效取决于物流方式。', 'zh', '["物流", "时效", "配送"]', 9),
('产品', '产品有质量问题怎么办？', '如果产品有质量问题，请在收到货7天内申请售后，我们会安排退换货。', 'zh', '["售后", "质量", "退换货"]', 8),
('价格', '产品价格会变动吗？', '我们的产品价格会根据市场情况动态调整，系统会自动优化定价策略。您可以在产品详情页查看价格历史。', 'zh', '["价格", "变动"]', 7),
('库存', '如何查看库存情况？', '您可以在库存管理页面查看所有产品的库存状态，包括当前库存、安全库存、预计到货时间等信息。', 'zh', '["库存", "查询"]', 9),
('售后', '如何申请退款？', '在订单详情页点击「申请售后」，选择退款类型和原因，提交后我们会尽快处理。', 'zh', '["退款", "售后"]', 8);

-- 插入示例用户
INSERT INTO chat_session (session_id, user_id, title, status, message_count) VALUES
('session-001', 1, '关于订单的咨询', 'ACTIVE', 0),
('session-002', 2, '产品问题', 'ACTIVE', 0),
('session-003', 3, '物流查询', 'ACTIVE', 0);

-- 查看数据
SELECT '数据库初始化完成！' AS message;
SELECT '会话数量' AS info, COUNT(*) AS value FROM chat_session;
SELECT '知识库条目数量' AS info, COUNT(*) AS value FROM knowledge_base;
