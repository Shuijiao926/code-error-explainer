import { useMemo, useState } from 'react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { App as AntdApp, ConfigProvider, Layout, Menu, Space, Typography, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import Home from './pages/Home'
import KnowledgePage from './pages/KnowledgePage'
import HistoryPage from './pages/HistoryPage'
import LogsPage from './pages/LogsPage'
import ThemeToggle from './components/ThemeToggle'

const { Header, Content } = Layout

const NAV_ITEMS = [
  { key: '/', label: '分诊' },
  { key: '/knowledge', label: '异常速查' },
  { key: '/history', label: '历史' },
  { key: '/logs', label: '调用日志' },
]

export default function App() {
  const [darkMode, setDarkMode] = useState<boolean>(() => localStorage.getItem('cee-theme') === 'dark')
  const location = useLocation()

  const themeConfig = useMemo(
    () => ({
      algorithm: darkMode ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: { colorPrimary: '#1677ff' },
    }),
    [darkMode],
  )

  const toggleTheme = () => {
    const next = !darkMode
    setDarkMode(next)
    localStorage.setItem('cee-theme', next ? 'dark' : 'light')
  }

  const selectedKey = NAV_ITEMS.find((i) => location.pathname === i.key)?.key ?? '/'

  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        <Layout style={{ minHeight: '100vh' }}>
          <Header
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingInline: 24,
            }}
          >
            <Space size="middle">
              <Typography.Title level={4} style={{ margin: 0, color: 'inherit', whiteSpace: 'nowrap' }}>
                🔧 人话说八哥
              </Typography.Title>
              <Menu
                mode="horizontal"
                selectedKeys={[selectedKey]}
                items={NAV_ITEMS}
                style={{ background: 'transparent', minWidth: 320 }}
              />
            </Space>
            <ThemeToggle darkMode={darkMode} onToggle={toggleTheme} />
          </Header>
          <Content style={{ padding: 24 }}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/knowledge" element={<KnowledgePage />} />
              <Route path="/history" element={<HistoryPage />} />
              <Route path="/logs" element={<LogsPage />} />
            </Routes>
          </Content>
        </Layout>
      </AntdApp>
    </ConfigProvider>
  )
}
