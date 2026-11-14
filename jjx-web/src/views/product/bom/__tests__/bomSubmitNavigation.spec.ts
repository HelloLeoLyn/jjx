vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
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
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BomPage from '../index.vue'
import { BomStatusEnum } from '@/enums/product'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn(),
  del: vi.fn(),
  checkEngineeringBom: vi.fn(),
  submitEngineeringBom: vi.fn(),
  approveEngineeringBom: vi.fn(),
  confirm: vi.fn(),
  alert: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: { get: mocks.get, put: mocks.put, post: mocks.post, delete: mocks.del },
}))
vi.mock('@/api/product/bom', () => ({
  productBomApi: {
    checkEngineeringBom: mocks.checkEngineeringBom,
    submitEngineeringBom: mocks.submitEngineeringBom,
    approveEngineeringBom: mocks.approveEngineeringBom,
    getEngineeringBomInfo: vi.fn(),
    copyEngineeringBom: vi.fn(),
    removeEngineeringBom: vi.fn(),
    exportEngineeringBom: vi.fn(),
  },
}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: () => true }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: mocks.success, error: mocks.error, warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm, alert: mocks.alert },
}))

vi.mock('@/components/product/VersionMaintenanceDialog.vue', () => ({
  default: {
    name: 'VersionMaintenanceDialog',
    props: [
      'modelValue',
      'title',
      'domain',
      'rows',
      'loading',
      'busy',
      'supportsValidity',
      'supportsReviewAudit',
      'fetchDetail',
    ],
    emits: ['update:modelValue', 'action'],
    template: '<div class="version-dialog-stub" :data-title="title" :data-rows="rows.length" />',
  },
}))
vi.mock('@/views/product/bom/components/BomSubmitCheckDialog.vue', () => ({
  default: {
    name: 'BomSubmitCheckDialog',
    props: ['modelValue', 'issues', 'targetName'],
    emits: ['update:modelValue', 'navigate'],
    template: '<div class="submit-check-stub" />',
  },
}))
vi.mock('@/views/product/bom/components/BomFormDialog.vue', () => ({
  default: { name: 'BomFormDialog', props: ['modelValue', 'bomId'], emits: ['success'], template: '<div />' },
}))
vi.mock('@/views/product/bom/components/BomDetail.vue', () => ({
  default: { name: 'BomDetail', props: ['modelValue', 'bomId'], template: '<div />' },
}))
vi.mock('@/views/product/bom/components/BomApproveDialog.vue', () => ({
  default: { name: 'BomApproveDialog', props: ['modelValue', 'bomId'], emits: ['success'], template: '<div />' },
}))
vi.mock('@/components/TraceTimeline/index.vue', () => ({
  default: { name: 'TraceTimeline', props: ['modelValue', 'bizType', 'bizId'], template: '<div />' },
}))
vi.mock('@/views/product/route/components/RouteCopyDialog.vue', () => ({
  default: { name: 'RouteCopyDialog', props: ['modelValue'], emits: ['confirm'], template: '<div />' },
}))

const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide('rows', () => props.data)
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  setup(_, { slots }) {
    const rows = inject<() => any[]>('rows')!
    return () => h('div', rows().map((row, $index) => slots.default?.({ row, $index })))
  },
})
const SelectStub = defineComponent({
  props: ['modelValue', 'disabled', 'clearable'],
  emits: ['update:modelValue'],
  template: '<select><slot /></select>',
})
const OptionStub = defineComponent({
  props: ['value', 'label'],
  template: '<option :value="value">{{ label }}</option>',
})
const PaginationStub = defineComponent({
  props: ['total', 'page', 'limit'],
  emits: ['update:page', 'update:limit', 'pagination'],
  template: '<div />',
})

const productRow = {
  outputType: 'PRODUCT',
  outputId: 2,
  productCode: 'YL1025PWOO',
  productName: '离型纸组件',
  specification: '',
  currentId: 5,
  currentVersion: 'V1.0',
  currentEffectiveDate: '',
  latestId: 6,
  latestVersion: 'V1.1',
  versionCount: 2,
  hasDraft: false,
  hasPending: false,
  updateTime: '2026-11-14 09:00:00',
}
const mainVersions = [
  {
    bomId: 5,
    bomCode: 'YL1025PWOO-BOM',
    bomName: 'YL1025PWOO-BOM',
    bomVersion: 'V1.0',
    approveStatus: BomStatusEnum.DRAFT.value,
    isCurrent: false,
    canSetCurrent: true,
  },
]
const schemeVersions = [
  {
    bomId: 4,
    bomCode: 'AUX000348-MFG',
    bomName: '离型纸 RP-W195-L80制造方案',
    bomVersion: 'V1.0',
    approveStatus: BomStatusEnum.DRAFT.value,
    isCurrent: false,
    canSetCurrent: false,
  },
]
const threeSameIssues = [
  { itemId: 9, materialCode: 'RP-W195-L80', materialName: '离型纸 RP-W195-L80', field: 'subBomId', message: 'SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=4）' },
  { field: 'subBomId', message: 'SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=4）' },
  { field: 'schemeChoices', message: 'SUB_BOM_NOT_APPROVED：产品主方案未批准（bomId=4）' },
]

