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

/** 历史 / 踩坑知识库详情弹窗的展示态（前端聚合，非后端 DTO） */
export interface DetailState {
  id: number
  errorType: string
  /** 一句话概要（对非技术读者友好） */
  summary: string
  language: string
  techStack: string[]
  tags: string[]
  isArchived: boolean
  /** 原始异常堆栈（可长文本，弹窗内滚动查看） */
  stackTrace: string
  createdAt: string
  report: PreTriageReport
  entries: DiagnosisEntry[]
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

/** 踩坑知识库列表项（GET /api/kb） */
export interface KbItem {
  id: number
  errorType: string
  language: string | null
  techStack: string | null
  summary: string
  tags: string | null
  isArchived: boolean
  createdAt: string
  /** "[]" 表示有回填诊断（前端 truthy 判「已诊断」），null 表示无 */
  finalDiagnosis: string | null
}

/** PUT /api/history/{id}/archive 请求体：沉淀/取消沉淀为踩坑档案，并可选更新标签 */
export interface ArchiveRequest {
  archived: boolean
  /** 逗号分隔标签；不传保持原值，传空串清空 */
  tags?: string
}

/** 统计分布项（异常类型/语言/技术栈分布通用） */
export interface NameCount {
  name: string
  count: number
}

/** 趋势点：某天新增数量（date 为 yyyy-MM-dd） */
export interface TrendPoint {
  date: string
  count: number
}

/** GET /api/kb/stats 统计响应 */
export interface KbStats {
  /** 命中筛选条件的记录总数 */
  total: number
  /** 异常类型 TopN（默认 10） */
  errorTypeTop: NameCount[]
  /** 按 language 分布 */
  languageDist: NameCount[]
  /** 按 tech_stack 分布（逗号分隔拆开后逐项计数） */
  techStackDist: NameCount[]
  /** 近 N 天新增趋势（默认近 30 天，零值天已补齐） */
  trend: TrendPoint[]
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
