import { App as AntdApp, Button, Divider, Tag, Typography } from 'antd'
import { CopyOutlined } from '@ant-design/icons'
import type { Handoff, PersonReport } from '../types'

interface Props {
  person: PersonReport | undefined
  handoff: Handoff | undefined
}

/** 双段式报告正文：人话版 + 项目AI诊断包 + 复制按钮（主页卡片与历史详情共用） */
export default function ReportBody({ person, handoff }: Props) {
  const { message } = AntdApp.useApp()

  const copyHandoff = async () => {
    const text = [
      '【任务】请作为本项目的后端专家，结合仓库上下文诊断下面这个报错，给出根因与修复方案。',
      '',
      '【原始报错】',
      handoff?.rawError ?? '',
      '',
      '【前置预判 · 待你核实】',
      ...(handoff?.suspectedPoints ?? []).map((s) => `- 疑似：${s}`),
      `- 技术栈：${(handoff?.techStack ?? []).join(', ') || '未知'}`,
      '',
      '【请核查】',
      ...(handoff?.checkList ?? []).map((s, i) => `${i + 1}. ${s}`),
      '',
      '【输出】请给出：根因 + 具体修复方案 + 如何预防',
    ].join('\n')
    try {
      await navigator.clipboard.writeText(text)
      message.success('诊断包已复制，粘贴给项目 AI 即可')
    } catch {
      message.error('复制失败，请手动选择文本')
    }
  }

  return (
    <>
      <Typography.Paragraph>
        <b>🏷 出在哪一层</b>
        <br />
        {person?.layer || '—'}
      </Typography.Paragraph>

      <Typography.Paragraph>
        <b>⚠ 疑似技术使用不妥（方向，非断言）</b>
        <br />
        {person?.misuse || '—'}
      </Typography.Paragraph>

      <Typography.Paragraph>
        <b>👣 你现在可以怎么做</b>
        <br />
        {person?.nextStep || '—'}
      </Typography.Paragraph>

      <Divider />
      <Typography.Title level={5}>🤝 给项目 AI 的诊断包</Typography.Title>
      <div style={{ marginBottom: 12 }}>
        {(handoff?.suspectedPoints ?? []).map((s, i) => (
          <Tag key={i} color="orange" style={{ marginBottom: 4 }}>
            {s}
          </Tag>
        ))}
      </div>
      <Typography.Paragraph>
        <b>待查证项</b>
        <ol>
          {(handoff?.checkList ?? []).map((s, i) => (
            <li key={i}>{s}</li>
          ))}
        </ol>
      </Typography.Paragraph>
      <Button type="primary" icon={<CopyOutlined />} onClick={copyHandoff}>
        复制诊断包
      </Button>
    </>
  )
}
