import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Form, Input, Button, Checkbox, Card, Typography, Space, message, Alert } from 'antd'
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import type { LoginRequest } from '@/types'

const { Title, Text, Paragraph } = Typography

/**
 * 独立登录页 (PRD 08: /login 为独立路由与独立视觉, 无业务导航)。
 * 未登录访问任意业务路由均重定向至此; 连续5次失败锁定15分钟; 失败≥2次要求验证码。
 */
export default function Login() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const redirect = params.get('redirect') || '/dashboard'
  const setAuth = useAuthStore((s) => s.setAuth)
  const queryClient = useQueryClient()

  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [captcha, setCaptcha] = useState<{ key: string; code: string } | null>(null)
  const [needCaptcha, setNeedCaptcha] = useState(false)
  const [form] = Form.useForm<LoginRequest & { captcha?: string }>()

  const loadCaptcha = async () => {
    try {
      const c = await authApi.captcha()
      setCaptcha(c)
    } catch {
      /* 忽略 */
    }
  }

  useEffect(() => {
    if (needCaptcha) loadCaptcha()
  }, [needCaptcha])

  const onFinish = async (values: LoginRequest & { captcha?: string }) => {
    setLoading(true)
    setErrorMsg('')
    try {
      const resp = await authApi.login({
        username: values.username,
        password: values.password,
        rememberMe: values.rememberMe,
        captcha: values.captcha,
        captchaKey: captcha?.key,
      })
      setAuth(resp.user, resp.accessToken, resp.refreshToken)
      queryClient.clear()
      message.success(`欢迎回来, ${resp.user.displayName || resp.user.username}`)
      navigate(redirect, { replace: true })
    } catch (e) {
      const err = e as { code?: number; message?: string }
      setErrorMsg(err.message || '登录失败')
      // 登录失败后可能需要验证码 (后端失败≥2次要求); 骨架: 失败即加载验证码
      setNeedCaptcha(true)
      loadCaptcha()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #1d2129 0%, #2b3a55 100%)', padding: 24,
    }}>
      <div style={{ display: 'flex', gap: 64, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'center' }}>
        {/* 左侧品牌区 (PRD 08 附录视觉) */}
        <div style={{ color: '#fff', maxWidth: 420 }}>
          <Space align="center" style={{ marginBottom: 24 }}>
            <img src="/favicon.svg" alt="logo" width={44} height={44} />
            <span style={{ fontSize: 26, fontWeight: 700, letterSpacing: 2 }}>望哨 WATCHPOST</span>
          </Space>
          <Title level={2} style={{ color: '#fff', lineHeight: 1.4 }}>
            让每一台主机、每一个接口、每一个库，都有可配的阈值。
          </Title>
          <Paragraph style={{ color: 'rgba(255,255,255,0.75)', fontSize: 15 }}>
            不装 Agent，不改业务代码。填一次地址与只读凭据，就能开始监控、越限告警与邮件通知。
          </Paragraph>
          <Space direction="vertical" size={6} style={{ color: 'rgba(255,255,255,0.65)', fontSize: 13 }}>
            <Text style={{ color: 'rgba(255,255,255,0.65)' }}>· PULL 直连 SSH / SNMP / HTTP / JDBC，对端零安装</Text>
            <Text style={{ color: 'rgba(255,255,255,0.65)' }}>· 服务器 · API · MySQL · PostgreSQL · Oracle 统一纳管</Text>
            <Text style={{ color: 'rgba(255,255,255,0.65)' }}>· 越限自动收敛并发邮件，恢复后再通知一次</Text>
          </Space>
        </div>

        {/* 右侧登录卡片 */}
        <Card style={{ width: 380, borderRadius: 12, boxShadow: '0 8px 32px rgba(0,0,0,0.3)' }}>
          <Title level={4} style={{ marginBottom: 4 }}>登录控制台</Title>
          <Text type="secondary" style={{ fontSize: 12 }}>请使用运维账号登录；连续 5 次失败将锁定 15 分钟</Text>

          {errorMsg && (
            <Alert
              message={errorMsg}
              type="error"
              showIcon
              style={{ marginTop: 16 }}
              closable
              onClose={() => setErrorMsg('')}
            />
          )}

          <Form form={form} layout="vertical" onFinish={onFinish} style={{ marginTop: 20 }} requiredMark={false}>
            <Form.Item name="username" label="用户名 / 工号" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined />} placeholder="请输入用户名" size="large" autoFocus />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" size="large" />
            </Form.Item>
            {needCaptcha && captcha && (
              <Form.Item name="captcha" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Space.Compact style={{ width: '100%' }}>
                  <Input prefix={<SafetyCertificateOutlined />} placeholder="请输入验证码" size="large" />
                  <Button size="large" onClick={loadCaptcha} style={{ minWidth: 96 }}>
                    {captcha.code}
                  </Button>
                </Space.Compact>
              </Form.Item>
            )}
            <Form.Item>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                  <Checkbox>记住我 · 7 天内免登录</Checkbox>
                </Form.Item>
                <Text type="secondary" style={{ fontSize: 12, cursor: 'pointer' }}>忘记密码?</Text>
              </div>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" size="large" block loading={loading}>
                登 录
              </Button>
            </Form.Item>
          </Form>
          <Text type="secondary" style={{ fontSize: 12, display: 'block', textAlign: 'center' }}>
            首次登录请联系管理员开通账号 · 所有登录行为均记录审计日志
          </Text>
        </Card>
      </div>
    </div>
  )
}
