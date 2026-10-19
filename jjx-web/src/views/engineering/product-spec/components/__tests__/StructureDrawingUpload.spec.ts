vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('@/utils/request', () => ({ default: {} }))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import StructureDrawingUpload from '../StructureDrawingUpload.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { productWorkSpecApi } from '@/api/product/workSpec'

const auth = vi.hoisted(() => ({ release: true }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    hasPermission: (permission: string) =>
      permission !== 'engineering:drawing:release' || auth.release,
  }),
}))
vi.mock('@/api/system/attachmentRef', () => ({
  attachmentRefApi: { listRefs: vi.fn(), tempUpload: vi.fn(), createRef: vi.fn() },
}))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: { setCurrent: vi.fn(), release: vi.fn() },
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: { get: vi.fn(), save: vi.fn() },
}))

const slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const input = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue', 'input', 'change'],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'input\', $event.target.value)" @change="$emit(\'change\')" />',
})
const stubs = {
  ElDialog: defineComponent({
    props: ['modelValue'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
  }),
  ElButton: defineComponent({
    props: ['disabled', 'loading'],
    template: '<button :disabled="disabled || loading"><slot /></button>',
  }),
  ElAlert: defineComponent({ props: ['title'], template: '<div>{{ title }}</div>' }),
  ElInput: input,
  ElSelect: slot,
  ElOption: true,
  ElForm: slot,
  ElFormItem: slot,
}
function mountUpload() {
  return mount(StructureDrawingUpload, {
    props: { productId: 2, productCode: 'P2' },
    global: { stubs },
  })
}
async function openWithFile(wrapper: ReturnType<typeof mountUpload>) {
  await wrapper.get('[data-test="open"]').trigger('click')
  await flushPromises()
  const field = wrapper.get('[data-test="file"]')
  Object.defineProperty(field.element, 'files', {
    configurable: true,
    value: [new File(['drawing'], '结构图.pdf', { type: 'application/pdf' })],
  })
  await field.trigger('change')
}
async function submit(wrapper: ReturnType<typeof mountUpload>) {
  await wrapper.get('[data-test="submit"]').trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  auth.release = true
  vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
    data: [
      {
        attachmentId: 224,
        category: '结构图',
        documentType: 'STRUCTURE_DRAWING',
        drawingNo: 'DWG-2',
        version: 'A',
      },
    ],
  } as any)
  vi.mocked(attachmentRefApi.tempUpload).mockResolvedValue({ data: 225 } as any)
  vi.mocked(attachmentRefApi.createRef).mockResolvedValue({ data: 300 } as any)
  vi.mocked(attachmentApi.setCurrent).mockResolvedValue({ data: true } as any)
  vi.mocked(attachmentApi.release).mockResolvedValue({ data: true } as any)
  vi.mocked(productWorkSpecApi.get).mockResolvedValue({
    data: { revision: 'latest', engineeringRequirements: '保留备注', structureFileId: 224 },
  } as any)
  vi.mocked(productWorkSpecApi.save).mockResolvedValue({ data: {} } as any)
})

