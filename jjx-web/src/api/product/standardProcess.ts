import request from '@/utils/request'
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
   * GET /product/standard-processes/page
   */
  pageQuery(params: StandardProcessQueryParams) {
    return request.get<R<PageResult<StandardProcessItem>>>('/product/standard-processes/page', {
      params,
    })
  },

  /**
   * 获取标准工序详情
   * GET /product/standard-processes/{processId}
   */
  getById(processId: number) {
    return request.get<R<StandardProcessItem>>(`/product/standard-processes/${processId}`)
  },

  /**
   * 创建标准工序
   * POST /product/standard-processes
   */
  create(data: StandardProcessFormData) {
    return request.post<R<StandardProcessItem>>('/product/standard-processes', data)
  },

  /**
   * 更新标准工序
   * PUT /product/standard-processes/{processId}
   */
  update(processId: number, data: StandardProcessFormData) {
    return request.put<R<StandardProcessItem>>(`/product/standard-processes/${processId}`, data)
  },

  /**
   * 删除标准工序
   * DELETE /product/standard-processes/{processId}
   */
  remove(processId: number) {
    return request.delete(`/product/standard-processes/${processId}`)
  },

  /**
   * 启用工序
   * PUT /product/standard-processes/{processId}/enable
   */
  enable(processId: number) {
    return request.put(`/product/standard-processes/${processId}/enable`)
  },

  /**
   * 禁用工序
   * PUT /product/standard-processes/{processId}/disable
   */
  disable(processId: number) {
    return request.put(`/product/standard-processes/${processId}/disable`)
  },

  /**
   * 获取启用的工序列表
   * GET /product/standard-processes/enabled
   */
  getEnabledProcesses() {
    return request.get<R<StandardProcessItem[]>>('/product/standard-processes/enabled')
  },

  /**
   * 根据工序类型获取工序列表
   * GET /product/standard-processes/type/{processType}
   */
  getByProcessType(processType: string) {
    return request.get<R<StandardProcessItem[]>>(`/product/standard-processes/type/${processType}`)
  },

  /**
   * 根据工序类别获取工序列表
   * GET /product/standard-processes/category/{processCategory}
   */
  getByProcessCategory(processCategory: string) {
    return request.get<R<StandardProcessItem[]>>(
      `/product/standard-processes/category/${processCategory}`
    )
  },

  /**
   * 生成下一个工序编码
   * GET /product/standard-processes/generate-code
   */
  generateNextProcessCode(processType: string, processCategory: string) {
    return request.get<R<string>>('/product/standard-processes/generate-code', {
      params: { processType, processCategory },
    })
  },
}
