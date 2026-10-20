/**
 * 卡 dev-20261019-014：工艺路线工序库图标 tab 固定业务顺序
 *
 * 覆盖：
 * 1）groupProcessesByCategory / processCategorySortOrder：乱序输入 → 面板/上线/下线/其他，未分类与未知分类末位；
 * 2）图标区 tab 实际渲染顺序不随数据到达顺序漂移（乱序 props 挂载）；
 * 3）搜索过滤后仍按同一业务顺序，不回落到到达顺序；
 * 4）未分类/未知分类排最后且标签仍为「未分类」；
 * 5）下方组合工序表格 tab（ROUTE_STRUCTURE_TABS）顺序不变，且与图标区共用同一分类顺序。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 \
 *     npx vitest run src/views/product/route/components/__tests__/routeItemIconTabs.spec.ts --maxWorkers=1
 */
import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

import RouteItemIconEditor from '../RouteItemIconEditor.vue'
import {
  ROUTE_STRUCTURE_TABS,
  groupProcessesByCategory,
  processCategorySortOrder,
} from '../routeProcessTabs'
import type { StandardProcessOption } from '@/types/product'

// 仓库 vitest.config.ts 未把 element-plus 内联，外部化依赖的 style/css 会走 Node 加载器报
// Unknown file extension ".css"，这里只 mock 样式副作用入口，组件仍真实挂载。
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))

vi.mock('../RouteRemarkAutocomplete.vue', () => ({ default: {
  name: 'RouteRemarkAutocomplete',
  props: ['modelValue', 'processCategory', 'processIds'],
  emits: ['update:modelValue'],
  template: `<input class="remark-test-input" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`,
} }))

// 子组件在这里只作为挂载占位：真实模块会连带引入大量 element-plus 样式入口，
// 与「图标区 tab 排序」无关，mock 掉可把外部化 CSS 的加载面收敛到本文件的 mock 列表。
vi.mock('@/components/print/PrintFieldAutocomplete.vue', () => ({
  default: { name: 'PrintFieldAutocomplete', render: () => null },
}))
vi.mock('@/components/product/EngineeringRoutingItem.vue', () => ({
  default: { name: 'EngineeringRoutingItem', render: () => null },
}))
vi.mock('@/components/ProcessOperation/index.vue', () => ({
  default: { name: 'ProcessOperation', render: () => null },
}))

function processOption(
  processId: number,
  processName: string,
  processCategory: string
): StandardProcessOption {
  return {
    processId,
    processCode: `P${processId}`,
    processName,
    processType: 'PUNCH_SHAPE',
    processTypeName: '冲型',
    processCategory,
    processCategoryName: '',
    standardLaborHours: 0,
    standardMachineHours: 0,
    processParamTemplate: '',
    skillRequirement: '',
    equipmentType: '',
    qualityStandard: '',
    description: '',
    hasIndex: 0,
    hasWorkInstruction: 0,
    isEnabled: 1,
    displayOrder: 0,
    icon: '',
  }
}

const GLOBAL_STUBS = {
  SvgIcon: true,
  PrintFieldAutocomplete: true,
  EngineeringRoutingItem: true,
  ProcessOperation: true,
}

function mountEditor(standardProcesses: StandardProcessOption[]) {
  return mount(RouteItemIconEditor, {
    props: { modelValue: [], standardProcesses },
    global: { stubs: GLOBAL_STUBS },
  })
}

/** 图标区（上方标准工序库）tab 标签顺序 */
function iconTabLabels(wrapper: ReturnType<typeof mountEditor>): string[] {
  const area = wrapper.find('.icon-selector-area')
  expect(area.exists()).toBe(true)
  return area.findAll('.el-tabs__item').map((tab) => tab.text())
}

describe('groupProcessesByCategory 分类业务顺序', () => {
  it('乱序输入（OTHER→DOWN_LINE→UP_LINE→PANEL）输出 面板/上线/下线/其他', () => {
    const groups = groupProcessesByCategory(
      [
        processOption(1, '其他工序', 'OTHER'),
        processOption(2, '下线工序', 'DOWN_LINE'),
        processOption(3, '上线工序', 'UP_LINE'),
        processOption(4, '面板工序', 'PANEL'),
      ],
      (p) => p.processCategory
    )
    expect(groups.map((g) => g.key)).toEqual(['PANEL', 'UP_LINE', 'DOWN_LINE', 'OTHER'])
    expect(groups.map((g) => g.label)).toEqual(['面板', '上线', '下线', '其他'])
  })

  it('未分类/未知分类固定末位并合并为「未分类」', () => {
    const groups = groupProcessesByCategory(
      [
        processOption(1, '未知甲', 'UNKNOWN_A'),
        processOption(2, '空分类', ''),
        processOption(3, '其他工序', 'OTHER'),
        processOption(4, '面板工序', 'PANEL'),
        processOption(5, '未知乙', 'UNKNOWN_B'),
      ],
      (p) => p.processCategory
    )
    expect(groups.map((g) => g.key)).toEqual(['PANEL', 'OTHER', ''])
    expect(groups.at(-1)!.label).toBe('未分类')
    expect(groups.at(-1)!.options.map((p) => p.processName)).toEqual(['未知甲', '空分类', '未知乙'])
    expect(processCategorySortOrder('UNKNOWN_A')).toBeGreaterThan(processCategorySortOrder('OTHER'))
  })

  it('同分类内保持传入顺序（不在分类内再排序）', () => {
    const groups = groupProcessesByCategory(
      [processOption(9, '面板乙', 'PANEL'), processOption(2, '面板甲', 'PANEL')],
      (p) => p.processCategory
    )
    expect(groups[0].options.map((p) => p.processName)).toEqual(['面板乙', '面板甲'])
  })
})

