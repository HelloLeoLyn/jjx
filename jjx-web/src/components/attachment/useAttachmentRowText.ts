import type { Ref } from 'vue'
import type { AttachmentDocumentTypeItem, AttachmentRefItem } from '@/api/system/attachmentRef'
import { AttachmentSourceTypeEnum, canonicalAttachmentSource } from '@/enums/system/AttachmentEnum'
import type { AttachmentFieldKey } from './attachmentFields'
import { formatSize, formatTime } from './attachmentFormat'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'

/** 引用、链路与待绑定行的共同展示数据，读写模式不改变字段含义。 */
export type AttachmentRow = Pick<
  AttachmentRefItem,
  | 'attachmentId'
  | 'fileName'
  | 'displayName'
  | 'sourceNo'
  | 'productCode'
  | 'documentType'
  | 'fileSize'
  | 'fileType'
  | 'createBy'
  | 'createTime'
> & {
  sourceType?: string | null
  sourceTypeName?: string | null
  status?: string
}

export function useAttachmentRowText(
  types: Ref<AttachmentDocumentTypeItem[]>,
  sourceType: () => string
) {
  const { categoryLabelOf, secondLabelOf } = useAttachmentDocTypes(types, sourceType)
  function textOf(key: AttachmentFieldKey, row: AttachmentRow) {
    if (key === 'sourceType') {
      const source = row.sourceType || sourceType()
      return (
        row.sourceTypeName ||
        (source ? AttachmentSourceTypeEnum.getLabel(canonicalAttachmentSource(source)) : '-')
      )
    }
    if (key === 'fileName') return row.fileName || `附件 #${row.attachmentId}`
    if (key === 'displayName') return row.displayName || row.fileName || `附件 #${row.attachmentId}`
    if (key === 'category') return row.documentType ? categoryLabelOf(row.documentType) : '-'
    if (key === 'documentType') return row.documentType ? secondLabelOf(row.documentType) : '-'
    if (key === 'fileSize') return row.fileSize == null ? '-' : formatSize(row.fileSize)
    if (key === 'createTime') return row.createTime ? formatTime(row.createTime) : '-'
    return row[key as keyof AttachmentRow] ?? '-'
  }
  return { textOf }
}
