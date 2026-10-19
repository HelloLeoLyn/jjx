vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
const http = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: http.request }))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import StructureDrawingUpload from '../StructureDrawingUpload.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { productWorkSpecApi } from '@/api/product/workSpec'

const auth = vi.hoisted(() => ({ release: true, edit: true }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    hasPermission: (permission: string) =>
      permission === 'product:edit'
        ? auth.edit
        : permission !== 'engineering:drawing:release' || auth.release,
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

const stubs = {
  ElButton: defineComponent({
    props: ['disabled', 'loading'],
    template: '<button :disabled="disabled || loading"><slot /></button>',
  }),
}
function mountUpload() {
  return mount(StructureDrawingUpload, {
    props: { productId: 2, productCode: 'P2', editable: true },
    global: { stubs },
  })
}
async function openWithFile(
  wrapper: ReturnType<typeof mountUpload>,
  name = '结构图.pdf',
  type = 'application/pdf'
) {
  await wrapper.get('[data-test="open"]').trigger('click')
  await flushPromises()
  const field = wrapper.get('[data-test="file"]')
  Object.defineProperty(field.element, 'files', {
    configurable: true,
    value: [new File(['drawing'], name, { type })],
  })
  await field.trigger('change')
  await flushPromises()
}
async function submit(wrapper: ReturnType<typeof mountUpload>) {
  await wrapper.get('[data-test="open"]').trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  auth.release = true
  auth.edit = true
  http.request.mockResolvedValue({ data: 225 })
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
  it('建议下一版本，串行上传、建产品引用并保存最新规范内容，不设现行或下发', async () => {
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(wrapper.find('el-dialog').exists()).toBe(false)
    expect(wrapper.find('[data-test="version"]').exists()).toBe(false)
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
    expect(wrapper.text()).toContain('结构图已保存')
    wrapper.unmount()
  })
  it('按钮直接打开系统文件选择器，取消选择不调用上传接口', async () => {
    const wrapper = mountUpload()
    const click = vi.spyOn(wrapper.get('[data-test="file"]').element as HTMLInputElement, 'click')
    await wrapper.get('[data-test="open"]').trigger('click')
    expect(click).toHaveBeenCalledTimes(1)
    await wrapper.get('[data-test="file"]').trigger('change')
    expect(attachmentRefApi.listRefs).not.toHaveBeenCalled()
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
    expect(wrapper.find('el-dialog').exists()).toBe(false)
    wrapper.unmount()
  })
  it('文件指纹可能重复只提示，不阻断自动上传', async () => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
      data: [
        {
          attachmentId: 224,
          documentType: 'STRUCTURE_DRAWING',
          drawingNo: '产品结构图',
          version: 'A',
          fileName: '结构图.pdf',
          fileSize: 7,
        },
      ],
    } as any)
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(wrapper.text()).toContain('可能重复')
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    expect(productWorkSpecApi.save).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('有编辑权限而无下发权限也能保存工作态，不设现行/下发/发布', async () => {
    auth.release = false
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(1)
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(attachmentApi.release).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('updated')).toHaveLength(1)
    wrapper.unmount()
  })
  it('建引用失败保留上传结果，重试同一附件', async () => {
    vi.mocked(attachmentRefApi.createRef).mockRejectedValueOnce(new Error('引用失败'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(wrapper.text()).toContain('产品引用未建立')
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    await submit(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
  it('规范保存失败不通知替换旧图，重试读取最新规范且不重复上传和建引用', async () => {
    vi.mocked(productWorkSpecApi.save).mockRejectedValueOnce(new Error('revision 冲突'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(wrapper.text()).toContain('规范保存失败')
    expect(wrapper.emitted('updated')).toBeUndefined()
    await submit(wrapper)
    expect(productWorkSpecApi.get).toHaveBeenCalledTimes(2)
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(attachmentApi.release).not.toHaveBeenCalled()
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it.each([
    ['Z', 'AA'],
    ['AA', 'AB'],
    ['无法解析', 'A'],
    ['ZZZZZZZZZZZZZZZZZZZZ', 'AAAAAAAAAAAAAAAAAAAAA'],
  ])('版本 %s 自动生成 %s', async (old, next) => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
      data: [
        {
          attachmentId: 224,
          category: '结构图',
          drawingNo: 'DWG-2',
          version: old,
        },
      ],
    } as any)
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledWith(
      expect.any(File),
      expect.any(String),
      undefined,
      expect.objectContaining({ version: next })
    )
    wrapper.unmount()
  })
  it('后端拒绝同版本重复建引用时显示错误，不伪报上传下发成功', async () => {
    vi.mocked(attachmentRefApi.createRef).mockRejectedValueOnce(
      new Error('该图纸版本已存在相同用途的文件')
    )
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(wrapper.text()).toContain('该图纸版本已存在相同用途的文件')
    expect(wrapper.text()).toContain('产品引用未建立')
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('使用最新结构图引用的历史编号，同编号所有版本参与递增', async () => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({
      data: [
        {
          id: 30,
          attachmentId: 230,
          documentType: 'STRUCTURE_DRAWING',
          drawingNo: '产品结构图',
          version: 'B',
          createTime: '2026-10-19 10:00:00',
        },
        {
          id: 29,
          attachmentId: 229,
          category: '结构图',
          drawingNo: '产品结构图',
          version: 'D',
          released: 0,
          createTime: '2026-10-18 10:00:00',
        },
        {
          id: 28,
          attachmentId: 224,
          category: '结构图',
          drawingNo: 'OLD',
          version: 'Z',
          isCurrent: 1,
          createTime: '2026-10-17 10:00:00',
        },
        { id: 31, attachmentId: 231, documentType: 'OTHER', drawingNo: 'OTHER', version: 'Z' },
      ],
    } as any)
    const wrapper = mountUpload()
    await wrapper.setProps({ structureFileId: 224 })
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledWith(
      expect.any(File),
      expect.any(String),
      undefined,
      expect.objectContaining({ drawingNo: '产品结构图', version: 'E' })
    )
    wrapper.unmount()
  })
  it.each([
    ['结构图.pdf', '', 'PRINT'],
    ['图.png', '', 'PRINT'],
    ['图.bin', 'image/png', 'PRINT'],
  ])('无历史、缺产品编码仍上传，%s 自动选用途', async (name, type, role) => {
    vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({ data: [] } as any)
    const wrapper = mountUpload()
    await wrapper.setProps({ productCode: '' })
    await openWithFile(wrapper, name, type)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledWith(
      expect.any(File),
      expect.any(String),
      undefined,
      { category: '结构图', drawingNo: '-ST', version: 'A', fileRole: role }
    )
    wrapper.unmount()
  })
  it('首次生成产品编码-ST，再传复用编号并升级到 B', async () => {
    vi.mocked(attachmentRefApi.listRefs)
      .mockResolvedValueOnce({ data: [] } as any)
      .mockResolvedValueOnce({
        data: [
          {
            id: 301,
            attachmentId: 225,
            documentType: 'STRUCTURE_DRAWING',
            drawingNo: 'P2-ST',
            version: 'A',
          },
        ],
      } as any)
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenNthCalledWith(
      1,
      expect.any(File),
      expect.any(String),
      undefined,
      expect.objectContaining({ drawingNo: 'P2-ST', version: 'A' })
    )
    expect(attachmentRefApi.tempUpload).toHaveBeenNthCalledWith(
      2,
      expect.any(File),
      expect.any(String),
      undefined,
      expect.objectContaining({ drawingNo: 'P2-ST', version: 'B' })
    )
    wrapper.unmount()
  })
  it('清单读取失败可重试，保留所选文件，不猜历史版本', async () => {
    vi.mocked(attachmentRefApi.listRefs).mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('读取失败')
    await submit(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('上传失败重试沿用请求键，禁用时不处理文件', async () => {
    vi.mocked(attachmentRefApi.tempUpload).mockRejectedValueOnce(new Error('网络失败'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await submit(wrapper)
    const calls = vi.mocked(attachmentRefApi.tempUpload).mock.calls
    expect(calls[0][1]).toBe(calls[1][1])
    expect(attachmentRefApi.createRef).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ disabled: true })
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
  it('只读或无编辑权限不能通过区域点击/文件事件上传，退出编辑不落库', async () => {
    const wrapper = mountUpload()
    await wrapper.setProps({ editable: false })
    expect(wrapper.get('[data-test="open"]').element.tagName).toBe('DIV')
    await openWithFile(wrapper)
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
    await wrapper.setProps({ editable: true })
    auth.edit = false
    await openWithFile(wrapper)
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('无 randomUUID 的内网环境也产生合法请求键，同文件重试沿用，新文件换键', async () => {
    const uuid = vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('' as any)
    try {
      vi.mocked(attachmentRefApi.tempUpload).mockRejectedValueOnce(new Error('network'))
      const wrapper = mountUpload()
      await openWithFile(wrapper)
      await submit(wrapper)
      const calls = vi.mocked(attachmentRefApi.tempUpload).mock.calls
      expect(calls[0][1]).toMatch(/^[A-Za-z0-9_-]{16,100}$/)
      expect(calls[0][1]).toBe(calls[1][1])
      await openWithFile(wrapper, 'second.png', 'image/png')
      expect(vi.mocked(attachmentRefApi.tempUpload).mock.calls[2][1]).not.toBe(calls[0][1])
      wrapper.unmount()
    } finally {
      uuid.mockRestore()
    }
  })
  it('真实附件API将 requestKey 编入 multipart FormData，重试使用相同值', async () => {
    const actual = await vi.importActual<typeof import('@/api/system/attachmentRef')>(
      '@/api/system/attachmentRef'
    )
    vi.mocked(attachmentRefApi.tempUpload).mockImplementation(actual.attachmentRefApi.tempUpload)
    http.request.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await submit(wrapper)
    const a = http.request.mock.calls[0][0],
      b = http.request.mock.calls[1][0]
    expect(a.url).toBe('/system/attachment/refs/temp')
    expect(a.data).toBeInstanceOf(FormData)
    expect(a.data.get('requestKey')).toMatch(/^[A-Za-z0-9_-]{16,100}$/)
    expect(a.data.get('requestKey')).toBe(b.data.get('requestKey'))
    expect(a.data.get('file')).toBeInstanceOf(File)
    wrapper.unmount()
  })
  it('不支持的结构图类型在上传前提示，不创建引用', async () => {
    const wrapper = mountUpload()
    await openWithFile(wrapper, 'drawing.dwg', '')
    expect(wrapper.text()).toContain('可预览的图片或 PDF')
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('上传中切换产品，旧请求不再建引用或覆盖新产品规范', async () => {
    let resolve!: (r: any) => void
    vi.mocked(attachmentRefApi.tempUpload).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r
        })
    )
    const wrapper = mountUpload()
    await openWithFile(wrapper)
    await wrapper.setProps({ productId: 3, productCode: 'P3' })
    resolve({ data: 225 })
    await flushPromises()
    expect(attachmentRefApi.createRef).not.toHaveBeenCalled()
    expect(productWorkSpecApi.save).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')).toBeUndefined()
    wrapper.unmount()
  })
})
