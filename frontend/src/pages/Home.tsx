import { useEffect, useState } from 'react'
import { Alert, App as AntdApp, Button, Card, Empty, Input, Space } from 'antd'
import ExplanationCard from '../components/ExplanationCard'
import { explainStack } from '../services/api'
import type { ExplainResponse } from '../types'

const { TextArea } = Input

export default function Home() {
  const { message } = AntdApp.useApp()
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ExplainResponse | null>(null)
  const [error, setError] = useState('')

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

  const handleExplain = async () => {
    const stack = input.trim()
    if (!stack) {
      message.warning('请先粘贴异常堆栈')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await explainStack(stack)
      setResult(res)
    } catch (e) {
      setError((e as Error).message || '翻译失败，请稍后重试')
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
              '\tat com.app.AuthController.login(AuthController.java:12)'
            }
            style={{ fontFamily: 'Consolas, Monaco, monospace' }}
          />
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button type="primary" size="large" loading={loading} onClick={handleExplain}>
              🔍 翻译成人话
            </Button>
          </div>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {!result && !error && (
          <Empty description="粘贴堆栈，点击「翻译成人话」，AI 会给你一份大白话解释" style={{ padding: 40 }} />
        )}

        {result && <ExplanationCard result={result} />}
      </Space>
    </div>
  )
}
