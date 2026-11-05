import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { useUserStore } from '@/store/modules/user'
import { Permissions } from '@/enums/permissions'
import MaterialMasterImportDialog from '../MaterialMasterImportDialog.vue'

const mocks = vi.hoisted(() => ({
  categories: vi.fn(), importExcel: vi.fn(), getItems: vi.fn(), addItem: vi.fn(),
  info: vi.fn(), success: vi.fn(), error: vi.fn(),
}))
vi.mock('@/api/auth', () => ({ authApi: {} }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: mocks }))
vi.mock('@/api/inventory/classify', () => ({ materialClassifyApi: { applyRules: vi.fn() } }))
vi.mock('@/api/system/dict', () => ({ dictApi: mocks }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { info: mocks.info, success: mocks.success, error: mocks.error },
}))

const Slot = defineComponent({ template: '<div><slot /></div>' })
const Dialog = defineComponent({
  props: ['modelValue'],
  template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
})
const FormItem = defineComponent({
  props: ['label'],
  template: '<label :data-label="label"><span>{{ label }}</span><slot /></label>',
})
const Input = defineComponent({
  props: ['modelValue', 'readonly', 'disabled', 'maxlength'],
  emits: ['update:modelValue'],
  template: '<input :value="modelValue" :readonly="readonly" :disabled="disabled" :maxlength="maxlength" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
const Button = defineComponent({
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
})
const Select = defineComponent({
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<select :value="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
})
const Option = defineComponent({
  props: ['value', 'label'],
  template: '<option :value="value">{{ label }}</option>',
})
const Alert = defineComponent({ props: ['title'], template: '<p>{{ title }}</p>' })
let wrapper: VueWrapper
const button = (text: string) => wrapper.findAll('button').find(item => item.text() === text)!
const input = (label: string) => wrapper.get(`[data-label="${label}"] input`)
const errorMessage = (value: string, dict: string) => `第2行：字典值不存在或名称不唯一：${value}（${dict}）`
const result = (errors: string[] = []) => ({ data: { errors, successCount: errors.length ? 0 : 1 } })

async function preview(errors: string[]) {
  mocks.importExcel.mockResolvedValue(result(errors))
  wrapper = mount(MaterialMasterImportDialog, {
    props: { modelValue: false },
    global: { stubs: {
      ElDialog: Dialog, ElForm: Slot, ElFormItem: FormItem, ElSelect: Select,
      ElOption: Option, ElInput: Input, ElButton: Button, ElAlert: Alert, ElSwitch: true,
    } },
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  await wrapper.get('select').setValue('7')
  const file = new File(['example'], '物料.xlsx')
  const fileInput = wrapper.get('input[type="file"]')
  Object.defineProperty(fileInput.element, 'files', { value: [file] })
  await fileInput.trigger('change')
  await button('预检').trigger('click')
  await flushPromises()
  return file
}

describe('物料导入预览就地补字典', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
    useUserStore().permissions = [Permissions.SYSTEM_DICT_ADD]
    mocks.categories.mockResolvedValue({ data: [{ categoryId: 7, selectable: true, pathName: '原材料/辅料' }] })
    mocks.getItems.mockResolvedValue({ data: [] })
    mocks.addItem.mockResolvedValue({ code: 200 })
  })
  afterEach(() => wrapper?.unmount())

  it('CHL 可填名称成浩林，按最大排序号新增，沿用文件和分类只重预览一次', async () => {
    const file = await preview([errorMessage('CHL', 'brand')])
    mocks.getItems.mockResolvedValue({ data: [
      { itemKey: 'master_OTHER', itemValue: 'OTHER', sortOrder: 18 },
      { itemKey: 'master_A', itemValue: 'A', sortOrder: 3 },
    ] })
    await button('补进字典').trigger('click')
    expect((input('字典').element as HTMLInputElement).value).toBe('品牌')
    expect((input('字典').element as HTMLInputElement).readOnly).toBe(true)
    expect((input('值').element as HTMLInputElement).value).toBe('CHL')
    expect((input('值').element as HTMLInputElement).readOnly).toBe(true)
    expect((input('名称').element as HTMLInputElement).value).toBe('CHL')
    await input('名称').setValue('成浩林')
    mocks.importExcel.mockResolvedValue(result())
    await button('确认').trigger('click')
    await flushPromises()
    expect(mocks.getItems).toHaveBeenCalledWith('brand')
    expect(mocks.addItem).toHaveBeenCalledExactlyOnceWith({
      dictCode: 'brand', itemKey: 'master_CHL', itemValue: 'CHL', label: '成浩林', sortOrder: 19, isActive: 1,
    })
    expect(mocks.importExcel).toHaveBeenCalledTimes(2)
    expect(mocks.importExcel).toHaveBeenLastCalledWith(file, 7, true, false)
    expect(wrapper.findAll('.import-error')).toHaveLength(0)
    expect(wrapper.text()).toContain('预检通过：1条')
    expect(button('导入物料').attributes('disabled')).toBeUndefined()
  })

  it.each([['本色', 'color', '颜色'], ['薄膜', 'form', '形态'], ['pcs', 'unit', '单位']])(
    '%s 识别为 %s，名称默认等于值，键转为大写', async (value, dict, label) => {
      await preview([errorMessage(value, dict)])
      await button('补进字典').trigger('click')
      expect((input('字典').element as HTMLInputElement).value).toBe(label)
      expect((input('名称').element as HTMLInputElement).value).toBe(value)
      mocks.importExcel.mockResolvedValue(result())
      await button('确认').trigger('click')
      await flushPromises()
      expect(mocks.addItem).toHaveBeenCalledWith(expect.objectContaining({
        dictCode: dict, itemKey: `master_${value.toUpperCase()}`, itemValue: value, label: value, sortOrder: 1,
      }))
      expect(mocks.importExcel).toHaveBeenCalledTimes(2)
    }
  )

  it('同值已经存在时提示已存在，不再插入、不报红错并重新预览', async () => {
    await preview([errorMessage('CHL', 'brand')])
    mocks.getItems.mockResolvedValue({ data: [{ itemKey: 'legacy_CHL', itemValue: 'CHL' }] })
    await button('补进字典').trigger('click')
    mocks.importExcel.mockResolvedValue(result())
    await button('确认').trigger('click')
    await flushPromises()
    expect(mocks.info).toHaveBeenCalledExactlyOnceWith('已存在')
    expect(mocks.error).not.toHaveBeenCalled()
    expect(mocks.addItem).not.toHaveBeenCalled()
    expect(mocks.importExcel).toHaveBeenCalledTimes(2)
  })

  it('连续补同值只新增一次，第二次确认提示已存在', async () => {
    await preview([errorMessage('CHL', 'brand')])
    await button('补进字典').trigger('click')
    await button('确认').trigger('click')
    await flushPromises()
    mocks.getItems.mockResolvedValue({ data: [{ itemKey: 'master_CHL', itemValue: 'CHL' }] })
    await button('补进字典').trigger('click')
    await button('确认').trigger('click')
    await flushPromises()
    expect(mocks.addItem).toHaveBeenCalledTimes(1)
    expect(mocks.info).toHaveBeenCalledWith('已存在')
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('错误由名称不唯一引起时提示已存在，不再增加同名项', async () => {
    await preview([errorMessage('本色', 'color')])
    mocks.getItems.mockResolvedValue({ data: [
      { itemKey: 'master_NATURAL', itemValue: 'NATURAL', label: '本色' },
      { itemKey: 'master_N', itemValue: 'N', label: '本色' },
    ] })
    await button('补进字典').trigger('click')
    await button('确认').trigger('click')
    await flushPromises()
    expect(mocks.info).toHaveBeenCalledWith('已存在')
    expect(mocks.addItem).not.toHaveBeenCalled()
    expect(wrapper.find('.import-error').text()).toContain(errorMessage('本色', 'color'))
  })

  it('只有菜单权限而无 system:dict:add 权限时不显示按钮', async () => {
    useUserStore().permissions = ['system:dict:list', 'system:dict:query']
    await preview([errorMessage('CHL', 'brand')])
    expect(button('补进字典')).toBeUndefined()
    expect(mocks.getItems).not.toHaveBeenCalled()
    expect(mocks.addItem).not.toHaveBeenCalled()
  })

  it('普通错误、缺少编码、其它字典及对象原型名均无按钮，逐行保留原文', async () => {
    const errors = [
      '第2行：物料编码重复', '第3行：字典值不存在或名称不唯一：CHL',
      errorMessage('PC', 'material_meta'), errorMessage('X', 'toString'),
      errorMessage(' ', 'brand'), errorMessage('CHL', 'brand'),
    ]
    await preview(errors)
    const rows = wrapper.findAll('.import-error')
    expect(rows).toHaveLength(errors.length)
    rows.forEach((row, index) => {
      expect(row.text()).toContain(errors[index])
      expect(row.find('button').exists()).toBe(index === errors.length - 1)
    })
  })

  it('取消弹窗不新增、不重新预览，空名称不能确认', async () => {
    await preview([errorMessage('CHL', 'brand')])
    await button('补进字典').trigger('click')
    await input('名称').setValue('  ')
    expect(button('确认').attributes('disabled')).toBeDefined()
    await button('取消').trigger('click')
    expect(mocks.addItem).not.toHaveBeenCalled()
    expect(mocks.importExcel).toHaveBeenCalledTimes(1)
  })

  it('连续点击确认时只发起一次新增；请求失败保留弹窗和原预览', async () => {
    let rejectAdd!: (error: Error) => void
    mocks.addItem.mockImplementation(() => new Promise((_, reject) => { rejectAdd = reject }))
    await preview([errorMessage('CHL', 'brand')])
    await button('补进字典').trigger('click')
    await button('确认').trigger('click')
    await flushPromises()
    await button('确认').trigger('click')
    expect(mocks.addItem).toHaveBeenCalledTimes(1)
    rejectAdd(new Error('网络不可用'))
    await flushPromises()
    expect(wrapper.text()).toContain('网络不可用')
    expect(button('确认')).toBeDefined()
    expect(mocks.importExcel).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.import-error').exists()).toBe(true)
  })

  it('字典读取失败不插入；关闭导入后迟到的读取不触发新增或预览', async () => {
    await preview([errorMessage('CHL', 'brand')])
    await button('补进字典').trigger('click')
    mocks.getItems.mockRejectedValueOnce(new Error('读取失败'))
    await button('确认').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('读取失败')
    expect(mocks.addItem).not.toHaveBeenCalled()
    let resolveItems!: (value: { data: [] }) => void
    mocks.getItems.mockImplementationOnce(() => new Promise(resolve => { resolveItems = resolve }))
    await button('确认').trigger('click')
    await wrapper.setProps({ modelValue: false })
    resolveItems({ data: [] })
    await flushPromises()
    expect(mocks.addItem).not.toHaveBeenCalled()
    expect(mocks.importExcel).toHaveBeenCalledTimes(1)
  })

  it('弹窗打开后撤销新增权限会隐藏行按钮且不能确认', async () => {
    await preview([errorMessage('CHL', 'brand')])
    await button('补进字典').trigger('click')
    useUserStore().permissions = []
    await flushPromises()
    expect(button('补进字典')).toBeUndefined()
    expect(button('确认').attributes('disabled')).toBeDefined()
    expect(mocks.addItem).not.toHaveBeenCalled()
  })
})
