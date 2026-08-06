# V1.3 功能增强 — 执行计划（分阶段）

> 4 个阶段，每阶段独立可验证；权衡点先 brainstorm 再动手。

## Phase 1 — 粘贴即检测 ✅
- [x] 1.1 `Home.tsx`：双态即时提示（绿色"检测到 Java 堆栈" / 黄色"未检测到"），替换 V1.2 单一警告
- [x] 1.2 验证：`npm run build`

## Phase 2 — 内置知识卡片库 ✅
- [x] 2.1 `sql/seed-templates.sql`（8 类幂等种子）+ `init.sql` 建表 + `migrate-v13`（含 language 列）
- [x] 2.2 后端：`ErrorTemplate`/Mapper/`TemplateService`/`GET /api/templates`
- [x] 2.3 前端：`TemplateDrawer` + 头部「📚 异常速查」入口
- [x] 2.4 测试（TemplateService）+ 修复 `spring.sql.init.encoding=UTF-8`（Windows 中文乱码）

## Phase 3 — 多语言支持 ✅
- [x] 3.1 `LanguageDetector` + 5 用例
- [x] 3.2 `extractErrorType` 按语言分支 + python/node/go 用例
- [x] 3.3 `AIService.explain(stack, techStack, language)`；OpenAi 提示词按语言模板化；Mock 分语言模板
- [x] 3.4 `language` 字段：实体/响应/schema/migrate + 前端结果/历史详情展示
- [x] 3.5 验证：`mvn test` 26 全绿 + `npm run build` + 真实 AI python 分诊冒烟

## Phase 4 — 验证与收尾 ✅
- [x] 4.1 端到端走查：migrate+seed 应用 → templates(8 类/搜索) → python 真实分诊(lang/type 正确) → language 列
- [x] 4.2 README 更新（V1.3 功能 + API 表 + 版本规划）
- [x] 4.3 spec 沉淀（多语言约定 + 知识卡片约定）+ 提交

## 验证命令

```bash
mvn -q test
cd frontend && npm run build
# 冒烟
curl "http://localhost:8080/api/templates?keyword=NPE"
curl -X POST http://localhost:8080/api/explain -H 'Content-Type: application/json' \
     -d '{"stackTrace":"Traceback (most recent call last):\n  File \"app.py\", line 5, in <module>\n    raise ValueError(\"bad\")"}'
```

## 风险点 / 回滚点

- 多语言识别启发式，不保证 100%；未知回退 Java，不阻断。
- `AIService` 签名变化为内部收敛，回滚 = git revert。
- `error_template` 新增表与种子幂等，不破坏现有数据。
