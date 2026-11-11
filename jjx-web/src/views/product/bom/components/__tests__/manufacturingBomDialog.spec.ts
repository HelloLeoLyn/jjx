import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  manufacturingOptions: vi.fn(),
  getEngineeringBomInfo: vi.fn(),
  listEngineeringBomItem: vi.fn(),
  saveManufacturingBom: vi.fn(),
  updateManufacturingBom: vi.fn(),
  adoptManufacturingBom: vi.fn(),
  confirm: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/api/product/bom', () => ({
  productBomApi: {
    manufacturingOptions: mocks.manufacturingOptions,
    getEngineeringBomInfo: mocks.getEngineeringBomInfo,
    listEngineeringBomItem: mocks.listEngineeringBomItem,
    saveManufacturingBom: mocks.saveManufacturingBom,
    updateManufacturingBom: mocks.updateManufacturingBom,
    adoptManufacturingBom: mocks.adoptManufacturingBom,
  },
}))
// 权限在真机上由用户快照决定；本用例验证“有权限”与 readonly 两条路径
const permission = vi.hoisted(() => ({ allowed: true }))
vi.mock('@/directives', () => ({ hasPermi: () => permission.allowed }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
    info: vi.fn(),
  },
  ElMessageBox: { confirm: mocks.confirm },
}))
vi.mock('@/components/Selector/BomMaterialSelector.vue', () => ({
  default: { template: '<span class="material-selector-stub" />' },
}))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({
  default: { template: '<span class="material-form-stub" />' },
}))
// 路径条单独有 manufacturingBomPath.spec.ts，这里只关心导航回调
vi.mock('../ManufacturingBomPath.vue', () => ({
  default: defineComponent({
    props: ['nodes'],
    emits: ['navigate'],
    template: '<div class="path-stub" />',
  }),
}))

import ManufacturingBomDialog from '../ManufacturingBomDialog.vue'
import ManufacturingBomItemTable from '../ManufacturingBomItemTable.vue'
import type { EngineeringBomItem } from '@/types/product/bom'

const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })

const item = (over: Partial<EngineeringBomItem> = {}): EngineeringBomItem => ({
  itemId: 11,
  bomId: 124,
  materialId: 109,
  materialCode: 'RM000105',
  materialName: 'PC-800 茶色',
  specification: '195mm*80mm',
  unit: 'M',
  quantity: 0.1667,
  moduleQty: 6,
  baseQty: 1,
  lossRate: 0,
  minIssueQty: 0,
  split: '50mm',
  sortOrder: 1,
  subBomId: null,
  ...over,
})

const scheme = (over: Record<string, unknown> = {}) => ({
  bomId: 124,
  bomCode: 'SFG000345-MFG-A',
  bomName: '中间件制造方案-甲',
  bomVersion: 'V1.0',
  outputType: 'MATERIAL',
  outputId: 349,
  approveStatus: 1,
  isPrimary: true,
  itemCount: 1,
  ...over,
})

const material = {
  materialId: 349,
  materialCode: 'SFG000345',
  materialName: '中间件',
  specification: '70*180mm',
  unit: 'PCS',
}

const mounted: ReturnType<typeof mount>[] = []

