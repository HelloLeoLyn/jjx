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
  setCurrent: vi.fn(), release: vi.fn(), updateDrawingMetadata: vi.fn(), getWorkSpec: vi.fn(), saveWorkSpec: vi.fn(),
  success: vi.fn(), error: vi.fn(), confirm: vi.fn(), download: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/api/product/workSpec', async original => ({
  ...await original<typeof import('@/api/product/workSpec')>(),
  productWorkSpecApi: { get: mocks.getWorkSpec, save: mocks.saveWorkSpec },
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
  mocks.setCurrent.mockImplementation(async (id: number) => {
    const target = data.find(file => file.attachmentId === id)!
    for (const file of data.filter(file => file.drawingNo === target.drawingNo)) {
      file.isCurrent = file.version === target.version ? 1 : 0
      if (file.version === target.version) file.released = 0
    }
    return { data: true }
  })
  const wrapper = mount(EngineeringDrawingLibrary, {
    props: { productId: 42, productCode: 'P42', productName: '测试产品', active: true },
    global: { stubs: { ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>' } } },
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
  mocks.getWorkSpec.mockResolvedValue({ data: { revision: 'latest', engineeringRequirements: '最新备注', structureFileId: 999 } })
  mocks.saveWorkSpec.mockImplementation(async (_, value) => ({ data: value }))
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
    expect(wrapper.findAll('button').some(button => button.text() === '撤回')).toBe(false)
    await wrapper.get('.el-table__expand-icon').trigger('click')
    await flushPromises()
    await clickText(wrapper, '撤回')
    expect(mocks.release).toHaveBeenCalledWith(1, false)
    await clickText(wrapper, '设为现行')
    expect(mocks.setCurrent).toHaveBeenCalledWith(8)
    state.view = 'history'
    await nextTick()
    expect(state.filteredRows[0].version).toBe('A')
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

  it('下发与设现行仅在版本时间线提供，无现行时允许设置选中版本', async () => {
    const { wrapper } = await setup([file(1, { isCurrent: 1 }), file(2, { version: 'B' })])
    expect(wrapper.findAll('button').some(button => button.text() === '下发')).toBe(false)
    await wrapper.get('.el-table__expand-icon').trigger('click'); await flushPromises()
    await clickText(wrapper, '下发')
    expect(mocks.release).toHaveBeenCalledWith(1, true)
    const pending = await setup([file(4), file(5, { version: 'B' })])
    expect(pending.wrapper.text()).toContain('待设现行')
    await pending.wrapper.get('.el-table__expand-icon').trigger('click'); await flushPromises()
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
  it('结构图现行切换优先同版可预览PRINT，保存最新revision和备注，非结构图不改规范', async () => {
    const { state } = await setup([file(1, { fileRole: 'ORIGINAL' }), file(2), file(3, { version: 'B', isCurrent: 1 })])
    await state.setCurrent(state.revisionRows.find((r: any) => r.version === 'A'))
    expect(mocks.setCurrent).toHaveBeenCalledWith(1)
    expect(mocks.saveWorkSpec).toHaveBeenCalledWith(42, expect.objectContaining({ revision: 'latest', engineeringRequirements: '最新备注', structureFileId: 2 }))
    expect(mocks.release).not.toHaveBeenCalled()
    mocks.saveWorkSpec.mockClear(); mocks.getWorkSpec.mockClear()
    const other = await setup([file(4, { documentType: 'PANEL_DRAWING', category: '面板图' })])
    await other.state.setCurrent(other.state.revisionRows[0])
    expect(mocks.saveWorkSpec).not.toHaveBeenCalled(); expect(mocks.getWorkSpec).not.toHaveBeenCalled()
  })
  it('规范revision冲突保留现行状态，版本行提供重试；只重试保存、不再次切换或下发', async () => {
    mocks.saveWorkSpec.mockRejectedValueOnce(new Error('规范已变化，请重新加载'))
    const { wrapper, state } = await setup([file(1), file(2, { version: 'B', isCurrent: 1 })])
    await wrapper.get('.el-table__expand-icon').trigger('click'); await flushPromises()
    await clickText(wrapper, '设为现行')
    expect(state.revisionRows.find((r: any) => r.version === 'A').current).toBe(true)
    expect(state.syncError).toContain('现行已切换')
    expect(mocks.success).not.toHaveBeenCalledWith('已设为现行，作业规范结构图已同步')
    mocks.getWorkSpec.mockResolvedValue({ data: { revision: 'newer', engineeringRequirements: '其他人修改', structureFileId: 999 } })
    await clickText(wrapper, '重试同步结构图')
    expect(mocks.setCurrent).toHaveBeenCalledTimes(1)
    expect(mocks.saveWorkSpec).toHaveBeenLastCalledWith(42, expect.objectContaining({ revision: 'newer', engineeringRequirements: '其他人修改', structureFileId: 1 }))
    expect(state.syncError).toBe(''); expect(mocks.release).not.toHaveBeenCalled()
  })
  it('没有PRINT时只取同版可预览文件；只有CAD时保留原结构图并提示补充', async () => {
    const { state } = await setup([file(1, { fileRole: 'ORIGINAL' })])
    await state.setCurrent(state.revisionRows[0]); expect(mocks.saveWorkSpec).toHaveBeenCalledWith(42, expect.objectContaining({ structureFileId: 1 }))
    mocks.saveWorkSpec.mockClear()
    const cad = await setup([file(2, { fileName: 'source.dwg', fileRole: 'ORIGINAL' })])
    await cad.state.setCurrent(cad.state.revisionRows[0])
    expect(mocks.saveWorkSpec).not.toHaveBeenCalled(); expect(cad.state.syncError).toContain('没有可预览的结构图')
  })
  it('行内新版本选PDF和CAD直接上传同一新版本，继承身份；取消选文件无写入', async () => {
    const { wrapper, state } = await setup([file(1, { version: 'Z', isCurrent: 1, category: '结构图' }), file(2, { version: 'Y', category: '结构图' })])
    const input = wrapper.get('[data-test="new-revision-file"]')
    const click = vi.spyOn(input.element as HTMLInputElement, 'click')
    await clickText(wrapper, '新版本'); expect(click).toHaveBeenCalledTimes(1); expect(state.uploadVisible).toBe(false)
    await input.trigger('change'); expect(mocks.tempUpload).not.toHaveBeenCalled()
    Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['pdf'], 'print.pdf', { type: 'application/pdf' }), new File(['cad'], 'source.dwg')] })
    await input.trigger('change'); await flushPromises()
    expect(mocks.tempUpload).toHaveBeenCalledTimes(2)
    for (const call of mocks.tempUpload.mock.calls) expect(call[3]).toMatchObject({ drawingNo: 'DWG-1', drawingName: '结构总图', version: 'AA', category: '结构图' })
    expect(mocks.tempUpload.mock.calls.map(c => c[3].fileRole)).toEqual(['PRINT', 'ORIGINAL'])
    expect(state.uploadVisible).toBe(false)
  })
  it('无法解析版本回退现有弹窗，仅确认版本；编号名称继承，不显示输入框', async () => {
    const { wrapper, state } = await setup([file(1, { version: 'V1.0' })])
    await clickText(wrapper, '新版本'); expect(state.uploadVisible).toBe(true); expect(state.defaults.version).toBe('')
    state.addFile({ uid: 90, name: 'print.pdf', raw: new File(['pdf'], 'print.pdf') })
    expect(state.queue[0]).toMatchObject({ drawingNo: 'DWG-1', drawingName: '结构总图', documentType: 'STRUCTURE_DRAWING' })
    await state.submitUpload(); expect(mocks.tempUpload).not.toHaveBeenCalled()
  })
  it('批量默认继承筛选，编号名称自动分配，类型可逐项修改，用途自动', async () => {
    const { wrapper, state } = await setup([]); state.categoryFilter = 'PANEL_DRAWING'; state.openUpload()
    state.addFile({ uid: 90, name: 'one.pdf', raw: new File(['pdf'], 'one.pdf') })
    state.addFile({ uid: 91, name: 'two.dwg', raw: new File(['cad'], 'two.dwg') })
    await flushPromises()
    const headers = wrapper.get('.upload-queue').text()
    expect(headers).not.toContain('图纸编号'); expect(headers).not.toContain('图纸名称'); expect(headers).not.toContain('用途')
    expect(state.queue[0]).toMatchObject({ documentType: 'PANEL_DRAWING', drawingNo: 'P42-PNL', drawingName: '测试产品面板图', fileRole: 'PRINT' })
    expect(state.queue[1]).toMatchObject({ drawingNo: 'P42-PNL-2', drawingName: '测试产品面板图-2', fileRole: 'ORIGINAL' })
    state.defaults.version = 'B'; state.applyDefaults()
    expect(state.queue.map((row: any) => row.drawingNo)).toEqual(['P42-PNL', 'P42-PNL-2'])
    state.queue[1].documentType = 'CIRCUIT_DRAWING'; state.regenerateIdentity(state.queue[1])
    expect(state.queue[1].drawingNo).toBe('P42-CIR')
    await state.submitUpload(); expect(mocks.createRef).toHaveBeenCalledWith(expect.objectContaining({ documentType: 'CIRCUIT_DRAWING' }))
  })
  it('未分类可上传后补录，历史编号名称不批量重写', async () => {
    const { state } = await setup([file(1, { drawingNo: '历史编号', drawingName: '历史名称' })]); state.openUpload()
    state.addFile({ uid: 90, name: 'unknown.pdf', raw: new File(['pdf'], 'unknown.pdf') })
    expect(state.queue[0].documentType).toBe(''); await state.submitUpload()
    expect(mocks.createRef).toHaveBeenCalledWith(expect.objectContaining({ documentType: null }))
    expect(mocks.updateDrawingMetadata).not.toHaveBeenCalled()
    expect(state.files[0]).toMatchObject({ drawingNo: '历史编号', drawingName: '历史名称' })
  })

  it('新图纸上传不写旧图种，不恢复类别输入', async () => {
    const { state } = await setup([]); state.categoryFilter = 'STRUCTURE_DRAWING'; state.openUpload()
    state.addFile({ uid: 90, name: 'new.pdf', raw: new File(['pdf'], 'new.pdf') })
    await state.submitUpload(); expect(mocks.tempUpload.mock.calls[0][3]).not.toHaveProperty('category')
  })

  it('现行接口返回false时不写作业规范，不提示同步成功', async () => {
    const { state } = await setup([file(1)])
    mocks.setCurrent.mockResolvedValueOnce({ data: false }); await state.setCurrent(state.revisionRows[0])
    expect(mocks.saveWorkSpec).not.toHaveBeenCalled(); expect(mocks.getWorkSpec).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith('现行版本切换失败')
  })
  it('重试前目标已被改为非现行时拒绝同步，不把旧图写回规范', async () => {
    const data = [file(1), file(2, { version: 'B', isCurrent: 1 })]
    const { state } = await setup(data)
    mocks.saveWorkSpec.mockRejectedValueOnce(new Error('冲突'))
    await state.setCurrent(state.revisionRows.find((r: any) => r.version === 'A'))
    data[0].isCurrent = 0; data[1].isCurrent = 1
    mocks.saveWorkSpec.mockClear(); await state.retryStructureSync()
    expect(mocks.saveWorkSpec).not.toHaveBeenCalled(); expect(state.syncError).toContain('目标版本已不是现行版本')
  })

})
