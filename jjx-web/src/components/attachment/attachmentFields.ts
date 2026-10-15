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

/** 两种模式使用同一默认列集；编辑能力由单元格插槽提供。 */
export const attachmentColumns: readonly AttachmentFieldKey[] = [
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

export const readonlyDocColumns = attachmentColumns
