// src/enums/system/FileEnum.ts
import { createEnum } from '../base'

/**
 * 文件类型枚举
 */
export const FileTypeEnum = createEnum({
  items: [
    { value: 'image', label: '图片', tagProps: { type: 'primary' } },
    { value: 'document', label: '文档', tagProps: { type: 'success' } },
    { value: 'spreadsheet', label: '表格', tagProps: { type: 'warning' } },
    { value: 'archive', label: '压缩包', tagProps: { type: 'info' } },
    { value: 'video', label: '视频', tagProps: { type: 'danger' } },
    { value: 'audio', label: '音频', tagProps: { type: 'info' } },
    { value: 'other', label: '其他', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 文件状态枚举
 */
export const FileStatusEnum = createEnum({
  items: [
    { value: 'normal', label: '正常', tagProps: { type: 'success' } },
    { value: 'deleted', label: '已删除', tagProps: { type: 'danger' } },
    { value: 'archived', label: '已归档', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 存储类型枚举
 */
export const StorageTypeEnum = createEnum({
  items: [
    { value: 'local', label: '本地存储', tagProps: { type: 'primary' } },
    { value: 'cloud', label: '云存储', tagProps: { type: 'success' } },
    { value: 'ftp', label: 'FTP存储', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 文件相关枚举统一导出
 */
export const FileEnum = {
  type: FileTypeEnum,
  status: FileStatusEnum,
  storageType: StorageTypeEnum,
}
