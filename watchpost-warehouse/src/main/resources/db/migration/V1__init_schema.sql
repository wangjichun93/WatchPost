-- =====================================================================
-- 望哨 WATCHPOST · 无 Agent 实时监控系统
-- V1__init_schema.sql — 初始化全量表结构 (PostgreSQL 16)
-- 对应《系统架构设计 v1.0》§4 领域模型 ER 图
-- 约定: 主键 bigint 自增; 时间戳 timestamptz; 软删除 deleted bool; 审计字段 create_time/update_time
-- =====================================================================

-- ---------- 枚举类型 ----------
DO $$ BEGIN
    CREATE TYPE role_code AS ENUM ('ADMIN', 'OPERATOR', 'READONLY');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE app_type AS ENUM ('linux', 'http', 'mysql', 'postgresql', 'oracle');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE protocol_type AS ENUM ('ssh', 'snmp', 'ipmi', 'http', 'jdbc');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE severity_level AS ENUM ('INFO', 'WARN', 'CRITICAL', 'FATAL');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE alert_state AS ENUM ('NORMAL', 'PENDING', 'TRIGGERED', 'NOTIFYING', 'ACKNOWLEDGED', 'RECOVERED', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE rule_scope AS ENUM ('GLOBAL', 'TYPE', 'INSTANCE', 'FIELD');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE compare_op AS ENUM ('GT', 'GE', 'LT', 'LE', 'EQ', 'NE', 'BETWEEN', 'DELTA_PCT');
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- =====================================================================
-- 1. 用户 / 角色 / 权限 (RBAC 三角色)
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,          -- 用户名/工号
    password_hash   VARCHAR(100) NOT NULL,                 -- bcrypt
    display_name    VARCHAR(64),
    email           VARCHAR(128),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_count    INT          NOT NULL DEFAULT 0,       -- 连续失败次数
    lock_until      TIMESTAMPTZ,                           -- 锁定截止 (5次失败锁15min)
    last_login_at   TIMESTAMPTZ,
    last_login_ip   VARCHAR(64),
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_user IS 'Web 登录用户 (与被监控 DB 账号体系完全分离)';

CREATE TABLE IF NOT EXISTS wp_role (
    id          BIGSERIAL PRIMARY KEY,
    code        role_code    NOT NULL UNIQUE,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(128)
);
COMMENT ON TABLE wp_role IS '角色: ADMIN 管理员 / OPERATOR 运维 / READONLY 只读';

CREATE TABLE IF NOT EXISTS wp_user_role (
    user_id BIGINT NOT NULL REFERENCES wp_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES wp_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- =====================================================================
-- 2. 采集模板 / 监控对象 / 指标字段
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_monitor_template (
    id          BIGSERIAL PRIMARY KEY,
    app_type    app_type      NOT NULL UNIQUE,             -- linux/http/mysql/postgresql/oracle
    protocol    protocol_type NOT NULL,
    name        VARCHAR(64)   NOT NULL,
    yaml_spec   TEXT          NOT NULL,                    -- 采集模板 YAML (命令/SQL/字段映射/单位换算)
    version     INT           NOT NULL DEFAULT 1,
    builtin     BOOLEAN       NOT NULL DEFAULT TRUE,       -- 是否内置模板
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ   NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_monitor_template IS '按 app_type 内置的采集模板, 存 YAML 规范, 支持热加载';

CREATE TABLE IF NOT EXISTS wp_monitor (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    app_type      app_type     NOT NULL,
    protocol      protocol_type NOT NULL,
    host          VARCHAR(128) NOT NULL,                   -- 地址/IP/URL
    port          INT,                                     -- 端口
    interval_sec  INT          NOT NULL DEFAULT 30,        -- 采集周期 (>=15)
    template_id   BIGINT       REFERENCES wp_monitor_template(id),
    status        SMALLINT     NOT NULL DEFAULT 1,         -- 0停用 1正常 2不可达
    conn_fail_cnt INT          NOT NULL DEFAULT 0,         -- 连续失败计数 (主机可达性规则依赖)
    last_collect_at TIMESTAMPTZ,
    last_value_json JSONB,                                 -- 最近一次采集快照 (冗余, 详情秒开)
    remark        VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_by     BIGINT,
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_monitor_app_type ON wp_monitor(app_type) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_monitor_status   ON wp_monitor(status)   WHERE deleted = FALSE;
COMMENT ON TABLE wp_monitor IS '监控对象实例 (用户接入的目标)';

CREATE TABLE IF NOT EXISTS wp_metric_field (
    id           BIGSERIAL PRIMARY KEY,
    monitor_id   BIGINT      NOT NULL REFERENCES wp_monitor(id) ON DELETE CASCADE,
    template_id  BIGINT      REFERENCES wp_monitor_template(id),
    metric_group VARCHAR(64) NOT NULL,                     -- cpu/memory/disk/load/io/tcp/process/api/...
    field_name   VARCHAR(64) NOT NULL,                     -- usage_percent/await_ms/...
    field_type   VARCHAR(16) NOT NULL DEFAULT 'double',    -- double/long/int/string/bool
    unit         VARCHAR(16),                              -- %/ms/MB/...
    label_flag   BOOLEAN     NOT NULL DEFAULT FALSE,       -- 是否作为标签维度 (如 disk mountpoint)
    sort_no      INT         NOT NULL DEFAULT 0,
    create_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (monitor_id, metric_group, field_name)
);
CREATE INDEX IF NOT EXISTS idx_metric_field_monitor ON wp_metric_field(monitor_id);
COMMENT ON TABLE wp_metric_field IS '指标字段 (一等公民, 每个字段可独立绑定阈值规则)';

CREATE TABLE IF NOT EXISTS wp_tag (
    id          BIGSERIAL PRIMARY KEY,
    tag_key     VARCHAR(64) NOT NULL,
    tag_value   VARCHAR(64) NOT NULL,
    UNIQUE (tag_key, tag_value)
);

CREATE TABLE IF NOT EXISTS wp_monitor_tag (
    monitor_id BIGINT NOT NULL REFERENCES wp_monitor(id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES wp_tag(id)     ON DELETE CASCADE,
    PRIMARY KEY (monitor_id, tag_id)
);

-- =====================================================================
-- 3. 凭据 (独立加密表, AES-256-GCM 密文)
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_credential (
    id          BIGSERIAL PRIMARY KEY,
    monitor_id  BIGINT      NOT NULL REFERENCES wp_monitor(id) ON DELETE CASCADE,
    cred_type   protocol_type NOT NULL,                    -- ssh/snmp/ipmi/jdbc
    username    VARCHAR(128),
    cipher_text TEXT        NOT NULL,                      -- AES-256-GCM 密文 (口令/私钥/community)
    kms_key_id  VARCHAR(64),                               -- 主密钥来源标识 (env/KMS), 密钥本身不落库
    extra_json  JSONB,                                     -- 其他非敏感参数 (如 SNMP version, IPMI cipher suite)
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (monitor_id, cred_type)
);
COMMENT ON TABLE wp_credential IS '采集凭据密文; 运行时解密注入采集器, 用后即弃; 页面/日志脱敏';

-- =====================================================================
-- 4. 阈值规则 (逐字段可配, 优先级覆写)
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_threshold_rule (
    id              BIGSERIAL PRIMARY KEY,
    scope           rule_scope   NOT NULL,                 -- GLOBAL/TYPE/INSTANCE/FIELD
    scope_ref       VARCHAR(64),                           -- 作用域引用 (app_type 或 monitor_id 或 field_id)
    monitor_id      BIGINT       REFERENCES wp_monitor(id) ON DELETE CASCADE,
    metric_field_id BIGINT       REFERENCES wp_metric_field(id) ON DELETE CASCADE,
    metric_group    VARCHAR(64),
    field_name      VARCHAR(64),
    operator        compare_op   NOT NULL,                 -- GT/GE/LT/LE/EQ/NE/BETWEEN/DELTA_PCT
    threshold_json  JSONB        NOT NULL,                 -- {value:85} / {min:14,max:99} / {baselineWindowSec:300,pct:50}
    consecutive_n   INT          NOT NULL DEFAULT 3,       -- 连续 N 次
    window_sec      INT          NOT NULL DEFAULT 300,     -- 时间窗口 (默认5min)
    severity        severity_level NOT NULL DEFAULT 'WARN',
    effective_period VARCHAR(32) NOT NULL DEFAULT 'ALL_DAY', -- ALL_DAY/WORK/CRON
    cron_expr       VARCHAR(64),                           -- effective_period=CRON 时生效
    suppress_sec    INT          NOT NULL DEFAULT 600,     -- 抑制窗口 (同指标10min不重发)
    recovery_notify BOOLEAN      NOT NULL DEFAULT TRUE,    -- 恢复通知
    notify_policy_id BIGINT,                               -- 通知策略 (FK 后置, 见下)
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    priority        INT          NOT NULL DEFAULT 0,       -- 实例>类型>全局 (数值大者优先)
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rule_field    ON wp_threshold_rule(metric_field_id);
CREATE INDEX IF NOT EXISTS idx_rule_monitor  ON wp_threshold_rule(monitor_id);
CREATE INDEX IF NOT EXISTS idx_rule_scope    ON wp_threshold_rule(scope, scope_ref);
COMMENT ON TABLE wp_threshold_rule IS '阈值规则: 每个 metric field 一条可独立编辑规则; 优先级 实例>类型>全局';

-- =====================================================================
-- 5. 通知: 收件人组 / 通知策略 / 投递日志
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_notify_group (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL UNIQUE,             -- 数据库值班/网络组...
    members_json JSONB        NOT NULL,                    -- [{name,email,phone?}]
    description  VARCHAR(255),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_notify_group IS '收件人组 (按团队/值班表/标签分组)';

CREATE TABLE IF NOT EXISTS wp_notify_policy (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(64) NOT NULL,
    match_tags_json  JSONB,                                -- 匹配的对象标签 {env:prod}
    match_severity   VARCHAR(64),                          -- 匹配的告警级别, 逗号分隔 (CRITICAL,FATAL); 空=全部
    notify_group_id  BIGINT      NOT NULL REFERENCES wp_notify_group(id) ON DELETE CASCADE,
    enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    priority         INT         NOT NULL DEFAULT 0,
    create_time      TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time      TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_notify_policy IS '通知策略: 告警级别 × 对象标签 → 收件人组';

-- 补: threshold_rule.notify_policy_id 外键 (建表顺序后置)
ALTER TABLE wp_threshold_rule
    DROP CONSTRAINT IF EXISTS fk_rule_notify_policy;
ALTER TABLE wp_threshold_rule
    ADD CONSTRAINT fk_rule_notify_policy
    FOREIGN KEY (notify_policy_id) REFERENCES wp_notify_policy(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS wp_smtp_config (
    id           BIGSERIAL PRIMARY KEY,
    host         VARCHAR(128) NOT NULL,
    port         INT          NOT NULL DEFAULT 465,
    starttls     BOOLEAN      NOT NULL DEFAULT TRUE,
    sender       VARCHAR(128) NOT NULL,
    auth_cipher  TEXT         NOT NULL,                    -- 授权码 AES-256-GCM 密文
    kms_key_id   VARCHAR(64),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_smtp_config IS 'SMTP 配置 (单条生效, 授权码加密)';

CREATE TABLE IF NOT EXISTS wp_delivery_log (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     BIGINT,                                   -- FK 后置 (alert 表在后)
    channel      VARCHAR(16)  NOT NULL DEFAULT 'EMAIL',    -- EMAIL/WEBHOOK(预留)
    recipient    VARCHAR(128) NOT NULL,
    subject      VARCHAR(255),
    status       SMALLINT     NOT NULL,                    -- 0待发 1成功 2失败 3退信
    retry_no     INT          NOT NULL DEFAULT 0,          -- 第几次重试 (1/5/15min)
    next_retry_at TIMESTAMPTZ,
    error_reason VARCHAR(512),
    sent_at      TIMESTAMPTZ,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_delivery_status ON wp_delivery_log(status, next_retry_at);
COMMENT ON TABLE wp_delivery_log IS '邮件投递日志 (全量可查 + 失败重试调度)';

-- =====================================================================
-- 6. 告警 / 告警事件 (收敛状态机)
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_alert (
    id              BIGSERIAL PRIMARY KEY,
    alert_no        VARCHAR(32)  NOT NULL UNIQUE,          -- A-2317
    monitor_id      BIGINT       NOT NULL REFERENCES wp_monitor(id) ON DELETE CASCADE,
    metric_field_id BIGINT       REFERENCES wp_metric_field(id) ON DELETE SET NULL,
    rule_id         BIGINT       REFERENCES wp_threshold_rule(id) ON DELETE SET NULL,
    metric_group    VARCHAR(64),
    field_name      VARCHAR(64),
    state           alert_state  NOT NULL DEFAULT 'PENDING',
    severity        severity_level NOT NULL,
    current_value   VARCHAR(64),
    threshold_desc  VARCHAR(128),                          -- "≥ 30 s"
    hit_count       INT          NOT NULL DEFAULT 1,       -- 命中次数 (重复合并递增)
    first_trigger_at TIMESTAMPTZ,
    last_trigger_at TIMESTAMPTZ,
    ack_by          BIGINT,                                -- 确认人
    ack_at          TIMESTAMPTZ,
    recovered_at    TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    notify_group_id BIGINT       REFERENCES wp_notify_group(id) ON DELETE SET NULL,
    silenced_until  TIMESTAMPTZ,                           -- 静默截止 (维护窗口/静默1h)
    fingerprint     VARCHAR(128) NOT NULL,                 -- 收敛指纹: monitor+field+rule, 用于合并重复命中
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_alert_state     ON wp_alert(state);
CREATE INDEX IF NOT EXISTS idx_alert_monitor   ON wp_alert(monitor_id);
CREATE INDEX IF NOT EXISTS idx_alert_fingerprint ON wp_alert(fingerprint) WHERE state NOT IN ('CLOSED');
CREATE INDEX IF NOT EXISTS idx_alert_severity  ON wp_alert(severity, state);
COMMENT ON TABLE wp_alert IS '告警主体 (活动+历史); 同 fingerprint 重复命中合并为一条, 仅递增 hit_count';

CREATE TABLE IF NOT EXISTS wp_alert_event (
    id         BIGSERIAL PRIMARY KEY,
    alert_id   BIGINT      NOT NULL REFERENCES wp_alert(id) ON DELETE CASCADE,
    from_state alert_state,
    to_state   alert_state NOT NULL,
    operator   VARCHAR(64),                                -- 系统/用户名
    note       VARCHAR(512),
    create_time TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_alert_event_alert ON wp_alert_event(alert_id);
COMMENT ON TABLE wp_alert_event IS '告警状态迁移与确认审计';

-- 补: delivery_log.alert_id 外键
ALTER TABLE wp_delivery_log
    DROP CONSTRAINT IF EXISTS fk_delivery_alert;
ALTER TABLE wp_delivery_log
    ADD CONSTRAINT fk_delivery_alert
    FOREIGN KEY (alert_id) REFERENCES wp_alert(id) ON DELETE SET NULL;

-- =====================================================================
-- 7. 审计日志
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(64),
    action      VARCHAR(64)  NOT NULL,                     -- LOGIN/LOGOUT/CONFIG_CHANGE/ALERT_ACK/...
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    detail_json JSONB,                                     -- 脱敏后的变更详情
    ip          VARCHAR(64),
    user_agent  VARCHAR(255),
    success     BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_user   ON wp_audit_log(user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action ON wp_audit_log(action, create_time DESC);
COMMENT ON TABLE wp_audit_log IS '登录与配置变更全量审计 (详情脱敏)';

-- =====================================================================
-- 8. 时序历史 (v1 默认 PostgreSQL 落盘; 生产可插拔切换 IoTDB)
--    说明: 架构默认 TSDB 为 Apache IoTDB; 此表为单机起步/降级方案,
--    通过 TimeSeriesStore SPI 隔离, 切换 IoTDB 后此表可停用。
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_metric_history (
    id          BIGSERIAL,
    monitor_id  BIGINT      NOT NULL,
    app_type    VARCHAR(16) NOT NULL,
    metric_group VARCHAR(64) NOT NULL,
    field_name  VARCHAR(64) NOT NULL,
    field_type  SMALLINT    NOT NULL DEFAULT 0,            -- 0数值 1字符串 2时间
    dbl_value   DOUBLE PRECISION,
    str_value   VARCHAR(255),
    labels_json JSONB,
    record_time BIGINT      NOT NULL,                      -- epoch millis
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, record_time)
) PARTITION BY RANGE (record_time);
CREATE INDEX IF NOT EXISTS idx_history_query ON wp_metric_history(monitor_id, metric_group, field_name, record_time DESC);
COMMENT ON TABLE wp_metric_history IS '时序历史 (按月分区, TTL 30天由分区滚动清理; 生产建议切换 IoTDB)';

-- 初始分区 (当月 + 下月), 后续由 wp_partition_cron 维护
DO $$
DECLARE
    m_start BIGINT; m_end BIGINT; part_name TEXT; i INT;
BEGIN
    FOR i IN 0..1 LOOP
        m_start := (extract(epoch FROM date_trunc('month', now()) + (i || ' month')::interval) * 1000)::BIGINT;
        m_end   := (extract(epoch FROM date_trunc('month', now()) + ((i+1) || ' month')::interval) * 1000)::BIGINT;
        part_name := 'wp_metric_history_' || to_char(date_trunc('month', now()) + (i || ' month')::interval, 'YYYYMM');
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF wp_metric_history FOR VALUES FROM (%s) TO (%s)',
                       part_name, m_start, m_end);
    END LOOP;
END $$;

-- =====================================================================
-- 9. 采集器注册 (集群预留; v1 单机也登记一条本地采集器)
-- =====================================================================
CREATE TABLE IF NOT EXISTS wp_collector_node (
    id           BIGSERIAL PRIMARY KEY,
    identity     VARCHAR(64)  NOT NULL UNIQUE,             -- 采集器唯一标识
    mode         VARCHAR(16)  NOT NULL DEFAULT 'public',   -- public/private
    host         VARCHAR(128),
    port         INT,
    status       SMALLINT     NOT NULL DEFAULT 1,          -- 0离线 1在线
    instance_cnt INT          NOT NULL DEFAULT 0,          -- 承载实例数
    last_heartbeat TIMESTAMPTZ,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE wp_collector_node IS '采集器节点注册 (v1 单机一条; 集群期水平扩展, 调度分片依据)';

-- =====================================================================
-- update_time 自动维护触发器
-- =====================================================================
CREATE OR REPLACE FUNCTION wp_touch_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['wp_user','wp_monitor_template','wp_monitor','wp_credential',
                            'wp_threshold_rule','wp_notify_group','wp_notify_policy',
                            'wp_smtp_config','wp_alert'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_touch ON %I', t, t);
        EXECUTE format('CREATE TRIGGER trg_%s_touch BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION wp_touch_update_time()', t, t);
    END LOOP;
END $$;
