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