async function dialog(props: Record<string, unknown> = {}) {
  const wrapper = mount(ManufacturingBomDialog, {
    props: {
      modelValue: true,
      material,
      productId: 1,
      productCode: 'P0001',
      productName: '成品甲',
      subBomId: 124,
      primaryBomId: 124,
      ...props,
    },
    global: {
      stubs: {
        ElDialog: Slot,
        ElDatePicker: true,
        ElAlert: true,
        ElDescriptions: true,
        ElDescriptionsItem: true,
        ElTooltip: true,
      },
    },
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  permission.allowed = true
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.manufacturingOptions.mockResolvedValue({
    data: [scheme(), scheme({ bomId: 125, bomCode: 'SFG000345-MFG-B', bomName: '中间件制造方案-乙' })],
  })
  mocks.getEngineeringBomInfo.mockImplementation(async (bomId: number) => ({
    data: {
      bomId,
      bomCode: `SFG000345-MFG-${bomId}`,
      bomName: `制造方案${bomId}`,
      bomVersion: 'V1.0',
      approveStatus: 1,
      effectiveDate: '2026-11-11 00:00:00',
      expiryDate: null,
      remark: '',
    },
  }))
  mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
  mocks.saveManufacturingBom.mockResolvedValue({
    data: {
      bomId: 130,
      bomCode: 'NEW-MFG',
      bomName: '新方案',
      bomVersion: 'V1.0',
      outputType: 'MATERIAL',
      outputId: 349,
      approveStatus: 1,
      items: [item({ itemId: 301 })],
    },
  })
  mocks.updateManufacturingBom.mockResolvedValue({
    data: {
      bomId: 124,
      bomCode: 'SFG000345-MFG-A',
      bomName: '中间件制造方案-甲',
      bomVersion: 'V1.0',
      outputType: 'MATERIAL',
      outputId: 349,
      approveStatus: 1,
      items: [item()],
    },
  })
  mocks.adoptManufacturingBom.mockResolvedValue({ data: scheme() })
})

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('制造BOM独立窗口：候选、回显与真实状态', () => {
  it('按 MATERIAL+当前物料+产品上下文拉候选，回显已采用方案与真实版本/状态', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    expect(mocks.manufacturingOptions).toHaveBeenCalledWith({
      outputType: 'MATERIAL',
      outputId: 349,
      productId: 1,
    })
    expect(state.current.options.map((option: any) => option.bomId)).toEqual([124, 125])
    expect(state.current.draft.bomId).toBe(124)
    expect(state.current.draft.effectiveDate).toBe('2026-11-11')
    expect(state.current.subBomId).toBe(124)
    expect(state.statusLabel(1)).toBe('草稿')
    expect(state.statusLabel(3)).toBe('已批准')
    // 候选用真实状态展示（1=草稿），不把草稿伪装成已批准，也不自动审核
    expect(state.current.options.map((option: any) => option.approveStatus)).toEqual([1, 1])
    expect(state.isPrimaryScheme(124)).toBe(true)
    expect(state.isPrimaryScheme(125)).toBe(false)
  })

  it('没有已保存方案时给空白新方案，未保存不能采用、不能设主方案成功', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [] })
    const wrapper = await dialog({ subBomId: null, primaryBomId: null })
    const state = wrapper.vm as any
    expect(state.current.draft.bomId).toBeNull()
    expect(state.canAdopt).toBe(false)
    expect(await state.adoptScheme()).toBe(false)
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith('请先保存制造方案；未保存的方案不能被采用')
  })

  it('只读（无新增/编辑权限）时不保存、不采用，仍可查看候选', async () => {
    permission.allowed = false
    const wrapper = await dialog()
    const state = wrapper.vm as any
    expect(state.current.options).toHaveLength(2)
    expect(state.canAdopt).toBe(false)
    expect(await state.saveScheme()).toBe(false)
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('保存制造BOM：先新增再编辑，不自动发布/不改父BOM', () => {
  it('第一次保存走 POST（产出身份 MATERIAL+物料ID），第二次保存走 PUT 同一 bomId', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [] })
    const wrapper = await dialog({ subBomId: null })
    const state = wrapper.vm as any
    state.current.draft.bomCode = 'SFG000345-MFG-NEW'
    state.current.draft.bomName = '新制造方案'
    state.current.draft.items = [item({ itemId: -1 })]
    await flushPromises()

    expect(await state.saveScheme()).toBe(true)
    expect(mocks.saveManufacturingBom).toHaveBeenCalledTimes(1)
    const created = mocks.saveManufacturingBom.mock.calls[0][0]
    expect(created).toMatchObject({
      bomCode: 'SFG000345-MFG-NEW',
      bomName: '新制造方案',
      outputType: 'MATERIAL',
      outputId: 349,
      effectiveDate: null,
      expiryDate: null,
    })
    expect(created.bomId).toBeUndefined()
    expect(state.current.draft.bomId).toBe(130)
    expect(state.current.draft.items[0].itemId).toBe(301)

    state.current.draft.remark = '第二次保存'
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.updateManufacturingBom).toHaveBeenCalledTimes(1)
    expect(mocks.updateManufacturingBom.mock.calls[0][0]).toBe(130)
    expect(mocks.saveManufacturingBom).toHaveBeenCalledTimes(1)
    // 只落方案：没有发布/批准/父BOM保存调用
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
  })

  it('缺少方案编码/名称或没有投入行时拦在保存前，不发请求', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [] })
    const wrapper = await dialog({ subBomId: null })
    const state = wrapper.vm as any
    state.current.draft.bomCode = ''
    expect(await state.saveScheme()).toBe(false)
    state.current.draft.bomCode = 'X-MFG'
    state.current.draft.items = []
    expect(await state.saveScheme()).toBe(false)
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('采用方案：只回填父草稿，不落库、不设主方案', () => {
  it('采用已保存方案只调用 adopt 并发出 adopt 事件', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    expect(await state.adoptScheme()).toBe(true)
    expect(mocks.adoptManufacturingBom).toHaveBeenCalledWith(124)
    const payload = wrapper.emitted('adopt')?.at(-1)?.[0] as any
    expect(payload).toMatchObject({ materialId: 349, bomId: 124 })
    expect(payload.scheme.bomCode).toBe('SFG000345-MFG-A')
    // 采用不写主方案、不保存父 BOM
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
    expect(wrapper.emitted('primary-change')).toBeUndefined()
    expect(state.current.subBomId).toBe(124)
    expect(mocks.success).toHaveBeenCalledWith('已采用该制造方案；保存产品BOM后才会持久化关联')
  })

  it('有未保存修改时先提示保存，不直接采用', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.remark = '改了备注'
    expect(await state.adoptScheme()).toBe(false)
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith('请先保存当前修改，再采用该方案')
  })
})

