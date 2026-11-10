import { useState } from 'react'
import {
  Card, Form, Input, InputNumber, Switch, Button, Space, Typography, Tabs, Table,
  Tag, message, Popconfirm, Modal,
} from 'antd'
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { settingsApi } from '@/api'
import type { SmtpConfig, CollectorNode, UserItem } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title, Text } = Typography

/**
 * 系统设置 (PRD 06: /settings SMTP / 凭据 / 采集器 / 用户与角色, 仅管理员)。
 * SMTP 授权码加密存储 (提交明文, 服务端 AES-256-GCM 加密; 回显恒为 ***)。
 */
export default function Settings() {
  const queryClient = useQueryClient()
  const [smtpForm] = Form.useForm()
  const [pwdTarget, setPwdTarget] = useState<UserItem | null>(null)
  const [newPwd, setNewPwd] = useState('')

  const { data: smtp } = useQuery({ queryKey: ['smtp'], queryFn: settingsApi.getSmtp })
  const { data: collectors, refetch: refetchCollectors } = useQuery({ queryKey: ['collectors'], queryFn: settingsApi.collectors })
  const { data: users, refetch: refetchUsers } = useQuery({ queryKey: ['users'], queryFn: settingsApi.users })

  const saveSmtpMut = useMutation({
    mutationFn: (cfg: Record<string, unknown>) => settingsApi.saveSmtp(cfg),
    onSuccess: () => { message.success('SMTP 配置已保存 (授权码已加密)'); queryClient.invalidateQueries({ queryKey: ['smtp'] }) },
    onError: (e: Error) => message.error(e.message),
  })
  const toggleUserMut = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => settingsApi.toggleUser(id, enabled),
    onSuccess: () => { message.success('已更新'); refetchUsers() },
    onError: (e: Error) => message.error(e.message),
  })
  const resetPwdMut = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) => settingsApi.resetPassword(id, password),
    onSuccess: () => { message.success('口令已重置'); setPwdTarget(null); setNewPwd('') },
    onError: (e: Error) => message.error(e.message),
  })

  const collectorColumns: ColumnsType<CollectorNode> = [
    { title: '标识', dataIndex: 'identity' },
    { title: '模式', dataIndex: 'mode', width: 100, render: (m: string) => <Tag>{m}</Tag> },
    { title: '地址', width: 180, render: (_, r) => `${r.host ?? '-'}:${r.port ?? '-'}` },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: number) => <Tag color={s === 1 ? 'success' : 'default'}>{s === 1 ? '在线' : '离线'}</Tag> },
    { title: '承载实例', dataIndex: 'instanceCnt', width: 100 },
    { title: '最近心跳', dataIndex: 'lastHeartbeat', width: 180, render: (t: string) => t ? new Date(t).toLocaleString() : '—' },
  ]

  const userColumns: ColumnsType<UserItem> = [
    { title: '用户名', dataIndex: 'username', width: 160 },
    { title: '显示名', dataIndex: 'displayName', width: 160 },
    { title: '邮箱', dataIndex: 'email' },
    { title: '角色', dataIndex: 'roles', width: 200, render: (roles: string[]) => <Space size={4}>{roles.map((r) => <Tag key={r} color="blue">{r}</Tag>)}</Space> },
    { title: '启用', width: 90, render: (_, r) => (
      <Switch size="small" checked={r.enabled} onChange={(c) => toggleUserMut.mutate({ id: r.id, enabled: c })} />
    ) },
    { title: '操作', width: 120, render: (_, r) => (
      <Popconfirm title="确认重置该用户口令?" disabled onConfirm={() => {}}>
        <Button size="small" onClick={() => { setPwdTarget(r); setNewPwd('') }}>重置口令</Button>
      </Popconfirm>
    ) },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>系统设置</Title>

      <Tabs items={[
        {
          key: 'smtp', label: 'SMTP 邮件',
          children: (
            <Card style={{ maxWidth: 560 }}>
              <Form form={smtpForm} layout="vertical" initialValues={smtp as SmtpConfig}
                onFinish={(v) => saveSmtpMut.mutate(v)} requiredMark={false}>
                <Form.Item name="host" label="SMTP 主机" rules={[{ required: true, message: '请输入 SMTP 主机' }]}>
                  <Input placeholder="smtp.example.com" />
                </Form.Item>
                <Space.Compact style={{ width: '100%' }}>
                  <Form.Item name="port" label="端口" style={{ width: '50%' }} rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={1} max={65535} />
                  </Form.Item>
                  <Form.Item name="starttls" label="STARTTLS" style={{ width: '50%' }} valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space.Compact>
                <Form.Item name="sender" label="发件人" rules={[{ required: true, message: '请输入发件人' }]}>
                  <Input placeholder="watchpost@example.com" />
                </Form.Item>
                <Form.Item name="authCode" label="授权码" tooltip="提交后 AES-256-GCM 加密存储; 留空或 *** 表示不修改">
                  <Input.Password placeholder="留空则不修改" autoComplete="new-password" />
                </Form.Item>
                <Form.Item>
                  <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saveSmtpMut.isPending}>保存 SMTP 配置</Button>
                </Form.Item>
              </Form>
            </Card>
          ),
        },
        {
          key: 'collectors', label: '采集器',
          children: (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => refetchCollectors()}>刷新</Button>
                <Text type="secondary" style={{ fontSize: 12 }}>v1 单机一条本地采集器; 集群期水平扩展</Text>
              </Space>
              <Table rowKey="id" columns={collectorColumns} dataSource={collectors ?? []} pagination={false} />
            </Space>
          ),
        },
        {
          key: 'users', label: '用户与角色',
          children: (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => refetchUsers()}>刷新</Button>
                <Text type="secondary" style={{ fontSize: 12 }}>RBAC 三角色: ADMIN 管理员 / OPERATOR 运维 / READONLY 只读</Text>
              </Space>
              <Table rowKey="id" columns={userColumns} dataSource={users ?? []} pagination={false} />
            </Space>
          ),
        },
      ]} />

      <Modal title={`重置口令: ${pwdTarget?.username ?? ''}`} open={!!pwdTarget}
        onCancel={() => { setPwdTarget(null); setNewPwd('') }}
        onOk={() => pwdTarget && newPwd && resetPwdMut.mutate({ id: pwdTarget.id, password: newPwd })}
        confirmLoading={resetPwdMut.isPending} okText="重置" okButtonProps={{ disabled: !newPwd }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>新口令 (bcrypt 哈希存储):</Text>
          <Input.Password value={newPwd} onChange={(e) => setNewPwd(e.target.value)} placeholder="请输入新口令" autoComplete="new-password" />
        </Space>
      </Modal>
    </Space>
  )
}
