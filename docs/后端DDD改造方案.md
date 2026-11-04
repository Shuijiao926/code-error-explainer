# 后端 DDD 改造方案

> 编写日期：2026-11-04
> 目标：在开发 V2.0 新功能之前，先把后端从「贫血 CRUD + 上帝服务」重构为「限界上下文 + 四层架构」。
> 约束：**数据库表名不变、REST 接口契约不变、前端无需改动**——这是行为保持的结构重构。

---

## 0. 为什么现在做

V2.0 要新增 **用户体系、学习导学、IM 接入、多模态**四个模块，都是横跨多层的功能。
如果继续按现在的 `controller / service / mapper / entity` 技术分层往后堆，会出现：

- 一个 `HistoryService` 已经 488 行，再加学习、用户会变成几千行的"上帝服务"；
- 新增模块的代码散落在 5 个包，模块边界彻底消失；
- 用户数据隔离、权限校验这类横切关注点无处安放。

**结论：先把骨架立对，再往上长。** DDD 改造本身约 3.5 人天，但会让后续 20 人天的功能开发少绕很多路。

---

## 1. 现状诊断（DDD 视角）

| # | 问题 | 具体证据 |
|---|---|---|
| 1 | **贫血模型** | 4 个 `entity` 全是 Lombok `@Data` + MyBatis 注解，零行为。所有规则都在 Service |
| 2 | **上帝服务** | `HistoryService` 488 行，同时承担：分页查询、知识库筛选、统计聚合、Markdown/CSV 导出、沉淀、诊断回填、删除清空——**6 个用例塞一个类** |
| 3 | **按技术分层分包** | `controller / dto / entity / mapper / service`。改一个功能要横跨 5 个包，模块边界无法表达 |
| 4 | **持久化模型 = 领域模型** | `ErrorHistory` 直接挂 `@TableName`，数据库结构变化直接冲击业务代码 |
| 5 | **业务规则散落** | 「finalDiagnosis 由子表重建」「入库失败清理孤儿日志」「tags 传 null 不更新、传空串清空」这类领域规则，全埋在 Service 的注释里 |
| 6 | **无上下文边界** | 分诊、知识沉淀、知识卡片、AI 日志全部平铺在 `service` 包下，平级无边界 |
| 7 | **DTO 混杂** | `dto` 包里既有 HTTP 请求响应（`ExplainRequest`），也有真正的领域值对象（`PreTriageReport`、`Handoff`） |

---

## 2. 限界上下文划分

按业务能力划分 **6 个上下文**，与 V2.0 模块一一对应：

| 上下文 | 类型 | 职责 | 对应现状 |
|---|---|---|---|
| **triage 分诊** | ★核心域 | 堆栈识别、语言/技术栈判定、AI 分诊、双段式报告生成 | `service/` + `service/ai/` + `dto/PreTriageReport 等` |
| **knowledge 知识沉淀** | 支撑域 | 历史记录、诊断回填、踩坑档案、知识卡、统计与导出 | `HistoryService`（拆分）+ `ErrorHistory` + `ErrorTemplate` |
| **learning 学习导学** | 支撑域（V2.0 新增） | 概念图谱、学习路径、资料推荐、术语表 | 新建 |
| **identity 用户与权限** | 通用域（V2.0 新增） | 登录、角色、数据隔离 | 新建 |
| **channel 渠道接入** | 支撑域（V2.0 新增） | IM（飞书）、多模态输入（图片/语音） | 新建 |
| **observability 可观测** | 通用域 | AI 调用日志、保留期清理、质量指标 | `AiCallLog` + `AiLogService` + `AiLogRetentionTask` |

**上下文之间不直接依赖对方的 domain**，只允许：
- 通过对方的 **应用服务**（同步、主流程）
- 通过 **Spring 事件**（异步、旁路数据，如日志/通知）

---

## 3. 分层与依赖规则（铁律）

每个上下文内部统一四层，依赖方向**严格单向**：

```
interfaces  ──▶  application  ──▶  domain
   (入站适配)      (用例编排)        ▲
                                    │ 实现
                              infrastructure
                                 (出站适配)
```

| 层 | 放什么 | 禁止什么 |
|---|---|---|
| **domain** | 聚合根、实体、值对象、领域服务、仓储接口（port）、领域事件 | **禁止**依赖 Spring / MyBatis / Jackson / HTTP；纯 Java |
| **application** | 用例编排、事务边界、DTO 组装、跨上下文调用 | 禁止写业务规则（规则属于 domain） |
| **infrastructure** | 持久化实现（PO + Mapper + RepositoryImpl）、外部 API 适配器、定时任务 | 禁止被 interfaces 直接调用 |
| **interfaces** | Controller、IM Webhook、参数校验、响应包装 | 禁止写业务逻辑 |

