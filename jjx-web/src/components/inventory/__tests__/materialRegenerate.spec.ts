vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialRegenerateDialog from '../MaterialRegenerateDialog.vue'
import {
  buildRegeneration,
  describeRegenerationRules,
  diffCharacters,
  hasRegenerationChanges,
  regenerationValues,
  resolveFinalName,
  specificationUnavailableReason,
} from '../materialRegenerate'
import type { MaterialAttributeSchema } from '@/types/inventory/materialMaster'

const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  dictionaries: vi.fn(),
  configuration: vi.fn(),
  renderNaming: vi.fn(),
  updateNaming: vi.fn(),
  legacyUpdate: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  put: vi.fn(),
}))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: mocks }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { update: mocks.legacyUpdate } }))
vi.mock('@/utils/request', () => ({ default: { put: mocks.put } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: mocks.success, error: mocks.error },
}))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Description = defineComponent({
  props: ['label'],
  template: '<div>{{ label }}<slot /></div>',
})
const Input = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
const Checkbox = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<label><input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" /><slot /></label>',
})
const Button = defineComponent({
  props: ['disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
})
const createDialog = () =>
  mount(MaterialRegenerateDialog, {
    props: { modelValue: true, materialId: 7 },
    global: {
      stubs: {
        ElDialog: Slot,
        ElDescriptions: Slot,
        ElDescriptionsItem: Description,
        ElTag: Slot,
        ElForm: Slot,
        ElFormItem: Slot,
        ElInput: Input,
        ElCheckbox: Checkbox,
        ElButton: Button,
        ElSkeleton: true,
        ElEmpty: true,
      },
    },
  })

const current = { materialName: '人工名称', specification: '旧规格' }
const generated = { name: 'PC11008透明（3M）', specification: '厚度:0.175mm；表面处理:细砂' }

const schema: MaterialAttributeSchema = {
  version: 1,
  meta_options: [],
  fields: [
    {
      key: 'adhesive',
      label: '胶型',
      type: 'enum',
      required: true,
      options: [{ code: 'A', label: '丙烯酸' }],
    },
    { key: 'count', label: '数量', type: 'integer', required: true },
    { key: 'treated', label: '处理', type: 'boolean', required: true, false_label: '未处理' },
    { key: 'missing', label: '缺项', type: 'multienum', required: true },
    { key: 'optional', label: '选填', type: 'string', required: false },
  ],
  name_pattern: {
    fields: ['brand', 'model', 'adhesive'],
    separator: ' ',
    separators: { adhesive: '' },
    empty: 'skip',
  },
  spec_pattern: {
    fields: [
      { key: 'thickness_mm', sep: '', unit: 'μm', scale: 1 },
      { key: 'width', sep: '*', unit: 'mm' },
      { key: 'length', sep: '*', unit: 'm', scale: 2 },
    ],
    show_label: false,
    with_unit: true,
    unit_position: 'each',
  },
}

describe('重算信息纯展示函数', () => {
  it.each([
    ['PC透明', 'PC黑', '透明', '黑'],
    ['0.175*300mm', '0.175*305mm', '0', '5'],
    ['', '新增', '', '新增'],
    ['删除', '', '删除', ''],
    ['甲😀乙', '甲🧪乙', '😀', '🧪'],
    ['ABA', 'BAA', 'A', 'A'],
  ])('逐字区分 %s → %s 的删除与新增', (before, after, removed, added) => {
    const diff = diffCharacters(before, after)
    expect(diff.current.map((p) => p.text).join('')).toBe(before)
    expect(diff.generated.map((p) => p.text).join('')).toBe(after)
    expect(
      diff.current
        .filter((p) => p.kind === 'removed')
        .map((p) => p.text)
        .join('')
    ).toBe(removed)
    expect(
      diff.generated
        .filter((p) => p.kind === 'added')
        .map((p) => p.text)
        .join('')
    ).toBe(added)
  })

  it.each(['', '无差异😀'])('相同字符无新增删除：%s', (value) => {
    const diff = diffCharacters(value, value)
    expect(diff.changed).toBe(false)
    expect([...diff.current, ...diff.generated].every((p) => p.kind === 'equal')).toBe(true)
  })

  it('规则保留字段顺序、空格与紧贴分隔符、单位与小数位', () => {
    const rules = describeRegenerationRules(schema)
    expect(rules.name).toBe('品牌 型号胶型')
    expect(rules.nameDetails).toContain('型号=" "、胶型=""')
    expect(rules.spec).toBe('厚(μm,1位)*宽(mm)*长(m,2位)')
    expect(rules.specDetails).toContain('单位位置：每项')
    const zeroScale = describeRegenerationRules({
      ...schema,
      spec_pattern: {
        ...schema.spec_pattern!,
        fields: [{ key: 'width', sep: '*', scale: 0 }],
      },
    })
    expect(zeroScale.spec).toBe('宽(0位)')
  })

  it('无规则与空字段清单均明确显示未配置', () => {
    for (const input of [
      null,
      { ...schema, name_pattern: { ...schema.name_pattern!, fields: [] }, spec_pattern: null },
    ]) {
      expect(describeRegenerationRules(input).name).toBe('该品类未配置命名规则')
      expect(describeRegenerationRules(input).spec).toBe('该品类未配置规格模板')
    }
  })

  it('当前源值、字典标签、必填属性逐项列出，0/false不当作空值', () => {
    const values = regenerationValues(
      { materialMeta: 'PC', brand: 'B', thicknessMm: 0.175 },
      {
        adhesive: 'A',
        count: 0,
        treated: false,
        missing: [],
      },
      schema,
      { brand: [{ code: 'B', label: '博明' }] }
    )
    expect(values).toHaveLength(11)
    expect(values.find((v) => v.key === 'brand')?.value).toBe('博明')
    expect(values.find((v) => v.key === 'thickness_mm')?.value).toBe('175μm')
    expect(values.find((v) => v.key === 'attribute:adhesive')?.value).toBe('丙烯酸')
    expect(values.find((v) => v.key === 'attribute:count')).toMatchObject({
      value: '0',
      empty: false,
    })
    expect(values.find((v) => v.key === 'attribute:treated')).toMatchObject({
      value: '未处理',
      empty: false,
    })
    expect(values.find((v) => v.key === 'attribute:missing')?.empty).toBe(true)
    expect(values.find((v) => v.key === 'width')?.empty).toBe(true)
    expect(values.some((v) => v.key === 'attribute:optional')).toBe(false)
  })

  it('尺寸参与值按三个模板字段单位展示，旧整行单位不再参与', () => {
    const fields = [{ key: 'thickness_mm', sep: '*', unit: 'μm' as const, scale: 1 }, { key: 'width', sep: '*', unit: 'mm' as const }, { key: 'length', sep: '*', unit: 'm' as const, scale: 2 }]
    const values = regenerationValues({ widthMm: 315, lengthMm: 1000000, thicknessMm: 0.254, sizeUnit: 'cm' }, {}, { ...schema, spec_pattern: { fields } }, {})
    expect(values.find(v => v.key === 'thickness_mm')).toMatchObject({ label: '厚度（μm）', value: '254μm' })
    expect(values.find(v => v.key === 'width')).toMatchObject({ label: '宽（mm）', value: '315mm' })
    expect(values.find(v => v.key === 'length')).toMatchObject({ label: '长（m）', value: '1000m' })
    expect(values.some(v => v.key === 'sizeUnit')).toBe(false)
  })

  it('未生成原因区分未归类、无启用模板、字段全空，不推算生成结果', () => {
    expect(specificationUnavailableReason(null, null, '')).toContain('未归类')
    expect(specificationUnavailableReason(23, null, '')).toContain('该品类无启用模板')
    expect(specificationUnavailableReason(23, schema, '未生成：规格模板字段当前均无值')).toContain(
      '模板字段全空'
    )
    expect(specificationUnavailableReason(23, null, '', '网络异常')).toContain('暂无法核实原因')
  })
})

describe('重算名称与规格', () => {
  it('对比当前值与生成值，标出变化', () => {
    const result = buildRegeneration(current, generated, ['厚度：0.175mm'])
    expect(result.currentName).toBe('人工名称')
    expect(result.generatedName).toBe('PC11008透明（3M）')
    expect(result.nameChanged).toBe(true)
    expect(result.specChanged).toBe(true)
    expect(result.evidence).toEqual(['厚度：0.175mm'])
  })

  it('一致时不算变化', () => {
    const result = buildRegeneration(
      { materialName: generated.name, specification: generated.specification },
      generated,
      []
    )
    expect(result.nameChanged).toBe(false)
    expect(result.specChanged).toBe(false)
    expect(hasRegenerationChanges(result, result.currentName)).toBe(false)
  })

  it('默认保留当前人工名称，勾选后才采用生成名称', () => {
    const result = buildRegeneration(current, generated, [])
    expect(resolveFinalName(result, false)).toBe('人工名称')
    expect(resolveFinalName(result, true)).toBe('PC11008透明（3M）')
  })

  it('当前名称为空时回落到生成名称', () => {
    const result = buildRegeneration({ materialName: '', specification: '' }, generated, [])
    expect(resolveFinalName(result, false)).toBe('PC11008透明（3M）')
  })

  it('最终值不同于当前值才算需要保存', () => {
    const result = buildRegeneration(current, generated, [])
    expect(hasRegenerationChanges(result, '人工名称')).toBe(true)
    expect(hasRegenerationChanges(result, generated.name)).toBe(true)
    const unavailable = buildRegeneration(
      current,
      {
        ...generated,
        specification: current.specification,
        specGenerated: false,
      },
      []
    )
    expect(unavailable.generatedSpec).toBe('旧规格')
    expect(hasRegenerationChanges(unavailable, '人工名称')).toBe(false)
  })
})

describe('重算弹窗命名补丁保存', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.detail.mockResolvedValue({
      data: {
        material: {
          materialId: 7,
          materialName: '人工名称',
          specification: '旧规格',
          materialMeta: 'PC',
        },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: '旧规格',
          generated: false,
          message: '未生成：无模板，保留原规格',
        },
      },
    })
    mocks.dictionaries.mockResolvedValue({ data: { material_meta: [{ code: 'PC', label: 'PC' }] } })
    mocks.configuration.mockResolvedValue({ data: null })
    mocks.updateNaming.mockResolvedValue({ data: {} })
    mocks.renderNaming.mockResolvedValue({ data: null })
  })

  it('展示差异并保留人工名称，只有勾选才替换且提示替换人工名称', async () => {
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('当前名称')
    expect(wrapper.text()).toContain('重算名称')
    expect(wrapper.text()).toContain('有变化')
    expect(wrapper.text()).toContain('会替换当前人工确认的名称')
    expect(wrapper.findAll('input')[0].element.value).toBe('人工名称')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(wrapper.findAll('input')[0].element.value).toBe('PC')
    expect(mocks.updateNaming).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('取消只关闭弹窗，不写库且不触发列表刷新', async () => {
    const wrapper = createDialog()
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(mocks.updateNaming).not.toHaveBeenCalled()
    expect(mocks.legacyUpdate).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('并排展示逐字差异、规则原文与当前参与值，空值带红色类和提示', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { ...current, categoryId: 23, materialMeta: 'PC', thicknessMm: 0.175 },
        attributes: { adhesive: 'A', count: 0, treated: false },
        specificationGeneration: {
          specification: '新规格',
          generated: true,
          message: '按规格模板生成',
        },
      },
    })
    mocks.configuration.mockResolvedValue({ data: { schema } })
    mocks.renderNaming.mockResolvedValue({ data: '人工新名称' })
    const wrapper = createDialog()
    await flushPromises()
    expect(mocks.configuration).toHaveBeenCalledWith(23)
    const name = wrapper.find('[data-field="名称"]')
    expect(name.find('ins').text()).toBe('新')
    const spec = wrapper.find('[data-field="规格"]')
    expect(spec.find('del').text()).toBe('旧')
    expect(spec.find('ins').text()).toBe('新')
    expect(wrapper.text()).toContain('规格由后端按模板重算、不能手改')
    expect(wrapper.text()).toContain('厚(μm,1位)*宽(mm)*长(m,2位)')
    expect(wrapper.find('[data-value-key="attribute:adhesive"]').text()).toContain('丙烯酸')
    expect(wrapper.find('[data-value-key="width"]').classes()).toContain('missing-value')
    expect(wrapper.find('[data-value-key="width"]').text()).toContain(
      '该字段为空，生成结果会缺这一段'
    )
    expect(wrapper.find('[data-value-key="attribute:count"]').classes()).not.toContain(
      'missing-value'
    )
    expect(wrapper.findAll('input:not([type=checkbox])')).toHaveLength(1)
    wrapper.unmount()
  })

  it('相同结果显示无差异；未归类时显示明确原因与未配置规则', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { materialName: 'PC', materialMeta: 'PC', specification: '旧规格' },
        attributes: {},
        specificationGeneration: { specification: '旧规格', generated: false, message: '未生成' },
      },
    })
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.findAll('.no-diff')).toHaveLength(2)
    expect(wrapper.findAll('.comparison del, .comparison ins')).toHaveLength(0)
    expect(wrapper.text()).toContain('未生成：未归类')
    expect(wrapper.text()).toContain('该品类未配置命名规则')
    expect(wrapper.text()).toContain('该品类未配置规格模板')
    expect(mocks.configuration).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each([
    ['该品类尚未配置启用的属性模板', '该品类无启用模板'],
    ['配置网络异常', '配置读取失败，暂无法核实'],
  ])('配置接口失败 %s 时展示事实，保留原保存能力', async (error, expected) => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { ...current, categoryId: 23 },
        attributes: {},
        specificationGeneration: { specification: '旧规格', generated: false, message: '未生成' },
      },
    })
    mocks.configuration.mockRejectedValue(new Error(error))
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain(expected)
    await wrapper.find('input:not([type=checkbox])').setValue('人工新名称')
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '人工新名称',
      reason: expect.any(String),
    })
    wrapper.unmount()
  })

  it('仅提交命名字段及依据，成功后关闭并通知列表刷新，等待中不重复保存', async () => {
    let finish!: (value: unknown) => void
    mocks.updateNaming.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    const wrapper = createDialog()
    await flushPromises()
    await wrapper.findAll('input')[0].setValue(' 人工确认新名称 ')
    const save = wrapper.findAll('button')[1]
    await save.trigger('click')
    await save.trigger('click')
    expect(mocks.updateNaming).toHaveBeenCalledTimes(1)
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '人工确认新名称',
      reason: expect.stringContaining('回退材料 + 型号 + 颜色（品牌）'),
    })
    expect(mocks.legacyUpdate).not.toHaveBeenCalled()
    expect(wrapper.emitted('success')).toBeUndefined()
    finish({ data: {} })
    await flushPromises()
    expect(wrapper.emitted('success')).toEqual([[]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('最终值无变化时禁用保存，不提交', async () => {
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.findAll('input:not([type=checkbox])')).toHaveLength(1)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).toContain('未生成')
    expect(wrapper.text()).toContain('旧规格')
    expect(wrapper.text()).toContain('如需调整请回编辑改属性/厚度/尺寸')
    const save = wrapper.findAll('button')[1]
    expect(save.element.disabled).toBe(true)
    await save.trigger('click')
    expect(mocks.updateNaming).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('保存失败保留人工输入且不关闭、不刷新', async () => {
    mocks.updateNaming.mockRejectedValue(new Error('命名保存失败'))
    const wrapper = createDialog()
    await flushPromises()
    await wrapper.findAll('input')[0].setValue('手动输入')
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(wrapper.findAll('input')[0].element.value).toBe('手动输入')
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(mocks.error).toHaveBeenCalledWith('命名保存失败')
    wrapper.unmount()
  })

  it('规格只读，直接采用后端保存预览，规格变化可单独保存', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: {
          materialId: 7,
          materialName: '人工名称',
          specification: '旧规格',
          thickness: '0.175',
          widthSize: '300×305',
        },
        attributes: {},
        configuration: {
          schema: { fields: [{ key: 'thickness_mm', label: '厚度', type: 'decimal', unit: 'mm' }] },
        },
        specificationGeneration: {
          specification: '0.175*300*305mm',
          generated: true,
          message: '按当前规格模板生成',
        },
      },
    })
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('0.175*300*305mm')
    expect(wrapper.text()).not.toContain('厚度:0.175mm；尺寸:300*305mm')
    expect(wrapper.text()).toContain('按当前规格模板生成')
    expect(wrapper.findAll('input:not([type=checkbox])')).toHaveLength(1)
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '人工名称',
      reason: expect.any(String),
    })
    wrapper.unmount()
  })

  it('空模板保留历史规格，不能误报规格变化', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: current,
        attributes: {},
        configuration: { schema: { fields: [] } },
        specificationGeneration: {
          specification: '旧规格',
          generated: false,
          message: '未生成：无模板，保留原规格',
        },
      },
    })
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('未生成')
    expect(wrapper.text()).toContain('旧规格')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(true)
    wrapper.unmount()
  })

  it('名称复用新增编辑的后端品类规则，默认保留人工名称，勾选才提交生成名称', async () => {
    const material = {
      materialId: 7,
      categoryId: 23,
      materialName: '人工名称',
      specification: '旧规格',
      materialMeta: 'PC',
      model: '11008',
      color: '透明',
      brand: 'BOMING',
      widthMm: 300,
      lengthMm: 305,
      thicknessMm: 0.175,
      sizeUnit: 'mm',
      isCustomerSupplied: true,
      isSample: true,
      attributeJson: '{"adhesive_type":"ACRYLIC"}',
    }
    const attributes = { adhesive_type: 'ACRYLIC' }
    mocks.detail.mockResolvedValue({
      data: {
        material,
        attributes,
        configuration: { schema: { fields: [], name_pattern: { fields: ['brand', 'model'] } } },
        specificationGeneration: {
          specification: '旧规格',
          generated: true,
          message: '按规格模板生成',
        },
      },
    })
    mocks.renderNaming.mockResolvedValue({ data: '博明 11008 透明' })
    const wrapper = createDialog()
    await flushPromises()
    const { sizeUnit: _legacySizeUnit, ...namingMaterial } = material
    expect(mocks.renderNaming).toHaveBeenCalledWith(23, { ...namingMaterial, attributeJson: attributes })
    expect(wrapper.text()).toContain('博明 11008 透明')
    expect(wrapper.text()).toContain('按该品类当前命名规则生成')
    expect(wrapper.text()).not.toContain('PC11008透明（BOMING）')
    expect(wrapper.findAll('input')[0].element.value).toBe('人工名称')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(true)
    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(wrapper.findAll('input')[0].element.value).toBe('博明 11008 透明')
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '博明 11008 透明',
      reason: expect.stringContaining('按该品类当前命名规则生成'),
    })
    wrapper.unmount()
  })

  it('后端模板无值需要清空规格时，预览显示空值且允许单独保存清空', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { ...current, categoryId: 23 },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: null,
          generated: false,
          message: '未生成：模板字段当前均无值，规格留空',
        },
      },
    })
    mocks.configuration.mockResolvedValue({ data: { schema } })
    const wrapper = createDialog()
    await flushPromises()
    const generatedSpec = wrapper.find('[data-field="规格"] [data-side="generated"]')
    expect(generatedSpec.text()).not.toContain('旧规格')
    expect(generatedSpec.text()).toContain('—')
    expect(wrapper.find('[data-field="规格"]').text()).toContain('有变化')
    expect(wrapper.text()).toContain('模板字段当前均无值，规格留空')
    expect(wrapper.find('[data-field="规格"]').text()).toContain('未生成：模板字段全空，规格留空')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(false)
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '人工名称',
      reason: expect.stringContaining('规格留空'),
    })
    wrapper.unmount()
  })

  it.each([null, ''])('品类规则返回%j时，与新增编辑一样回退旧名称生成', async (name) => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { ...current, categoryId: 23, materialMeta: 'PC', model: '11008' },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: '旧规格',
          generated: false,
          message: '保留原规格',
        },
      },
    })
    mocks.renderNaming.mockResolvedValue({ data: name })
    const wrapper = createDialog()
    await flushPromises()
    expect(mocks.renderNaming).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('回退材料 + 型号 + 颜色（品牌）')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(wrapper.findAll('input')[0].element.value).toBe('PC11008')
    wrapper.unmount()
  })

  it('命名接口报错时不回退旧规则，也不允许保存', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: { ...current, categoryId: 23 },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: '新规格',
          generated: true,
          message: '按规格模板生成',
        },
      },
    })
    mocks.renderNaming.mockRejectedValue(new Error('命名规则加载失败'))
    const wrapper = createDialog()
    await flushPromises()
    expect(mocks.error).toHaveBeenCalledWith('命名规则加载失败')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(true)
    await wrapper.findAll('button')[1].trigger('click')
    expect(mocks.updateNaming).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('缺少后端规格结果时不显示旧算法预览，也不允许保存', async () => {
    mocks.detail.mockResolvedValue({
      data: {
        material: current,
        attributes: {},
        configuration: null,
      },
    })
    const wrapper = createDialog()
    await flushPromises()
    expect(mocks.error).toHaveBeenCalledWith('规格生成结果加载失败，请稍后重试')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(true)
    expect(mocks.updateNaming).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('关闭弹窗后命名响应迟到，不覆盖重新打开的另一条物料', async () => {
    let finish!: (value: unknown) => void
    mocks.detail.mockResolvedValueOnce({
      data: {
        material: { ...current, categoryId: 23 },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: '旧规格',
          generated: true,
          message: '按规格模板生成',
        },
      },
    })
    mocks.renderNaming.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    const wrapper = createDialog()
    await flushPromises()
    await wrapper.setProps({ modelValue: false })
    mocks.detail.mockResolvedValueOnce({
      data: {
        material: { materialId: 8, materialName: '另一条物料', specification: '另一规格' },
        attributes: {},
        configuration: null,
        specificationGeneration: {
          specification: '另一规格',
          generated: false,
          message: '保留原规格',
        },
      },
    })
    await wrapper.setProps({ modelValue: true, materialId: 8 })
    await flushPromises()
    finish({ data: '迟到的旧物料名称' })
    await flushPromises()
    expect(wrapper.findAll('input')[0].element.value).toBe('另一条物料')
    expect(wrapper.text()).toContain('另一规格')
    expect(wrapper.text()).not.toContain('迟到的旧物料名称')
    expect(wrapper.findAll('button')[1].element.disabled).toBe(true)
    wrapper.unmount()
  })

  it('API 使用 master 命名补丁 URL', async () => {
    const { materialMasterApi } = await vi.importActual<
      typeof import('@/api/inventory/materialMaster')
    >('@/api/inventory/materialMaster')
    const payload = { materialName: '名称' }
    await materialMasterApi.updateNaming(7, payload)
    expect(mocks.put).toHaveBeenCalledWith('/inventory/material/master/7/naming', payload)
  })

  it('关闭重开后迟到的配置不覆盖新物料规则和参与值', async () => {
    let finish!: (value: unknown) => void
    mocks.detail.mockResolvedValueOnce({
      data: {
        material: { ...current, categoryId: 23 },
        attributes: {},
        specificationGeneration: {
          specification: '旧规格',
          generated: false,
          message: '保留原规格',
        },
      },
    })
    mocks.configuration.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    const wrapper = createDialog()
    await flushPromises()
    await wrapper.setProps({ modelValue: false })
    mocks.detail.mockResolvedValueOnce({
      data: {
        material: { materialName: '另一条物料', specification: '另一规格' },
        attributes: {},
        specificationGeneration: {
          specification: '另一规格',
          generated: false,
          message: '保留原规格',
        },
      },
    })
    await wrapper.setProps({ modelValue: true, materialId: 8 })
    await flushPromises()
    finish({ data: { schema } })
    await flushPromises()
    expect(wrapper.text()).toContain('该品类未配置规格模板')
    expect(wrapper.text()).not.toContain('厚(μm,1位)')
    expect(wrapper.find('[data-value-key="attribute:adhesive"]').exists()).toBe(false)
    expect(wrapper.findAll('input')[0].element.value).toBe('另一条物料')
    wrapper.unmount()
  })
})
