# V1.2 交互体验优化 — 技术设计

## 1. 总体变化

- 输入区回归"纯粘贴"：技术栈识别完全后台化。
- 历史详情抽屉化：完整报告 + 回填记录在同一上下文内查看。
- 交互细节补位：空状态 / 生成提示 / 软校验 / 状态筛选。

## 2. Phase 1 — 简化输入（去掉技术栈选择）

### 后端
- `dto/ExplainRequest`：删除 `techStack` 字段。
- `controller/ErrorExplainController`：删除 `/api/detect` 方法。
- `service/ErrorExplainService.explain(String stackTrace, List<String> techStackOverride)`
  → `explain(String stackTrace)`；内部 `TechStackDetector.detect()` 作为唯一来源。
- `service/ai/AIService.explain(stackTrace, techStack)` 签名**保留**（后端传入识别结果）。
- `TechStackDetector` 保留。
- 测试：删除 `techStackOverride_winsOverAutoDetect`（无覆盖参数了）；其余断言适配。

### 前端
- `pages/Home.tsx`：删除 `Select` 技术栈控件、`detecting` 状态、`/api/detect` 防抖调用、`userEdited` ref、`techStack` 状态。
- `services/api.ts`：删除 `detectStack`。
- `types/index.ts`：保留 `ExplainResponse.techStack`（输出展示用）。

## 3. Phase 2 — 历史详情查看

### 交互
- 历史列表每条新增「详情」按钮 → 在历史抽屉内打开**嵌套 Drawer**。
- 详情 Drawer 内容：
  - 头部：异常类型 + 技术栈标签 + 时间。
  - 人话版四段 + 项目AI诊断包（suspectedPoints / checkList）+ 「复制诊断包」。
  - 诊断回填记录（多条带时间戳）+ 追加输入框（就地回填）。
- 移除「点异常类型 → 跳主页」的旧事件交互；主页只保留本次生成的结果。

### 组件复用（防重复）
- 抽 `components/ReportBody.tsx`：渲染 personReport + handoff + 复制按钮；
  `ExplanationCard` 与历史详情 Drawer 共用（人话版/诊断包渲染逻辑单点维护）。

### 数据
- 复用 `fetchHistoryDetail(id)`（已返回完整记录 + preReport + finalDiagnosis）。

## 4. Phase 3 — 交互细节

### 空状态引导（Home）
- 无结果时：引导文案 + 「试试示例堆栈」按钮，点击填入一段 NPE 示例。

### 生成中提示（Home）
- 按钮 loading 旁追加文案"AI 分诊中，通常需要 5-30 秒"。

### 非堆栈软校验（Home）
- 前端启发式：粘贴文本不含 `Exception|Error|Caused by|^\s*at ` 时，提示
  "这看起来不像 Java 异常堆栈，仍可继续尝试"（Alert 警告，不阻断）。

### 历史状态筛选
- 后端 `GET /api/history` 增加 `status` 参数：`all`（默认）/ `pending` / `diagnosed`。
  - `pending` → `wrapper.isNull(finalDiagnosis)`；`diagnosed` → `wrapper.isNotNull(finalDiagnosis)`。
- 前端历史抽屉顶部用 `Segmented`（全部 / 待回填 / 已诊断）。

## 5. 兼容性 / 风险

- `/api/detect` 删除、`techStack` 参数删除均为内部 API 收敛，前后端同仓库同步改。
- 旧记录（无 preReport）详情层需兜底（空字段展示"—"）。
- 测试保持 H2 + Mock 全链路覆盖。

## 6. 涉及文件（预估）

- 后端：`ExplainRequest` / `ErrorExplainController` / `ErrorExplainService` / `HistoryService`（status 参数）/ 相关测试。
- 前端：`Home.tsx` / `api.ts` / `HistoryDrawer.tsx` / 新增 `ReportBody.tsx` / `ExplanationCard.tsx` / `types`。
