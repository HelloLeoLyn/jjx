import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BomPage from '../index.vue'
import { BomStatusEnum } from '@/enums/product'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  detail: vi.fn(),
  reloadScope: vi.fn(),
  checkEngineeringBom: vi.fn(),
  submitEngineeringBom: vi.fn(),
  approveEngineeringBom: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { get: mocks.get, put: mocks.put } }))
vi.mock('@/api/product/bom', () => ({
  productBomApi: {
    getEngineeringBomInfo: mocks.detail,
    checkEngineeringBom: mocks.checkEngineeringBom,
    submitEngineeringBom: mocks.submitEngineeringBom,
    approveEngineeringBom: mocks.approveEngineeringBom,
  },
}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: () => true }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))
const versionStub = {
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
  template: '<div />',
}
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
    template: '<div />',
  },
}))
vi.mock('@/views/product/bom/components/BomReviewScope.vue', () => ({
  default: {
    name: 'BomReviewScope',
    props: ['modelValue', 'action', 'rootBomIds'],
    emits: ['update:modelValue', 'done', 'close', 'open-bom'],
    setup(_props: unknown, { expose }: any) {
      expose({ loadPlan: mocks.reloadScope })
    },
    template: '<div />',
  },
}))
vi.mock('@/views/product/bom/components/BomFormDialog.vue', () => ({
  default: {
    name: 'BomFormDialog',
    props: ['modelValue', 'bomId'],
    emits: ['update:modelValue', 'success'],
    template: '<div />',
  },
}))
vi.mock('@/views/product/bom/components/BomDetail.vue', () => ({
  default: { name: 'BomDetail', props: ['modelValue', 'bomId'], template: '<div />' },
}))
vi.mock('@/views/product/bom/components/BomApproveDialog.vue', () => ({
  default: {
    name: 'BomApproveDialog',
    props: { modelValue: Boolean, bomId: Number, itemsOnly: Boolean },
    emits: ['update:modelValue', 'success'],
    template: '<div />',
  },
}))
vi.mock('@/components/TraceTimeline/index.vue', () => ({
  default: {
    name: 'TraceTimeline',
    props: ['modelValue', 'bizType', 'bizId'],
    template: '<div />',
  },
}))
vi.mock('@/views/product/route/components/RouteCopyDialog.vue', () => ({
  default: { name: 'RouteCopyDialog', props: ['modelValue'], template: '<div />' },
}))
const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide('rows', () => props.data)
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  props: ['label'],
  setup(_, { slots }) {
    const rows = inject<() => any[]>('rows')!
    return () =>
      h(
        'div',
        rows().map((row, $index) => slots.default?.({ row, $index }))
      )
  },
})
const Slot = defineComponent({ template: '<div><slot /></div>' })
const product = {
  outputType: 'PRODUCT',
  outputId: 2,
  productCode: 'YL1026MEOO',
  productName: 'YL1026MEOO',
  latestId: 7,
  latestVersion: 'V1.1',
  versionCount: 2,
}
const versions = [
  { bomId: 7, bomVersion: 'V1.1', approveStatus: BomStatusEnum.DRAFT.value },
  { bomId: 6, bomVersion: 'V1.0', approveStatus: BomStatusEnum.REVIEWING.value },
]
let wrapper: ReturnType<typeof mount> | undefined
const setup = async (outputType = 'PRODUCT') => {
  mocks.get.mockImplementation(async (url: string) =>
    url.endsWith('product-page')
      ? { data: { records: [{ ...product, outputType }], total: 1 } }
      : { data: versions }
  )
  wrapper = mount(BomPage, {
    global: {
      directives: { hasPermi: () => {}, loading: () => {} },
      stubs: {
        ElTable: Table,
        ElTableColumn: Column,
        ElSelect: Slot,
        ElOption: true,
        Pagination: true,
      },
    },
  })
  await flushPromises()
  await wrapper
    .findAll('button')
    .find((button) => button.text() === '版本维护')!
    .trigger('click')
  await flushPromises()
  return {
    version: wrapper.findComponent({ name: versionStub.name }),
    scope: wrapper.findComponent({ name: 'BomReviewScope' }),
  }
}
beforeEach(() => {
  vi.clearAllMocks()
})
afterEach(() => {
  wrapper?.unmount()
})

