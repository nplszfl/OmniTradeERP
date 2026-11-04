-- AI智能客服服务数据库初始化脚本
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
    user_id BIGINT COMMENT '用户ID',
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

-- ===================================================
-- 知识库文档表（RAG 持久化）
--
-- ⚠️ 这张表之前**根本不在 DDL 里**，但代码里有对应的 JPA 实体
--    （@Entity @Table(name="knowledge_document") + JpaRepository）。
--    后果：KnowledgeDocumentRepository 的所有查询在运行期报
--    "Table 'erp_ai_assistant_db.knowledge_document' doesn't exist"，
--    而 RAGService / VectorSearchService 会把它 catch 掉 → 知识库检索静默返回空。
--    这就是「AI 客服看起来能聊，但检索不到任何知识」的原因。
--
--    字段严格对齐 KnowledgeDocument 实体：
--    id / title / content / category / tags / language / embedding / active
--    / create_time / update_time
-- ===================================================
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content TEXT NOT NULL COMMENT '文档正文（检索 + 喂给 LLM）',
    category VARCHAR(50) DEFAULT NULL COMMENT '分类：订单/物流/产品/退货',
    tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    language VARCHAR(10) NOT NULL DEFAULT 'zh' COMMENT '语言代码',
    embedding TEXT DEFAULT NULL COMMENT '嵌入向量，JSON 数组形式',
    active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（软删除标志）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_category (category),
    INDEX idx_language (language),
    INDEX idx_active (active),
    INDEX idx_kd_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表（RAG）';

-- 插入示例文档（让检索接口一上来就有东西可查）
INSERT INTO knowledge_document (title, content, category, tags, language, active) VALUES
('订单查询指南', '您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。支持按订单号、平台、下单时间筛选。', '订单', '订单,查询,列表', 'zh', 1),
('物流时效说明', '一般情况下国内订单 1-3 天送达，国际订单 7-15 天。具体时效取决于物流方式与承运商。', '物流', '物流,时效,配送', 'zh', 1),
('产品售后政策', '如果产品有质量问题，请在收到货 7 天内申请售后，我们会安排退换货。', '产品', '售后,质量,退换货', 'zh', 1),
('价格变动规则', '产品价格会根据市场情况动态调整，系统会自动优化定价策略。您可以在产品详情页查看价格历史。', '价格', '价格,变动,策略', 'zh', 1),
('库存与补货', '您可以在库存管理页面查看所有产品的库存状态，包括当前库存、安全库存、预计到货时间等信息。', '库存', '库存,查询,补货', 'zh', 1);

