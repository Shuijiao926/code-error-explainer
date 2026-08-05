import axios from 'axios'
import type {
  AiCallLog,
  ApiResponse,
  ArchiveRequest,
  DiagnosisEntry,
  ErrorTemplate,
  ExplainResponse,
  HistoryItem,
  KbItem,
  KbStats,
  PageResult,
} from '../types'

const api = axios.create({ baseURL: '/api', timeout: 60000 })

function ensureOk<T>(data: ApiResponse<T>): T {
  if (data.code !== 200) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

export interface ExplainRequest {
  stackTrace: string
}

export async function explainStack(req: ExplainRequest): Promise<ExplainResponse> {
  const { data } = await api.post<ApiResponse<ExplainResponse>>('/explain', req)
  return ensureOk(data)
}

export type HistoryStatus = 'all' | 'pending' | 'diagnosed'

export async function fetchHistory(params: {
  page: number
  size: number
  keyword?: string
  status?: HistoryStatus
}): Promise<PageResult<HistoryItem>> {
  const { data } = await api.get<ApiResponse<PageResult<HistoryItem>>>('/history', { params })
  return ensureOk(data)
}

/** 历史详情：preReport 为分诊报告 JSON 字符串 */
export interface HistoryDetail extends HistoryItem {
  language: string
  stackTrace: string
  preReport: string
  explanation: string
}

export async function fetchHistoryDetail(id: number): Promise<HistoryDetail> {
  const { data } = await api.get<ApiResponse<HistoryDetail>>(`/history/${id}`)
  return ensureOk(data)
}

/** 回填/追加项目 AI 的最终诊断，返回追加后的完整记录 */
export async function backfillDiagnosis(id: number, diagnosis: string): Promise<DiagnosisEntry[]> {
  const { data } = await api.put<ApiResponse<DiagnosisEntry[]>>(`/history/${id}/diagnosis`, { diagnosis })
  return ensureOk(data)
}

export async function removeHistory(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/history/${id}`)
  ensureOk(data)
}

export async function clearHistoryAll(): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>('/history')
  ensureOk(data)
}

/** 内置异常知识卡片（离线速查） */
export async function fetchTemplates(keyword?: string): Promise<ErrorTemplate[]> {
  const { data } = await api.get<ApiResponse<ErrorTemplate[]>>('/templates', { params: { keyword } })
  return ensureOk(data)
}

/** 踩坑知识库检索参数（GET /api/kb，与后端契约一一对应） */
export interface KbQuery {
  page: number
  size: number
  keyword?: string
  language?: string
  techStack?: string
  archived?: boolean
  startTime?: string
  endTime?: string
  tag?: string
}

/** 踩坑知识库分页检索（GET /api/kb） */
export async function fetchKb(params: KbQuery): Promise<PageResult<KbItem>> {
  const { data } = await api.get<ApiResponse<PageResult<KbItem>>>('/kb', { params })
  return ensureOk(data)
}

/** 沉淀/取消沉淀为踩坑档案，并可选更新标签（PUT /api/history/{id}/archive） */
export async function archiveHistory(id: number, req: ArchiveRequest): Promise<KbItem> {
  const { data } = await api.put<ApiResponse<KbItem>>(`/history/${id}/archive`, req)
  return ensureOk(data)
}

/** AI 调用日志（分页/筛选） */
export async function fetchAiLogs(params: {
  page: number
  size: number
  status?: string
  provider?: string
  historyId?: number
}): Promise<PageResult<AiCallLog>> {
  const { data } = await api.get<ApiResponse<PageResult<AiCallLog>>>('/ai-logs', { params })
  return ensureOk(data)
}

/** 知识库统计查询参数（GET /api/kb/stats，与 /api/kb 筛选口径一致） */
export interface KbStatsQuery {
  archived?: boolean
  keyword?: string
  language?: string
  techStack?: string
  startTime?: string
  endTime?: string
  tag?: string
  topN?: number
  days?: number
}

/** 踩坑知识库统计（GET /api/kb/stats） */
export async function fetchKbStats(params: KbStatsQuery): Promise<KbStats> {
  const { data } = await api.get<ApiResponse<KbStats>>('/kb/stats', { params })
  return ensureOk(data)
}

export type KbExportFormat = 'markdown' | 'csv'

export interface KbExportResult {
  blob: Blob
  /** 服务端 Content-Disposition 提供的文件名 */
  filename: string
}

/** 踩坑知识库导出（GET /api/kb/export），返回 Blob 供浏览器下载 */
export async function exportKb(format: KbExportFormat, params: KbQuery): Promise<KbExportResult> {
  const res = await api.get<Blob>('/kb/export', {
    params: { ...params, format },
    responseType: 'blob',
  })
  // 后端业务异常会以 JSON 返回（此时 content-type 为 application/json），解析后抛出
  const contentType = res.headers['content-type'] as string | undefined
  if (contentType?.includes('application/json')) {
    let msg = '导出失败'
    try {
      const parsed = JSON.parse(await res.data.text())
      if (parsed?.message) msg = parsed.message
    } catch {
      /* 保持默认文案 */
    }
    throw new Error(msg)
  }
  const disposition = res.headers['content-disposition'] as string | undefined
  const matched = disposition?.match(/filename="?([^"]+)"?/)
  const fallback = `kb-export-${new Date().toISOString().slice(0, 10)}.${format === 'csv' ? 'csv' : 'md'}`
  return { blob: res.data, filename: matched?.[1] ?? fallback }
}
