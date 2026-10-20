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
import Panel from '@/views/engineering/product-spec/components/ProductWorkSpecPanel.vue'
import ColorPanel from '@/views/engineering/product-spec/components/ProductColorCheckPanel.vue'
import Library from '@/views/engineering/drawing/components/EngineeringDrawingLibrary.vue'
import Drawer from '@/components/product/ProductSpecDrawer.vue'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { loadDocset, renderFile } from '@/views/engineering/product-spec/components/docset'

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
vi.mock('@/views/engineering/product-spec/components/ProductWorkSpecVersionPanel.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/engineering/product-spec/components/InkMixingForm.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/engineering/product-spec/components/ProductSpecPreview.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/engineering/product-spec/components/StructureDrawingUpload.vue', () => ({
  default: {
    props: ['editable'],
    template: '<div class="structure-stub" :data-editable="String(!!editable)" />',
  },
}))
vi.mock('@/views/engineering/product-spec/components/WorkSpecWorkflow.vue', () => ({
  default: {
    props: ['editable'],
    template: '<div class="route-stub" :data-editable="String(!!editable)" />',
  },
}))
vi.mock('@/views/engineering/product-spec/components/ProductPrintSpecPanel.vue', () => ({
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
vi.mock('@/components/attachment/AttachmentManager.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/attachment/AttachmentUpload.vue', () => ({
  default: {
    props: ['disabled'],
    template: '<button class="upload-stub" :disabled="disabled">上传</button>',
  },
}))
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
vi.mock('@/views/engineering/product-spec/components/docset', async (original) => ({
  ...(await original<typeof import('@/views/engineering/product-spec/components/docset')>()),
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

describe('抽屉只读/继续制定完整回归', () => {
  it('同一正文从只读切为继续制定，恢复正文、BOM、路线、结构图编辑', async () => {
    const wrapper = await mountPanel(true)
    expect(editButtons(wrapper)).toHaveLength(0)
    expect(wrapper.find('.route-stub').attributes('data-editable')).toBe('false')
    expect(wrapper.find('.print-stub').attributes('data-readonly')).toBe('true')
    await wrapper.setProps({ viewOnly: false })
    expect(editButtons(wrapper)).toHaveLength(5)
    expect(wrapper.find('.route-stub').attributes('data-editable')).toBe('true')
    expect(wrapper.find('.print-stub').attributes('data-readonly')).toBe('false')
    await wrapper.find('.spec-top-col .section-heading button').trigger('click')
    expect(wrapper.find('.structure-stub').attributes('data-editable')).toBe('true')
  })
  it('同一分色面板从只读切为继续制定恢复填写', async () => {
    const wrapper = mount(ColorPanel, {
      props: { productId: 2, readOnly: true },
      global: { stubs: { ElDialog: { template: '<div />' } } },
    })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.text()).not.toContain('填写检查表')
    await wrapper.setProps({ readOnly: false })
    expect(wrapper.text()).toContain('填写检查表')
  })
  it('抽屉只读标记向正文/图集/分色透传，重新打开继续制定后恢复', async () => {
    const wrapper = mount(Drawer, {
      global: {
        directives: { loading: () => {}, hasPermi: () => {} },
        stubs: {
          ElDrawer: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElDialog: { template: '<div />' },
          ElTable: true,
          ProductWorkSpecPanel: {
            props: ['viewOnly'],
            template: '<div class="body-probe" :data-readonly="String(viewOnly)" />',
          },
          EngineeringDrawingLibrary: {
            props: ['viewOnly'],
            template: '<div class="atlas-probe" :data-readonly="String(viewOnly)" />',
          },
          ProductColorCheckPanel: {
            props: ['readOnly'],
            template: '<div class="color-probe" :data-readonly="String(readOnly)" />',
          },
        },
      },
    })
    wrappers.push(wrapper)
    for (const readOnly of [true, false, true]) {
      await (wrapper.vm as any).open(2, undefined, { readOnly })
      for (const [tab, probe] of [
        ['spec', 'body'],
        ['atlas', 'atlas'],
        ['color', 'color'],
      ]) {
        ;(wrapper.vm as any).$.setupState.activeTab = tab
        await flushPromises()
        expect(wrapper.find('.' + probe + '-probe').attributes('data-readonly')).toBe(
          String(readOnly)
        )
      }
    }
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
  })
  it('图集只读禁用上传/排序/打印勾选，继续制定恢复且可排序、勾选', async () => {
    spec = normalizeWorkSpec({
      revision: 'r1',
      draftManaged: true,
      preparedById: 7,
      draftAttachments: [1, 2].map((attachmentId) => ({
        attachmentId,
        documentType: 'STRUCTURE_DRAWING',
        print: true,
      })),
      draftFiles: [1, 2].map((attachmentId, sortOrder) => ({
        attachmentId,
        documentType: 'STRUCTURE_DRAWING',
        fileName: attachmentId + '.pdf',
        sortOrder,
      })),
    })
    vi.mocked(attachmentRefApi.documentTypes).mockResolvedValue({
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
    const mountLibrary = (viewOnly: boolean) =>
      mount(Library, {
        props: {
          productId: 2,
          productCode: 'P2',
          productName: '产品',
          viewOnly,
          showVersions: false,
          active: true,
        },
        global: { directives: { loading: () => {} }, stubs: { ElDialog: { template: '<div />' } } },
      })
    let wrapper = mountLibrary(true)
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('.upload-stub').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('上移')
    expect(wrapper.text()).not.toContain('下移')
    const checks = () => wrapper.findAll('input[type="checkbox"]')
    expect(checks()).toHaveLength(2)
    expect(checks().every((c) => c.attributes('disabled') !== undefined)).toBe(true)
    // Drawer.open clears detail before reloading, remounting the atlas for continue.
    wrapper.unmount()
    wrapper = mountLibrary(false)
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('.upload-stub').exists()).toBe(true)
    expect(wrapper.find('.upload-stub').attributes('disabled')).toBeDefined()
    const state = (wrapper.vm as any).$.setupState
    state.uploadType = 'STRUCTURE_DRAWING'
    await flushPromises()
    expect(wrapper.find('.upload-stub').attributes('disabled')).toBeUndefined()
    expect(checks().every((c) => c.attributes('disabled') === undefined)).toBe(true)
    expect(wrapper.text()).toContain('上移')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '下移')!
      .trigger('click')
    expect(state.draft.files.value.map((f: any) => f.attachmentId)).toEqual([2, 1])
    await checks()[0].setValue(false)
    expect(state.draft.prints[2]).toBe(false)
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
  })
})
