vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import Panel from '../ProductWorkSpecPanel.vue'
import ColorPanel from '../ProductColorCheckPanel.vue'
import Drawer from '@/components/product/ProductSpecDrawer.vue'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { loadDocset, renderFile } from '../docset'

const auth = vi.hoisted(() => ({
  userId: 7,
  perms: ['product:edit', 'engineering:bom:edit', 'engineering:routing:edit'],
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    userId: auth.userId,
    permissions: ['*'],
    roles: [],
    hasPermission: (permission: string) => auth.perms.includes(permission),
  }),
}))
vi.mock('@/api/product', () => ({
  getFullProduct: vi.fn().mockResolvedValue({
    data: { product: { productId: 2, productCode: 'P2', productName: '产品' } },
  }),
}))
vi.mock('@/components/product/customer-doc/CustomerDocPanel.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/product/ProductBasicInfo.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/ProductDocPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/ProductSpecHeader.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/engineering/drawing/components/EngineeringDrawingLibrary.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('../ProductWorkSpecVersionPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../InkMixingForm.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ProductSpecPreview.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../StructureDrawingUpload.vue', () => ({
  default: {
    props: ['editable'],
    template: '<div class="structure-stub" :data-editable="String(!!editable)" />',
  },
}))
vi.mock('../WorkSpecWorkflow.vue', () => ({
  default: {
    props: ['editable'],
    template: '<div class="route-stub" :data-editable="String(!!editable)" />',
  },
}))
vi.mock('../ProductPrintSpecPanel.vue', () => ({
  default: {
    props: ['readOnly'],
    template: '<div class="print-stub" :data-readonly="String(!!readOnly)" />',
  },
}))
vi.mock('@/views/product/bom/components/BomFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/product/route/components/RouteFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/api/system/attachmentRef', () => ({
  attachmentRefApi: {
    documentTypes: vi.fn(),
    listRefs: vi.fn(),
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
    listVersions: vi.fn(),
    publishVersion: vi.fn(),
  },
}))
vi.mock('../docset', async (original) => ({
  ...(await original<typeof import('../docset')>()),
  loadDocset: vi.fn(),
  renderFile: vi.fn(),
}))

let spec: ReturnType<typeof normalizeWorkSpec>
const wrappers: any[] = []

beforeEach(() => {
  vi.clearAllMocks()
  auth.userId = 7
  auth.perms = ['product:edit', 'engineering:bom:edit', 'engineering:routing:edit']
  localStorage.clear()
  vi.stubGlobal('crypto', {})
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  spec = normalizeWorkSpec({
    revision: 'r1',
    draftManaged: true,
    preparedById: 7,
    preparedByName: '工程甲',
  })
  vi.mocked(loadDocset).mockImplementation(async () => ({
    product: {},
    workSpec: { ...spec },
    files: [],
    bom: { bomId: 9, items: [] },
    routing: { routingId: 11, items: [] },
    warnings: [],
  }))
  vi.mocked(renderFile).mockResolvedValue([])
  vi.mocked(productWorkSpecApi.get).mockImplementation(async () => ({ data: { ...spec } }) as any)
  vi.mocked(productWorkSpecApi.listVersions).mockResolvedValue({ data: [] } as any)
  vi.mocked(attachmentRefApi.documentTypes).mockResolvedValue({ data: [] } as any)
  vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({ data: [] } as any)
})

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
})

const stubs = {
  ElDialog: { template: '<div><slot /></div>' },
  StructureDrawingUpload: {
    props: ['editable'],
    template: '<div class="structure-stub" :data-editable="String(!!editable)" />',
  },
  WorkSpecWorkflow: {
    props: ['editable'],
    template: '<div class="route-stub" :data-editable="String(!!editable)" />',
  },
  ProductPrintSpecPanel: {
    props: ['readOnly'],
    template: '<div class="print-stub" :data-readonly="String(!!readOnly)" />',
  },
  BomFormDialog: { template: '<div />' },
  RouteFormDialog: { template: '<div />' },
}

