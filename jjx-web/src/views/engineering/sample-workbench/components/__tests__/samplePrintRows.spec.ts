/**
 * dev-20261018-036：印刷工序不再自动补空行
 *
 * 覆盖（卡面验收）：
 * 1）加载/保存后重载/切换分类都不自动新增空行；
 * 2）空分类仍有显式「添加印刷工序」入口；
 * 3）多个空行、仅空行都能删除且不回弹（删完显示空态，不自动补）；
 * 4）内容行删除仍按本地移除语义（保存后由整单保存落定）。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run \
 *     src/views/engineering/sample-workbench/components/__tests__/samplePrintRows.spec.ts --maxWorkers=1
 */
import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'

import PrintProcessPanel from '../PrintProcessPanel.vue'

vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
vi.mock('@/api/sales/sampleOrder', () => ({
  getProcessHistory: vi.fn().mockResolvedValue({ data: { printNames: ['丝印', '移印'], colorNos: [], inkNos: [] } }),
}))
vi.mock('@/api/inventory/material', () => ({
  materialApi: { search: vi.fn().mockResolvedValue({ data: { records: [] } }) },
}))

// 仓库 vitest.config.ts 未把 element-plus 加入 server.deps.inline：外部化依赖的 style/css
// 会走 Node 加载器报 Unknown file extension ".css"。这里只 mock 样式副作用入口。
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
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
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

type Row = Record<string, any>

/** 已存内容行（uid 来自后端，不是 new- 前缀） */
const savedRow = (printName: string, category = 'PANEL'): Row => ({
  uid: `dbp-${printName}`,
  category,
  printName,
  saveState: 'synced',
  materials: null,
})

/** 手动新增的空行 */
const blankRow = (category = 'PANEL', uid = `new-${category}-blank`): Row => ({
  uid,
  category,
  printName: '',
  saveState: 'synced',
  materials: null,
})

function mountPanel(printList: Row[], extra: Record<string, any> = {}) {
  const addPrintRow = vi.fn((category?: string) => {
    printList.push(blankRow(category || ''))
  })
  const wrapper = mount(PrintProcessPanel, {
    props: {
      printList,
      savingPlan: false,
      parseMaterials: () => [],
      addPrintRow,
      removePrintRow: vi.fn(),
      movePrintRow: vi.fn(),
      savePlan: vi.fn().mockResolvedValue(true),
      readonly: false,
      ...extra,
    },
    global: {
      stubs: { PrintFieldAutocomplete: true, MaterialFormDialog: true },
      directives: { hasPermi: {} },
    },
  })
  return { wrapper, addPrintRow }
}

/** el-table 的 body 在 jsdom 里要等两拍（列注册后）才有内容 */
async function settle() {
  await flushPromises()
  await nextTick()
  await nextTick()
}

function tab(wrapper: any, label: string) {
  return wrapper.findAll('.el-tabs__item').find((t: any) => t.text().includes(label))
}

function bodyRows(wrapper: any) {
  return wrapper.findAll('tbody tr')
}

function deleteButton(row: any) {
  return row.findAll('button').find((b: any) => b.text().includes('删除'))
}

function addButton(wrapper: any) {
  return wrapper.findAll('button').find((b: any) => b.text().includes('添加印刷工序'))
}

describe('PrintProcessPanel 不再自动补空行（dev-20261018-036）', () => {
  it('挂载已有印刷行不自动补空行', async () => {
    const printList = reactive<Row[]>([savedRow('丝印'), savedRow('移印')])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()

    expect(addPrintRow).not.toHaveBeenCalled()
    expect(printList).toHaveLength(2)
    expect(bodyRows(wrapper)).toHaveLength(2)
    wrapper.unmount()
  })

  it('切换分类不自动补空行', async () => {
    const printList = reactive<Row[]>([savedRow('丝印', 'PANEL')])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()

    await tab(wrapper, '上线')!.trigger('click')
    await settle()
    await tab(wrapper, '未分类')!.trigger('click')
    await settle()

    expect(addPrintRow).not.toHaveBeenCalled()
    expect(printList).toHaveLength(1)
    // 空的「上线/未分类」显示空态说明，且仍保留显式新增按钮
    expect(wrapper.text()).toContain('暂无印刷工序')
    expect(addButton(wrapper)).toBeTruthy()
    wrapper.unmount()
  })

  it('保存后重载（列表被整体替换）不自动补空行', async () => {
    const printList = reactive<Row[]>([savedRow('丝印'), blankRow('PANEL', 'new-old')])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()

    // 模拟 loadPlan 重建：整体替换为后端返回的已存行
    printList.splice(0, printList.length, savedRow('丝印'), savedRow('网印'))
    await settle()

    expect(addPrintRow).not.toHaveBeenCalled()
    expect(printList).toHaveLength(2)
    expect(printList[1].printName).toBe('网印')
    expect(bodyRows(wrapper)).toHaveLength(2)
    wrapper.unmount()
  })

  it('显式「添加印刷工序」仍新增一行（当前分类）', async () => {
    const printList = reactive<Row[]>([])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()

    const addBtn = addButton(wrapper)
    expect(addBtn).toBeTruthy()
    await addBtn!.trigger('click')
    await settle()

    expect(addPrintRow).toHaveBeenCalledTimes(1)
    expect(addPrintRow).toHaveBeenCalledWith('PANEL')
    expect(printList).toHaveLength(1)
    wrapper.unmount()
  })

  it('删除尾部空行不回弹，多个空行都能删干净', async () => {
    const printList = reactive<Row[]>([
      savedRow('丝印'),
      blankRow('PANEL', 'new-a'),
      blankRow('PANEL', 'new-b'),
    ])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()
    expect(bodyRows(wrapper)).toHaveLength(3)

    await deleteButton(bodyRows(wrapper)[2])!.trigger('click')
    await settle()
    expect(printList).toHaveLength(2)
    expect(addPrintRow).not.toHaveBeenCalled()

    await deleteButton(bodyRows(wrapper)[1])!.trigger('click')
    await settle()
    expect(printList).toHaveLength(1)
    expect(printList[0].printName).toBe('丝印')
    expect(addPrintRow).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('仅剩空行时删完不回弹，显示空态与显式新增入口', async () => {
    const printList = reactive<Row[]>([blankRow('PANEL', 'new-only')])
    const { wrapper, addPrintRow } = mountPanel(printList)
    await settle()

    await deleteButton(bodyRows(wrapper)[0])!.trigger('click')
    await settle()

    expect(printList).toHaveLength(0)
    expect(addPrintRow).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('暂无印刷工序')
    expect(addButton(wrapper)).toBeTruthy()
    wrapper.unmount()
  })

  it('内容行删除仍是本地移除（不由补行逻辑代劳）', async () => {
    const printList = reactive<Row[]>([savedRow('丝印'), savedRow('移印')])
    const removePrintRow = vi.fn()
    const { wrapper, addPrintRow } = mountPanel(printList, { removePrintRow })
    await settle()

    await deleteButton(bodyRows(wrapper)[0])!.trigger('click')
    await settle()

    expect(printList.map((r) => r.printName)).toEqual(['移印'])
    expect(addPrintRow).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
