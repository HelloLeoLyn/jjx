// 通用类型定义
export interface PageQuery {
  pageNum: number
  pageSize: number
  sortBy?: string // 排序字段
  order?: 'asc' | 'desc' // 排序方向
}

export interface PageResult<T> {
  total: number
  records: T[]
  pageNum: number
  pageSize: number
  totalPages: number
}

export interface R<T = any> {
  code: number
  msg: string
  data: T | null
}