async function mountPanel(viewOnly: boolean) {
  const wrapper = mount(Panel, {
    props: { productId: 2, productCode: 'P2', productName: '产品', viewOnly },
    global: { directives: { loading: () => {} }, stubs },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}

const editButtons = (wrapper: any) =>
  wrapper.findAll('button').filter((button: any) => button.text() === '编辑')

describe('作业规范只读模式覆盖正文外编辑入口', () => {
  it.each([
    ['只读查看', true, 7, undefined],
    ['非负责人', false, 9, undefined],
    ['已定版', false, 7, 3],
  ])(
    '%s时不出现 BOM/路线/刀模/发行/结构图/印刷备注编辑入口',
    async (_, viewOnly, owner, finalized) => {
      spec = normalizeWorkSpec({
        revision: 'r1',
        draftManaged: true,
        preparedById: owner,
        preparedByName: owner === 7 ? '工程甲' : '工程乙',
        finalizedVersionId: finalized,
        dieLocationText: '旧刀模位置',
      })
      const wrapper = await mountPanel(viewOnly)
      expect(editButtons(wrapper)).toHaveLength(0)
      expect(wrapper.find('.structure-stub').attributes('data-editable')).toBe('false')
      expect(wrapper.find('.route-stub').attributes('data-editable')).toBe('false')
      expect(wrapper.find('.print-stub').attributes('data-readonly')).toBe('true')
      expect(wrapper.text()).not.toContain('迁移旧文本')
    }
  )

  it('继续制定（可编辑态）时各入口恢复，且只读标记关闭', async () => {
    const wrapper = await mountPanel(false)
    // 结构图、项目材料、备注、变更内容 · 发行单位等现有编辑入口
    expect(editButtons(wrapper)).toHaveLength(5)
    expect(wrapper.find('.route-stub').attributes('data-editable')).toBe('true')
    expect(wrapper.find('.print-stub').attributes('data-readonly')).toBe('false')
    expect(wrapper.find('.structure-stub').attributes('data-editable')).toBe('false')
    await wrapper.find('.spec-top-col .section-heading button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.structure-stub').attributes('data-editable')).toBe('true')
  })
})

describe('项目材料独立编辑', () => {
  it('支持增删排序、params编辑恢复自动，保存后重新打开保留数据且不修改BOM', async () => {
    const bom = [
      {
        materialId: 1,
        materialCode: 'M1',
        materialName: '面材',
        processName: '面板',
        quantity: 1,
        unit: 'PCS',
        specification: '100mm',
      },
      {
        materialId: 2,
        materialCode: 'M2',
        materialName: '胶',
        processName: '面板',
        quantity: 2,
        unit: 'PCS',
        specification: '200mm',
      },
    ]
    vi.mocked(attachmentRefApi.documentTypes).mockResolvedValue({
      data: [
        {
          dict_code: 'attachment_category',
          item_key: 'ENGINEERING_DOC',
          label: '工程技术资料',
          is_active: 1,
        },
      ],
    } as any)
    const errors = vi.spyOn(ElMessage, 'error')
    const before = JSON.stringify(bom)
    spec.projectMaterials = normalizeWorkSpec({
      projectMaterials: bom.map((row, i) => ({
        ...row,
        params: '',
        sortOrder: i,
        dieLocation: '',
      })),
    }).projectMaterials
    vi.mocked(loadDocset).mockImplementation(async () => ({
      product: {},
      workSpec: normalizeWorkSpec(spec),
      files: [],
      bom: { items: bom },
      routing: { items: [] },
      warnings: [],
    }))
    vi.mocked(productWorkSpecApi.saveDraft).mockImplementation(async (_id, input) => {
      spec = normalizeWorkSpec({ ...spec, ...input.spec, revision: 'r2' })
      return { data: normalizeWorkSpec(spec) } as any
    })
    const wrapper = await mountPanel(false)
    const section = () =>
      wrapper.findAll('.data-section').find((node) => node.find('h3').text() === '项目材料')!
    const action = (text: string) =>
      section()
        .findAll(
          ['恢复规格及模数', '删除', '下移'].includes(text)
            ? '.el-table__body-wrapper button'
            : '.section-heading button'
        )
        .find((node) => node.text() === text)!
    await action('编辑').trigger('click')
    await flushPromises()
    await section().findAll('.el-table__body-wrapper tbody textarea')[0].setValue('人工规格\n2模')
    await action('恢复规格及模数').trigger('click')
    await flushPromises()
    expect(
      (
        section().findAll('.el-table__body-wrapper tbody textarea')[0]
          .element as HTMLTextAreaElement
      ).value
    ).toBe('')
    await section().findAll('.el-table__body-wrapper tbody textarea')[0].setValue('人工规格\n2模')
    await action('新增').trigger('click')
    await flushPromises()
    expect(section().findAll('.el-table__body-wrapper tbody textarea')).toHaveLength(3)
    await action('删除').trigger('click')
    await flushPromises()
    expect(section().findAll('.el-table__body-wrapper tbody textarea')).toHaveLength(2)
    await action('下移').trigger('click')
    await flushPromises()
    await section().findAll('.el-table__body-wrapper tbody textarea')[0].setValue('胶参数')
    await action('保存').trigger('click')
    await flushPromises()
    expect(errors.mock.calls).toEqual([])
    errors.mockRestore()
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalled()
    expect(
      spec.projectMaterials?.flatMap((group) => group.materials.map((row) => row.materialCode))
    ).toEqual(['M1', 'M2'])
    expect(spec.projectMaterials?.[0].materials[0].params).toBe('胶参数')
    expect(JSON.stringify(bom)).toBe(before)
    await action('编辑').trigger('click')
    await flushPromises()
    expect(
      (
        section().findAll('.el-table__body-wrapper tbody textarea')[0]
          .element as HTMLTextAreaElement
      ).value
    ).toBe('胶参数')
  })
})

describe('分色检查表只读', () => {
  async function mountColor(readOnly: boolean) {
    const wrapper = mount(ColorPanel, {
      props: { productId: 2, readOnly },
      global: {
        stubs: {
          ProductSpecPreview: { template: '<div />' },
          ElDialog: { template: '<div><slot /></div>' },
        },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    return wrapper
  }

  it('只读时不出现填写检查表入口', async () => {
    const wrapper = await mountColor(true)
    expect(wrapper.text()).not.toContain('填写检查表')
  })

  it('可编辑时保留填写检查表入口', async () => {
    const wrapper = await mountColor(false)
    expect(wrapper.text()).toContain('填写检查表')
  })

  it('抽屉只读/非负责人时向分色面板透传只读，制定负责人可编辑时关闭只读', async () => {
    const colorStub = {
      props: ['readOnly'],
      template: '<div class="color-stub" :data-readonly="String(!!readOnly)" />',
    }
    const wrapper = mount(Drawer, {
      global: {
        directives: { loading: () => {}, hasPermi: () => {} },
        stubs: {
          ElDrawer: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElTable: { template: '<div />' },
          ElDialog: { template: '<div />' },
          ProductColorCheckPanel: colorStub,
          EngineeringDrawingLibrary: true,
        },
      },
    })
    wrappers.push(wrapper)
    await (wrapper.vm as any).open(2, undefined, { readOnly: true })
    const state = (wrapper.vm as any).$.setupState
    state.activeTab = 'color'
    await flushPromises()
    expect(wrapper.find('.color-stub').attributes('data-readonly')).toBe('true')

    await (wrapper.vm as any).open(2)
    state.activeTab = 'color'
    await flushPromises()
    expect(state.specViewOnly).toBe(false)
    expect(state.draftSession.canWrite.value).toBe(true)
    expect(wrapper.find('.color-stub').attributes('data-readonly')).toBe('false')

    spec = normalizeWorkSpec({ revision: 'r2', draftManaged: true, preparedById: 9 })
    await state.draftSession.load()
    state.activeTab = 'color'
    await flushPromises()
    expect(wrapper.find('.color-stub').attributes('data-readonly')).toBe('true')
  })
})
