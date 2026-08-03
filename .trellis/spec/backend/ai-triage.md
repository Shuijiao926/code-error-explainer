# AI 前置分诊契约（V1.1）

> 产品定位：**前置分诊**——工具不做最终项目级诊断，产出人话说明 + 交给项目 AI 的诊断包。

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
