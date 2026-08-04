# 实施计划：前端独立页面

> 顺序执行；每步完成保持可编译。

1. **加依赖**：`cd frontend && npm install react-router-dom`（写入 package.json）。
2. **main.tsx**：`<BrowserRouter>` 包裹 `<App/>`。
3. **App.tsx**：
   - import 改：去 `HistoryDrawer`/`TemplateDrawer`，加 `useLocation`、`Menu`、`Routes`/`Route`、三个页面组件。
   - Header：标题 + `<Menu mode="horizontal" selectedKeys={[当前路径]}>`（分诊 `/`、异常速查 `/knowledge`、历史 `/history`、日志 `/logs`）+ ThemeToggle。
   - Content：`<Routes>` 四个路由，`/logs` 先渲染 `Empty` 占位。
4. **新增 `pages/KnowledgePage.tsx`**：从 TemplateDrawer 抽取（容器 maxWidth 960 居中，挂载加载，保留搜索+卡片）。
5. **新增 `pages/HistoryPage.tsx`**：从 HistoryDrawer 抽取（页面容器 + 工具栏含清空；保留嵌套详情 Drawer + 诊断回填；挂载加载）。
6. **删除** `components/TemplateDrawer.tsx`、`components/HistoryDrawer.tsx`。
7. **验证**：`npm run build`（tsc）；`npm run dev` 检查 `/`、`/knowledge`、`/history` 与导航高亮、F5 刷新。

## 验证命令

```bash
cd frontend && npm run build
cd frontend && npm run dev   # 手动检查
```
