import request from '@/utils/request'
import type { AxiosPromise } from 'axios'
import type {
  MaterialInquiryVO,
  MaterialInquiryDTO,
  MaterialInquiryQueryDTO,
} from '@/types/purchase'

/**
 * 查询材料询价列表
 * @param query 查询参数
 * @returns 询价列表
 */
export function listMaterialInquiry(
  query?: MaterialInquiryQueryDTO,
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/list',
    method: 'get',
    params: query,
  })
}

/**
 * 查询材料询价详情
 * @param inquiryId 询价ID
 * @returns 询价详情
 */
export function getMaterialInquiry(inquiryId: number): AxiosPromise {
  return request({
    url: `/purchase/inquiry/${inquiryId}`,
    method: 'get',
  })
}

/**
 * 新增材料询价
 * @param data 询价数据
 * @returns 结果
 */
export function addMaterialInquiry(data: MaterialInquiryDTO): AxiosPromise {
  return request({
    url: '/purchase/inquiry',
    method: 'post',
    data: data,
  })
}

/**
 * 修改材料询价
 * @param data 询价数据
 * @returns 结果
 */
export function updateMaterialInquiry(data: MaterialInquiryDTO): AxiosPromise {
  return request({
    url: '/purchase/inquiry',
    method: 'put',
    data: data,
  })
}

/**
 * 删除材料询价
 * @param inquiryIds 询价ID数组
 * @returns 结果
 */
export function delMaterialInquiry(inquiryIds: number[]): AxiosPromise {
  return request({
    url: `/purchase/inquiry/${inquiryIds}`,
    method: 'delete',
  })
}

/**
 * 根据物料编码查询询价历史
 * @param materialCode 物料编码
 * @param limit 限制条数
 * @returns 询价历史列表
 */
export function getInquiryByMaterial(
  materialCode: string,
  limit?: number,
): AxiosPromise {
  return request({
    url: `/purchase/inquiry/material/${materialCode}`,
    method: 'get',
    params: { limit },
  })
}

/**
 * 获取物料最新询价
 * @param materialCode 物料编码
 * @returns 最新询价
 */
export function getLatestInquiry(materialCode: string): AxiosPromise {
  return request({
    url: `/purchase/inquiry/latest/${materialCode}`,
    method: 'get',
  })
}

/**
 * 获取物料询价统计
 * @param materialCode 物料编码
 * @returns 统计信息
 */
export function getMaterialInquiryStats(materialCode: string): AxiosPromise {
  return request({
    url: `/purchase/inquiry/stats/material/${materialCode}`,
    method: 'get',
  })
}

/**
 * 获取供应商询价统计
 * @param supplierId 供应商ID
 * @returns 统计信息
 */
export function getSupplierInquiryStats(supplierId: number): AxiosPromise {
  return request({
    url: `/purchase/inquiry/stats/supplier/${supplierId}`,
    method: 'get',
  })
}

/**
 * 获取价格趋势数据
 * @param materialCode 物料编码
 * @param days 天数
 * @returns 价格趋势列表
 */
export function getPriceTrend(
  materialCode: string,
  days?: number,
): AxiosPromise {
  return request({
    url: `/purchase/inquiry/trend/${materialCode}`,
    method: 'get',
    params: { days },
  })
}

/**
 * 批量更新询价状态
 * @param inquiryIds 询价ID列表
 * @param status 状态
 * @returns 结果
 */
export function updateInquiryStatus(
  inquiryIds: number[],
  status: string,
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/status',
    method: 'put',
    params: { inquiryIds, status },
  })
}

/**
 * 更新过期询价状态
 * @returns 结果
 */
export function updateExpiredInquiryStatus(): AxiosPromise {
  return request({
    url: '/purchase/inquiry/expired/update',
    method: 'post',
  })
}

/**
 * 检查询价是否存在
 * @param materialCode 物料编码
 * @param supplierId 供应商ID
 * @param inquiryDate 询价日期
 * @returns 是否存在
 */
export function checkInquiryExists(
  materialCode: string,
  supplierId: number,
  inquiryDate: string,
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/exists',
    method: 'get',
    params: { materialCode, supplierId, inquiryDate },
  })
}

/**
 * 复制材料询价
 * @param inquiryId 源询价ID
 * @returns 新询价ID
 */
export function copyMaterialInquiry(inquiryId: number): AxiosPromise {
  return request({
    url: `/purchase/inquiry/copy/${inquiryId}`,
    method: 'post',
  })
}

/**
 * 导出材料询价数据
 * @param query 查询参数
 * @returns 导出结果
 */
