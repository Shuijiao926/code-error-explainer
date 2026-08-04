# 前端独立页面（异常速查 / 历史 / 日志路由框架）

## Goal & User Value

把目前以 Drawer 抽屉承载的「异常速查」「历史」升级为**独立页面**，并引入 react-router 建立 `/` `/knowledge` `/history` `/logs` 四个路由。用户可直接访问/刷新/分享页面，Header 导航高亮当前页。

## Requirements

### R1 引入路由
- 新增依赖 `react-router-dom@^6`；`main.tsx` 用 `<BrowserRouter>` 包裹。
- 路由表：`/`→Home（分诊）、`/knowledge`→知识卡片页、`/history`→历史页、`/logs`→日志页（本子任务只建路由占位/框架，页面实现属 v14-ai-call-log；本任务先不挂页面则留空占位）。

### R2 Header 导航
- Header 由「两个按钮」改为 antd `Menu` 水平导航：分诊/异常速查/历史（日志项在本任务预留，若 LogsPage 未实现可先不显示或占位）。
- `selectedKeys` 跟随 `useLocation` 高亮；保留标题与 `ThemeToggle`。

### R3 抽独立页面
- 新增 `pages/KnowledgePage.tsx`：把 `components/TemplateDrawer.tsx` 内容抽出（去 Drawer 外壳，页面容器 maxWidth 960 居中），加载时机改为**挂载时**，保留搜索与卡片渲染。
- 新增 `pages/HistoryPage.tsx`：把 `components/HistoryDrawer.tsx` 内容抽出（去外层 Drawer，**保留页面内嵌套的详情 Drawer** 及诊断回填逻辑），加载时机改**挂载时**，保留状态筛选/搜索/分页/清空。
- 删除 `components/TemplateDrawer.tsx`、`components/HistoryDrawer.tsx`。

## Acceptance Criteria

- [ ] `npm run build`（含 tsc）通过。
- [ ] `/` 分诊功能不回归；`/knowledge` 搜索/卡片可用；`/history` 列表/筛选/详情抽屉/诊断回填可用。
- [ ] Header 导航高亮随路由变化正确；路由刷新（F5）不白屏。

## Out of Scope

- `/logs` 页面内容（属 v14-ai-call-log）。
- 后端任何改动。
