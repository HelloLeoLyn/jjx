import { createNamedEnum } from '@/enums/base'

/** 规范分色检查表·单项自查结论；空值表示未检查（打印留空）。 */
export const ColorCheckResultEnum = createNamedEnum(
  {
    CORRECT: { value: 'CORRECT', label: '正确', tagProps: { type: 'success' } },
    INCORRECT: { value: 'INCORRECT', label: '错误', tagProps: { type: 'danger' } },
    NA: { value: 'NA', label: '不适用', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)

export interface ColorCheckItem {
  /** 稳定键，用于保存与历史比对，勿随意改动。 */
  key: string
  /** 展示名称，随参考样张。 */
  label: string
}

export interface ColorCheckGroup {
  key: string
  label: string
  /** 该块下方的注意事项；样张文字待核定，现留空占位。 */
  note: string
  items: ColorCheckItem[]
}

/** 四块（规范、面板菲林分色、线路、刀模治具）共 25 项；与后端 ColorCheckItemEnum 保持同步。 */
export const COLOR_CHECK_GROUPS: ColorCheckGroup[] = [
  {
    key: 'spec',
    label: '规范',
    note: '',
    items: [
      { key: 'spec.source_documents', label: '资料（客供资料、工程图纸）' },
      { key: 'spec.name_label', label: '品名标签' },
      { key: 'spec.material_spec', label: '材料规格' },
      { key: 'spec.customer_internal_notes', label: '客户要求和内部备注' },
      { key: 'spec.processing_sequence', label: '加工冲形作业工序' },
      { key: 'spec.print_color_sequence', label: '印刷色序（客户要求和备注）' },
      { key: 'spec.artwork_die_dimensions', label: '产品彩图和刀模尺寸图' },
    ],
  },
  {
    key: 'panel',
    label: '面板菲林分色',
    note: '',
    items: [
      { key: 'panel.printable_content', label: '内容（可印刷）' },
      { key: 'panel.bleed', label: '出血' },
      { key: 'panel.surface_effect', label: '表面效果' },
      { key: 'panel.direction', label: '方向' },
      { key: 'panel.pitch', label: '跳距' },
      { key: 'panel.film_label', label: '菲林标签（目数和名称）' },
    ],
  },
  {
    key: 'circuit',
    label: '线路',
    note: '',
    items: [
      { key: 'circuit.functional_routing', label: '功能走线' },
      { key: 'circuit.led', label: 'LED灯（大小颜色正负极）' },
      { key: 'circuit.annotations', label: '辅助标注' },
      { key: 'circuit.direction', label: '方向' },
      { key: 'circuit.uv_jumper', label: 'UV跳线点' },
    ],
  },
  {
    key: 'tooling',
    label: '刀模治具',
    note: '',
    items: [
      { key: 'tooling.outline_die', label: '外形刀' },
      { key: 'tooling.spacer_die', label: '隔片刀' },
      { key: 'tooling.adhesive_die', label: '背胶刀' },
      { key: 'tooling.emboss_die', label: '凹凸模' },
      { key: 'tooling.direction', label: '方向' },
      { key: 'tooling.scale', label: '缩放' },
      { key: 'tooling.other_die', label: '垫片保护膜刀等' },
    ],
  },
]

export const colorCheckItemKeys = COLOR_CHECK_GROUPS.flatMap((group) => group.items.map((item) => item.key))

export function colorCheckItemLabel(key: string): string {
  for (const group of COLOR_CHECK_GROUPS) {
    const item = group.items.find((entry) => entry.key === key)
    if (item) return item.label
  }
  return key
}
