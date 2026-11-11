import request from '@/utils/request'
import type { R } from '@/types'

/** 制造快照状态（后端 ManufacturingSnapshotVO.snapshotStatus，dev-20261111-009 G1） */
export type ManufacturingSnapshotStatus = 'COMPLETE' | 'SNAPSHOT_PARTIAL'

export const MANUFACTURING_SNAPSHOT_COMPLETE: ManufacturingSnapshotStatus = 'COMPLETE'
export const MANUFACTURING_SNAPSHOT_PARTIAL: ManufacturingSnapshotStatus = 'SNAPSHOT_PARTIAL'

/** BOM 层材料耗用节点（按工单持久化 bom_id 展开，供只读展示） */
export interface ManufacturingSnapshotNodeVO {
  level?: number | null
  itemOrder?: number | null
  parentMaterialId?: number | null
  materialId?: number | null
  materialCode?: string | null
  materialName?: string | null
  unit?: string | null
  quantity?: number | string | null
  moduleQty?: number | string | null
  baseQty?: number | string | null
  lossRate?: number | string | null
  subBomId?: number | null
  processId?: number | null
  processName?: string | null
  sourceType?: string | null
  /** BOM_ITEM / SUB_BOM_MISSING */
  snapshotSource?: string | null
  children?: ManufacturingSnapshotNodeVO[]
}

/** 路线工序行（历史路线依据） */
export interface ManufacturingSnapshotRoutingStep {
  processOrder?: number | null
  processName?: string | null
  processId?: number | null
  workflowSeq?: number | null
  workInstruction?: string | null
}

/** 工单实际工序快照（production_operation_execution） */
export interface ManufacturingSnapshotExecutionStep {
  executionId?: number | null
  processOrder?: number | null
  processName?: string | null
  executionStatus?: number | null
}

/** 工单制造历史快照（只读；全部按工单持久化 ID 组装，不用最新版本顶替） */
export interface ManufacturingSnapshotVO {
  orderId?: number
  orderNo?: string
  outputType?: string | null
  outputId?: number | null
  productCode?: string | null
  productName?: string | null
  plannedQuantity?: number | string | null
  bomId?: number | null
  bomCode?: string | null
  routingId?: number | null
  routingCode?: string | null
  routingVersion?: string | null
  workSpecVersionId?: number | null
  workSpecVersionNo?: string | null
  snapshotStatus?: ManufacturingSnapshotStatus | string | null
  depth?: number | null
  nodes?: ManufacturingSnapshotNodeVO[]
  routingSteps?: ManufacturingSnapshotRoutingStep[]
  executionSteps?: ManufacturingSnapshotExecutionStep[]
  warnings?: string[]
  blockers?: string[]
}

/** 工单制造历史快照（GET /production/order/{orderId}/manufacturing-snapshot，权限 production:order:view） */
export function getManufacturingSnapshot(orderId: number, depth = 32) {
  return request.get<R<ManufacturingSnapshotVO>>(
    `/production/order/${orderId}/manufacturing-snapshot`,
    { params: { depth } },
  )
}
