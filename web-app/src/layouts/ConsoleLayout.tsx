import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space, theme, Badge, Typography } from 'antd'
import {
  DashboardOutlined, CloudServerOutlined, AlertOutlined, BellOutlined,
  SettingOutlined, AuditOutlined, UserOutlined, LogoutOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, SafetyOutlined,
} from '@ant-design/icons'
import { useAuthStore, PERM } from '@/store/auth'
import { authApi } from '@/api'
import type { MenuProps } from 'antd'

const { Header, Sider, Content } = Layout
const { Text } = Typography

/**
 * 控制台框架 (PRD 06: 登录后进入左侧导航 + 内容区)。
 * 导航项按 RBAC 过滤: 通知/设置/审计仅管理员可见。
 */
export default function ConsoleLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, roles, hasAnyRole, logout } = useAuthStore()
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken()

  const isAdmin = hasAnyRole(PERM.ADMIN_ONLY)

  const menuItems: MenuProps['items'] = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: '总览大盘' },
    { key: '/monitors', icon: <CloudServerOutlined />, label: '监控中心' },
    { key: '/alert-rules', icon: <SafetyOutlined />, label: '阈值规则' },
    { key: '/alerts', icon: <AlertOutlined />, label: '告警中心' },
    ...(isAdmin
      ? ([
          { key: '/notifications', icon: <BellOutlined />, label: '通知管理' },
          { key: '/settings', icon: <SettingOutlined />, label: '系统设置' },
          { key: '/audit', icon: <AuditOutlined />, label: '审计日志' },
        ] as NonNullable<MenuProps['items']>
        )
      : []),
  ]

  // 选中态: 详情页 /monitors/:id 高亮 /monitors
  const selectedKey = menuItems
    ?.map((m) => (m as { key: string }).key)
    .filter((k) => location.pathname.startsWith(k))
    .sort((a, b) => b.length - a.length)[0] || '/dashboard'

  const userMenu: MenuProps['items'] = [
    {
      key: 'roles',
      label: <Text type="secondary">{roles.join(' / ')}</Text>,
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: async () => {
        try { await authApi.logout() } catch { /* 忽略 */ }
        logout()
        navigate('/login', { replace: true })
      },
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} theme="dark" width={220}>
        <div style={{
          height: 56, display: 'flex', alignItems: 'center', justifyContent: 'center',
          gap: 8, color: '#fff', borderBottom: '1px solid rgba(255,255,255,0.08)',
        }}>
          <img src="/favicon.svg" alt="logo" width={26} height={26} />
          {!collapsed && <span style={{ fontSize: 16, fontWeight: 600, letterSpacing: 1 }}>望哨 WATCHPOST</span>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 24px', background: colorBgContainer,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          boxShadow: '0 1px 4px rgba(0,21,41,0.08)',
        }}>
          <Space>
            {collapsed
              ? <MenuUnfoldOutlined onClick={() => setCollapsed(false)} style={{ fontSize: 18, cursor: 'pointer' }} />
              : <MenuFoldOutlined onClick={() => setCollapsed(true)} style={{ fontSize: 18, cursor: 'pointer' }} />}
          </Space>
          <Space size="large">
            <Badge count={0} showZero={false}>
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer' }} onClick={() => navigate('/alerts')} />
            </Badge>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} style={{ background: '#165dff' }} />
                <Text>{user?.displayName || user?.username}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16, padding: 24, background: colorBgContainer, borderRadius: borderRadiusLG, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
