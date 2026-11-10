import { Card, Col, Row, Statistic, Typography, Space, Tag, Empty } from 'antd'
import {
  CloudServerOutlined, AlertOutlined, DisconnectOutlined, CheckCircleOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import { dashboardApi, alertApi } from '@/api'
import { useRealtime } from '@/hooks/useRealtime'
import { SEVERITY_META } from '@/components/severity'

const { Title, Text } = Typography

/**
 * 总览大盘 (PRD 06: /dashboard 分类统计 / 告警级别分布, 全部角色)。
 * 实时刷新: WebSocket 推送 + react-query 轮询兜底。
 */
export default function Dashboard() {
  const { data: summary, refetch } = useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: dashboardApi.summary,
    refetchInterval: 15000,
  })
  const { data: activeAlerts } = useQuery({
    queryKey: ['alerts', 'active'],
    queryFn: alertApi.active,
    refetchInterval: 15000,
  })

  // 实时推送: 收到采集/告警事件即刷新大盘
  useRealtime(() => { refetch() })

  const byType = summary?.monitorByType ?? {}
  const bySeverity = summary?.activeAlertBySeverity ?? {}

  const typePieOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'],
      data: Object.entries(byType).map(([name, value]) => ({ name, value })),
      label: { formatter: '{b}: {c}' },
    }],
  }

  const severityBarOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: Object.keys(bySeverity) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', barWidth: '50%',
      data: Object.entries(bySeverity).map(([k, v]) => ({
        value: v, itemStyle: { color: SEVERITY_META[k as keyof typeof SEVERITY_META]?.color ?? '#86909c' },
      })),
    }],
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>总览大盘</Title>

      <Row gutter={16}>
        <Col xs={12} sm={12} md={6}>
          <Card>
            <Statistic title="监控对象总数" value={summary?.monitorTotal ?? 0}
              prefix={<CloudServerOutlined />} valueStyle={{ color: '#165dff' }} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6}>
          <Card>
            <Statistic title="活动告警" value={summary?.activeAlertTotal ?? 0}
              prefix={<AlertOutlined />} valueStyle={{ color: '#f53f3f' }} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6}>
          <Card>
            <Statistic title="不可达主机" value={summary?.monitorUnreachable ?? 0}
              prefix={<DisconnectOutlined />} valueStyle={{ color: '#ff7d00' }} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6}>
          <Card>
            <Statistic title="正常运行" value={(summary?.monitorTotal ?? 0) - (summary?.monitorUnreachable ?? 0)}
              prefix={<CheckCircleOutlined />} valueStyle={{ color: '#00b42a' }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Card title="监控对象分类统计">
            {summary && summary.monitorTotal > 0
              ? <ReactECharts option={typePieOption} style={{ height: 300 }} />
              : <Empty description="暂无监控对象" style={{ padding: 60 }} />}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="活动告警级别分布">
            {summary && summary.activeAlertTotal > 0
              ? <ReactECharts option={severityBarOption} style={{ height: 300 }} />
              : <Empty description="暂无活动告警" style={{ padding: 60 }} />}
          </Card>
        </Col>
      </Row>

      <Card title="值班视图 · 未确认活动告警 (按级别降序)">
        {activeAlerts && activeAlerts.length > 0 ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {activeAlerts.slice(0, 8).map((a) => (
              <div key={a.id} style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: '8px 12px', background: '#f7f8fa', borderRadius: 6,
              }}>
                <Space>
                  <Tag color={SEVERITY_META[a.severity]?.color}>{SEVERITY_META[a.severity]?.label ?? a.severity}</Tag>
                  <span>{a.alertNo}</span>
                  <span style={{ color: '#86909c' }}>{a.metricGroup}.{a.fieldName}</span>
                </Space>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  当前值 {a.currentValue} / 阈值 {a.thresholdDesc} · 命中 {a.hitCount} 次
                </Text>
              </div>
            ))}
          </Space>
        ) : (
          <Empty description="暂无未确认告警" />
        )}
      </Card>
    </Space>
  )
}
