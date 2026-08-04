# 设计：V1.5 长文本存储短期修复

## 数据流变化（final_diagnosis → history_diagnosis）

```
旧：saveDiagnosis → 读 final_diagnosis JSON → 追加 → 整列重写（无界增长 ❌）
新：saveDiagnosis → INSERT history_diagnosis 一行 ✅
     detail()/page() → 由子表重建 finalDiagnosis 字段（保持前端契约）
```

## 表结构

```sql
history_diagnosis (
  id          BIGINT      PK AUTO_INCREMENT,
  history_id  BIGINT      NOT NULL,
  content     MEDIUMTEXT  NOT NULL,          -- 诊断内容
  ts          VARCHAR(20) NOT NULL,          -- yyyy-MM-dd HH:mm:ss
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_history_id (history_id)
)
```

## HistoryService 三方法改造

- `saveDiagnosis(id, diag)`：校验存在 → `childMapper.insert`（ts=now）→ 返回 `listDiagnoses(id)`。
- `listDiagnoses(id)`：`childMapper.selectList(history_id=id orderBy ts,id)` → 映射 `DiagnosisEntry(ts,content)`。
- `detail(id)`：加载 history → `setFinalDiagnosis(toJson(listDiagnoses(id)))`（空→null）。
- `page(...)`：
  - 轻量 select **去掉** `finalDiagnosis` 列；
  - 状态筛选用 `apply("EXISTS/NOT EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = error_history.id)")`；
  - 分页记录回来后按 `historyId in (...)` 批量查子表，`finalDiagnosis = 有诊断 ? "[]" : null`（前端 truthy 判断不变）。
- `delete(id)` / `clearAll()`：级联删除该 history 的子行。

## 保留期任务

```java
@Scheduled(cron = "0 0 3 * * *")   // 每日 03:00
purgeExpired(): delete ai_call_log where created_at < now - ai.call-log-retention-days
```
- 主类加 `@EnableScheduling`。
- `AiProperties` 加 `callLogRetentionDays`（默认 30）。

## 入库兜底

```java
try { historyMapper.insert(history); aiCallRecorder.linkHistory(outcome.logId(), history.getId()); }
catch (DataAccessException e) {
    aiCallRecorder.delete(outcome.logId());          // 删孤儿日志，保证日志/记录一致
    throw new BusinessException("保存失败：记录内容可能过长（堆栈/报告），请精简后重试");
}
```
- `AiCallRecorder.delete(Long logId)`：`aiCallLogMapper.deleteById(logId)`。

## 迁移脚本幂等性

- `ALTER TABLE ... MODIFY` 可重复执行（幂等）。
- `CREATE TABLE IF NOT EXISTS history_diagnosis`。
- 回填用 `INSERT ... SELECT ... WHERE NOT EXISTS (子表该 history_id 无行)` 保证幂等。

## 前端契约

- 不变。`HistoryItem.finalDiagnosis`（"[]"/null）与 `HistoryDetail.finalDiagnosis`（重建 JSON）沿用现有解析。
