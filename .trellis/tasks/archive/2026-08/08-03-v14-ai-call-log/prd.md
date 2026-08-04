# AI 调用日志（表 + 接口 + 页面）

## Goal & User Value

让每次 AI 调用**可观测**：以 `ai_call_log` 表记录 provider/model/耗时/状态/token/输入摘要/错误，并提供 `GET /api/ai-logs` 分页查询接口与前端 `/logs` 页面，回答"AI 到底调没调、多久、花多少 token、有没有降级"。

## Requirements

### R1 表结构
- 新增幂等脚本 `sql/migrate-v14.sql` 建 `ai_call_log` 表：
  `id / history_id / provider / model / status(success|fallback) / duration_ms / prompt_tokens / completion_tokens / input_excerpt(前200字符) / error_message / created_at`，索引 `idx_history_id`、`idx_created_at`。
- `src/test/resources/schema-test.sql` 同步 H2 建表（保证集成测试可跑）。

### R2 落库
- 新增 `AiCallRecorder`（@Component）：`record(AiCallLog) -> Long`（返回 log id）、`linkHistory(logId, historyId)`。
- `OpenAiCompatAIService`：计时；成功记录 `status=success` + 解析响应 `usage` token；catch 记录 `status=fallback` + 错误消息。
- `MockAIService`：记录 `provider=mock`、`status=success`（模板成功路径）。
- `AIConfig` 把 `AiCallRecorder` 注入两个实现；`ErrorExplainService` 在 history 入库后回填 `history_id`。

### R3 查询接口
- `GET /api/ai-logs?page&size&status&provider&historyId` → `Result<PageResult<AiCallLog>>`（分页插件已配置）。

### R4 前端页面
- `types` + `services/api.ts` 加 `AiCallLog`、`fetchAiLogs`。
- 新增 `pages/LogsPage.tsx`：antd `Table` 分页展示（时间/provider/模型/状态/耗时/token/输入摘要），状态 Tag 着色（success=绿、fallback=橙；provider=mock 灰标"模板"）。
- `App.tsx` 挂 `/logs` 路由与导航项（依赖 v14-pages 的路由框架）。

## Acceptance Criteria

- [ ] `mvn test` 全绿，且 `ExplainFlowIntegrationTest` 断言 `explain()` 后 `ai_call_log` 有 1 条 `provider=mock` 记录。
- [ ] 真实模式（配 AI_API_KEY）下 `ai_call_log` 记录 success + token；失败记录 fallback + error_message。
- [ ] `/api/ai-logs` 分页/筛选正常；`/logs` 页面表格展示正常。
- [ ] `npm run build`（tsc）通过。

## Out of Scope

- 告警/统计报表；真实 token 计费。
- 请求/响应全量 payload 落库（仅存输入摘要，原始堆栈已在 error_history.stack_trace）。
