import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { RequireAuth, RequireRole } from './guards'
import ConsoleLayout from '@/layouts/ConsoleLayout'
import { PERM } from '@/store/auth'

// 路由懒加载 (代码分割, 首屏更快)
const Login = lazy(() => import('@/pages/Login'))
const Dashboard = lazy(() => import('@/pages/Dashboard'))
const Monitors = lazy(() => import('@/pages/Monitors'))
const MonitorDetail = lazy(() => import('@/pages/MonitorDetail'))
const AlertRules = lazy(() => import('@/pages/AlertRules'))
const Alerts = lazy(() => import('@/pages/Alerts'))
const Notifications = lazy(() => import('@/pages/Notifications'))
const Settings = lazy(() => import('@/pages/Settings'))
const Audit = lazy(() => import('@/pages/Audit'))
const Forbidden = lazy(() => import('@/pages/Forbidden'))

function withSuspense(el: React.ReactElement) {
  return (
    <Suspense fallback={<div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>}>
      {el}
    </Suspense>
  )
}

/**
 * 路由表 (严格对应 PRD 第06章页面清单与权限):
 *   /login 公开; /dashboard /monitors/:id /alerts 全部角色;
 *   /monitors /alert-rules 写操作 管理员·运维; /notifications /settings /audit 仅管理员。
 * /login 独立路由独立视觉 (无业务导航); 登录后进入 ConsoleLayout (左侧导航 + 内容区)。
 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: withSuspense(<Login />),
  },
  {
    path: '/403',
    element: withSuspense(<Forbidden />),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <ConsoleLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: withSuspense(<Dashboard />) },
      { path: 'monitors', element: withSuspense(<Monitors />) },
      { path: 'monitors/:id', element: withSuspense(<MonitorDetail />) },
      {
        path: 'alert-rules',
        element: (
          <RequireRole roles={PERM.ALL}>
            {withSuspense(<AlertRules />)}
          </RequireRole>
        ),
      },
      { path: 'alerts', element: withSuspense(<Alerts />) },
      {
        path: 'notifications',
        element: (
          <RequireRole roles={PERM.ADMIN_ONLY}>
            {withSuspense(<Notifications />)}
          </RequireRole>
        ),
      },
      {
        path: 'settings',
        element: (
          <RequireRole roles={PERM.ADMIN_ONLY}>
            {withSuspense(<Settings />)}
          </RequireRole>
        ),
      },
      {
        path: 'audit',
        element: (
          <RequireRole roles={PERM.ADMIN_ONLY}>
            {withSuspense(<Audit />)}
          </RequireRole>
        ),
      },
    ],
  },
  { path: '*', element: <Navigate to="/dashboard" replace /> },
])
