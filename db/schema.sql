-- =====================================================================
-- 数字内容与数据服务业务中台 —— MySQL 8 建表脚本（生产 / docker-compose 使用）
-- 与 backend/src/main/resources/db/h2/schema.sql 字段完全同构，仅方言不同
-- 执行：mysql -u root -p business_suite < db/schema.sql
--       mysql -u root -p business_suite < db/data.sql
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 系统 / 权限
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)     NOT NULL COMMENT '登录名',
    password      VARCHAR(200)    NOT NULL COMMENT 'PBKDF2 散列串 pbkdf2$迭代$盐$散列',
    nickname      VARCHAR(64)     NOT NULL COMMENT '昵称',
    avatar        VARCHAR(500)             DEFAULT NULL COMMENT '头像地址',
    phone         VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
    source        VARCHAR(16)     NOT NULL DEFAULT 'web' COMMENT 'web / miniapp',
    openid        VARCHAR(64)              DEFAULT NULL COMMENT '小程序 openid',
    last_login_at DATETIME                 DEFAULT NULL COMMENT '最近登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '后台用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(64)     NOT NULL COMMENT '角色标识，如 admin',
    name       VARCHAR(64)     NOT NULL COMMENT '角色名',
    remark     VARCHAR(255)             DEFAULT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-角色关联';