-- 📌 **插入知识库问答数据**（聊天检索用的就是这张表 knowledge_base）
--
-- 📌 **2026-11-04 扩充**：原来只有 6 条，而聊天页开场白写着「基于 330+ 行业知识库」
--    → **承诺与事实不符**（规则 19：降级/能力必须可被看见，不能写假的）。
--    现扩到 28 条，覆盖开场白承诺的 订单/物流/库存/财务/退款 五大类。
--
-- 📌 **这批数据必须能被检索到** —— 检索用**字符 n-gram + 同义词**，
--    所以 question 要写成**用户真会打出来的口语**（规则 44：文档/文案别写理想化说法）。
INSERT INTO knowledge_base (category, question, answer, language, tags, priority) VALUES
-- ===== 订单 =====
('订单', '如何查询我的订单？', '您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。', 'zh', '["订单", "查询", "我的订单"]', 10),
('订单', '订单状态有哪些？', '订单状态包括待付款、待发货、已发货、已完成、已取消等，在订单列表页可以直接看到当前状态。', 'zh', '["订单", "状态", "流程"]', 8),
('订单', '怎么取消订单？', '未发货的订单可以在订单详情页点击「取消订单」，已发货的订单需要走售后流程。', 'zh', '["订单", "取消", "撤销"]', 7),
('订单', '订单号在哪里看？', '订单编号在订单列表和订单详情页都会显示，联系客服时提供订单编号可以更快定位到订单。', 'zh', '["订单", "编号", "订单号"]', 6),
('订单', '可以按平台筛选订单吗？', '可以。订单列表支持按平台、下单时间等条件筛选，方便您定位不同渠道的订单。', 'zh', '["订单", "筛选", "平台"]', 6),
('订单', '多久没发货会被提醒？', '系统会对处于异常状态的订单进行预警，具体的补货与发货提醒可以在「订单管理」页面查看。', 'zh', '["订单", "预警", "提醒"]', 5),
-- ===== 物流 =====
('物流', '订单发货后多久能收到？', '一般情况下，国内订单1-3天，国际订单7-15天。具体时效取决于物流方式。', 'zh', '["物流", "时效", "配送"]', 9),
('物流', '在哪里查看物流信息？', '您可以在「物流管理」页面查看各个订单的物流跟踪信息与发货状态。', 'zh', '["物流", "跟踪", "查询"]', 8),
('物流', '支持哪些物流方式？', '系统支持国内快递、国际专线、海外仓发货等多种物流方式，可在物流管理中查看每种方式的适用场景。', 'zh', '["物流", "方式", "国际"]', 6),
('物流', '运费怎么算？', '运费与商品重量、体积和所选物流方式有关，系统会在定价时一并计算，您也可以在物流页面查看明细。', 'zh', '["物流", "运费", "邮费", "费用"]', 7),
('物流', '什么时候能收到货？', '国内订单通常1-3天送达，国际订单7-15天；如果所在地区偏远或遇海关清关，时效会有所延长。', 'zh', '["物流", "时效", "多久", "几天", "送达"]', 8),
-- ===== 库存 =====
('库存', '如何查看库存情况？', '您可以在库存管理页面查看所有产品的库存状态，包括当前库存、安全库存、预计到货时间等信息。', 'zh', '["库存", "查询"]', 9),
('库存', '缺货了什么时候能补上？', '系统会根据销量预测给出补货建议，在「库存预警」页面可以查看缺货商品和建议补货量。', 'zh', '["库存", "缺货", "补货", "补库"]', 8),
('库存', '安全库存是什么？', '安全库存是系统设定的最低库存水位，低于该水位会触发库存预警，避免断货影响销售。', 'zh', '["库存", "安全库存", "预警", "水位"]', 7),
('库存', '库存预测准吗？', '系统基于历史销量做趋势预测，您可以在「AI 库存预测」页面查看预测结果与补货建议，预测仅供参考。', 'zh', '["库存", "预测", "补货", "销量"]', 6),
('库存', '仓库和库位怎么管理？', '在仓库管理页面可以维护仓库与库位信息，库存作业（移库、冻结、报损、盘点等）也在该模块完成。', 'zh', '["库存", "仓库", "库位", "盘点"]', 6),
-- ===== 价格 =====
('价格', '产品价格会变动吗？', '我们的产品价格会根据市场情况动态调整，系统会自动优化定价策略。您可以在产品详情页查看价格历史。', 'zh', '["价格", "变动"]', 7),
('价格', '在哪里看定价建议？', '您可以在「AI 智能定价」页面查看系统给出的定价建议与价格变动原因，确认后再执行调价。', 'zh', '["价格", "定价", "调价", "建议"]', 7),
('价格', '怎么设置促销价？', '在定价模块可以基于竞品价格与成本制定促销策略，设定的价格会同步到各销售渠道。', 'zh', '["价格", "促销", "优惠", "折扣"]', 6),
('价格', '为什么不同平台价格不一样？', '各平台的手续费与竞争环境不同，系统会按渠道分别定价，您可以在定价页面看到各渠道的价格策略。', 'zh', '["价格", "渠道", "平台", "差异"]', 5),
-- ===== 售后 / 退款 =====
('售后', '如何申请退款？', '在订单详情页点击「申请售后」，选择退款类型和原因，提交后我们会尽快处理。', 'zh', '["退款", "售后"]', 8),
('售后', '退钱流程是什么？', '提交退款申请后，由售后人员审核，审核通过后按原支付渠道退回，具体时长以支付渠道为准。', 'zh', '["退款", "退钱", "流程", "售后"]', 8),
('售后', '产品有质量问题怎么办？', '如果产品有质量问题，请在收到货7天内申请售后，我们会安排退换货。', 'zh', '["售后", "质量", "退换货"]', 8),
('售后', '退货运费谁出？', '质量问题导致的退货运费由我们承担；非质量原因退货的运费按物流规则计算，售后页面会显示明细。', 'zh', '["售后", "运费", "退货运费"]', 6),
('售后', '退款多久到账？', '退款审核通过后按原支付渠道退回，到账时间取决于支付渠道，通常为1-7个工作日。', 'zh', '["退款", "到账", "多久", "几天"]', 7),
-- ===== 财务 =====
('财务', '在哪里查看财务报表？', '在「财务管理」页面可以查看收入、成本、利润等经营数据，并按时间维度做对比分析。', 'zh', '["财务", "报表", "利润", "收入"]', 7),
('财务', '怎么查看店铺营收？', '财务页面汇总了各平台的营收与结算情况，支持按平台和时间筛选查看。', 'zh', '["财务", "营收", "结算", "平台"]', 6),
('财务', '能做经营分析吗？', '可以。在「数据分析」页面可以查看销售趋势、渠道占比、利润结构等指标，辅助经营决策。', 'zh', '["财务", "分析", "趋势", "指标"]', 6),
-- ===== 账号 / 其他 =====
('账号', '忘记密码怎么办？', '在登录页面点击「忘记密码」，通过绑定的邮箱或手机号验证后即可重置密码。', 'zh', '["账号", "密码", "登录", "重置"]', 6),
('账号', '怎么切换店铺或租户？', '在页面顶部的租户切换器中可以选择当前操作的店铺，不同租户的数据彼此隔离。', 'zh', '["账号", "租户", "店铺", "切换"]', 5),
('产品', '产品信息在哪里维护？', '在产品管理页面可以维护商品标题、规格、价格、描述等信息，产品描述支持 AI 批量生成与 SEO 优化。', 'zh', '["产品", "商品", "维护", "描述"]', 6);

-- 插入示例用户
INSERT INTO chat_session (session_id, user_id, title, status, message_count) VALUES
('session-001', 1, '关于订单的咨询', 'ACTIVE', 0),
('session-002', 2, '产品问题', 'ACTIVE', 0),
('session-003', 3, '物流查询', 'ACTIVE', 0);

-- 查看数据
SELECT '数据库初始化完成！' AS message;
SELECT '会话数量' AS info, COUNT(*) AS value FROM chat_session;
SELECT '知识库条目数量' AS info, COUNT(*) AS value FROM knowledge_base;
