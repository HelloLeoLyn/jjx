vi.mock('element-plus/es/components/config-provider/style/css', () => ({}))
vi.mock('element-plus/es/components/menu-item-group/style/css', () => ({}))
vi.mock('element-plus/es/components/color-picker-panel/style/css', () => ({}))
vi.mock('element-plus/es/components/tree-v2/style/css', () => ({}))
vi.mock('element-plus/es/components/option-group/style/css', () => ({}))
vi.mock('element-plus/es/components/teleport/style/css', () => ({}))
vi.mock('element-plus/es/components/steps/style/css', () => ({}))
vi.mock('element-plus/es/components/select-v2/style/css', () => ({}))
vi.mock('element-plus/es/components/text/style/css', () => ({}))
vi.mock('element-plus/es/components/time-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/splitter/style/css', () => ({}))
vi.mock('element-plus/es/components/sub-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/virtual-list/style/css', () => ({}))
vi.mock('element-plus/es/components/cascader-panel/style/css', () => ({}))
vi.mock('element-plus/es/components/header/style/css', () => ({}))
vi.mock('element-plus/es/components/affix/style/css', () => ({}))
vi.mock('element-plus/es/components/step/style/css', () => ({}))
vi.mock('element-plus/es/components/notification/style/css', () => ({}))
vi.mock('element-plus/es/components/image-viewer/style/css', () => ({}))
vi.mock('element-plus/es/components/tour/style/css', () => ({}))
vi.mock('element-plus/es/components/menu-item/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton-item/style/css', () => ({}))
vi.mock('element-plus/es/components/container/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/countdown/style/css', () => ({}))
vi.mock('element-plus/es/components/segmented/style/css', () => ({}))
vi.mock('element-plus/es/components/anchor-link/style/css', () => ({}))
vi.mock('element-plus/es/components/carousel/style/css', () => ({}))
vi.mock('element-plus/es/components/backtop/style/css', () => ({}))
vi.mock('element-plus/es/components/breadcrumb/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker-panel/style/css', () => ({}))
vi.mock('element-plus/es/components/avatar-group/style/css', () => ({}))
vi.mock('element-plus/es/components/button-group/style/css', () => ({}))
vi.mock('element-plus/es/components/main/style/css', () => ({}))
vi.mock('element-plus/es/components/mention/style/css', () => ({}))
vi.mock('element-plus/es/components/breadcrumb-item/style/css', () => ({}))
vi.mock('element-plus/es/components/color-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/popper/style/css', () => ({}))
vi.mock('element-plus/es/components/badge/style/css', () => ({}))
vi.mock('element-plus/es/components/avatar/style/css', () => ({}))
vi.mock('element-plus/es/components/statistic/style/css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse-transition/style/css', () => ({}))
vi.mock('element-plus/es/components/footer/style/css', () => ({}))
vi.mock('element-plus/es/components/table-v2/style/css', () => ({}))
vi.mock('element-plus/es/components/transfer/style/css', () => ({}))
vi.mock('element-plus/es/components/rate/style/css', () => ({}))
vi.mock('element-plus/es/components/image/style/css', () => ({}))
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
vi.mock('element-plus/es/components/carousel-item/style/css', () => ({}))
vi.mock('element-plus/es/components/splitter-panel/style/css', () => ({}))
vi.mock('element-plus/es/components/watermark/style/css', () => ({}))
vi.mock('element-plus/es/components/infinite-scroll/style/css', () => ({}))
vi.mock('element-plus/es/components/aside/style/css', () => ({}))
vi.mock('element-plus/es/components/menu/style/css', () => ({}))
vi.mock('element-plus/es/components/time-select/style/css', () => ({}))
vi.mock('element-plus/es/components/page-header/style/css', () => ({}))
vi.mock('element-plus/es/components/input-tag/style/css', () => ({}))
vi.mock('element-plus/es/components/tour-step/style/css', () => ({}))
vi.mock('element-plus/es/components/space/style/css', () => ({}))
vi.mock('element-plus/es/components/anchor/style/css', () => ({}))
vi.mock('element-plus/es/components/result/style/css', () => ({}))
vi.mock('element-plus/es/components/check-tag/style/css', () => ({}))
vi.mock('element-plus/es/components/calendar/style/css', () => ({}))
vi.mock('element-plus/es/components/overlay/style/css', () => ({}))
vi.mock('element-plus/es/components/slider/style/css', () => ({}))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, reactive, computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import Workbench from '../../workbench.vue'
import SamplePlanView from '../SamplePlanView.vue'
import SampleExecutionView from '../SampleExecutionView.vue'
import SampleInfoCard from '../SampleInfoCard.vue'
import { SampleProcessStatusEnum } from '@/enums/sales'

