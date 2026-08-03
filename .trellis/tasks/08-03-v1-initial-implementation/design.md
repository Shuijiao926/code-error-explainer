# 代码异常智能解释系统 V1.0 — 技术设计

## 1. 架构总览

```
React 18 + TS + AntD5 (frontend/)  --HTTP/JSON-->  Spring Boot 4.0.7（项目根目录）
                                                     │
                                             MyBatis-Plus 3.5.16
                                                     │
                                                     ▼
                                                  MySQL 8
                                                     ▲
                                      ┌──────────────┴──────────────┐
                                      │  AIService 接口              │
                                      │  ├─ OpenAiCompatAIService   │  DeepSeek / OpenAI
                                      │  └─ MockAIService           │  无 Key 离线 demo
                                      └─────────────────────────────┘
```

## 2. 目录结构

```
D:\Project\code-error-explainerdemo\
├── pom.xml                                  # Spring Boot 4.0.7（沿用脚手架，新增依赖）
├── sql/init.sql                             # 建库建表 error_history
├── src/main/java/com/org/codeerrorexplainerdemo/
│   ├── CodeErrorExplainerdemoApplication.java   # 已有
│   ├── common/Result.java                   # 统一响应 {code,message,data}
│   ├── config/WebConfig.java                # CORS
│   ├── config/MybatisPlusConfig.java        # 分页拦截器
│   ├── config/AIConfig.java                 # AI provider 装配
│   ├── controller/ErrorExplainController.java
│   ├── service/ErrorExplainService.java
│   ├── service/HistoryService.java
│   ├── service/ai/AIService.java
│   ├── service/ai/OpenAiCompatAIService.java
│   ├── service/ai/MockAIService.java
│   ├── mapper/ErrorHistoryMapper.java
│   ├── entity/ErrorHistory.java
│   └── dto/
│       ├── ExplainRequest.java
│       ├── ExplainResponse.java
│       ├── Explanation.java
│       └── PageResult.java
└── frontend/                                # Vite + React + TS + AntD5
    ├── vite.config.ts                       # proxy /api → http://localhost:8080
    ├── index.html
    ├── tsconfig.json / package.json
    └── src/
        ├── main.tsx / App.tsx
        ├── pages/Home.tsx
        ├── components/{StackInput,ExplanationCard,HistoryDrawer,ThemeToggle}.tsx
        ├── services/api.ts                  # axios 封装
        └── types/index.ts
```

> 说明：设计文档原文建议 `backend/` 子目录 + 包名 `com.bage`，但脚手架已建在根目录
> 且包名为 `com.org.codeerrorexplainerdemo`，**不迁移**，避免无谓 churn。

## 3. 依赖变更（pom.xml）

| 依赖 | 版本 | 用途 |
|------|------|------|
| `spring-boot-starter-web` | 由 parent 管理 | REST / RestClient / Jackson |
| `com.baomidou:mybatis-plus-spring-boot4-starter` | **3.5.16** | SB4 专用 ORM starter（≥3.5.13） |
| `com.baomidou:mybatis-plus-jsqlparser` | 3.5.16 | 分页拦截器（3.5.9 起拆分独立，必加） |
| `com.mysql:mysql-connector-j` | runtime | MySQL 驱动 |
| `org.projectlombok:lombok` | provided | @Data/@Builder 简化 |

**约束**：不引入 `mybatis` / `mybatis-spring-boot-starter` / `mybatis-spring`，避免与 MP 冲突。

## 4. 数据模型（error_history）

