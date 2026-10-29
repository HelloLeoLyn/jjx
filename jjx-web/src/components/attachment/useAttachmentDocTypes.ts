import { computed, type ComputedRef, type Ref } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'

/**
 * 附件资料类型字典（一级 attachment_category + 二级 attachment_document_type）的派生逻辑。
 *
 * 抽成 composable 的唯一原因：AttachmentCenter（待绑定行 / 分类命名弹窗）与 AttachmentRefList
 * （引用列表的类型列 / 批量改类型）都要用同一套「二级→一级」「按一级分组」「本来源可选集合」，
 * 两处各写一份就是第二真源。这里只读字典，不写任何数据。
 */
export type SecondaryGroup = { key: string; label: string; options: AttachmentDocumentTypeItem[] }

export function useAttachmentDocTypes(
  documentTypes: Ref<AttachmentDocumentTypeItem[]> | ComputedRef<AttachmentDocumentTypeItem[]>,
  sourceType: Ref<string> | (() => string),
) {
  const source = typeof sourceType === 'function' ? sourceType : () => sourceType.value

  function safeJson(raw: string): Record<string, unknown> {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }

  /** 二级 → 一级（attachment_category）解析：用真实字典 ext_data.parent，不硬编码中文；未知/悬空返回 null */
  function parentCategoryOf(t: AttachmentDocumentTypeItem): AttachmentDocumentTypeItem | null {
    const ext = typeof t.ext_data === 'string' ? safeJson(t.ext_data) : t.ext_data
    const parent = (ext as any)?.parent
    if (!parent) return null
    const parentKey = String(parent).toLowerCase()
    return (
      documentTypes.value.find(
        (x) => x.item_key.toLowerCase() === parentKey && x.dict_code === 'attachment_category',
      ) || null
    )
  }

  /** 二级编码 → 一级编码（未知返回空串） */
  function categoryKeyOfType(code?: string | null): string {
    if (!code) return ''
    const t = documentTypes.value.find(
      (x) => x.item_key === code && x.dict_code === 'attachment_document_type',
    )
    return t ? parentCategoryOf(t)?.item_key || '' : ''
  }

  /** 二级编码 → 一级名（无/未知返回 '—'） */
  function categoryLabelOf(code?: string | null): string {
    const key = categoryKeyOfType(code)
    if (!key) return '—'
    const c = documentTypes.value.find((x) => x.item_key === key && x.dict_code === 'attachment_category')
    return c ? c.label : key
  }

  /** 二级编码 → 二级名（空='未分类'；未知回退原编码） */
  function secondLabelOf(code?: string | null): string {
    if (!code) return '未分类'
    const t = documentTypes.value.find(
      (x) => x.item_key === code && x.dict_code === 'attachment_document_type',
    )
    return t ? t.label : code
  }

  /** 本来源可选的二级（启用 + 未停用 + sources 含本来源） */
  const selectableTypes = computed(() =>
    documentTypes.value.filter((t) => {
      if (t.dict_code !== 'attachment_document_type' || t.is_active !== 1) return false
      const ext = typeof t.ext_data === 'string' ? safeJson(t.ext_data) : t.ext_data
      const sources = (ext as any)?.sources
      if (!Array.isArray(sources)) return true
      return sources.includes(source())
    }),
  )

  /** 两级形态：按一级分组，组内为可选二级 */
  const groupedSelectableTypes = computed<SecondaryGroup[]>(() => {
    const groups = new Map<string, SecondaryGroup>()
    for (const t of selectableTypes.value) {
      const parent = parentCategoryOf(t)
      const key = parent?.item_key || '__unknown__'
      let g = groups.get(key)
      if (!g) {
        g = { key, label: parent?.label || '未分类', options: [] }
        groups.set(key, g)
      }
      g.options.push(t)
    }
    return Array.from(groups.values())
  })

  /** 一级选项（= 本来源可选二级所属的一级） */
  const selectableCategories = computed(() =>
    groupedSelectableTypes.value
      .filter((g) => g.key !== '__unknown__')
      .map((g) => ({ key: g.key, label: g.label })),
  )

  /** 二级下拉的组选项：选了某一级则只给该组，否则给全部（保留「按一级分组」形态） */
  function secondaryGroupsFor(categoryKey: string): SecondaryGroup[] {
    return categoryKey
      ? groupedSelectableTypes.value.filter((g) => g.key === categoryKey)
      : groupedSelectableTypes.value
  }

  return {
    parentCategoryOf,
    categoryKeyOfType,
    categoryLabelOf,
    secondLabelOf,
    selectableTypes,
    groupedSelectableTypes,
    selectableCategories,
    secondaryGroupsFor,
  }
}
