# 设计审计：超长文本存储的隐含缺陷与落地结论

## 0. 实证基线（真实库 information_schema 查询）

| 表 | 字段 | 类型 | 上限 |
|---|---|---|---|
| error_history | stack_trace | TEXT | 65535 字节 |
| error_history | explanation | TEXT | 65535 字节 |
| error_history | pre_report | TEXT | 65535 字节 |
| error_history | final_diagnosis | TEXT | 65535 字节（**追加式** JSON） |
| error_history | summary | VARCHAR(500) | 500 字符 |
| ai_call_log | input_excerpt / error_message | VARCHAR(1000) | 1000 字符 |
| error_template | cause_top3 / fix_template | TEXT | 65535 字节 |

当前数据量：hist 2 行（max stack 973B / max report 2699B）、log 3 行——**尚未溢出，但类型与结构存在系统性缺陷**。

> utf8mb4 下 TEXT 按**字节**计 65535（≈ 16K 汉字），一个深层堆栈或长 AI 输出随时可超。

## 1. 完整隐含设计缺陷清单

### D1 类型上限无余量（最直接）
`stack_trace` 是**用户输入**，不可控；一个 Spring 深层 cause-chain、OOM dump、或压测下的巨型堆栈
轻松超 64KB → INSERT 直接报 `Data too long for column 'stack_trace'`（MySQL 默认严格模式）。
"字段过长"最可能的真实触发点之一。

### D2 追加型单列 = 无界增长（最隐蔽）
`final_diagnosis` 用 TEXT 存 JSON 数组，`saveDiagnosis()` **每次追加**重写整列。随历史诊断累积，
该列**必然**突破 64KB。它在语义上是"一对多"的流水，却用单列拼接——业界反模式，
应拆 `history_diagnosis(id, history_id, content, ts)` 子表，一行一条。

### D3 冗余存储（同一内容存 2~3 份）
同一堆栈同时存在于：`error_history.stack_trace` + `pre_report` JSON 里的 `handoff.rawError`
+ `ai_call_log.input_excerpt`（前 200 字符，可接受）。前两处是**全量重复**：
改一处不同步 → 数据不一致 + 存储翻倍 + 序列化开销翻倍。

### D4 无保留期/无清理（日志表无界）
`ai_call_log` 每次 AI 调用必落一行，`error_history` 只增。**没有 TTL、没有分区、没有清理任务**。
观测数据无界累积 = 磁盘耗尽 + 查询退化。业界（Sentry/Langfuse/ClickHouse）标配按时间分区 + 保留期自动过期。

### D5 大正文与可查询元数据混在热表
列表查询虽用 `select()` 只取轻字段（HistoryService 已做），但详情查询要读整个 TEXT blob；
大量 blob 行会撑大 InnoDB 页/缓冲池，拖慢全表扫描与复制。应"元数据行"与"正文 blob"分离。

### D6 检索能力缺失
正文全文检索没有（无 FULLTEXT、无 ES/向量库）。当前 LIKE 只搜 `error_type/summary/tech_stack`
（短字段，合理），但**用户输入的堆栈本身不可搜**。若将来要按堆栈片段检索，TEXT+LIKE 不可行。

### D7 字节/字符计数混淆风险
`VARCHAR(1000)`/`VARCHAR(500)` 在 utf8mb4 下按**字符**计、TEXT 按**字节**计；
写代码时容易误算余量；且 InnoDB 索引键上限 3072 字节，将来对长 VARCHAR 加索引会直接撞墙。

### D8 无压缩
堆栈是高度重复文本（包名前缀、重复帧），gzip/zstd 常可压 5~20 倍。当前原样存储，浪费空间。
外置/日志方案普遍内建压缩。

### D9 无"正文外置"演进路径
当前 schema 没有 `content_key`/外置位，一旦正文需要外置（MinIO 等），得加列 + 双写迁移，
没有提前留缝。

### D10 溢出后无兜底
`ErrorExplainService` 对 AI 调用失败有 fallback 兜底，但对**入库失败**（字段过长）没有兜底——
用户提交一个超长堆栈 → 500，且 `ai_call_log` 已写但 `error_history` 未写，出现"日志在、记录丢"的裂痕。

## 2. "字段过长"最可能的真实触发点（排序）
1. `final_diagnosis` 追加型 TEXT 被历史诊断撑爆（必然发生）。
2. 用户粘贴超大堆栈 > 64KB（`stack_trace`/`pre_report`）。
3. （`enterprise_digital_platform` 的 Druid 报错与本问题无关，见 prd 触发背景。）

## 3. 业界结论（详见 research/）
- 单字段存大文本：**正文 ≤ 几十 KB 且量小时够用**；否则必须分层（元数据 + 指针 + 对象存储 / 列存日志库）。
- LLM 观测对标 Langfuse/Helicone：excerpt + token/耗时/状态进库（我们已对齐），全量 body 外置。
- 错误追踪对标 Sentry：堆栈 blob → 对象存储，索引字段 → 库。

## 4. 分阶段落地结论

### 短期（无架构改动，立即可做）
- `error_history.stack_trace / explanation / pre_report`：TEXT → **MEDIUMTEXT(16MB)**，消灭 D1 溢出。
- `final_diagnosis`：拆 **`history_diagnosis` 子表**（一行一条，迁移现有 JSON），消灭 D2/D3。
- `ai_call_log`：加**保留期策略**（如保留 30 天 + 定时清理任务 / 按周分区），消灭 D4。
- 入库失败兜底：explain 时对字段超长给友好错误而非裸 500，并保证日志与记录一致，消 D10。

### 中期（数据量上来后）
- 全量堆栈/AI 完整响应 → 对象存储（平台已有 MinIO），DB 留 `content_key` + 摘要，消 D5/D8。
- `ai_call_log` 若量大 → 迁 ClickHouse/日志系统（Langfuse 模式），DB 只留近期热数据。
- 需要堆栈片段检索 → FULLTEXT 或 ES/向量库，消 D6。

### 不做（除非明确需要）
- LONGTEXT(4GB) 一律存 DB：不推荐作为长期方案，正文外置更优。

## 5. 是否需要实施
本任务只做审计与设计。实施（MEDIUMTEXT 迁移、final_diagnosis 拆表、日志保留期）为独立的实现任务，
改动涉及新表 + 迁移脚本 + HistoryService 改造 + 测试，需用户确认后另建 Trellis 任务。
