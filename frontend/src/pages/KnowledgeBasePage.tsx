import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Input,
  List,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Tag,
  theme,
  Tooltip,
  Typography,
} from 'antd'
import {
  BugOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  GlobalOutlined,
  LineChartOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import { archiveHistory, exportKb, fetchKb, fetchKbStats } from '../services/api'
import type { KbExportFormat } from '../services/api'
import DetailModal from '../components/DetailModal'
import PageContainer from '../components/PageContainer'
import PaginationBar from '../components/PaginationBar'
import { splitCsv, useHistoryDetail } from '../hooks/useHistoryDetail'
import type { KbItem, KbStats, NameCount, TrendPoint } from '../types'

const PAGE_SIZE = 10

type ArchivedFilter = 'all' | 'true' | 'false'

const LANGUAGE_OPTIONS = [
  { label: 'Java', value: 'java' },
  { label: 'Python', value: 'python' },
  { label: 'Node.js', value: 'node' },
  { label: 'Go', value: 'go' },
]

interface KbFilters {
  keyword: string
  language?: string
  techStack: string
  tag: string
  range: [Dayjs | null, Dayjs | null] | null
  archived: ArchivedFilter
}

interface TagModalState {
  id: number
  errorType: string
  /** archive=沉淀并打标签；edit=编辑已沉淀档案标签 */
  mode: 'archive' | 'edit'
}

const INITIAL_FILTERS: KbFilters = {
  keyword: '',
  language: undefined,
  techStack: '',
  tag: '',
  range: null,
  archived: 'all',
}

/** 当前筛选条件的中文摘要（统计视图头部展示，说明统计口径） */
const summarizeFilters = (f: KbFilters): string[] => {
  const tags: string[] = []
  if (f.keyword) tags.push(`关键字「${f.keyword}」`)
  if (f.language) tags.push(`语言「${f.language}」`)
  if (f.techStack) tags.push(`技术栈「${f.techStack}」`)
  if (f.tag) tags.push(`标签「${f.tag}」`)
  if (f.range?.[0] && f.range?.[1]) {
    tags.push(`${f.range[0].format('YYYY-MM-DD')} ~ ${f.range[1].format('YYYY-MM-DD')}`)
  }
  if (f.archived === 'true') tags.push('仅已沉淀')
  if (f.archived === 'false') tags.push('仅未沉淀')
  return tags
}

/** 分布条：名称 + 数量 + 相对条（按最大值归一化，技术栈多值场景不误导为占比） */
function DistList({ items }: { items: NameCount[] }) {
  const { token } = theme.useToken()
  if (!items.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" style={{ padding: 24 }} />
  }
  const max = Math.max(...items.map((i) => i.count), 1)
  return (
    <List
      size="small"
      dataSource={items}
      renderItem={(item) => (
        <List.Item style={{ padding: '10px 0', display: 'block' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, gap: 8 }}>
            <Typography.Text ellipsis style={{ fontSize: 14 }} title={item.name}>
              {item.name}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 14 }}>
              {item.count}
            </Typography.Text>
          </div>
          <Progress
            percent={Math.round((item.count / max) * 100)}
            showInfo={false}
            size="small"
            strokeColor={token.colorPrimary}
            trailColor={token.colorFillSecondary}
          />
        </List.Item>
      )}
    />
  )
}

