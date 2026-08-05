import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { App as AntdApp, Card, Empty, Input, Skeleton, Space, Tag, Typography } from 'antd'
import { fetchTemplates } from '../services/api'
import type { ErrorTemplate } from '../types'
import PaginationBar from '../components/PaginationBar'
import PageContainer from '../components/PageContainer'

const PAGE_SIZE = 10

export default function KnowledgePage() {
  const { message } = AntdApp.useApp()
  const [searchParams] = useSearchParams()
  const [templates, setTemplates] = useState<ErrorTemplate[]>([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)

  const load = async (kw = keyword) => {
    setLoading(true)
    try {
      const list = await fetchTemplates(kw || undefined)
      setTemplates(list)
      setPage(1)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    // 支持首页「常见异常速查」卡片跳转：/knowledge?keyword=NullPointerException 时预填搜索
    const kw = searchParams.get('keyword') || ''
    setKeyword(kw)
    load(kw)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 数据量变化（如搜索结果变少）时，当前页越界自动回退到最后一页
  const totalPages = Math.max(1, Math.ceil(templates.length / PAGE_SIZE))
  useEffect(() => {
    if (page > totalPages) setPage(totalPages)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templates.length, totalPages])

  const visibleTemplates = templates.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  const parseCauses = (t: ErrorTemplate): string[] => {
    try {
      const arr = t.causeTop3 ? JSON.parse(t.causeTop3) : []
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  }

  return (
    <PageContainer>
      <Card title="📚 异常速查（内置知识卡片 · 离线可用）">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Input.Search
            placeholder="搜索异常类型 / 分类"
            allowClear
            loading={loading}
            onChange={(e) => {
              // 清空搜索框即恢复全量并回到第一页
              if (!e.target.value) {
                setKeyword('')
                load('')
              }
            }}
            onSearch={(v) => {
              setKeyword(v)
              load(v)
            }}
          />

          {loading && templates.length === 0 ? (
            <Skeleton active paragraph={{ rows: 5 }} />
          ) : templates.length === 0 ? (
            <Empty description="暂无卡片" style={{ padding: 40 }} />
          ) : (
            <>
              {visibleTemplates.map((t) => (
                <Card
                  key={t.id}
                  size="small"
                  title={
                    <Space>
                      <span>{t.errorType}</span>
                      {t.category && <Tag color="blue">{t.category}</Tag>}
                    </Space>
                  }
                >
                  <Typography.Paragraph style={{ marginBottom: 8 }}>
                    <b>🧭 常见原因</b>
                    <ol style={{ margin: '4px 0', paddingLeft: 20 }}>
                      {parseCauses(t).map((c, i) => (
                        <li key={i}>{c}</li>
                      ))}
                    </ol>
                  </Typography.Paragraph>
                  <Typography.Paragraph style={{ marginBottom: 8 }}>
                    <b>🔧 修复模板</b>
                    <pre className="cee-code-block">{t.fixTemplate}</pre>
                  </Typography.Paragraph>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    继承关系：{t.hierarchy || '—'}
                  </Typography.Text>
                </Card>
              ))}

              <PaginationBar
                total={templates.length}
                page={page}
                pageSize={PAGE_SIZE}
                onChange={(p) => setPage(p)}
              />
            </>
          )}
        </Space>
      </Card>
    </PageContainer>
  )
}
