import { quotationApi } from '@/api/sales/quotation'
import { sampleOrderApi } from '@/api/sales/sampleOrder'

/**
 * 操作预览器 - 操作注册表
 *
 * 全模块通用：新模块的操作只需在此加一条定义，组件零改动。
 * 每个操作描述：状态跳转 / 需要填写的内容 / 可挂证据 / 会触发的事件
 */

/** 表单字段类型 */
export type OperationFieldType = 'input' | 'textarea' | 'number'

export interface OperationField {
  key: string
  label: string
  type: OperationFieldType
  required?: boolean
  placeholder?: string
  defaultValue?: string | number
}

export interface OperationContext {
  bizId: number
  values: Record<string, any>
  attachmentIds: number[]
}

/** 操作成功后的结果展示配置（对接 OperationResultDialog） */
export interface OperationResultSpec {
  /** 结果卡操作名 */
  name: string
  /** 展示用旧状态文本 */
  from?: string
  /** 展示用新状态文本 */
  to?: string
  /** 单据类型：审核单/快递单/普通 */
  docType?: 'audit' | 'express' | 'normal'
  /** 下一步指引 */
  nextSteps?: string[]
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
  /** 成功后的结果展示配置（可选） */
  result?: OperationResultSpec
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
    fields: [{ key: 'remark', label: '审核意见', type: 'textarea', placeholder: '选填，审核意见/说明' }],
    evidence: true,
    events: ['quotation.reviewed'],
    api: ({ bizId, values, attachmentIds }) =>
      quotationApi.review(bizId, true, values.remark, attachmentIds.length ? attachmentIds.join(',') : undefined),
  },
  {
    key: 'quotation.reject',
    bizType: 'quotation',
    name: '审核驳回',
    fromStatus: [5],
    toStatus: 3,
    fields: [{ key: 'remark', label: '驳回原因', type: 'textarea', required: true, placeholder: '请填写驳回原因（必填）' }],
    evidence: true,
    events: ['quotation.reviewed'],
    api: ({ bizId, values, attachmentIds }) =>
      quotationApi.review(bizId, false, values.remark, attachmentIds.length ? attachmentIds.join(',') : undefined),
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
    fields: [{ key: 'remark', label: '拒绝原因', type: 'textarea', required: true, placeholder: '请填写拒绝原因（必填）' }],
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
    api: ({ bizId, attachmentIds }) =>
      quotationApi.convert(bizId).then((r) => r),
  },
  {
    key: 'quotation.toSample',
    bizType: 'quotation',
    name: '转为样品单',
    fromStatus: [0, 1, 2, 3, 4, 6],
    fields: [{ key: 'sampleQty', label: '打样数量', type: 'number', required: true, defaultValue: 10 }],
    api: ({ bizId, values }) => sampleOrderApi.createFromQuotation(bizId, { sampleQty: Number(values.sampleQty) }),
  },
]

/** ==================== 销售模块 · 样品单 ==================== */

