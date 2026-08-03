# 代码异常智能解释系统 V1.0 — 执行计划

顺序：**依赖验证 → DB → 后端骨架 → AI 层 → 业务 → 接口 → 后端冒烟 → 前端 → 联调 → 全量验证**

## 实施清单

1. ✅ **依赖解析**：SB4.0.7 + `mybatis-plus-spring-boot4-starter` 3.5.16 + `mybatis-plus-jsqlparser` 编译通过。
2. ✅ **数据库**：`sql/init.sql`；`application.yaml` 配数据源（env 可覆盖）+ `ai.*` 配置。
3. ✅ **后端骨架**：`Result` / `ErrorHistory` / `ErrorHistoryMapper` / `MybatisPlusConfig`（分页）。
4. ✅ **AI 层**：`AIService` + `OpenAiCompatAIService`（围栏剥离+兜底）+ `MockAIService` + `AIConfig`。
5. ✅ **业务层**：`ErrorExplainService`、`HistoryService`（分页/搜索/详情/删除/清空）。
6. ✅ **接入层**：`ErrorExplainController`、`WebConfig`（CORS）、`GlobalExceptionHandler`。
7. ✅ **后端冒烟**：`/api/health` 返回 ok；explain 无 DB 时优雅降级；全链路由 H2 集成测试验证。
8. ✅ **前端脚手架**：Vite + React + TS + AntD5 + axios；vite 代理 `/api`。
9. ✅ **前端功能**：Home（输入/结果卡片/复制）、HistoryDrawer（列表/搜索/回看/删除/清空）、深色模式。
10. ✅ **联调**：dev 服务器 200、代理 `/api/health → 8080` 200。
11. ✅ **全量验证**：`mvn test` 8 用例全绿；`npm run build` 通过。

> 收尾备注：本机无本地 MySQL（仅有远端 192.168.1.101:3306，无凭据），真实 MySQL 联调待用户环境；
> 项目目录当前**不是 git 仓库**，Phase 3.4 提交待用户确认是否 `git init`。

## 验证命令

- 后端：`mvn -q compile` / `mvn test` / `mvn spring-boot:run`
- 冒烟（另起终端）：
  ```bash
  curl http://localhost:8080/api/health
  curl -X POST http://localhost:8080/api/explain -H 'Content-Type: application/json' \
       -d '{"stackTrace":"java.lang.NullPointerException\n\tat com.app.UserService.login(UserService.java:47)"}'
  curl "http://localhost:8080/api/history?page=1&size=20&keyword=NPE"
  ```
- 前端：`npm install` / `npm run dev` / `npm run build`

## 风险点 / 回滚点

- **依赖解析失败** → 回退步骤 1，调整 MP 版本。
- **MySQL 不可用** → 检查 `init.sql` 执行与连接串；确保本地 MySQL 8 运行。
- **AI 返回格式异常** → 兜底逻辑兜住，不阻塞主链路；`mvn test` 覆盖该分支。
- 任一步骤未验证通过，不进入下一步。

## 收尾检查（Phase 3）

- 最后 2.2 全量：列出受影响包 → 按各 spec Quality Check 复查跨层一致性。
- 3.3 更新 spec（沉淀 SB4+MP4 组合、mock 模式、异常提取正则等经验）。
- 3.4 批量提交（work → archive → journal），随后 `/trellis:finish-work`。
