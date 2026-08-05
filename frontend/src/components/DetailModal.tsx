import { Button, Divider, Empty, Input, List, Modal, Space, Tag, Typography } from 'antd'
import ReportBody from './ReportBody'
import type { DetailState } from '../types'

interface DetailModalProps {
  open: boolean
  loading: boolean
  detail: DetailState | null
  onClose: () => void
  /** 标题前缀：历史页 🔍、知识库 📥 */
  titlePrefix?: string
  // 历史页「追加诊断」区（知识库页不传则不渲染）
  appendText?: string
  onAppendTextChange?: (v: string) => void
  appending?: boolean
  onAppend?: () => void
}

/**
 * 历史 / 踩坑知识库共用的详情居中弹窗。
 * 字段完整（概要/语言/技术栈/标签/原始堆栈/回填诊断），正文区可滚动、宽度自适应移动端。
 */
export default function DetailModal({
  open,
  loading,
  detail,
  onClose,
  titlePrefix = '🔍',
  appendText,
  onAppendTextChange,
  appending,
  onAppend,
}: DetailModalProps) {
  return (
    <Modal
      title={`${titlePrefix} ${detail?.errorType ?? '详情'}`}
      centered
      width={720}
      open={open}
      onCancel={onClose}
      footer={
        <Button type="primary" onClick={onClose}>
          关闭
        </Button>
      }
      styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
    >
      {loading || !detail ? (
        <Empty description="加载中…" />
      ) : (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            {detail.isArchived && (
              <Tag color="green" style={{ marginRight: 4, marginBottom: 4 }}>
                已沉淀
              </Tag>
            )}
            <Tag color="purple" style={{ marginRight: 4, marginBottom: 4 }}>
              {detail.language}
            </Tag>
            {detail.techStack.map((s, i) => (
              <Tag key={i} color="blue" style={{ marginRight: 4, marginBottom: 4 }}>
                {s}
              </Tag>
            ))}
            {detail.tags.map((s, i) => (
              <Tag key={i} color="gold" style={{ marginRight: 4, marginBottom: 4 }}>
                {s}
              </Tag>
            ))}
            <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
              {detail.createdAt}
            </Typography.Text>
          </div>

          {detail.summary && (
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              <b>📝 概要</b>
              <br />
              {detail.summary}
            </Typography.Paragraph>
          )}

          <ReportBody person={detail.report.personReport} handoff={detail.report.handoff} />

          {detail.stackTrace && (
            <>
              <Divider style={{ margin: '8px 0' }} />
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                🧱 原始堆栈
              </Typography.Title>
              <pre className="cee-code-block" style={{ maxHeight: 240, margin: 0 }}>
                {detail.stackTrace}
              </pre>
            </>
          )}

          <Divider />
          <Typography.Title level={5}>🤝 项目 AI 诊断记录</Typography.Title>
          {detail.entries.length === 0 ? (
            <Typography.Text type="secondary">
              {onAppend ? '暂无回填，粘贴项目 AI 的结论到下方。' : '暂无回填诊断记录。'}
            </Typography.Text>
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
          {onAppend && (
            <>
              <Input.TextArea
                rows={3}
                placeholder="粘贴项目 AI 给出的最终诊断结论…"
                value={appendText}
                onChange={(e) => onAppendTextChange?.(e.target.value)}
              />
              <Button
                type="primary"
                loading={appending}
                onClick={onAppend}
                style={{ alignSelf: 'flex-end' }}
              >
                追加诊断
              </Button>
            </>
          )}
        </Space>
      )}
    </Modal>
  )
}
