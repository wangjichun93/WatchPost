import { useState } from 'react'
import {
  Table, Tag, Space, Button, Select, Card, Typography, Modal, InputNumber,
  message, Popconfirm, Tooltip,
} from 'antd'
import { CheckOutlined, StopOutlined, CloseCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { alertApi, type AlertQuery } from '@/api'
import { useRealtime } from '@/hooks/useRealtime'
import { SEVERITY_META, ALERT_STATE_META } from '@/components/severity'
import type { Alert, AlertState, Severity } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title } = Typography

/**
 * 告警中心 (PRD 06: /alerts 确认 / 静默 / 关闭, 全部角色)。
 * 值班视图: 未确认活动告警常驻, 按级别降序。
 */
export default function Alerts() {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState<AlertQuery>({ page: 1, size: 20 })
  const [silenceTarget, setSilenceTarget] = useState<Alert | null>(null)
  const [silenceSec, setSilenceSec] = useState(3600)

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['alerts', query],
    queryFn: () => alertApi.list(query),
    refetchInterval: 15000,
  })

  useRealtime(() => refetch())

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['alerts'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const ackMut = useMutation({
    mutationFn: (no: string) => alertApi.ack(no),
    onSuccess: () => { message.success('已确认'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const closeMut = useMutation({
    mutationFn: (no: string) => alertApi.close(no),
    onSuccess: () => { message.success('已关闭'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const silenceMut = useMutation({
    mutationFn: ({ no, sec }: { no: string; sec: number }) => alertApi.silence(no, sec),
    onSuccess: () => { message.success('已静默'); setSilenceTarget(null); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })

  const columns: ColumnsType<Alert> = [
    { title: '告警编号', dataIndex: 'alertNo', width: 110 },
    {
      title: '级别', dataIndex: 'severity', width: 90,
      render: (s: Severity) => <Tag color={SEVERITY_META[s]?.color}>{SEVERITY_META[s]?.label ?? s}</Tag>,
    },
    {
      title: '状态', dataIndex: 'state', width: 100,
      render: (s: AlertState) => <Tag color={ALERT_STATE_META[s]?.color}>{ALERT_STATE_META[s]?.label ?? s}</Tag>,
    },
    { title: '指标', width: 200, render: (_, r) => `${r.metricGroup}.${r.fieldName}` },
    { title: '当前值 / 阈值', width: 160, render: (_, r) => `${r.currentValue} / ${r.thresholdDesc}` },
    { title: '命中', dataIndex: 'hitCount', width: 70 },
    {
      title: '首次触发', dataIndex: 'firstTriggerAt', width: 170,
      render: (t: string) => (t ? new Date(t).toLocaleString() : '—'),
    },
    {
      title: '操作', width: 220, fixed: 'right',
      render: (_, r) => {
        const isActive = ['PENDING', 'TRIGGERED', 'NOTIFYING', 'ACKNOWLEDGED'].includes(r.state)
        return (
          <Space size="small">
            <Tooltip title="确认">
              <Button size="small" icon={<CheckOutlined />} disabled={!isActive || r.state === 'ACKNOWLEDGED'}
                loading={ackMut.isPending} onClick={() => ackMut.mutate(r.alertNo)} />
            </Tooltip>
            <Tooltip title="静默">
              <Button size="small" icon={<StopOutlined />} disabled={!isActive}
                onClick={() => { setSilenceTarget(r); setSilenceSec(3600) }} />
            </Tooltip>
            <Popconfirm title="确认关闭该告警?" onConfirm={() => closeMut.mutate(r.alertNo)} disabled={!isActive}>
              <Button size="small" danger icon={<CloseCircleOutlined />} disabled={!isActive} />
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>告警中心</Title>
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>
      </div>

      <Card size="small">
        <Space wrap>
          <Select placeholder="状态" allowClear style={{ width: 130 }}
            options={Object.entries(ALERT_STATE_META).map(([value, m]) => ({ value, label: m.label }))}
            onChange={(v) => setQuery({ ...query, state: v, page: 1 })} />
          <Select placeholder="级别" allowClear style={{ width: 120 }}
            options={Object.entries(SEVERITY_META).map(([value, m]) => ({ value, label: m.label }))}
            onChange={(v) => setQuery({ ...query, severity: v as Severity, page: 1 })} />
        </Space>
      </Card>

      <Table
        rowKey="id" columns={columns} dataSource={data?.items ?? []} loading={isLoading}
        scroll={{ x: 1200 }}
        rowClassName={(r) => (r.state === 'NOTIFYING' || r.state === 'TRIGGERED' ? 'alert-row-active' : '')}
        pagination={{
          current: query.page, pageSize: query.size, total: data?.total ?? 0,
          showSizeChanger: true, showTotal: (t) => `共 ${t} 条告警`,
          onChange: (page, size) => setQuery({ ...query, page, size }),
        }}
      />

      <Modal
        title={`静默告警 ${silenceTarget?.alertNo ?? ''}`}
        open={!!silenceTarget}
        onCancel={() => setSilenceTarget(null)}
        onOk={() => silenceTarget && silenceMut.mutate({ no: silenceTarget.alertNo, sec: silenceSec })}
        confirmLoading={silenceMut.isPending}
        okText="静默"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>静默时长 (秒):</span>
          <InputNumber style={{ width: '100%' }} min={60} max={86400} step={60}
            value={silenceSec} onChange={(v) => setSilenceSec(v ?? 3600)} />
          <Space>
            {[1800, 3600, 7200, 14400].map((s) => (
              <Button key={s} size="small" onClick={() => setSilenceSec(s)}>
                {s >= 3600 ? `${s / 3600}h` : `${s / 60}min`}
              </Button>
            ))}
          </Space>
        </Space>
      </Modal>
    </Space>
  )
}
