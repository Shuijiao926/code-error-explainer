import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Skeleton,
  Space,
  Steps,
  Tag,
  Typography,
} from 'antd'
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  FileSearchOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import ExplanationCard from '../components/ExplanationCard'
import PageContainer from '../components/PageContainer'
import { explainStack, fetchHistory, fetchTemplates } from '../services/api'
import type { ErrorTemplate, ExplainResponse, HistoryItem } from '../types'

const { TextArea } = Input

const SAMPLE_STACK =
  'java.lang.NullPointerException: Cannot invoke "com.example.app.UserRepository.findByUsername(String)" because "this.userRepository" is null\n' +
  '\tat com.example.app.service.UserService.login(UserService.java:47)\n' +
  '\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(AutowiredAnnotationBeanPostProcessor.java:376)'

export default function Home() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ExplainResponse | null>(null)
  const [error, setError] = useState('')

  // 底部增强区：常见异常速查前 6 条 + 最近记录前 3 条（复用现有接口，失败静默降级）
  const [templates, setTemplates] = useState<ErrorTemplate[]>([])
  const [templatesLoading, setTemplatesLoading] = useState(true)
  const [recent, setRecent] = useState<HistoryItem[]>([])
  const [recentLoading, setRecentLoading] = useState(true)

  const looksLikeJavaStack = /(Exception|Error|Caused by|^\s*at\s)/m.test(input)

  const handleExplain = async () => {
    const stack = input.trim()
    if (!stack) {
      message.warning('请先粘贴异常堆栈')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await explainStack({ stackTrace: stack })
      setResult(res)
    } catch (e) {
      setError((e as Error).message || '生成失败，请稍后重试')
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  const fillSample = () => {
    setInput(SAMPLE_STACK)
    setResult(null)
    setError('')
  }

  useEffect(() => {
    let alive = true
    fetchTemplates()
      .then((list) => {
        if (alive) setTemplates((list || []).slice(0, 6))
      })
      .catch(() => {
        if (alive) setTemplates([])
      })
      .finally(() => {
        if (alive) setTemplatesLoading(false)
      })
    fetchHistory({ page: 1, size: 3 })
      .then((res) => {
        if (alive) setRecent(res?.records || [])
      })
      .catch(() => {
        if (alive) setRecent([])
      })
      .finally(() => {
        if (alive) setRecentLoading(false)
      })
    return () => {
      alive = false
    }
  }, [])

  // 取模板卡片的常见原因首条作副标题
  const templateLead = (t: ErrorTemplate): string => {
    try {
      const arr = t.causeTop3 ? JSON.parse(t.causeTop3) : []
      return Array.isArray(arr) && arr.length > 0 ? String(arr[0]) : '点击查看常见原因与修复模板'
    } catch {
      return '点击查看常见原因与修复模板'
    }
  }

  return (
    <PageContainer>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card title="📥 粘贴 Java 异常堆栈" size="small">
          <TextArea
            rows={8}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={
              'java.lang.NullPointerException: Cannot invoke "com.app.UserRepository.findById(Object)" because "this.userRepository" is null\n' +
              '\tat com.app.UserService.login(UserService.java:47)\n' +
              '\tat com.app.AuthController.login(AuthController.java:12)'
            }
            style={{ fontFamily: 'Consolas, Monaco, monospace' }}
          />
          {input.trim() && (
            <div style={{ marginTop: 12 }}>
              {looksLikeJavaStack ? (
                <Tag color="green" icon={<CheckCircleOutlined />} style={{ padding: '2px 8px' }}>
                  检测到 Java 异常堆栈，可直接生成
                </Tag>
              ) : (
                <Tag color="orange" icon={<WarningOutlined />} style={{ padding: '2px 8px' }}>
                  未检测到 Java 异常堆栈，可能不是程序报错
                </Tag>
              )}
            </div>
          )}
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button type="primary" size="large" loading={loading} onClick={handleExplain}>
              🔍 生成前置分诊报告
            </Button>
            {loading && (
              <span style={{ marginLeft: 12, color: 'rgba(0,0,0,0.45)' }}>
                AI 分诊中，通常需要 5-30 秒…
              </span>
            )}
          </div>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {!result && !error && (
          <Empty
            style={{ padding: 40 }}
            description={
              <>
                粘贴堆栈生成「前置分诊报告」——非 IT 也看得懂，还能复制给项目 AI 做最终诊断。
                <Button type="link" onClick={fillSample}>
                  试试示例堆栈
                </Button>
              </>
            }
          />
        )}

        {result && <ExplanationCard result={result} />}

        <Card
          title="📚 常见异常速查"
          size="small"
          extra={
            <Button type="link" size="small" onClick={() => navigate('/knowledge')}>
              查看全部 <ArrowRightOutlined />
            </Button>
          }
        >
          {templatesLoading ? (
            <Skeleton active paragraph={{ rows: 2 }} />
          ) : templates.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内置卡片" style={{ padding: 16 }} />
          ) : (
            <Row gutter={[12, 12]}>
              {templates.map((t) => (
                <Col key={t.id} xs={24} sm={12} md={8}>
                  <Card size="small" hoverable onClick={() => navigate(`/knowledge?keyword=${encodeURIComponent(t.errorType)}`)}>
                    <Space direction="vertical" size={2} style={{ width: '100%' }}>
                      <Space size={6} wrap>
                        <Typography.Text strong>{t.errorType}</Typography.Text>
                        {t.category && (
                          <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                            {t.category}
                          </Tag>
                        )}
                      </Space>
                      <Typography.Text
                        type="secondary"
                        style={{ fontSize: 12 }}
                        ellipsis={{ tooltip: templateLead(t) }}
                      >
                        {templateLead(t)}
                      </Typography.Text>
                    </Space>
                  </Card>
                </Col>
              ))}
            </Row>
          )}
        </Card>

        <Card title="🚀 三步使用引导" size="small">
          <Steps
            current={-1}
            items={[
              {
                title: '贴堆栈',
                description: '把报错堆栈粘贴到上方输入框，点击「生成前置分诊报告」',
                icon: <FileSearchOutlined />,
              },
              {
                title: '看人话报告',
                description: '生成「人话版」前置分诊报告，非 IT 也看得懂问题在哪、该找谁',
                icon: <CheckCircleOutlined />,
              },
              {
                title: '复制诊断包给项目 AI',
                description: '复制 handoff 诊断包，交给项目 AI 做最终诊断与修复建议',
                icon: <CopyOutlined />,
              },
            ]}
          />
        </Card>

        <Card
          title="🕘 最近记录"
          size="small"
          extra={
            <Button type="link" size="small" onClick={() => navigate('/history')}>
              查看全部 <ArrowRightOutlined />
            </Button>
          }
        >
          {recentLoading ? (
            <Skeleton active paragraph={{ rows: 1 }} />
          ) : recent.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无诊断记录" style={{ padding: 16 }} />
          ) : (
            <Row gutter={[12, 12]}>
              {recent.map((item) => (
                <Col key={item.id} xs={24} md={8}>
                  <Card size="small" hoverable onClick={() => navigate('/history')}>
                    <Space direction="vertical" size={2} style={{ width: '100%' }}>
                      <Typography.Text strong ellipsis>
                        {item.errorType}
                      </Typography.Text>
                      <Typography.Paragraph
                        type="secondary"
                        ellipsis={{ rows: 1 }}
                        style={{ marginBottom: 0, fontSize: 12 }}
                      >
                        {item.summary}
                      </Typography.Paragraph>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.createdAt}
                      </Typography.Text>
                    </Space>
                  </Card>
                </Col>
              ))}
            </Row>
          )}
        </Card>
      </Space>
    </PageContainer>
  )
}
