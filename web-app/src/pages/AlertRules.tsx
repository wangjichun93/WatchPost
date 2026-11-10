import { useState } from 'react'
import {
  Table, Button, Space, Tag, Select, Card, Typography, Modal, Form, InputNumber,
  Switch, message, Popconfirm, Input, Segmented, Tooltip,
} from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ruleApi } from '@/api'
import { useAuthStore, PERM } from '@/store/auth'
import { SEVERITY_META, APP_TYPE_LABEL } from '@/components/severity'
import type { ThresholdRule, CompareOp, Severity, RuleScope } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title, Text } = Typography

const OP_LABEL: Record<CompareOp, string> = {
  GT: '>', GE: '≥', LT: '<', LE: '≤', EQ: '==', NE: '!=', BETWEEN: 'between', DELTA_PCT: '环比Δ%',
}

/**
 * 阈值规则 (PRD 06: /alert-rules 逐指标配置 / 默认模板, 管理员·运维)。
 * 视图切换: 全局默认模板 (按 app_type) / 实例覆写。保存即热加载生效。
 */
export default function AlertRules() {
  const queryClient = useQueryClient()
  const canWrite = useAuthStore((s) => s.hasAnyRole(PERM.RULE_WRITE))
  const [view, setView] = useState<'global' | 'all'>('global')
  const [scopeRef, setScopeRef] = useState<string>('linux')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ThresholdRule | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['rules', view, scopeRef],
    queryFn: () => (view === 'global' ? ruleApi.list('GLOBAL', scopeRef) : ruleApi.list()),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['rules'] })

  const saveMut = useMutation({
    mutationFn: (rule: ThresholdRule) => ruleApi.save(rule),
    onSuccess: () => { message.success('规则已保存并热加载生效'); setModalOpen(false); setEditing(null); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const toggleMut = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => ruleApi.toggle(id, enabled),
    onSuccess: () => { message.success('已更新'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })
  const removeMut = useMutation({
    mutationFn: (id: number) => ruleApi.remove(id),
    onSuccess: () => { message.success('已删除'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })

  const openCreate = () => {
    setEditing(null); form.resetFields()
    form.setFieldsValue({
      scope: 'GLOBAL', scopeRef, operator: 'GE', severity: 'WARN',
      consecutiveN: 3, windowSec: 300, suppressSec: 600,
      effectivePeriod: 'ALL_DAY', recoveryNotify: true, enabled: true, priority: 0,
      thresholdValue: 85,
    })
    setModalOpen(true)
  }
  const openEdit = (r: ThresholdRule) => {
    setEditing(r)
    let tv: number | undefined
    try { tv = JSON.parse(r.thresholdJson)?.value } catch { /* ignore */ }
    form.setFieldsValue({ ...r, thresholdValue: tv })
    setModalOpen(true)
  }

  const columns: ColumnsType<ThresholdRule> = [
    { title: '作用域', dataIndex: 'scope', width: 100, render: (s: RuleScope) => <Tag>{s}</Tag> },
    { title: '指标', width: 200, render: (_, r) => `${r.metricGroup}.${r.fieldName}` },
    {
      title: '条件', width: 160,
      render: (_, r) => <Text code>{OP_LABEL[r.operator]} {r.thresholdJson}</Text>,
    },
    { title: '连续/窗口', width: 110, render: (_, r) => `${r.consecutiveN}次 / ${r.windowSec / 60}min` },
    {
      title: '级别', dataIndex: 'severity', width: 90,
      render: (s: Severity) => <Tag color={SEVERITY_META[s]?.color}>{SEVERITY_META[s]?.label ?? s}</Tag>,
    },
    { title: '生效时段', dataIndex: 'effectivePeriod', width: 100 },
    {
      title: '启用', width: 80,
      render: (_, r) => (
        <Switch size="small" checked={r.enabled} disabled={!canWrite}
          onChange={(c) => r.id && toggleMut.mutate({ id: r.id, enabled: c })} />
      ),
    },
    {
      title: '操作', width: 140, fixed: 'right',
      render: (_, r) => (
        <Space size="small">
          <Button size="small" disabled={!canWrite} onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除该规则?" disabled={!canWrite}
            onConfirm={() => r.id && removeMut.mutate(r.id)}>
            <Button size="small" danger disabled={!canWrite}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>阈值规则</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>
          {canWrite && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增规则</Button>}
        </Space>
      </div>

      <Card size="small">
        <Space wrap>
          <Segmented value={view} onChange={(v) => setView(v as 'global' | 'all')}
            options={[{ label: '全局默认模板', value: 'global' }, { label: '全部规则', value: 'all' }]} />
          {view === 'global' && (
            <Select value={scopeRef} style={{ width: 160 }} onChange={setScopeRef}
              options={Object.entries(APP_TYPE_LABEL).map(([value, label]) => ({ value, label }))} />
          )}
          <Tooltip title="优先级: 实例级 > 类型级 > 全局模板; 上层覆写下层">
            <Text type="secondary" style={{ fontSize: 12 }}>ⓘ 默认值仅为起点, 可在监控详情按实例覆写</Text>
          </Tooltip>
        </Space>
      </Card>

      <Table rowKey={(r) => `${r.id ?? r.metricGroup + r.fieldName}`} columns={columns}
        dataSource={data ?? []} loading={isLoading} scroll={{ x: 1100 }} pagination={{ pageSize: 20 }} />

      <Modal title={editing ? '编辑规则' : '新增规则'} open={modalOpen} width={620} destroyOnClose
        onCancel={() => { setModalOpen(false); setEditing(null) }}
        onOk={() => form.submit()} confirmLoading={saveMut.isPending} okText="保存并生效">
        <Form form={form} layout="vertical" onFinish={(v) => {
          const thresholdJson = v.operator === 'BETWEEN'
            ? JSON.stringify({ min: v.min, max: v.max })
            : v.operator === 'DELTA_PCT'
              ? JSON.stringify({ baselineWindowSec: v.windowSec, pct: v.thresholdValue, direction: v.direction ?? 'drop' })
              : JSON.stringify({ value: v.thresholdValue })
          const rule: ThresholdRule = {
            id: editing?.id, scope: v.scope, scopeRef: v.scopeRef, monitorId: v.monitorId,
            metricGroup: v.metricGroup, fieldName: v.fieldName, operator: v.operator,
            thresholdJson, consecutiveN: v.consecutiveN, windowSec: v.windowSec, severity: v.severity,
            effectivePeriod: v.effectivePeriod, cronExpr: v.cronExpr, suppressSec: v.suppressSec,
            recoveryNotify: v.recoveryNotify, enabled: v.enabled, priority: v.priority,
          }
          saveMut.mutate(rule)
        }} requiredMark={false}>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="scope" label="作用域" style={{ width: '50%' }} rules={[{ required: true }]}>
              <Select options={['GLOBAL', 'TYPE', 'INSTANCE', 'FIELD'].map((v) => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item name="scopeRef" label="作用域引用 (app_type)" style={{ width: '50%' }}>
              <Select allowClear options={Object.entries(APP_TYPE_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="metricGroup" label="指标组" style={{ width: '50%' }} rules={[{ required: true }]}>
              <Input placeholder="如 cpu / memory / api" />
            </Form.Item>
            <Form.Item name="fieldName" label="字段名" style={{ width: '50%' }} rules={[{ required: true }]}>
              <Input placeholder="如 usage_percent" />
            </Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="operator" label="比较符" style={{ width: '40%' }} rules={[{ required: true }]}>
              <Select options={Object.entries(OP_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="thresholdValue" label="阈值" style={{ width: '60%' }} rules={[{ required: true }]}>
              <InputNumber style={{ width: '100%' }} placeholder="如 85" />
            </Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="consecutiveN" label="连续次数" style={{ width: '33%' }} rules={[{ required: true }]}>
              <InputNumber style={{ width: '100%' }} min={1} max={100} />
            </Form.Item>
            <Form.Item name="windowSec" label="窗口(秒)" style={{ width: '33%' }} rules={[{ required: true }]}>
              <InputNumber style={{ width: '100%' }} min={10} max={3600} />
            </Form.Item>
            <Form.Item name="suppressSec" label="抑制(秒)" style={{ width: '34%' }} rules={[{ required: true }]}>
              <InputNumber style={{ width: '100%' }} min={0} max={86400} />
            </Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="severity" label="告警级别" style={{ width: '50%' }} rules={[{ required: true }]}>
              <Select options={Object.entries(SEVERITY_META).map(([value, m]) => ({ value, label: m.label }))} />
            </Form.Item>
            <Form.Item name="effectivePeriod" label="生效时段" style={{ width: '50%' }} rules={[{ required: true }]}>
              <Select options={['ALL_DAY', 'WORK', 'CRON'].map((v) => ({ value: v, label: v }))} />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="recoveryNotify" label="恢复通知" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
