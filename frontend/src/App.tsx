import { useMemo, useState } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { App as AntdApp, ConfigProvider, Layout, Menu, Typography, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import Home from './pages/Home'
import KnowledgePage from './pages/KnowledgePage'
import KnowledgeBasePage from './pages/KnowledgeBasePage'
import HistoryPage from './pages/HistoryPage'
import LogsPage from './pages/LogsPage'
import ThemeToggle from './components/ThemeToggle'

const { Header, Content } = Layout

const NAV_ITEMS = [
  { key: '/', label: '分诊' },
  { key: '/knowledge', label: '异常速查' },
  { key: '/knowledge-base', label: '踩坑知识库' },
  { key: '/history', label: '历史' },
  { key: '/logs', label: '调用日志' },
]

export default function App() {
  const [darkMode, setDarkMode] = useState<boolean>(() => localStorage.getItem('cee-theme') === 'dark')
  const location = useLocation()
  const navigate = useNavigate()

  const themeConfig = useMemo(
    () => ({
      algorithm: darkMode ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: '#1677ff',
        // 统一圆角 / 卡片阴影 / 页面背景，深浅主题一致
        borderRadius: 6,
        borderRadiusLG: 10,
        boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04), 0 2px 8px rgba(0, 0, 0, 0.06)',
        boxShadowSecondary: '0 4px 12px rgba(0, 0, 0, 0.12)',
        colorBgLayout: darkMode ? '#0f0f13' : '#f5f6f8',
      },
    }),
    [darkMode],
  )

  const toggleTheme = () => {
    const next = !darkMode
    setDarkMode(next)
    localStorage.setItem('cee-theme', next ? 'dark' : 'light')
  }

  const selectedKey = NAV_ITEMS.find((i) => location.pathname === i.key)?.key ?? '/'

  // Header 背景/文字颜色跟随主题（antd Header 默认深底不随算法变，需手动适配）
  const headerBg = darkMode ? '#141414' : '#fff'
  const headerColor = darkMode ? 'rgba(255, 255, 255, 0.85)' : 'rgba(0, 0, 0, 0.88)'
  // 浅色下用边框分隔 Header 与内容区，深色下用更高亮度的底区分层级
  const headerBorder = darkMode ? '#1f1f1f' : '#eaecef'

  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        <Layout className={darkMode ? 'cee-layout cee-dark' : 'cee-layout'} style={{ minHeight: '100vh' }}>
          <Header
            className="cee-header"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              paddingInline: 24,
              background: headerBg,
              color: headerColor,
              borderBottom: `1px solid ${headerBorder}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0, flex: 1 }}>
              <Typography.Title
                level={4}
                style={{ margin: 0, color: 'inherit', whiteSpace: 'nowrap', flexShrink: 0 }}
              >
                🔧 人话说八哥
              </Typography.Title>
              <div style={{ overflowX: 'auto', flex: 1, minWidth: 0 }}>
                <Menu
                  mode="horizontal"
                  selectedKeys={[selectedKey]}
                  items={NAV_ITEMS}
                  onClick={({ key }) => navigate(key)}
                  style={{ background: 'transparent', minWidth: 'max-content' }}
                />
              </div>
            </div>
            <ThemeToggle darkMode={darkMode} onToggle={toggleTheme} />
          </Header>
          <Content className="cee-content" style={{ padding: 24 }}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/knowledge" element={<KnowledgePage />} />
              <Route path="/knowledge-base" element={<KnowledgeBasePage />} />
              <Route path="/history" element={<HistoryPage />} />
              <Route path="/logs" element={<LogsPage />} />
            </Routes>
          </Content>
        </Layout>
      </AntdApp>
    </ConfigProvider>
  )
}
