# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

<!--
Document your project's database conventions here.

Questions to answer:
- What ORM/query library do you use?
- How are migrations managed?
- What are the naming conventions for tables/columns?
- How do you handle transactions?
-->

(To be filled by the team)

---

## Query Patterns

<!-- How should queries be written? Batch operations? -->

(To be filled by the team)

---

## Migrations

<!-- How to create and run migrations -->

(To be filled by the team)

---

## Naming Conventions

<!-- Table names, column names, index names -->

(To be filled by the team)

---

## Common Mistakes

<!-- Database-related mistakes your team has made -->

(To be filled by the team)

---

## V1.0 实现经验（2026-08-03 沉淀）

### ORM 选型（Spring Boot 4 必须用对 starter）

- 本工程是 **Spring Boot 4.0.x**，MyBatis-Plus 必须用 `com.baomidou:mybatis-plus-spring-boot4-starter`（**≥3.5.13**），
  不能用 `mybatis-plus-boot-starter`(SB2) 或 `mybatis-plus-spring-boot3-starter`(SB3)。
- 自 MP 3.5.9 起分页拦截器拆分独立，**必须额外引入** `com.baomidou:mybatis-plus-jsqlparser`，否则分页静默失效。
- 不要同时引入 `mybatis` / `mybatis-spring-boot-starter` / `mybatis-spring`，会版本冲突。
- 分页插件：`PaginationInnerInterceptor(DbType.MYSQL)`，配合 `Page` + `LambdaQueryWrapper` 使用。

### 数据库依赖

- 运行时用 MySQL 8：`sql/init.sql` 一键建库建表（库名 `code_error_explainer`）。
- **测试用 H2 内存库**（test scope，不污染运行时）：`src/test/resources/application-test.yaml` +
  `schema-test.sql`。无本地 MySQL 也能跑通全链路集成测试。H2 连接串：
  `jdbc:h2:mem:explainer;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1`。

### 命名约定

- 表/列名 snake_case，实体驼峰；`map-underscore-to-camel-case: true`。
- 主键 `@TableId(type = IdType.AUTO)`，插入后自动回填 id。
- `created_at` 走 DB 默认 `CURRENT_TIMESTAMP`，插入时不传（MP NOT_NULL 字段策略自动省略 null 列）。

### 陷阱

- 列表接口不要 SELECT 大字段（stack_trace/explanation 是 TEXT），用 `wrapper.select(...)` 只取轻量列。
- `historyMapper.delete(null)` 会删除全表，用作「清空」接口。

---

## V1.1 实现经验（2026-08-03 沉淀）

### 幂等迁移模式（MySQL）

- MySQL 8 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`，幂等迁移用「存储过程 + INFORMATION_SCHEMA 检查」实现，
  见 `sql/migrate-v11.sql`（可重复执行，列已存在自动跳过）。
- 增量加列，不要动已有 NOT NULL 约束的旧列；新列一律可空。

### 追加式诊断回填

- 「项目 AI 最终诊断」支持多条追加：`final_diagnosis` 列存 JSON 数组
  `[{"ts":"yyyy-MM-dd HH:mm:ss","content":"…"}]`；空 = NULL（未诊断）。
- 追加用 readValue(TypeReference<List>) 解析 → add → writeValueAsString → updateById，不要手拼 JSON。
- 列表 select 需包含 `final_diagnosis`（判断「已诊断/待回填」状态），但它是 TEXT，记录量上来后可考虑拆表。

### 技术栈识别

- 服务端规则表（类名前缀 → 框架标签）见 `service/TechStackDetector.java`；前端通过 `POST /api/detect`
  预填选择器，**不要在前后端重复实现识别逻辑**。
- 规则注意优先级：`org.springframework.boot` 必须先于 `org.springframework`（命中 Boot 不再标 Spring）。

---

## V1.3 实现经验（2026-08-03 沉淀）

### 知识卡片表（error_template）

- 建表：`sql/init.sql` 包含 `error_template` 建表语句；种子数据文件 `sql/seed-templates.sql`（幂等 INSERT IGNORE）。
- 实体：`entity/ErrorTemplate.java`，轻量 POJO，`cause_top3` 存 JSON 数组，`fix_template` 存代码模板。
- 接口：`GET /api/templates?keyword=`（Keyword 同时匹配 error_type + category），不调 AI，离线可用。
- 种子数据覆盖 ≥7 类：NullPointerException / ClassCastException / ConcurrentModificationException /
  SQLException / OutOfMemoryError / StackOverflowError / TimeoutException / IllegalArgumentException。
- 每类卡片包含：error_type（唯一）、category、cause_top3(JSON 数组)、fix_template、hierarchy（继承链）。

### 中文编码陷阱（Windows）

- **`spring.sql.init.encoding: UTF-8` 必须显式设置**。Windows 默认编码非 UTF-8，不设此项会导致
  `seed-templates.sql` 中的中文（如 category "空指针"、fix_template 中文注释）读入后乱码，
  测试中搜索中文关键字匹配不到任何行。
- 测试配置（`application-test.yaml`）同样需要此设置。
- H2 内存库模式下同样受影响——H2 不自动推断脚本编码。

### application.yaml 配置一致性

- `application.yaml` 中 `ai.model` 和 `ai.base-url` 的值必须与 Java 默认值（`AiProperties.java`）
  保持一致。随意修改为无效值（如不存在的模型名、错误的 API 端点）会导致运行时 AI 调用静默失败，
  所有请求进入 fallback 路径。
- 任何运行时配置变更都应先在 `AiProperties.java` 默认值中同步调整，再改 yaml，避免默认值与现实脱节。