/** 近 N 天新增趋势：轻量 CSS 柱状图（无额外图表依赖，颜色随 AntD 主题，柱顶数值 + hover 提示） */
function TrendChart({ trend }: { trend: TrendPoint[] }) {
  const { token } = theme.useToken()
  if (!trend.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无趋势数据" style={{ padding: 24 }} />
  }
  const max = Math.max(...trend.map((t) => t.count), 1)
  // 数据点多时（如 90 天）间隔展示日期标签与柱顶数值，避免重叠
  const labelEvery = Math.max(1, Math.ceil(trend.length / 12))
  const dense = trend.length > 31
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 220 }}>
      {trend.map((t, i) => (
        <div
          key={t.date}
          style={{
            flex: 1,
            height: '100%',
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
            alignItems: 'center',
            gap: 3,
          }}
        >
          {/* 柱顶数值：稀疏数据逐柱显示，密集数据只标非零峰值 */}
          <div style={{ height: 16, display: 'flex', alignItems: 'flex-end' }}>
            {t.count > 0 && (!dense || i % labelEvery === 0) ? (
              <Tooltip title={`${t.date}：${t.count} 条`}>
                <Typography.Text style={{ fontSize: 10, lineHeight: 1.2, color: token.colorTextSecondary }}>
                  {t.count}
                </Typography.Text>
              </Tooltip>
            ) : null}
          </div>
          <Tooltip title={`${t.date}：${t.count} 条`}>
            <div
              style={{
                width: '100%',
                maxWidth: 22,
                height: Math.max(2, Math.round((t.count / max) * 158)),
                borderRadius: 3,
                cursor: t.count > 0 ? 'pointer' : 'default',
                background: t.count > 0 ? token.colorPrimary : token.colorFillSecondary,
              }}
            />
          </Tooltip>
          <div style={{ height: 16, display: 'flex', alignItems: 'flex-start' }}>
            {i % labelEvery === 0 ? (
              <Typography.Text type="secondary" style={{ fontSize: 10, lineHeight: 1.2 }}>
                {t.date.slice(5)}
              </Typography.Text>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  )
}

/** KPI 汇总卡片：彩色图标 + 标题 + 大数值，用于统计概览顶部总览行 */
function KpiCard({
  icon,
  label,
  value,
  color,
}: {
  icon: ReactNode
  label: string
  value: number
  color?: string
}) {
  const { token } = theme.useToken()
  const accent = color ?? token.colorPrimary
  return (
    <Card size="small" styles={{ body: { padding: 16 } }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 10,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 22,
            color: accent,
            background: `${accent}1f`,
          }}
        >
          {icon}
        </div>
        <div style={{ minWidth: 0 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', lineHeight: 1.4 }}>
            {label}
          </Typography.Text>
          <Typography.Title level={3} style={{ margin: 0, lineHeight: 1.3 }}>
            {value.toLocaleString()}
          </Typography.Title>
        </div>
      </div>
    </Card>
  )
}

const buildFilterParams = (f: KbFilters) => ({
  keyword: f.keyword || undefined,
  language: f.language,
  techStack: f.techStack || undefined,
  tag: f.tag || undefined,
  startTime: f.range?.[0] ? f.range[0].format('YYYY-MM-DD') : undefined,
  endTime: f.range?.[1] ? f.range[1].format('YYYY-MM-DD') : undefined,
  archived: f.archived === 'all' ? undefined : f.archived === 'true',
})

const buildKbParams = (page: number, f: KbFilters) => ({
  page,
  size: PAGE_SIZE,
  ...buildFilterParams(f),
})

export default function KnowledgeBasePage() {
  const { message } = AntdApp.useApp()
  const { token } = theme.useToken()
  const [records, setRecords] = useState<KbItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [filters, setFilters] = useState<KbFilters>(INITIAL_FILTERS)
  const [drafts, setDrafts] = useState({ keyword: '', techStack: '', tag: '' })

  // 统计视图与导出
  const [view, setView] = useState<'list' | 'stats'>('list')
  const [stats, setStats] = useState<KbStats | null>(null)
  const [statsLoading, setStatsLoading] = useState(false)
  const [exporting, setExporting] = useState<KbExportFormat | null>(null)
  const activeFilters = summarizeFilters(filters)
  // KPI「近 30 天新增」= 趋势序列求和（趋势按当前筛选口径已按天补齐，含当天）
  const trendTotal = stats?.trend.reduce((sum, t) => sum + t.count, 0) ?? 0

  // 详情弹窗（共享 hook：加载/解析/关闭）
  const { detail, detailLoading, openDetail, patchDetail, closeDetail } = useHistoryDetail()

  // 沉淀/编辑标签弹窗
  const [tagModal, setTagModal] = useState<TagModalState | null>(null)
  const [tagText, setTagText] = useState('')
  const [tagSaving, setTagSaving] = useState(false)

  const load = async (next?: { page?: number; filters?: KbFilters }) => {
    const p = next?.page ?? page
    const f = next?.filters ?? filters
    setPage(p)
    setLoading(true)
    try {
      const res = await fetchKb(buildKbParams(p, f))
      // 删除/取消沉淀后当前页可能越界，回退到最后一页
      if (res.records.length === 0 && res.total > 0 && p > 1) {
        const last = Math.max(1, Math.ceil(res.total / PAGE_SIZE))
        setPage(last)
        const retry = await fetchKb(buildKbParams(last, f))
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

  /** 统计加载：复用当前筛选口径，TopN=0 全量（KPI 需要完整异常类型数）/ 近 30 天 */
  const loadStats = async (f: KbFilters = filters) => {
    setStatsLoading(true)
    try {
      const res = await fetchKbStats({ ...buildFilterParams(f), topN: 0, days: 30 })
      setStats(res)
    } catch (e) {
      message.error(`统计加载失败：${(e as Error).message}`)
      setStats(null)
    } finally {
      setStatsLoading(false)
    }
  }

  /** 导出下载：Markdown / CSV，走浏览器下载 */
  const download = async (format: KbExportFormat) => {
    setExporting(format)
    try {
      const { blob, filename } = await exportKb(format, buildKbParams(page, filters))
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      // 延迟释放对象 URL：同步 revoke 会在个别浏览器中于下载启动前取消下载
      setTimeout(() => URL.revokeObjectURL(url), 10000)
      message.success(format === 'csv' ? 'CSV 已导出' : 'Markdown 已导出')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setExporting(null)
    }
  }

  useEffect(() => {
    load({ page: 1 })
    loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** 离散筛选控件（语言/时间范围/沉淀状态）变化即查 */
  const applyFilter = <K extends keyof KbFilters>(key: K, value: KbFilters[K]) => {
    const next = { ...filters, [key]: value }
    setFilters(next)
    load({ page: 1, filters: next })
    loadStats(next)
  }

  /** 文本筛选（关键字/技术栈/标签）：回车或点击搜索才生效，清空立即生效 */
  const commitText = (key: 'keyword' | 'techStack' | 'tag', value: string) => {
    const next = { ...filters, [key]: value }
    setFilters(next)
    setDrafts((prev) => ({ ...prev, [key]: value }))
    load({ page: 1, filters: next })
    loadStats(next)
  }

  const resetFilters = () => {
    setFilters(INITIAL_FILTERS)
    setDrafts({ keyword: '', techStack: '', tag: '' })
    load({ page: 1, filters: INITIAL_FILTERS })
    loadStats(INITIAL_FILTERS)
  }

  const toggleArchive = async (item: KbItem) => {
    try {
      await archiveHistory(item.id, { archived: !item.isArchived })
      message.success(item.isArchived ? '已取消沉淀' : '已沉淀为踩坑档案')
      if (detail?.id === item.id) patchDetail({ isArchived: !item.isArchived })
      load({ page })
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const openTagModal = (item: KbItem, mode: 'archive' | 'edit') => {
    setTagModal({ id: item.id, errorType: item.errorType, mode })
    setTagText(item.tags ?? '')
  }

  const saveTagModal = async () => {
    if (!tagModal) return
    setTagSaving(true)
    try {
      const tags = splitCsv(tagText).join(',')
      await archiveHistory(tagModal.id, { archived: true, tags })
      message.success(tagModal.mode === 'archive' ? '已沉淀为踩坑档案' : '标签已更新')
      if (detail?.id === tagModal.id) patchDetail({ tags: splitCsv(tags), isArchived: true })
      setTagModal(null)
      load({ page })
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setTagSaving(false)
    }
  }

  const renderItem = (item: KbItem) => {
    const techStackTags = splitCsv(item.techStack)
    const tags = splitCsv(item.tags)
    return (
      <List.Item
        actions={[
          <Button
            key="view"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetail(item.id, { tags: splitCsv(item.tags), isArchived: item.isArchived })}
          >
            详情
          </Button>,
          item.isArchived ? (
            <Popconfirm
              key="unarchive"
              title="取消沉淀？该档案将从踩坑知识库移除"
              onConfirm={() => toggleArchive(item)}
              okText="取消沉淀"
              cancelText="再想想"
            >
              <Button size="small" icon={<StarFilled />}>
                取消沉淀
              </Button>
            </Popconfirm>
          ) : (
            <Button
              key="archive"
              size="small"
              type="primary"
              ghost
              icon={<StarOutlined />}
              onClick={() => openTagModal(item, 'archive')}
            >
              沉淀
            </Button>
          ),
          item.isArchived && (
            <Button key="tags" size="small" icon={<EditOutlined />} onClick={() => openTagModal(item, 'edit')}>
              编辑标签
            </Button>
          ),
        ]}
      >
        <List.Item.Meta
          title={
            <Space wrap>
              <Typography.Text strong>{item.errorType}</Typography.Text>
              {item.isArchived && <Tag color="green">已沉淀</Tag>}
              {item.finalDiagnosis ? <Tag color="blue">已诊断</Tag> : <Tag>待回填</Tag>}
            </Space>
          }
          description={
            <>
              <div>{item.summary}</div>
              {techStackTags.length > 0 && (
                <div style={{ marginTop: 4 }}>
                  {techStackTags.map((s, i) => (
                    <Tag key={i} color="blue" style={{ marginRight: 4, marginBottom: 4 }}>
                      {s}
                    </Tag>
                  ))}
                </div>
              )}
              {tags.length > 0 && (
                <div style={{ marginTop: 4 }}>
                  {tags.map((s, i) => (
                    <Tag key={i} color="gold" style={{ marginRight: 4, marginBottom: 4 }}>
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
    )
  }

  return (
    <PageContainer>
      <Card
        title="📥 踩坑知识库（团队踩坑档案）"
        extra={
          <Space>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting === 'markdown'}
              onClick={() => download('markdown')}
            >
              Markdown
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting === 'csv'}
              onClick={() => download('csv')}
            >
              CSV
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            团队沉淀的真实踩坑档案，可按需检索、沉淀与打标签；与「异常速查」的内置离线卡片不同。
          </Typography.Text>

          <Segmented
            value={view}
            onChange={(v) => setView(v as 'list' | 'stats')}
            options={[
              { label: '📋 档案列表', value: 'list' },
              { label: '📊 统计概览', value: 'stats' },
            ]}
          />

          {view === 'stats' ? (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <Typography.Text type="secondary">统计范围：</Typography.Text>
                {activeFilters.length === 0 ? (
                  <Typography.Text type="secondary">全部档案</Typography.Text>
                ) : (
                  activeFilters.map((s) => (
                    <Tag key={s} style={{ marginInlineEnd: 4, marginBottom: 4 }}>
                      {s}
                    </Tag>
                  ))
                )}
                <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                  （在「档案列表」调整筛选后，统计随之变化）
                </Typography.Text>
              </div>

              {statsLoading ? (
                <Card size="small" loading style={{ minHeight: 300 }} />
              ) : stats === null ? (
                <Empty description="统计加载失败，请稍后重试" style={{ padding: 40 }} />
              ) : stats.total === 0 ? (
                <Empty description="当前筛选下暂无统计数据" style={{ padding: 40 }} />
              ) : (
                <>
                  {/* KPI 汇总行：总沉淀数 / 异常类型数 / 涉及语言数 / 近 30 天新增 */}
                  <Row gutter={[16, 16]}>
                    <Col xs={12} lg={6}>
                      <KpiCard icon={<DatabaseOutlined />} label="总沉淀数" value={stats.total} />
                    </Col>
                    <Col xs={12} lg={6}>
                      <KpiCard icon={<BugOutlined />} label="异常类型数" value={stats.errorTypeTop.length} color={token.colorWarning} />
                    </Col>
                    <Col xs={12} lg={6}>
                      <KpiCard icon={<GlobalOutlined />} label="涉及语言数" value={stats.languageDist.length} color={token.colorSuccess} />
                    </Col>
                    <Col xs={12} lg={6}>
                      <KpiCard icon={<LineChartOutlined />} label="近 30 天新增" value={trendTotal} color={token.colorInfo} />
                    </Col>
                  </Row>

                  {/* 主区：左侧大趋势图 + 右侧异常类型分布 */}
                  <Row gutter={[16, 16]}>
                    <Col xs={24} lg={16}>
                      <Card
                        title="📈 近 30 天新增趋势"
                        extra={
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            近 30 天共 {trendTotal} 条 · 共 {stats.total} 条档案
                          </Typography.Text>
                        }
                      >
                        <TrendChart trend={stats.trend} />
                      </Card>
                    </Col>
                    <Col xs={24} lg={8}>
                      <Card
                        title="🪲 异常类型分布"
                        extra={
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            共 {stats.errorTypeTop.length} 类
                          </Typography.Text>
                        }
                      >
                        <DistList items={stats.errorTypeTop.slice(0, 10)} />
                      </Card>
                    </Col>
                  </Row>

                  {/* 次区：语言 / 技术栈分布 */}
                  <Row gutter={[16, 16]}>
                    <Col xs={24} md={12}>
                      <Card title="🌐 语言分布">
                        <DistList items={stats.languageDist} />
                      </Card>
                    </Col>
                    <Col xs={24} md={12}>
                      <Card title="🧱 技术栈分布">
                        <DistList items={stats.techStackDist} />
                      </Card>
                    </Col>
                  </Row>
                </>
              )}
            </Space>
          ) : (
            <>
              {/* 筛选区 */}
              <Space wrap size={[8, 8]}>
                <Input.Search
                  placeholder="关键字：异常类型 / 概要 / 技术栈 / 诊断"
                  allowClear
                  style={{ width: 260 }}
                  value={drafts.keyword}
                  onChange={(e) => {
                    const v = e.target.value
                    setDrafts((prev) => ({ ...prev, keyword: v }))
                    if (v === '') commitText('keyword', '')
                  }}
                  onSearch={(v) => commitText('keyword', v)}
                />
                <Select
                  placeholder="语言"
                  allowClear
                  style={{ width: 110 }}
                  options={LANGUAGE_OPTIONS}
                  value={filters.language}
                  onChange={(v: string) => applyFilter('language', v || undefined)}
                />
                <Input.Search
                  placeholder="技术栈"
                  allowClear
                  style={{ width: 150 }}
                  value={drafts.techStack}
                  onChange={(e) => {
                    const v = e.target.value
                    setDrafts((prev) => ({ ...prev, techStack: v }))
                    if (v === '') commitText('techStack', '')
                  }}
                  onSearch={(v) => commitText('techStack', v)}
                />
                <Input.Search
                  placeholder="标签"
                  allowClear
                  style={{ width: 140 }}
                  value={drafts.tag}
                  onChange={(e) => {
                    const v = e.target.value
                    setDrafts((prev) => ({ ...prev, tag: v }))
                    if (v === '') commitText('tag', '')
                  }}
                  onSearch={(v) => commitText('tag', v)}
                />
                <DatePicker.RangePicker
                  value={filters.range}
                  onChange={(dates) => applyFilter('range', dates)}
                />
                <Segmented
                  value={filters.archived}
                  onChange={(v) => applyFilter('archived', v as ArchivedFilter)}
                  options={[
                    { label: '全部', value: 'all' },
                    { label: '已沉淀', value: 'true' },
                    { label: '未沉淀', value: 'false' },
                  ]}
                />
                <Button onClick={resetFilters}>重置</Button>
              </Space>

              {records.length === 0 && !loading ? (
                <Empty description="暂无踩坑档案，可在「历史」中沉淀记录" style={{ padding: 40 }} />
              ) : (
                <List loading={loading} dataSource={records} renderItem={renderItem} />
              )}

              {total > 0 && (
                <PaginationBar
                  total={total}
                  page={page}
                  pageSize={PAGE_SIZE}
                  onChange={(p) => load({ page: p })}
                />
              )}
            </>
          )}
        </Space>
      </Card>

      {/* 详情弹窗（居中 Modal，展示回填诊断列表） */}
      <DetailModal
        open={detail !== null}
        loading={detailLoading}
        detail={detail}
        onClose={closeDetail}
        titlePrefix="📥"
      />

      {/* 沉淀 / 编辑标签弹窗 */}
      <Modal
        title={tagModal?.mode === 'archive' ? '沉淀为踩坑档案' : '编辑踩坑标签'}
        open={tagModal !== null}
        onCancel={() => setTagModal(null)}
        onOk={saveTagModal}
        okText={tagModal?.mode === 'archive' ? '沉淀' : '保存'}
        cancelText="取消"
        confirmLoading={tagSaving}
      >
        <Typography.Paragraph type="secondary">
          {tagModal?.mode === 'archive'
            ? `将「${tagModal?.errorType}」沉淀为团队踩坑档案，可同时打上标签（可选）。`
            : `编辑「${tagModal?.errorType}」的踩坑标签。`}
        </Typography.Paragraph>
        <Input
          value={tagText}
          onChange={(e) => setTagText(e.target.value)}
          placeholder="逗号分隔，如 空指针, 并发, SQL"
        />
      </Modal>
    </PageContainer>
  )
}
