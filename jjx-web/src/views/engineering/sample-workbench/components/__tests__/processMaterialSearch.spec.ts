/**
 * 打样工序材料输入框行为测试（dev-20261018-035）
 *
 * 覆盖：
 * 1）已选材料在输入框内显示名称+规格+编码，不显示裸ID；
 * 2）输入文字只发 search-material，不改该行 materialId / 快照；
 * 3）选中候选项以完整物料对象回传（material-selected），由父级更新身份；
 * 4）显式清空发 material-cleared，不在组件内私改身份；
 * 5）查询失败给重试入口，且不提供「新建物料」诱导；
 * 6）只读场景显示材料名称。
 *
 * 表格替身说明：el-table 在 jsdom 无布局时只把列渲进 hidden-columns、tbody 行无单元格，
 * 故这里用 ElTableStub 按 data × 列默认插槽渲染真实单元格；被测量的材料输入框
 * （EditableSearchSelect）仍是真实公共组件，不是替身。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run \
 *     src/views/engineering/sample-workbench/components/__tests__/processMaterialSearch.spec.ts --maxWorkers=1
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, reactive } from 'vue'

// 仓库 vitest 未把 element-plus 加入 inline，外部化依赖的 style/css 会走 Node 加载器
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))

import ProcessMaterialEditor from '../ProcessMaterialEditor.vue'
import EditableSearchSelect from '@/components/Selector/EditableSearchSelect.vue'

const A = { materialId: 1, materialName: '铜箔A', specification: '35um', materialCode: 'MAT-A', unit: 'M' }
const B = { materialId: 2, materialName: '铜箔B', specification: '70um', materialCode: 'MAT-B', unit: 'M' }

const ElTableStub = defineComponent({
  name: 'ElTable',
  props: { data: { type: Array, default: () => [] as any[] } },
  setup(props, { slots }) {
    return () => {
      const cols: any[] = []
      const collect = (nodes: any[]) => {
        nodes.forEach((n) => {
          if (!n) return
          if (Array.isArray(n)) return collect(n)
          if ((n.type as any)?.name === 'ElTableColumn') cols.push(n)
        })
      }
      collect((slots.default?.() as any[]) || [])
      const cells: any[] = []
      ;(props.data as any[]).forEach((row) => {
        cols.forEach((col, ci) => {
          const cell = (col.children as any)?.default
          cells.push(h('td', { key: `${row.uid || row.materialId}-${ci}` }, typeof cell === 'function' ? cell({ row, $index: 0 }) : []))
        })
      })
      return h('div', { class: 'el-table-stub' }, cells)
    }
  },
})
const ElTableColumnStub = defineComponent({ name: 'ElTableColumn', render: () => null })

const parseMaterials = (json?: string | null) => {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function makePc(editing = true) {
  return reactive({
    editing,
    materials: JSON.stringify([
      { name: '铜箔A', spec: '35um', materialId: 1, materialCode: 'MAT-A', unit: 'M', baseQty: 5, moduleQty: 2, qty: 2.5 },
    ]),
    materialRows: [
      {
        name: '铜箔A', spec: '35um', materialId: 1, materialCode: 'MAT-A', unit: 'M',
        baseQty: 5, moduleQty: 2, qty: 2.5,
        options: [A], loading: false, loadingMore: false, searchFailed: false,
        total: 1, pageNum: 1, lastQuery: '铜箔A', _matKw: '', _searchSeq: 0, uid: 'u1',
      },
    ],
  })
}

function mountIt(pc: any, readonly = false) {
  return mount(ProcessMaterialEditor, {
    attachTo: document.body,
    props: { pc, readonly, parseMaterials },
    global: { stubs: { ElTable: ElTableStub, ElTableColumn: ElTableColumnStub } },
  })
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('ProcessMaterialEditor 材料可编辑搜索', () => {
  it('已选材料显示名称而不是裸ID，并复用公共可编辑搜索框', () => {
    const pc = makePc()
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')

    expect((input.element as HTMLInputElement).value).toBe('铜箔A 35um (MAT-A)')
    w.unmount()
  })

  it('只改输入文字：发 search-material，不改该行 materialId 与档案快照', async () => {
    const pc = makePc()
    const row = pc.materialRows[0]
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')

    await input.trigger('focus')
    await input.setValue('铜箔B')

    const [, emittedRow] = w.emitted('search-material')?.[0] as any[]
    expect((w.emitted('search-material')?.[0] as any[])[0]).toBe('铜箔B')
    expect(emittedRow).toBe(row)
    expect(row.materialId).toBe(1)
    expect(row.name).toBe('铜箔A')
    expect(row.spec).toBe('35um')
    expect(row.unit).toBe('M')
    expect(row.moduleQty).toBe(2)
    expect(row.baseQty).toBe(5)
    w.unmount()
  })

  it('选中候选以完整物料对象回传，由父级写身份', async () => {
    const pc = makePc()
    const row = pc.materialRows[0]
    const w = mountIt(pc)

    w.findComponent(EditableSearchSelect).vm.$emit('select', B)
    await nextTick()

    expect(w.emitted('material-selected')?.[0]).toEqual([row, B])
    expect(row.materialId).toBe(1) // 组件不私改身份，交给 composable
    w.unmount()
  })

  it('显式清空发 material-cleared', async () => {
    const pc = makePc()
    const row = pc.materialRows[0]
    const w = mountIt(pc)

    w.findComponent(EditableSearchSelect).vm.$emit('clear')
    await nextTick()

    expect(w.emitted('material-cleared')?.[0]).toEqual([row])
    w.unmount()
  })

  it('查询失败：给重试入口且不提供新建物料诱导', async () => {
    const pc = makePc()
    const row = pc.materialRows[0]
    row.searchFailed = true
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')

    await input.trigger('focus')
    await input.setValue('不存在的物料')
    await nextTick()

    expect(document.body.querySelector('.ess-create')).toBeNull()

    const retryBtn = Array.from(document.body.querySelectorAll('.mat-failed button')).find((b) =>
      (b.textContent || '').includes('重试')
    ) as HTMLElement
    expect(retryBtn).toBeTruthy()
    retryBtn.click()
    await nextTick()
    const emissions = w.emitted('search-material') as any[][]
    expect(emissions[emissions.length - 1][0]).toBe('铜箔A') // 沿用上次关键词，不是失败时的乱打文字
    w.unmount()
  })

  it('只读场景直接显示材料名称', () => {
    const pc = makePc(false)
    const w = mountIt(pc, true)

    expect(w.text()).toContain('铜箔A')
    expect(w.findComponent(EditableSearchSelect).exists()).toBe(false)
    w.unmount()
  })
})
