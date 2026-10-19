vi.mock('element-plus/es/components/base/style/css', () => ({}))
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import AttachmentUpload from '../AttachmentUpload.vue'
import AttachmentTempUploader from '../AttachmentTempUploader.vue'
import type { AttachmentSession, AttachmentOwnership } from '@/composables/useAttachmentSession'

vi.mock('element-plus/es/components/upload/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/progress/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { warning: vi.fn(), error: vi.fn() },
}))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach((w) => w.unmount()) })
function setup(options: { disabled?: boolean; custom?: boolean; inline?: boolean } = {}) {
  const uploadTemporary = vi.fn().mockResolvedValue({ attachmentId: 1, entry: { attachmentId: 1 } })
  const session = { canEdit: ref(true), uploadingCount: ref(0), pendingCount: ref(0), uploadTemporary } as unknown as AttachmentSession
  const ownership = { kind: 'product', productId: 7, productCode: 'AA001MEOO', ownershipKey: 'product:7' } as AttachmentOwnership
  const w = mount(AttachmentUpload, {
    props: { session, ownership, disabled: options.disabled, inline: options.inline },
    slots: options.custom ? { trigger: '<template #default="{ disabled }"><button class="row-upload" :disabled="disabled">上传</button></template>' } : {},
  })
  wrappers.push(w)
  return { w, uploadTemporary, ownership }
}
async function select(w: ReturnType<typeof mount>, file: File) {
  const input = w.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}
describe('调用方自定义上传入口', () => {
  it('行内模式不占提示区，点击仍选文件，进度在操作栏外展示', async () => {
    const { w, uploadTemporary } = setup({ custom: true, inline: true })
    expect(w.find('.el-upload__tip').exists()).toBe(false)
    const click = vi.spyOn(w.find('input[type=file]').element as HTMLInputElement, 'click')
    await w.find('.row-upload').trigger('click')
    expect(click).toHaveBeenCalledOnce()
    let finish!: (value: unknown) => void
    uploadTemporary.mockImplementationOnce((_file, _ownership, onProgress) => {
      onProgress(30)
      return new Promise((resolve) => { finish = resolve })
    })
    w.findComponent(AttachmentTempUploader).vm.$emit('files', [new File(['a'], 'a.pdf')])
    await flushPromises()
    const progress = document.body.querySelector('[role="status"]')
    expect(progress?.textContent).toContain('附件上传中')
    expect(w.element.contains(progress)).toBe(false)
    finish({ attachmentId: 1, entry: { attachmentId: 1 } })
    await flushPromises()
    expect(document.body.querySelector('[role="status"]')).toBeNull()
  })
  it('自定义按钮直接触发原生文件选择并沿现有通道上传', async () => {
    const { w, uploadTemporary, ownership } = setup({ custom: true })
    const inputClick = vi.spyOn(w.find('input[type=file]').element as HTMLInputElement, 'click')
    await w.find('.row-upload').trigger('click')
    expect(inputClick).toHaveBeenCalledOnce()
    expect(uploadTemporary).not.toHaveBeenCalled()
    await select(w, new File(['abc'], 'drawing.pdf'))
    expect(uploadTemporary).toHaveBeenCalledWith(expect.any(File), ownership, expect.any(Function))
    expect(w.emitted('uploaded')).toHaveLength(1)
  })
  it('默认入口兼容，格式不合法仍被原校验拒绝', async () => {
    const { w, uploadTemporary } = setup()
    expect(w.text()).toContain('上传附件')
    await select(w, new File(['abc'], 'bad.exe'))
    expect(uploadTemporary).not.toHaveBeenCalled()
    expect(w.emitted('error')?.[0]?.[0]).toEqual(expect.objectContaining({ action: 'validate' }))
  })
  it('禁用状态传给按钮并阻止上传', async () => {
    const { w, uploadTemporary } = setup({ custom: true, disabled: true })
    expect(w.find('.row-upload').attributes('disabled')).toBeDefined()
    w.findComponent(AttachmentTempUploader).vm.$emit('files', [new File(['a'], 'a.pdf')])
    await flushPromises()
    expect(uploadTemporary).not.toHaveBeenCalled()
  })
  it('一次选择的多个文件在异步上传中保持归属', async () => {
    const { w, uploadTemporary, ownership } = setup()
    let finish!: (value: unknown) => void
    uploadTemporary.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    w.findComponent(AttachmentTempUploader).vm.$emit('files', [new File(['a'], 'a.pdf'), new File(['b'], 'b.pdf')])
    await w.setProps({ ownership: { ...ownership, productId: 8, ownershipKey: 'product:8' } })
    finish({ attachmentId: 1, entry: { attachmentId: 1 } })
    await flushPromises()
    expect(uploadTemporary).toHaveBeenCalledTimes(2)
    expect(uploadTemporary.mock.calls[1][1]).toEqual(ownership)
  })
})
