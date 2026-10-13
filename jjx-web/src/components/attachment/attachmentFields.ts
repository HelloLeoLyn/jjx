/** 待绑定、引用和历史附件共用的列配置。 */
export const attachmentFields = {
  fileName: { key: 'fileName', label: '原文件名', minWidth: 180, render: 'preview' },
  displayName: { key: 'displayName', label: '展示文件名', minWidth: 180, render: 'displayName', editable: true },
  category: { key: 'category', label: '一级类型', width: 120, render: 'category' },
  documentType: { key: 'documentType', label: '二级类型', width: 180, render: 'typeSelect', editable: true },
  fileSize: { key: 'fileSize', label: '大小', width: 90, render: 'size' },
  createBy: { key: 'createBy', label: '上传人', width: 100, render: 'text' },
  createTime: { key: 'createTime', label: '上传时间', width: 140, render: 'time' },
} as const

export const referenceColumns = Object.values(attachmentFields)