CREATE TABLE IF NOT EXISTS sys_menu (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    name       VARCHAR(64)     NOT NULL,
    path       VARCHAR(200)             DEFAULT NULL COMMENT '前端路由 path',
    component  VARCHAR(200)             DEFAULT NULL COMMENT '前端组件路径',
    icon       VARCHAR(64)              DEFAULT NULL,
    type       TINYINT         NOT NULL DEFAULT 1 COMMENT '0 目录 1 菜单 2 按钮',
    perm       VARCHAR(100)             DEFAULT NULL COMMENT '权限标识',
    sort       INT             NOT NULL DEFAULT 0,
    hidden     TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '菜单与按钮权限';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色-菜单关联';

-- ---------------------------------------------------------------------
-- 内容中心
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cms_category (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)     NOT NULL,
    code       VARCHAR(64)     NOT NULL,
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sort       INT             NOT NULL DEFAULT 0,
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    deleted    TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_category_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '内容分类';

CREATE TABLE IF NOT EXISTS cms_content (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)    NOT NULL,
    summary      VARCHAR(500)             DEFAULT NULL,
    body         LONGTEXT,
    cover_url    VARCHAR(500)             DEFAULT NULL,
    category_id  BIGINT UNSIGNED          DEFAULT NULL,
    content_type VARCHAR(16)     NOT NULL DEFAULT 'article' COMMENT 'article/gallery/video',
    status       TINYINT         NOT NULL DEFAULT 0 COMMENT '0 草稿 1 待审 2 已发布 3 已下架 4 已驳回',
    top_flag     TINYINT         NOT NULL DEFAULT 0,
    sort         INT             NOT NULL DEFAULT 0,
    author_id    BIGINT UNSIGNED          DEFAULT NULL,
    author_name  VARCHAR(64)              DEFAULT NULL,
    publish_time DATETIME                 DEFAULT NULL,
    view_count   BIGINT          NOT NULL DEFAULT 0,
    deleted      TINYINT         NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cms_content_status (status, deleted),
    KEY idx_cms_content_category (category_id),
    KEY idx_cms_content_publish (publish_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '内容';

CREATE TABLE IF NOT EXISTS cms_tag (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)     NOT NULL,
    code       VARCHAR(64)     NULL COMMENT '标签 slug，运营手输标签可为空',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_tag_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签';

CREATE TABLE IF NOT EXISTS cms_content_tag (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    content_id BIGINT UNSIGNED NOT NULL,
    tag_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_content_tag (content_id, tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '内容-标签关联';

-- ---------------------------------------------------------------------
-- 地理 / 点位
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS geo_region (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)     NOT NULL,
    code       VARCHAR(64)     NOT NULL,
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    level      TINYINT         NOT NULL DEFAULT 1,
    center_lng DOUBLE                   DEFAULT NULL,
    center_lat DOUBLE                   DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_region_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '地理区域';

CREATE TABLE IF NOT EXISTS geo_point (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100)    NOT NULL,
    region_id  BIGINT UNSIGNED          DEFAULT NULL,
    type       VARCHAR(16)     NOT NULL DEFAULT 'scenic' COMMENT 'scenic/heritage/plot/service',
    lng        DOUBLE          NOT NULL,
    lat        DOUBLE          NOT NULL,
    address    VARCHAR(255)             DEFAULT NULL,
    cover_url  VARCHAR(500)             DEFAULT NULL,
    summary    VARCHAR(1000)            DEFAULT NULL,
    status     TINYINT         NOT NULL DEFAULT 1,
    deleted    TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 边界框查询走联合索引，避免经纬度两次范围扫描
    KEY idx_geo_point_lnglat (lng, lat)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '地理点位（文旅资源 / 遥感地块）';

-- ---------------------------------------------------------------------
-- 活动 / 场次 / 报名
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title             VARCHAR(200)    NOT NULL,
    cover_url         VARCHAR(500)             DEFAULT NULL,
    region_id         BIGINT UNSIGNED          DEFAULT NULL,
    description       TEXT,
    status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1 上线 0 下线',
    signup_start_time DATETIME                 DEFAULT NULL,
    signup_end_time   DATETIME                 DEFAULT NULL,
    start_time        DATETIME                 DEFAULT NULL,
    end_time          DATETIME                 DEFAULT NULL,
    deleted           TINYINT         NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_status (status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '活动';

CREATE TABLE IF NOT EXISTS activity_session (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id BIGINT UNSIGNED NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    start_time  DATETIME                 DEFAULT NULL,
    end_time    DATETIME                 DEFAULT NULL,
    capacity    INT             NOT NULL DEFAULT 0 COMMENT '总名额',
    remain      INT             NOT NULL DEFAULT 0 COMMENT '剩余名额（并发扣减字段）',
    price       DECIMAL(10, 2)  NOT NULL DEFAULT 0.00,
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_session_activity (activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '活动场次';

CREATE TABLE IF NOT EXISTS activity_signup (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id    BIGINT UNSIGNED NOT NULL,
    activity_id   BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    contact_name  VARCHAR(64)     NOT NULL,
    contact_phone VARCHAR(20)     NOT NULL,
    code          VARCHAR(32)     NOT NULL COMMENT '核销码',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已报名 2 已核销 3 已取消',
    signup_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verify_time   DATETIME                 DEFAULT NULL,
    verify_by     BIGINT UNSIGNED          DEFAULT NULL,
    remark        VARCHAR(255)             DEFAULT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_signup_code (code),
    -- 带 status：允许"取消后重新报名"，同时让并发重复报名由数据库唯一键兜底
    UNIQUE KEY uk_activity_signup_session_user (session_id, user_id, status),
    KEY idx_activity_signup_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '活动报名';

CREATE TABLE IF NOT EXISTS signup_verify_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    signup_id   BIGINT UNSIGNED NOT NULL,
    operator_id BIGINT UNSIGNED          DEFAULT NULL,
    action      VARCHAR(16)     NOT NULL COMMENT 'verify/cancel',
    result      VARCHAR(16)     NOT NULL COMMENT 'success/fail',
    remark      VARCHAR(255)             DEFAULT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_signup_verify_log_signup (signup_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '核销日志';

-- ---------------------------------------------------------------------
-- AI 会话 / 操作日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_conversation (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    title         VARCHAR(200)    NOT NULL,
    scene         VARCHAR(32)     NOT NULL DEFAULT 'assistant',
    message_count INT             NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_conversation_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 会话';

CREATE TABLE IF NOT EXISTS ai_message (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id   BIGINT UNSIGNED NOT NULL,
    msg_role          VARCHAR(16)     NOT NULL COMMENT 'user/assistant',
    content           LONGTEXT        NOT NULL,
    provider          VARCHAR(32)              DEFAULT NULL,
    model             VARCHAR(64)              DEFAULT NULL,
    prompt_tokens     INT                      DEFAULT NULL,
    completion_tokens INT                      DEFAULT NULL,
    latency_ms        BIGINT                   DEFAULT NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_message_conversation (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 消息';

CREATE TABLE IF NOT EXISTS operation_log (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trace_id   VARCHAR(32)              DEFAULT NULL,
    user_id    BIGINT UNSIGNED          DEFAULT NULL,
    username   VARCHAR(64)              DEFAULT NULL,
    action     VARCHAR(64)              DEFAULT NULL,
    method     VARCHAR(8)               DEFAULT NULL,
    path       VARCHAR(255)             DEFAULT NULL,
    ip         VARCHAR(64)              DEFAULT NULL,
    cost_ms    BIGINT                   DEFAULT NULL,
    success    TINYINT         NOT NULL DEFAULT 1,
    detail     VARCHAR(1000)            DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_operation_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '操作日志';

SET FOREIGN_KEY_CHECKS = 1;
