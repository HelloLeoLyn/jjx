import { computed, onScopeDispose, ref, watch } from 'vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import type { AttachmentSourceItem } from '@/api/system/attachment'
import type { AttachmentSession } from '@/composables/useAttachmentSession'

/** 转单新增页只按明确来源读取报价引用，不改变本单队列或未保存单据的链路查询守卫。 */
export function useQuotationAttachmentView(
  session: AttachmentSession,
  quotationId: () => number | undefined,
  orderId: () => number | undefined,
): AttachmentSession {
  const sourceRows = ref<AttachmentSourceItem[]>([])
  const sourceError = ref('')
  const sourceLoading = ref(false)
  let request = 0

  watch(() => [quotationId(), orderId(), session.canView.value], async () => {
    const current = ++request
    sourceRows.value = []
    sourceError.value = ''
    sourceLoading.value = false
    const id = quotationId()
    if (!id || orderId() || !session.canView.value) return
    sourceLoading.value = true
    try {
      const response = await attachmentRefApi.listRefs('quotation', id)
      if (request !== current) return
      if (!Array.isArray(response.data)) throw new Error('来源报价附件返回格式异常')
      sourceRows.value = response.data
        .filter(row => row.sourceType === 'quotation' && row.sourceId === id && !row.deleted)
        .map(row => ({
          ...row,
          id: row.attachmentId,
          refId: row.id,
          bizType: row.sourceType,
          bizId: row.sourceId,
        }))
    } catch (error: unknown) {
      if (request !== current) return
      sourceError.value = error instanceof Error ? `来源报价附件加载失败：${error.message}` : '来源报价附件加载失败'
    } finally {
      if (request === current) sourceLoading.value = false
    }
  }, { immediate: true })
  onScopeDispose(() => { request++ })

  // 会话读写方法仍指向原实例；仅给 Manager 补只读展示行，不计入本单附件及保存内容。
  return {
    ...session,
    traceOnlyRows: computed(() => [...sourceRows.value, ...session.traceOnlyRows.value]),
    hasReadonlyDocs: computed(() => sourceRows.value.length > 0 || session.hasReadonlyDocs.value),
    traceError: computed(() => sourceError.value || session.traceError.value),
    traceLoading: computed(() => sourceLoading.value || session.traceLoading.value),
  }
}
