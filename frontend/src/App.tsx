import { useMemo, useState } from 'react'
import { App as AntdApp, Button, ConfigProvider, Layout, Space, Typography, theme as antdTheme } from 'antd'
import { HistoryOutlined } from '@ant-design/icons'
import zhCN from 'antd/locale/zh_CN'
import Home from './pages/Home'
import HistoryDrawer from './components/HistoryDrawer'
import ThemeToggle from './components/ThemeToggle'

const { Header, Content } = Layout

export default function App() {
  const [darkMode, setDarkMode] = useState<boolean>(() => localStorage.getItem('cee-theme') === 'dark')
  const [historyOpen, setHistoryOpen] = useState(false)

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

  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        <Layout style={{ minHeight: '100vh' }}>
          <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography.Title level={4} style={{ margin: 0, color: 'inherit' }}>
              🔧 人话说八哥
            </Typography.Title>
            <Space>
              <Button icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>
                历史
              </Button>
              <ThemeToggle darkMode={darkMode} onToggle={toggleTheme} />
            </Space>
          </Header>
          <Content style={{ padding: 24 }}>
            <Home />
          </Content>
        </Layout>
        <HistoryDrawer open={historyOpen} onClose={() => setHistoryOpen(false)} />
      </AntdApp>
    </ConfigProvider>
  )
}
