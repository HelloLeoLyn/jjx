import { customerApi } from '@/api/sales/customer'
import { quotationApi } from '@/api/sales/quotation'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { inboundApi } from '@/api/inventory/inbound'
import { outboundApi } from '@/api/inventory/outbound'
import * as purchaseOrderApi from '@/api/purchase/order'
import * as productionOrderApi from '@/api/production/order'
import { useUserStore } from '@/store/modules/user'

// 当前用户（入库/出库审批人等字段用）
function currentUser() {
  const store = useUserStore()
  return { id: String(store.userId || 1), name: store.nickName || '当前用户' }
}

/**
 * 操作预览器 - 操作注册表
 *
 * 全模块通用：新模块的操作只需在此加一条定义，组件零改动。
 * 每个操作描述：状态跳转 / 需要填写的内容 / 可挂证据 / 会触发的事件
 */

/** 表单字段类型 */
export type OperationFieldType = 'input' | 'textarea' | 'number' | 'select'

export interface OperationFieldOption {
  label: string
  value: string | number
}

export interface OperationField {
  key: string
  label: string
  type: OperationFieldType
  required?: boolean
  placeholder?: string
  defaultValue?: string | number
  /** select 类型选项 */
  options?: OperationFieldOption[]
}

export interface OperationContext {
  bizId: number
  values: Record<string, any>
  attachmentIds: number[]
}

export interface OperationDef {
  /** 操作唯一标识，如 quotation.approve */
  key: string
  /** 业务类型（附件上传 bizType） */
  bizType: string
  /** 操作名，如 审核通过 */
  name: string
  /** 确认按钮文案，默认 `确认${name}` */
  confirmText?: string
  /** 允许操作的来源状态列表 */
  fromStatus: number[]
  /** 目标状态（用于状态跳转展示；undefined 表示不改变状态，如转样品单） */
  toStatus?: number
  /** 动态表单字段 */
  fields?: OperationField[]
  /** 是否允许挂证据（截图/文件/文档） */
  evidence?: boolean
  /** 触发的系统事件 code（对应 sys_event_config.event_code），用于事件预告 */
  events?: string[]
  /** 自然语言业务影响说明（纯文案，展示时不带任何技术信息；配置后替代技术事件预告区） */
  impact?: string
  /** 成功提示文案，默认 `${name}成功` */
  successText?: string
  /** 确认后执行的真实接口调用 */
  api: (ctx: OperationContext) => Promise<any>
}

/** ==================== 销售模块 · 报价单 ==================== */

