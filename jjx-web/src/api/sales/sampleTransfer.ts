import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// ==================== 类型定义 ====================

/** 打样工序项（预览返回） */
export interface SampleProcessItem {
  processId: number
  processName: string
  processOrder: number | null
  processCategory: string | null
  processNote: string | null
  durationMinutes: number | null
  matchedStdProcessId: number | null
  matchedStdProcessName: string | null
  matched: boolean
}

/** 打样物料项（预览返回，materials JSON 展开） */
export interface SampleMaterialItem {
  rowKey: string
  sourceProcessId: number
  sourceProcessName: string
  name: string
  spec: string | null
  qty: number | null
  unit: string | null
  materialId: number | null
  materialCode: string | null
  matchedMaterialId: number | null
  matchedMaterialCode: string | null
  matchedMaterialName: string | null
  matched: boolean
}

/** 标准工序库选项 */
export interface StandardProcessOption {
  processId: number
  processCode: string
  processName: string
  processType: string | null
  processCategory: string | null
}

/** 标准物料库选项 */
export interface StandardMaterialOption {
  materialId: number
  materialCode: string
  materialName: string
  specification: string | null
  unit: string | null
}

/** 预览返回结构 */
export interface SampleTransferPreview {
  orderId: number
  orderNo: string
  sampleProcesses: SampleProcessItem[]
  sampleMaterials: SampleMaterialItem[]
  standardProcesses: StandardProcessOption[]
  standardMaterials: StandardMaterialOption[]
}

/** 工序映射项（确认入参） */
export interface ProcessMapping {
  sampleProcessId: number | null
  stdProcessId: number | null // null=未匹配（前端标红）
  processName: string
  processOrder: number
  groupId: number | null
  groupOrder: number | null
  groupName: string | null
  processCategory: string | null
  processNote: string | null
  durationMinutes: number | null
}

/** 物料映射项（确认入参） */
export interface MaterialMapping {
  rowKey: string
  sourceProcessId: number
  sourceProcessName: string
  materialId: number | null // null=未匹配（前端标红）
  materialName: string
  spec: string | null
  qty: number
  unit: string | null
}

/** 确认转移入参 */
export interface SampleTransferConfirmDTO {
  orderId: number
  processMappings: ProcessMapping[]
  materialMappings: MaterialMapping[]
}

/** 确认转移返回 */
export interface SampleTransferConfirmResult {
  transferNo: string
  transferId: number
  productAction: string
  bomAction: string
  routingAction: string
  productId: number | null
  bomId: number | null
  routingId: number | null
  version: string | null
  detail: string[]
}

// ==================== API ====================

/** 打样转标准接口 */
export const sampleTransferApi = {
  /** 预览：读取打样数据+自动匹配推荐 */
  preview(orderId: number): AxiosPromise<SampleTransferPreview> {
    return request({
      url: `/sample/transfer/preview/${orderId}`,
      method: 'get',
    })
  },

  /** 确认转移：接收前端编辑后的标准数据落库 */
  confirm(data: SampleTransferConfirmDTO): AxiosPromise<SampleTransferConfirmResult> {
    return request({
      url: '/sample/transfer/confirm',
      method: 'post',
      data,
    })
  },
}
