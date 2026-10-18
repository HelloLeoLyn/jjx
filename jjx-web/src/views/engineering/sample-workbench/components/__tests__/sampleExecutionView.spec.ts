/**
 * 打样执行视图定向测试（dev-20261018-008，公共契约 E/F/J）
 *
 * 覆盖：
 * 1）组装卡片与印刷行都在执行视图展示，分别调用 advancePlan / advancePrint；
 * 2）已完成工序不出现推进操作；
 * 3）readonly 不出现推进、不良新增/删除与标记样品完成能力，查看图纸仍可用；
 * 4）阻断原因直接可见且推进按钮禁用（不能绕过）；
 * 5）view-files / mark-ready 只发事件，不调业务写接口；
 * 6）compact 不良记录默认折叠、展开可查，orderId/roundNo 归属不变；非 compact 保持原行为。
 *
 * 运行（内存笼子，禁止裸跑）：
 *   flock /tmp/jjx-sample-validation.lock \
 *     systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- \
 *     timeout 120 npx vitest run src/views/engineering/sample-workbench/components/__tests__/sampleExecutionView.spec.ts --maxWorkers=1
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { SampleProcessStatusEnum } from '@/enums/sales'
import SampleExecutionView from '../SampleExecutionView.vue'
import DefectRecordPanel from '../DefectRecordPanel.vue'

const api = vi.hoisted(() => ({
  listDefects: vi.fn(),
  addDefect: vi.fn(),
  deleteDefect: vi.fn(),
}))

vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))

// Element Plus 样式在 jsdom 单测里无意义；仓库 vitest.config.ts 未把 element-plus 置为
// server.deps.inline，外部化依赖的 style/css 会走 Node 加载器报 Unknown file extension ".css"。
// 这里逐个屏蔽自动导入注入的样式入口（行为断言仍挂真实组件，不弱化）。
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

// Element Plus 部分组件在 jsdom 下需要 ResizeObserver / matchMedia
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub)
vi.stubGlobal(
  'matchMedia',
  vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }),
)

function planCard(over: Record<string, any> = {}) {
  return {
    uid: 'plan-1',
    items: [{ processName: '冲型' }],
    status: SampleProcessStatusEnum.TODO.value,
    operator: '',
    startTime: null,
    endTime: null,
    durationMinutes: null,
    processNote: '',
    materials: null,
    processOrder: 10,
    isPrint: false,
    advancing: false,
    raw: {},
    ...over,
  }
}

function printRow(over: Record<string, any> = {}) {
  return {
    processId: 101,
    printName: '丝印',
    status: SampleProcessStatusEnum.TODO.value,
    processOrder: 20,
    colorNo: 'PANTONE 186C',
    ...over,
  }
}

function mountView(props: Record<string, any> = {}) {
  return mount(SampleExecutionView, {
    props: {
      readonly: false,
      orderId: 8801,
      roundNo: 2,
      planList: [],
      printList: [],
      formatTime: (t?: string) => (t ? `T:${t}` : ''),
      parseMaterials: (json?: string | null) => (json ? JSON.parse(json) : []),
      saving: false,
      ...props,
    },
  })
}

function advanceButtons(wrapper: ReturnType<typeof mountView>) {
  return wrapper
    .findAll('button')
    .filter((b) => /▶ 开始|✓ 完成/.test(b.text()))
}

beforeEach(() => {
  vi.clearAllMocks()
  api.listDefects.mockResolvedValue({ data: [] })
  api.addDefect.mockResolvedValue({ data: {} })
  api.deleteDefect.mockResolvedValue({ data: {} })
})

describe('SampleExecutionView 打样执行视图', () => {
  it('组装与印刷工序都展示，并分别调用 advancePlan / advancePrint', async () => {
    const advancePlan = vi.fn()
    const advancePrint = vi.fn()
    const plan = planCard()
    const print = printRow()
    const wrapper = mountView({
      planList: [plan],
      printList: [print],
      advancePlan,
      advancePrint,
    })
    await flushPromises()

    expect(wrapper.text()).toContain('冲型')
    expect(wrapper.text()).toContain('丝印')

    const buttons = advanceButtons(wrapper)
    expect(buttons).toHaveLength(2)

    // 按 processOrder：组装卡片在前，印刷行在后，各自走原回调
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(advancePlan).toHaveBeenCalledTimes(1)
    expect(advancePlan).toHaveBeenCalledWith(expect.objectContaining({ uid: 'plan-1' }))
    expect(advancePrint).toHaveBeenCalledTimes(1)
    expect(advancePrint).toHaveBeenCalledWith(expect.objectContaining({ processId: 101 }))
  })

  it('已完成工序不出现推进操作', async () => {
    const wrapper = mountView({
      planList: [planCard({ status: SampleProcessStatusEnum.DONE.value })],
      printList: [printRow({ status: SampleProcessStatusEnum.DONE.value })],
    })
    await flushPromises()

    expect(wrapper.text()).toContain('已完成 2 / 2')
    expect(advanceButtons(wrapper)).toHaveLength(0)
  })

  it('readonly 不出现推进、不良新增/删除与标记样品完成能力', async () => {
    const wrapper = mountView({
      readonly: true,
      planList: [planCard()],
      printList: [printRow()],
    })
    await flushPromises()

    expect(advanceButtons(wrapper)).toHaveLength(0)
    expect(wrapper.text()).not.toContain('标记样品完成')
    expect(wrapper.text()).not.toContain('新增')
    expect(wrapper.text()).toContain('查看图纸')
  })

  it('阻断原因直接可见且推进按钮禁用（不能绕过）', async () => {
    const wrapper = mountView({
      planList: [planCard()],
      printList: [printRow()],
      execBlockReason: () => '工序草稿未保存',
      printBlockReason: () => '印刷行未保存',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('工序草稿未保存')
    expect(wrapper.text()).toContain('印刷行未保存')
    const buttons = advanceButtons(wrapper)
    expect(buttons).toHaveLength(2)
    buttons.forEach((b) => expect(b.attributes('disabled')).toBeDefined())
  })

  it('view-files / mark-ready 只发事件，不调业务写接口', async () => {
    const wrapper = mountView({ planList: [planCard()] })
    await flushPromises()

    const filesBtn = wrapper.findAll('button').find((b) => b.text().includes('查看图纸'))
    const readyBtn = wrapper.findAll('button').find((b) => b.text().includes('标记样品完成'))
    expect(filesBtn).toBeTruthy()
    expect(readyBtn).toBeTruthy()

    await filesBtn!.trigger('click')
    await readyBtn!.trigger('click')

    expect(wrapper.emitted('view-files')).toHaveLength(1)
    expect(wrapper.emitted('mark-ready')).toHaveLength(1)
    expect(api.addDefect).not.toHaveBeenCalled()
    expect(api.deleteDefect).not.toHaveBeenCalled()
  })

  it('compact 不良记录默认折叠、展开可查，新增归属保持 orderId/roundNo', async () => {
    api.listDefects.mockResolvedValue({
      data: [
        {
          id: 1,
          craftType: 'PRINT',
          defectReason: '拉丝',
          improvement: '调压',
          recorderName: '张三',
          recordDate: '2026-10-18',
        },
      ],
    })
    const wrapper = mountView({ planList: [planCard()] })
    await flushPromises()

    // 样品单级：列表只按 orderId 取，不按轮次筛选
    expect(api.listDefects).toHaveBeenCalledWith(8801)

    // 默认折叠：历史记录不渲染，入口可发现
    expect(wrapper.text()).not.toContain('拉丝')
    const toggle = wrapper.findAll('button').find((b) => b.text().includes('展开历史记录'))
    expect(toggle).toBeTruthy()
    expect(toggle!.text()).toContain('展开历史记录（1）')

    await toggle!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('拉丝')

    // 新增仍按订单 + 轮次归属，请求链不变
    const reasonInput = wrapper.find('input[placeholder="不良原因"]')
    expect(reasonInput.exists()).toBe(true)
    await reasonInput.setValue('气泡')
    const addBtn = wrapper.findAll('button').find((b) => b.text() === '新增')
    expect(addBtn).toBeTruthy()
    await addBtn!.trigger('click')
    await flushPromises()

    expect(api.addDefect).toHaveBeenCalledTimes(1)
    expect(api.addDefect).toHaveBeenCalledWith(
      8801,
      expect.objectContaining({ defectReason: '气泡', roundNo: 2 }),
    )
  })

  it('DefectRecordPanel 默认非 compact 行为不变：历史直接展示、无折叠入口', async () => {
    api.listDefects.mockResolvedValue({
      data: [
        {
          id: 2,
          craftType: 'PUNCH',
          defectReason: '毛边',
          improvement: '换刀',
          recorderName: '李四',
          recordDate: '2026-10-18',
        },
      ],
    })
    const wrapper = mount(DefectRecordPanel, { props: { orderId: 8801, roundNo: 2 } })
    await flushPromises()

    expect(wrapper.text()).toContain('毛边')
    expect(wrapper.text()).not.toContain('展开历史记录')
  })
})
