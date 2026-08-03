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
