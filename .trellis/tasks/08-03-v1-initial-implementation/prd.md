# 代码异常智能解释系统 V1.0 初步实现

## Goal & User Value

**把程序报错日志翻译成人话。** 用户粘贴 Java 异常堆栈 → AI 用大白话分层解释
（一句话概述 / 出问题位置 / 根因 / 修复步骤 / 预防建议）+ 自动存入历史 →
10 秒得到结论，不用再逐行查 Google。

本项目是「代码异常智能解释系统 V1.0」，代码名沿用 `code-error-explainer`。

## Confirmed Facts（来自仓库证据）

- 脚手架：Spring Boot **4.0.7** / Java 17 / Maven / 包名 `com.org.codeerrorexplainerdemo`（已存在启动类与测试）。
- 已有需求文档：`src/main/resources/doc/01-PRD-产品需求文档.md`、`02-技术选型-架构设计.md`。
- PRD 版本规划：**V1.0 = 堆栈翻译 + 历史记录（P0）**；异常知识卡片 / 粘贴自动检测 = V1.1。
- 技术栈（PRD+设计文档）：前端 React18 + TS + Ant Design5 + Vite + axios；后端 Spring Boot + MyBatis-Plus + MySQL8。
- AI 接入：`AIService` 接口多 provider；V1 推荐 DeepSeek（OpenAI 兼容协议）。
- 数据库两张表：`error_history`（P0 用）、`error_template`（P1 用，本任务不建模块）。

## Requirements（范围内）

### R1 堆栈翻译（核心，P0）
- `POST /api/explain`，入参 `{stackTrace}`。
- 自动识别异常类型：正则提取首个 `XXXException` / `XXXError` 类名；无匹配 → `Unknown`。
- AI 结构化解释五要素：`overview / location / rootCause / fixSteps[] / prevention`。
- **无 API Key 时自动走 Mock 提供者**，保证 demo 端到端可跑（真实分析需配置 Key）。
- 返回：`{id, errorType, summary, explanation, createdAt}`，并自动入库。
- AI 返回解析失败时兜底：仍返回可展示内容并入库，不白屏。

### R2 历史记录（P0）
- 每次 explain 自动保存一条历史。
- `GET /api/history?page=&size=&keyword=` 分页、时间倒序；keyword 模糊匹配 errorType + summary。
- `GET /api/history/{id}` 详情。
- `DELETE /api/history/{id}` 单条删除。
- `DELETE /api/history` 全部清空（PRD「全部清空」需求，补充接口）。

### R3 系统能力
- `GET /api/health` 健康检查。
- 统一响应 `Result{code, message, data}`。
- CORS 放行前端开发端口 `localhost:5173`；另配 vite dev 代理 `/api → :8080` 双保险。

### R4 前端（P0）
- 主页：堆栈输入框 + 「翻译成人话」按钮 + 结构化结果卡片 + 一键复制。
- 历史侧边抽屉：列表、搜索、回看详情、单删/清空。
- 深色模式切换（localStorage 持久化）。
- 开发期 `npm run dev` + 后端，主链路可走通。

## Acceptance Criteria

- [ ] AC1 构建：后端 `mvn test` 通过；前端 `npm run build` 通过。
- [ ] AC2 `POST /api/explain` 返回结构符合 PRD §7.2；errorType 正确提取（如 NPE 堆栈 → `NullPointerException`）。
- [ ] AC3 无 API Key（mock 模式）下 explain 全程可跑通并成功入库。
- [ ] AC4 历史接口支持分页、倒序、keyword 搜索；详情/单删/清空均可用。
- [ ] AC5 `GET /api/health` 返回正常。
- [ ] AC6 前端主链路（输入 → 结果卡片 → 复制 → 历史回看）可用；深色模式可切换。
- [ ] AC7 执行 `sql/init.sql` 后，后端可连 MySQL 读写 `error_history`。

## Out of Scope（V1.1+）

- 异常知识卡片模块（`error_template` 建表/接口/前端）。
- 粘贴即自动检测跳转、多日志格式（Spring Boot/MyBatis 日志、Maven/Gradle 构建错误）解析。
- Ollama 本地模型、用户系统/多端、AI 对话追问。

## Key Decisions（详见 design.md）

1. **目录**：根目录即后端（沿用已生成脚手架，不做 `backend/` 子目录迁移），`frontend/` 放前端，`sql/init.sql` 建表。
2. **ORM**：Spring Boot 4.0.7 + `mybatis-plus-spring-boot4-starter` 3.5.16 + `mybatis-plus-jsqlparser`（研究已确认兼容）。
3. **AI**：默认 OpenAI 兼容通道（DeepSeek/OpenAI 通用，RestClient 直连，不引重型 SDK）；无 Key 自动 Mock。
4. **DB**：MySQL 8（PRD 指定），需先执行 `sql/init.sql`。

## Open Questions

- 无阻塞项。
