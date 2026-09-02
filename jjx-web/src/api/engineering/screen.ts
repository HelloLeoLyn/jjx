import request from '@/utils/request'

/** 分页查询网版 */
export function pageScreen(params?: Record<string, unknown>) {
  return request({
    url: '/engineering/screen/page',
    method: 'get',
    params,
  })
}

/** 网版详情 */
export function getScreen(screenId: number) {
  return request({
    url: `/engineering/screen/${screenId}`,
    method: 'get',
  })
}

/** 新增网版 */
export function addScreen(data: Record<string, unknown>) {
  return request({
    url: '/engineering/screen',
    method: 'post',
    data,
  })
}

/** 编辑网版 */
export function updateScreen(data: Record<string, unknown>) {
  return request({
    url: '/engineering/screen',
    method: 'put',
    data,
  })
}

/** 生效/停用 */
export function changeScreenStatus(screenId: number, status: number) {
  return request({
    url: `/engineering/screen/${screenId}/status`,
    method: 'put',
    params: { status },
  })
}

/** 删除网版 */
export function delScreen(screenId: number) {
  return request({
    url: `/engineering/screen/${screenId}`,
    method: 'delete',
  })
}

/** 网版联想（印刷工序网框输入） */
export function suggestScreen(keyword?: string, limit?: number) {
  return request({
    url: '/engineering/screen/suggest',
    method: 'get',
    params: { keyword, limit },
  })
}
