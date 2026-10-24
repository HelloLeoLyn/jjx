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
  hasRegenerationChanges,
  parseOriginalName,
  resolveFinalName,
} from '../materialRegenerate'

const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  dictionaries: vi.fn(),
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

describe('迁移期【原名称】解析', () => {
  it('取【原名称】与【原规格】之间的文本', () => {
    expect(parseOriginalName('【原名称】PC膜旧名【原规格】0.38mm 幼砂')).toBe('PC膜旧名')
  })

  it('忽略前面的原供应商等前缀标记', () => {
    expect(parseOriginalName('【原物料ID】12【原供应商】3M【原名称】PET膜125【原规格】雾面')).toBe(
      'PET膜125'
    )
  })

  it('遇到【重生成】留痕时截断', () => {
    expect(parseOriginalName('【原名称】胶带5604（3M）\n【重生成】2026-10-23 18:45 大黄')).toBe(
      '胶带5604（3M）'
    )
  })

  it('无标记返回空串', () => {
    expect(parseOriginalName('普通备注')).toBe('')
    expect(parseOriginalName('')).toBe('')
    expect(parseOriginalName(undefined)).toBe('')
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
    const unavailable = buildRegeneration(current, { ...generated, specGenerated: false }, [])
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
      },
    })
    mocks.dictionaries.mockResolvedValue({ data: { material_meta: [{ code: 'PC', label: 'PC' }] } })
    mocks.updateNaming.mockResolvedValue({ data: {} })
  })

  it('展示差异并保留人工名称，只有勾选才替换且提示替换人工名称', async () => {
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('当前名称')
    expect(wrapper.text()).toContain('生成名称')
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
      reason: expect.stringContaining('名称：材料 + 型号 + 颜色（品牌）'),
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

  it('规格只读，按同一模型预览厚度和尺寸，规格变化可单独保存', async () => {
    mocks.detail.mockResolvedValue({ data: {
      material: { materialId: 7, materialName: '人工名称', specification: '旧规格', thickness: '0.175', widthSize: '300×305' },
      attributes: {},
      configuration: { schema: { fields: [{ key: 'thickness_mm', label: '厚度', type: 'decimal', unit: 'mm' }] } },
    } })
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('厚度:0.175mm；尺寸:300×305mm')
    expect(wrapper.findAll('input:not([type=checkbox])')).toHaveLength(1)
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mocks.updateNaming).toHaveBeenCalledWith(7, {
      materialName: '人工名称', reason: expect.any(String),
    })
    wrapper.unmount()
  })

  it('空模板保留历史规格，不能误报规格变化', async () => {
    mocks.detail.mockResolvedValue({ data: {
      material: current, attributes: {}, configuration: { schema: { fields: [] } },
    } })
    const wrapper = createDialog()
    await flushPromises()
    expect(wrapper.text()).toContain('未生成')
    expect(wrapper.text()).toContain('旧规格')
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
})
