# 产品重定位 V1.1 — 执行计划（分阶段）

> 分 4 个阶段，每阶段独立可验证、可回滚；**每阶段的权衡点先 brainstorm 人工介入判断，再动手实现**。

## Phase 1 — 数据模型与后端分诊内核 ✅
- [x] 1.1 **brainstorm**：迁移幂等方式=幂等脚本 / 报告 schema 四段式（含 nextStep）定稿 / 分诊提示词确认
- [x] 1.2 数据库：`sql/init.sql` 加三列 + `sql/migrate-v11.sql`（INFORMATION_SCHEMA 幂等迁移）
- [x] 1.3 `TechStackDetector` 规则表 + 单元测试（命中/未命中/用户覆盖/Spring Boot 去重）
- [x] 1.4 DTO：`PreTriageReport`/`PersonReport`/`Handoff`；AI 分诊提示词；OpenAi/Mock 双实现改输出（删旧 `Explanation`）
- [x] 1.5 `ErrorExplainService` 接入 tech_stack + pre_report + 入库（explanation 兼容旧列）
- [x] 1.6 验证：`mvn test` 14 用例全绿

## Phase 2 — 前端双段式报告 ✅
- [x] 2.1 **brainstorm**：技术栈修正 UI=下拉多选预设（可自定义）；复制格式=Markdown 提示词
- [x] 2.2 `types/api` 更新（PreTriageReport/PersonReport/Handoff、`POST /api/detect`）
- [x] 2.3 双段式报告卡片 + 「复制诊断包」按钮（Markdown 提示词）
- [x] 2.4 输入区技术栈自动识别预填 + 下拉多选修正（防抖 detect，用户手改后不覆盖）
- [x] 2.5 验证：`npm run build` 通过；`/api/detect` 运行时返回 `["Spring","Tomcat"]`

## Phase 3 — 历史闭环 ✅
- [x] 3.1 **brainstorm**：回填=多条追加（带时间戳）；列表加「已诊断/待回填」状态标签
- [x] 3.2 后端 `PUT /api/history/{id}/diagnosis`（DiagnosisEntry 数组追加）+ 搜索含 tech_stack + 列表 select 含 finalDiagnosis
- [x] 3.3 前端回填弹窗（已有记录+追加框）+ 技术栈/状态标签 + 搜索
- [x] 3.4 验证：`mvn test` 15 用例全绿（含回填追加测试）；`npm run build` 通过

## Phase 4 — 非IT友好打磨 ✅
- [x] 4.1 **brainstorm**：人话版风格=类比优先
- [x] 4.2 Mock 模板 + AI 系统提示词改为类比优先
- [x] 4.3 README 更新（V1.1 定位 + 双段式 + 历史闭环 + API 表）
- [x] 4.4 全量验证：`mvn test` 15 全绿 + `npm run build` 通过；spec 沉淀（ai-triage.md / database-guidelines V1.1 经验 / index 更新）

## 验证命令

```bash
mvn -q test                              # 后端全部用例
cd frontend && npm run build             # 前端 TS 检查 + 打包
# 冒烟
curl -X POST http://localhost:8080/api/explain -H 'Content-Type: application/json' \
     -d '{"stackTrace":"java.lang.NullPointerException\n\tat com.app.UserService.login(UserService.java:47)"}'
curl -X PUT http://localhost:8080/api/history/1/diagnosis -H 'Content-Type: application/json' \
     -d '{"diagnosis":"@Service 缺失，已补上"}'
```

## 风险点 / 回滚点

- explain 接口破坏性变更 → 前后端同仓库同步升级，回滚点 = `git revert`。
- AI 输出非 JSON → 沿用兜底（仍入库，不白屏）。
- 迁移脚本 → 幂等设计 + 执行前备份。
- 无本地 MySQL → H2 集成测试覆盖数据链路。

## 收尾检查（Phase 3 / Phase 4 末尾）

- 最后 2.2 全量：跨层一致性复查（types↔DTO、API 路径、搜索字段）。
- 3.3 更新 spec（技术栈识别规则、双段式契约、迁移经验）。
- 3.4 批量提交（work → archive → journal），随后 `/trellis:finish-work`。
