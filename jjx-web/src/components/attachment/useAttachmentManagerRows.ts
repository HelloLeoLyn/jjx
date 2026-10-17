import { computed, ref } from 'vue'
import type { AttachmentRefItem } from '@/api/system/attachmentRef'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import { ownershipKeyOfPending, ownershipKeyOfRef, type AttachmentSession } from '@/composables/useAttachmentSession'
import { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'
import { attachmentDocFromFile, type AttachmentDocRow } from './useAttachmentDocList'
import type { AttachmentRow } from './useAttachmentRowText'

type ManagerRowBase = AttachmentRow & { rowKey: string; ownershipKey: string | null }
export type PendingManagerRow = ManagerRowBase & {
  kind: 'pending'
  data: PendingEntry
  refId: null
}
export type RefManagerRow = ManagerRowBase & {
  kind: 'ref'
  data: AttachmentRefItem
  refId: number
}
export type ReadonlyManagerRow = ManagerRowBase & AttachmentDocRow & {
  kind: 'readonly'
  readonlyReason: string
}
export type EditableManagerRow = PendingManagerRow | RefManagerRow
export type AttachmentManagerRow = EditableManagerRow | ReadonlyManagerRow

/** 展示适配层：只派生行，不复制会话队列或编辑差异；引用按 refId 保留，不按文件去重。 */
export function useAttachmentManagerRows(
  session: AttachmentSession,
  source: () => { sourceType?: string; sourceNo?: string; lockReason?: string },
) {
  const filterKey = ref('')
  const ownershipOptions = computed(() => session.ownerships.value)
  const pendingCount = computed(() => session.pending.value.length)
  const ownRows = computed<EditableManagerRow[]>(() => [
    ...session.pending.value.map((entry): PendingManagerRow => ({
      rowKey: `pending:${entry.attachmentId}`,
      kind: 'pending', data: entry, refId: null,
      ownershipKey: ownershipKeyOfPending(entry),
      attachmentId: entry.attachmentId,
      fileName: entry.fileName, displayName: entry.displayName,
      documentType: entry.documentType, fileSize: entry.fileSize,
      createTime: entry.createdAt, productCode: entry.productCode,
      sourceType: source().sourceType, sourceNo: source().sourceNo,
    })),
    ...session.refRows.value.map((row): RefManagerRow => {
      const { id: refId, ...fields } = row
      return {
        ...fields, rowKey: `ref:${refId}`, kind: 'ref', data: row,
        refId, ownershipKey: ownershipKeyOfRef(row),
      }
    }),
  ])
  const tableRows = computed(() => ownRows.value.filter(row =>
    !filterKey.value || row.ownershipKey === filterKey.value,
  ))
  // 本任务保留历史/同链路独立展示、计数及行级屏蔽口径，后续合表复用同一行模型。
  const readonlyDocRows = computed<ReadonlyManagerRow[]>(() => [
    ...session.legacyOnlyRows.value.map((row): ReadonlyManagerRow => ({
      ...attachmentDocFromFile(row, AttachmentDocumentStatusEnum.LEGACY.value),
      rowKey: `legacy:${row.id}`, kind: 'readonly', ownershipKey: null,
      readonlyReason: '历史附件尚无本单有效引用，仅供查阅',
    })),
    ...session.traceOnlyRows.value.map((row): ReadonlyManagerRow => ({
      ...attachmentDocFromFile(row, AttachmentDocumentStatusEnum.TRACE.value),
      rowKey: `trace:${row.id}`, kind: 'readonly', ownershipKey: null,
      readonlyReason: '同链路附件仅供查阅，不能在本单修改',
    })),
  ])

  /** 每次操作读取当前会话权限及成员关系，避免权限变化、移除或刷新后继续使用旧行。 */
  function capabilities(row: AttachmentManagerRow) {
    const current = row.kind === 'ref'
      ? session.refRows.value.some(item => item.id === row.refId)
      : row.kind === 'pending' && session.pending.value.includes(row.data)
    const editable = row.kind !== 'readonly' && current && session.canEdit.value
    const saving = row.kind === 'ref' && session.savingRefIds.value.has(row.refId)
    return {
      editable,
      saving,
      canEdit: editable && !saving,
      canSelect: editable && row.kind === 'ref' && !saving,
      canRemove: editable && !saving,
      canSave: editable && row.kind === 'ref' && !saving && session.canSave.value,
      readonlyReason: row.kind === 'readonly' ? row.readonlyReason
        : !current ? '附件已移除或刷新'
          : !session.canEdit.value ? source().lockReason || '当前附件只读或无编辑权限'
            : saving ? '附件正在保存' : '',
    }
  }
  function refRow(refId: number) {
    return ownRows.value.find((row): row is RefManagerRow => row.kind === 'ref' && row.refId === refId)
  }
  return { tableRows, readonlyDocRows, filterKey, ownershipOptions, pendingCount, capabilities, refRow }
}
