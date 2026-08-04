# 长文本存储设计审计（超长堆栈 / AI 报告 / 调用日志）

## Goal & User Value

用户提出：**超长文本（异常堆栈、AI 分诊报告、AI 调用日志）应该怎么存才成熟？单字段够吗？业界 Agent/LLM 产品如何处理？** 要求用 Trellis 全盘梳理该问题的**完整隐含设计缺陷**，产出可落地的设计结论（本任务为研究/审计性质，是否实施另起实现任务）。

## 触发背景

- 用户提到"数据库提示字段过长"，但本次贴出的报错日志实为 `enterprise_digital_platform/contract-service` 的 **Druid 依赖缺失**（`ClassNotFoundException: com.alibaba.druid.pool.DruidDataSource`，来源 `application-dev.yml:30`），与字段过长无关——已向用户澄清。
- 本项目 `code-error-explainerdemo` 的真实长文本字段（V1.4 后）：`error_history.stack_trace/explanation/pre_report/final_diagnosis`（TEXT）、`ai_call_log.input_excerpt/error_message`（VARCHAR(1000)）。TEXT 上限 64KB，存在溢出风险。

## Requirements

### R1 枚举当前 schema 中所有长文本字段与类型边界
- `error_history`：`stack_trace`(TEXT)、`explanation`(TEXT)、`pre_report`(TEXT)、`final_diagnosis`(TEXT，**追加式** JSON)、`summary`(VARCHAR(500))。
- `ai_call_log`：`input_excerpt`(VARCHAR(1000))、`error_message`(VARCHAR(1000))、其余为数值/短字段。
- `error_template`：`cause_top3`(TEXT)、`fix_template`(TEXT)、`hierarchy`(VARCHAR(500))。

### R2 梳理隐含设计缺陷（全盘）
- 类型上限、追加式单列、冗余存储、无保留期、全文检索缺失、utf8mb4 体积、InnoDB 大字段代价、无压缩、无"元数据/正文分离"等。

### R3 调研业界成熟方案
- MySQL 原生类型选择；对象存储 + 指针；日志系统（ELK/Loki/ClickHouse）；LLM 观测产品（Langfuse/Helicone/Sentry 等）如何存 prompt/completion/堆栈全量。
- 本项目的分阶段落地建议（短期无架构改动 / 中期外置）。

## Acceptance Criteria

- [ ] `research/` 有业界方案调研文件（含 Langfuse/Sentry/ClickHouse 等具体做法）。
- [ ] `design.md` 完整列出 ≥8 条隐含设计缺陷 + 分阶段落地结论（短期/中期）。
- [ ] 明确"字段过长"最可能的真实触发点（`final_diagnosis` 追加 / 超大堆栈超 64KB）。
- [ ] 结论已向用户汇报；用户决定是否实施（实施另建实现任务）。

## Out of Scope

- 实际改表/改代码（本任务只审计 + 设计）。
- 修复 contract-service 的 Druid 依赖问题（属于 enterprise_digital_platform，与本项目无关，如需另办）。
