# 设计：前端独立页面

## 路由与依赖

- **依赖**：`react-router-dom@^6`（BrowserRouter 模式）。
- **入口**：`main.tsx` 用 `<BrowserRouter>` 包裹 `<App/>`。
- **部署备注**：若将来部署到无 SPA fallback 的静态服务器，深层链接需改 HashRouter；本次默认 BrowserRouter（Vite dev 无影响）。

## 页面结构

```
main.tsx
└─ <BrowserRouter>
   └─ <App>            (Layout)
      ├─ <Header> 标题 + <Menu> 导航(useLocation 高亮) + ThemeToggle
      └─ <Content>
         ├─ <Routes>
         │   ├─ /          → <Home/>              （分诊，不动）
         │   ├─ /knowledge → <KnowledgePage/>
         │   ├─ /history   → <HistoryPage/>
         │   └─ /logs      → <LogsPage/>          （v14-ai-call-log 提供；本任务留占位）
         └─ ...
```

## 组件抽取

| 来源（删除） | 去向（新增） | 关键调整 |
|---|---|---|
| `components/TemplateDrawer.tsx` | `pages/KnowledgePage.tsx` | 去 Drawer 外壳 → 页面容器；`useEffect` 从 `open` 触发改为挂载触发（`[]`）；`onSearch` 保持 |
| `components/HistoryDrawer.tsx` | `pages/HistoryPage.tsx` | 去外层 Drawer → 页面容器 + 工具栏（清空按钮放页面内）；**保留嵌套详情 Drawer**（DetailState 逻辑原样）；挂载触发加载 |

## 边界

- `App.tsx` 不再持有 `historyOpen/templateOpen` 状态，删除两个 Drawer import。
- 复用 `services/api.ts` 现有接口；`ReportBody`、`ExplanationCard`、`ThemeToggle` 不动。
- `/logs` 路由占位：本任务先渲染一个 antd `Empty` 占位组件（或临时内联），v14-ai-call-log 替换为真实 LogsPage。
