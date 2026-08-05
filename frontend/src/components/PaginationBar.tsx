import type { CSSProperties } from 'react'
import { Pagination } from 'antd'

interface PaginationBarProps {
  total: number
  page: number
  pageSize: number
  onChange: (page: number) => void
  style?: CSSProperties
}

/** 统一分页条：始终显示「共 N 条 · 第 x/y 页 · 上一页/下一页/跳页」，AntD5 默认尺寸保证视觉清晰 */
export default function PaginationBar({ total, page, pageSize, onChange, style }: PaginationBarProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', ...style }}>
      <Pagination
        current={page}
        pageSize={pageSize}
        total={total}
        onChange={onChange}
        showSizeChanger={false}
        showQuickJumper
        showTotal={(t) => (
          <span style={{ marginRight: 8 }}>
            共 {t} 条 · 第 {page}/{totalPages} 页
          </span>
        )}
      />
    </div>
  )
}