```sql
CREATE TABLE error_history (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  error_type  VARCHAR(100) NOT NULL,
  stack_trace TEXT         NOT NULL,
  explanation TEXT         NOT NULL,   -- AI 解释的 JSON 文本
  summary     VARCHAR(500) NOT NULL,   -- 一句话概要 = overview
  source_ip   VARCHAR(50),
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_error_type (error_type),
  INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `created_at` 由 DB 默认值生成；插入时 entity 为 null，MP 的 NOT_NULL 字段策略自动省略该列。
- 实体用 `@TableId(type = IdType.AUTO)`，插入后回填 id。

## 5. 接口契约

统一返回 `Result<T>{code, message, data}`；成功 code=200。

| Method | Path | 入参 | data |
|--------|------|------|------|
| POST | `/api/explain` | `{stackTrace}` | `ExplainResponse` |
| GET | `/api/history` | `page=1, size=20, keyword` | `PageResult<HistoryItem>` |
| GET | `/api/history/{id}` | - | 完整 `ErrorHistory` |
| DELETE | `/api/history/{id}` | - | `null` |
| DELETE | `/api/history` | - | `null`（清空） |
| GET | `/api/health` | - | `"ok"` |

`ExplainResponse`：
```json
{ "id": 1, "errorType": "NullPointerException", "summary": "…",
  "explanation": { "overview": "…", "location": "…", "rootCause": "…",
                   "fixSteps": ["…"], "prevention": "…" },
  "createdAt": "2026-08-03 09:00:00" }
```

**异常类型提取**：`Pattern.compile("([\\w.]+(?:Exception|Error))")` 匹配堆栈首行区域，
取简单类名（`lastIndexOf('.')+1`）；无匹配 → `Unknown`。

## 6. AI 接入层

### 6.1 接口
```java
public interface AIService {
    Explanation explain(String stackTrace);
}
```

### 6.2 OpenAiCompatAIService（DeepSeek / OpenAI / 兼容端点）
- 用 Spring `RestClient` POST `{baseUrl}/chat/completions`。
- body：`{model, messages:[{role:system, content:<PRD §8 系统提示词>}, {role:user, content:stackTrace}], temperature:0.3}`。
- 解析 `choices[0].message.content` → 剥掉 ```json 围栏 → Jackson 反序列化为 `Explanation`。
- **兜底**：解析失败/HTTP 非 2xx 时，返回 `Explanation`：overview=原始堆栈首行，其余字段 = 友好提示
  （"AI 返回格式异常，请检查 API Key 或稍后重试"），仍入库——用户不白屏。

### 6.3 MockAIService（无 Key 离线 demo）
- 根据提取到的 errorType 生成模板化解释（NPE/SQL 等常见类型给定制文案，其他给通用文案），
  保证未配置 Key 也能端到端演示。

### 6.4 装配（AIConfig）
- 读配置 `ai.provider` / `ai.api-key`；`provider=mock` 或 `api-key` 为空 → Mock，否则 OpenAI 兼容。

### 6.5 配置（application.yaml）
```yaml
ai:
  provider: deepseek
  api-key: ${AI_API_KEY:}
  model: deepseek-chat
  base-url: https://api.deepseek.com/v1
  timeout: 30s
```

## 7. 前端设计

- **构建**：Vite 5 + React 18 + TS 5 + AntD 5 + axios + highlight.js。
- **代理**：`vite.config.ts` 配置 `/api → http://localhost:8080`（开发免 CORS）。
- **页面/组件**：
  - `Home`：StackInput + 翻译按钮 + ExplanationCard（五要素卡片）+ 复制按钮。
  - `HistoryDrawer`：历史列表（倒序卡片）、搜索框、回看（载入完整结果）、单删、清空。
  - `ThemeToggle`：AntD `ConfigProvider.theme` 切换 darkAlgorithm/默认，localStorage 持久化。
- **类型**：`types/index.ts` 与后端 DTO 对齐（ExplainResponse/Explanation/HistoryItem/PageResult）。

## 8. 错误处理与日志

- Controller 层全局 `@RestControllerAdvice` 兜底：未捕获异常 → `Result.fail` + 日志 ERROR。
- 业务异常（如空堆栈、记录不存在）→ `Result.fail` 带中文提示。
- 日志规范：AI 调用记录耗时与失败原因（脱敏，不记完整堆栈/Key）。

## 9. 兼容性与风险

| 风险 | 对策 |
|------|------|
| SB4 + MP4 starter 较新 | implement.md 第 1 步先验证依赖可解析、可启动；失败则换 3.5.13+ 任一版本 |
| MySQL 需本地可用 | `sql/init.sql` 一键建库建表；连接失败给出清晰报错 |
| 无真实 AI Key | Mock 模式兜底，仅 demo；真实分析质量依赖 Key |
| AI 返回非 JSON | 兜底逻辑兜住，主链路不阻塞 |
| 清空接口误删 | 前端二次确认（AntD Popconfirm） |
