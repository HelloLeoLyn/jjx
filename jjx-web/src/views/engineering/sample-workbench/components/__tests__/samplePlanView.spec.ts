/**
 * 卡007 方案编制视图定向测试（dev-20261018-007）
 *
 * 覆盖：
 * 1）SamplePlanView：大类切换、BOM 默认折叠/展开、折叠不重建插槽实例、备注可见；
 * 2）SampleProcessPlanEditor：选择器默认收起、展开后可点选/拖拽入口仍在、保存仍走 controller.savePlan；
 * 3）SampleProcessPlanItem：完成编辑只发 finish-edit，并提示需保存打样方案；
 * 4）BomPanel：showTransfer 默认 true 兼容既有调用方，false 只隐藏资料转移区、transfer 事件不变；
 * 5）PrintProcessPanel：保存按钮说明会提交整份打样方案。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run src/views/engineering/sample-workbench/components/__tests__/samplePlanView.spec.ts --maxWorkers=1
 */
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, onMounted, reactive } from 'vue'

import SamplePlanView from '../SamplePlanView.vue'
import SampleProcessPlanEditor from '../SampleProcessPlanEditor.vue'
import SampleProcessPlanItem from '../SampleProcessPlanItem.vue'
import BomPanel from '../BomPanel.vue'
import PrintProcessPanel from '../PrintProcessPanel.vue'

vi.mock('@/api/sales/sampleOrder', () => ({
  getProcessHistory: vi.fn().mockResolvedValue({ data: { printNames: [], colorNos: [], inkNos: [] } }),
}))
vi.mock('@/api/inventory/material', () => ({
  materialApi: { search: vi.fn().mockResolvedValue({ data: { records: [] } }) },
}))

// dev-20261018-007 复核修复：仓库 vitest.config.ts 未把 element-plus 加入
// server.deps.inline，外部化依赖的 style/css 会走 Node 加载器报
// Unknown file extension ".css"。这里只 mock 样式副作用入口，行为组件仍真实挂载。
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))


const slotOf = (name: string) => () => h('div', { 'data-test': 'slot-' + name }, name)

// v-show 在 jsdom 下以行内 style.display 为准（VTU isVisible 对 jsdom 无布局节点不可靠）
function shown(el: any) {
  return !(el.attributes('style') || '').includes('display: none')
}

describe('SamplePlanView 方案编制视图', () => {
  it('默认显示冲型组装，切到印刷后只切换展示', async () => {
    const wrapper = mount(SamplePlanView, {
      slots: { assembly: slotOf('assembly'), print: slotOf('print') },
    })

    expect(shown(wrapper.find('#pane-ASSEMBLY'))).toBe(true)
    expect(shown(wrapper.find('#pane-PRINT'))).toBe(false)

    const printTab = wrapper.findAll('.el-tabs__item').find((tab) => tab.text().includes('印刷'))
    expect(printTab).toBeTruthy()
    await printTab!.trigger('click')
    await nextTick()

    expect(shown(wrapper.find('#pane-PRINT'))).toBe(true)
    expect(shown(wrapper.find('#pane-ASSEMBLY'))).toBe(false)
    expect(wrapper.emitted('update:majorCategory')?.at(-1)).toEqual(['PRINT'])

    wrapper.unmount()
  })

  it('BOM 默认折叠、入口常驻；展开/收起不重建插槽实例（不丢草稿）', async () => {
    let mountedCount = 0
    const MaterialsProbe = defineComponent({
      setup() {
        onMounted(() => {
          mountedCount += 1
        })
        return () => h('div', { 'data-test': 'materials-probe' }, 'materials')
      },
    })

    const wrapper = mount(SamplePlanView, {
      slots: { materials: () => h(MaterialsProbe), note: slotOf('note') },
    })

    const materialsBody = wrapper.find('[data-test="plan-slot-materials"]')
    expect(shown(materialsBody)).toBe(false)
    expect(wrapper.find('[data-test="materials-probe"]').exists()).toBe(true)
    expect(mountedCount).toBe(1)
    expect(wrapper.find('.materials-toggle').text()).toContain('展开核对 BOM')

    await wrapper.find('.materials-toggle').trigger('click')
    expect(shown(materialsBody)).toBe(true)
    expect(wrapper.find('.materials-toggle').text()).toContain('收起 BOM')

    await wrapper.find('.materials-toggle').trigger('click')
    expect(shown(materialsBody)).toBe(false)
    // 折叠两次仍是同一个实例：v-show，而不是 v-if 重建
    expect(mountedCount).toBe(1)

    // 工程备注正常显示
    expect(wrapper.find('[data-test="slot-note"]').exists()).toBe(true)

    wrapper.unmount()
  })
})

