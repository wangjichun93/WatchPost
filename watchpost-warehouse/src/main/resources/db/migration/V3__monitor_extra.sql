-- =====================================================================
-- V3__monitor_extra.sql — 监控对象扩展配置 (HTTP 探测等)
-- 背景: HTTP API 监控需要 per-monitor 的 method/path/headers/body/期望状态码,
--       原 wp_monitor 无此列, 导致前端无法配置 GET/POST 探测。
--  additive 变更, 不影响已有数据。
-- =====================================================================
ALTER TABLE wp_monitor
    ADD COLUMN IF NOT EXISTS extra_json JSONB;

COMMENT ON COLUMN wp_monitor.extra_json IS
    '扩展配置 JSON: HTTP 型 {method,path,headers,body,expectStatus}; 其他类型预留';
