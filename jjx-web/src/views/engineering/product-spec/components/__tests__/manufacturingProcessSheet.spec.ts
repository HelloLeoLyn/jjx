vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))

import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ManufacturingProcessSheet from '../ManufacturingProcessSheet.vue'
import { getManufacturingSnapshot } from '@/api/production/manufacturingSnapshot'

vi.mock('@/api/production/manufacturingSnapshot', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/production/manufacturingSnapshot')>()
  return { ...actual, getManufacturingSnapshot: vi.fn() }
})

const mockedSnapshot = vi.mocked(getManufacturingSnapshot)

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  mockedSnapshot.mockReset()
})

async function mountSheet(orderId = 21) {
  const wrapper = mount(ManufacturingProcessSheet, {
    props: { orderId },
    global: { directives: { loading: () => {} } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}

function tables(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAllComponents({ name: 'ElTable' })
}

function completeSnapshot() {
  return {
    orderId: 21,
    orderNo: 'MO20261111001',
    productCode: 'JTT993MHML',
    productName: '面板标贴',
    plannedQuantity: 1200,
    bomId: 31,
    bomCode: 'MBOM-31',
    routingId: 41,
    routingCode: 'RT-41',
    routingVersion: 'A',
    workSpecVersionId: 51,
    workSpecVersionNo: 'V3',
    snapshotStatus: 'COMPLETE',
    depth: 32,
    nodes: [
      {
        level: 1,
        materialCode: 'M-001',
        materialName: '面板标贴成品',
        unit: '张',
        quantity: 1200,
        moduleQty: 1,
        lossRate: 2,
        snapshotSource: 'BOM_ITEM',
        children: [
          {
            level: 2,
            materialCode: 'M-002',
            materialName: 'PET 片材',
            unit: '张',
            quantity: 1224,
            moduleQty: 1.02,
            lossRate: 2,
            snapshotSource: 'BOM_ITEM',
            children: [],
          },
          {
            level: 2,
            materialCode: 'M-003',
            materialName: '离型纸',
            unit: '张',
            quantity: 1224,
            moduleQty: 1.02,
            lossRate: 2,
            snapshotSource: 'SUB_BOM_MISSING',
            children: [],
          },
        ],
      },
    ],
    routingSteps: [
      { processOrder: 1, processName: '印刷', workflowSeq: 1, workInstruction: '印红' },
      { processOrder: 2, processName: '模切', workflowSeq: 2, workInstruction: '' },
    ],
    executionSteps: [
      { executionId: 801, processOrder: 1, processName: '印刷', executionStatus: 4 },
      { executionId: 802, processOrder: 2, processName: '模切', executionStatus: 2 },
    ],
    warnings: [],
  }
}

describe('作业规范生产资料展示：制造历史快照', () => {
  it('COMPLETE：展示 BOM 多层材料耗用、路线工序、工单实际工序与作业规范版本号', async () => {
    mockedSnapshot.mockResolvedValue({ code: 200, msg: 'ok', data: completeSnapshot() } as any)

    const wrapper = await mountSheet()

    expect(mockedSnapshot).toHaveBeenCalledWith(21, 32)
    expect(wrapper.find('.partial-alert').exists()).toBe(false)
    expect(wrapper.text()).toContain('快照完整')
    expect(wrapper.text()).toContain('MO20261111001')
    expect(wrapper.text()).toContain('MBOM-31')
    expect(wrapper.text()).toContain('RT-41')
    // 作业规范版本号取工单绑定的历史版本
    expect(wrapper.text()).toContain('V3')

    const [bomTable, routingTable, executionTable] = tables(wrapper)
    const bomRows = bomTable.props('data') as any[]
    expect(bomRows).toHaveLength(3)
    expect(bomRows.map((row) => row.depth)).toEqual([0, 1, 1])
    expect(bomRows.map((row) => row.node.materialCode)).toEqual(['M-001', 'M-002', 'M-003'])
    // 子 BOM 缺失只在数据来源列标注，不静默替换成最新档案
    expect(wrapper.text()).toContain('子BOM缺失')

    expect(routingTable.props('data')).toHaveLength(2)
    expect(wrapper.text()).toContain('印刷')
    expect(wrapper.text()).toContain('印红')

    const executionRows = executionTable.props('data') as any[]
    expect(executionRows).toHaveLength(2)
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('执行中')
  })

  it('SNAPSHOT_PARTIAL：显示告警与 warnings 列表，且不使用最新版本顶替', async () => {
    mockedSnapshot.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: {
        orderId: 21,
        orderNo: 'MO20261111001',
        productName: '面板标贴',
        bomCode: 'MBOM-31',
        bomId: 31,
        routingId: null,
        routingCode: null,
        workSpecVersionId: 50,
        workSpecVersionNo: 'V2',
        snapshotStatus: 'SNAPSHOT_PARTIAL',
        depth: 32,
        nodes: [
          {
            level: 1,
            materialCode: 'M-001',
            materialName: '面板标贴成品',
            quantity: 1200,
            snapshotSource: 'BOM_ITEM',
            children: [],
          },
        ],
        routingSteps: [],
        executionSteps: [],
        warnings: ['工单未绑定工艺路线'],
      },
    } as any)

    const wrapper = await mountSheet()

    const alert = wrapper.find('.partial-alert')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('SNAPSHOT_PARTIAL')
    expect(wrapper.find('.warning-list').text()).toContain('工单未绑定工艺路线')
    expect(wrapper.text()).toContain('快照不完整')
    // 历史规范版本仍按工单绑定值展示，没有替换成“最新版本”
    expect(wrapper.text()).toContain('V2')
    expect(wrapper.text()).not.toContain('V3')
    // 只读：不出现任何写入动作
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('缺失 nodes：BOM 材料表显示空态，不报错也不凭空造行', async () => {
    mockedSnapshot.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: {
        orderId: 21,
        orderNo: 'MO20261111001',
        bomId: null,
        bomCode: null,
        workSpecVersionNo: 'V1',
        snapshotStatus: 'SNAPSHOT_PARTIAL',
        depth: 32,
        nodes: [],
        routingSteps: [],
        executionSteps: [],
        warnings: ['工单未绑定生产BOM，无法展示材料耗用'],
      },
    } as any)

    const wrapper = await mountSheet()

    expect(wrapper.text()).toContain('该工单快照没有 BOM 材料行')
    expect(wrapper.findComponent({ name: 'ElEmpty' }).exists()).toBe(true)
    expect(tables(wrapper)).toHaveLength(0)
    expect(wrapper.text()).toContain('工单未绑定生产BOM，无法展示材料耗用')
    expect(wrapper.find('.sheet-error').exists()).toBe(false)
  })
})
