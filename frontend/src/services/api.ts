import axios from 'axios'
import type {
  ApiResponse,
  DiagnosisEntry,
  ExplainResponse,
  HistoryItem,
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
  techStack?: string[]
}

export async function explainStack(req: ExplainRequest): Promise<ExplainResponse> {
  const { data } = await api.post<ApiResponse<ExplainResponse>>('/explain', req)
  return ensureOk(data)
}

/** 技术栈自动识别（预填选择器用，不调用 AI） */
export async function detectStack(stackTrace: string): Promise<string[]> {
  const { data } = await api.post<ApiResponse<string[]>>('/detect', { stackTrace })
  return ensureOk(data)
}

export async function fetchHistory(params: {
  page: number
  size: number
  keyword?: string
}): Promise<PageResult<HistoryItem>> {
  const { data } = await api.get<ApiResponse<PageResult<HistoryItem>>>('/history', { params })
  return ensureOk(data)
}

/** 历史详情：preReport 为分诊报告 JSON 字符串 */
export interface HistoryDetail extends HistoryItem {
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
