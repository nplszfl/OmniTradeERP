-- 租户服务数据库初始化脚本
-- PostgreSQL

-- 创建租户表
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    domain VARCHAR(100) UNIQUE,
    plan VARCHAR(50) NOT NULL DEFAULT 'STARTER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    region VARCHAR(50),
    settings TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建租户用户表
CREATE TABLE IF NOT EXISTS tenant_users (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    name VARCHAR(100),
    role VARCHAR(50) NOT NULL DEFAULT 'OPERATOR',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- 创建租户索引
-- ⚠️ 必须加 IF NOT EXISTS：测试里两个 @SpringBootTest 类共用同一个
--    jdbc:h2:mem:testdb，schema.sql 会被跑第二遍，
--    而 CREATE INDEX 没有 IF NOT EXISTS → "Index already exists" → 整个上下文起不来
CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant_id ON tenant_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_domain ON tenants(domain);

-- 租户订阅计划详情表
CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    plan VARCHAR(50) NOT NULL,
    price INTEGER NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    auto_renew BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- AI用量记录表
CREATE TABLE IF NOT EXISTS ai_usage_records (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    usage_count INTEGER NOT NULL DEFAULT 0,
    cost DECIMAL(10, 2) NOT NULL DEFAULT 0,
    record_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_tenant_date ON ai_usage_records(tenant_id, record_date);