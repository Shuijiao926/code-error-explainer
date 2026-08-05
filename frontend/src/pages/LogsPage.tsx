import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fetchAiLogs } from '../services/api'
import PageContainer from '../components/PageContainer'
import type { AiCallLog } from '../types'

const PAGE_SIZE = 20

const statusColor = (status: string | null) => {
  if (status === 'success') return 'green'
  if (status === 'fallback') return 'orange'
  return 'default'
}

const providerLabel = (provider: string | null) => {
  if (provider === 'mock') return 'mock（模板）'
  return provider || '—'
}

export default function LogsPage() {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<AiCallLog[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)

  const load = async (p = page) => {
    setLoading(true)
    try {
      const res = await fetchAiLogs({ page: p, size: PAGE_SIZE })
      setRecords(res.records)
      setTotal(res.total)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<AiCallLog> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => <Typography.Text type="secondary">{v}</Typography.Text>,
    },
    {
      title: 'Provider',
      dataIndex: 'provider',
      width: 120,
      render: (v: string | null) => <Tag color={v === 'mock' ? 'default' : 'blue'}>{providerLabel(v)}</Tag>,
    },
    { title: '模型', dataIndex: 'model', width: 140, render: (v: string | null) => v || '—' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string | null) => <Tag color={statusColor(v)}>{v || '—'}</Tag>,
    },
    {
      title: '耗时(ms)',
      dataIndex: 'durationMs',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '—' : v),
    },
    {
      title: '输入 token',
      dataIndex: 'promptTokens',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '—' : v),
    },
    {
      title: '输出 token',
      dataIndex: 'completionTokens',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '—' : v),
    },
    { title: '关联历史', dataIndex: 'historyId', width: 90, render: (v: number | null) => (v == null ? '—' : v) },
    {
      title: '输入摘要 / 错误',
      key: 'detail',
      render: (_, r) => (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {r.errorMessage ? `⚠ ${r.errorMessage}` : r.inputExcerpt || '—'}
        </Typography.Text>
      ),
    },
  ]

  return (
    <PageContainer maxWidth={1200}>
      <Card title="🛰️ AI 调用日志">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            记录每次 AI 调用：provider / 模型 / 耗时 / token / 状态。当前 {total} 条。
          </Typography.Text>
          <Table<AiCallLog>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={records}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
              onChange: (p) => {
                setPage(p)
                load(p)
              },
            }}
          />
        </Space>
      </Card>
    </PageContainer>
  )
}
