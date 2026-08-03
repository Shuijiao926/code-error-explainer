import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Drawer,
  Empty,
  Input,
  List,
  Pagination,
  Popconfirm,
  Space,
  Typography,
} from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { clearHistoryAll, fetchHistory, fetchHistoryDetail, removeHistory } from '../services/api'
import type { ExplainResponse, HistoryItem } from '../types'

interface Props {
  open: boolean
  onClose: () => void
}

const PAGE_SIZE = 10

export default function HistoryDrawer({ open, onClose }: Props) {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<HistoryItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async (p = page, kw = keyword) => {
    setLoading(true)
    try {
      const res = await fetchHistory({ page: p, size: PAGE_SIZE, keyword: kw || undefined })
      setRecords(res.records)
      setTotal(res.total)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      setPage(1)
      setKeyword('')
      load(1, '')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSearch = (value: string) => {
    setKeyword(value)
    setPage(1)
    load(1, value)
  }

  const onView = async (id: number) => {
    try {
      const detail = await fetchHistoryDetail(id)
      let explanation: ExplainResponse['explanation'] | null = null
      try {
        explanation = JSON.parse(detail.explanation)
      } catch {
        explanation = null
      }
      const full: ExplainResponse = {
        id: detail.id,
        errorType: detail.errorType,
        summary: detail.summary,
        createdAt: detail.createdAt,
        explanation:
          explanation ?? {
            overview: detail.explanation,
            location: '',
            rootCause: '',
            fixSteps: [],
            prevention: '',
          },
      }
      onClose()
      window.dispatchEvent(new CustomEvent('history:view', { detail: full }))
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const onDelete = async (id: number) => {
    try {
      await removeHistory(id)
      message.success('已删除')
      load(page, keyword)
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const onClear = async () => {
    try {
      await clearHistoryAll()
      message.success('已清空全部历史')
      setRecords([])
      setTotal(0)
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  return (
    <Drawer
      title="📋 历史记录"
      width={420}
      open={open}
      onClose={onClose}
      extra={
        <Popconfirm title="确定清空全部历史？" onConfirm={onClear} okText="清空" cancelText="取消">
          <Button danger size="small" icon={<DeleteOutlined />}>
            清空
          </Button>
        </Popconfirm>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Input.Search placeholder="搜索异常类型 / 概要" allowClear onSearch={onSearch} />

        {records.length === 0 && !loading ? (
          <Empty description="暂无历史记录" style={{ padding: 40 }} />
        ) : (
          <List
            loading={loading}
            dataSource={records}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Popconfirm
                    key="del"
                    title="删除这条记录？"
                    onConfirm={() => onDelete(item.id)}
                    okText="删除"
                    cancelText="取消"
                  >
                    <Button size="small" danger type="text" icon={<DeleteOutlined />} />
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Typography.Link onClick={() => onView(item.id)}>
                      {item.errorType}
                    </Typography.Link>
                  }
                  description={
                    <>
                      <div>{item.summary}</div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.createdAt}
                      </Typography.Text>
                    </>
                  }
                />
              </List.Item>
            )}
          />
        )}

        {total > PAGE_SIZE && (
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={total}
            onChange={(p) => {
              setPage(p)
              load(p, keyword)
            }}
            showSizeChanger={false}
            size="small"
          />
        )}
      </Space>
    </Drawer>
  )
}
