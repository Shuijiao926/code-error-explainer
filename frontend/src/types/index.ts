// 与后端 DTO 对齐的类型定义（单一数据契约来源）

export interface Explanation {
  overview: string
  location: string
  rootCause: string
  fixSteps: string[]
  prevention: string
}

export interface ExplainResponse {
  id: number
  errorType: string
  summary: string
  explanation: Explanation
  createdAt: string
}

export interface HistoryItem {
  id: number
  errorType: string
  summary: string
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
