import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Divider,
  Drawer,
  Empty,
  Input,
  List,
  Pagination,
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
  fetchHistoryDetail,
  removeHistory,
} from '../services/api'
import type { HistoryStatus } from '../services/api'
import ReportBody from './ReportBody'
import type { DiagnosisEntry, HistoryItem, PreTriageReport } from '../types'

interface Props {
  open: boolean
  onClose: () => void
}

const PAGE_SIZE = 10

interface DetailState {
  id: number
  errorType: string
  techStack: string[]
  createdAt: string
  report: PreTriageReport
  entries: DiagnosisEntry[]
}

export default function HistoryDrawer({ open, onClose }: Props) {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<HistoryItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<HistoryStatus>('all')
  const [loading, setLoading] = useState(false)

  // 详情抽屉状态
  const [detail, setDetail] = useState<DetailState | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [appendText, setAppendText] = useState('')
  const [appending, setAppending] = useState(false)

  const load = async (p = page, kw = keyword, st = status) => {
    setLoading(true)
    try {
      const res = await fetchHistory({ page: p, size: PAGE_SIZE, keyword: kw || undefined, status: st })
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
      setStatus('all')
      load(1, '', 'all')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSearch = (value: string) => {
    setKeyword(value)
    setPage(1)
    load(1, value)
  }

  const openDetail = async (item: HistoryItem) => {
    setDetailLoading(true)
    setDetail(null)
    try {
      const d = await fetchHistoryDetail(item.id)
      let report: PreTriageReport | null = null
      try {
        report = JSON.parse(d.preReport || d.explanation)
      } catch {
        report = null
      }
      let entries: DiagnosisEntry[] = []
      try {
        entries = d.finalDiagnosis ? JSON.parse(d.finalDiagnosis) : []
      } catch {
        entries = []
      }
      setDetail({
        id: d.id,
        errorType: d.errorType,
        techStack: d.techStack
          ? d.techStack.split(',').map((s) => s.trim()).filter(Boolean)
          : [],
        createdAt: d.createdAt,
        report:
          report ?? {
            personReport: { summary: d.summary, layer: '', misuse: '', nextStep: '' },
            handoff: { techStack: [], suspectedPoints: [], checkList: [], rawError: d.stackTrace },
          },
        entries,
      })
      setAppendText('')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setDetailLoading(false)
    }
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
      setDetail((prev) => (prev ? { ...prev, entries: updated } : prev))
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
      if (detail?.id === id) setDetail(null)
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
      setDetail(null)
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const techStackTags = (raw: string | null) =>
    (raw ?? '').split(',').map((s) => s.trim()).filter(Boolean)

  return (
    <Drawer
      title="📋 历史记录"
      width={460}
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
                    onClick={() => openDetail(item)}
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

      {/* 详情抽屉（嵌套在历史抽屉之上） */}
      <Drawer
        title={`🔍 ${detail?.errorType ?? '历史详情'}`}
        width={420}
        open={detail !== null}
        onClose={() => setDetail(null)}
      >
        {detailLoading || !detail ? (
          <Empty description="加载中…" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div>
              {detail.techStack.map((s, i) => (
                <Tag key={i} color="blue" style={{ marginRight: 4, marginBottom: 4 }}>
                  {s}
                </Tag>
              ))}
              <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                {detail.createdAt}
              </Typography.Text>
            </div>

            <ReportBody
              person={detail.report.personReport}
              handoff={detail.report.handoff}
            />

            <Divider />
            <Typography.Title level={5}>🤝 项目 AI 诊断记录</Typography.Title>
            {detail.entries.length === 0 ? (
              <Typography.Text type="secondary">暂无回填，粘贴项目 AI 的结论到下方。</Typography.Text>
            ) : (
              <List
                size="small"
                dataSource={detail.entries}
                renderItem={(r) => (
                  <List.Item style={{ padding: '6px 0', alignItems: 'flex-start' }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12, marginRight: 8, flexShrink: 0 }}>
                      [{r.ts}]
                    </Typography.Text>
                    <span style={{ fontSize: 13 }}>{r.content}</span>
                  </List.Item>
                )}
              />
            )}
            <Input.TextArea
              rows={3}
              placeholder="粘贴项目 AI 给出的最终诊断结论…"
              value={appendText}
              onChange={(e) => setAppendText(e.target.value)}
            />
            <Button
              type="primary"
              loading={appending}
              onClick={submitAppend}
              style={{ alignSelf: 'flex-end' }}
            >
              追加诊断
            </Button>
          </Space>
        )}
      </Drawer>
    </Drawer>
  )
}
