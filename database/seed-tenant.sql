-- ============================================================
-- 9. 租户与多租户（活表 tenants / tenant_users /
--     tenant_feature_flags / platform_credentials /
--     tenant_usage_records / billing_rules / tenant_bills，库 erp_tenant）
-- ============================================================
-- ⚠️ 单独成文件而不是并进 seed-live-data.sql：
--    docker-entrypoint-initdb.d 里每个 .sql 是**一个独立的 mysql 会话**，
--    任何一条语句报错 → **从出错行起该文件剩余语句全部不执行**，
--    而容器退出码可能仍是 0 → 后面几百行种子**静默丢失**。
--    拆成独立文件 = 故障隔离，一段坏了不会连累别的段。
--
-- ⚠️ 本文件必须自带 `SET NAMES utf8mb4;`：
--    每个 init 文件由 docker-entrypoint 用**独立的 mysql 会话**执行，
--    容器级 locale 兜底不可靠 → 不写这句，官方 mysql:8.0 镜像（LANG 为空）
--    会按 POSIX locale 退化成 latin1 → 中文双重编码成乱码。
--
-- ⚠️ 之前**完全没有租户种子数据** ——
--    seed-data.sql 和 seed-live-data.sql 都没种 erp_tenant 的表，
--    结果：租户管理页面**列表空、看板 0、账单空、套餐是前端写死的假数据**。
--
-- ⚠️ 更严重的是 erp-tenant-service 的 SecurityConfig 配了
--    `anyRequest().authenticated()` 却**没有任何认证 provider/过滤器**，
--    → 除 /api/v1/auth/** 外**所有接口一律 403 空 body**，
--    连管理端的 GET /api/tenant/list 都进不去。
--    （已修：新增 TenantJwtAuthenticationFilter + 管理端 /api/tenant/** 放行）
--
-- ⚠️ id 是 VARCHAR(36)（Hibernate GenerationType.UUID），这里用可读的 UUID 串。
-- ⚠️⚠️ tenant_users.username 是**全局唯一**（uk_tenant_users_username 只建在 username 上，
--    不是 (tenant_id, username)）→ 每个租户的用户名必须互不相同。
--    踩过的坑：一开始每个租户都叫 'admin'，结果 INSERT 撞唯一键后走
--    `ON DUPLICATE KEY UPDATE` —— 它不是「跳过」，而是**把已存在的那一行改成指向新租户**
--    （cd-logistics 的 admin 把 dolphin 的 admin 行覆盖掉了）→ 静默的数据串租户。
-- ⚠️ 状态/类型取值必须和后端枚举逐字一致：
--    tenants.status        PENDING/ACTIVE/SUSPENDED/EXPIRED/DELETED
--    tenants.plan          STARTER/PROFESSIONAL/BUSINESS/ENTERPRISE
--    tenant_users.role     OWNER/ADMIN/MANAGER/OPERATOR/VIEWER
--    tenant_users.status   ACTIVE/INACTIVE/LOCKED
--    bills.bill_type       SUBSCRIPTION/USAGE/OVERDUE
--    bills.status          PENDING/PAID/OVERDUE/CANCELLED
--    billing_rules.resource_type  API_CALLS/STORAGE/ORDERS/PLATFORMS/AI_CALLS
--    billing_rules.tier_type      TIERED/VOLUME/FIXED
--    platform_credentials.status   ACTIVE/INACTIVE/ERROR/EXPIRED
SET NAMES utf8mb4;

USE `erp_tenant`;

-- 租户主体
INSERT INTO `tenants`
    (`id`, `name`, `domain`, `plan`, `status`, `region`, `settings`,
     `expires_at`, `created_at`, `updated_at`)
VALUES
    ('11111111-1111-4111-8111-111111111111', '深圳海豚跨境科技',   'dolphin-tech',  'BUSINESS',    'ACTIVE',    'cn-beijing', '{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 300 DAY, NOW() - INTERVAL 420 DAY, NOW()),
    ('22222222-2222-4222-8222-222222222222', '义乌小商品王国',     'yiwu-kingdom',  'PROFESSIONAL','ACTIVE',    'cn-hangzhou','{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 180 DAY, NOW() - INTERVAL 260 DAY, NOW()),
    ('33333333-3333-4333-8333-333333333333', '广州美妆优选',       'gz-beauty',     'PROFESSIONAL','ACTIVE',    'cn-guangzhou','{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 90 DAY,  NOW() - INTERVAL 150 DAY, NOW()),
    ('44444444-4444-4444-8444-444444444444', '宁波家纺工坊',       'nb-textile',    'STARTER',     'ACTIVE',    'cn-ningbo', '{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 45 DAY,  NOW() - INTERVAL 90 DAY,  NOW()),
    ('55555555-5555-4555-8555-555555555555', '苏州精密五金',       'sz-hardware',   'STARTER',     'SUSPENDED', 'cn-suzhou',  '{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() - INTERVAL 10 DAY,  NOW() - INTERVAL 200 DAY, NOW()),
    ('66666666-6666-4666-8666-666666666666', '厦门环球贸易',       'xm-global',     'ENTERPRISE',  'ACTIVE',    'cn-xiamen',  '{"currency":"USD","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 700 DAY, NOW() - INTERVAL 600 DAY, NOW()),
    ('77777777-7777-4777-8777-777777777777', '杭州极客智造',       'hz-geek',       'STARTER',     'PENDING',   'cn-hangzhou','{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() + INTERVAL 30 DAY,  NOW() - INTERVAL 5 DAY,   NOW()),
    ('88888888-8888-4888-8888-888888888888', '成都蜀道物流',       'cd-logistics',  'PROFESSIONAL','EXPIRED',   'cn-chengdu', '{"currency":"CNY","timezone":"Asia/Shanghai"}', NOW() - INTERVAL 30 DAY,  NOW() - INTERVAL 400 DAY, NOW())
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `domain` = VALUES(`domain`), `plan` = VALUES(`plan`),
    `status` = VALUES(`status`), `region` = VALUES(`region`), `settings` = VALUES(`settings`),
    `expires_at` = VALUES(`expires_at`);

-- 租户用户（密码统一为 **admin123** 的 bcrypt 哈希）
--
-- ⚠️⚠️ 这里原来用的哈希 `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`
--    **不是合法的 bcrypt 串**（salt 段长度不对），BCrypt.checkpw 直接返回 false
--    → 任何密码都登不进去，登录接口永远返回「密码错误」。
--    症状极具迷惑性：接口正常响应、错误信息也正常，但**没有一个账号能登录**。
--    验证方式（别猜密码）：
--      BCrypt.checkpw("admin123", hash)  → 必须是 true
--    下面是 spring-security BCryptPasswordEncoder("admin123") 的真实输出。
INSERT INTO `tenant_users`
    (`id`, `tenant_id`, `username`, `email`, `password`, `phone`, `name`,
     `role`, `status`, `last_login_at`, `created_at`)
VALUES
    ('a1000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'dolphin_admin',   'admin@dolphin-tech.example.com',   '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000001', '张海豚', 'OWNER',    'ACTIVE',   NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 420 DAY),
    ('a1000001-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'manager', 'manager@dolphin-tech.example.com','$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000002', '李运营', 'MANAGER',  'ACTIVE',   NOW() - INTERVAL 3 DAY,  NOW() - INTERVAL 300 DAY),
    ('a1000002-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222', 'yiwu_admin',      'admin@yiwu-kingdom.example.com',  '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000003', '王商城', 'OWNER',    'ACTIVE',   NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 260 DAY),
    ('a1000003-0000-4000-8000-000000000004', '33333333-3333-4333-8333-333333333333', 'beauty_admin',    'admin@gz-beauty.example.com',     '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000004', '陈美妆', 'OWNER',    'ACTIVE',   NOW() - INTERVAL 5 DAY,  NOW() - INTERVAL 150 DAY),
    ('a1000003-0000-4000-8000-000000000005', '33333333-3333-4333-8333-333333333333', 'designer','designer@gz-beauty.example.com', '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000005', '刘设计', 'OPERATOR', 'ACTIVE',   NOW() - INTERVAL 9 DAY,  NOW() - INTERVAL 120 DAY),
    ('a1000004-0000-4000-8000-000000000006', '44444444-4444-4444-8444-444444444444', 'textile_admin',   'admin@nb-textile.example.com',    '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000006', '赵家纺', 'OWNER',    'ACTIVE',   NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 90 DAY),
    ('a1000006-0000-4000-8000-000000000007', '66666666-6666-4666-8666-666666666666', 'global_admin',    'admin@xm-global.example.com',    '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000007', '孙环球', 'OWNER',    'ACTIVE',   NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 600 DAY),
    ('a1000006-0000-4000-8000-000000000008', '66666666-6666-4666-8666-666666666666', 'viewer',  'viewer@xm-global.example.com',   '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000008', '周观察', 'VIEWER',   'ACTIVE',   NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 300 DAY),
    ('a1000007-0000-4000-8000-000000000009', '77777777-7777-4777-8777-777777777777', 'geek_admin',      'admin@hz-geek.example.com',       '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000009', '吴极客', 'OWNER',    'INACTIVE', NULL,                     NOW() - INTERVAL 5 DAY),
    ('a1000008-0000-4000-8000-000000000010', '88888888-8888-4888-8888-888888888888', 'cd_admin',        'admin@cd-logistics.example.com',  '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '13800000010', '郑蜀道', 'OWNER',    'LOCKED',   NULL,                     NOW() - INTERVAL 400 DAY)
ON DUPLICATE KEY UPDATE
    `tenant_id` = VALUES(`tenant_id`), `email` = VALUES(`email`), `phone` = VALUES(`phone`),
    `name` = VALUES(`name`), `role` = VALUES(`role`), `status` = VALUES(`status`),
    `last_login_at` = VALUES(`last_login_at`);

-- 功能开关（键名必须和 TenantFeatureFlag.DEFAULT_FEATURES 一致）
INSERT INTO `tenant_feature_flags`
    (`id`, `tenant_id`, `feature_key`, `feature_name`, `enabled`, `config`, `description`)
VALUES
    ('f1000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'AI_ASSISTANT',    'AI智能助手',   1, NULL, '智能客服与商品描述生成'),
    ('f1000001-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'AUTO_SYNC',       '自动同步',     1, NULL, '平台订单自动同步'),
    ('f1000001-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'ADVANCED_REPORT', '高级报表',     1, NULL, '多维度经营分析'),
    ('f1000001-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'API_ACCESS',      'API访问',      1, NULL, '开放平台 API'),
    ('f1000001-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'MULTI_USER',      '多用户协作',   1, NULL, '团队协作与权限'),
    ('f1000001-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', 'EXPORT_EXCEL',    'Excel导出',    1, NULL, '报表导出'),
    ('f1000001-0000-4000-8000-000000000007', '11111111-1111-4111-8111-111111111111', 'WHITE_LABEL',     '白标定制',     0, NULL, '自定义品牌'),
    ('f1000001-0000-4000-8000-000000000008', '11111111-1111-4111-8111-111111111111', 'WEBHOOK',         'Webhooks',     0, NULL, '事件推送'),
    ('f1000002-0000-4000-8000-000000000009', '22222222-2222-4222-8222-222222222222', 'AI_ASSISTANT',    'AI智能助手',   1, NULL, '智能客服与商品描述生成'),
    ('f1000002-0000-4000-8000-000000000010', '22222222-2222-4222-8222-222222222222', 'AUTO_SYNC',       '自动同步',     1, NULL, '平台订单自动同步'),
    ('f1000002-0000-4000-8000-000000000011', '22222222-2222-4222-8222-222222222222', 'EXPORT_EXCEL',    'Excel导出',    1, NULL, '报表导出'),
    ('f1000003-0000-4000-8000-000000000012', '33333333-3333-4333-8333-333333333333', 'AI_ASSISTANT',    'AI智能助手',   1, NULL, '智能客服与商品描述生成'),
    ('f1000003-0000-4000-8000-000000000013', '33333333-3333-4333-8333-333333333333', 'MULTI_USER',      '多用户协作',   1, NULL, '团队协作与权限'),
    ('f1000004-0000-4000-8000-000000000014', '44444444-4444-4444-8444-444444444444', 'EXPORT_EXCEL',    'Excel导出',    1, NULL, '报表导出'),
    ('f1000006-0000-4000-8000-000000000015', '66666666-6666-4666-8666-666666666666', 'AI_ASSISTANT',    'AI智能助手',   1, NULL, '智能客服与商品描述生成'),
    ('f1000006-0000-4000-8000-000000000016', '66666666-6666-4666-8666-666666666666', 'AUTO_SYNC',       '自动同步',     1, NULL, '平台订单自动同步'),
    ('f1000006-0000-4000-8000-000000000017', '66666666-6666-4666-8666-666666666666', 'ADVANCED_REPORT', '高级报表',     1, NULL, '多维度经营分析'),
    ('f1000006-0000-4000-8000-000000000018', '66666666-6666-4666-8666-666666666666', 'API_ACCESS',      'API访问',      1, NULL, '开放平台 API'),
    ('f1000006-0000-4000-8000-000000000019', '66666666-6666-4666-8666-666666666666', 'WHITE_LABEL',     '白标定制',     1, NULL, '自定义品牌'),
    ('f1000006-0000-4000-8000-000000000020', '66666666-6666-4666-8666-666666666666', 'MULTI_USER',      '多用户协作',   1, NULL, '团队协作与权限'),
    ('f1000006-0000-4000-8000-000000000021', '66666666-6666-4666-8666-666666666666', 'WEBHOOK',         'Webhooks',     1, NULL, '事件推送'),
    ('f1000006-0000-4000-8000-000000000022', '66666666-6666-4666-8666-666666666666', 'EXPORT_EXCEL',    'Excel导出',    1, NULL, '报表导出')
ON DUPLICATE KEY UPDATE
    `tenant_id` = VALUES(`tenant_id`), `feature_name` = VALUES(`feature_name`),
    `enabled` = VALUES(`enabled`), `config` = VALUES(`config`),
    `description` = VALUES(`description`);

-- 平台凭据
INSERT INTO `platform_credentials`
    (`id`, `tenant_id`, `platform`, `shop_name`, `shop_id`, `credentials`,
     `client_id`, `client_secret`, `developer_id`, `refresh_token`, `access_token`,
     `token_expires_at`, `marketplace`, `status`, `last_sync_at`, `created_at`)
VALUES
    ('c1000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'AMAZON', '海豚美国店',   'ATVPDKIKX0DER', NULL, 'amz-cn-001', 'sec-***', 'dev-001', 'rt-amazon-001', 'at-amazon-001', NOW() + INTERVAL 30 DAY,  'US',     'ACTIVE',   NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 400 DAY),
    ('c1000001-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'SHOPEE', '海豚东南亚店', 'S1234567',      NULL, 'shp-cn-001', 'sec-***', 'dev-002', 'rt-shopee-001', 'at-shopee-001', NOW() + INTERVAL 14 DAY,  'SG',     'ACTIVE',   NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 380 DAY),
    ('c1000001-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'TIKTOK', '海豚TikTok店',  'tk-001',        NULL, 'tk-cn-001',  'sec-***', 'dev-003', 'rt-tiktok-001', 'at-tiktok-001', NOW() - INTERVAL 2 DAY,  'US',     'EXPIRED',  NOW() - INTERVAL 8 DAY,  NOW() - INTERVAL 300 DAY),
    ('c1000002-0000-4000-8000-000000000004', '22222222-2222-4222-8222-222222222222', 'AMAZON', '义乌欧洲店',   'A1B2C3D4E5',    NULL, 'amz-cn-002', 'sec-***', 'dev-011', 'rt-amazon-002', 'at-amazon-002', NOW() + INTERVAL 25 DAY,  'DE',     'ACTIVE',   NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 250 DAY),
    ('c1000002-0000-4000-8000-000000000005', '22222222-2222-4222-8222-222222222222', 'EBAY',   '义乌全球店',   'E-001',         NULL, 'eby-cn-001', 'sec-***', 'dev-012', 'rt-ebay-001',   'at-ebay-001',   NOW() + INTERVAL 10 DAY,  'US',     'ACTIVE',   NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 200 DAY),
    ('c1000003-0000-4000-8000-000000000006', '33333333-3333-4333-8333-333333333333', 'LAZADA', '美妆马来店',   'LZ-001',        NULL, 'lzd-cn-001', 'sec-***', 'dev-021', 'rt-lazada-001', 'at-lazada-001', NOW() + INTERVAL 20 DAY,  'MY',     'ACTIVE',   NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 140 DAY),
    ('c1000003-0000-4000-8000-000000000007', '33333333-3333-4333-8333-333333333333', 'AMAZON', '美妆北美店',   'ATVPDKIKX0DES', NULL, 'amz-cn-003', 'sec-***', 'dev-022', 'rt-amazon-003', 'at-amazon-003', NOW() + INTERVAL 5 DAY,   'US',     'ERROR',    NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 130 DAY),
    ('c1000004-0000-4000-8000-000000000008', '44444444-4444-4444-8444-444444444444', 'SHOPEE', '家纺新加坡店', 'S7654321',      NULL, 'shp-cn-004', 'sec-***', 'dev-031', 'rt-shopee-004', 'at-shopee-004', NOW() + INTERVAL 12 DAY,  'SG',     'ACTIVE',   NOW() - INTERVAL 30 HOUR,NOW() - INTERVAL 85 DAY),
    ('c1000006-0000-4000-8000-000000000009', '66666666-6666-4666-8666-666666666666', 'AMAZON', '环球美国店',   'ATVPDKIKX0DFT', NULL, 'amz-cn-006', 'sec-***', 'dev-061', 'rt-amazon-006', 'at-amazon-006', NOW() + INTERVAL 28 DAY,  'US',     'ACTIVE',   NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 590 DAY),
    ('c1000006-0000-4000-8000-000000000010', '66666666-6666-4666-8666-666666666666', 'TIKTOK', '环球TikTok店',  'tk-006',        NULL, 'tk-cn-006',  'sec-***', 'dev-062', 'rt-tiktok-006', 'at-tiktok-006', NOW() + INTERVAL 18 DAY,  'US',     'ACTIVE',   NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 580 DAY),
    ('c1000006-0000-4000-8000-000000000011', '66666666-6666-4666-8666-666666666666', 'EBAY',   '环球欧洲店',   'E-006',         NULL, 'eby-cn-006', 'sec-***', 'dev-063', 'rt-ebay-006',   'at-ebay-006',   NOW() + INTERVAL 3 DAY,   'DE',     'ACTIVE',   NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 500 DAY),
    ('c1000006-0000-4000-8000-000000000012', '66666666-6666-4666-8666-666666666666', 'SHOPIFY','环球独立站',   'shop-006',      NULL, 'shfy-cn-001','sec-***', 'dev-064', 'rt-shopify-001','at-shopify-001',NOW() + INTERVAL 22 DAY,  'US',     'ACTIVE',   NOW() - INTERVAL 7 HOUR, NOW() - INTERVAL 520 DAY),
    ('c1000005-0000-4000-8000-000000000013', '55555555-5555-4555-8555-555555555555', 'AMAZON', '五金旧店',     'ATVPDKIKX0DGG', NULL, 'amz-cn-005', 'sec-***', 'dev-051', 'rt-amazon-005', 'at-amazon-005', NOW() - INTERVAL 40 DAY, 'US',     'INACTIVE', NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 190 DAY),
    ('c1000008-0000-4000-8000-000000000014', '88888888-8888-4888-8888-888888888888', 'AMAZON', '蜀道旧店',     'ATVPDKIKX0DHH', NULL, 'amz-cn-008', 'sec-***', 'dev-081', 'rt-amazon-008', 'at-amazon-008', NOW() - INTERVAL 90 DAY, 'US',     'EXPIRED',  NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 390 DAY)
ON DUPLICATE KEY UPDATE
    `tenant_id` = VALUES(`tenant_id`), `platform` = VALUES(`platform`),
    `shop_name` = VALUES(`shop_name`), `shop_id` = VALUES(`shop_id`),
    `client_id` = VALUES(`client_id`), `marketplace` = VALUES(`marketplace`),
    `status` = VALUES(`status`), `token_expires_at` = VALUES(`token_expires_at`),
    `last_sync_at` = VALUES(`last_sync_at`);

-- 用量记录（最近 6 个自然月）
INSERT INTO `tenant_usage_records`
    (`id`, `tenant_id`, `billing_period`, `api_calls`, `storage_bytes`,
     `order_count`, `platform_count`, `ai_calls`, `created_at`, `updated_at`)
VALUES
    ('u1000001-0000-4000-8000-20260501', '11111111-1111-4111-8111-111111111111', '2026-05', 128500,  3221225472,  860, 3,  12400, NOW() - INTERVAL 180 DAY, NOW()),
    ('u1000001-0000-4000-8000-20260601', '11111111-1111-4111-8111-111111111111', '2026-06', 145200,  4294967296,  972, 3,  15800, NOW() - INTERVAL 150 DAY, NOW()),
    ('u1000001-0000-4000-8000-20260701', '11111111-1111-4111-8111-111111111111', '2026-07', 162800,  5368709120, 1085, 3,  19200, NOW() - INTERVAL 120 DAY, NOW()),
    ('u1000001-0000-4000-8000-20260801', '11111111-1111-4111-8111-111111111111', '2026-08', 158300,  5368709120, 1042, 4,  18600, NOW() - INTERVAL 90 DAY,  NOW()),
    ('u1000001-0000-4000-8000-20260901', '11111111-1111-4111-8111-111111111111', '2026-09', 171600,  6442450944, 1158, 4,  21500, NOW() - INTERVAL 60 DAY,  NOW()),
    ('u1000001-0000-4000-8000-20261001', '11111111-1111-4111-8111-111111111111', '2026-10', 184900,  7516192768, 1264, 4,  24800, NOW() - INTERVAL 30 DAY,  NOW()),
    ('u1000002-0000-4000-8000-20260501', '22222222-2222-4222-8222-222222222222', '2026-05',  46200,  1073741824,  310, 2,   3200, NOW() - INTERVAL 180 DAY, NOW()),
    ('u1000002-0000-4000-8000-20260601', '22222222-2222-4222-8222-222222222222', '2026-06',  51800,  1288490188,  355, 2,   3900, NOW() - INTERVAL 150 DAY, NOW()),
    ('u1000002-0000-4000-8000-20260701', '22222222-2222-4222-8222-222222222222', '2026-07',  57300,  1610612736,  402, 2,   4600, NOW() - INTERVAL 120 DAY, NOW()),
    ('u1000002-0000-4000-8000-20260801', '22222222-2222-4222-8222-222222222222', '2026-08',  58900,  1610612736,  418, 3,   4900, NOW() - INTERVAL 90 DAY,  NOW()),
    ('u1000002-0000-4000-8000-20260901', '22222222-2222-4222-8222-222222222222', '2026-09',  62400,  1932735283,  447, 3,   5400, NOW() - INTERVAL 60 DAY,  NOW()),
    ('u1000002-0000-4000-8000-20261001', '22222222-2222-4222-8222-222222222222', '2026-10',  67900,  2147483648,  483, 3,   6100, NOW() - INTERVAL 30 DAY,  NOW()),
    ('u1000003-0000-4000-8000-20260801', '33333333-3333-4333-8333-333333333333', '2026-08',  28400,   536870912,  196, 2,   2400, NOW() - INTERVAL 90 DAY,  NOW()),
    ('u1000003-0000-4000-8000-20260901', '33333333-3333-4333-8333-333333333333', '2026-09',  31600,   805306368,  218, 2,   2900, NOW() - INTERVAL 60 DAY,  NOW()),
    ('u1000003-0000-4000-8000-20261001', '33333333-3333-4333-8333-333333333333', '2026-10',  34200,  1073741824,  236, 2,   3300, NOW() - INTERVAL 30 DAY,  NOW()),
    ('u1000004-0000-4000-8000-20260901', '44444444-4444-4444-8444-444444444444', '2026-09',   8600,   268435456,   58, 1,    400, NOW() - INTERVAL 60 DAY,  NOW()),
    ('u1000004-0000-4000-8000-20261001', '44444444-4444-4444-8444-444444444444', '2026-10',  11200,   322122547,   74, 1,    520, NOW() - INTERVAL 30 DAY,  NOW()),
    ('u1000006-0000-4000-8000-20260501', '66666666-6666-4666-8666-666666666666', '2026-05', 312400,  9663676416, 2450, 4,  36400, NOW() - INTERVAL 180 DAY, NOW()),
    ('u1000006-0000-4000-8000-20260601', '66666666-6666-4666-8666-666666666666', '2026-06', 356800, 10737418240,2780, 4,  42800, NOW() - INTERVAL 150 DAY, NOW()),
    ('u1000006-0000-4000-8000-20260701', '66666666-6666-4666-8666-666666666666', '2026-07', 398200, 11811160064,3095, 5,  48600, NOW() - INTERVAL 120 DAY, NOW()),
    ('u1000006-0000-4000-8000-20260801', '66666666-6666-4666-8666-666666666666', '2026-08', 421600, 12884901888,3268, 5,  52400, NOW() - INTERVAL 90 DAY,  NOW()),
    ('u1000006-0000-4000-8000-20260901', '66666666-6666-4666-8666-666666666666', '2026-09', 448900, 13958643712,3512, 6,  56800, NOW() - INTERVAL 60 DAY,  NOW()),
    ('u1000006-0000-4000-8000-20261001', '66666666-6666-4666-8666-666666666666', '2026-10', 476300, 15032385536,3724, 6,  61200, NOW() - INTERVAL 30 DAY,  NOW())
ON DUPLICATE KEY UPDATE
    `api_calls` = VALUES(`api_calls`), `storage_bytes` = VALUES(`storage_bytes`),
    `order_count` = VALUES(`order_count`), `platform_count` = VALUES(`platform_count`),
    `ai_calls` = VALUES(`ai_calls`);

-- ⚠️ **当前月**的用量必须单独种一行，而且 billing_period 要**动态生成**：
--    写死 '2026-11' 的话，过一个月「本月用量」就永远是 0
--    （看板/用量页读的是当前周期，不是历史周期）。
--    DATE_FORMAT(NOW(), '%Y-%m') 保证永远跟着当月走。
--    id 固定为 *-current → 重复执行时命中主键 → 幂等。
INSERT INTO `tenant_usage_records`
    (`id`, `tenant_id`, `billing_period`, `api_calls`, `storage_bytes`,
     `order_count`, `platform_count`, `ai_calls`, `created_at`, `updated_at`)
VALUES
    ('u1000001-current', '11111111-1111-4111-8111-111111111111', DATE_FORMAT(NOW(), '%Y-%m'),  96400,  3221225472,   642, 4, 12900, NOW(), NOW()),
    ('u1000002-current', '22222222-2222-4222-8222-222222222222', DATE_FORMAT(NOW(), '%Y-%m'),  35800,  1073741824,   251, 3,  3200, NOW(), NOW()),
    ('u1000003-current', '33333333-3333-4333-8333-333333333333', DATE_FORMAT(NOW(), '%Y-%m'),  17900,   536870912,   124, 2,  1700, NOW(), NOW()),
    ('u1000004-current', '44444444-4444-4444-8444-444444444444', DATE_FORMAT(NOW(), '%Y-%m'),   5800,   268435456,    39, 1,   260, NOW(), NOW()),
    ('u1000006-current', '66666666-6666-4666-8666-666666666666', DATE_FORMAT(NOW(), '%Y-%m'), 248700,  7516192768,  1936, 6, 31800, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `billing_period` = VALUES(`billing_period`),
    `api_calls` = VALUES(`api_calls`), `storage_bytes` = VALUES(`storage_bytes`),
    `order_count` = VALUES(`order_count`), `platform_count` = VALUES(`platform_count`),
    `ai_calls` = VALUES(`ai_calls`);

-- 计费规则
INSERT INTO `billing_rules`
    (`id`, `name`, `resource_type`, `tier_type`, `free_quota`, `unit_price`,
     `tier_config`, `active`, `created_at`, `updated_at`)
VALUES
    ('b1000001-0000-4000-8000-000000000001', 'API调用计费',   'API_CALLS', 'TIERED', 10000,  0.0080, '{"tiers":[{"upTo":50000,"price":0.008},{"upTo":200000,"price":0.006},{"upTo":null,"price":0.004}]}', 1, NOW() - INTERVAL 400 DAY, NOW()),
    ('b1000001-0000-4000-8000-000000000002', '存储空间计费',   'STORAGE',   'VOLUME',  1073741824, 0.0000, '{"unit":"GB","perGB":0.35}', 1, NOW() - INTERVAL 400 DAY, NOW()),
    ('b1000001-0000-4000-8000-000000000003', '订单处理计费',   'ORDERS',    'FIXED',  0,       0.5000, '{"perOrder":0.5}', 1, NOW() - INTERVAL 400 DAY, NOW()),
    ('b1000001-0000-4000-8000-000000000004', '平台接入计费',   'PLATFORMS', 'FIXED',  1,       29.9000, '{"perPlatform":29.9}', 1, NOW() - INTERVAL 400 DAY, NOW()),
    ('b1000001-0000-4000-8000-000000000005', 'AI调用计费',     'AI_CALLS',  'TIERED', 1000,    0.0200, '{"tiers":[{"upTo":10000,"price":0.02},{"upTo":50000,"price":0.015},{"upTo":null,"price":0.01}]}', 1, NOW() - INTERVAL 400 DAY, NOW())
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `resource_type` = VALUES(`resource_type`),
    `tier_type` = VALUES(`tier_type`), `free_quota` = VALUES(`free_quota`),
    `unit_price` = VALUES(`unit_price`), `tier_config` = VALUES(`tier_config`),
    `active` = VALUES(`active`);

-- 账单
INSERT INTO `tenant_bills`
    (`id`, `tenant_id`, `billing_period`, `bill_type`, `status`,
     `subscription_fee`, `usage_fee`, `discount`, `total_amount`, `paid_amount`,
     `payment_method`, `paid_at`, `due_date`, `details`, `remark`, `created_at`, `updated_at`)
VALUES
    ('bill-1111-202605', '11111111-1111-4111-8111-111111111111', '2026-05', 'SUBSCRIPTION', 'PAID',    12999.00,  486.20,  0.00, 13485.20, 13485.20, 'BANK_TRANSFER', NOW() - INTERVAL 170 DAY, NOW() - INTERVAL 165 DAY, '{"apiCalls":128500,"orders":860}', '5月账单', NOW() - INTERVAL 175 DAY, NOW()),
    ('bill-1111-202606', '11111111-1111-4111-8111-111111111111', '2026-06', 'SUBSCRIPTION', 'PAID',    12999.00,  512.60,  0.00, 13511.60, 13511.60, 'BANK_TRANSFER', NOW() - INTERVAL 140 DAY, NOW() - INTERVAL 135 DAY, '{"apiCalls":145200,"orders":972}', '6月账单', NOW() - INTERVAL 145 DAY, NOW()),
    ('bill-1111-202607', '11111111-1111-4111-8111-111111111111', '2026-07', 'SUBSCRIPTION', 'PAID',    12999.00,  596.40,  500.00, 13095.40, 13095.40, 'BANK_TRANSFER', NOW() - INTERVAL 110 DAY, NOW() - INTERVAL 105 DAY, '{"apiCalls":162800,"orders":1085}', '7月账单（含活动折扣）', NOW() - INTERVAL 115 DAY, NOW()),
    ('bill-1111-202608', '11111111-1111-4111-8111-111111111111', '2026-08', 'SUBSCRIPTION', 'PAID',    12999.00,  582.10,  0.00, 13581.10, 13581.10, 'BANK_TRANSFER', NOW() - INTERVAL 80 DAY,  NOW() - INTERVAL 75 DAY,  '{"apiCalls":158300,"orders":1042}', '8月账单', NOW() - INTERVAL 85 DAY,  NOW()),
    ('bill-1111-202609', '11111111-1111-4111-8111-111111111111', '2026-09', 'SUBSCRIPTION', 'PAID',    12999.00,  635.80,  0.00, 13634.80, 13634.80, 'BANK_TRANSFER', NOW() - INTERVAL 50 DAY,  NOW() - INTERVAL 45 DAY,  '{"apiCalls":171600,"orders":1158}', '9月账单', NOW() - INTERVAL 55 DAY,  NOW()),
    ('bill-1111-202610', '11111111-1111-4111-8111-111111111111', '2026-10', 'SUBSCRIPTION', 'PENDING', 12999.00,  688.30,  0.00, 13687.30,     0.00, NULL,                 NULL,                  NOW() + INTERVAL 5 DAY,   '{"apiCalls":184900,"orders":1264}', '10月账单待支付', NOW() - INTERVAL 25 DAY, NOW()),
    ('bill-2222-202608', '22222222-2222-4222-8222-222222222222', '2026-08', 'SUBSCRIPTION', 'PAID',    4999.00,   186.40,  0.00,  5185.40,  5185.40, 'BANK_TRANSFER', NOW() - INTERVAL 80 DAY, NOW() - INTERVAL 75 DAY, '{"apiCalls":58900,"orders":418}', '8月账单', NOW() - INTERVAL 85 DAY, NOW()),
    ('bill-2222-202609', '22222222-2222-4222-8222-222222222222', '2026-09', 'SUBSCRIPTION', 'PAID',    4999.00,   208.60,  0.00,  5207.60,  5207.60, 'BANK_TRANSFER', NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 45 DAY, '{"apiCalls":62400,"orders":447}', '9月账单', NOW() - INTERVAL 55 DAY, NOW()),
    ('bill-2222-202610', '22222222-2222-4222-8222-222222222222', '2026-10', 'SUBSCRIPTION', 'PENDING', 4999.00,   232.10,  0.00,  5231.10,     0.00, NULL,                 NULL,                  NOW() + INTERVAL 7 DAY,   '{"apiCalls":67900,"orders":483}', '10月账单待支付', NOW() - INTERVAL 25 DAY, NOW()),
    ('bill-3333-202609', '33333333-3333-4333-8333-333333333333', '2026-09', 'SUBSCRIPTION', 'PAID',    4999.00,   142.30,  0.00,  5141.30,  5141.30, 'BANK_TRANSFER', NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 45 DAY, '{"apiCalls":31600,"orders":218}', '9月账单', NOW() - INTERVAL 55 DAY, NOW()),
    ('bill-3333-202610', '33333333-3333-4333-8333-333333333333', '2026-10', 'SUBSCRIPTION', 'OVERDUE', 4999.00,   156.80,  0.00,  5155.80,     0.00, NULL,                 NULL,                  NOW() - INTERVAL 3 DAY,   '{"apiCalls":34200,"orders":236}', '10月账单已逾期', NOW() - INTERVAL 25 DAY, NOW()),
    ('bill-4444-202610', '44444444-4444-4444-8444-444444444444', '2026-10', 'SUBSCRIPTION', 'PENDING', 2999.00,    48.60,  0.00,  3047.60,     0.00, NULL,                 NULL,                  NOW() + INTERVAL 10 DAY,  '{"apiCalls":11200,"orders":74}', '10月账单待支付', NOW() - INTERVAL 25 DAY, NOW()),
    ('bill-6666-202609', '66666666-6666-4666-8666-666666666666', '2026-09', 'SUBSCRIPTION', 'PAID',       0.00, 1836.40,  0.00,  1836.40,  1836.40, 'BANK_TRANSFER', NOW() - INTERVAL 50 DAY, NOW() - INTERVAL 45 DAY, '{"apiCalls":448900,"orders":3512}', '9月用量账单（企业版无订阅费）', NOW() - INTERVAL 55 DAY, NOW()),
    ('bill-6666-202610', '66666666-6666-4666-8666-666666666666', '2026-10', 'USAGE',        'PENDING',    0.00, 1976.20,  0.00,  1976.20,     0.00, NULL,                 NULL,                  NOW() + INTERVAL 3 DAY,   '{"apiCalls":476300,"orders":3724}', '10月用量账单待支付', NOW() - INTERVAL 25 DAY, NOW()),
    ('bill-8888-202609', '88888888-8888-4888-8888-888888888888', '2026-09', 'OVERDUE',       'OVERDUE', 4999.00,   168.20,  0.00,  5167.20,  2000.00, 'BANK_TRANSFER', NOW() - INTERVAL 55 DAY, NOW() - INTERVAL 50 DAY, '{"apiCalls":0,"orders":0}', '9月账单逾期，仅部分付款', NOW() - INTERVAL 55 DAY, NOW())
ON DUPLICATE KEY UPDATE
    `bill_type` = VALUES(`bill_type`), `status` = VALUES(`status`),
    `subscription_fee` = VALUES(`subscription_fee`), `usage_fee` = VALUES(`usage_fee`),
    `discount` = VALUES(`discount`), `total_amount` = VALUES(`total_amount`),
    `paid_amount` = VALUES(`paid_amount`), `payment_method` = VALUES(`payment_method`),
    `paid_at` = VALUES(`paid_at`), `due_date` = VALUES(`due_date`),
    `details` = VALUES(`details`), `remark` = VALUES(`remark`);

-- ⚠️ 同上：**当前月**账单必须动态生成 billing_period，
--    否则「本月账单」永远是空（看板的 currentBill 会显示 0 / NO_BILL）。
--    id 固定 → 幂等。
INSERT INTO `tenant_bills`
    (`id`, `tenant_id`, `billing_period`, `bill_type`, `status`,
     `subscription_fee`, `usage_fee`, `discount`, `total_amount`, `paid_amount`,
     `payment_method`, `paid_at`, `due_date`, `details`, `remark`, `created_at`, `updated_at`)
VALUES
    ('bill-1111-current', '11111111-1111-4111-8111-111111111111', DATE_FORMAT(NOW(), '%Y-%m'), 'SUBSCRIPTION', 'PENDING', 12999.00, 358.40, 0.00, 13357.40, 0.00, NULL, NULL, NOW() + INTERVAL 5 DAY,  '{"apiCalls":96400,"orders":642}',  '本月账单待支付', NOW(), NOW()),
    ('bill-2222-current', '22222222-2222-4222-8222-222222222222', DATE_FORMAT(NOW(), '%Y-%m'), 'SUBSCRIPTION', 'PENDING',  4999.00, 121.60, 0.00,  5120.60, 0.00, NULL, NULL, NOW() + INTERVAL 7 DAY,  '{"apiCalls":35800,"orders":251}',  '本月账单待支付', NOW(), NOW()),
    ('bill-3333-current', '33333333-3333-4333-8333-333333333333', DATE_FORMAT(NOW(), '%Y-%m'), 'SUBSCRIPTION', 'PENDING',  4999.00,  62.80, 0.00,  5061.80, 0.00, NULL, NULL, NOW() + INTERVAL 3 DAY,  '{"apiCalls":17900,"orders":124}',  '本月账单待支付', NOW(), NOW()),
    ('bill-4444-current', '44444444-4444-4444-8444-444444444444', DATE_FORMAT(NOW(), '%Y-%m'), 'SUBSCRIPTION', 'PENDING',  2999.00,  18.20, 0.00,  3017.20, 0.00, NULL, NULL, NOW() + INTERVAL 10 DAY, '{"apiCalls":5800,"orders":39}',    '本月账单待支付', NOW(), NOW()),
    ('bill-6666-current', '66666666-6666-4666-8666-666666666666', DATE_FORMAT(NOW(), '%Y-%m'), 'USAGE',        'PENDING',     0.00, 1024.60, 0.00,  1024.60, 0.00, NULL, NULL, NOW() + INTERVAL 3 DAY,  '{"apiCalls":248700,"orders":1936}','本月用量账单待支付', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `billing_period` = VALUES(`billing_period`),
    `bill_type` = VALUES(`bill_type`), `status` = VALUES(`status`),
    `subscription_fee` = VALUES(`subscription_fee`), `usage_fee` = VALUES(`usage_fee`),
    `discount` = VALUES(`discount`), `total_amount` = VALUES(`total_amount`),
    `paid_amount` = VALUES(`paid_amount`), `payment_method` = VALUES(`payment_method`),
    `paid_at` = VALUES(`paid_at`), `due_date` = VALUES(`due_date`),
    `details` = VALUES(`details`), `remark` = VALUES(`remark`);
