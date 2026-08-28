import { createEnum } from '../base'

export const QualityTemplateStatus = {
  DRAFT: 0,
  ACTIVE: 1,
  DISABLED: 2,
} as const

export const QualityTemplateStatusEnum = createEnum<number>({
  items: [
    { value: QualityTemplateStatus.DRAFT, label: '草稿', tagProps: { type: 'info' } },
    { value: QualityTemplateStatus.ACTIVE, label: '生效', tagProps: { type: 'success' } },
    { value: QualityTemplateStatus.DISABLED, label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const QualityTemplateCategory = {
  BLANK: 'blank',
  DATA: 'data',
} as const

export const QualityTemplateCategoryEnum = createEnum<string>({
  items: [
    { value: QualityTemplateCategory.BLANK, label: '空白表', tagProps: { type: 'info' } },
    { value: QualityTemplateCategory.DATA, label: '数据联动', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'info' },
})
