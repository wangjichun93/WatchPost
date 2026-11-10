import { get, post, put, del } from './client'
import type {
  LoginRequest, LoginResponse, UserInfo,
  MonitorRequest, MonitorView, PageResult, Latest, DashboardSummary, HistoryPoint,
  Alert, AlertState, Severity, ThresholdRule,
  NotifyGroup, NotifyPolicy, NotifyResult,
  SmtpConfig, CollectorNode, UserItem, AuditLog,
} from '@/types'

// ==================== 认证 ====================
export const authApi = {
  login: (req: LoginRequest) => post<LoginResponse>('/auth/login', req),
  captcha: () => get<{ key: string; code: string }>('/auth/captcha'),
  refresh: (refreshToken: string) => post<LoginResponse>('/auth/refresh', { refreshToken }),
  logout: () => post<void>('/auth/logout'),
  me: () => get<UserInfo>('/auth/me'),
}

// ==================== 监控中心 ====================
export interface MonitorQuery {
  appType?: string
  status?: number
  keyword?: string
  page?: number
  size?: number
}
export const monitorApi = {
  list: (q: MonitorQuery) => get<PageResult<MonitorView>>('/monitors', q as Record<string, unknown>),
  get: (id: number) => get<MonitorView>(`/monitors/${id}`),
  create: (req: MonitorRequest) => post<MonitorView>('/monitors', req),
  update: (id: number, req: MonitorRequest) => put<MonitorView>(`/monitors/${id}`, req),
  remove: (id: number) => del<void>(`/monitors/${id}`),
  toggle: (id: number, enabled: boolean) => put<MonitorView>(`/monitors/${id}/toggle`, undefined, { enabled }),
  collectNow: (id: number) => post<void>(`/monitors/${id}/collect`),
  realtime: (id: number) => get<Record<string, Latest>>(`/monitors/${id}/realtime`),
  history: (id: number, group: string, field: string, from: number, to: number, step: number) =>
    get<HistoryPoint[]>(`/monitors/${id}/history`, { group, field, from, to, step }),
}

// ==================== 总览大盘 ====================
export const dashboardApi = {
  summary: () => get<DashboardSummary>('/dashboard/summary'),
}

// ==================== 告警中心 ====================
export interface AlertQuery {
  state?: AlertState
  monitorId?: number
  severity?: Severity
  page?: number
  size?: number
}
export const alertApi = {
  list: (q: AlertQuery) => get<PageResult<Alert>>('/alerts', q as Record<string, unknown>),
  active: () => get<Alert[]>('/alerts/active'),
  stats: () => get<Record<string, number>>('/alerts/stats'),
  get: (alertNo: string) => get<Alert>(`/alerts/${alertNo}`),
  ack: (alertNo: string) => post<Alert>(`/alerts/${alertNo}/ack`),
  silence: (alertNo: string, seconds = 3600) =>
    post<Alert>(`/alerts/${alertNo}/silence`, undefined, { seconds }),
  close: (alertNo: string) => post<void>(`/alerts/${alertNo}/close`),
}

// ==================== 阈值规则 ====================
export const ruleApi = {
  list: (scope?: string, scopeRef?: string) =>
    get<ThresholdRule[]>('/alert-rules', { scope, scopeRef }),
  effective: (monitorId: number, appType: string, group?: string, field?: string) =>
    get<ThresholdRule[]>('/alert-rules/effective', { monitorId, appType, group, field }),
  get: (id: number) => get<ThresholdRule>(`/alert-rules/${id}`),
  save: (rule: ThresholdRule) => post<ThresholdRule>('/alert-rules', rule),
  toggle: (id: number, enabled: boolean) =>
    put<ThresholdRule>(`/alert-rules/${id}/toggle`, undefined, { enabled }),
  remove: (id: number) => del<void>(`/alert-rules/${id}`),
}

// ==================== 通知管理 ====================
export const notificationApi = {
  groups: () => get<NotifyGroup[]>('/notifications/groups'),
  saveGroup: (g: NotifyGroup) => post<NotifyGroup>('/notifications/groups', g),
  removeGroup: (id: number) => del<void>(`/notifications/groups/${id}`),
  policies: () => get<NotifyPolicy[]>('/notifications/policies'),
  savePolicy: (p: NotifyPolicy) => post<NotifyPolicy>('/notifications/policies', p),
  removePolicy: (id: number) => del<void>(`/notifications/policies/${id}`),
  test: (email: string) => post<NotifyResult>('/notifications/test', { email }),
}

// ==================== 系统设置 ====================
export const settingsApi = {
  getSmtp: () => get<SmtpConfig>('/settings/smtp'),
  saveSmtp: (cfg: Record<string, unknown>) => post<void>('/settings/smtp', cfg),
  collectors: () => get<CollectorNode[]>('/settings/collectors'),
  users: () => get<UserItem[]>('/settings/users'),
  toggleUser: (id: number, enabled: boolean) =>
    put<void>(`/settings/users/${id}/toggle`, undefined, { enabled }),
  resetPassword: (id: number, password: string) =>
    put<void>(`/settings/users/${id}/reset-password`, { password }),
}

// ==================== 审计日志 ====================
export const auditApi = {
  list: (q: { action?: string; userId?: number; page?: number; size?: number }) =>
    get<PageResult<AuditLog>>('/audit', q as Record<string, unknown>),
}
