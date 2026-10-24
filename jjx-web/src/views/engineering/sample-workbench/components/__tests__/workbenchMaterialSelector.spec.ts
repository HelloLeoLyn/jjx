/** dev-20261024-013：BOM/印刷入口挂载真实公共选择器，验证PET 008请求、回填及取消语义。 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, reactive } from 'vue'
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
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/api/sales/sampleOrder', () => ({ getProcessHistory: vi.fn().mockResolvedValue({ data: {} }) }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { name: 'MaterialFormDialog', template: '<div />' } }))
vi.mock('@/components/print/PrintFieldAutocomplete.vue', () => ({ default: { template: '<div />' } }))
import { materialApi } from '@/api/inventory/material'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import EditableSearchSelect from '@/components/Selector/EditableSearchSelect.vue'
import BomPanel from '../BomPanel.vue'
import PrintProcessPanel from '../PrintProcessPanel.vue'
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


const PET = { materialId: 11, materialName: 'PET', specification: '008', materialCode: 'MAT-PET', unit: 'M' }
const wrappers: any[] = []
const stubs = { ElTable: ElTableStub, ElTableColumn: ElTableColumnStub,
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue" class="dialog"><slot /><slot name="footer" /></div>' } }
beforeEach(() => { vi.useFakeTimers(); vi.mocked(materialApi.search).mockReset().mockResolvedValue({ data: { records: [PET], total: 1 } } as any) })
afterEach(() => { wrappers.forEach(w => w.unmount()); wrappers.length = 0; vi.useRealTimers(); document.body.innerHTML = '' })

async function searchAndPick(wrapper: any) {
  const selector = wrapper.getComponent(BomMaterialSelector)
  const input = selector.get('input')
  await input.trigger('focus'); await input.setValue('PET 008')
  await vi.advanceTimersByTimeAsync(450)
  expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: 'PET 008', pageNum: 1, pageSize: 10 })
  ;(document.body.querySelector('.ess-option') as HTMLElement).click()
  await nextTick()
  return selector
}
function mountPrint(row: any) {
  const wrapper = mount(PrintProcessPanel, { attachTo: document.body,
    props: { printList: [row], savingPlan: false, parseMaterials: (json) => JSON.parse(json || '[]'),
      addPrintRow: vi.fn(), removePrintRow: vi.fn(), movePrintRow: vi.fn(), savePlan: vi.fn() },
    global: { stubs, directives: { hasPermi: {} } } })
  wrappers.push(wrapper)
  return wrapper
}

describe('BOM物料清单公共搜索', () => {
  it('PET 008发统一keyword，选中回传完整物料；清空回传目标行且不新增建档入口', async () => {
    const row = { uid: 'bom-1', materialId: 1, name: '旧物料', spec: '旧规格', unit: 'M', baseQty: 4, moduleQty: 2 }
    const wrapper = mount(BomPanel, { attachTo: document.body, props: { bomList: [row], projects: [] }, global: { stubs } })
    wrappers.push(wrapper)
    const selector = await searchAndPick(wrapper)
    expect(wrapper.emitted('select')?.[0]).toEqual([row, PET])
    expect(row.materialId).toBe(1)
    expect(selector.props('allowCreate')).toBe(false)
    selector.getComponent(EditableSearchSelect).vm.$emit('clear')
    expect(wrapper.emitted('clear')?.[0]).toEqual([row])
    await wrapper.setProps({ readonly: true })
    expect(wrapper.findComponent(BomMaterialSelector).exists()).toBe(false)
  })
})

describe('印刷添加材料公共搜索', () => {
  it('搜索选择不写工序，点击添加才写名称规格编码单位；关闭重开不保留上次候选', async () => {
    const row = reactive({ uid: 'print-1', category: 'PANEL', printName: '丝印', materials: null as string | null, saveState: 'synced' })
    const wrapper = mountPrint(row)
    ;(wrapper.vm as any).openMaterialPicker(row)
    await nextTick()
    await searchAndPick(wrapper)
    expect(row.materials).toBeNull()
    ;(wrapper.vm as any).confirmMaterial()
    expect(JSON.parse(row.materials!)[0]).toMatchObject({ name: 'PET', spec: '008', materialId: 11, materialCode: 'MAT-PET', unit: 'M' })
    expect(row.saveState).toBe('dirty')
    await nextTick()
    ;(wrapper.vm as any).openMaterialPicker(row)
    await nextTick()
    ;(wrapper.vm as any).confirmMaterial()
    expect(JSON.parse(row.materials!)).toHaveLength(1)
  })
  it('公共建档预填关键词并回填候选；取消弹窗不写入原工序', async () => {
    const row = reactive({ uid: 'print-1', category: 'PANEL', printName: '丝印', materials: null, saveState: 'synced' })
    const wrapper = mountPrint(row)
    ;(wrapper.vm as any).openMaterialPicker(row)
    await nextTick()
    vi.mocked(materialApi.search).mockResolvedValueOnce({ data: { records: [], total: 0 } } as any)
    const selector = wrapper.getComponent(BomMaterialSelector)
    await selector.get('input').trigger('focus'); await selector.get('input').setValue('新PET 008')
    await vi.advanceTimersByTimeAsync(450)
    ;(document.body.querySelector('.ess-create') as HTMLElement).click()
    await nextTick()
    expect((wrapper.vm as any).matFormPreset).toEqual({ materialName: '新PET 008', unit: 'PCS' })
    ;(wrapper.vm as any).onMaterialCreated(PET)
    await nextTick()
    expect(wrapper.getComponent(BomMaterialSelector).props('materialId')).toBe(11)
    expect(row.materials).toBeNull()
    const cancel = wrapper.findAll('button').find(button => button.text() === '取消')!
    await cancel.trigger('click')
    expect(row.materials).toBeNull()
  })
})
