# 实施计划：AI 调用日志

> 前提：v14-pages 已完成（`/logs` 路由框架存在）。顺序执行。

## 后端

1. **SQL**：新增 `sql/migrate-v14.sql`（幂等建 `ai_call_log`）；`src/test/resources/schema-test.sql` 加 H2 版。
2. **实体/Mapper**：`entity/AiCallLog.java`（仿 ErrorHistory）、`mapper/AiCallLogMapper.java`（extends BaseMapper）。
3. **`service/ai/AiCallRecorder.java`**（@Component）：`record(AiCallLog) -> Long`、`linkHistory(Long logId, Long historyId)`。
4. **改 `OpenAiCompatAIService.java`**：构造器加 `AiCallRecorder`；`explain()` 内 `System.nanoTime()` 计时；成功解析 `usage` token 记录 success；catch 记录 fallback + error_message。
5. **改 `MockAIService.java`**：构造器加 `AiCallRecorder`，记录 provider=mock、status=success。
6. **改 `config/AIConfig.java`**：`aiService(AiProperties, ObjectMapper, AiCallRecorder)` 传入两个实现。
7. **改 `service/ErrorExplainService.java`**：注入 `AiCallRecorder`；拿 `aiService.explain()` 前先记 log 得 id；history insert 后 `linkHistory(logId, history.getId())`。
8. **`service/AiLogService.java`**：分页查询（status/provider/historyId 条件）。
9. **`controller/AiLogController.java`**：`GET /api/ai-logs`。
10. **测试**：`ExplainFlowIntegrationTest` 加用例：explain 后查询 `ai_call_log` == 1 且 provider=mock（注入 `AiCallLogMapper`）。

## 前端

11. **`types/index.ts`**：加 `AiCallLog` 接口。
12. **`services/api.ts`**：加 `fetchAiLogs(params)`。
13. **`pages/LogsPage.tsx`**：Table + 分页 + 状态 Tag（success=绿/fallback=橙；provider=mock 灰标"模板"）。
14. **`App.tsx`**：`/logs` 路由替换占位为 `<LogsPage/>`。

## 验证命令

```bash
cd D:\Project\code-error-explainerdemo && mvn -q test
cd frontend && npm run build
cd frontend && npm run dev   # 手动：/api/explain 后看 /logs 表格
```

## 回滚

- 新代码均为增量；删除新文件 + 还原被改 5 个文件即可。`migrate-v14.sql` 幂等，不执行则表不存在（mapper 查询会报错——故测试与运行前必须执行）。
