import { Card, Divider, Typography } from 'antd'
import ReportBody from './ReportBody'
import type { ExplainResponse } from '../types'

interface Props {
  result: ExplainResponse
}

/** 主页结果卡片：摘要 + 双段式报告正文 */
export default function ExplanationCard({ result }: Props) {
  const { errorType, techStack, summary, report, createdAt } = result

  return (
    <Card title={<span>🧭 {summary}</span>}>
      <Typography.Text type="secondary">
        异常类型：{errorType} · 技术栈：{techStack.join(', ') || '未知'} · {createdAt}
      </Typography.Text>
      <Divider style={{ margin: '12px 0' }} />
      <ReportBody person={report?.personReport} handoff={report?.handoff} />
    </Card>
  )
}