describe('主方案：product×material 显式调整', () => {
  it('没有可靠产品ID时提示先保存产品，不发事件', async () => {
    const wrapper = await dialog({ productId: null, productCode: '', productName: '' })
    const state = wrapper.vm as any
    state.markPrimary(scheme())
    expect(wrapper.emitted('primary-change')).toBeUndefined()
    expect(mocks.warning).toHaveBeenCalledWith(
      '当前没有可靠的产品ID：请先选择并保存产品，再设置主方案'
    )
    expect(mocks.manufacturingOptions).toHaveBeenCalledWith({
      outputType: 'MATERIAL',
      outputId: 349,
      productId: undefined,
    })
  })

  it('显式“设为主方案”只发事件，不调用任何写接口', async () => {
    const wrapper = await dialog({ primaryBomId: null })
    const state = wrapper.vm as any
    expect(state.isPrimaryScheme(124)).toBe(false)
    state.markPrimary(scheme({ bomId: 125 }))
    expect(wrapper.emitted('primary-change')?.at(-1)?.[0]).toEqual({
      materialId: 349,
      bomId: 125,
    })
    expect(state.isPrimaryScheme(125)).toBe(true)
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('切换/关闭的未保存处理', () => {
  it('未保存时切换方案：X 或“继续编辑”不切换，“放弃修改”才加载目标方案', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.bomName = '未保存改名'
    mocks.confirm.mockRejectedValue('close')
    await state.selectSchemeByBomId(125)
    expect(state.current.selectedBomId).toBe(124)

    mocks.confirm.mockRejectedValue('cancel')
    await state.selectSchemeByBomId(125)
    expect(state.current.selectedBomId).toBe(125)
    expect(mocks.getEngineeringBomInfo).toHaveBeenLastCalledWith(125)
  })

  it('未保存时关闭：选“继续编辑”不关闭，选“放弃修改”关闭且不发保存/删除请求', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.remark = '未保存'
    mocks.confirm.mockRejectedValue('close')
    expect(await state.resolveLeave()).toBe(false)

    mocks.confirm.mockRejectedValue('cancel')
    expect(await state.resolveLeave()).toBe(true)
    await state.handleCancel()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('下层显式引用与返回：保护上层草稿', () => {
  it('只对挂了 subBomId 的行下钻；下钻按该行物料重新拉候选；返回后上层草稿保留', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.bomName = '父层未保存'
    await state.drillDown(item({ subBomId: 200, sourceType: 'make' }))
    expect(state.stack).toHaveLength(2)
    expect(state.current.material.materialId).toBe(109)
    expect(state.current.subBomId).toBe(200)
    expect(mocks.manufacturingOptions).toHaveBeenLastCalledWith({
      outputType: 'MATERIAL',
      outputId: 109,
      productId: 1,
    })
    expect(state.pathNodes).toHaveLength(2)

    await state.handlePathNavigate(0)
    expect(state.stack).toHaveLength(1)
    expect(state.current.draft.bomName).toBe('父层未保存')
    expect(state.currentDirty).toBe(true)
  })

  it('没有 subBomId 的行不进入下层（不从分组/规格猜制造关系）', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    await state.drillDown(item({ subBomId: null }))
    expect(state.stack).toHaveLength(1)
  })
})

describe('投入行合法小数与旧数据承接', () => {
  it('模数/基数允许正小数（0.5/2.5），拒绝 0 与超过4位小数；跳距旧值保留', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    state.current.draft.items = [item({ moduleQty: 0.5, baseQty: 2.5, split: '75mm' })]
    await flushPromises()
    expect((table.vm as any).validate()).toBe('')

    state.current.draft.items[0].moduleQty = 0
    expect((table.vm as any).validate()).toBe('第1行模数必须大于 0')
    state.current.draft.items[0].moduleQty = 1.23456
    expect((table.vm as any).validate()).toBe('第1行模数最多 4 位小数')
    state.current.draft.items[0].moduleQty = 6
    state.current.draft.items[0].baseQty = 0
    expect((table.vm as any).validate()).toBe('第1行基数必须大于 0')
    state.current.draft.items[0].baseQty = 1
    state.current.draft.items[0].lossRate = 120
    expect((table.vm as any).validate()).toBe('第1行损耗率须在 0~100 之间')
    state.current.draft.items[0].lossRate = 0

    // 旧跳距原样回显/提交，不因新增窗口丢字段
    expect(state.current.draft.items[0].split).toBe('75mm')
    const payload = state.buildPayload()
    expect(payload.items[0].split).toBe('75mm')
    expect(payload.items[0].moduleQty).toBe(6)
    expect(payload.items[0].baseQty).toBe(1)
  })

  it('投入行必须有真实物料身份与单位，未建档行不许保存', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    state.current.draft.items = [item({ materialId: 0, materialCode: '', materialName: '' })]
    await flushPromises()
    expect((table.vm as any).validate()).toBe('第1行请选择投入物料')
  })
})
