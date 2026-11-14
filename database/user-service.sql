-- ===================================================
-- 用户与权限服务数据库初始化脚本 (MySQL)
-- ===================================================
-- 数据库: erp_user
--
-- 表清单:
--   t_user             用户
--   t_role             角色
--   t_user_role        用户-角色关联
--   t_permission       权限（树形）
--   t_role_permission  角色-权限关联
--   t_department       部门（树形）
--   t_user_department  用户-部门关联
--
-- 说明: t_user / t_role / t_user_role / t_permission / t_role_permission
--       在 init.sql 里早就存在（而且有种子数据），但 Java 侧一直没有实体和 Controller，
--       前端 /user /role /permission /department 的调用全是 404。
--       2026-11-02 起补齐整套 RBAC。
-- ===================================================

-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_user`;

-- ===================================================
-- 用户
-- ===================================================
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `email` VARCHAR(200) COMMENT '邮箱',
    `phone` VARCHAR(50) COMMENT '手机号',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(64) COMMENT '最后登录IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ===================================================
-- 角色
-- ===================================================
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(500) COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ===================================================
-- 权限（树形）
-- ===================================================
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父权限ID(根为0)',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '权限编码',
    `permission_name` VARCHAR(200) NOT NULL COMMENT '权限名称',
    `permission_type` VARCHAR(50) NOT NULL COMMENT '权限类型(menu/button/api)',
    `url` VARCHAR(500) COMMENT '菜单URL/API路径',
    `component` VARCHAR(255) COMMENT '前端组件路径',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ===================================================
-- 部门（树形）—— 新增
-- ===================================================
DROP TABLE IF EXISTS `t_department`;
CREATE TABLE `t_department` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `dept_code` VARCHAR(50) NOT NULL COMMENT '部门编码',
    `dept_name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '上级部门ID(根为0)',
    `leader` VARCHAR(50) COMMENT '负责人',
    `phone` VARCHAR(50) COMMENT '联系电话',
    `email` VARCHAR(200) COMMENT '邮箱',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_dept_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ===================================================
-- 关联表
-- ===================================================
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_ur_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_rp_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

DROP TABLE IF EXISTS `t_user_department`;
CREATE TABLE `t_user_department` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `department_id` BIGINT NOT NULL COMMENT '部门ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_department` (`user_id`, `department_id`),
    KEY `idx_ud_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户部门关联表';

-- ===================================================
-- 种子数据（与 database/seed-data.sql 保持一致）
-- ===================================================
INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `description`, `status`, `sort_order`) VALUES
(1, 'SUPER_ADMIN',  '超级管理员', '拥有全部权限',           1, 1),
(2, 'OPS_ADMIN',    '运营管理员', '负责日常运营，订单/商品/库存', 1, 2),
(3, 'FINANCE',      '财务人员',   '查看财务报表、处理结算',      1, 3),
(4, 'WAREHOUSE',    '仓库管理员', '负责出入库、库存盘点',        1, 4),
(5, 'CUSTOMER_SERVICE', '客服人员', '处理客户问题与售后',      1, 5)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name), description = VALUES(description),
    status = VALUES(status), sort_order = VALUES(sort_order);

-- 密码 admin123（BCrypt）
INSERT INTO `t_user` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `status`, `last_login_time`) VALUES
(1, 'admin',      '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '系统管理员', 'admin@crossborder.com',    '13800138000', 1, NOW()),
(2, 'ops001',     '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '张运营',    'ops001@crossborder.com',   '13800138001', 1, NOW()),
(3, 'finance001', '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '李财务',    'finance001@crossborder.com','13800138002', 1, NOW()),
(4, 'wh001',      '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '王仓管',    'wh001@crossborder.com',    '13800138003', 1, NOW()),
(5, 'cs001',      '$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree', '赵客服',    'cs001@crossborder.com',   '13800138004', 1, NOW())
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name), email = VALUES(email), phone = VALUES(phone),
    status = VALUES(status);
-- 📌 **password 不更新** —— 管理员改过密码后，重跑不该把它打回默认值。
-- 📌 **last_login_time 不更新** —— 那是运行时状态。

INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 1),(2, 2, 2),(3, 3, 3),(4, 4, 4),(5, 5, 5)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);

INSERT INTO `t_department` (`id`, `dept_code`, `dept_name`, `parent_id`, `leader`, `phone`, `email`, `sort_order`, `status`) VALUES
(1, 'HQ',       '总公司',   0, '黄辉翔', '13800138000', 'hq@crossborder.com',       1, 1),
(2, 'OPS',      '运营部',   1, '张运营',  '13800138001', 'ops@crossborder.com',      2, 1),
(3, 'FIN',      '财务部',   1, '李财务',  '13800138002', 'fin@crossborder.com',      3, 1),
(4, 'WH',       '仓储部',   1, '王仓管',  '13800138003', 'wh@crossborder.com',       4, 1),
(5, 'CS',       '客服部',   1, '赵客服',  '13800138004', 'cs@crossborder.com',       5, 1)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    dept_name = VALUES(dept_name), parent_id = VALUES(parent_id),
    leader = VALUES(leader), phone = VALUES(phone), email = VALUES(email),
    sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO `t_user_department` (`id`, `user_id`, `department_id`) VALUES
(1, 1, 1),(2, 2, 2),(3, 3, 3),(4, 4, 4),(5, 5, 5)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    department_id = VALUES(department_id);
