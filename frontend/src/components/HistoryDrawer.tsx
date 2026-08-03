import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Drawer,
  Empty,
  Input,
  List,
  Modal,
  Pagination,
  Popconfirm,
  Space,
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons'
import {
  backfillDiagnosis,
  clearHistoryAll,
  fetchHistory,
  fetchHistoryDetail,
  removeHistory,
} from '../services/api'
import type { DiagnosisEntry, ExplainResponse, HistoryItem, PreTriageReport } from '../types'

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

  // 回填弹窗状态
  const [backfillItem, setBackfillItem] = useState<HistoryItem | null>(null)
  const [backfillRecords, setBackfillRecords] = useState<DiagnosisEntry[]>([])
  const [backfillText, setBackfillText] = useState('')
  const [backfillSubmitting, setBackfillSubmitting] = useState(false)

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
      let report: PreTriageReport | null = null
      try {
        report = JSON.parse(detail.preReport || detail.explanation)
      } catch {
        report = null
      }
      const full: ExplainResponse = {
        id: detail.id,
        errorType: detail.errorType,
        techStack: detail.techStack
          ? detail.techStack.split(',').map((s) => s.trim()).filter(Boolean)
          : [],
        summary: detail.summary,
        createdAt: detail.createdAt,
        report:
          report ?? {
            personReport: { summary: detail.summary, layer: '', misuse: '', nextStep: '' },
            handoff: { techStack: [], suspectedPoints: [], checkList: [], rawError: detail.stackTrace },
          },
      }
      onClose()
      window.dispatchEvent(new CustomEvent('history:view', { detail: full }))
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const openBackfill = async (item: HistoryItem) => {
    try {
      const detail = await fetchHistoryDetail(item.id)
      let entries: DiagnosisEntry[] = []
      try {
        entries = detail.finalDiagnosis ? JSON.parse(detail.finalDiagnosis) : []
      } catch {
        entries = []
      }
      setBackfillItem(item)
      setBackfillRecords(entries)
      setBackfillText('')
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const submitBackfill = async () => {
    const text = backfillText.trim()
    if (!text) {
      message.warning('请填写诊断内容')
      return
    }
    if (!backfillItem) return
    setBackfillSubmitting(true)
    try {
      const updated = await backfillDiagnosis(backfillItem.id, text)
      setBackfillRecords(updated)
      setBackfillText('')
      message.success('已追加诊断记录')
      load(page, keyword)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setBackfillSubmitting(false)
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

  const techStackTags = (raw: string | null) =>
    (raw ?? '').split(',').map((s) => s.trim()).filter(Boolean)

  return (
    <Drawer
      title="📋 历史记录"
      width={440}
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
                    key="bf"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openBackfill(item)}
                  >
                    回填
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
                      <Typography.Link onClick={() => onView(item.id)}>{item.errorType}</Typography.Link>
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
                      {techStackTags(item.techStack).length > 0 && (
                        <div style={{ marginTop: 4 }}>
                          {techStackTags(item.techStack).map((s, i) => (
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

      {/* 回填项目 AI 诊断弹窗 */}
      <Modal
        title={`🤝 回填项目 AI 诊断 · ${backfillItem?.errorType ?? ''}`}
        open={backfillItem !== null}
        onCancel={() => setBackfillItem(null)}
        onOk={submitBackfill}
        okText="追加"
        cancelText="取消"
        confirmLoading={backfillSubmitting}
      >
        <Typography.Text type="secondary">已有诊断记录：</Typography.Text>
        <List
          size="small"
          style={{ marginTop: 4 }}
          locale={{ emptyText: '暂无诊断记录' }}
          dataSource={backfillRecords}
          renderItem={(r) => (
            <List.Item style={{ padding: '6px 0' }}>
              <Typography.Text type="secondary" style={{ fontSize: 12, marginRight: 8, flexShrink: 0 }}>
                [{r.ts}]
              </Typography.Text>
              <span style={{ fontSize: 13 }}>{r.content}</span>
            </List.Item>
          )}
        />
        <Input.TextArea
          rows={4}
          placeholder="粘贴项目 AI 给出的最终诊断结论…"
          value={backfillText}
          onChange={(e) => setBackfillText(e.target.value)}
          style={{ marginTop: 12 }}
        />
      </Modal>
    </Drawer>
  )
}
