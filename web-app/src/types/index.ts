// 望哨 WATCHPOST · 前端类型定义 (对应 docs/api/openapi.yaml)

/** 统一 API 响应包络 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页结果 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// ---- 枚举 ----
export type AppType = 'linux' | 'http' | 'mysql' | 'postgresql' | 'oracle'
export type ProtocolType = 'ssh' | 'snmp' | 'ipmi' | 'http' | 'jdbc'
export type Severity = 'INFO' | 'WARN' | 'CRITICAL' | 'FATAL'
export type AlertState =
  | 'NORMAL' | 'PENDING' | 'TRIGGERED' | 'NOTIFYING'
  | 'ACKNOWLEDGED' | 'RECOVERED' | 'CLOSED'
export type RuleScope = 'GLOBAL' | 'TYPE' | 'INSTANCE' | 'FIELD'
export type CompareOp = 'GT' | 'GE' | 'LT' | 'LE' | 'EQ' | 'NE' | 'BETWEEN' | 'DELTA_PCT'
export type EffectivePeriod = 'ALL_DAY' | 'WORK' | 'CRON'
export type RoleCode = 'ADMIN' | 'OPERATOR' | 'READONLY'

// ---- 认证 ----
export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
  rememberMe?: boolean
}

export interface UserInfo {
  id: number
  username: string
  displayName: string
  roles: RoleCode[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken?: string | null
  user: UserInfo
}

// ---- 监控对象 ----
export interface MonitorRequest {
  id?: number
  name: string
  appType: AppType
  host: string
  port?: number
  intervalSec?: number
  remark?: string
  tags?: string[]
  credential: Record<string, string>
  extra?: Record<string, unknown>
}

export interface MonitorView {
  id: number
  name: string
  appType: AppType
  protocol: ProtocolType
  host: string
  port?: number
  intervalSec: number
  status: number // 0停用 1正常 2不可达
  statusLabel: string
  connFailCount: number
  lastCollectAt?: number | null
  remark?: string
  tags: string[]
  credentialMasked: Record<string, string>
  /** 扩展配置 (HTTP 型: method/path/headers/body/expectStatus) */
  extra?: Record<string, unknown>
  createTime?: number
}

/** 实时值快照 */
export interface Latest {
  group: string
  field: string
  doubleValue?: number | null
  strValue?: string | null
  unit?: string | null
  timestamp: number
  success: boolean
  labels?: Record<string, string> | null
}

export interface DashboardSummary {
  monitorTotal: number
  monitorByType: Record<string, number>
  monitorUnreachable: number
  activeAlertTotal: number
  activeAlertBySeverity: Record<string, number>
}

/** 历史趋势点 (TSDB 降采样查询结果) */
export interface HistoryPoint {
  ts: number
  value?: number | null
  min?: number | null
  max?: number | null
  avg?: number | null
}

// ---- 告警 ----
export interface Alert {
  id: number
  alertNo: string
  monitorId: number
  metricFieldId?: number | null
  ruleId?: number | null
  metricGroup: string
  fieldName: string
  state: AlertState
  severity: Severity
  currentValue: string
  thresholdDesc: string
  hitCount: number
  firstTriggerAt?: string | null
  lastTriggerAt?: string | null
  ackBy?: number | null
  ackAt?: string | null
  recoveredAt?: string | null
  closedAt?: string | null
  notifyGroupId?: number | null
  silencedUntil?: string | null
  fingerprint: string
}

// ---- 阈值规则 ----
export interface ThresholdRule {
  id?: number
  scope: RuleScope
  scopeRef?: string | null
  monitorId?: number | null
  metricFieldId?: number | null
  metricGroup: string
  fieldName: string
  operator: CompareOp
  thresholdJson: string
  consecutiveN: number
  windowSec: number
  severity: Severity
  effectivePeriod: EffectivePeriod
  cronExpr?: string | null
  suppressSec: number
  recoveryNotify: boolean
  notifyPolicyId?: number | null
  enabled: boolean
  priority: number
}

// ---- 通知 ----
export interface NotifyGroup {
  id?: number
  name: string
  membersJson: string
  description?: string
  enabled: boolean
}

export interface NotifyPolicy {
  id?: number
  name: string
  matchTagsJson?: string | null
  matchSeverity?: string | null
  notifyGroupId: number
  enabled: boolean
  priority: number
}

export interface NotifyResult {
  success: boolean
  messageId?: string | null
  error?: string | null
}

// ---- 设置 ----
export interface SmtpConfig {
  id?: number
  host: string
  port: number
  starttls: boolean
  sender: string
  authCipher?: string
  enabled?: boolean
}

export interface CollectorNode {
  id: number
  identity: string
  mode: string
  host?: string
  port?: number
  status: number
  instanceCnt: number
  lastHeartbeat?: string | null
}

export interface UserItem {
  id: number
  username: string
  displayName: string
  email: string
  enabled: boolean
  roles: RoleCode[]
}

// ---- 审计 ----
export interface AuditLog {
  id: number
  userId?: number | null
  username: string
  action: string
  targetType?: string | null
  targetId?: string | null
  detailJson?: string | null
  ip?: string | null
  userAgent?: string | null
  success: boolean
  createTime: string
}

// ---- WebSocket 实时推送 ----
export interface RealtimeMessage {
  type: string
  data: unknown
  ts: number
}