function makeController(overrides: Record<string, any> = {}) {
  return reactive({
    batchMode: false,
    batchSelected: new Set<string>(),
    batchCategory: null,
    savingPlan: false,
    hasUnsavedChanges: false,
    frequentMaterials: [],
    planTabs: [{ value: 'PANEL', label: '面板' }],
    categoryOptions: [{ itemValue: 'PANEL', label: '面板' }],
    cardsByTab: vi.fn().mockReturnValue([]),
    saveStateText: vi.fn().mockReturnValue('已保存'),
    parseMaterials: vi.fn().mockReturnValue([]),
    toggleBatchMode: vi.fn(),
    toggleBatchSelect: vi.fn(),
    toggleBatchSelectAll: vi.fn(),
    savePlan: vi.fn().mockResolvedValue(true),
    addFrequentMaterial: vi.fn(),
    onPlanDrop: vi.fn(),
    onCardDrop: vi.fn(),
    onCardDragOver: vi.fn(),
    onCardDragLeave: vi.fn(),
    removeCardItem: vi.fn(),
    removePlanCard: vi.fn(),
    cancelEdit: vi.fn(),
    finishEdit: vi.fn(),
    addMaterialRow: vi.fn(),
    startEdit: vi.fn(),
    openCardPicker: vi.fn(),
    openBatchMaterial: vi.fn(),
    batchDelete: vi.fn(),
    applyBatchCategory: vi.fn(),
    onUpdateIndex: vi.fn(),
    searchMaterials: vi.fn(),
    onMaterialSelected: vi.fn(),
    onSelectVisibleChange: vi.fn(),
    openMaterialCreate: vi.fn(),
    ...overrides,
  })
}

describe('SampleProcessPlanEditor 左侧标准工序栏', () => {
  it('默认展开宽 300、可收起/展开且不重建选择器，保存仍走控制器', async () => {
    localStorage.removeItem('jjx.sample.planPickerWidth')
    const controller = makeController()
    const wrapper = mount(SampleProcessPlanEditor, {
      props: { controller, readonly: false },
      global: {
        stubs: {
          WorkProjectPicker: true,
          FrequentMaterialsBar: true,
          SampleProcessPlanItem: true,
          BatchToolbar: true,
        },
      },
    })

    // dev-20261024-010：标准工序库改左侧竖栏，默认展开 300px，可收起（40px 轨道），只切 v-show 不重建
    const toggle = wrapper.find('.picker-toggle')
    expect(toggle.exists()).toBe(true)
    const col = wrapper.find('.picker-col')
    expect(col.attributes('style')).toContain('300px')
    expect(shown(wrapper.find('[data-test="picker-body"]'))).toBe(true)
    expect(wrapper.find('[data-test="picker-resizer"]').exists()).toBe(true)
    const picker = wrapper.find('work-project-picker-stub')
    expect(picker.exists()).toBe(true)
    expect(picker.attributes('layout')).toBeUndefined()
    await toggle.trigger('click')
    expect(shown(wrapper.find('[data-test="picker-body"]'))).toBe(false)
    expect(wrapper.find('.picker-col').attributes('style')).toContain('40px')
    await toggle.trigger('click')
    expect(shown(wrapper.find('[data-test="picker-body"]'))).toBe(true)
    expect(wrapper.find('work-project-picker-stub').exists()).toBe(true)
    // 拖拽落点（新建卡片区域）仍在
    expect(wrapper.find('.plan-scroll').exists()).toBe(true)

    const saveBtn = wrapper.findAll('button').find((btn) => btn.text().includes('保存打样方案'))
    expect(saveBtn).toBeTruthy()
    expect(saveBtn!.attributes('title')).toContain('组装')
    expect(saveBtn!.attributes('title')).toContain('印刷')
    expect(saveBtn!.attributes('title')).toContain('BOM')
    await saveBtn!.trigger('click')
    expect(controller.savePlan).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })
})