const shared = vi.hoisted(() => ({ state: null as any }))
vi.mock('../../composables/useSampleWorkbench', () => ({ useSampleWorkbench: () => shared.state }))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({
  default: { name: 'AttachmentCenter', template: '<div data-test="attachment-center" />' },
}))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
vi.mock('@/api/sales/sampleOrder', () => ({
  sampleOrderApi: { listDefects: vi.fn(async () => ({ data: [] })) },
}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse-item/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-group/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-button/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-button/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/popconfirm/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/progress/style/css', () => ({}))
vi.mock('element-plus/es/components/tree/style/css', () => ({}))
vi.mock('element-plus/es/components/tree-select/style/css', () => ({}))
vi.mock('element-plus/es/components/cascader/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/sample-order/components/WorkProjectPicker.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/sample-order/components/SampleTransferDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/quotation/components/QuotationDetailDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/inquiry/components/InquiryDetailDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('../SourceDocSummaryDialog.vue', () => ({ default: { template: '<div />' } }))
// 边界组件替身只隔离重型档案依赖；主页面、两视图、公共卡、执行时间线和备注挂真实组件。
vi.mock('../SampleProcessPlanEditor.vue', () => ({
  default: {
    props: ['controller', 'readonly'],
    template:
      '<input data-test="assembly-draft" v-model="controller.planList[0].processNote" :readonly="readonly" />',
  },
}))
vi.mock('../PrintProcessPanel.vue', () => ({
  default: {
    props: ['printList', 'readonly'],
    template:
      '<input data-test="print-draft" v-model="printList[0].printName" :readonly="readonly" />',
  },
}))
vi.mock('../BomPanel.vue', () => ({
  default: {
    props: ['bomList', 'showTransfer', 'readonly'],
    template: '<input data-test="bom-draft" v-model="bomList[0].name" :readonly="readonly" />',
  },
}))
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
)
vi.stubGlobal(
  'matchMedia',
  vi.fn(() => ({
    matches: false,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
  }))
)

let state: any
beforeEach(() => {
  document.body.innerHTML = ''
  state = new Proxy({} as Record<string, any>, {
    get(target, key: string) {
      if (!(key in target)) target[key] = vi.fn()
      return target[key]
    },
  })
  for (const name of [
    'cardPickerVisible',
    'indexDialogVisible',
    'historyCopyVisible',
    'historyLoading',
    'historyCopying',
    'copyPrevLoading',
    'productPlanLoading',
    'productPlanCopying',
    'materialCreateVisible',
    'transferDialogVisible',
    'batchMaterialVisible',
    'batchMaterialLoading',
  ])
    state[name] = ref(false)
  Object.assign(state, {
    card: ref({ orderId: 8801, orderNo: 'S-8801', productName: '测试产品', sampleRound: 2 }),
    orderId: ref(8801),
    readonlyMode: ref(false),
    saving: ref(false),
    savingPlan: ref(false),
    form: reactive({ note: '备注草稿' }),
    majorCategory: ref('ASSEMBLY'),
    activePlanTab: ref('all'),
    planList: ref([
      {
        uid: 'p1',
        items: [{ processName: '冲型' }],
        processNote: '组装草稿',
        status: SampleProcessStatusEnum.TODO.value,
      },
    ]),
    printList: ref([
      { processId: 9, printName: '印刷草稿', status: SampleProcessStatusEnum.TODO.value },
    ]),
    bomList: ref([{ name: '材料草稿' }]),
    roundList: ref([{ roundNo: 1 }, { roundNo: 2 }]),
    activeRound: ref('2'),
    summary: ref({}),
    doneCount: ref(0),
    totalCount: ref(2),
    customerFileList: ref([]),
    cardPickerIds: ref([]),
    indexDialogName: ref(''),
    indexDialogValue: ref(1),
    batchMaterialOptions: ref([]),
    copyOverwrite: ref(false),
    allProcesses: ref([]),
    historyOrders: ref([]),
    batchSelected: ref(new Set()),
    productPlan: ref(null),
    historySelected: ref(null),
    historySelectedRound: ref(null),
    activeRoundData: ref({ engineeringNote: '历史备注' }),
    activeRoundBom: ref([{ name: '历史材料' }]),
    activeRoundProcesses: ref([{ processName: '历史冲型' }]),
    activeRoundPrintList: ref([
      { processName: '历史印刷', status: SampleProcessStatusEnum.DONE.value },
    ]),
    formatTime: () => '',
    parseMaterials: () => [],
    printParamsOf: () => ({}),
    execBlockReason: () => '工序草稿未保存',
    printBlockReason: () => '印刷行未保存',
  })
  state.isCurrentRound = computed(() => state.activeRound.value === '2')
  state.planEditor = reactive({ planList: state.planList.value })
  shared.state = state
})
function openPage() {
  return mount(Workbench, {
    attachTo: document.body,
    global: {
      directives: { hasPermi: {} },
      stubs: {
        Teleport: true,
        ElDialog: {
          props: ['modelValue', 'title'],
          template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
        },
      },
    },
  })
}
async function selectView(wrapper: any, value: string) {
  wrapper.findComponent('.view-tabs').vm.$emit('update:modelValue', value)
  await flushPromises()
}
describe('工程打样工作台双视图接入', () => {
  it('切换保留组装/印刷/BOM/备注草稿和组件实例，不触发保存或执行', async () => {
    const w = openPage()
    await flushPromises()
    const planUid = w.findComponent(SamplePlanView).vm.$.uid
    await w.get('[data-test="assembly-draft"]').setValue('新组装')
    state.majorCategory.value = 'PRINT'
    await flushPromises()
    await w.get('[data-test="print-draft"]').setValue('新印刷')
    await w
      .findAll('button')
      .find((b) => b.text().includes('展开核对 BOM'))!
      .trigger('click')
    await w.get('[data-test="bom-draft"]').setValue('新材料')
    await w
      .get('textarea[placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"]')
      .setValue('新备注')
    await selectView(w, 'execution')
    expect(w.findComponent(SamplePlanView).isVisible()).toBe(false)
    expect(w.findComponent(SampleExecutionView).isVisible()).toBe(true)
    await selectView(w, 'plan')
    expect(w.findComponent(SamplePlanView).vm.$.uid).toBe(planUid)
    expect(state.planList.value[0].processNote).toBe('新组装')
    expect(state.printList.value[0].printName).toBe('新印刷')
    expect(state.bomList.value[0].name).toBe('新材料')
    expect(state.form.note).toBe('新备注')
    for (const name of ['savePlan', 'saveNote', 'advancePlan', 'advancePrint', 'handleMarkReady'])
      expect(state[name]).not.toHaveBeenCalled()
    w.unmount()
  })
  it('执行绑定同一订单轮次与原回调，未保存工序不能推进；资料仍为公共单一入口', async () => {
    const w = openPage()
    await flushPromises()
    await selectView(w, 'execution')
    const exec = w.findComponent(SampleExecutionView)
    expect(exec.props('orderId')).toBe(8801)
    expect(exec.props('roundNo')).toBe(2)
    expect(exec.props('planList')).toBe(state.planList.value)
    expect(exec.props('advancePlan')).toBe(state.advancePlan)
    expect(
      w
        .findAll('button')
        .filter((b) => /▶ 开始/.test(b.text()))
        .every((b) => b.attributes('disabled') !== undefined)
    ).toBe(true)
    exec.vm.$emit('view-files')
    await flushPromises()
    expect(w.findComponent(SampleInfoCard).find('.el-collapse-item').classes()).toContain(
      'is-active'
    )
    expect(w.findAllComponents({ name: 'AttachmentCenter' })).toHaveLength(1)
    exec.vm.$emit('mark-ready')
    expect(state.handleMarkReady).toHaveBeenCalledTimes(1)
    w.unmount()
  })
  it('历史轮不挂当前方案/执行，三类快照可见，公共写事件不能调用当前轮业务', async () => {
    state.activeRound.value = '1'
    const w = openPage()
    await flushPromises()
    expect(w.findComponent(SamplePlanView).exists()).toBe(false)
    expect(w.findComponent(SampleExecutionView).exists()).toBe(false)
    expect(w.text()).toContain('历史冲型')
    expect(w.text()).toContain('历史材料')
    state.majorCategory.value = 'PRINT'
    await flushPromises()
    expect(w.text()).toContain('历史印刷')
    const info = w.findComponent(SampleInfoCard)
    expect(info.props('readonly')).toBe(true)
    expect(info.props('displayRound')).toBe(1)
    for (const event of ['historyCopy', 'clear', 'transfer']) info.vm.$emit(event)
    await flushPromises()
    for (const name of ['openHistoryCopy', 'clearCurrentRound', 'handleTransfer'])
      expect(state[name]).not.toHaveBeenCalled()
    await selectView(w, 'execution')
    expect(w.findComponent(SampleExecutionView).exists()).toBe(false)
    w.unmount()
  })
  it('只读当前轮禁用公共写操作，备注仍独立保存', async () => {
    state.readonlyMode.value = true
    const w = openPage()
    await flushPromises()
    expect(w.findComponent(SampleExecutionView).props('readonly')).toBe(true)
    const info = w.findComponent(SampleInfoCard)
    for (const event of ['historyCopy', 'clear', 'transfer']) info.vm.$emit(event)
    w.findComponent(SampleExecutionView).vm.$emit('mark-ready')
    expect(state.openHistoryCopy).not.toHaveBeenCalled()
    expect(state.handleTransfer).not.toHaveBeenCalled()
    expect(state.handleMarkReady).not.toHaveBeenCalled()
    expect(w.text()).toContain('单独保存')
    w.unmount()
  })
  it('复制方案三种来源仍走原回调；弹窗打开后切历史也不能确认复制', async () => {
    state.openHistoryCopy.mockImplementation(() => {
      state.historyCopyVisible.value = true
    })
    state.historySelectedRound.value = { roundNo: 1 }
    state.historySelected.value = { orderId: 77 }
    state.productPlan.value = { eligible: true }
    const w = openPage()
    await flushPromises()
    w.findComponent(SampleInfoCard).vm.$emit('historyCopy')
    await flushPromises()
    expect(state.openHistoryCopy).toHaveBeenCalledTimes(1)
    const cases = [
      ['product', '复制方案到当前轮', 'confirmProductPlanCopy'],
      ['order', '复制到当前', 'confirmHistoryCopy'],
      ['round', '复制选中轮到当前', 'copyRoundFrom'],
    ]
    for (const [tab, label, callback] of cases) {
      ;(w.vm as any).copySourceTab = tab
      await flushPromises()
      const button = Array.from(document.querySelectorAll('button')).find(
        (b) => b.textContent?.trim() === label
      )
      expect(button).toBeTruthy()
      button!.click()
      await flushPromises()
      expect(state[callback]).toHaveBeenCalledTimes(1)
    }
    expect(state.copyRoundFrom).toHaveBeenCalledWith(1)
    state.activeRound.value = '1'
    await flushPromises()
    const blocked = Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === '复制选中轮到当前'
    )!
    expect(blocked.disabled).toBe(true)
    blocked.click()
    await flushPromises()
    expect(state.copyRoundFrom).toHaveBeenCalledTimes(1)
    w.unmount()
  })
  it('清空保留确认及保存后生效语义；确认过程中换历史轮也不清空当前轮', async () => {
    let confirm: () => void = () => {}
    const spy = vi.spyOn(ElMessageBox, 'confirm').mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          confirm = resolve
        }) as any
    )
    const w = openPage()
    await flushPromises()
    const info = w.findComponent(SampleInfoCard)
    info.vm.$emit('clear')
    await flushPromises()
    expect(spy.mock.calls[0][0]).toContain('保存才生效')
    state.activeRound.value = '1'
    confirm()
    await flushPromises()
    expect(state.clearCurrentRound).not.toHaveBeenCalled()
    state.activeRound.value = '2'
    await flushPromises()
    info.vm.$emit('clear')
    await flushPromises()
    confirm()
    await flushPromises()
    expect(state.clearCurrentRound).toHaveBeenCalledTimes(1)
    info.vm.$emit('transfer')
    expect(state.handleTransfer).toHaveBeenCalledTimes(1)
    spy.mockRestore()
    w.unmount()
  })
})
