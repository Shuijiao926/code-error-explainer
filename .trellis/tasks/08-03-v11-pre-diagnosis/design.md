# 产品重定位 V1.1 — 技术设计

> 本设计覆盖 V1.1 整体架构；**各阶段的权衡点单独列出，实现前逐个 brainstorm 人工介入判断**。

## 1. 总体变化

保持 V1.0 分层（Controller/Service/Mapper/AI/前端）不动，核心变化三点：
1. **AI 输出契约**从"五要素解释"重构为**双段式前置分诊报告**。
2. 新增**技术栈自动识别**模块。
3. **历史表扩展**，支持诊断回填闭环。

```
POST /api/explain {stackTrace, techStack?}
   → TechStackDetector 识别技术栈（可被用户修正）
   → AIService 按「分诊」角色输出 PreTriageReport
   → 入库（error_type / tech_stack / pre_report / summary）
   → 返回 {id, errorType, techStack, personReport, handoff, createdAt}
```

## 2. 数据模型变更（error_history）

```sql
-- 新增三列（迁移脚本需对已有库幂等）
ALTER TABLE error_history
  ADD COLUMN tech_stack       VARCHAR(255) NULL COMMENT '识别到的技术栈，逗号分隔',
  ADD COLUMN pre_report       TEXT         NULL COMMENT '前置分诊报告 JSON',
  ADD COLUMN final_diagnosis  TEXT         NULL COMMENT '项目AI回填的最终诊断';
```

- `pre_report` 存完整 `PreTriageReport` JSON（含 personReport + handoff）。
- `summary` 复用：取 `personReport.summary`，列表展示用。
- 旧数据：三列为 NULL，兼容读取。

## 3. AI 输出契约（PreTriageReport）

```json
{
  "personReport": {
    "summary": "一句话定性（大白话，非IT可懂）",
    "layer":   "出在哪一层（业务/框架/基础设施）+ 人话解释",
    "misuse":  "疑似技术使用不妥（人话，方向性，非断言）",
    "nextStep":"你现在可以怎么做（引导：复制诊断包发给项目 AI）"
  },
  "handoff": {
    "techStack": ["Spring Boot", "MyBatis"],
    "suspectedPoints": ["依赖注入可能未生效（@Service/@Autowired 或包扫描范围）"],
    "checkList": ["UserService 注解与包扫描路径", "是否存在对应单元测试覆盖该路径"],
    "rawError": "原始堆栈"
  }
}
```

- **AI 角色**：资深 Java 分诊专家。约束：人话通俗、只给方向不给断言、
  明确指出"哪些需要项目 AI 结合仓库上下文确认"。
- 前端从 handoff 字段拼一段**可直接喂给项目 AI 的提示词文本**（复制用）。
- Mock 提供者同步输出同构双段式（无 Key 也可端到端演示）。

## 4. 技术栈识别（TechStackDetector）

- 规则表：堆栈中的类名前缀 → 框架标签。

| 类名前缀（堆栈中出现即命中） | 标签 |
|---|---|
| `org.springframework.boot.*` | Spring Boot |
| `org.springframework.*` | Spring |
| `org.apache.ibatis.*` / `com.baomidou.mybatisplus.*` | MyBatis |
| `org.hibernate.*` / `jakarta.persistence.*` | JPA/Hibernate |
| `com.fasterxml.jackson.*` / `tools.jackson.*` | Jackson |
| `org.apache.catalina.*` / `org.apache.tomcat.*` | Tomcat |
| `io.netty.*` | Netty |
| `com.mysql.cj.*` / `java.sql.*` | MySQL/JDBC |
| `okhttp3.*` / `org.apache.http.*` | HTTP 客户端 |

- 扫描堆栈各行 class 名 → 去重 → 取前 N（如 5）个。
- 无命中 → `["通用/未知"]`。
- 用户传入 `techStack` 时**以用户值为准**（覆盖识别结果）。

## 5. 历史闭环

- `PUT /api/history/{id}/diagnosis`，body `{diagnosis}` → 写入 `final_diagnosis`。
- 列表：显示技术栈标签；搜索 keyword 匹配 `error_type / summary / tech_stack`。
- 详情：返回完整记录（含 pre_report、final_diagnosis）。

## 6. 前端

- `types/api`：新增 `PreTriageReport` / `PersonReport` / `Handoff`、回填接口。
- 输入区下方：识别出的技术栈（`Tag`，可点击进入修正）。
- 报告卡片：双段式——人话版区块 + 「项目AI诊断包」区块 + 「复制诊断包」按钮。
- 历史抽屉：技术栈标签、回填诊断输入、按技术栈搜索。

## 7. 关键权衡点（各阶段 brainstorm 议题）

| 阶段 | 待人工介入判断的权衡点 |
|------|------------------------|
| Phase 1 | ① 迁移幂等方式（存储过程 vs 说明文档 run-once）；② 报告 schema 定稿（字段命名/rawError 是否冗余）；③ 分诊提示词措辞 |
| Phase 2 | ④ 技术栈修正 UI（Tag 编辑 vs 下拉多选）；⑤ 复制文本格式（Markdown vs 纯文本 vs JSON） |
| Phase 3 | ⑥ 回填格式（纯文本 vs 结构化多轮可追加）；⑦ 回填是否允许修改/多轮 |
| Phase 4 | ⑧ 人话版风格（类比/emoji/分级措辞） |

## 8. 兼容性 / 风险

- `POST /api/explain` 响应为**破坏性变更**，前后端同仓库同步升级；回滚点 = `git revert`。
- AI 非 JSON 输出 → 沿用 V1.0 兜底（仍入库，不白屏）。
- 迁移脚本：需幂等；执行前备份库。
- 无本地 MySQL 环境 → 用 H2 集成测试覆盖数据链路（沿用 V1.0 模式）。