describe('作业规范结构图上传', () => {
  it('建议下一版本，串行上传、建同源引用、设现行、下发并保存最新规范内容', async () => {
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect((wrapper.get('[data-test="version"]').element as HTMLInputElement).value).toBe('B')
    await submit(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledWith(
      expect.any(File),
      expect.any(String),
      undefined,
      { category: '结构图', drawingNo: 'DWG-2', version: 'B', fileRole: 'PRINT' }
    )
    expect(attachmentRefApi.createRef).toHaveBeenCalledWith({
      attachmentId: 225,
      sourceType: 'product',
      sourceId: 2,
      productId: 2,
      productCode: 'P2',
      documentType: 'STRUCTURE_DRAWING',
    })
    const calls = [
      attachmentRefApi.tempUpload,
      attachmentRefApi.createRef,
      attachmentApi.setCurrent,
      attachmentApi.release,
      productWorkSpecApi.get,
      productWorkSpecApi.save,
    ]
    const order = calls.map((fn) => vi.mocked(fn).mock.invocationCallOrder[0])
    expect(order).toEqual([...order].sort((a, b) => a - b))
    expect(productWorkSpecApi.save).toHaveBeenCalledWith(
      2,
      expect.objectContaining({
        revision: 'latest',
        structureFileId: 225,
        engineeringRequirements: '保留备注',
      })
    )
    expect(wrapper.text()).toContain('作业规范结构图已更新')
    wrapper.unmount()
  })
  it('版本必填，可改；同编号同版本只提示可能重复，仍调用上传', async () => {
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await wrapper.get('[data-test="version"]').setValue('')
    expect(wrapper.get('[data-test="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-test="version"]').setValue('A')
    expect(wrapper.text()).toContain('可能重复')
    await submit(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('无下发权限只上传和建引用，提示未下发，不更改现行或规范引用', async () => {
    auth.release = false
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await submit(wrapper)
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(1)
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(attachmentApi.release).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('已上传，需有权限的人下发')
    expect(wrapper.text()).toContain('未下发')
    wrapper.unmount()
  })
  it.each(['current', 'release', 'false-result'])(
    '%s 失败显示未下发，重试不重复上传',
    async (failure) => {
      if (failure === 'current')
        vi.mocked(attachmentApi.setCurrent).mockRejectedValueOnce(new Error('现行失败'))
      else if (failure === 'release')
        vi.mocked(attachmentApi.release).mockRejectedValueOnce(new Error('下发失败'))
      else vi.mocked(attachmentApi.release).mockResolvedValueOnce({ data: false } as any)
      const wrapper = mountUpload()
      await openWithFile(wrapper)
      await submit(wrapper)
      expect(wrapper.text()).toContain('已上传，未下发，可重试')
      expect(productWorkSpecApi.save).not.toHaveBeenCalled()
      await submit(wrapper)
      expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
      expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(1)
      expect(attachmentApi.setCurrent).toHaveBeenCalledTimes(failure === 'current' ? 2 : 1)
      expect(wrapper.text()).toContain('作业规范结构图已更新')
      wrapper.unmount()
    }
  )
  it('建引用失败保留上传结果，重试同一附件', async () => {
    vi.mocked(attachmentRefApi.createRef).mockRejectedValueOnce(new Error('引用失败'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await submit(wrapper)
    expect(wrapper.text()).toContain('产品引用未建立，未下发')
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    await submit(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
  it('规范保存失败准确提示已下发，重试读取最新规范且不重复设现行或下发', async () => {
    vi.mocked(productWorkSpecApi.save).mockRejectedValueOnce(new Error('revision 冲突'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await submit(wrapper)
    expect(wrapper.text()).toContain('已下发，作业规范引用未更新')
    await submit(wrapper)
    expect(productWorkSpecApi.get).toHaveBeenCalledTimes(2)
    expect(attachmentApi.setCurrent).toHaveBeenCalledTimes(1)
    expect(attachmentApi.release).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('Z 之后建议 AA，手工版本值不会被编号变动覆盖', async () => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
      data: [{ attachmentId: 224, category: '结构图', drawingNo: 'DWG-2', version: 'Z' }],
    } as any)
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect((wrapper.get('[data-test="version"]').element as HTMLInputElement).value).toBe('AA')
    await wrapper.get('[data-test="version"]').setValue('CUSTOM')
    await wrapper.get('[data-test="drawing-no"]').setValue('OTHER')
    await wrapper.get('[data-test="drawing-no"]').trigger('change')
    expect((wrapper.get('[data-test="version"]').element as HTMLInputElement).value).toBe('CUSTOM')
    wrapper.unmount()
  })
  it('后端拒绝同版本重复建引用时显示错误，不伪报上传下发成功', async () => {
    vi.mocked(attachmentRefApi.createRef).mockRejectedValueOnce(
      new Error('该图纸版本已存在相同用途的文件')
    )
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await wrapper.get('[data-test="version"]').setValue('A')
    await submit(wrapper)
    expect(wrapper.text()).toContain('该图纸版本已存在相同用途的文件')
    expect(wrapper.text()).toContain('产品引用未建立，未下发')
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已有多张结构图时沿用作业规范当前引用的编号，再建议下一版本', async () => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
      data: [
        { attachmentId: 223, category: '结构图', drawingNo: 'OTHER', version: 'D' },
        {
          attachmentId: 224,
          documentType: 'STRUCTURE_DRAWING',
          drawingNo: 'SELECTED',
          version: 'B',
        },
      ],
    } as any)
    const wrapper = mountUpload()
    await wrapper.setProps({ structureFileId: 224 })
    await openWithFile(wrapper)
    expect((wrapper.get('[data-test="drawing-no"]').element as HTMLInputElement).value).toBe(
      'SELECTED'
    )
    expect((wrapper.get('[data-test="version"]').element as HTMLInputElement).value).toBe('C')
    wrapper.unmount()
  })
})