export const quotationOperations: OperationDef[] = [
  {
    key: 'quotation.submitReview',
    bizType: 'quotation',
    name: '提交审核',
    fromStatus: [0],
    toStatus: 5,
    evidence: true,
    events: ['quotation.submitted'],
    api: ({ bizId, attachmentIds }) =>
      quotationApi.submitReview(bizId, attachmentIds.length ? attachmentIds.join(',') : undefined),
  },
  {
    key: 'quotation.approve',
    bizType: 'quotation',
    name: '审核通过',
    fromStatus: [5],
    toStatus: 6,
    fields: [
      { key: 'remark', label: '审核意见', type: 'textarea', placeholder: '选填，审核意见/说明' },
    ],
    evidence: true,
    events: ['quotation.reviewed'],
    api: ({ bizId, values, attachmentIds }) =>
      quotationApi.review(
        bizId,
        true,
        values.remark,
        attachmentIds.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'quotation.reject',
    bizType: 'quotation',
    name: '审核驳回',
    fromStatus: [5],
    toStatus: 3,
    fields: [
      {
        key: 'remark',
        label: '驳回原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写驳回原因（必填）',
      },
    ],
    evidence: true,
    events: ['quotation.reviewed'],
    api: ({ bizId, values, attachmentIds }) =>
      quotationApi.review(
        bizId,
        false,
        values.remark,
        attachmentIds.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'quotation.send',
    bizType: 'quotation',
    name: '发送报价',
    fromStatus: [0, 6],
    toStatus: 1,
    evidence: true,
    events: ['quotation.sent'],
    api: ({ bizId, attachmentIds }) =>
      quotationApi.send(bizId, attachmentIds.length ? attachmentIds.join(',') : undefined),
  },
  {
    key: 'quotation.customerConfirm',
    bizType: 'quotation',
    name: '客户确认',
    fromStatus: [1],
    toStatus: 2,
    evidence: true,
    events: ['quotation.confirmed'],
    api: ({ bizId, attachmentIds }) =>
      quotationApi.confirm(bizId, attachmentIds.length ? attachmentIds.join(',') : undefined),
  },
  {
    key: 'quotation.customerReject',
    bizType: 'quotation',
    name: '客户拒绝',
    fromStatus: [1],
    toStatus: 3,
    fields: [
      {
        key: 'remark',
        label: '拒绝原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写拒绝原因（必填）',
      },
    ],
    evidence: true,
    events: ['quotation.rejected'],
    api: ({ bizId, values, attachmentIds }) =>
      quotationApi.reject(bizId, attachmentIds.length ? attachmentIds.join(',') : undefined),
  },
  {
    key: 'quotation.convert',
    bizType: 'quotation',
    name: '转为订单',
    fromStatus: [2],
    toStatus: 9,
    evidence: true,
    events: ['quotation.converted'],
    api: ({ bizId, attachmentIds }) => quotationApi.convert(bizId).then((r) => r),
  },
  {
    key: 'quotation.toSample',
    bizType: 'quotation',
    name: '转为样品单',
    fromStatus: [0, 1, 2, 3, 4, 6],
    // DEV-1111：默认值由报价单页动态注入（按报价单明细数量求和），此处不再写死
    fields: [{ key: 'sampleQty', label: '打样数量', type: 'number', required: true }],
    api: ({ bizId, values }) =>
      sampleOrderApi.createFromQuotation(bizId, { sampleQty: Number(values.sampleQty) }),
  },
]

/** ==================== 销售模块 · 样品单 ==================== */

export const sampleOperations: OperationDef[] = [
  {
    key: 'sample.submitRequest',
    bizType: 'sample_order',
    name: '申请打样',
    fromStatus: [1],
    toStatus: 2,
    events: ['sample.submitted'],
    impact: '提交后进入工程打样。',
    successText: '已提交打样申请',
    api: ({ bizId, attachmentIds }) =>
      sampleOrderApi.submitRequest(
        bizId,
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.approve',
    bizType: 'sample_order',
    name: '审核通过',
    fromStatus: [2],
    toStatus: 3,
    impact: '通过后进入工程打样，由工程人员接单处理。',
    fields: [
      { key: 'remark', label: '审核意见', type: 'textarea', placeholder: '选填，审核意见/说明' },
    ],
    evidence: true,
    events: ['sample.approved'],
    api: ({ bizId, values, attachmentIds }) =>
      sampleOrderApi.approve(
        bizId,
        values.remark || '',
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.rejectReview',
    bizType: 'sample_order',
    name: '审核驳回',
    fromStatus: [2],
    toStatus: 1,
    impact: '驳回后退回销售处理，修改后可重新提交审核。',
    fields: [
      {
        key: 'remark',
        label: '驳回原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写驳回原因（必填）',
      },
    ],
    evidence: true,
    events: ['sample.rejected'],
    api: ({ bizId, values, attachmentIds }) =>
      sampleOrderApi.rejectReview(
        bizId,
        values.remark,
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.markReady',
    bizType: 'sample_order',
    name: '样品完成',
    fromStatus: [3],
    toStatus: 4,
    fields: [
      { key: 'sampleQty', label: '实际打样数量', type: 'number', required: true, defaultValue: 10 },
    ],
    events: ['sample.ready'],
    api: ({ bizId, values }) => sampleOrderApi.markReady(bizId, Number(values.sampleQty)),
  },
  {
    key: 'sample.sendSample',
    bizType: 'sample_order',
    name: '送样登记',
    fromStatus: [4],
    toStatus: 5,
    fields: [{ key: 'trackingNo', label: '快递单号', type: 'input', placeholder: '选填' }],
    evidence: true,
    events: ['sample.sent'],
    api: ({ bizId, values, attachmentIds }) =>
      sampleOrderApi.sendSample(
        bizId,
        values.trackingNo || '',
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.confirm',
    bizType: 'sample_order',
    name: '客户确认OK',
    fromStatus: [5],
    toStatus: 6,
    fields: [
      { key: 'clientName', label: '确认人姓名', type: 'input', placeholder: '客户方确认人姓名' },
    ],
    evidence: true,
    events: ['sample.confirmed'],
    api: ({ bizId, values, attachmentIds }) =>
      sampleOrderApi.confirm(
        bizId,
        values.clientName || '客户确认',
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.rejectSample',
    bizType: 'sample_order',
    name: '退回修改',
    fromStatus: [5],
    toStatus: 9,
    fields: [
      {
        key: 'reason',
        label: '退回原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写退回原因/修改要求（必填）',
      },
    ],
    evidence: true,
    events: ['sample.rejected_by_customer'],
    api: ({ bizId, values, attachmentIds }) =>
      sampleOrderApi.rejectSample(
        bizId,
        values.reason,
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'sample.convert',
    bizType: 'sample_order',
    name: '转量产',
    fromStatus: [6],
    toStatus: 7,
    events: ['sample.converted'],
    api: ({ bizId }) => sampleOrderApi.convertToProduction(bizId),
  },
  {
    key: 'sample.restart',
    bizType: 'sample_order',
    name: '重新打样',
    fromStatus: [9],
    toStatus: 3,
    events: ['sample.restarted'],
    api: ({ bizId }) => sampleOrderApi.restartEngineering(bizId),
  },
  {
    key: 'sample.accept',
    bizType: 'sample_order',
    name: '工程接单',
    // 接单在工程打样中(3)内进行，状态不变（记录接单人）
    fromStatus: [3],
    events: [],
    api: ({ bizId }) => sampleOrderApi.acceptEngineering(bizId),
  },
  {
    key: 'sample.transfer',
    bizType: 'sample_order',
    name: '资料转移',
    // 已确认(6)/已转量产(7)可转移；不改变样品单状态（建档前置动作）
    fromStatus: [6, 7],
    events: ['sample.transferred'],
    api: ({ bizId }) => sampleOrderApi.transfer(bizId),
  },
]

/** ==================== 销售模块 · 客户状态 ==================== */

export const customerOperations: OperationDef[] = [
  {
    key: 'customer.changeStatus',
    bizType: 'customer',
    name: '状态变更',
    // 目标状态由选择器决定，不做固定跳转展示
    fromStatus: [1, 2, 3, 4],
    fields: [
      {
        key: 'status',
        label: '目标状态',
        type: 'select',
        required: true,
        options: [
          { label: '潜在客户', value: 1 },
          { label: '正式客户', value: 2 },
          { label: '暂停合作', value: 3 },
          { label: '终止合作', value: 4 },
        ],
      },
      { key: 'remark', label: '变更说明', type: 'textarea', placeholder: '选填' },
    ],
    events: ['sales.customer.status_updated'],
    api: ({ bizId, values }) =>
      customerApi.changeCustomerStatus(Number(bizId), Number(values.status)),
  },
]

/** ==================== 库存模块 · 入库单 ==================== */

export const inboundOperations: OperationDef[] = [
  {
    key: 'inbound.submit',
    bizType: 'inbound',
    name: '提交审核',
    fromStatus: [0],
    toStatus: 1,
    events: ['inventory.inbound.submitted'],
    api: ({ bizId }) => inboundApi.submitApprove(String(bizId)),
  },
  {
    key: 'inbound.approve',
    bizType: 'inbound',
    name: '审批通过',
    fromStatus: [1],
    toStatus: 2,
    fields: [{ key: 'remark', label: '审批备注', type: 'textarea', placeholder: '选填' }],
    events: ['inventory.inbound.approved'],
    api: ({ bizId, values }) => {
      const u = currentUser()
      return inboundApi.approve({
        inboundId: String(bizId),
        approverId: u.id,
        approverName: u.name,
        remark: values.remark || '',
      })
    },
  },
  {
    key: 'inbound.confirm',
    bizType: 'inbound',
    name: '确认入库',
    fromStatus: [1, 2],
    toStatus: 3,
    events: ['inventory.inbound.confirmed'],
    api: ({ bizId }) => {
      const u = currentUser()
      return inboundApi.confirm(String(bizId), u.id, u.name)
    },
  },
  {
    key: 'inbound.cancel',
    bizType: 'inbound',
    name: '取消入库单',
    fromStatus: [0, 1],
    fields: [
      {
        key: 'reason',
        label: '取消原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写取消原因（必填）',
      },
    ],
    events: ['inventory.inbound.cancelled'],
    api: ({ bizId, values }) => inboundApi.cancel(String(bizId), values.reason),
  },
]

/** ==================== 库存模块 · 出库单 ==================== */

export const outboundOperations: OperationDef[] = [
  {
    key: 'outbound.confirm',
    bizType: 'outbound',
    name: '确认出库',
    fromStatus: [1, 2],
    toStatus: 3,
    events: ['inventory.outbound.confirmed'],
    api: ({ bizId }) => {
      const u = currentUser()
      return outboundApi.confirm(String(bizId), u.id, u.name)
    },
  },
]

/** ==================== 采购模块 · 采购订单 ==================== */

export const purchaseOperations: OperationDef[] = [
  {
    key: 'purchase.submitReview',
    bizType: 'purchase_order',
    name: '提交审核',
    fromStatus: [1],
    toStatus: 3,
    events: ['purchase.submitted'],
    api: ({ bizId }) => purchaseOrderApi.submitOrder(Number(bizId)),
  },
  {
    key: 'purchase.approve',
    bizType: 'purchase_order',
    name: '审核通过',
    fromStatus: [3],
    toStatus: 4,
    fields: [
      {
        key: 'approvalComment',
        label: '审批意见',
        type: 'textarea',
        placeholder: '选填，审批意见/说明',
      },
    ],
    evidence: true,
    events: ['purchase.approved'],
    api: ({ bizId, values, attachmentIds }) => {
      const u = currentUser()
      return purchaseOrderApi.approveOrder(
        {
          orderId: Number(bizId),
          approverId: Number(u.id),
          approverName: u.name,
          approvalComment: values.approvalComment || '',
          approvalStatus: 3,
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      )
    },
  },
  {
    key: 'purchase.reject',
    bizType: 'purchase_order',
    name: '审核驳回',
    fromStatus: [3],
    toStatus: 5,
    fields: [
      {
        key: 'approvalComment',
        label: '驳回原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写驳回原因（必填）',
      },
    ],
    evidence: true,
    events: ['purchase.approved'],
    api: ({ bizId, values, attachmentIds }) => {
      const u = currentUser()
      return purchaseOrderApi.approveOrder(
        {
          orderId: Number(bizId),
          approverId: Number(u.id),
          approverName: u.name,
          approvalComment: values.approvalComment || '',
          approvalStatus: 4,
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      )
    },
  },
  {
    key: 'purchase.cancel',
    bizType: 'purchase_order',
    name: '取消订单',
    fromStatus: [1, 3, 5],
    toStatus: 2,
    fields: [
      {
        key: 'reason',
        label: '取消原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写取消原因（必填）',
      },
    ],
    evidence: true,
    events: [],
    api: ({ bizId, attachmentIds }) =>
      purchaseOrderApi.cancleOrder(
        Number(bizId),
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
]

/** ==================== 生产模块 · 生产工单 ==================== */

export const productionOperations: OperationDef[] = [
  {
    key: 'production.submitReview',
    bizType: 'production_order',
    name: '提交审核',
    fromStatus: [0],
    toStatus: 1,
    events: [],
    api: ({ bizId }) =>
      productionOrderApi.submitApproval(String(bizId), {
        approvalStatus: 1,
      }),
  },
  {
    key: 'production.approve',
    bizType: 'production_order',
    name: '审核通过',
    fromStatus: [1],
    toStatus: 2,
    fields: [
      {
        key: 'approvalRemark',
        label: '审批意见',
        type: 'textarea',
        placeholder: '选填，审批意见/说明',
      },
    ],
    evidence: true,
    events: [],
    api: ({ bizId, values, attachmentIds }) =>
      productionOrderApi.updateOrderStatus(
        {
          orderId: String(bizId),
          orderStatus: 2,
          approvalStatus: 2,
          approvalRemark: values.approvalRemark || '',
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'production.reject',
    bizType: 'production_order',
    name: '审核驳回',
    fromStatus: [1],
    toStatus: 3,
    fields: [
      {
        key: 'approvalRemark',
        label: '驳回原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写驳回原因（必填）',
      },
    ],
    evidence: true,
    events: [],
    api: ({ bizId, values, attachmentIds }) =>
      productionOrderApi.updateOrderStatus(
        {
          orderId: String(bizId),
          // 3=已驳回（WorkOrderEnum 状态，OrderStatus 类型缺该成员，后端合法）
          orderStatus: 3 as any,
          approvalStatus: 3,
          approvalRemark: values.approvalRemark || '',
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'production.start',
    bizType: 'production_order',
    name: '开始执行',
    fromStatus: [4],
    toStatus: 6,
    fields: [{ key: 'remark', label: '开始备注', type: 'textarea', placeholder: '选填，开工说明' }],
    evidence: true,
    events: ['product.instance.production_started'],
    api: ({ bizId, values, attachmentIds }) =>
      productionOrderApi.startExecution(
        String(bizId),
        { remark: values.remark || '' },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'production.complete',
    bizType: 'production_order',
    name: '完成工单',
    fromStatus: [6],
    toStatus: 8,
    fields: [
      {
        key: 'completedQuantity',
        label: '完成数量',
        type: 'number',
        required: true,
        defaultValue: 1,
      },
      {
        key: 'qualityResult',
        label: '质量结果',
        type: 'select',
        required: true,
        options: [
          { label: '合格', value: 'qualified' },
          { label: '不合格', value: 'unqualified' },
          { label: '待检', value: 'pending' },
        ],
      },
      { key: 'remark', label: '完成备注', type: 'textarea', placeholder: '选填' },
    ],
    evidence: true,
    events: ['production.completed'],
    api: ({ bizId, values, attachmentIds }) =>
      productionOrderApi.completeExecution(
        String(bizId),
        {
          completedQuantity: Number(values.completedQuantity),
          qualityResult: values.qualityResult,
          remark: values.remark || '',
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
  {
    key: 'production.cancel',
    bizType: 'production_order',
    name: '取消工单',
    fromStatus: [0, 1, 2, 3, 4, 6],
    toStatus: 9,
    fields: [
      {
        key: 'remark',
        label: '取消原因',
        type: 'textarea',
        required: true,
        placeholder: '请填写取消原因（必填）',
      },
    ],
    evidence: true,
    events: [],
    api: ({ bizId, values, attachmentIds }) =>
      productionOrderApi.updateOrderStatus(
        {
          orderId: String(bizId),
          orderStatus: 9,
          remark: values.remark || '',
        },
        attachmentIds?.length ? attachmentIds.join(',') : undefined
      ),
  },
]

/** 全模块注册表汇总（后续模块在此追加） */
export const operationRegistry: Record<string, OperationDef> = {
  ...Object.fromEntries(quotationOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(sampleOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(inboundOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(outboundOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(customerOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(purchaseOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(productionOperations.map((op) => [op.key, op])),
}

export function getOperation(key: string): OperationDef | undefined {
  return operationRegistry[key]
}
