/** 待绑定、引用和历史附件共用的列配置。 */
export const attachmentFields = {
  fileName: { key: 'fileName', label: '原文件名', minWidth: 180, render: 'preview' },
  displayName: {
    key: 'displayName',
    label: '展示文件名',
    minWidth: 180,
    render: 'displayName',
    editable: true,
  },
  category: { key: 'category', label: '一级类型', width: 120, render: 'category' },
  documentType: {
    key: 'documentType',
    label: '二级类型',
    width: 180,
    render: 'typeSelect',
    editable: true,
  },
  fileSize: { key: 'fileSize', label: '大小', width: 90, render: 'size' },
  createBy: { key: 'createBy', label: '上传人', width: 100, render: 'text' },
  createTime: { key: 'createTime', label: '上传时间', width: 140, render: 'time' },
  sourceType: { key: 'sourceType', label: '来源类型', width: 110, render: 'sourceType' },
  sourceNo: { key: 'sourceNo', label: '来源单号', minWidth: 150, render: 'sourceNo' },
  productCode: { key: 'productCode', label: '产品编码', width: 120, render: 'productCode' },
  status: { key: 'status', label: '状态', width: 90, render: 'status' },
  actions: { key: 'actions', label: '操作', width: 80, render: 'download' },
} as const

export type AttachmentFieldKey = keyof typeof attachmentFields

// 列集只有两份（2026-10-15 收口，旧的 referenceColumns 已删除）：
//  1) editableColumns  —— 可写表（本单引用 / 待绑定）默认列
//  2) readonlyDocColumns —— 只读表（详情 / 同链路 / 历史）默认列

/** 只读表默认列（2026-10-15 用户要求：与可写表默认字段一致 + 状态/操作） */
export const readonlyDocColumns: readonly AttachmentFieldKey[] = [
  'fileName',
  'displayName',
  'sourceType',
  'sourceNo',
  'productCode',
  'category',
  'documentType',
  'status',
  'actions',
]

/** 可写表默认列（2026-10-15 用户指定）：原文件名 / 展示文件名 / 来源类型 / 来源单号 / 产品编码 / 一级+二级类型；大小·上传人·时间在「详情」里看 */
export const editableColumns = [
  attachmentFields.fileName,
  attachmentFields.displayName,
  attachmentFields.sourceType,
  attachmentFields.sourceNo,
  attachmentFields.productCode,
  attachmentFields.category,
  attachmentFields.documentType,
]
