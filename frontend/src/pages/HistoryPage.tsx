import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  List,
  Popconfirm,
  Segmented,
  Space,
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, EyeOutlined } from '@ant-design/icons'
import {
  backfillDiagnosis,
  clearHistoryAll,
  fetchHistory,
  removeHistory,
} from '../services/api'
import type { HistoryStatus } from '../services/api'
import DetailModal from '../components/DetailModal'
import PageContainer from '../components/PageContainer'
import PaginationBar from '../components/PaginationBar'
import { splitCsv, useHistoryDetail } from '../hooks/useHistoryDetail'
import type { HistoryItem } from '../types'

const PAGE_SIZE = 10

export default function HistoryPage() {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<HistoryItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<HistoryStatus>('all')
  const [loading, setLoading] = useState(false)

  // 详情弹窗（共享 hook：加载/解析/关闭）
  const { detail, detailLoading, openDetail, patchDetail, closeDetail } = useHistoryDetail()

  // 追加诊断输入区（仅历史页）
  const [appendText, setAppendText] = useState('')
  const [appending, setAppending] = useState(false)

  const load = async (p = page, kw = keyword, st = status) => {
    setLoading(true)
    try {
      const res = await fetchHistory({ page: p, size: PAGE_SIZE, keyword: kw || undefined, status: st })
      // 删除后当前页可能越界，回退到最后一页
      if (res.records.length === 0 && res.total > 0 && p > 1) {
        const last = Math.max(1, Math.ceil(res.total / PAGE_SIZE))
        setPage(last)
        const retry = await fetchHistory({ page: last, size: PAGE_SIZE, keyword: kw || undefined, status: st })
        setRecords(retry.records)
        setTotal(retry.total)
      } else {
        setRecords(res.records)
        setTotal(res.total)
      }
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(1, '', 'all')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSearch = (value: string) => {
    setKeyword(value)
    setPage(1)
    load(1, value)
  }

  const submitAppend = async () => {
    const text = appendText.trim()
    if (!text) {
      message.warning('请填写诊断内容')
      return
    }
    if (!detail) return
    setAppending(true)
    try {
      const updated = await backfillDiagnosis(detail.id, text)
      patchDetail({ entries: updated })
      setAppendText('')
      message.success('已追加诊断记录')
      load(page, keyword)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setAppending(false)
    }
  }

  const onDelete = async (id: number) => {
    try {
      await removeHistory(id)
      message.success('已删除')
      if (detail?.id === id) closeDetail()
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
      closeDetail()
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  return (
    <PageContainer>
      <Card
        title="📋 历史记录"
        extra={
          <Popconfirm title="确定清空全部历史？" onConfirm={onClear} okText="清空" cancelText="取消">
            <Button danger size="small" icon={<DeleteOutlined />}>
              清空
            </Button>
          </Popconfirm>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Segmented
            block
            value={status}
            onChange={(v) => {
              const st = v as HistoryStatus
              setStatus(st)
              setPage(1)
              load(1, keyword, st)
            }}
            options={[
              { label: '全部', value: 'all' },
              { label: '待回填', value: 'pending' },
              { label: '已诊断', value: 'diagnosed' },
            ]}
          />
          <Input.Search placeholder="搜索异常类型 / 概要 / 技术栈" allowClear onSearch={onSearch} />

          {records.length === 0 && !loading ? (
            <Empty description="暂无历史记录" style={{ padding: 40 }} />
          ) : (
            <List
              loading={loading}
              dataSource={records}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Button
                      key="view"
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => openDetail(item.id)}
                    >
                      详情
                    </Button>,
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
                      <Space>
                        <Typography.Text strong>{item.errorType}</Typography.Text>
                        {item.finalDiagnosis ? (
                          <Tag color="green">已诊断</Tag>
                        ) : (
                          <Tag>待回填</Tag>
                        )}
                      </Space>
                    }
                    description={
                      <>
                        <div>{item.summary}</div>
                        {splitCsv(item.techStack).length > 0 && (
                          <div style={{ marginTop: 4 }}>
                            {splitCsv(item.techStack).map((s, i) => (
                              <Tag key={i} color="blue" style={{ marginRight: 4, marginBottom: 4 }}>
                                {s}
                              </Tag>
                            ))}
                          </div>
                        )}
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

          {total > 0 && (
            <PaginationBar
              total={total}
              page={page}
              pageSize={PAGE_SIZE}
              onChange={(p) => {
                setPage(p)
                load(p, keyword)
              }}
            />
          )}
        </Space>
      </Card>

      {/* 详情弹窗（居中 Modal，历史页带追加诊断区） */}
      <DetailModal
        open={detail !== null}
        loading={detailLoading}
        detail={detail}
        onClose={closeDetail}
        titlePrefix="🔍"
        appendText={appendText}
        onAppendTextChange={setAppendText}
        appending={appending}
        onAppend={submitAppend}
      />
    </PageContainer>
  )
}
