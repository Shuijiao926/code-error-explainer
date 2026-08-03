import { useState } from 'react'
import { Alert, App as AntdApp, Button, Card, Empty, Input, Space } from 'antd'
import ExplanationCard from '../components/ExplanationCard'
import { explainStack } from '../services/api'
import type { ExplainResponse } from '../types'

const { TextArea } = Input

const SAMPLE_STACK =
  'java.lang.NullPointerException: Cannot invoke "com.example.app.UserRepository.findByUsername(String)" because "this.userRepository" is null\n' +
  '\tat com.example.app.service.UserService.login(UserService.java:47)\n' +
  '\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(AutowiredAnnotationBeanPostProcessor.java:376)'

export default function Home() {
  const { message } = AntdApp.useApp()
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ExplainResponse | null>(null)
  const [error, setError] = useState('')

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
          {input.trim() && !looksLikeJavaStack && (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 12 }}
              message="这看起来不像 Java 异常堆栈，仍可继续尝试"
            />
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
      </Space>
    </div>
  )
}
