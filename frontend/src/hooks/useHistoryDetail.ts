import { useCallback, useState } from 'react'
import { App as AntdApp } from 'antd'
import { fetchHistoryDetail } from '../services/api'
import type { DetailState, DiagnosisEntry, PreTriageReport } from '../types'

/** 逗号分隔字符串 → 去空格数组（后端 tech_stack / tags 的存储约定） */
export const splitCsv = (raw: string | null): string[] =>
  (raw ?? '').split(',').map((s) => s.trim()).filter(Boolean)

/** 打开详情时可携带的列表页元信息（知识库的标签/沉淀状态） */
export interface DetailOpenMeta {
  tags?: string[]
  isArchived?: boolean
}

/**
 * 历史 / 踩坑知识库共用的详情加载与状态管理。
 * 集中了 fetchHistoryDetail + preReport/finalDiagnosis 的 JSON.parse 解析与兜底结构，
 * 两页不再各自维护一套抽屉状态。
 */
export function useHistoryDetail() {
  const { message } = AntdApp.useApp()
  const [detail, setDetail] = useState<DetailState | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const openDetail = useCallback(
    async (id: number, meta: DetailOpenMeta = {}) => {
      setDetailLoading(true)
      setDetail(null)
      try {
        const d = await fetchHistoryDetail(id)
        let report: PreTriageReport | null = null
        try {
          report = JSON.parse(d.preReport || d.explanation)
        } catch {
          report = null
        }
        let entries: DiagnosisEntry[] = []
        try {
          entries = d.finalDiagnosis ? JSON.parse(d.finalDiagnosis) : []
        } catch {
          entries = []
        }
        setDetail({
          id: d.id,
          errorType: d.errorType,
          summary: d.summary,
          language: d.language || '未知',
          techStack: splitCsv(d.techStack),
          tags: meta.tags ?? [],
          isArchived: meta.isArchived ?? false,
          stackTrace: d.stackTrace,
          createdAt: d.createdAt,
          report:
            report ?? {
              personReport: { summary: d.summary, layer: '', misuse: '', nextStep: '' },
              handoff: { techStack: [], suspectedPoints: [], checkList: [], rawError: d.stackTrace },
            },
          entries,
        })
      } catch (e) {
        message.error((e as Error).message)
      } finally {
        setDetailLoading(false)
      }
    },
    [message],
  )

  /** 局部更新当前详情（回填后 entries、沉淀/打标签后 isArchived/tags） */
  const patchDetail = useCallback((patch: Partial<DetailState>) => {
    setDetail((prev) => (prev ? { ...prev, ...patch } : prev))
  }, [])

  const closeDetail = useCallback(() => setDetail(null), [])

  return { detail, detailLoading, openDetail, patchDetail, closeDetail }
}
