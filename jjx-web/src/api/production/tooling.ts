import request from '@/utils/request'

export interface ToolingQuery {
  pageNum?: number
  pageSize?: number
  type?: string
  keyword?: string
  status?: number
}

export interface ToolingVO {
  toolingId: number
  toolingNo: string
  toolingName: string
  toolingType: string
  typeLabel?: string
  spec?: string
  // 刀模
  lifeLimit?: number
  currentCount?: number
  // 公共
  status: number
  statusLabel?: string
  location?: string
  department?: string
  responsible?: string
  customer?: string
  enableDate?: string
  lastUseTime?: string
  useCount?: number
  remark?: string
  createTime?: string
  photoId?: number
}

export interface ToolingForm {
  toolingId?: number
  toolingNo: string
  toolingName: string
  toolingType: string
  spec?: string
  lifeLimit?: number
  currentCount?: number
  status: number
  location?: string
  department?: string
  responsible?: string
  customer?: string
  enableDate?: string
  remark?: string
}

// 分页查询
export function getToolingPage(params: ToolingQuery) {
  return request({
    url: '/production/tooling/page',
    method: 'get',
    params,
  })
}

// 详情
export function getToolingById(id: number) {
  return request({
    url: `/production/tooling/${id}`,
    method: 'get',
  })
}

// 下拉选项（未报废）
export function getToolingOptions(type?: string) {
  return request({
    url: '/production/tooling/options',
    method: 'get',
    params: type ? { type } : {},
  })
}

// 按规则生成编号
export function genToolingNo(type: string) {
  return request({
    url: '/production/tooling/gen-no',
    method: 'get',
    params: { type },
  })
}

// 新增
export function createTooling(data: ToolingForm) {
  return request({
    url: '/production/tooling',
    method: 'post',
    data,
  })
}

// 修改
export function updateTooling(data: ToolingForm) {
  return request({
    url: '/production/tooling',
    method: 'put',
    data,
  })
}

// 状态变更
export function changeToolingStatus(id: number, status: number) {
  return request({
    url: `/production/tooling/${id}/status`,
    method: 'put',
    data: { status },
  })
}

// 删除
export function deleteTooling(id: number) {
  return request({
    url: `/production/tooling/${id}`,
    method: 'delete',
  })
}

// 导入
export function importTooling(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/production/tooling/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 下载导入模板
export function downloadToolingTemplate() {
  return request({
    url: '/production/tooling/importTemplate',
    method: 'get',
    responseType: 'blob',
  })
}

// 导出
export function exportTooling(params: ToolingQuery) {
  return request({
    url: '/production/tooling/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}
