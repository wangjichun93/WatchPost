-- =====================================================================
-- V2__seed_data.sql — 初始化种子数据
-- 角色 / 出厂默认阈值规则 (对应 PRD 第03章) / 本地采集器节点 / 默认收件人组
-- 说明: admin 用户与内置采集模板由 Java 启动初始化器写入 (bcrypt 口令 + classpath YAML), 不在此硬编码
-- =====================================================================

-- ---------- 角色 ----------
INSERT INTO wp_role (code, name, description) VALUES
    ('ADMIN',    '管理员', '全部权限: 配置/用户/审计/通知策略'),
    ('OPERATOR', '运维',   '监控中心增删改查 + 阈值规则 + 告警处理'),
    ('READONLY', '只读',   '仅查看大盘/详情/告警')
ON CONFLICT (code) DO NOTHING;

-- ---------- 本地采集器节点 (v1 单机) ----------
INSERT INTO wp_collector_node (identity, mode, host, port, status, instance_cnt)
VALUES ('local-collector', 'public', '127.0.0.1', 1158, 1, 0)
ON CONFLICT (identity) DO NOTHING;

-- ---------- 默认收件人组 ----------
INSERT INTO wp_notify_group (name, members_json, description) VALUES
    ('运维值班', '[{"name":"ops","email":"ops@example.com"}]'::jsonb, '默认值班组, 请在 /notifications 维护真实成员')
ON CONFLICT (name) DO NOTHING;

-- =====================================================================
-- 出厂默认阈值规则 (scope=GLOBAL, scope_ref=app_type, priority=0 最低)
-- threshold_json: {value} / {min,max} / {baselineWindowSec,pct,direction}
-- =====================================================================

-- ---------- Linux 服务器 (PRD 03: 服务器指标) ----------
INSERT INTO wp_threshold_rule (scope, scope_ref, metric_group, field_name, operator, threshold_json, consecutive_n, window_sec, severity, priority, enabled) VALUES
    ('GLOBAL','linux','cpu','usage_percent','GE','{"value":85}',3,300,'WARN',0,TRUE),
    ('GLOBAL','linux','memory','usage_percent','GE','{"value":90}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','linux','disk','usage_percent','GE','{"value":85}',1,300,'WARN',0,TRUE),
    ('GLOBAL','linux','disk','usage_percent','GE','{"value":95}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','linux','load','load1_per_core','GE','{"value":2.0}',1,300,'WARN',0,TRUE),
    ('GLOBAL','linux','io','await_ms','GE','{"value":50}',1,300,'WARN',0,TRUE),
    ('GLOBAL','linux','tcp','established','GE','{"value":8000}',1,300,'WARN',0,TRUE),
    ('GLOBAL','linux','process','alive_count','EQ','{"value":0}',2,300,'CRITICAL',0,TRUE),
    ('GLOBAL','linux','host','reachable','EQ','{"value":0}',2,300,'CRITICAL',0,TRUE)
ON CONFLICT DO NOTHING;

-- ---------- HTTP API 嗅探 (PRD 03: 应用 API) ----------
INSERT INTO wp_threshold_rule (scope, scope_ref, metric_group, field_name, operator, threshold_json, consecutive_n, window_sec, severity, priority, enabled) VALUES
    ('GLOBAL','http','api','available','EQ','{"value":0}',2,300,'CRITICAL',0,TRUE),
    ('GLOBAL','http','api','p95_ms','GE','{"value":2000}',3,300,'WARN',0,TRUE),
    ('GLOBAL','http','api','status_5xx','GE','{"value":1}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','http','api','json_assert_fail','EQ','{"value":1}',2,300,'CRITICAL',0,TRUE),
    ('GLOBAL','http','api','ssl_cert_days','LE','{"value":14}',1,300,'WARN',0,TRUE),
    ('GLOBAL','http','api','ssl_cert_days','LE','{"value":3}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','http','api','body_keyword_missing','EQ','{"value":1}',2,300,'WARN',0,TRUE),
    ('GLOBAL','http','api','size_ratio','GE','{"value":3}',1,300,'INFO',0,TRUE)
ON CONFLICT DO NOTHING;

-- ---------- MySQL (PRD 05) ----------
INSERT INTO wp_threshold_rule (scope, scope_ref, metric_group, field_name, operator, threshold_json, consecutive_n, window_sec, severity, priority, enabled) VALUES
    ('GLOBAL','mysql','connections','usage_percent','GE','{"value":85}',1,300,'WARN',0,TRUE),
    ('GLOBAL','mysql','throughput','qps_drop_pct','DELTA_PCT','{"baselineWindowSec":300,"pct":50,"direction":"drop"}',1,300,'WARN',0,TRUE),
    ('GLOBAL','mysql','queries','slow_per_min','GE','{"value":20}',1,300,'WARN',0,TRUE),
    ('GLOBAL','mysql','innodb','buffer_pool_hit_rate','LT','{"value":95}',1,300,'WARN',0,TRUE),
    ('GLOBAL','mysql','replication','seconds_behind_master','GE','{"value":30}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','mysql','locks','max_wait_sec','GE','{"value":5}',1,300,'WARN',0,TRUE)
ON CONFLICT DO NOTHING;

-- ---------- PostgreSQL (PRD 05) ----------
INSERT INTO wp_threshold_rule (scope, scope_ref, metric_group, field_name, operator, threshold_json, consecutive_n, window_sec, severity, priority, enabled) VALUES
    ('GLOBAL','postgresql','connections','usage_percent','GE','{"value":85}',1,300,'WARN',0,TRUE),
    ('GLOBAL','postgresql','cache','hit_rate','LT','{"value":95}',1,300,'WARN',0,TRUE),
    ('GLOBAL','postgresql','tables','dead_tuples','GE','{"value":100000}',1,300,'INFO',0,TRUE),
    ('GLOBAL','postgresql','replication','lag_sec','GE','{"value":30}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','postgresql','transactions','max_duration_sec','GE','{"value":60}',1,300,'WARN',0,TRUE),
    ('GLOBAL','postgresql','transactions','rollback_rate','GE','{"value":10}',1,300,'WARN',0,TRUE)
ON CONFLICT DO NOTHING;

-- ---------- Oracle (PRD 05) ----------
INSERT INTO wp_threshold_rule (scope, scope_ref, metric_group, field_name, operator, threshold_json, consecutive_n, window_sec, severity, priority, enabled) VALUES
    ('GLOBAL','oracle','sessions','usage_percent','GE','{"value":85}',1,300,'WARN',0,TRUE),
    ('GLOBAL','oracle','tablespace','usage_percent','GE','{"value":85}',1,300,'WARN',0,TRUE),
    ('GLOBAL','oracle','tablespace','usage_percent','GE','{"value":95}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','oracle','memory','sga_usage_percent','GE','{"value":90}',1,300,'WARN',0,TRUE),
    ('GLOBAL','oracle','memory','pga_usage_percent','GE','{"value":90}',1,300,'WARN',0,TRUE),
    ('GLOBAL','oracle','redo','switches_per_hour','GE','{"value":12}',1,300,'WARN',0,TRUE),
    ('GLOBAL','oracle','archive','free_percent','LE','{"value":20}',1,300,'CRITICAL',0,TRUE),
    ('GLOBAL','oracle','waits','top_avg_wait_ms','GE','{"value":100}',1,300,'WARN',0,TRUE)
ON CONFLICT DO NOTHING;
