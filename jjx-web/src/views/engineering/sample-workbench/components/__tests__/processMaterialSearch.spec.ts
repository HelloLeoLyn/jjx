/**
 * 打样工序材料输入框行为测试（dev-20261018-035）
 *
 * 覆盖：
 * 1）已选材料在输入框内显示名称+规格+编码，不显示裸ID；
 * 2）输入文字由公共选择器按keyword搜索，不改该行 materialId / 快照；
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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
import { materialApi } from '@/api/inventory/material'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import ProcessMaterialEditor from '../ProcessMaterialEditor.vue'
import { ElInputNumber } from 'element-plus'
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

beforeEach(() => { vi.useFakeTimers(); vi.mocked(materialApi.search).mockReset() })
afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

describe('ProcessMaterialEditor 材料可编辑搜索', () => {
  it('已选材料显示名称而不是裸ID，并复用公共可编辑搜索框', () => {
    const pc = makePc()
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')

    expect((input.element as HTMLInputElement).value).toBe('铜箔A · 35um (MAT-A)')
    w.unmount()
  })

  it('PET 008使用公共keyword搜索，不改该行 materialId 与档案快照', async () => {
    const pc = makePc()
    const row = pc.materialRows[0]
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')

    vi.mocked(materialApi.search).mockResolvedValueOnce({ data: { records: [B], total: 1 } } as any)
    await input.trigger('focus')
    await input.setValue('PET  008')
    await vi.advanceTimersByTimeAsync(450)
    expect(w.findComponent(BomMaterialSelector).exists()).toBe(true)
    expect(materialApi.search).toHaveBeenCalledExactlyOnceWith({ keyword: 'PET 008', pageNum: 1, pageSize: 10 })
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

  it('查询失败给公共重试入口且不提供新建物料，重试沿用当前关键词', async () => {
    const pc = makePc()
    const w = mountIt(pc)
    const input = w.findComponent(EditableSearchSelect).find('input')
    vi.mocked(materialApi.search).mockRejectedValueOnce(new Error('网络失败'))
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    await input.trigger('focus')
    await input.setValue('PET 008')
    await vi.advanceTimersByTimeAsync(450)
    expect(document.body.querySelector('.ess-create')).toBeNull()
    const retryBtn = Array.from(document.body.querySelectorAll('button')).find(b => b.textContent?.includes('重试'))!
    expect(retryBtn).toBeTruthy()
    vi.mocked(materialApi.search).mockResolvedValueOnce({ data: { records: [B], total: 1 } } as any)
    retryBtn.click()
    await vi.advanceTimersByTimeAsync(450)
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: 'PET 008', pageNum: 1, pageSize: 10 })
    expect(pc.materialRows[0].materialId).toBe(1)
    w.unmount(); log.mockRestore()
  })

  it('公共建档入口把关键词和目标行交给原有建档流程', async () => {
    const pc = makePc()
    const w = mountIt(pc)
    vi.mocked(materialApi.search).mockResolvedValueOnce({ data: { records: [], total: 0 } } as any)
    const input = w.get('input')
    await input.trigger('focus'); await input.setValue('新PET')
    await vi.advanceTimersByTimeAsync(450)
    ;(document.body.querySelector('.ess-create') as HTMLElement).click()
    expect(w.emitted('create-material')?.[0]).toEqual([pc.materialRows[0]])
    expect(pc.materialRows[0]._matKw).toBe('新PET')
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


describe('打样工序跳距输入', () => {
  it('输入整数以mm写入原草稿，清空不补默认值', async () => {
    const pc: any = makePc()
    pc.materialRows[0].split = '50mm'
    const w = mountIt(pc)
    const input = w.findAllComponents(ElInputNumber).find(input => input.props('ariaLabel') === '跳距（mm）')!
    expect(input.props('modelValue')).toBe(50)
    input.vm.$emit('update:modelValue', 60)
    await nextTick()
    expect(pc.materialRows[0].split).toBe('60mm')
    expect(pc.materialRows[0].qty).toBe(2.5)
    input.vm.$emit('update:modelValue', undefined)
    await nextTick()
    expect(pc.materialRows[0].split).toBe('')
    w.unmount()
  })
  it('历史只读显示原跳距，未填显示横线', () => {
    const pc = makePc(false)
    pc.materials = JSON.stringify([{ name: '铜', split: '50mm' }, { name: '胶' }])
    const w = mountIt(pc, true)
    expect(w.text()).toContain('50mm')
    expect(w.text()).toContain('—')
    expect(w.findAllComponents(ElInputNumber)).toHaveLength(0)
    w.unmount()
  })
})
