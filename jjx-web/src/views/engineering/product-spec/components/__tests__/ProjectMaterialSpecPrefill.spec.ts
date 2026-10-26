import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 仓库 vitest 未把 element-plus 加入 server.deps.inline，样式副作用入口会走 Node 加载器
// 报 Unknown file extension ".css"；这里只 mock 样式入口，组件仍真实挂载（同其它既有用例）。
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-group/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/progress/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/popper/style/css', () => ({}))
vi.mock('element-plus/es/components/overlay/style/css', () => ({}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    userId: 7,
    permissions: ['*'],
    roles: [],
    hasPermission: () => true,
  }),
}))
vi.mock('@/api/product', () => ({ getFullProduct: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/api/system/attachmentRef', () => ({
  attachmentRefApi: {
    documentTypes: vi.fn().mockResolvedValue({ data: [] }),
    listRefs: vi.fn().mockResolvedValue({ data: [] }),
    tempUpload: vi.fn(),
    createRef: vi.fn(),
  },
}))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: { list: vi.fn().mockResolvedValue({ data: [] }) },
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: {
    get: vi.fn(),
    getVersion: vi.fn(),
    saveDraft: vi.fn(),
    listVersions: vi.fn().mockResolvedValue({ data: [] }),
    publishVersion: vi.fn(),
  },
}))
vi.mock('../docset', async (original) => ({
  ...(await original<typeof import('../docset')>()),
  loadDocset: vi.fn(),
  renderFile: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/api/product/standardProcess', () => ({
  loadCommonStandardProcesses: vi.fn().mockResolvedValue([]),
  searchStandardProcesses: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/components/Selector/StandardProcessSelector.vue', () => ({
  default: { template: '<div class="process-stub" />' },
}))
vi.mock('../StructureDrawingUpload.vue', () => ({
  default: { template: '<div class="structure-stub" />' },
}))
vi.mock('../WorkSpecWorkflow.vue', () => ({
  default: { template: '<div class="route-stub" />' },
}))
vi.mock('../ProductPrintSpecPanel.vue', () => ({
  default: { template: '<div class="print-stub" />' },
}))
vi.mock('../ProductSpecPreview.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/product/bom/components/BomFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/product/route/components/RouteFormDialog.vue', () => ({
  default: { template: '<div />' },
}))

import Panel from '../ProductWorkSpecPanel.vue'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { loadDocset, workSpecMaterialSpec } from '../docset'

const bomRow = {
  materialId: 1,
  materialCode: '01.JB.CAS206B65N10',
  materialName: '01.JB.CAS206B65N10',
  processName: '组装',
  quantity: 1,
  unit: 'PCS',
  baseQty: 1,
  moduleQty: 1,
  specification: '0.6T 020 蓝灯',
}

async function mountEditing() {
  let spec = normalizeWorkSpec({
    revision: 'r1',
    draftManaged: true,
    preparedById: 7,
    preparedByName: '工程甲',
    projectMaterials: [{ ...bomRow, params: '', sortOrder: 0, dieLocation: '' }],
  })
  vi.mocked(productWorkSpecApi.get).mockImplementation(async () => ({ data: { ...spec } }) as any)
  vi.mocked(loadDocset).mockImplementation(async () => ({
    product: {},
    workSpec: normalizeWorkSpec(spec),
    files: [],
    bom: { bomId: 9, items: [bomRow] },
    routing: { routingId: 11, items: [] },
    warnings: [],
  }))
  const wrapper = mount(Panel, {
    props: { productId: 2, productCode: 'P2', productName: '产品', viewOnly: false },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElDialog: { template: '<div><slot /></div>' },
        StructureDrawingUpload: { template: '<div class="structure-stub" />' },
        WorkSpecWorkflow: { template: '<div class="route-stub" />' },
        ProductPrintSpecPanel: { template: '<div class="print-stub" />' },
        BomFormDialog: { template: '<div />' },
        RouteFormDialog: { template: '<div />' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

const materialSection = (wrapper: any) =>
  wrapper.findAll('.data-section').find((node: any) => node.find('h3').text() === '项目材料')!

const specInput = (wrapper: any) =>
  materialSection(wrapper).find('.el-table__body-wrapper tbody tr').findAll('td')[3].find('input')

describe('项目材料规格及模数编辑预填（dev-20261026-008）', () => {
  it('点编辑后规格及模数值直接写在输入框里，不是只有灰色提示', async () => {
    const wrapper = await mountEditing()
    const expected = workSpecMaterialSpec({ ...bomRow, params: '' })
    expect(expected).toBeTruthy()
    await materialSection(wrapper)
      .findAll('.section-heading button')
      .find((node: any) => node.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    const input = specInput(wrapper)
    expect((input.element as HTMLInputElement).value).toBe(expected)
    expect((input.element as HTMLInputElement).value).not.toBe('')
    wrapper.unmount()
  })

  it('改动后按人工文本保存；清空回到自动值（等同恢复规格及模数）', async () => {
    const wrapper = await mountEditing()
    await materialSection(wrapper)
      .findAll('.section-heading button')
      .find((node: any) => node.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    await specInput(wrapper).setValue('人工规格 2模')
    expect((specInput(wrapper).element as HTMLInputElement).value).toBe('人工规格 2模')
    await specInput(wrapper).setValue('')
    expect((specInput(wrapper).element as HTMLInputElement).value).toBe(
      workSpecMaterialSpec({ ...bomRow, params: '' })
    )
    wrapper.unmount()
  })
})