export function exportMaterialInquiry(
  query?: MaterialInquiryQueryDTO,
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/export',
    method: 'post',
    params: query,
    responseType: 'blob',
  })
}

/**
 * 导入材料询价数据
 * @param file 文件
 * @param updateSupport 是否更新支持
 * @returns 导入结果
 */
export function importMaterialInquiry(
  file: File,
  updateSupport?: boolean,
): AxiosPromise {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('updateSupport', updateSupport ? 'true' : 'false')

  return request({
    url: '/purchase/inquiry/importData',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

/**
 * 下载导入模板
 * @returns 模板文件
 */
export function importTemplate(): AxiosPromise {
  return request({
    url: '/purchase/inquiry/importTemplate',
    method: 'post',
    responseType: 'blob',
  })
}

/**
 * 逻辑删除询价记录
 * @param inquiryId 询价ID
 * @returns 结果
 */
export function logicDeleteMaterialInquiry(inquiryId: number): AxiosPromise {
  return request({
    url: `/purchase/inquiry/logicDelete/${inquiryId}`,
    method: 'put',
  })
}

/**
 * 恢复逻辑删除的询价记录
 * @param inquiryId 询价ID
 * @returns 结果
 */
export function recoverMaterialInquiry(inquiryId: number): AxiosPromise {
  return request({
    url: `/purchase/inquiry/recover/${inquiryId}`,
    method: 'put',
  })
}

/**
 * 验证询价数据
 * @param data 询价数据
 * @returns 验证结果
 */
export function validateMaterialInquiry(
  data: MaterialInquiryDTO,
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/validate',
    method: 'post',
    data: data,
  })
}

/**
 * 获取可用的询价状态列表
 * @returns 状态列表
 */
export function getInquiryStatusList(): AxiosPromise {
  return request({
    url: '/purchase/inquiry/status/list',
    method: 'get',
  })
}

/**
 * 获取币种列表
 * @returns 币种列表
 */
export function getCurrencyList(): AxiosPromise {
  return request({
    url: '/purchase/inquiry/currency/list',
    method: 'get',
  })
}

/**
 * 获取询价人列表
 * @returns 询价人列表
 */
export function getInquiryPersonList(): AxiosPromise {
  return request({
    url: '/purchase/inquiry/person/list',
    method: 'get',
  })
}

/**
 * 批量新增材料询价
 * @param dataList 询价数据列表
 * @returns 结果
 */
export function batchAddMaterialInquiry(
  dataList: MaterialInquiryDTO[],
): AxiosPromise {
  return request({
    url: '/purchase/inquiry/batch',
    method: 'post',
    data: dataList,
  })
}

/**
 * 获取询价状态选项
 * @returns 状态选项
 */
export function getInquiryStatusOptions() {
  return [
    { value: 'active', label: '有效', tagType: 'success' },
    { value: 'inactive', label: '无效', tagType: 'info' },
    { value: 'expired', label: '已过期', tagType: 'danger' },
  ]
}

/**
 * 获取币种选项
 * @returns 币种选项
 */
export function getCurrencyOptions() {
  return [
    { value: 'CNY', label: '人民币' },
    { value: 'USD', label: '美元' },
    { value: 'EUR', label: '欧元' },
    { value: 'JPY', label: '日元' },
    { value: 'HKD', label: '港币' },
  ]
}

/**
 * 获取状态标签类型
 * @param status 状态
 * @param withinValidityPeriod 是否在有效期内
 * @returns 标签类型
 */
export function getStatusTagType(
  status: string,
  withinValidityPeriod?: boolean,
): string {
  if (status === 'active') {
    return withinValidityPeriod ? 'success' : 'warning'
  } else if (status === 'expired') {
    return 'danger'
  } else if (status === 'inactive') {
    return 'info'
  }
  return 'default'
}

/**
 * 获取状态标签文本
 * @param status 状态
 * @returns 标签文本
 */
export function getStatusLabel(status: string): string {
  const statusMap: Record<string, string> = {
    active: '有效',
    inactive: '无效',
    expired: '已过期',
  }
  return statusMap[status] || status
}

/**
 * 格式化询价价格
 * @param price 价格
 * @param currency 币种
 * @returns 格式化后的价格
 */
export function formatInquiryPrice(
  price: number,
  currency: string = 'CNY',
): string {
  if (!price) return '0.00'

  const formatter = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  const formattedPrice = formatter.format(price)
  return `${formattedPrice} ${currency}`
}

/**
 * 计算询价总金额
 * @param price 单价
 * @param quantity 数量
 * @returns 总金额
 */
export function calculateTotalAmount(price: number, quantity: number): number {
  if (!price || !quantity) return 0
  return price * quantity
}
