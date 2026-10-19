vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/image/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/progress/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import EngineeringDrawingLibrary from '../EngineeringDrawingLibrary.vue'

const mocks = vi.hoisted(() => ({
  refsByProduct: vi.fn(), documentTypes: vi.fn(), tempUpload: vi.fn(), createRef: vi.fn(), updateMeta: vi.fn(), unbind: vi.fn(),
  setCurrent: vi.fn(), release: vi.fn(), updateDrawingMetadata: vi.fn(),
  success: vi.fn(), error: vi.fn(), confirm: vi.fn(), download: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ hasPermission: () => true }) }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: mocks }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: mocks }))
vi.mock('@/components/product/ProductFilePreview.vue', () => ({ default: { props: ['modelValue', 'file'], template: '<div />' } }))
vi.mock('@/components/product/productFilePreview', () => ({
  downloadFile: mocks.download,
  fileKind: (file: { fileName: string }) => file.fileName.endsWith('.pdf') ? 'pdf' : 'other',
}))
vi.mock('element-plus', async original => ({
  ...await original<typeof import('element-plus')>(),
  ElMessage: { success: mocks.success, error: mocks.error, warning: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm },
}))

function file(id: number, overrides: Record<string, unknown> = {}) {
  return { id: id + 100, attachmentId: id, sourceType: 'product', fileName: `${id}.pdf`, fileSize: 100,
    drawingNo: 'DWG-1', drawingName: '结构总图', version: 'A', documentType: 'STRUCTURE_DRAWING',
    fileRole: 'PRINT', isCurrent: 0, released: 0, createBy: `上传人${id}`, createTime: `2026-10-19T10:0${id}:00`, ...overrides }
}
const wrappers: ReturnType<typeof mount>[] = []
async function setup(data = [file(1), file(2, { version: 'B', isCurrent: 1, released: 1 }), file(3, { version: 'B', isCurrent: 1, released: 1, fileRole: 'ORIGINAL' })]) {
  mocks.refsByProduct.mockResolvedValue({ data })
  const wrapper = mount(EngineeringDrawingLibrary, {
    props: { productId: 42, productCode: 'P42', productName: '测试产品', active: true },
    global: { stubs: { ElDialog: { template: '<div />' } } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  // setup state 保留真实 computed 和处理函数；DOM 交互另核对入口及目标。
  const state = (wrapper.vm as any).$.setupState
  return { wrapper, state }
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.documentTypes.mockResolvedValue({ data: [] })
  mocks.confirm.mockResolvedValue('confirm')
  mocks.tempUpload.mockResolvedValue({ data: 9 })
  mocks.createRef.mockResolvedValue({ data: 109 })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.useRealTimers() })

async function clickText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find(button => button.text() === text)
  expect(button, `缺少操作入口：${text}`).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

describe('工程图集列表与筛选（dev-20261019-024）', () => {
  it('A/B 多文件仅一张图纸，外层选 B 现行，展开包含时间线和各版文件', async () => {
    const { wrapper, state } = await setup()
    expect(state.rows).toHaveLength(1)
    expect(state.rows[0]).toMatchObject({ version: 'B', current: true })
    expect(state.rows[0].totalFiles).toHaveLength(3)
    expect(state.rows[0].files).toHaveLength(2)
    await wrapper.get('.el-table__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.el-timeline-item')).toHaveLength(2)
    expect(wrapper.findAll('.revision-file')).toHaveLength(3)
    expect(wrapper.text()).toContain('上传人1')
    expect(wrapper.text()).toContain('2026-10-19 10:01')
    expect(wrapper.text()).toContain('A 版 · 1 个文件')
    expect(wrapper.text()).toContain('B 版 · 2 个文件')
  })

  it('新上传的历史版与改名不会改变现行目标，外层撤回 B，展开设现行 A', async () => {
    const { wrapper, state } = await setup([file(1, { version: 'B', isCurrent: 1, released: 1 }), file(8, { drawingName: '结构图新名称' })])
    expect(state.rows).toHaveLength(1)
    expect(state.rows[0].version).toBe('B')
    await clickText(wrapper, '撤回')
    expect(mocks.release).toHaveBeenCalledWith(1, false)
    await wrapper.get('.el-table__expand-icon').trigger('click')
    await flushPromises()
    await clickText(wrapper, '设为现行')
    expect(mocks.setCurrent).toHaveBeenCalledWith(8)
    state.view = 'history'
    await nextTick()
    expect(state.filteredRows[0].version).toBe('B')
  })

  it('缺打印件依据当前版，同编号不同版本仍合并；关键词和类型同步更新图纸计数', async () => {
    const { state } = await setup([
      file(1), file(2, { version: 'B', isCurrent: 1, fileRole: 'ORIGINAL' }),
      file(3, { drawingNo: 'DWG-2', drawingName: '包装图', documentType: 'PACKAGING_DRAWING' }),
      file(4, { drawingNo: 'DWG-3', documentType: null, fileRole: 'ORIGINAL' }),
    ])
    expect(state.rows).toHaveLength(3)
    expect(state.missingPrintCount).toBe(2)
    expect(state.unclassifiedCount).toBe(1)
    state.onlyMissingPrint = true
    await nextTick()
    expect(state.filteredRows.map((row: any) => row.drawingNo)).toEqual(['DWG-3', 'DWG-1'])
    state.categoryFilter = 'STRUCTURE_DRAWING'
    await nextTick()
    expect(state.missingPrintCount).toBe(1)
    expect(state.unclassifiedCount).toBe(0)
    state.keyword = '包装'
    await nextTick()
    expect(state.missingPrintCount).toBe(0)
    expect(state.documentTypeTabs.find((item: any) => item.value === 'PACKAGING_DRAWING').count).toBe(1)
    state.categoryFilter = 'PACKAGING_DRAWING'
    state.onlyMissingPrint = false
    await nextTick()
    expect(state.filteredRows).toHaveLength(1)
  })

  it('未分类开关、提示与补录入口可用；没有编号的旧文件不错误合并', async () => {
    const { wrapper, state } = await setup([file(1, { documentType: null }), file(2, { drawingNo: null, documentType: null }), file(3, { drawingNo: null })])
    expect(state.rows).toHaveLength(3)
    state.onlyUnclassified = true
    await nextTick()
    expect(state.filteredRows).toHaveLength(2)
    expect(wrapper.text()).toContain('补资料类型后才能进打印')
    await clickText(wrapper, '补资料类型')
    expect(state.metadataVisible).toBe(true)
    expect(state.metadataFile.id).toBe(2)
    state.metadata.documentType = 'STRUCTURE_DRAWING'
    state.metadata.drawingNo = '补录编号'
    await state.saveMetadata()
    expect(mocks.updateMeta).toHaveBeenCalledWith(102, { documentType: 'STRUCTURE_DRAWING' })
  })

  it('历史文件保留预览、下载、编辑、删除；现行与已下发文件不提供删除', async () => {
    const { wrapper, state } = await setup()
    await wrapper.get('.el-table__expand-icon').trigger('click')
    await flushPromises()
    const historical = wrapper.findAll('.revision-file').find(row => row.text().includes('1.pdf'))!
    await historical.get('.el-link').trigger('click')
    expect(state.previewFile.id).toBe(1)
    await clickText(historical as any, '下载')
    expect(mocks.download).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }))
    await clickText(historical as any, '编辑关联')
    expect(state.metadataFile.id).toBe(1)
    expect(wrapper.findAll('button').filter(button => button.text() === '删除')).toHaveLength(1)
    await clickText(historical as any, '删除')
    expect(mocks.unbind).toHaveBeenCalledWith(101)
  })

  it('外层下发作用现行版本，无现行时明确显示并允许设置最新版本', async () => {
    const { wrapper } = await setup([file(1, { isCurrent: 1 }), file(2, { version: 'B' })])
    await clickText(wrapper, '下发')
    expect(mocks.release).toHaveBeenCalledWith(1, true)
    const pending = await setup([file(4), file(5, { version: 'B' })])
    expect(pending.wrapper.text()).toContain('待设现行')
    await clickText(pending.wrapper, '设为现行')
    expect(mocks.setCurrent).toHaveBeenLastCalledWith(5)
  })

  it('空态按钮直接打开既有上传，成功后提示编号版本、高亮约2秒、清除隐藏筛选', async () => {
    vi.useFakeTimers()
    const { wrapper, state } = await setup([])
    await clickText(wrapper, '上传第一张结构图')
    expect(state.uploadVisible).toBe(true)
    state.addFile({ uid: 9, name: '新结构图.pdf', raw: new File(['pdf'], '新结构图.pdf', { type: 'application/pdf' }) })
    state.categoryFilter = 'PACKAGING_DRAWING'
    state.keyword = '无匹配'
    state.onlyMissingPrint = true
    mocks.refsByProduct.mockResolvedValue({ data: [file(9, { drawingNo: '新结构图', drawingName: '新结构图' })] })
    await state.submitUpload()
    await flushPromises()
    expect(mocks.success).toHaveBeenCalledWith('已上传：新结构图-A')
    expect(state.filteredRows).toHaveLength(1)
    expect(state.uploadVisible).toBe(false)
    expect(wrapper.find('.uploaded-drawing').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(2000)
    expect(wrapper.find('.uploaded-drawing').exists()).toBe(false)
    expect(mocks.createRef).toHaveBeenCalledWith(expect.objectContaining({ attachmentId: 9, documentType: 'STRUCTURE_DRAWING' }))
  })

  it('初次加载不误报上传；上传失败不提示成功；加载失败提供重试', async () => {
    const { wrapper, state } = await setup()
    expect(mocks.success).not.toHaveBeenCalled()
    state.openUpload()
    state.addFile({ uid: 9, name: '失败.pdf', raw: new File(['pdf'], '失败.pdf', { type: 'application/pdf' }) })
    mocks.tempUpload.mockRejectedValue(new Error('上传失败'))
    await state.submitUpload()
    expect(mocks.success).not.toHaveBeenCalled()
    expect(state.queue[0].status).toBe('failed')
    mocks.refsByProduct.mockRejectedValueOnce(new Error('网络异常'))
    await state.loadFiles()
    await flushPromises()
    expect(wrapper.text()).toContain('图纸加载失败')
    await clickText(wrapper, '重试')
    expect(state.error).toBe('')
    expect(mocks.success).not.toHaveBeenCalled()
  })
})
