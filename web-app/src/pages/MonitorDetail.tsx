import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card, Descriptions, Tag, Space, Button, Row, Col, Statistic, Typography,
  Segmented, Empty, Spin, message,
} from 'antd'
import { ArrowLeftOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery, useMutation } from '@tanstack/react-query'
import { monitorApi } from '@/api'
import { useRealtime } from '@/hooks/useRealtime'
import MetricChart from '@/components/MetricChart'
import { APP_TYPE_LABEL, MONITOR_STATUS_META } from '@/components/severity'
import type { Latest } from '@/types'

const { Title, Text } = Typography

type RangeKey = '1h' | '6h' | '24h' | '7d'
const RANGE_MS: Record<RangeKey, number> = {
  '1h': 3600e3, '6h': 6 * 3600e3, '24h': 24 * 3600e3, '7d': 7 * 24 * 3600e3,
}
const RANGE_STEP: Record<RangeKey, number> = {
  '1h': 60, '6h': 300, '24h': 900, '7d': 3600,
}

/**
 * 监控详情 (PRD 06: /monitors/:id 实时指标卡 / 历史趋势图, 全部角色)。
 * 实时指标卡秒级读 Redis; 历史趋势图读 TSDB 降采样; WebSocket 推送实时刷新。
 */
export default function MonitorDetail() {
  const { id } = useParams<{ id: string }>()
  const monitorId = Number(id)
  const navigate = useNavigate()
  const [range, setRange] = useState<RangeKey>('1h')
  const [selected, setSelected] = useState<string | null>(null)

  const { data: monitor, refetch } = useQuery({
    queryKey: ['monitor', monitorId],
    queryFn: () => monitorApi.get(monitorId),
    enabled: !!monitorId,
  })

  const { data: realtime, refetch: refetchRealtime } = useQuery({
    queryKey: ['monitor', monitorId, 'realtime'],
    queryFn: () => monitorApi.realtime(monitorId),
    enabled: !!monitorId,
    refetchInterval: 10000,
  })

  // 选中的字段查历史趋势
  const selectedLatest: Latest | null = selected && realtime ? realtime[selected] ?? null : null
  const { data: history, isFetching: histLoading } = useQuery({
    queryKey: ['monitor', monitorId, 'history', selected, range],
    queryFn: () => {
      if (!selectedLatest) return Promise.resolve([])
      const to = Date.now()
      const from = to - RANGE_MS[range]
      return monitorApi.history(monitorId, selectedLatest.group, selectedLatest.field, from, to, RANGE_STEP[range])
    },
    enabled: !!selectedLatest,
  })

  // 实时推送刷新
  useRealtime(() => { refetchRealtime() })

  const collectMut = useMutation({
    mutationFn: () => monitorApi.collectNow(monitorId),
    onSuccess: () => { message.success('已触发采集'); setTimeout(refetchRealtime, 1500) },
    onError: (e: Error) => message.error(e.message),
  })

  if (!monitor) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  const entries = Object.entries(realtime ?? {})
  const statusMeta = MONITOR_STATUS_META[monitor.status]
  // 默认选中第一个数值字段
  const activeKey = selected ?? entries.find(([, v]) => v.doubleValue != null)?.[0] ?? null
  const activeLatest = activeKey ? realtime?.[activeKey] ?? null : null

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/monitors')}>返回</Button>
          <Title level={4} style={{ margin: 0 }}>{monitor.name}</Title>
          <Tag color={statusMeta?.color}>{statusMeta?.label}</Tag>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => { refetch(); refetchRealtime() }}>刷新</Button>
          <Button type="primary" icon={<ThunderboltOutlined />} loading={collectMut.isPending}
            onClick={() => collectMut.mutate()}>立即采集</Button>
        </Space>
      </div>

      <Card size="small">
        <Descriptions column={{ xs: 1, sm: 2, md: 4 }} size="small">
          <Descriptions.Item label="类型">{APP_TYPE_LABEL[monitor.appType] ?? monitor.appType}</Descriptions.Item>
          <Descriptions.Item label="协议">{monitor.protocol.toUpperCase()}</Descriptions.Item>
          <Descriptions.Item label="地址">{monitor.host}{monitor.port ? `:${monitor.port}` : ''}</Descriptions.Item>
          <Descriptions.Item label="采集周期">{monitor.intervalSec}s</Descriptions.Item>
          <Descriptions.Item label="连续失败">{monitor.connFailCount}</Descriptions.Item>
          <Descriptions.Item label="最近采集">
            {monitor.lastCollectAt ? new Date(monitor.lastCollectAt).toLocaleString() : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="标签" span={2}>
            {monitor.tags?.length ? monitor.tags.map((t) => <Tag key={t}>{t}</Tag>) : <Text type="secondary">无</Text>}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 实时指标卡 */}
      <Card title="实时指标" size="small"
        extra={<Text type="secondary" style={{ fontSize: 12 }}>每 10s 刷新 · WebSocket 推送</Text>}>
        {entries.length === 0 ? (
          <Empty description="暂无采集数据 (等待首个采集周期或点击立即采集)" />
        ) : (
          <Row gutter={[12, 12]}>
            {entries.map(([key, v]) => (
              <Col xs={12} sm={8} md={6} lg={4} key={key}>
                <Card size="small" hoverable
                  style={{
                    cursor: v.doubleValue != null ? 'pointer' : 'default',
                    borderColor: activeKey === key ? '#165dff' : undefined,
                    borderWidth: activeKey === key ? 2 : 1,
                  }}
                  onClick={() => v.doubleValue != null && setSelected(key)}>
                  <Statistic
                    title={<span style={{ fontSize: 12 }}>{v.group}.{v.field}{v.labels ? ` (${Object.values(v.labels).join(',')})` : ''}</span>}
                    value={v.doubleValue != null ? v.doubleValue : (v.strValue ?? '—')}
                    precision={v.doubleValue != null ? 2 : undefined}
                    suffix={v.unit ?? undefined}
                    valueStyle={{ fontSize: 18, color: v.success ? undefined : '#f53f3f' }}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      {/* 历史趋势图 */}
      <Card title="历史趋势" size="small"
        extra={
          <Segmented size="small" value={range} onChange={(v) => setRange(v as RangeKey)}
            options={[{ label: '1小时', value: '1h' }, { label: '6小时', value: '6h' },
              { label: '24小时', value: '24h' }, { label: '7天', value: '7d' }]} />
        }>
        {!activeLatest ? (
          <Empty description="点击上方数值指标卡查看历史趋势" />
        ) : histLoading ? (
          <Spin style={{ display: 'block', margin: '60px auto' }} />
        ) : (
          <MetricChart
            title={`${activeLatest.group}.${activeLatest.field}`}
            unit={activeLatest.unit ?? undefined}
            points={(history ?? []).map((p) => ({ ts: p.ts, avg: p.avg, min: p.min, max: p.max, value: p.value }))}
            height={280}
          />
        )}
      </Card>
    </Space>
  )
}
