import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserInfo, RoleCode } from '@/types'
import { tokenStore } from '@/api/client'

/**
 * 认证状态 (Zustand + persist)。
 * 令牌存 localStorage (与 axios client 共享 key); 用户信息/角色存 store。
 * RBAC: hasRole / hasAnyRole 供路由守卫与按钮级权限。
 */
interface AuthState {
  user: UserInfo | null
  roles: RoleCode[]
  isAuthenticated: boolean
  setAuth: (user: UserInfo, accessToken: string, refreshToken?: string | null) => void
  logout: () => void
  hasRole: (role: RoleCode) => boolean
  hasAnyRole: (roles: RoleCode[]) => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      roles: [],
      isAuthenticated: false,

      setAuth: (user, accessToken, refreshToken) => {
        tokenStore.set(accessToken)
        if (refreshToken) tokenStore.setRefresh(refreshToken)
        set({ user, roles: user.roles, isAuthenticated: true })
      },

      logout: () => {
        tokenStore.clear()
        set({ user: null, roles: [], isAuthenticated: false })
      },

      hasRole: (role) => get().roles.includes(role),

      hasAnyRole: (roles) => roles.some((r) => get().roles.includes(r)),
    }),
    {
      name: 'wp_auth',
      // 令牌由 tokenStore 单独管理 (axios 拦截器读取), persist 仅存用户/角色
      partialize: (state) => ({
        user: state.user,
        roles: state.roles,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
)

/** 权限常量 (PRD 06 路由权限矩阵) */
export const PERM = {
  // 写操作: 管理员·运维
  MONITOR_WRITE: ['ADMIN', 'OPERATOR'] as RoleCode[],
  RULE_WRITE: ['ADMIN', 'OPERATOR'] as RoleCode[],
  // 仅管理员
  ADMIN_ONLY: ['ADMIN'] as RoleCode[],
  // 全部登录角色
  ALL: ['ADMIN', 'OPERATOR', 'READONLY'] as RoleCode[],
}
