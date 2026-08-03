# V1.3 功能增强 — 技术设计

## 1. 总体变化

三个独立可交付：**粘贴即检测**（前端）、**知识卡片库**（新表+API+页面）、**多语言**（识别+分诊扩展）。

## 2. Phase 1 — 粘贴即检测（前端）

- `Home.tsx` 输入区增加**双态即时提示**（输入变化即算，无需请求）：
  - `isJavaStack(text)` 正则命中（`(?:Exception|Error|Caused by|^\s*at\s)`）→ 绿色 Tag"检测到 Java 异常堆栈，可直接生成"。
  - 否则 → 黄色 Tag"未检测到 Java 异常堆栈，可能不是程序报错"。
- 取代 V1.2 的单一警告 Alert；仍不阻断生成。
- 多语言后该提示扩展为"识别到 X 语言堆栈"（复用 Phase 3 语言识别思路，前端先只判 Java）。

## 3. Phase 2 — 内置知识卡片库

### 数据模型（error_template，原 PRD §6）
```sql
CREATE TABLE error_template (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  error_type  VARCHAR(100) NOT NULL UNIQUE,
  category    VARCHAR(50),
  cause_top3  TEXT,        -- JSON 数组
  fix_template TEXT,
  hierarchy   VARCHAR(500),
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
- 种子数据：NPE / ClassCastException / ConcurrentModificationException / SQLException /
  OutOfMemoryError / StackOverflowError / TimeoutException（`sql/seed-templates.sql`，INSERT ... ON DUPLICATE KEY UPDATE 幂等）。

### 后端
- `entity/ErrorTemplate` + `mapper/ErrorTemplateMapper` + `service/TemplateService`。
- `GET /api/templates?keyword=` → `List<ErrorTemplate>`（keyword 匹配 error_type/category）。

### 前端
- 头部新增「📚 异常速查」按钮 → 打开 Drawer：搜索框 + 卡片列表
  （异常类型 + 分类标签 + 成因 Top3 + 修复模板 + 继承关系）。
- 纯前端渲染，不调 AI。

## 4. Phase 3 — 多语言支持

### 语言识别（service/LanguageDetector）
```java
String detect(String text)   // "java" | "python" | "node" | "go" | "unknown"
```
规则（按优先级）：
- **java**：`(?:Exception|Error)` 类名 或 `^\s*at\s+[\w.$]+\(` 或 `java\.lang\..*(Exception|Error)`
- **python**：`Traceback \(most recent call last\)` 或 `File "..."` + `line \d+` 或 `^\s*raise `
- **node**：`(TypeError|ReferenceError|RangeError|SyntaxError):` 或 `at .+\(.+\.(js|ts):\d+:\d+\)`
- **go**：`panic:` 或 `goroutine \d+ \[` 或 `\.go:\d+`
- 默认回退：java 正则能命中则 java，否则 unknown。

### 错误类型提取（按语言）
- `ErrorExplainService.extractErrorType` 改为按语言分支：
  - java：现有正则（简单类名）。
  - python：Traceback 最后一行 `XXXError: msg` → 取 `XXXError`。
  - node：`TypeError: msg` 等 → 取错误名。
  - go：`panic: ...` → 取 `panic` 后第一段（best-effort，如 `runtime error`）。
- 兜底 "Unknown"。

### AIService 扩展
- 签名：`explain(String stackTrace, List<String> techStack, String language)`。
- `OpenAiCompatAIService`：系统提示词按语言模板化（"资深 Java/Python/Node.js/Go 开发与调试专家"）；
  用户消息带上语言与已识别技术栈。
- `MockAIService`：`buildForType` 按语言分发（Java 用现有模板；Python/Node/Go 提供通用分诊模板，
  常见错误给定制文案）。
- `fallback` 兜底逻辑保持语言无关。

### 响应与存储
- `ExplainResponse` 增加 `language`；`error_history` 增加 `language VARCHAR(20) NULL`（`sql/migrate-v13.sql` 幂等）。
- 前端结果卡片 meta 展示语言。

## 5. 兼容性 / 风险

- `AIService.explain` 签名变化（+language）为内部收敛，前后端同仓库同步改。
- 多语言识别为启发式，未知内容回退 Java 正则，不阻断。
- 旧记录 language 为 NULL，前端兜底"未知"。
- 知识卡片为独立模块，不动分诊链路。

## 6. 涉及文件（预估）

- 后端：`LanguageDetector`(新)、`ErrorTemplate`/`ErrorTemplateMapper`/`TemplateService`(新)、
  `ErrorExplainService`、`AIService`/`OpenAiCompat`/`Mock`、`ExplainResponse`、`ErrorHistory`、
  `ErrorExplainController`、`sql/seed-templates.sql`、`sql/migrate-v13.sql`、测试。
- 前端：`Home.tsx`（双态提示）、`api.ts`/`types`（templates/language）、
  `TemplateDrawer.tsx`(新)、`App.tsx`（速查入口）、`ExplanationCard`/`HistoryDrawer`（语言展示）。
