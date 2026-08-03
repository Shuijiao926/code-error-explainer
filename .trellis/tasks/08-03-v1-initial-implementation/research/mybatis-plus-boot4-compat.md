# 研究：Spring Boot 4.0.x 与 MyBatis-Plus 兼容性

> 日期：2026-08-03 · 结论：无需降级 Spring Boot，保持脚手架 4.0.7

## 背景

脚手架（start.spring.io 生成）用的是 Spring Boot **4.0.7**，而 `02-技术选型-架构设计.md`
里写的是 Spring Boot 3.3 + MyBatis-Plus 3.5.7。需确认 4.0.7 下 MyBatis-Plus 是否可用。

## 结论

| 项 | 值 |
|----|----|
| starter 坐标 | `com.baomidou:mybatis-plus-spring-boot4-starter` |
| 版本要求 | **≥ 3.5.13**（Spring Boot 4 官方支持起点） |
| 推荐版本 | **3.5.16**（2026-01 发布，对应 Spring Boot 4.0.x） |
| 分页额外依赖 | `com.baomidou:mybatis-plus-jsqlparser`（自 3.5.9 起分页拦截器拆分独立，必须加） |

## 关键规则

1. **starter 必须匹配 Spring Boot 大版本**：SB2→`mybatis-plus-boot-starter`，SB3→`mybatis-plus-spring-boot3-starter`，SB4→`mybatis-plus-spring-boot4-starter`。
2. **不要**同时引入 `mybatis` / `mybatis-spring-boot-starter` / `mybatis-spring`，会版本冲突。
3. 分页 `PaginationInnerInterceptor` 需依赖 `mybatis-plus-jsqlparser`，缺失时分页静默失效。

## 来源

- MyBatis-Plus 官方更新日志：https://baomidou.com/resources/changlog/
- MyBatis-Plus 安装文档：https://baomidou.com/en/getting-started/install/
- 社区实战（SB4.0.3 + MP 3.5.15）：https://zeeklog.com/spring-boot-4-gao-dong-mybatis-plusde-yong-fa
