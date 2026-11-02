-- H2 schema（测试环境）
-- 对应 database/user-service.sql 的 7 张表

CREATE TABLE IF NOT EXISTS t_user (
    id               BIGINT       NOT NULL PRIMARY KEY,
    username         VARCHAR(100) NOT NULL,
    password         VARCHAR(255) NOT NULL,
    real_name        VARCHAR(100),
    email            VARCHAR(200),
    phone            VARCHAR(50),
    avatar           VARCHAR(500),
    status           TINYINT      NOT NULL DEFAULT 1,
    last_login_time  TIMESTAMP,
    last_login_ip    VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_role (
    id           BIGINT       NOT NULL PRIMARY KEY,
    role_code    VARCHAR(50)  NOT NULL,
    role_name    VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    status       TINYINT      NOT NULL DEFAULT 1,
    sort_order   INT          NOT NULL DEFAULT 0,
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_permission (
    id               BIGINT       NOT NULL PRIMARY KEY,
    parent_id        BIGINT       NOT NULL DEFAULT 0,
    permission_code  VARCHAR(100) NOT NULL,
    permission_name  VARCHAR(200) NOT NULL,
    permission_type  VARCHAR(50)  NOT NULL,
    url              VARCHAR(500),
    component        VARCHAR(255),
    icon             VARCHAR(100),
    sort_order       INT          NOT NULL DEFAULT 0,
    status           TINYINT      NOT NULL DEFAULT 1,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_department (
    id           BIGINT       NOT NULL PRIMARY KEY,
    dept_code    VARCHAR(50)  NOT NULL,
    dept_name    VARCHAR(100) NOT NULL,
    parent_id    BIGINT       NOT NULL DEFAULT 0,
    leader       VARCHAR(50),
    phone        VARCHAR(50),
    email        VARCHAR(200),
    sort_order   INT          NOT NULL DEFAULT 0,
    status       TINYINT      NOT NULL DEFAULT 1,
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_user_role (
    id           BIGINT    NOT NULL PRIMARY KEY,
    user_id      BIGINT    NOT NULL,
    role_id      BIGINT    NOT NULL,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_role_permission (
    id             BIGINT    NOT NULL PRIMARY KEY,
    role_id        BIGINT    NOT NULL,
    permission_id  BIGINT    NOT NULL,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_user_department (
    id             BIGINT    NOT NULL PRIMARY KEY,
    user_id        BIGINT    NOT NULL,
    department_id  BIGINT    NOT NULL,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
