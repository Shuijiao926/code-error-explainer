# 实施计划：V1.5 长文本存储短期修复

> 顺序执行，每步保持可编译/可测。

## 1. SQL
- 新增 `sql/migrate-v15.sql`：
  - `ALTER TABLE error_history MODIFY stack_trace/explanation/pre_report/final_diagnosis MEDIUMTEXT`。
  - `CREATE TABLE IF NOT EXISTS history_diagnosis(...)`。
  - 回填：`INSERT INTO history_diagnosis(history_id, content, ts) SELECT ... FROM error_history eh, JSON_TABLE(COALESCE(NULLIF(eh.final_diagnosis,''),'[]'),'$[*]' COLUMNS(content MEDIUMTEXT PATH '$.content', ts VARCHAR(20) PATH '$.ts')) t WHERE eh.final_diagnosis IS NOT NULL AND NOT EXISTS(子表已有该 history_id)`。
- `src/test/resources/schema-test.sql`：加 `history_diagnosis` 表（H2 用 CLOB 代替 MEDIUMTEXT）。

## 2. 后端新文件
- `entity/HistoryDiagnosis.java`、`mapper/HistoryDiagnosisMapper.java`。

## 3. 改 `service/HistoryService.java`
- `saveDiagnosis` / `detail` / `page` / `delete` / `clearAll` 按 design.md 改造；加私有 `listDiagnoses`、`toJson`。

## 4. 保留期
- `config/AiProperties.java` 加 `callLogRetentionDays=30`。
- 主类 `CodeErrorExplainerdemoApplication` 加 `@EnableScheduling`。
- 新增 `service/AiLogRetentionTask.java`（`@Scheduled(cron="0 0 3 * * *")` 删超期 ai_call_log）。

## 5. 入库兜底
- `service/ai/AiCallRecorder.java` 加 `delete(Long)`。
- `service/ErrorExplainService.java`：insert+linkHistory 包 try/catch `DataAccessException` → 删孤儿日志 + `BusinessException` 友好提示。

## 6. 测试
- `ExplainFlowIntegrationTest`：
  - 现有 `backfillDiagnosis_appendsEntriesWithTimestamp`、`statusFilter_pendingAndDiagnosed` 应继续通过（契约保持）。
  - 新增断言：`saveDiagnosis` 后 `history_diagnosis` 表 1 行；重复 save 多行；detail 的 finalDiagnosis 重建为 JSON。

## 7. 验证命令
```bash
cd D:\Project\code-error-explainerdemo && mvn -q test
docker exec -i code-error-mysql mysql -uroot -proot < sql/migrate-v15.sql
docker exec code-error-mysql mysql -uroot -proot -e "SHOW CREATE TABLE code_error_explainer.history_diagnosis\G"
cd frontend && npm run build   # 前端契约不变，防回归
```

## 回滚
- SQL 幂等可重跑；代码为增量，删除新文件 + 还原 HistoryService/ErrorExplainService/AiProperties/主类 4 个改动即可。
