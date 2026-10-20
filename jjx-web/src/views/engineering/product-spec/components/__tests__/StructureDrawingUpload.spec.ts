import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import StructureDrawingUpload from '../StructureDrawingUpload.vue'
import { productWorkSpecApi } from '@/api/product/workSpec'
const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveDraft: vi.fn(),
  tempUpload: vi.fn(),
  listRefs: vi.fn(),
  documentTypes: vi.fn(),
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: api,
}))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: {} }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 7, permissions: ['*'], roles: [], hasPermission: () => true }),
}))
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.clearAllMocks()
  api.documentTypes.mockResolvedValue({
    data: [
      { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料' },
      {
        dict_code: 'attachment_document_type',
        item_key: 'STRUCTURE_DRAWING',
        label: '结构图',
        is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC' },
      },
    ],
  } as any)
  localStorage.clear()
  vi.stubGlobal('crypto', {})
  api.get.mockResolvedValue({
    data: {
      revision: 'r1',
      engineeringRequirements: '保留备注',
      draftFiles: [
        { refId: 100, attachmentId: 1, fileName: 'old.pdf', documentType: 'STRUCTURE_DRAWING' },
      ],
    },
  })
  api.tempUpload.mockResolvedValue({ data: 2 })
  api.saveDraft.mockImplementation(async (_id, body) => ({
    data: {
      ...body.spec,
      revision: 'r2',
      draftAttachments: body.attachments,
      draftFiles: body.attachments.map((f: any) => ({
        ...f,
        refId: f.attachmentId + 100,
        fileName: f.attachmentId + '.pdf',
      })),
    },
  }))
})
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  vi.unstubAllGlobals()
})
async function setup(editable = true) {
  const w = mount(StructureDrawingUpload, {
    props: { productId: 42, productCode: 'P42', editable },
  })
  wrappers.push(w)
  await flushPromises()
  return w
}
async function choose(w: ReturnType<typeof mount>) {
  const input = w.get('[data-test=file]')
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [new File(['pdf'], 'new.pdf', { type: 'application/pdf' })],
  })
  await input.trigger('change')
  await flushPromises()
}
describe('结构图统一草稿保存', () => {
  it('不填图号版本，不先建引用，正文和完整附件集一次保存', async () => {
    const w = await setup()
    await choose(w)
    expect(api.tempUpload).toHaveBeenCalledTimes(1)
    expect(api.tempUpload.mock.calls[0][3]).toBeNull()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        revision: 'r1',
        spec: expect.objectContaining({ structureFileId: 2, engineeringRequirements: '保留备注' }),
        attachments: [
          expect.objectContaining({ attachmentId: 1 }),
          expect.objectContaining({ attachmentId: 2, documentType: 'STRUCTURE_DRAWING' }),
        ],
      })
    )
    expect(w.emitted('updated')).toHaveLength(1)
  })
  it('保存失败保留已上传结果，点击重试只重试草稿保存', async () => {
    api.saveDraft.mockRejectedValueOnce(new Error('revision conflict'))
    const w = await setup()
    await choose(w)
    expect(w.get('[role=alert]').text()).toContain('草稿保存失败')
    expect(w.emitted('updated')).toBeUndefined()
    await w.get('[data-test=open]').trigger('click')
    await flushPromises()
    expect(api.tempUpload).toHaveBeenCalledTimes(1)
    expect(api.saveDraft).toHaveBeenCalledTimes(2)
    expect(w.emitted('updated')).toHaveLength(1)
  })
  it('未进入编辑或被禁用时无上传写入', async () => {
    const w = await setup(false)
    await choose(w)
    expect(api.tempUpload).not.toHaveBeenCalled()
    await w.setProps({ editable: true, disabled: true })
    await choose(w)
    expect(api.tempUpload).not.toHaveBeenCalled()
  })
})