let wrapper: ReturnType<typeof mount> | undefined
const setup = async () => {
  mocks.get.mockImplementation(async (url: string, config?: any) => {
    if (url === '/engineering/bom/product-page') return { data: { records: [productRow], total: 1 } }
    if (url === '/engineering/bom/object-versions') {
      return { data: config?.params?.outputId === 352 ? schemeVersions : mainVersions }
    }
    return { data: null }
  })
  wrapper = mount(BomPage, {
    global: {
      directives: { hasPermi: { mounted: () => {} }, loading: () => {} },
      stubs: {
        ElTable: Table,
        ElTableColumn: Column,
        ElSelect: SelectStub,
        ElOption: OptionStub,
        Pagination: PaginationStub,
      },
    },
  })
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.confirm.mockResolvedValue('confirm')
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const openMainVersions = async () => {
  const button = wrapper!.findAll('button').find((node) => node.text() === '版本维护')!
  await button.trigger('click')
  await flushPromises()
}
const versionDialogs = () =>
  wrapper!.findAllComponents({ name: 'VersionMaintenanceDialog' })
const activeVersionDialog = () =>
  versionDialogs().find(
    (dialog) => dialog.props('modelValue') === true && dialog.props('title') !== '离型纸 RP-W195-L80制造方案 · V1.0'
  )!
const schemeVersionDialog = () =>
  versionDialogs().find((dialog) => dialog.props('title') === '离型纸 RP-W195-L80制造方案 · V1.0')!

describe('BOM 页面：提交预检弹窗与制造方案版本维护接线', () => {
  it('提交被拦截时打开可读弹窗（不拼接错误码），查看方案定位到目标产出对象版本维护', async () => {
    mocks.checkEngineeringBom.mockResolvedValue({ data: threeSameIssues })
    await setup()
    await openMainVersions()

    await activeVersionDialog().vm.$emit('action', 'submit', {
      id: 5,
      version: 'V1.0',
      approveStatus: BomStatusEnum.DRAFT.value,
      isCurrent: false,
      canSetCurrent: true,
    })
    await flushPromises()

    expect(mocks.checkEngineeringBom).toHaveBeenCalledWith(5)
    expect(mocks.alert).not.toHaveBeenCalled()
    const checkDialog = wrapper!.findComponent({ name: 'BomSubmitCheckDialog' })
    expect(checkDialog.props('modelValue')).toBe(true)
    expect(checkDialog.props('issues')).toHaveLength(3)
    expect(checkDialog.props('targetName')).toBe('V1.0')

    await checkDialog.vm.$emit('navigate', {
      bomId: 4,
      outputType: 'MATERIAL',
      outputId: 352,
      name: '离型纸 RP-W195-L80制造方案',
      version: 'V1.0',
    })
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/engineering/bom/object-versions', {
      params: { outputType: 'MATERIAL', outputId: 352 },
    })
    const active = schemeVersionDialog()
    expect(active.props('title')).toBe('离型纸 RP-W195-L80制造方案 · V1.0')
    expect(active.props('domain')).toBe('bom')
    expect(active.props('rows')).toHaveLength(1)
    expect(active.props('rows')[0].id).toBe(4)
    expect(active.props('rows')[0].version).toBe('V1.0')

    expect(mocks.submitEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.approveEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.put).not.toHaveBeenCalled()
  })

  it('查看/关闭弹窗不会提交或批准父 BOM；再次提交仍要先预检', async () => {
    mocks.checkEngineeringBom.mockResolvedValue({ data: threeSameIssues })
    mocks.confirm.mockRejectedValue('cancel')
    await setup()
    await openMainVersions()

    await activeVersionDialog().vm.$emit('action', 'submit', {
      id: 5,
      version: 'V1.0',
      approveStatus: BomStatusEnum.DRAFT.value,
      isCurrent: false,
      canSetCurrent: true,
    })
    await flushPromises()

    wrapper!.findComponent({ name: 'BomSubmitCheckDialog' }).vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(mocks.submitEngineeringBom).not.toHaveBeenCalled()

    mocks.checkEngineeringBom.mockResolvedValue({ data: [] })
    await activeVersionDialog().vm.$emit('action', 'submit', {
      id: 5,
      version: 'V1.0',
      approveStatus: BomStatusEnum.DRAFT.value,
      isCurrent: false,
      canSetCurrent: true,
    })
    await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith('提交 V1.0 审核？', '提交审核')
    expect(mocks.submitEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.checkEngineeringBom).toHaveBeenCalledTimes(2)
  })

  it('无阻断时保持原确认提交逻辑', async () => {
    mocks.checkEngineeringBom.mockResolvedValue({ data: [] })
    await setup()
    await openMainVersions()

    await activeVersionDialog().vm.$emit('action', 'submit', {
      id: 5,
      version: 'V1.0',
      approveStatus: BomStatusEnum.DRAFT.value,
      isCurrent: false,
      canSetCurrent: true,
    })
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith('提交 V1.0 审核？', '提交审核')
    expect(mocks.submitEngineeringBom).toHaveBeenCalledWith(5)
    expect(wrapper!.findComponent({ name: 'BomSubmitCheckDialog' }).props('modelValue')).toBe(false)
  })
})
