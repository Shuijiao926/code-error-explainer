# V1.2 交互体验优化 — 执行计划（分阶段）

> 4 个阶段，每阶段独立可验证；权衡点先 brainstorm 人工介入再动手。

## Phase 1 — 简化输入（去技术栈选择） ✅
- [x] 1.1 后端：删 `ExplainRequest.techStack`、删 `/api/detect`、`explain(stackTrace)` 签名收敛、识别改内部
- [x] 1.2 前端：删 Home 技术栈 Select / detectStack / 相关状态与 ref
- [x] 1.3 测试适配（删 override 用例）；`mvn test` + `npm run build` 绿

## Phase 2 — 历史详情查看 ✅
- [x] 2.1 抽 `ReportBody` 组件（人话版 + 诊断包 + 复制），`ExplanationCard` 复用
- [x] 2.2 历史抽屉内嵌套详情 Drawer：完整报告 + 回填记录 + 就地追加
- [x] 2.3 移除「跳主页」旧交互；验证 `mvn test` + `npm run build`

## Phase 3 — 交互细节 ✅
- [x] 3.1 空状态引导 + 「试试示例堆栈」
- [x] 3.2 生成中时长提示
- [x] 3.3 非堆栈软校验（警告不阻断）
- [x] 3.4 后端 `status` 参数（all/pending/diagnosed）+ 前端 Segmented 筛选 + 测试
- [x] 3.5 验证：`mvn test` 15 全绿 + `npm run build`（修复 H2 测试共享库隔离）

## Phase 4 — 全流程验证与文档 ✅
- [x] 4.1 端到端走查：真实AI分诊(id=5) → 回填(UTF-8, MySQL确认) → diagnosed/pending 筛选
- [x] 4.2 README 交互流程更新（端口 3000 + 交互流程图）
- [x] 4.3 全量验证 + spec 沉淀（ai-triage V1.2 交互约定）+ 提交

## 验证命令

```bash
mvn -q test
cd frontend && npm run build
# 冒烟
curl -s http://localhost:8080/api/history?status=pending
```

## 风险点 / 回滚点

- 详情 Drawer 与回填弹窗合并可能动交互较多 → 保留回填弹窗为回退（仅当详情内回填遇到阻力）。
- `/api/detect` 删除为内部收敛，回滚 = git revert。
