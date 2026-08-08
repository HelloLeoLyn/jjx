import request from '@/utils/request'
import type { AxiosPromise } from 'axios'
import type {
  StandardProcessQueryParams,
  StandardProcessFormData,
  StandardProcessItem,
} from '@/types/product/standardProcess'
import type { PageResult, R } from '@/types'

/**
 * 标准工序 API
 */
export const standardProcessApi = {
  /**
   * 分页查询标准工序
   * GET /engineering/standard-processes/page
   */
  pageQuery(params: StandardProcessQueryParams) {
    return request.get<R<PageResult<StandardProcessItem>>>('/engineering/standard-processes/page', {
      params,
    })
  },

  /**
   * 获取标准工序详情
   * GET /engineering/standard-processes/{processId}
   */
  getById(processId: number) {
    return request.get<R<StandardProcessItem>>(`/engineering/standard-processes/${processId}`)
  },

  /**
   * 创建标准工序
   * POST /product/standard-processes
   */
  create(data: StandardProcessFormData) {
    return request.post<R<StandardProcessItem>>('/engineering/standard-processes', data)
  },

  /**
   * 更新标准工序
   * PUT /engineering/standard-processes/{processId}
   */
  update(processId: number, data: StandardProcessFormData) {
    return request.put<R<StandardProcessItem>>(`/engineering/standard-processes/${processId}`, data)
  },

  /**
   * 删除标准工序
   * DELETE /engineering/standard-processes/{processId}
   */
  remove(processId: number) {
    return request.delete(`/engineering/standard-processes/${processId}`)
  },

  /**
   * 启用工序
   * PUT /engineering/standard-processes/{processId}/enable
   */
  enable(processId: number) {
    return request.put(`/engineering/standard-processes/${processId}/enable`)
  },

  /**
   * 禁用工序
   * PUT /engineering/standard-processes/{processId}/disable
   */
  disable(processId: number) {
    return request.put(`/engineering/standard-processes/${processId}/disable`)
  },

  /**
   * 获取启用的工序列表
   * GET /engineering/standard-processes/enabled
   */
  getEnabledProcesses() {
    return request.get<R<StandardProcessItem[]>>('/engineering/standard-processes/enabled')
  },

  /**
   * 根据工序类型获取工序列表
   * GET /engineering/standard-processes/type/{processType}
   */
  getByProcessType(processType: string) {
    return request.get<R<StandardProcessItem[]>>(`/engineering/standard-processes/type/${processType}`)
  },

  /**
   * 根据工序类别获取工序列表
   * GET /engineering/standard-processes/category/{processCategory}
   */
  getByProcessCategory(processCategory: string) {
    return request.get<R<StandardProcessItem[]>>(
      `/engineering/standard-processes/category/${processCategory}`
    )
  },

  /**
   * 生成下一个工序编码
   * GET /engineering/standard-processes/generate-code
   */
  generateNextProcessCode(processType: string, processCategory: string) {
    return request.get<R<string>>('/engineering/standard-processes/generate-code', {
      params: { processType, processCategory },
    })
  },

  /**
   * 标准工序导入（2026-08-08）
   */
  importProcesses(file: File): AxiosPromise<any> {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/engineering/standard-processes/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /**
   * 下载标准工序导入模板
   */
  importTemplate() {
    return request.post('/engineering/standard-processes/importTemplate', null, {
      responseType: 'blob',
    })
  },
}
