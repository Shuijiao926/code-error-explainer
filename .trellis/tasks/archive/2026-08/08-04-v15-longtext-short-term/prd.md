# V1.5 长文本存储短期修复（MEDIUMTEXT / 诊断子表 / 日志保留 / 入库兜底）

## Goal & User Value

落地设计审计（任务 v15-longtext-storage）的**短期方案**，消除"字段过长/无界增长/日志失配"三类隐患，
为中期外置（对象存储/列存）留好结构。

## Requirements

### R1 长字段升级 MEDIUMTEXT
- `sql/migrate-v15.sql`：`error_history.stack_trace/explanation/pre_report` → MEDIUMTEXT（16MB）。
- `final_diagnosis` 保留为遗留列（V1.5 起停写），同样升 MEDIUMTEXT 防历史数据迁移前溢出。

### R2 诊断追加拆子表（消灭"追加型单列"）
- 新表 `history_diagnosis(id, history_id, content MEDIUMTEXT, ts VARCHAR(20), created_at)`，索引 history_id。
- 迁移：把既有 `final_diagnosis` JSON 数组**回填**到子表（幂等：仅对尚无子行的 history_id）。
- 后端：新增 `HistoryDiagnosis` 实体 + Mapper。
- `HistoryService.saveDiagnosis()` → 改为**插子行**，返回该 history 全部诊断（有序）。
- `HistoryService.detail()` → `finalDiagnosis` 字段**由子表重建**（保持前端契约不变）。
- `HistoryService.page()` → 状态筛选（pending/diagnosed）与"已诊断"标记改为**基于子表存在性**；
  列表 `finalDiagnosis` 以 `"[]"`（有诊断）/ null（无）表达，前端标签逻辑不动。
- 删除/清空 history 时级联清理子表。

### R3 ai_call_log 保留期
- 新增配置 `ai.call-log-retention-days`（默认 30）。
- 新增定时任务（`@EnableScheduling` + `@Scheduled`，每日凌晨）删除超保留期的 ai_call_log。

### R4 入库兜底（日志与记录一致）
- `ErrorExplainService.explain()` 的 history 入库/回填失败时：删除孤儿 ai_call_log，抛 `BusinessException` 友好提示（而非裸 500）。
- `AiCallRecorder` 增加 `delete(logId)`。

## Acceptance Criteria

- [ ] `migrate-v15.sql` 在真实 MySQL 可重复执行；`history_diagnosis` 建表 + 回填正确。
- [ ] `mvn test` 全绿；集成测试补：`saveDiagnosis` 后 `ai_call_log`/`history_diagnosis` 行为正确、状态筛选仍对。
- [ ] 前端契约不变：列表"已诊断/待回填"、详情诊断记录均正常（`npm run build` 通过）。
- [ ] ai_call_log 保留期任务存在且逻辑正确（不删保留期内数据）。
- [ ] 前端 `16080` 联调：粘贴超长堆栈不裸 500（友好提示）；追加诊断不撑爆单列。

## Out of Scope

- 正文外置对象存储、ClickHouse 迁移（中期方案）。
- `final_diagnosis` 列的物理删除（保留遗留）。
- 修复 contract-service 的 Druid 依赖。
