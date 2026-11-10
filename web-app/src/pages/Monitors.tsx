import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Table, Button, Space, Tag, Input, Select, Modal, Form, InputNumber, message,
  Popconfirm, Switch, Card, Typography, Tooltip,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { monitorApi, type MonitorQuery } from '@/api'
import { useAuthStore, PERM } from '@/store/auth'
import { APP_TYPE_LABEL, APP_DEFAULT_PORT, APP_CREDENTIAL_FIELDS, MONITOR_STATUS_META } from '@/components/severity'
import type { MonitorView, MonitorRequest, AppType } from '@/types'
import type { ColumnsType } from 'antd/es/table'

const { Title } = Typography

/**
 * 监控中心 (PRD 06: /monitors 增删改查 / 标签 / 批量导入, 管理员·运维)。
 * 新增先连通性预检, 失败不落库; 凭据加密存储, 列表脱敏展示。
 */
export default function Monitors() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canWrite = useAuthStore((s) => s.hasAnyRole(PERM.MONITOR_WRITE))

  const [query, setQuery] = useState<MonitorQuery>({ page: 1, size: 20 })
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<MonitorView | null>(null)
  const [form] = Form.useForm()
  const appType = Form.useWatch('appType', form) as AppType | undefined

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['monitors', query],
    queryFn: () => monitorApi.list(query),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['monitors'] })

  const createMut = useMutation({
    mutationFn: (req: MonitorRequest) => (editing ? monitorApi.update(editing.id!, req) : monitorApi.create(req)),
    onSuccess: () => {
      message.success(editing ? '监控对象已更新' : '监控对象已创建 (连通性预检通过)')
      setModalOpen(false); setEditing(null); form.resetFields(); invalidate()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const removeMut = useMutation({
    mutationFn: (id: number) => monitorApi.remove(id),
    onSuccess: () => { message.success('已删除'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })

  const toggleMut = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => monitorApi.toggle(id, enabled),
    onSuccess: () => { message.success('状态已更新'); invalidate() },
    onError: (e: Error) => message.error(e.message),
  })

  const openCreate = () => {
    setEditing(null); form.resetFields()
    form.setFieldsValue({ appType: 'linux', intervalSec: 30, port: 22 })
    setModalOpen(true)
  }
  const openEdit = (m: MonitorView) => {
    setEditing(m)
    const ex = (m.extra ?? {}) as Record<string, unknown>
    const headers = ex.headers
    const expect = ex.expectStatus
    form.setFieldsValue({
      name: m.name, appType: m.appType, host: m.host, port: m.port,
      intervalSec: m.intervalSec, remark: m.remark, tags: m.tags,
      // HTTP 探测配置回填 (headers 对象→JSON串; expectStatus 数组→逗号串)
      http_method: (ex.method as string) ?? 'GET',
      http_path: (ex.path as string) ?? '',
      http_headers: typeof headers === 'string' ? headers : (headers ? JSON.stringify(headers) : ''),
      http_body: (ex.body as string) ?? '',
      http_contentType: (ex.contentType as string) ?? 'application/json',
      http_expect: Array.isArray(expect) ? expect.join(',') : ((expect as string) ?? ''),
    })
    setModalOpen(true)
  }

  const onAppTypeChange = (v: AppType) => {
    form.setFieldsValue({ port: APP_DEFAULT_PORT[v] })
  }

  const columns: ColumnsType<MonitorView> = [
    { title: '名称', dataIndex: 'name', render: (t, r) => <a onClick={() => navigate(`/monitors/${r.id}`)}>{t}</a> },
    { title: '类型', dataIndex: 'appType', width: 120, render: (t: string) => <Tag>{APP_TYPE_LABEL[t] ?? t}</Tag> },
    { title: '协议', dataIndex: 'protocol', width: 90, render: (t: string) => t.toUpperCase() },
    { title: '地址', width: 200, render: (_, r) => `${r.host}${r.port ? ':' + r.port : ''}` },
    { title: '周期', dataIndex: 'intervalSec', width: 80, render: (v: number) => `${v}s` },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: number) => <Tag color={MONITOR_STATUS_META[s]?.color}>{MONITOR_STATUS_META[s]?.label}</Tag>,
    },
    {
      title: '启用', width: 80,
      render: (_, r) => (
        <Switch size="small" checked={r.status !== 0} disabled={!canWrite}
          onChange={(c) => toggleMut.mutate({ id: r.id, enabled: c })} />
      ),
    },
    {
      title: '操作', width: 200, fixed: 'right',
      render: (_, r) => (
        <Space size="small">
          <Tooltip title="立即采集">
            <Button size="small" icon={<ThunderboltOutlined />} disabled={!canWrite}
              onClick={async () => { await monitorApi.collectNow(r.id); message.success('已触发采集'); refetch() }} />
          </Tooltip>
          <Button size="small" disabled={!canWrite} onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除该监控对象?" onConfirm={() => removeMut.mutate(r.id)} disabled={!canWrite}>
            <Button size="small" danger disabled={!canWrite}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const credFields = appType ? APP_CREDENTIAL_FIELDS[appType] ?? [] : []

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>监控中心</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>
          {canWrite && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增监控</Button>}
        </Space>
      </div>

      <Card size="small">
        <Space wrap>
          <Input placeholder="搜索名称/主机" prefix={<SearchOutlined />} allowClear style={{ width: 220 }}
            onPressEnter={(e) => setQuery({ ...query, keyword: (e.target as HTMLInputElement).value, page: 1 })} />
          <Select placeholder="类型" allowClear style={{ width: 140 }}
            options={Object.entries(APP_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            onChange={(v) => setQuery({ ...query, appType: v, page: 1 })} />
          <Select placeholder="状态" allowClear style={{ width: 120 }}
            options={[{ value: 1, label: '正常' }, { value: 2, label: '不可达' }, { value: 0, label: '停用' }]}
            onChange={(v) => setQuery({ ...query, status: v, page: 1 })} />
        </Space>
      </Card>

      <Table
        rowKey="id" columns={columns} dataSource={data?.items ?? []} loading={isLoading}
        scroll={{ x: 1100 }}
        pagination={{
          current: query.page, pageSize: query.size, total: data?.total ?? 0,
          showSizeChanger: true, showTotal: (t) => `共 ${t} 个监控对象`,
          onChange: (page, size) => setQuery({ ...query, page, size }),
        }}
      />

      <Modal
        title={editing ? '编辑监控对象' : '新增监控对象'}
        open={modalOpen} width={640} destroyOnClose
        onCancel={() => { setModalOpen(false); setEditing(null) }}
        onOk={() => form.submit()}
        confirmLoading={createMut.isPending}
        okText={editing ? '保存' : '创建 (含连通性预检)'}
      >
        <Form form={form} layout="vertical" onFinish={(v) => {
          const cred: Record<string, string> = {}
          credFields.forEach((f) => { if (v[`cred_${f.key}`]) cred[f.key] = v[`cred_${f.key}`] })
          // HTTP 型: 把 method/path/headers/body/expectStatus 收进 extra (后端存 wp_monitor.extra_json)
          const extra: Record<string, unknown> = {}
          if (v.appType === 'http') {
            if (v.http_method) extra.method = v.http_method
            if (v.http_path) extra.path = v.http_path
            if (v.http_headers) extra.headers = v.http_headers
            if (v.http_body) extra.body = v.http_body
            if (v.http_contentType) extra.contentType = v.http_contentType
            if (v.http_expect) extra.expectStatus = v.http_expect
          }
          const req: MonitorRequest = {
            name: v.name, appType: v.appType, host: v.host, port: v.port,
            intervalSec: v.intervalSec, remark: v.remark, tags: v.tags,
            credential: cred,
            extra: Object.keys(extra).length ? extra : undefined,
          }
          createMut.mutate(req)
        }} requiredMark={false}>
          <Form.Item name="name" label="监控名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如 prod-mysql-01" />
          </Form.Item>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="appType" label="监控类型" style={{ width: '40%' }} rules={[{ required: true }]}>
              <Select options={Object.entries(APP_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
                onChange={onAppTypeChange} />
            </Form.Item>
            <Form.Item name="host" label="主机地址" style={{ width: '60%' }} rules={[{ required: true, message: '请输入地址' }]}>
              <Input placeholder={appType === 'http' ? 'https://api.example.com/health' : '10.20.3.11'} />
            </Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="port" label="端口" style={{ width: '40%' }}>
              <InputNumber style={{ width: '100%' }} min={1} max={65535} />
            </Form.Item>
            <Form.Item name="intervalSec" label="采集周期(秒)" style={{ width: '60%' }}
              rules={[{ required: true }]} tooltip="PRD: 周期 ≥15s">
              <InputNumber style={{ width: '100%' }} min={15} max={3600} />
            </Form.Item>
          </Space.Compact>

          {/* HTTP API 探测配置 (GET/POST 嗅探): method/path/headers/body/期望状态码 */}
          {appType === 'http' && (
            <Card size="small" title="HTTP 探测配置 (GET/POST 嗅探)" style={{ marginBottom: 16 }}>
              <Space.Compact style={{ width: '100%' }}>
                <Form.Item name="http_method" label="请求方法" style={{ width: '35%' }} initialValue="GET">
                  <Select options={['GET', 'POST', 'PUT', 'DELETE', 'HEAD'].map((m) => ({ value: m, label: m }))} />
                </Form.Item>
                <Form.Item name="http_path" label="路径 (可选, 追加到主机地址后)" style={{ width: '65%' }}>
                  <Input placeholder="如 /api/v1/health 或 /open/status" />
                </Form.Item>
              </Space.Compact>
              <Form.Item name="http_expect" label="期望状态码 (逗号分隔, 空=2xx)" tooltip="如 200 或 200,204; 预检与可用性判定以此为准">
                <Input placeholder="200" />
              </Form.Item>
              <Form.Item name="http_headers" label="请求头 (JSON, 可选)" tooltip='如 {"X-App-Key":"abc","Accept":"application/json"}'>
                <Input.TextArea rows={2} placeholder='{"X-App-Key":"abc"}' style={{ fontFamily: 'monospace', fontSize: 12 }} />
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.http_method !== cur.http_method}>
                {({ getFieldValue }) =>
                  ['POST', 'PUT', 'PATCH'].includes(getFieldValue('http_method')) ? (
                    <>
                      <Form.Item name="http_contentType" label="Content-Type" initialValue="application/json">
                        <Input placeholder="application/json" />
                      </Form.Item>
                      <Form.Item name="http_body" label="请求体 (JSON, 可选)">
                        <Input.TextArea rows={3} placeholder='{"user":"demo"}' style={{ fontFamily: 'monospace', fontSize: 12 }} />
                      </Form.Item>
                    </>
                  ) : null
                }
              </Form.Item>
            </Card>
          )}

          {credFields.length > 0 && (
            <Card size="small" title="只读凭据 (AES-256-GCM 加密存储)" style={{ marginBottom: 16 }}>
              {credFields.map((f) => {
                // 必填判定: 非编辑态 且 字段未标记 optional
                const required = !editing && !f.optional
                const control = f.multiline
                  ? <Input.TextArea rows={3} placeholder={editing ? '留空则不修改' : (f.hint ?? `请输入${f.label}`)} style={{ fontFamily: 'monospace', fontSize: 12 }} />
                  : f.secret
                    ? <Input.Password placeholder={editing ? '留空则不修改' : (f.hint ?? `请输入${f.label}`)} autoComplete="new-password" />
                    : <Input placeholder={editing ? '留空则不修改' : (f.hint ?? `请输入${f.label}`)} />
                return (
                  <Form.Item key={f.key} name={`cred_${f.key}`} label={f.label}
                    tooltip={f.hint}
                    rules={required ? [{ required: true, message: `请输入${f.label}` }] : []}>
                    {control}
                  </Form.Item>
                )
              })}
            </Card>
          )}

          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="如 env:prod, team:db (回车添加)" tokenSeparators={[',']} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