export const sampleOperations: OperationDef[] = [
  {
    key: 'sample.submitReview',
    bizType: 'sample_order',
    name: '提交审核',
    fromStatus: [1],
    toStatus: 2,
    events: ['sample.submitted'],
    result: {
      name: '样品单提交审核',
      from: '样品需求已创建',
      to: '待审核',
      docType: 'audit',
      nextSteps: ['审核员审核样品单', '审核通过后进入工程打样'],
    },
    api: ({ bizId }) => sampleOrderApi.submitReview(bizId),
  },
  {
    key: 'sample.approve',
    bizType: 'sample_order',
    name: '审核通过',
    fromStatus: [2],
    toStatus: 3,
    fields: [{ key: 'remark', label: '审核备注', type: 'textarea', placeholder: '选填' }],
    evidence: true,
    events: ['sample.approved'],
    result: {
      name: '样品单审核通过',
      from: '待审核',
      to: '工程打样中',
      docType: 'audit',
      nextSteps: ['工程接单', '记录工序进度', '标记样品完成'],
    },
    api: ({ bizId, values }) => sampleOrderApi.approve(bizId, values.remark || ''),
  },
  {
    key: 'sample.rejectReview',
    bizType: 'sample_order',
    name: '审核驳回',
    fromStatus: [2],
    toStatus: 1,
    fields: [{ key: 'remark', label: '驳回原因', type: 'textarea', required: true, placeholder: '请填写驳回原因（必填）' }],
    evidence: true,
    events: ['sample.rejected'],
    result: {
      name: '样品单审核驳回',
      from: '待审核',
      to: '创建(可改重提)',
      docType: 'audit',
      nextSteps: ['销售修改样品单', '重新提交审核'],
    },
    api: ({ bizId, values }) => sampleOrderApi.rejectReview(bizId, values.remark),
  },
  {
    key: 'sample.markReady',
    bizType: 'sample_order',
    name: '样品完成',
    fromStatus: [3],
    toStatus: 4,
    fields: [{ key: 'sampleQty', label: '实际打样数量', type: 'number', required: true, defaultValue: 10 }],
    events: ['sample.ready'],
    result: {
      name: '样品制作完成',
      from: '工程打样中',
      to: '待送样',
      docType: 'audit',
      nextSteps: ['销售登记送样(快递单号)', '客户确认/退回'],
    },
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
    result: {
      name: '样品送样登记',
      from: '待送样',
      to: '已送样',
      docType: 'express',
      nextSteps: ['等待客户确认样品OK', '客户退回则重新打样'],
    },
    api: ({ bizId, values }) => sampleOrderApi.sendSample(bizId, values.trackingNo || ''),
  },
  {
    key: 'sample.confirm',
    bizType: 'sample_order',
    name: '客户确认OK',
    fromStatus: [5],
    toStatus: 6,
    fields: [{ key: 'clientName', label: '确认人姓名', type: 'input', placeholder: '客户方确认人姓名' }],
    evidence: true,
    events: ['sample.confirmed'],
    result: {
      name: '客户确认样品OK',
      from: '已送样',
      to: '已确认',
      docType: 'audit',
      nextSteps: ['转量产生成标准订单', '或继续多轮样品'],
    },
    api: ({ bizId, values }) => sampleOrderApi.confirm(bizId, values.clientName || '客户确认'),
  },
  {
    key: 'sample.rejectSample',
    bizType: 'sample_order',
    name: '退回修改',
    fromStatus: [5],
    toStatus: 9,
    fields: [{ key: 'reason', label: '退回原因', type: 'textarea', required: true, placeholder: '请填写退回原因/修改要求（必填）' }],
    evidence: true,
    events: ['sample.rejected_by_customer'],
    result: {
      name: '客户退回样品',
      from: '已送样',
      to: '客户退回(工程重打)',
      docType: 'audit',
      nextSteps: ['工程重新打样', '重新送样确认'],
    },
    api: ({ bizId, values }) => sampleOrderApi.rejectSample(bizId, values.reason),
  },
  {
    key: 'sample.convert',
    bizType: 'sample_order',
    name: '转量产',
    fromStatus: [6],
    toStatus: 7,
    events: ['sample.converted'],
    result: {
      name: '样品转量产',
      from: '已确认',
      to: '已转量产',
      docType: 'audit',
      nextSteps: ['标准订单提交审核', '订单确认后提交生产'],
    },
    api: ({ bizId }) => sampleOrderApi.convertToProduction(bizId),
  },
  {
    key: 'sample.restart',
    bizType: 'sample_order',
    name: '重新打样',
    fromStatus: [9],
    toStatus: 3,
    events: ['sample.restarted'],
    result: {
      name: '样品重新打样',
      from: '客户退回',
      to: '工程打样中',
      docType: 'audit',
      nextSteps: ['工程接单', '记录工序进度', '标记样品完成'],
    },
    api: ({ bizId }) => sampleOrderApi.restartEngineering(bizId),
  },
]

/** 全模块注册表汇总（后续模块在此追加） */
export const operationRegistry: Record<string, OperationDef> = {
  ...Object.fromEntries(quotationOperations.map((op) => [op.key, op])),
  ...Object.fromEntries(sampleOperations.map((op) => [op.key, op])),
}

export function getOperation(key: string): OperationDef | undefined {
  return operationRegistry[key]
}
