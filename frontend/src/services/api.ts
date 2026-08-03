import axios from 'axios'
import type {
  ApiResponse,
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

export async function explainStack(stackTrace: string): Promise<ExplainResponse> {
  const { data } = await api.post<ApiResponse<ExplainResponse>>('/explain', { stackTrace })
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

/** 历史详情：explanation 为 AI 解释 JSON 字符串 */
export interface HistoryDetail extends HistoryItem {
  stackTrace: string
  explanation: string
}

export async function fetchHistoryDetail(id: number): Promise<HistoryDetail> {
  const { data } = await api.get<ApiResponse<HistoryDetail>>(`/history/${id}`)
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
