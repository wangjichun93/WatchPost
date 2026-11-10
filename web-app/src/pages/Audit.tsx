import { useState } from 'react'
import { Table, Tag, Space, Button, Select, Input, Card, Typography } from 'antd'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { auditApi } from '@/api'
import type { AuditLog } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title, Text } = Typography

const ACTION_OPTIONS = [
  'LOGIN', 'LOGOUT', 'MONITOR_CREATE', 'MONITOR_UPDATE', 'MONITOR_DELETE', 'MONITOR_TOGGLE',
  'RULE_SAVE', 'RULE_TOGGLE', 'RULE_DELETE', 'ALERT_ACK', 'ALERT_SILENCE', 'ALERT_CLOSE',
  'NOTIFY_GROUP_SAVE', 'NOTIFY_POLICY_SAVE', 'NOTIFY_TEST', 'SMTP_SAVE', 'USER_TOGGLE', 'USER_RESET_PWD',
].map((a) => ({ value: a, label: a }))

/**
 * 审计日志 (PRD 06: /audit 登录与配置变更审计日志, 仅管理员)。
 */
export default function Audit() {
  const [query, setQuery] = useState<{ action?: string; page: number; size: number }>({ page: 1, size: 20 })

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['audit', query],
    queryFn: () => auditApi.list(query),
  })

  const columns: ColumnsType<AuditLog> = [
    { title: '时间', dataIndex: 'createTime', width: 180, render: (t: string) => new Date(t).toLocaleString() },
    { title: '用户', dataIndex: 'username', width: 140 },
    { title: '动作', dataIndex: 'action', width: 180, render: (a: string) => <Tag color="blue">{a}</Tag> },
    { title: '目标类型', dataIndex: 'targetType', width: 130 },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '结果', dataIndex: 'success', width: 90, render: (s: boolean) => <Tag color={s ? 'success' : 'error'}>{s ? '成功' : '失败'}</Tag> },
    { title: '详情', dataIndex: 'detailJson', render: (d: string) => <Text type="secondary" style={{ fontSize: 12 }} ellipsis>{d ?? '—'}</Text> },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>审计日志</Title>
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>
      </div>

      <Card size="small">
        <Space wrap>
          <Select placeholder="动作类型" allowClear style={{ width: 220 }} options={ACTION_OPTIONS}
            onChange={(v) => setQuery({ ...query, action: v, page: 1 })} />
          <Input placeholder="按动作过滤" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
            onPressEnter={(e) => setQuery({ ...query, action: (e.target as HTMLInputElement).value || undefined, page: 1 })} />
        </Space>
      </Card>

      <Table rowKey="id" columns={columns} dataSource={data?.items ?? []} loading={isLoading}
        scroll={{ x: 1100 }}
        pagination={{
          current: query.page, pageSize: query.size, total: data?.total ?? 0,
          showSizeChanger: true, showTotal: (t) => `共 ${t} 条审计记录`,
          onChange: (page, size) => setQuery({ ...query, page, size }),
        }} />
    </Space>
  )
}
