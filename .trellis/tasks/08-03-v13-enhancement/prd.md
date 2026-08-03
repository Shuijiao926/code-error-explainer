# V1.3 功能增强：粘贴即检测 / 知识卡片库 / 多语言支持

## Goal & User Value

从"仅 Java 单功能"迈向"**多语言 + 自解释**"：
1. **粘贴即检测**：粘贴时即时提示是否为 Java 异常堆栈，新手不再困惑。
2. **内置知识卡片库**：常见异常速查（成因/修复模板/继承链），**不调 AI 离线可用**。
3. **多语言支持**：Python / Node.js / Go 堆栈也能识别并生成双段式分诊。

## Confirmed Facts（用户决策 + 现状）

- 用户 V1.3 三方向全选：知识卡片库 + 粘贴即检测 + 多语言支持。
- V1.2 已交付：极简输入、历史详情抽屉、状态筛选、新手引导（含 V1.2 的"非堆栈软提示"）。
- `error_template` 表结构在原 PRD §6 已定义。
- 技术栈：Spring Boot 4 + MyBatis-Plus 后端、React + AntD5 前端（沿用）。

## Requirements（范围内）

### R1 粘贴即检测
- 输入时**即时**检测内容形态：
  - 是 Java 堆栈 → 绿色提示"检测到 Java 异常堆栈，可直接生成"。
  - 不是 → 黄色提示"未检测到 Java 异常堆栈，可能不是程序报错"。
- 均**不阻断**生成（沿用 V1.2 软校验思路，升级为双态提示）。

### R2 内置知识卡片库
- 建 `error_template` 表 + **种子数据**（≥7 类：NullPointerException / ClassCastException /
  ConcurrentModificationException / SQLException / OutOfMemoryError / StackOverflowError /
  TimeoutException…）。
- 每条：`error_type` / `category`（空指针/并发/SQL/内存/超时…）/ `cause_top3`(JSON) /
  `fix_template` / `hierarchy`（继承关系）。
- `GET /api/templates?keyword=` 列表 + 搜索。
- 前端新增「📚 异常速查」入口（头部），卡片式展示 + 搜索过滤，**不依赖 AI**。

### R3 多语言支持
- **语言识别**：按堆栈格式特征识别 Java / Python / Node / Go（Java 的 `at ...`、Python 的
  `Traceback (most recent call last)`、Node 的 `at ...(file.js:N:M)`、Go 的 `panic: runtime error`…）。
- **错误类型提取**按语言适配（Java 类名 / Python 异常名 / Node Error 名 / Go panic 信息）。
- **AI 提示词**按语言切换角色（Java/Python/Node/Go 分诊专家）。
- **Mock 模板**补 Python / Node / Go 通用分诊。
- 响应增加 `language` 字段；历史记录语言；前端展示。

## Acceptance Criteria

- [ ] AC1 粘贴 Java 堆栈 → 绿色提示；非堆栈 → 黄色提示；均不阻断生成。
- [ ] AC2 `/api/templates` 可查询；种子数据 ≥7 类；前端「异常速查」页可用（离线）。
- [ ] AC3 Python/Node/Go 堆栈能识别语言并生成双段式分诊（Mock 与真实 AI 均可）。
- [ ] AC4 响应含 `language`；历史详情展示语言。
- [ ] AC5 `mvn test` 全绿（含语言识别/知识卡片用例）；`npm run build` 通过。

## Out of Scope（后续）

- 知识卡片与 AI 分诊"疑似不妥"联动（未来可把卡片作为分诊依据）。
- 代码片段解释、Rust/其他语言。
- 用户系统。

## Key Decisions

1. 粘贴检测：前端即时正则双态提示；后端语言识别统一（Phase 3 复用同一思路）。
2. `error_template` 独立模块，不与 AI 分诊耦合（先做速查）。
3. 多语言：`AIService` 层按 `language` 切换提示词与解析；Mock 分语言模板。
4. 响应/实体加 `language` 字段（可空，兼容旧记录）。

## Open Questions

- 无阻塞项；细节权衡按阶段 brainstorm。