describe('SampleProcessPlanItem 完成编辑语义', () => {
  it('完成编辑只结束本地编辑并提示保存，不冒充已保存', async () => {
    const pc = reactive({
      uid: 'card-1',
      editing: true,
      saveState: 'dirty',
      items: [],
      materials: null,
      materialRows: [],
      operationRemark: '',
    })
    const wrapper = mount(SampleProcessPlanItem, {
      props: {
        pc,
        index: 0,
        batchMode: false,
        batchSelected: false,
        saveStateText: () => '未保存',
        parseMaterials: () => [],
        readonly: false,
      },
      global: { stubs: { ProcessContentEditor: true, ProcessMaterialEditor: true } },
    })

    expect(wrapper.text()).toContain('未保存')
    expect(wrapper.text()).toContain('完成编辑只结束本地编辑')
    expect(wrapper.text()).toContain('尚未保存')
    expect(wrapper.text()).toContain('保存后生效')
    const finishBtn = wrapper.findAll('button').find((btn) => btn.text() === '完成编辑')
    expect(finishBtn).toBeTruthy()
    expect(finishBtn!.attributes('title')).toContain('不代表已保存')
    await finishBtn!.trigger('click')
    expect(wrapper.emitted('finish-edit')).toHaveLength(1)
    expect(wrapper.emitted('cancel-edit')).toBeUndefined()

    wrapper.unmount()
  })
})

describe('BomPanel 资料转移区开关', () => {
  const baseProps = {
    bomList: [] as any[],
    projects: [] as any[],
    readonly: false,
    stateOf: () => ({ loading: false, options: [], total: 0 }),
  }

  it('默认显示资料转移区并保留 transfer 事件', async () => {
    const wrapper = mount(BomPanel, { props: { ...baseProps } })
    expect(wrapper.text()).toContain('BOM 物料清单')
    const zone = wrapper.find('[data-test="transfer-zone"]')
    expect(zone.exists()).toBe(true)
    await zone.find('button').trigger('click')
    expect(wrapper.emitted('transfer')).toHaveLength(1)
    wrapper.unmount()
  })

  it('showTransfer=false 只隐藏转移区，BOM 主体仍在', () => {
    const wrapper = mount(BomPanel, { props: { ...baseProps, showTransfer: false } })
    expect(wrapper.find('[data-test="transfer-zone"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('BOM 物料清单')
    expect(wrapper.text()).toContain('暂无材料')
    wrapper.unmount()
  })
})

describe('PrintProcessPanel 印刷行保存范围', () => {
  it('保存按钮与行保存都明确会提交整份打样方案', async () => {
    const savePlan = vi.fn().mockResolvedValue(true)
    const printList: any[] = [
      { uid: 'new-1', category: 'PANEL', printName: '丝印', saveState: 'dirty', materials: null },
    ]
    const wrapper = mount(PrintProcessPanel, {
      props: {
        printList,
        savingPlan: false,
        parseMaterials: () => [],
        addPrintRow: vi.fn(),
        removePrintRow: vi.fn(),
        movePrintRow: vi.fn(),
        savePlan,
        readonly: false,
      },
      global: {
        stubs: { PrintFieldAutocomplete: true, MaterialFormDialog: true },
        directives: { hasPermi: {} },
      },
    })

    expect(wrapper.text()).toContain('行内保存会提交整份打样方案')

    const headerSave = wrapper.findAll('button').find((btn) => btn.text().includes('保存打样方案'))
    expect(headerSave).toBeTruthy()
    expect(headerSave!.attributes('title')).toContain('组装')
    await headerSave!.trigger('click')
    expect(savePlan).toHaveBeenCalledTimes(1)

    const rowSave = wrapper.findAll('button').find((btn) => btn.text() === '保存')
    expect(rowSave).toBeTruthy()
    expect(rowSave!.attributes('title')).toContain('整份打样方案')
    await rowSave!.trigger('click')
    expect(savePlan).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })
})
