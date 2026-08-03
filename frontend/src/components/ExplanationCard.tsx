import { App as AntdApp, Button, Card, Divider, Typography } from 'antd'
import { CopyOutlined } from '@ant-design/icons'
import type { ExplainResponse } from '../types'

interface Props {
  result: ExplainResponse
}

export default function ExplanationCard({ result }: Props) {
  const { message } = AntdApp.useApp()
  const { errorType, summary, explanation, createdAt } = result

  const copyText = async () => {
    const lines = [
      `🌍 概述：${explanation.overview}`,
      `📍 位置：${explanation.location}`,
      `🎯 根因：${explanation.rootCause}`,
      '🔧 修复步骤：',
      ...(explanation.fixSteps ?? []).map((s, i) => `${i + 1}. ${s}`),
      `💡 预防：${explanation.prevention}`,
    ].join('\n')
    try {
      await navigator.clipboard.writeText(lines)
      message.success('解释已复制到剪贴板')
    } catch {
      message.error('复制失败，请手动选择文本')
    }
  }

  return (
    <Card
      title={<span>🌍 {summary}</span>}
      extra={
        <Button icon={<CopyOutlined />} onClick={copyText}>
          复制解释
        </Button>
      }
    >
      <Typography.Text type="secondary">
        异常类型：{errorType} · {createdAt}
      </Typography.Text>
      <Divider style={{ margin: '12px 0' }} />

      <Typography.Paragraph>
        <b>📍 出问题的地方</b>
        <br />
        {explanation.location || '—'}
      </Typography.Paragraph>

      <Typography.Paragraph>
        <b>🎯 根因</b>
        <br />
        {explanation.rootCause || '—'}
      </Typography.Paragraph>

      <Typography.Paragraph>
        <b>🔧 修复建议</b>
        <ol>
          {(explanation.fixSteps ?? []).map((s, i) => (
            <li key={i}>{s}</li>
          ))}
        </ol>
      </Typography.Paragraph>

      <Typography.Paragraph>
        <b>💡 以后怎么避免</b>
        <br />
        {explanation.prevention || '—'}
      </Typography.Paragraph>
    </Card>
  )
}