**可自动校验**：用 ArchUnit 写两条规则——
1. `domain` 包不允许 import `org.springframework..` / `com.baomidou..` / `tools.jackson..`
2. 上下文之间不允许 import 对方的 `domain` 包

---

## 4. 目标包结构

```
com.org.codeerrorexplainerdemo
├── CodeErrorExplainerApplication
│
├── shared/                                  # 共享内核（跨上下文）
│   ├── domain/
│   │   ├── BusinessException               # ← common/BusinessException
│   │   ├── PageResult                      # ← dto/PageResult
│   │   └── valueobject/…                   # Id、时间等通用值对象
│   └── interfaces/
│       ├── ApiResult                       # ← common/Result
│       └── GlobalExceptionHandler          # ← common/GlobalExceptionHandler
│
├── triage/                                  # ★ 分诊上下文（核心域）
│   ├── domain/
│   │   ├── model/
│   │   │   ├── TriageRecord                # 聚合根：一次分诊记录
│   │   │   ├── TriageReport                # 值对象：双段式报告 ← PreTriageReport
│   │   │   ├── PersonReport                # 值对象
│   │   │   ├── Handoff                     # 值对象
│   │   │   ├── StackTrace                  # 值对象（原始堆栈，含校验）
│   │   │   ├── DetectedLanguage            # 枚举 ← LanguageDetector 的字符串
│   │   │   ├── TechStack                   # 值对象（有序标签集）
│   │   │   └── ErrorType                   # 值对象
│   │   ├── service/
│   │   │   ├── LanguageDetector            # ← service/LanguageDetector
│   │   │   ├── TechStackDetector           # ← service/TechStackDetector
│   │   │   ├── ErrorTypeExtractor          # 从 ErrorExplainService 抽出
│   │   │   └── TriagePolicy                # 新：AI/Mock/降级的判定策略
│   │   ├── port/
│   │   │   ├── AiTriagePort                # ← service/ai/AIService
│   │   │   ├── TriageRecordRepository      # 新：仓储接口
│   │   │   └── AiCallLogPort               # 出站：写调用日志（由 observability 提供）
│   │   └── event/TriageCompletedEvent      # 领域事件
│   ├── application/
│   │   └── TriageApplicationService        # ← service/ErrorExplainService
│   ├── infrastructure/
│   │   ├── ai/
│   │   │   ├── OpenAiCompatAdapter         # ← service/ai/OpenAiCompatAIService
│   │   │   ├── MockAiAdapter               # ← service/ai/MockAIService
│   │   │   ├── AiProperties                # ← config/AiProperties
│   │   │   └── AiConfiguration             # ← config/AIConfig
│   │   └── persistence/
│   │       ├── TriageRecordPO              # ← entity/ErrorHistory（仅持久化）
│   │       ├── TriageRecordMapper
│   │       └── TriageRecordRepositoryImpl
│   └── interfaces/rest/
│       └── TriageController                # ← ErrorExplainController 的 /explain、/health
│
├── knowledge/                               # 知识沉淀上下文
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ErrorRecord                 # 聚合根：历史记录（聚合诊断列表）
│   │   │   ├── Diagnosis                   # 实体 ← HistoryDiagnosis
│   │   │   ├── KnowledgeCard               # 聚合根 ← ErrorTemplate
│   │   │   ├── KnowledgeFilter             # 值对象：9 个筛选条件（替代 9 个方法参数）
│   │   │   ├── ArchiveTag                  # 值对象：标签集合（含 500 字符规则）
│   │   │   └── KnowledgeStatistics         # 值对象 ← KbStats / NameCount / TrendPoint
│   │   ├── service/
│   │   │   ├── KnowledgeStatistician       # ← HistoryService 的 stats/buildTrend/toNameCounts
│   │   │   └── KnowledgeExporter           # ← exportMarkdown/exportCsv/csvField/truncateStack
│   │   ├── port/
│   │   │   ├── ErrorRecordRepository
│   │   │   └── KnowledgeCardRepository
│   │   └── event/
│   ├── application/
│   │   ├── HistoryApplicationService       # 分页/详情/删除/清空
│   │   ├── DiagnosisApplicationService     # 诊断回填
│   │   ├── ArchiveApplicationService       # 沉淀/标签
│   │   ├── KnowledgeQueryApplicationService# kb 检索 / 统计
│   │   └── KnowledgeExportApplicationService
│   ├── infrastructure/persistence/
│   │   ├── ErrorRecordPO / ErrorRecordMapper / ErrorRecordRepositoryImpl
│   │   ├── DiagnosisPO / DiagnosisMapper
│   │   └── KnowledgeCardPO / KnowledgeCardMapper
│   └── interfaces/rest/
│       ├── HistoryController               # /api/history/*
│       ├── KnowledgeController             # /api/kb/*
│       └── KnowledgeCardController         # /api/templates
│
├── observability/                           # 可观测上下文
│   ├── domain/
│   │   ├── model/AiCallRecord              # ← entity/AiCallLog
│   │   ├── model/LogRetentionPolicy        # 保留期规则（阈值、截断）
│   │   ├── port/AiCallRecordRepository
│   │   └── service/ExcerptPolicy           # 200 字符摘要规则
│   ├── application/AiLogQueryApplicationService  # ← AiLogService
│   ├── infrastructure/persistence/…
│   └── interfaces/rest/AiLogController     # ← AiLogController
│
├── identity/     # V2.0 新增（按同结构）
├── learning/     # V2.0 新增（按同结构）
├── channel/      # V2.0 新增（按同结构）
│
└── config/                                  # 仅保留全局配置
    ├── MybatisPlusConfig
    └── WebConfig
```

