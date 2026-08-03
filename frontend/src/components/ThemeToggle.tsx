import { Button } from 'antd'
import { MoonOutlined, SunOutlined } from '@ant-design/icons'

interface Props {
  darkMode: boolean
  onToggle: () => void
}

export default function ThemeToggle({ darkMode, onToggle }: Props) {
  return (
    <Button
      aria-label="切换深色模式"
      title={darkMode ? '切换到浅色模式' : '切换到深色模式'}
      icon={darkMode ? <SunOutlined /> : <MoonOutlined />}
      onClick={onToggle}
    />
  )
}
