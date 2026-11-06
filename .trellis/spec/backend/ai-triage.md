# AI 前置分诊契约（V1.1）

> 产品定位：**前置分诊**——工具不做最终项目级诊断，产出人话说明 + 交给项目 AI 的诊断包。

## Current provider contract

`TriageReportProvider` has two implementations: `LocalRuleTriageProvider` and
`OpenAiCompatibleTriageProvider`. The OpenAI-compatible provider parses the
report and checks that it has a displayable summary before marking a call
successful. Persist the final status once per request so an unusable reply can
fall back and still link its log to the saved report.

## 双段式报告契约

`POST /api/explain` 返回 `PreTriageReport`：

```json
{
  "personReport": {
    "summary": "一句话定性（大白话，类比优先）",
    "layer":   "出在哪一层 + 人话解释",
    "misuse":  "疑似技术使用不妥（方向，非断言）",
    "nextStep":"你现在可以怎么做（引导复制诊断包）"
  },
  "handoff": {
    "techStack": ["Spring Boot", "MyBatis"],
    "suspectedPoints": ["给项目 AI 的技术向疑似点"],
    "checkList": ["需要项目 AI 结合仓库上下文查证的事项"],
    "rawError": "原始堆栈"
  }
}
```

## 铁律

- **只给方向，绝不断言"确定根因"**——工具没有仓库上下文。
- personReport **面向非 IT**：生活化类比优先（如空指针="想用还没准备好的对象"）。
- handoff **面向项目 AI**：精确、可操作；缺失信息写进 checkList。
- AI 返回非 JSON → 兜底仍产出同构双段式并入库，用户不白屏。

## 提示词要点

- 角色：资深 Java 后端「分诊」专家。
- 系统提示词见 `service/ai/OpenAiCompatAIService.SYSTEM_PROMPT`。
- 用户消息需附带已识别技术栈（来自 `TechStackDetector` 或用户修正），AI 据此给更准的疑似点。

## 技术栈识别

- `TechStackDetector` 规则表：类名前缀 → 框架标签；无命中 → `["通用/未知"]`。
- **V1.2 起完全后台化**：前端无技术栈输入项，`ExplainRequest` 无 `techStack` 字段，已删除 `/api/detect`。
  识别结果仅作为输出展示（结果卡片/历史标签）。

## V1.2 交互约定

- **极简输入**：粘贴堆栈 → 点生成，无任何前置选择。
- **报告渲染复用**：双段式正文抽成 `frontend/src/components/ReportBody.tsx`，主页卡片与历史详情抽屉共用，
  复制诊断包逻辑只维护一份。
- **历史详情**：历史列表「详情」→ 嵌套 Drawer，展示完整报告 + 诊断回填记录（就地追加）。
- **状态筛选**：`GET /api/history?status=all|pending|diagnosed`，前端 Segmented 三段。
  - `pending` → `wrapper.isNull(finalDiagnosis)`；`diagnosed` → `wrapper.isNotNull(...)`。
- **新手引导**：空状态「试试示例堆栈」；生成中提示"5-30 秒"；非堆栈软提示（正则
  `/(Exception|Error|Caused by|^\s*at\s)/m` 不命中时警告，不阻断）。
- **测试隔离**：H2 内存库在测试类内共享，集成测试用 `@BeforeEach clearAll()` 保证计数断言独立。

## V1.3 多语言约定

- **语言识别**：`LanguageDetector`（启发式，按特征优先 Python→Go→Java→Node→unknown）。
  规则要点：Node 用 `(TypeError|...):` 与 `.js/.ts` 帧；Java 用 `java.lang.*` / `Caused by:` / `.java:N`。
  注意 `OutOfMemoryError:` 不能误判为 Node（Java 检查先于 Node 通用 Error）。
- **错误类型提取**：`ErrorExplainService.extractErrorType(stack, language)` 按语言分支
  （Python 取最后 `XXXError:` 行；Node 取错误名；Go 取 panic 信息截断）。