---

## 5. 现有类迁移映射（逐类）

| 现有 | 目标 | 动作 |
|---|---|---|
| `common/Result` | `shared/interfaces/ApiResult` | 移动+重命名 |
| `common/BusinessException` | `shared/domain/BusinessException` | 移动 |
| `common/GlobalExceptionHandler` | `shared/interfaces/GlobalExceptionHandler` | 移动 |
| `dto/PageResult` | `shared/domain/PageResult` | 移动 |
| `dto/PreTriageReport` | `triage/domain/model/TriageReport` | 重构为值对象 |
| `dto/PersonReport` / `dto/Handoff` | `triage/domain/model/` | 移动 |
| `dto/ExplainRequest` / `ExplainResponse` | `triage/interfaces/rest/dto/` | 移动（HTTP 契约） |
| `dto/DiagnosisRequest` / `DiagnosisEntry` / `ArchiveRequest` | `knowledge/interfaces/rest/dto/` | 移动 |
| `dto/KbStats` / `NameCount` / `TrendPoint` | `knowledge/domain/model/` | 合并进 `KnowledgeStatistics` |
| `entity/ErrorHistory` | `triage/…/TriageRecordPO` + `knowledge/…/ErrorRecord` | **拆分**：PO 与领域模型分离 |
| `entity/HistoryDiagnosis` | `knowledge/…/DiagnosisPO` + `Diagnosis` | 拆分为 PO + 实体 |
| `entity/ErrorTemplate` | `knowledge/…/KnowledgeCardPO` + `KnowledgeCard` | 拆分为 PO + 聚合根 |
| `entity/AiCallLog` | `observability/…/AiCallRecordPO` + `AiCallRecord` | 拆分为 PO + 领域模型 |
| `mapper/*` | 各上下文 `infrastructure/persistence/` | 移动 |
| `service/LanguageDetector` | `triage/domain/service/` | 移动（纯算法） |
| `service/TechStackDetector` | `triage/domain/service/` | 移动 |
| `service/ErrorExplainService` | `triage/application/TriageApplicationService` + `domain/service/ErrorTypeExtractor` | **拆分** |
| `service/ai/AIService` | `triage/domain/port/AiTriagePort` | 重命名 |
| `service/ai/AiCallOutcome` | `triage/domain/model/TriageOutcome` | 移动 |
| `service/ai/MockAIService` / `OpenAiCompatAIService` | `triage/infrastructure/ai/*Adapter` | 重命名 |
| `service/ai/AiCallRecorder` | `observability/domain/port/AiCallLogPort` + 实现 | 拆分 |
| `service/HistoryService`（488 行） | 拆成 5 个应用服务 + 2 个领域服务 | **重点拆分** |
| `service/TemplateService` | `knowledge/application/KnowledgeCardApplicationService` | 移动 |
| `service/AiLogService` | `observability/application/AiLogQueryApplicationService` | 移动 |
| `service/AiLogRetentionTask` | `observability/infrastructure/scheduler/` | 移动 |
| `controller/ErrorExplainController`（160 行，13 接口） | 拆成 `TriageController` / `HistoryController` / `KnowledgeController` | **重点拆分** |
| `controller/TemplateController` | `KnowledgeCardController` | 合并 |
| `controller/AiLogController` | `observability/interfaces/AiLogController` | 移动 |
| `config/AIConfig` + `AiProperties` | `triage/infrastructure/ai/` | 移动 |
| `config/MybatisPlusConfig` / `WebConfig` | `config/` 保留 | 保留 |

---

## 6. 关键设计决策

### 决策 1：领域模型与持久化对象（PO）分离 ★核心取舍

- 领域模型：纯 Java，无 MyBatis 注解，有行为（如 `ErrorRecord.appendDiagnosis()`、`ArchiveTag.parse()`）
- PO：`@TableName` 等持久化注解，只做数据搬运
- 每个聚合根配一个 `Assembler` 做双向转换

