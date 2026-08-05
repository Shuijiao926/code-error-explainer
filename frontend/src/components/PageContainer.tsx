import type { CSSProperties, ReactNode } from 'react'

interface PageContainerProps {
  children: ReactNode
  /** 内容最大宽度；默认 960（日志表格用 1200） */
  maxWidth?: number
  style?: CSSProperties
}

/**
 * 页面统一布局容器：替代各页重复的 `maxWidth + margin: '0 auto'`。
 * 所有页面共用同一套水平宽度、垂直呼吸感与背景色，深浅主题一致。
 */
export default function PageContainer({ children, maxWidth = 960, style }: PageContainerProps) {
  return (
    <div className="cee-page-container" style={{ maxWidth, ...style }}>
      {children}
    </div>
  )
}