- **AI 提示词**：`OpenAiCompatAIService.systemPrompt(language)` 按语言切换角色，其余分诊约束不变。
- **Mock**：`MockAIService.explain(stack, techStack, language)` 按语言分发，复用共享错误提取。
- **契约字段**：`ExplainResponse` / `error_history` 增加 `language`（java/python/node/go/unknown，
  unknown 时错误类型回退 Java 规则）。

## V1.3 知识卡片约定

- `error_template` 表 + `sql/seed-templates.sql`（幂等种子 ≥7 类），`GET /api/templates?keyword=`。
- 卡片是**独立速查模块**，暂不与 AI 分诊联动。
- `spring.sql.init.encoding: UTF-8` 必须设置，否则 Windows 下读测试脚本中文会乱码。

## V1.4 AI 调用可观测性

- **`ai_call_log` 表**（`sql/migrate-v14.sql` 幂等建表）：`id / history_id / provider / model /
  status(success|fallback) / duration_ms / prompt_tokens / completion_tokens /
  input_excerpt(前200字符) / error_message / created_at`，索引 `idx_history_id`、`idx_created_at`。
- **落库位置**：`AIService` 两个实现**内部**（`AiCallRecorder.record()`）——token/状态只有调用处可知，
  不在 service 层猜状态。Mock 也记录：`provider=mock`、`status=success`（模板成功路径），没配 Key 也可观测。
- **接口签名变更**：`AIService.explain()` 返回 `AiCallOutcome(report, logId)`（而非裸 `PreTriageReport`），
  把日志 id 随结果带出，供 `ErrorExplainService` 在 `error_history` 入库后 `linkHistory(logId, historyId)` 回填关联。
- **token 解析**：真实响应 `usage.prompt_tokens/completion_tokens`，负数视为无 token（置 null）。
- **查询接口**：`GET /api/ai-logs?page&size&status&provider&historyId` → `PageResult<AiCallLog>`。
- **测试隔离**：H2 集成测试需在 `@BeforeEach` 里 `aiCallLogMapper.delete(null)`，否则日志跨用例累积。
- > **Warning**: `@Slf4j` logger 字段名是 `log`；在 AI service 方法内**勿把局部日志对象命名为 `log`**，
  > 会遮蔽 logger 导致 `log.warn(...)` 编译报错（本次已踩坑，用 `callLog` 命名）。

## V1.4 前端路由约定

- 引入 `react-router-dom`（v7，`BrowserRouter`），路由：`/` 分诊、`/knowledge` 异常速查、
  `/history` 历史、`/logs` 调用日志；Header 用 antd `Menu` + `useLocation` 高亮。
- 页面组件放 `frontend/src/pages/`；从 Drawer 升级为页面时保留页内嵌套 Drawer（如 HistoryPage 的详情抽屉）。

## V1.5 长文本存储约定

- **大字段用 MEDIUMTEXT**：`error_history.stack_trace/explanation/pre_report/final_diagnosis` 为
  MEDIUMTEXT(16MB)，避免 TEXT(64KB) 溢出（用户输入的堆栈不可控，类型须留足余量）。
- **追加型数据一律子表，禁止单列 JSON 数组追加**：项目 AI 诊断存 `history_diagnosis`（一行一条，
  `content` MEDIUMTEXT + `ts` VARCHAR(20)）；对外 `finalDiagnosis` 字段由子表**重建**（空→null、
  列表有诊断→"[]"），前端契约保持不变。状态筛选（pending/diagnosed）用
  `EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = error_history.id)`。
- **日志表必须有保留期**：`ai_call_log` 由 `AiLogRetentionTask`（`@Scheduled` 每日 03:00）按
  `ai.call-log-retention-days`（默认 30）清理；主类需 `@EnableScheduling`。
- **入库失败兜底**：`ErrorExplainService` 捕获 `DataAccessException` → 删除孤儿 `ai_call_log`
  （`AiCallRecorder.delete`）+ 抛 `BusinessException` 友好提示，保证"日志与记录一致"不裂开。
