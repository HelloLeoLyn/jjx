import { formatSize, formatTime } from './attachmentFormat'

/** 唯一字段展示配置；场景仅挑选 key，不重复声明列名、宽度或渲染类型。 */
export const attachmentFields = {
  fileName: { key: 'fileName', label: '原文件名', minWidth: 180, render: 'preview' },
  displayName: { key: 'displayName', label: '展示文件名', minWidth: 180, render: 'displayName', editable: true },
  category: { key: 'category', label: '一级类型', width: 120, render: 'category' },
  documentType: { key: 'documentType', label: '二级类型', width: 180, render: 'typeSelect', editable: true },
  fileSize: { key: 'fileSize', label: '大小', width: 90, render: 'size' },
  createBy: { key: 'createBy', label: '上传人', width: 100, render: 'text' },
  createTime: { key: 'createTime', label: '上传时间', width: 140, render: 'time' },
} as const

export type AttachmentFieldKey = keyof typeof attachmentFields
export const attachmentFieldSets = {
  reference: ['fileName', 'displayName', 'category', 'documentType', 'fileSize', 'createBy', 'createTime'],
  pending: ['fileName', 'fileSize', 'category', 'documentType'],
  legacy: ['fileName', 'fileSize', 'createTime'],
  trace: ['fileName', 'fileSize', 'createTime'],
} as const satisfies Record<string, readonly AttachmentFieldKey[]>
export const referenceColumns = attachmentFieldSets.reference.map(key => attachmentFields[key])

/**
 * 字段 → 展示文本（唯一实现，只读列表/待绑定段共用）。
 * 未知 key 明确回退原始值并告警，禁止静默显示空（016 反例）。
 */
export function renderAttachmentField(key: AttachmentFieldKey, row: Record<string, any>): string {
  switch (key) {
    case 'fileName':
      return row.fileName || (row.id != null ? `附件 #${row.id}` : '')
    case 'displayName':
      return row.displayName || row.fileName || ''
    case 'category':
      return row.categoryName || row.category || ''
    case 'documentType':
      return row.documentTypeName || row.documentType || ''
    case 'fileSize':
      return formatSize(row.fileSize)
    case 'createBy':
      return row.createBy || ''
    case 'createTime':
      return formatTime(row.createTime)
    default:
      console.warn(`[attachment] 未知字段 key：${String(key)}（无渲染分支），已回退原始值`)
      return String((row as Record<string, any>)?.[key as string] ?? '')
  }
}
