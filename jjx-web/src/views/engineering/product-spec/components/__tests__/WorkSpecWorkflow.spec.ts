vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))

import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RouteProcessTable from '@/views/product/route/components/RouteProcessTable.vue'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('../ProductPrintSpecPanel.vue', () => ({ default: { template: '<div />' } }))

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

function mountDetails(items: any[]) {
  const wrapper=mount(RouteProcessTable, { props: { items }, global: { directives: { loading: () => {} } } })
  wrappers.push(wrapper)
  return wrapper
}
function pane(wrapper: ReturnType<typeof mount>, name: string) {
  return wrapper.findAllComponents({ name: 'ElTabPane' }).find(component => component.props('name') === name)!
}

describe('工艺路线详情作业流程：完整显示跨分类组合', () => {
  it('JTT993MHML面板第6道同时渲染面板和下线图标、作业说明，不挪到下线页签', async () => {
    const items: any[] = Array.from({ length:5 }, (_, index) => ({
      itemId:index+1,majorCategory:'ASSEMBLY',processCategory:'PANEL',processOrder:index+1,processName:`普通工序${index+1}`,
    }))
    items.push({ itemId:58,majorCategory:'ASSEMBLY',processCategory:'PANEL',processOrder:6,processName:'面板+下线',children:[
      {itemId:59,parentId:58,majorCategory:'ASSEMBLY',processCategory:'PANEL',processName:'面板',icon:'面板'},
      {itemId:60,parentId:58,majorCategory:'ASSEMBLY',processCategory:'DOWN_LINE',processName:'下线',icon:'下线',workInstruction:'11'},
    ] })
    const before=JSON.stringify(items)
    const wrapper=mountDetails(items)
    await flushPromises()
    const panel=pane(wrapper,'PANEL')
    const rows=panel.findComponent({name:'ElTable'}).props('data')
    expect(rows).toHaveLength(6)
    expect(rows[5].children.map((row:any)=>row.itemId)).toEqual([59,60])
    expect(panel.text()).toContain('面板 ＋ 下线')
    expect(panel.findAll('use').map(icon=>icon.attributes('href'))).toEqual(['#icon-面板','#icon-下线'])
    expect(panel.find('sub').text()).toBe('11')
    expect(pane(wrapper,'DOWN_LINE').findComponent({name:'ElTable'}).props('data')).toHaveLength(0)
    expect(JSON.stringify(items)).toBe(before)
  })

  it('下线组合保留垫片，印刷子项仍从冲型组装视图分离', async () => {
    const wrapper=mountDetails([{itemId:82,majorCategory:'ASSEMBLY',processCategory:'DOWN_LINE',processName:'下线+下线隔片+垫片',children:[
      {itemId:83,processCategory:'DOWN_LINE',processName:'下线',icon:'下线'},
      {itemId:84,processCategory:'DOWN_LINE',processName:'下线隔片',icon:'下线隔片'},
      {itemId:85,processCategory:'PANEL',processName:'垫片',icon:'垫片'},
      {itemId:86,majorCategory:'PRINT',processCategory:'UP_LINE',processName:'印红',icon:'印红'},
    ]}] as any)
    await flushPromises()
    const down=pane(wrapper,'DOWN_LINE')
    expect(down.findComponent({name:'ElTable'}).props('data')[0].children.map((row:any)=>row.itemId)).toEqual([83,84,85])
    expect(down.text()).toContain('下线 ＋ 下线隔片 ＋ 垫片')
    expect(down.findAll('use').map(icon=>icon.attributes('href'))).toEqual(['#icon-下线','#icon-下线隔片','#icon-垫片'])
    expect(down.text()).not.toContain('印红')
    expect(pane(wrapper,'PANEL').findComponent({name:'ElTable'}).props('data')).toHaveLength(0)
  })
})
