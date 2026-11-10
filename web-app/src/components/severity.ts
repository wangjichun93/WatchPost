import type { Severity, AlertState } from '@/types'

/** 告警级别元数据 (颜色/标签), 对应 PRD 四级: 提示/警告/严重/致命 */
export const SEVERITY_META: Record<Severity, { color: string; label: string }> = {
  INFO: { color: '#165dff', label: '提示' },
  WARN: { color: '#ff7d00', label: '警告' },
  CRITICAL: { color: '#f53f3f', label: '严重' },
  FATAL: { color: '#cb2634', label: '致命' },
}

/** 告警状态元数据 (颜色/标签), 对应收敛状态机 */
export const ALERT_STATE_META: Record<AlertState, { color: string; label: string }> = {
  NORMAL: { color: 'default', label: '正常' },
  PENDING: { color: 'processing', label: '待判定' },
  TRIGGERED: { color: 'error', label: '已触发' },
  NOTIFYING: { color: 'warning', label: '通知中' },
  ACKNOWLEDGED: { color: 'purple', label: '已确认' },
  RECOVERED: { color: 'success', label: '已恢复' },
  CLOSED: { color: 'default', label: '关闭' },
}

/** 监控对象状态元数据 (0停用 1正常 2不可达) */
export const MONITOR_STATUS_META: Record<number, { color: string; label: string }> = {
  0: { color: 'default', label: '停用' },
  1: { color: 'success', label: '正常' },
  2: { color: 'error', label: '不可达' },
}

/** app_type 显示名 */
export const APP_TYPE_LABEL: Record<string, string> = {
  linux: 'Linux 主机',
  http: 'HTTP API',
  mysql: 'MySQL',
  postgresql: 'PostgreSQL',
  oracle: 'Oracle',
}

/** 各 app_type 默认端口 */
export const APP_DEFAULT_PORT: Record<string, number> = {
  linux: 22,
  http: 80,
  mysql: 3306,
  postgresql: 5432,
  oracle: 1521,
}

/** 各 app_type 凭据字段定义 (表单动态渲染) */
export interface CredFieldDef {
  key: string
  label: string
  secret?: boolean
  multiline?: boolean   // 私钥用 TextArea
  optional?: boolean    // 非必填 (如密钥认证时口令可空)
  hint?: string
}

export const APP_CREDENTIAL_FIELDS: Record<string, CredFieldDef[]> = {
  linux: [
    { key: 'username', label: 'SSH 用户名' },
    { key: 'password', label: 'SSH 口令', secret: true, optional: true, hint: '口令与私钥二选一' },
    { key: 'privateKey', label: 'SSH 私钥 (PEM)', multiline: true, optional: true, hint: '-----BEGIN OPENSSH PRIVATE KEY----- ... 与口令二选一' },
    { key: 'passphrase', label: '私钥口令 (可选)', secret: true, optional: true },
  ],
  http: [
    { key: 'token', label: 'Token (可选)', secret: true, optional: true },
  ],
  mysql: [
    { key: 'username', label: '只读账号' },
    { key: 'password', label: '口令', secret: true },
  ],
  postgresql: [
    { key: 'username', label: '只读账号' },
    { key: 'password', label: '口令', secret: true },
  ],
  oracle: [
    { key: 'username', label: '只读账号' },
    { key: 'password', label: '口令', secret: true },
  ],
}
