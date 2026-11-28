import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from '../useAttachmentDocTypes'

describe('菲林版本附件类型来源', () => {
  it('新增与修改共用来源，一级显示工程技术资料，二级只显示适用菲林类型', () => {
    const types = ref([
      { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料', is_active: 1 },
      { dict_code: 'attachment_document_type', item_key: 'FILM', label: '菲林', is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC', sources: ['product', 'print_artwork_version'] } },
      { dict_code: 'attachment_document_type', item_key: 'BOM', label: 'BOM', is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC', sources: ['product'] } },
    ] as AttachmentDocumentTypeItem[])
    const model = useAttachmentDocTypes(types, () => 'print_artwork_version')
    expect(model.selectableCategories.value).toEqual([{ key: 'ENGINEERING_DOC', label: '工程技术资料' }])
    expect(model.secondaryGroupsFor('ENGINEERING_DOC').flatMap(group => group.options.map(type => type.item_key))).toEqual(['FILM'])
    expect(model.categoryKeyOfType('FILM')).toBe('ENGINEERING_DOC')
    // 本次来源补齐不能改变原产品入口的候选范围。
    const product = useAttachmentDocTypes(types, () => 'product')
    expect(product.selectableTypes.value.map(type => type.item_key)).toEqual(['FILM', 'BOM'])
  })
})
