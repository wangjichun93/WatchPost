import { Navigate, useLocation } from 'react-router-dom'
import type { ReactElement } from 'react'
import { useAuthStore } from '@/store/auth'
import type { RoleCode } from '@/types'

/**
 * 路由守卫: 未登录重定向 /login (保留回跳); 角色不足显示 403。
 * 对应 PRD 06/08: /login 独立路由, 未登录访问任意业务路由均重定向至此。
 */
export function RequireAuth({ children }: { children: ReactElement }) {
  const location = useLocation()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />
  }
  return children
}

export function RequireRole({ roles, children }: { roles: RoleCode[]; children: ReactElement }) {
  const hasAnyRole = useAuthStore((s) => s.hasAnyRole)
  if (!hasAnyRole(roles)) {
    return <Navigate to="/403" replace />
  }
  return children
}
