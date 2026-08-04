# 业界超长文本/大对象存储方案调研

> 结论先行：业界不存在"一个字段搞定大文本"的单一答案。成熟的架构都是**分层**的——
> 数据库只存"可索引的元数据 + 短摘要 + 指针"，大正文走**对象存储 / 分析型列存库**，
> 并配**压缩 + 保留期(TTL) + 分区**。LLM 观测/错误追踪产品是这套分层的最佳样板。

## 1. 行业模式分类（Taxonomy）

| 模式 | 做法 | 适用 | 典型产品 |
|---|---|---|---|
| **A. 元数据/正文分离 + 指针** | DB 存短摘要 + `content_key`，正文放对象存储(S3/MinIO/GCS) | 超大 blob：堆栈全量、AI 完整响应、日志文件 | Sentry、Langfuse、Helicone |
| **B. 分析型列存库** | ClickHouse 等存高吞吐结构化事件日志，按时间分区 + TTL 保留 | 可观测性日志、调用链、指标 | Sentry(Snuba)、Langfuse、ClickHouse 日志体系 |
| **C. 单库加大类型** | MEDIUMTEXT(16MB)/LONGTEXT(4GB) 直接存 | 中小规模、正文必须随行查询、无独立存储 | 大多数业务系统起步 |
| **D. 压缩** | gzip/zstd 压缩后存储（堆栈可压 5~20 倍） | 任意大文本 | 日志/事件系统内部 |
| **E. 追加表（关系化）** | 一条记录一行子表，不做 JSON 数组拼接 | 一对多的"追加型"数据 | 任何消息/评论/流水表 |
| **F. 分块 + 向量库** | 长文本按语义切 chunk，embedding 进向量库供检索 | Agent/RAG 上下文 | LangChain、各类 Agent 产品 |

## 2. 具体案例（已确认）

### Sentry（错误追踪 = 我们系统的直接对标）
- **PostgreSQL**：项目、用户、配置、以及 blob 文件的**元数据**。
- **ClickHouse（Snuba）**：事件(event)的**结构化索引字段**——异常类型、时间、release、tag 等；
  表按 `PARTITION BY (retention_days, toMonday(timestamp))` 分区，背景合并 + Zookeeper 复制，
  天然支持**按保留期清理**。
- **Filestore / 对象存储（S3、GCS、本地文件系统、SeaweedFS）**：**大二进制 blob**——
  Replay 录像、附件、debug 文件、profile。Replay 场景：`ReplayEvent`（可索引元数据）进 ClickHouse，
  `ReplayRecording`（大、压缩过的 blob）进 Filestore，文件元数据进 PostgreSQL。
- **Redis/Kafka**：临时缓存与消息队列。

→ **核心启示**：堆栈全量 = blob → 对象存储；异常类型/时间/摘要 = 索引字段 → 列存/行存。

### Langfuse（LLM 观测 = 我们 ai_call_log 的直接对标）
- **PostgreSQL**：项目、workspace、用户、API key 等操作元数据。
- **ClickHouse**：`trace`/`observation`（每次 LLM 调用）的**结构化列**——input/output(JSON)、
  **token 计数、model、耗时、状态、时间戳**，按时间分区 + TTL 保留。
- **Redis**：ingestion 队列。
- **S3 / S3 兼容（含 MinIO）**：多模态/超大 **media 内容**（图片、音频、上传文件）单独存放，DB 存引用。

→ **核心启示**：LLM 调用日志的"token/耗时/状态/模型"正是我们 `ai_call_log` 的列——这就是业界标准做法
  （我们已对齐）；若将来要存**完整 request/response**，Langfuse 的做法是**外置对象存储 + 指针**。

### Helicone（LLM 代理网关观测）
- 请求/响应正文存对象存储（S3），索引列（token、模型、延迟、状态）存 ClickHouse/Postgres，避免热表被大 body 撑爆。

### ClickHouse 通用实践
- 列式 + 压缩（LZ4/ZSTD），时间分区，`TTL` 自动过期删除——高吞吐日志的标配。

## 3. 关于"单字段行不行"

- **行**：正文 ≤ 数十 KB、访问频繁且必须随行返回、数据量不大——单 TEXT/MEDIUMTEXT 完全够，
  无需引入对象存储。这也是绝大多数业务系统的默认。
- **不行**：正文几十 MB 以上、或数据量增长快（日志类）、或需要全文检索——单字段会撑爆
  InnoDB 行缓冲、拖慢列表查询、无法做保留期。
- **关键判断**：正文和"索引/摘要/统计"分开看——**DB 永远只存需要被检索的东西**。

## 4. 结论映射（对我们的两个表）

| 我们的表 | 业界对标 | 建议方向 |
|---|---|---|
| `error_history`（堆栈/报告） | Sentry 的 event | 短期：TEXT→MEDIUMTEXT 保安全；中期：全量堆栈外置对象存储，DB 留摘要+指针 |
| `ai_call_log`（观测） | Langfuse/Helicone | 已对齐（excerpt+token+耗时+状态）；补齐 **TTL 保留期**；全量 body 可选外置 |
| `final_diagnosis`（追加） | 任何消息表 | 反模式，应改子表一行一条，见 design.md |
