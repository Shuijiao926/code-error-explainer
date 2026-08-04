# V1.4 前端页面重构 + AI 调用可观测（父任务）

## Goal & User Value

1. **独立页面**：把「异常速查（离线知识）」「历史」从 Drawer 抽屉升级为**独立页面**，配合导航，让功能可被直接书签/分享，承载后续更多页面。
2. **AI 调用可观测**：用 `ai_call_log` 表记录每次 AI 请求（provider/model/耗时/状态/token/输入摘要/错误），并提供前端查看页面，便于排查"AI 到底调用没调用、多久、花多少 token"。

## Confirmed Facts

- 用户确认三个方向，其中"当前 AI 是否真实"经调查**不改代码**：
  - 未用 SpringAI Alibaba；`OpenAiCompatAIService` 手写 RestClient 直连 `/chat/completions`（OpenAI 兼容，默认 DeepSeek）。
  - `AI_API_KEY` 为空时走 `MockAIService`（纯模板假 AI）；设环境变量即切真实调用。
- 用户确认：**Trellis 组织 = 父任务 + 2 子任务**；日志**要前端查看页面**。
- 现状：前端无路由（`App.tsx` 单页 + 两个 Drawer）；dev 端口已改 3000（vite.config.ts），CORS 已放行 3000。
- 技术栈沿用：Spring Boot 4 + MyBatis-Plus 后端；Vite5 + React18 + antd5 前端。

## Task Map

- 子任务 `v14-pages`：前端引入 react-router，把异常速查/历史抽成独立页面，建 `/logs` 路由框架。
- 子任务 `v14-ai-call-log`：`ai_call_log` 表 + 后端记录/查询接口 + 前端 `/logs` 页面。
- 顺序：先 `v14-pages`（建路由），后 `v14-ai-call-log`（依赖其 `/logs` 路由）。两子任务各自独立验收。

## Cross-Child Acceptance Criteria

- [ ] 前端有 `/`、`/knowledge`、`/history`、`/logs` 四个路由，导航高亮正确，`npm run build`（含 tsc）通过。
- [ ] 每次 AI 调用（Mock 与真实）都在 `ai_call_log` 落库；`/api/ai-logs` 可分页查询；`/logs` 页面正常展示。
- [ ] 后端 `mvn test` 全绿（含新增 ai_call_log 断言）；原有分诊/历史/知识卡功能不回归。
- [ ] 两个子任务分别经 Trellis 验收后归档，父任务做最终集成检查后归档。

## Out of Scope

- 引入 Spring AI Alibaba 或替换 AI 调用方式。
- 认证/多用户/权限。
- AI 日志告警、统计报表（仅存储 + 列表查看）。
