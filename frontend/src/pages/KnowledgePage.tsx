import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Empty, Input, Space, Tag, Typography } from 'antd'
import { fetchTemplates } from '../services/api'
import type { ErrorTemplate } from '../types'

export default function KnowledgePage() {
  const { message } = AntdApp.useApp()
  const [templates, setTemplates] = useState<ErrorTemplate[]>([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async (kw = keyword) => {
    setLoading(true)
    try {
      const list = await fetchTemplates(kw || undefined)
      setTemplates(list)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load('')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const parseCauses = (t: ErrorTemplate): string[] => {
    try {
      const arr = t.causeTop3 ? JSON.parse(t.causeTop3) : []
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  }

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Card title="📚 异常速查（内置知识卡片 · 离线可用）">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Input.Search
            placeholder="搜索异常类型 / 分类"
            allowClear
            loading={loading}
            onSearch={(v) => {
              setKeyword(v)
              load(v)
            }}
          />

          {templates.length === 0 && !loading ? (
            <Empty description="暂无卡片" style={{ padding: 40 }} />
          ) : (
            templates.map((t) => (
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
                  <pre
                    style={{
                      whiteSpace: 'pre-wrap',
                      margin: '4px 0',
                      fontSize: 12,
                      background: 'rgba(0,0,0,0.03)',
                      padding: 8,
                      borderRadius: 4,
                    }}
                  >
                    {t.fixTemplate}
                  </pre>
                </Typography.Paragraph>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  继承关系：{t.hierarchy || '—'}
                </Typography.Text>
              </Card>
            ))
          )}
        </Space>
      </Card>
    </div>
  )
}
