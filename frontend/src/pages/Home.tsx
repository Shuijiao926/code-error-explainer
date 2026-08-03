import { useEffect, useRef, useState } from 'react'
import { Alert, App as AntdApp, Button, Card, Empty, Input, Select, Space } from 'antd'
import ExplanationCard from '../components/ExplanationCard'
import { detectStack, explainStack } from '../services/api'
import type { ExplainResponse } from '../types'

const { TextArea } = Input

const TECH_STACK_OPTIONS = [
  'Spring Boot', 'Spring', 'MyBatis', 'JPA/Hibernate', 'Jackson',
  'Tomcat', 'Netty', 'MySQL/JDBC', 'JDBC', 'OkHttp', 'Apache HttpClient',
  'Redis', '通用/未知',
].map((v) => ({ value: v, label: v }))

export default function Home() {
  const { message } = AntdApp.useApp()
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [detecting, setDetecting] = useState(false)
  const [techStack, setTechStack] = useState<string[]>([])
  const [result, setResult] = useState<ExplainResponse | null>(null)
  const [error, setError] = useState('')
  const userEdited = useRef(false)

  // 监听历史抽屉「回看」事件，把某条历史重新渲染成结果卡片
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<ExplainResponse>).detail
      setResult(detail)
      setError('')
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
    window.addEventListener('history:view', handler)
    return () => window.removeEventListener('history:view', handler)
  }, [])

  // 防抖自动识别技术栈（用户手动改过后不再覆盖）
  useEffect(() => {
    const stack = input.trim()
    if (!stack) return
    setDetecting(true)
    const timer = setTimeout(async () => {
      try {
        const detected = await detectStack(stack)
        if (!userEdited.current) {
          setTechStack(detected)
        }
      } catch {
        /* 识别失败忽略，交给后端在 explain 时处理 */
      } finally {
        setDetecting(false)
      }
    }, 600)
    return () => clearTimeout(timer)
  }, [input])

  const handleExplain = async () => {
    const stack = input.trim()
    if (!stack) {
      message.warning('请先粘贴异常堆栈')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await explainStack({ stackTrace: stack, techStack })
      setResult(res)
      userEdited.current = false
    } catch (e) {
      setError((e as Error).message || '生成失败，请稍后重试')
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card title="📥 粘贴 Java 异常堆栈" size="small">
          <TextArea
            rows={8}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={
              'java.lang.NullPointerException: Cannot invoke "com.app.UserRepository.findById(Object)" because "this.userRepository" is null\n' +
              '\tat com.app.UserService.login(UserService.java:47)\n' +
              '\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor...'
            }
            style={{ fontFamily: 'Consolas, Monaco, monospace' }}
          />
          <div style={{ marginTop: 16 }}>
            <div style={{ marginBottom: 6, color: 'rgba(0,0,0,0.45)', fontSize: 13 }}>
              技术栈（自动识别，可修正）
              {detecting && <span style={{ marginLeft: 8 }}>识别中…</span>}
            </div>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="自动识别结果将出现在这里；也可手动选择/输入（逗号分隔）"
              options={TECH_STACK_OPTIONS}
              value={techStack}
              onChange={(v: string[]) => {
                userEdited.current = true
                setTechStack(v)
              }}
              tokenSeparators={[',']}
            />
          </div>
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button type="primary" size="large" loading={loading} onClick={handleExplain}>
              🔍 生成前置分诊报告
            </Button>
          </div>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {!result && !error && (
          <Empty
            description="粘贴堆栈，生成「前置分诊报告」——非 IT 也看得懂，还能复制给项目 AI 做最终诊断"
            style={{ padding: 40 }}
          />
        )}

        {result && <ExplanationCard result={result} />}
      </Space>
    </div>
  )
}
