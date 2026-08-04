// 与后端 DTO 对齐的类型定义（单一数据契约来源）

export interface PersonReport {
  summary: string
  layer: string
  misuse: string
  nextStep: string
}

export interface Handoff {
  techStack: string[]
  suspectedPoints: string[]
  checkList: string[]
  rawError: string
}

export interface PreTriageReport {
  personReport: PersonReport
  handoff: Handoff
}

export interface ExplainResponse {
  id: number
  errorType: string
  language: string
  techStack: string[]
  summary: string
  report: PreTriageReport
  createdAt: string
}

export interface HistoryItem {
  id: number
  errorType: string
  techStack: string | null
  summary: string
  finalDiagnosis: string | null
  createdAt: string
}

/** 一条项目 AI 最终诊断记录（多条追加） */
export interface DiagnosisEntry {
  ts: string
  content: string
}

/** AI 调用日志 */
export interface AiCallLog {
  id: number
  historyId: number | null
  provider: string | null
  model: string | null
  status: string | null
  durationMs: number | null
  promptTokens: number | null
  completionTokens: number | null
  inputExcerpt: string | null
  errorMessage: string | null
  createdAt: string
}

/** 内置异常知识卡片 */
export interface ErrorTemplate {
  id: number
  errorType: string
  category: string | null
  causeTop3: string | null
  fixTemplate: string | null
  hierarchy: string | null
  createdAt: string
  updatedAt: string
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