**代价**：约 +200 行映射代码。
**收益**：数据库变更不冲击业务；领域逻辑可脱离框架单测；这是「DDD 到位」的分水岭。

### 决策 2：主流程同步编排，旁路走事件

| 链路 | 方式 | 理由 |
|---|---|---|
| 分诊 → 入库 | 应用层**同步编排**（同一个事务） | 必须一致：分诊成功但没入库=数据丢失 |
| AI 调用日志 | **Spring 事件**（`@TransactionalEventListener`） | 旁路数据，日志失败不应回滚主流程 |
| 未来的 IM 推送 / 通知 | Spring 事件 | 外部依赖不可靠，必须解耦 |

### 决策 3：跨上下文协作只走两条路

1. **应用服务调用**（同步）：如 `knowledge` 需要写记录时调用 `triage` 的查询接口；
2. **Spring 事件**（异步）：如 `TriageCompletedEvent` → `observability` 记录日志。
   **禁止**：直接 import 别人的 domain 类。

### 决策 4：不引入的东西（避免过度设计）

| 不引入 | 理由 |
|---|---|
| Spring Modulith / Axon 等框架 | 4 千行项目，引入成本大于收益 |
| CQRS 命令总线 / Event Sourcing | 无此复杂度需求 |
| 领域事件持久化、Saga | 单体应用，事务足够 |
| 每个上下文独立数据库 | 单体 + 单库，用 `@Transactional` 管理即可 |

### 决策 5：V2.0 新模块直接按新结构生长

`identity` / `learning` / `channel` 三个上下文**不给迁移期**，从第一行代码就按四层写。
这样重构不是"一次性大手术"，而是"骨架立好后持续受益"。

---

## 7. 分阶段执行计划（约 3.5 人天）

| 阶段 | 内容 | 产出 | 工时 |
|---|---|---|---|
| **Phase 0** | 建新包骨架、`shared` 迁移、ArchUnit 依赖规则 | 骨架可编译、规则可跑 | 0.5d |
| **Phase 1** | `triage` 上下文完整迁移（含 AI 适配器与端口） | 分诊链路在新结构下跑通 | 1.0d |
| **Phase 2** | `knowledge` 上下文迁移，**拆分 HistoryService 与 Controller** | 知识沉淀链路跑通 | 1.0d |
| **Phase 3** | `observability` 上下文迁移 + 事件解耦 | 日志链路跑通 | 0.5d |
| **Phase 4** | 测试迁移、全量回归、删除旧包 | `mvn test` 全绿 | 0.5d |

**顺序原则**：自底向上（shared → core → supporting → generic），每阶段结束必须可编译、测试通过、接口行为不变，**不做一次性大爆炸**。

---

## 8. 验收标准

- [ ] `mvn test` 全绿（现有 8 个测试类全部迁移并通过）
- [ ] 13 个 REST 接口路径、入参、出参**完全不变**（前端零改动）
- [ ] 数据库表名、字段**完全不变**（现有数据可直接用）
- [ ] ArchUnit 两条规则通过：domain 无框架依赖、上下文间无 domain 交叉依赖
- [ ] `HistoryService`（488 行）被拆为 ≤5 个应用服务，单类均 <150 行
- [ ] `ErrorExplainController`（160 行 / 13 接口）拆为 3 个 Controller
- [ ] 领域模型类无 Lombok `@Data`（改为构造器 + 行为方法）

---

## 9. 风险与对策

| 风险 | 对策 |
|---|---|
| 大范围移动导致 git 历史断裂 | 用 `git mv` 保留重命名历史；分阶段提交 |
| 映射代码增加维护成本 | 只对聚合根做 PO↔Domain 映射，值对象直接复用 |
| 重构期间 V2.0 功能开发停摆 | 控制在 3.5 天内；Phase 0-1 完成后可并行开发 identity 上下文 |
| 行为不一致 | 每个 Phase 结束跑全量测试 + 关键接口手测 |
| 过度设计 | 严格执行第 6 节「不引入清单」 |

---

## 10. 改造后的收益（对 V2.0 与软著）

| 维度 | 改造前 | 改造后 |
|---|---|---|
| 模块边界 | 无（技术分层） | 6 个限界上下文 |
| 上帝服务 | `HistoryService` 488 行 | 拆分后单类 <150 行 |
| 领域逻辑 | 埋在 Service 注释里 | 收敛到领域模型与领域服务 |
| 新模块接入 | 继续堆在 service 包 | 按上下文独立生长 |
| 可测性 | 领域逻辑需起 Spring 容器 | 领域层可纯 JUnit 测试 |
| **软著可写点** | "三层架构"（模板化） | **DDD 限界上下文 + 端口适配器 + 领域事件 + 数据隔离拦截器**（有设计含量） |

---

*确认后从 Phase 0 开始执行，每阶段结束汇报并可运行验证。*
