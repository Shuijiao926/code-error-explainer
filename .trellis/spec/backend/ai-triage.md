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
- 用户传入 `techStack` 时**以用户值为准**，并覆盖 `handoff.techStack` 保持一致。
