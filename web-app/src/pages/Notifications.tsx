import { useState } from 'react'
import {
  Table, Button, Space, Typography, Modal, Form, Input, Switch, message,
  Popconfirm, Tabs, Select, Tag, InputNumber,
} from 'antd'
import { PlusOutlined, ReloadOutlined, MailOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api'
import { SEVERITY_META } from '@/components/severity'
import type { NotifyGroup, NotifyPolicy } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title } = Typography

/**
 * 通知管理 (PRD 06: /notifications 收件人组与通知策略, 仅管理员)。
 * 含发送测试邮件 (PRD 05 可观测性)。
 */
export default function Notifications() {
  const queryClient = useQueryClient()
  const [groupModal, setGroupModal] = useState(false)
  const [policyModal, setPolicyModal] = useState(false)
  const [testEmail, setTestEmail] = useState('')
  const [editingGroup, setEditingGroup] = useState<NotifyGroup | null>(null)
  const [editingPolicy, setEditingPolicy] = useState<NotifyPolicy | null>(null)
  const [groupForm] = Form.useForm()
  const [policyForm] = Form.useForm()

  const { data: groups, isLoading: gLoading, refetch: refetchGroups } = useQuery({
    queryKey: ['notify-groups'], queryFn: notificationApi.groups,
  })
  const { data: policies, isLoading: pLoading, refetch: refetchPolicies } = useQuery({
    queryKey: ['notify-policies'], queryFn: notificationApi.policies,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['notify-groups'] })
    queryClient.invalidateQueries({ queryKey: ['notify-policies'] })
  }

  const saveGroupMut = useMutation({
    mutationFn: (g: NotifyGroup) => notificationApi.saveGroup(g),
    onSuccess: () => { message.success('收件人组已保存'); setGroupModal(false); setEditingGroup(null); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const removeGroupMut = useMutation({
    mutationFn: (id: number) => notificationApi.removeGroup(id),
    onSuccess: () => { message.success('已删除'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const savePolicyMut = useMutation({
    mutationFn: (p: NotifyPolicy) => notificationApi.savePolicy(p),
    onSuccess: () => { message.success('通知策略已保存'); setPolicyModal(false); setEditingPolicy(null); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const removePolicyMut = useMutation({
    mutationFn: (id: number) => notificationApi.removePolicy(id),
    onSuccess: () => { message.success('已删除'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const testMut = useMutation({
    mutationFn: (email: string) => notificationApi.test(email),
    onSuccess: (r) => r.success ? message.success('测试邮件已发送') : message.error(`发送失败: ${r.error}`),
    onError: (e: Error) => message.error(e.message),
  })

  const groupColumns: ColumnsType<NotifyGroup> = [
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '成员', dataIndex: 'membersJson', render: (m: string) => {
      try {
        const arr = JSON.parse(m) as { name?: string; email?: string }[]
        return <Space wrap size={4}>{arr.map((x, i) => <Tag key={i}>{x.name ?? ''} {x.email ?? ''}</Tag>)}</Space>
      } catch { return <span>{m}</span> }
    } },
    { title: '描述', dataIndex: 'description', width: 200 },
    { title: '启用', dataIndex: 'enabled', width: 80, render: (e: boolean) => <Tag color={e ? 'success' : 'default'}>{e ? '是' : '否'}</Tag> },
    { title: '操作', width: 140, render: (_, r) => (
      <Space size="small">
        <Button size="small" onClick={() => { setEditingGroup(r); groupForm.setFieldsValue(r); setGroupModal(true) }}>编辑</Button>
        <Popconfirm title="确认删除?" onConfirm={() => r.id && removeGroupMut.mutate(r.id)}>
          <Button size="small" danger>删除</Button>
        </Popconfirm>
      </Space>
    ) },
  ]

  const policyColumns: ColumnsType<NotifyPolicy> = [
    { title: '策略名', dataIndex: 'name', width: 160 },
    { title: '匹配标签', dataIndex: 'matchTagsJson', width: 180, render: (t: string) => t || <span style={{ color: '#86909c' }}>全部</span> },
    { title: '匹配级别', dataIndex: 'matchSeverity', width: 180, render: (s: string) =>
      s ? <Space size={4}>{s.split(',').map((x) => <Tag key={x} color={SEVERITY_META[x.trim() as keyof typeof SEVERITY_META]?.color}>{x}</Tag>)}</Space>
        : <span style={{ color: '#86909c' }}>全部</span> },
    { title: '收件人组', dataIndex: 'notifyGroupId', width: 140, render: (id: number) =>
      groups?.find((g) => g.id === id)?.name ?? id },
    { title: '优先级', dataIndex: 'priority', width: 80 },
    { title: '启用', dataIndex: 'enabled', width: 80, render: (e: boolean) => <Tag color={e ? 'success' : 'default'}>{e ? '是' : '否'}</Tag> },
    { title: '操作', width: 140, render: (_, r) => (
      <Space size="small">
        <Button size="small" onClick={() => { setEditingPolicy(r); policyForm.setFieldsValue({ ...r, matchSeverity: r.matchSeverity?.split(',') ?? [] }); setPolicyModal(true) }}>编辑</Button>
        <Popconfirm title="确认删除?" onConfirm={() => r.id && removePolicyMut.mutate(r.id)}>
          <Button size="small" danger>删除</Button>
        </Popconfirm>
      </Space>
    ) },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>通知管理</Title>
        <Space>
          <Input placeholder="测试邮箱" value={testEmail} onChange={(e) => setTestEmail(e.target.value)} style={{ width: 200 }} />
          <Button icon={<MailOutlined />} loading={testMut.isPending} onClick={() => testEmail && testMut.mutate(testEmail)}>发送测试邮件</Button>
        </Space>
      </div>

      <Tabs
        items={[
          {
            key: 'groups', label: '收件人组',
            children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Space>
                  <Button icon={<ReloadOutlined />} onClick={() => refetchGroups()}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingGroup(null); groupForm.resetFields(); groupForm.setFieldsValue({ enabled: true, membersJson: '[{"name":"","email":""}]' }); setGroupModal(true) }}>新增收件人组</Button>
                </Space>
                <Table rowKey="id" columns={groupColumns} dataSource={groups ?? []} loading={gLoading} pagination={false} />
              </Space>
            ),
          },
          {
            key: 'policies', label: '通知策略',
            children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Space>
                  <Button icon={<ReloadOutlined />} onClick={() => refetchPolicies()}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingPolicy(null); policyForm.resetFields(); policyForm.setFieldsValue({ enabled: true, priority: 0, matchSeverity: [] }); setPolicyModal(true) }}>新增通知策略</Button>
                </Space>
                <Table rowKey="id" columns={policyColumns} dataSource={policies ?? []} loading={pLoading} pagination={false} />
              </Space>
            ),
          },
        ]}
      />

      {/* 收件人组弹窗 */}
      <Modal title={editingGroup ? '编辑收件人组' : '新增收件人组'} open={groupModal} destroyOnClose
        onCancel={() => { setGroupModal(false); setEditingGroup(null) }}
        onOk={() => groupForm.submit()} confirmLoading={saveGroupMut.isPending}>
        <Form form={groupForm} layout="vertical" onFinish={(v) => saveGroupMut.mutate({ ...v, id: editingGroup?.id })} requiredMark={false}>
          <Form.Item name="name" label="组名" rules={[{ required: true, message: '请输入组名' }]}>
            <Input placeholder="如 数据库值班" />
          </Form.Item>
          <Form.Item name="membersJson" label="成员 (JSON)" rules={[{ required: true }]}
            tooltip='格式: [{"name":"张三","email":"z@example.com"}]'>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="description" label="描述"><Input /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>

      {/* 通知策略弹窗 */}
      <Modal title={editingPolicy ? '编辑通知策略' : '新增通知策略'} open={policyModal} destroyOnClose
        onCancel={() => { setPolicyModal(false); setEditingPolicy(null) }}
        onOk={() => policyForm.submit()} confirmLoading={savePolicyMut.isPending}>
        <Form form={policyForm} layout="vertical" onFinish={(v) => savePolicyMut.mutate({
          ...v, id: editingPolicy?.id,
          matchSeverity: Array.isArray(v.matchSeverity) ? v.matchSeverity.join(',') : (v.matchSeverity || null),
          matchTagsJson: v.matchTagsJson || null,
        })} requiredMark={false}>
          <Form.Item name="name" label="策略名" rules={[{ required: true, message: '请输入策略名' }]}>
            <Input placeholder="如 生产库严重告警→DBA" />
          </Form.Item>
          <Form.Item name="matchSeverity" label="匹配级别 (空=全部)">
            <Select mode="multiple" allowClear options={Object.entries(SEVERITY_META).map(([value, m]) => ({ value, label: m.label }))} />
          </Form.Item>
          <Form.Item name="matchTagsJson" label="匹配标签 (JSON, 空=全部)" tooltip='如 {"env":"prod"}'>
            <Input placeholder='{"env":"prod"}' />
          </Form.Item>
          <Form.Item name="notifyGroupId" label="收件人组" rules={[{ required: true, message: '请选择收件人组' }]}>
            <Select options={(groups ?? []).map((g) => ({ value: g.id, label: g.name }))} />
          </Form.Item>
          <Form.Item name="priority" label="优先级"><InputNumber style={{ width: '100%' }} min={0} /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