describe('RouteItemIconEditor 图标区 tab 顺序', () => {
  const shuffled = [
    processOption(1, '其他工序', 'OTHER'),
    processOption(2, '下线工序', 'DOWN_LINE'),
    processOption(3, '上线工序', 'UP_LINE'),
    processOption(4, '面板工序', 'PANEL'),
  ]

  it('乱序数据挂载后 tab 顺序固定为 面板/上线/下线/其他', async () => {
    const wrapper = mountEditor(shuffled)
    await nextTick()
    expect(iconTabLabels(wrapper)).toEqual(['面板', '上线', '下线', '其他'])
    // 默认选中第一组（面板）
    expect(wrapper.find('.icon-selector-area .el-tabs__item.is-active').text()).toBe('面板')
    wrapper.unmount()
  })

  it('搜索命中部分工序后 tab 顺序仍按业务顺序，不回到到达顺序', async () => {
    const wrapper = mountEditor(shuffled)
    await nextTick()

    // 数据到达顺序是 下线 → 上线；过滤后仍必须是 上线 → 下线
    await wrapper.find('.selector-toolbar input').setValue('线')
    await nextTick()
    expect(iconTabLabels(wrapper)).toEqual(['上线', '下线'])

    // 清空关键字恢复全量，顺序仍稳定
    await wrapper.find('.selector-toolbar input').setValue('')
    await nextTick()
    expect(iconTabLabels(wrapper)).toEqual(['面板', '上线', '下线', '其他'])
    wrapper.unmount()
  })

  it('存在空分类/未知分类时排最后，标签为「未分类」', async () => {
    const wrapper = mountEditor([
      processOption(1, '未知工序', 'UNKNOWN_Z'),
      processOption(2, '空分类工序', ''),
      processOption(3, '下线工序', 'DOWN_LINE'),
      processOption(4, '面板工序', 'PANEL'),
    ])
    await nextTick()
    const labels = iconTabLabels(wrapper)
    expect(labels).toEqual(['面板', '下线', '未分类'])
    wrapper.unmount()
  })

  it('下方组合工序表格 tab 顺序不变，且与图标区共用同一分类顺序', async () => {
    expect(ROUTE_STRUCTURE_TABS.map((tab) => tab.label)).toEqual(['面板', '上线', '下线', '未分类'])

    const wrapper = mountEditor(shuffled)
    await nextTick()
    const iconLabels = iconTabLabels(wrapper)
    // 图标区的具名分类（去掉末位「其他/未分类」）必须与表格 tab 的固定顺序一致；
    // 表格侧把 OTHER 与空值都归入「未分类」，图标区仍保留「其他」标签，二者都在末位。
    expect(iconLabels.filter((label) => label !== '其他' && label !== '未分类')).toEqual(
      ROUTE_STRUCTURE_TABS.map((tab) => tab.label).filter((label) => label !== '未分类')
    )
    expect(iconLabels.at(-1)).toBe('其他')
    wrapper.unmount()
  })
})


describe('新旧编辑入口的父行备注', () => {
  it.each([[false, false], [false, true], [true, false], [true, true]])(
    'modernOperation=%s、组合=%s：备注可编辑且保存输出不串写说明或子备注',
    async (modernOperation, composite) => {
      const single = { ...processOption(3, '冲型', 'PANEL'), itemId: 1,
        remark: '单作业备注', description: '原说明', workInstruction: '原作业说明' }
      const child = { ...single, itemId: 2, parentId: 1, remark: '子备注' }
      const parent = composite ? { ...single, processId: undefined, remark: '组合备注',
        children: [child, { ...child, itemId: 3, processId: 20 }] } : single
      const wrapper = mount(RouteItemIconEditor, {
        props: { modelValue: [parent] as any, standardProcesses: [], modernOperation },
        global: { stubs: GLOBAL_STUBS },
      })
      await nextTick()
      await flushPromises()
      expect(wrapper.findAllComponents({ name: 'ElTableColumn' }).some(col => col.props('label') === '备注')).toBe(true)
      expect(wrapper.text()).not.toContain('组合备注（存到第一条工序的说明中）')
      const input = wrapper.find('.remark-test-input')
      expect((input.element as HTMLInputElement).value).toBe(composite ? '组合备注' : '单作业备注')
      await input.setValue('选择后修改的备注')
      const saved = (wrapper.vm as any).getItems()[0]
      expect(saved.remark).toBe('选择后修改的备注')
      expect(saved.description).toBe('原说明')
      expect(saved.workInstruction).toBe('原作业说明')
      if (composite) expect(saved.children.map((item: any) => item.remark)).toEqual(['子备注', '子备注'])
      wrapper.unmount()
    }
  )
})