describe('BOM版本维护整套操作接线', () => {
  it.each(['PRODUCT', 'MATERIAL'])(
    '%s 列表仅版本维护和流水，所选历史版本进入整套提交，返回保留上下文',
    async (outputType) => {
      const { version, scope } = await setup(outputType)
      const labels = wrapper!.findAll('button').map((button) => button.text())
      expect(labels).toContain('流水')
      expect(labels).not.toContain('查看整套')
      expect(labels).not.toContain('送审')
      expect(labels).not.toContain('审批')
      await version.vm.$emit('action', 'submit', version.props('rows')[1])
      await flushPromises()
      expect(scope.props('rootBomIds')).toEqual([6])
      expect(scope.props('action')).toBe('SUBMIT')
      expect(scope.props('modelValue')).toBe(true)
      expect(version.props('modelValue')).toBe(false)
      expect(mocks.checkEngineeringBom).not.toHaveBeenCalled()
      expect(mocks.submitEngineeringBom).not.toHaveBeenCalled()
      await scope.vm.$emit('update:modelValue', false)
      await scope.vm.$emit('close')
      await flushPromises()
      expect(version.props('modelValue')).toBe(true)
      expect(version.props('rows')).toHaveLength(2)
      expect(version.props('title')).toBe('YL1026MEOO')
    }
  )
  it('审核进入整套范围；辅助处理只有明细能力，关闭后刷新整套预检', async () => {
    const { version, scope } = await setup()
    await version.vm.$emit('action', 'approve', version.props('rows')[1])
    await flushPromises()
    expect(scope.props('rootBomIds')).toEqual([6])
    expect(scope.props('action')).toBe('APPROVE')
    expect(wrapper!.findComponent({ name: 'BomApproveDialog' }).props('modelValue')).toBe(false)
    mocks.detail.mockResolvedValue({ data: { approveStatus: BomStatusEnum.REVIEWING.value } })
    await scope.vm.$emit('open-bom', 5, true)
    await flushPromises()
    const helper = wrapper!.findComponent({ name: 'BomApproveDialog' })
    expect(helper.props('modelValue')).toBe(true)
    expect(helper.props('bomId')).toBe(5)
    expect(helper.props('itemsOnly')).toBe(true)
    await helper.vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(mocks.reloadScope).toHaveBeenCalledTimes(1)
    expect(mocks.approveEngineeringBom).not.toHaveBeenCalled()
  })
  it('草稿问题定位到确切下层方案编辑，成功刷新列表和原产出对象版本', async () => {
    const { version, scope } = await setup()
    await version.vm.$emit('action', 'submit', version.props('rows')[0])
    await flushPromises()
    mocks.detail.mockResolvedValue({ data: { approveStatus: BomStatusEnum.DRAFT.value } })
    await scope.vm.$emit('open-bom', 5, true)
    await flushPromises()
    const form = wrapper!.findComponent({ name: 'BomFormDialog' })
    expect(form.props('modelValue')).toBe(true)
    expect(form.props('bomId')).toBe(5)
    await form.vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(mocks.reloadScope).toHaveBeenCalledTimes(1)
    mocks.get.mockClear()
    await scope.vm.$emit('done', { successes: [{ rootBomId: 7 }], failures: [] })
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledWith('/engineering/bom/object-versions', {
      params: { outputType: 'PRODUCT', outputId: 2 },
    })
  })
  it('流水入口保留并指向当前行已有版本；查看明细不切换成编辑', async () => {
    const { scope } = await setup()
    await wrapper!
      .findAll('button')
      .find((button) => button.text() === '流水')!
      .trigger('click')
    await flushPromises()
    const trace = wrapper!.findComponent({ name: 'TraceTimeline' })
    expect(trace.props('modelValue')).toBe(true)
    expect(trace.props('bizId')).toBe('7')
    await scope.vm.$emit('open-bom', 1, false)
    await flushPromises()
    expect(wrapper!.findComponent({ name: 'BomDetail' }).props('bomId')).toBe(1)
    expect(wrapper!.findComponent({ name: 'BomFormDialog' }).props('modelValue')).toBe(false)
  })
})
