# 设计：AI 调用日志

## 数据流

```
用户 → POST /api/explain → ErrorExplainService.explain()
         ├─ aiService.explain()           （Mock 或 OpenAiCompat）
         │      └─ AiCallRecorder.record(log)  → log.id   （status/provider/model/duration/tokens/excerpt）
         ├─ historyMapper.insert(history) → history.id
         └─ AiCallRecorder.linkHistory(log.id, history.id)
```

## 落库位置选择

- 记录点放在 **AIService 两个实现内部**：状态（success/fallback）、token、provider 只有调用处知道，避免在 service 层猜状态。
- **history_id 回填**：AI 调用发生在 history 插入之前，故 log 先以 `history_id=null` 落库，`ErrorExplainService` 在 insert 后回填。`AiCallRecorder.record()` 返回自增 id 供回填。
- Mock 与真实都记录（provider 区分），保证"没配 key"时也可观测。

## token 解析

DeepSeek/OpenAI 响应结构：`choices[0].message.content` + `usage.{prompt_tokens, completion_tokens}`。
`OpenAiCompatAIService` 已有 `JsonNode root`，新增：
```java
int pt = root.at("/usage/prompt_tokens").asInt(-1);
int ct = root.at("/usage/completion_tokens").asInt(-1);
```
负数视为无 token（置 null）。

## 输入摘要

`stackTrace` 取前 200 字符（去空白）作为 `input_excerpt`；完整堆栈已在 `error_history.stack_trace`，不重复存 payload。

## 接口与页面

- `AiLogController`：`GET /api/ai-logs`，参数 page/size/status/provider/historyId，MyBatis-Plus `LambdaQueryWrapper` 条件拼接 + 分页（仿 `HistoryService.page`）。
- 前端 `LogsPage`：antd `Table` + `Pagination`，列：创建时间/provider/模型/状态/耗时(ms)/prompt tokens/completion tokens/输入摘要；状态映射 Tag。

## 依赖/顺序

- 依赖 v14-pages 已建立的 `/logs` 路由框架与 BrowserRouter。
- 测试用 H2：`schema-test.sql` 加 `ai_call_log` 表，避免 mapper 在测试启动时报错。
