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
  acquireBomEditSession: vi.fn(),
  renewBomEditSession: vi.fn(),
  releaseBomEditSession: vi.fn(),
  describeBomEditSession: vi.fn(),
  materialInfo: vi.fn(),
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
    acquireBomEditSession: mocks.acquireBomEditSession,
    renewBomEditSession: mocks.renewBomEditSession,
    releaseBomEditSession: mocks.releaseBomEditSession,
    describeBomEditSession: mocks.describeBomEditSession,
  },
}))
vi.mock('@/api/inventory/material', () => ({
  materialApi: { getInfo: mocks.materialInfo },
}))
// 权照矩阵：按权限码集合判定，覆盖 view-only / add-only / edit-only / 双权限
const permission = vi.hoisted(() => ({ perms: new Set<string>() }))
vi.mock('@/directives', () => ({
  hasPermi: (perms: string | string[]) => {
    const list = Array.isArray(perms) ? perms : [perms]
    return list.some((perm) => permission.perms.has(perm))
  },
}))
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

/** 物料档案桩：可按键覆盖；默认空单位（未读到时按后端校验，不猜关系） */
const archives = new Map<number, Record<string, string>>()

const mounted: ReturnType<typeof mount>[] = []

async function dialog(props: Record<string, unknown> = {}) {
  const wrapper = mount(ManufacturingBomDialog, {
    props: {
      modelValue: true,
      material,
      productId: 1,
      productCode: 'P0001',
      productName: '成品甲',
      subBomId: null,
      primaryBomId: null,
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
  permission.perms = new Set(['engineering:bom:add', 'engineering:bom:edit'])
  Object.values(mocks).forEach((mock) => mock.mockReset())
  archives.clear()
  let bomIdSeq = 130
  mocks.manufacturingOptions.mockResolvedValue({ data: [] })
  mocks.getEngineeringBomInfo.mockImplementation(async (bomId: number) => ({
    data: {
      bomId,
      bomCode: `MFG-${bomId}`,
      bomName: `制造方案${bomId}`,
      bomVersion: 'V1.0',
      approveStatus: 1,
      effectiveDate: '2026-11-11 00:00:00',
      expiryDate: null,
      remark: '',
      contentRevision: `rev-${bomId}`,
    },
  }))
  // 编辑占用（dev-20261111-018）：默认本窗口取得编辑权
  mocks.acquireBomEditSession.mockImplementation(async (bomId: number) => ({
    data: {
      leaseAvailable: true,
      acquired: true,
      occupied: false,
      ownerDisplayName: '张三',
      expiresAt: Date.now() + 90_000,
      token: `lease-${bomId}`,
    },
  }))
  mocks.renewBomEditSession.mockImplementation(async (bomId: number) => ({
    data: { leaseAvailable: true, acquired: true, token: `lease-${bomId}` },
  }))
  mocks.releaseBomEditSession.mockResolvedValue({ data: true })
  mocks.describeBomEditSession.mockResolvedValue({ data: { leaseAvailable: true, acquired: false, occupied: false } })
  mocks.listEngineeringBomItem.mockResolvedValue({ data: [] })
  // 新建方案：分配递增 bomId，用于验证逐层创建/回读
  mocks.saveManufacturingBom.mockImplementation(async (payload: any) => {
    const bomId = bomIdSeq++
    return {
      data: {
        bomId,
        bomCode: payload.bomCode,
        bomName: payload.bomName,
        bomVersion: payload.bomVersion || 'V1.0',
        outputType: 'MATERIAL',
        outputId: payload.outputId,
        approveStatus: 1,
      },
    }
  })
  mocks.updateManufacturingBom.mockImplementation(async (bomId: number, payload: any) => ({
    data: {
      bomId,
      bomCode: payload.bomCode,
      bomName: payload.bomName,
      bomVersion: payload.bomVersion || 'V1.0',
      outputType: 'MATERIAL',
      outputId: payload.outputId,
      approveStatus: 1,
      contentRevision: `rev-${bomId}-next`,
    },
  }))
  mocks.adoptManufacturingBom.mockImplementation(async (bomId: number) => ({
    data: scheme({ bomId, bomCode: `MFG-${bomId}`, outputId: 0 }),
  }))
  mocks.materialInfo.mockImplementation(async (id: string) => {
    const found = archives.get(Number(id))
    return {
      data: found ?? { materialCode: '', materialName: '', specification: '', unit: '' },
    }
  })
})

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('制造BOM跳距保存与未保存提醒（dev-20261112-010）', () => {
  it.each([undefined, null, ''])('读取与保存跳距行时空比例 %s 不转为0', async (empty) => {
    const stored = { ...item(), moduleQty: empty, baseQty: empty, pitchMm: 80, pcsPerPitch: 1 }
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [stored] })
    mocks.updateManufacturingBom.mockResolvedValue({
      data: { ...scheme(), items: [stored], contentRevision: 'rev-next' },
    })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    const row = state.current.draft.items[0]
    expect(row.moduleQty).toBeUndefined()
    expect(row.baseQty).toBeUndefined()
    expect(state.currentDirty).toBe(false)
    expect(state.validateDraft()).toBe('')
    expect(await state.saveScheme()).toBe(true)
    const payload = mocks.updateManufacturingBom.mock.calls[0][1]
    expect(payload.items[0]).toMatchObject({ pitchMm: 80, pcsPerPitch: 1 })
    expect(payload.items[0].moduleQty).toBeUndefined()
    expect(payload.items[0].baseQty).toBeUndefined()
    expect(state.current.draft.items[0].moduleQty).toBeUndefined()
    expect(state.current.draft.items[0].baseQty).toBeUndefined()
    expect(state.currentDirty).toBe(false)
  })

  it('新建制造方案两组皆空也可保存，不注入0值比例', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.items = [item({ moduleQty: undefined, baseQty: undefined })]
    await flushPromises()
    expect(state.validateDraft()).toBe('')
    expect(await state.saveScheme()).toBe(true)
    const saved = mocks.saveManufacturingBom.mock.calls[0][0].items[0]
    expect(saved.moduleQty).toBeUndefined()
    expect(saved.baseQty).toBeUndefined()
  })

  it.each([
    ['pitchMm', 100],
    ['pcsPerPitch', 2],
  ] as const)('只改 %s 也提示未保存，并可保存后再采用', async (field, value) => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({
      data: [item({ moduleQty: undefined, baseQty: undefined, pitchMm: 80, pcsPerPitch: 1 })],
    })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    expect(state.currentDirty).toBe(false)
    expect(state.canAdopt).toBe(true)
    state.current.draft.items[0][field] = value
    expect(state.currentDirty).toBe(true)
    expect(state.canAdopt).toBe(false)
    mocks.confirm.mockRejectedValueOnce('close')
    expect(await state.resolveLeave()).toBe(false)
    expect(mocks.confirm).toHaveBeenCalledWith('当前制造方案有未保存的修改。', '未保存提醒', expect.any(Object))
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.updateManufacturingBom.mock.calls[0][1].items[0][field]).toBe(value)
    expect(state.currentDirty).toBe(false)
    expect(state.canAdopt).toBe(true)
  })
})

describe('新建下层：已建档投入物料即使没有 subBomId 也能进入候选并新建', () => {
  it('按该投入物料重新拉候选，可直接新建并保存，产出身份仍是该物料', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    expect(state.current.draft.bomId).toBeNull()

    const rowB = item({
      itemId: 21,
      materialId: 350,
      materialCode: 'SFG000350',
      materialName: '小片',
      unit: 'PCS',
      subBomId: null,
    })
    state.current.draft.items = [rowB]
    await flushPromises()

    await state.drillDown(rowB)
    expect(state.stack).toHaveLength(2)
    expect(state.activeIndex).toBe(1)
    expect(state.current.material.materialId).toBe(350)
    expect(state.current.draft.bomId).toBeNull()
    expect(mocks.manufacturingOptions).toHaveBeenLastCalledWith({
      outputType: 'MATERIAL',
      outputId: 350,
      productId: 1,
    })
    // 进入的空白草稿带该物料编码（不是靠临时成品BOM绕路）
    expect(state.current.draft.bomCode).toBe('SFG000350-MFG')

    state.current.draft.items = [
      item({ itemId: -1, materialId: 109, materialCode: 'RM000105', materialName: 'PC-800' }),
    ]
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.saveManufacturingBom).toHaveBeenCalledTimes(1)
    expect(mocks.saveManufacturingBom.mock.calls[0][0]).toMatchObject({
      outputType: 'MATERIAL',
      outputId: 350,
    })
    expect(state.current.draft.bomId).toBe(130)
  })

  it('只读用户：没有 subBomId 不进入新建；有 subBomId 只能查看', async () => {
    permission.perms = new Set<string>()
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    expect(state.canCreate).toBe(false)
    expect(state.editable).toBe(false)

    await state.drillDown(item({ itemId: 21, materialId: 350, subBomId: null }))
    expect(state.stack).toHaveLength(1)

    await state.drillDown(item({ itemId: 22, materialId: 350, subBomId: 200 }))
    expect(state.stack).toHaveLength(2)
    expect(state.current.subBomId).toBe(200)
    expect(await state.saveScheme()).toBe(false)
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('逐层采用：只回填直接上层，父草稿变脏，顶层才交给产品BOM', () => {
  it('A→B→原料 从零创建、保存、采用、返回、保存父层，最后顶层采用', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    const rowB = item({
      itemId: 21,
      materialId: 350,
      materialCode: 'SFG000350',
      materialName: '小片',
      unit: 'PCS',
      subBomId: null,
    })
    const rowC = item({
      itemId: 22,
      materialId: 351,
      materialCode: 'SFG000351',
      materialName: '另一片',
      unit: 'PCS',
      subBomId: null,
    })
    state.current.draft.items = [rowB, rowC]

    // 1）从零创建 A（最外层）的制造方案
    expect(await state.saveScheme()).toBe(true)
    expect(state.current.draft.bomId).toBe(130)
    expect(state.currentDirty).toBe(false)

    // 2）进入 B（无 subBomId）新建 B 的制造方案
    await state.drillDown(rowB)
    state.current.draft.items = [item({ itemId: -1, materialId: 109 })]
    expect(await state.saveScheme()).toBe(true)
    expect(state.current.draft.bomId).toBe(131)

    // 3）深层采用：只回填直接上层 B 行，不发 adopt 给产品BOM，兄弟行不动
    expect(await state.adoptScheme()).toBe(true)
    expect(wrapper.emitted('adopt')).toBeUndefined()
    expect(state.stack[0].draft.items[0].subBomId).toBe(131)
    expect(state.stack[0].draft.items[0].sourceType).toBe('make')
    expect(state.stack[0].draft.items[1].subBomId).toBeFalsy()
    expect(state.stack[0].draft.items[1].sourceType).toBeFalsy()
    expect(state.current.subBomId).toBe(131)

    // 4）返回上层：父层变脏，保存后才持久化父行引用
    await state.handlePathNavigate(0)
    await flushPromises() // 返回已存上层后等待编辑占用申请完成
    expect(state.activeIndex).toBe(0)
    expect(state.currentDirty).toBe(true)
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.updateManufacturingBom).toHaveBeenCalledTimes(1)
    expect(mocks.updateManufacturingBom.mock.calls[0][0]).toBe(130)
    expect(mocks.updateManufacturingBom.mock.calls[0][1].items[0].subBomId).toBe(131)
    expect(mocks.updateManufacturingBom.mock.calls[0][1].items[1].subBomId).toBeFalsy()

    // 5）回到根后再采用，才把顶层方案交给产品BOM
    expect(await state.adoptScheme()).toBe(true)
    const adoptPayload = wrapper.emitted('adopt')?.at(-1)?.[0] as any
    expect(adoptPayload).toMatchObject({ materialId: 349, bomId: 130 })
  })

  it('下层有未保存修改时返回先提示：继续编辑留在本层，放弃才返回且不串改上层', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    state.current.draft.remark = '上层未保存'

    await state.drillDown(item({ itemId: 31, materialId: 350, subBomId: 200 }))
    state.current.draft.remark = '下层未保存'

    mocks.confirm.mockRejectedValue('close')
    await state.handlePathNavigate(0)
    await flushPromises() // 返回已存上层后等待编辑占用申请完成
    expect(state.activeIndex).toBe(1)

    mocks.confirm.mockRejectedValue('cancel')
    await state.handlePathNavigate(0)
    await flushPromises() // 返回已存上层后等待编辑占用申请完成
    expect(state.activeIndex).toBe(0)
    expect(state.current.draft.remark).toBe('上层未保存')
    // 关闭时的未保存提示同样按“当前层”处理，不串到兄弟层
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()
  })
})

describe('主方案：顶层显式调整；深层先挂起，链可达才随产品BOM提交', () => {
  it('深层设主不立即提交；采用并逐层保存、回到根且图可达后才发 primary-change', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    const rowB = item({ itemId: 21, materialId: 350, unit: 'PCS', subBomId: null })
    state.current.draft.items = [rowB]
    expect(await state.saveScheme()).toBe(true) // A 方案 130

    await state.drillDown(rowB)
    state.current.draft.items = [item({ itemId: -1, materialId: 109 })]
    expect(await state.saveScheme()).toBe(true) // B 方案 131

    state.markPrimary(scheme({ bomId: 131 }))
    expect(wrapper.emitted('primary-change')).toBeUndefined()
    expect(state.pendingPrimary[350]).toMatchObject({ materialId: 350, bomId: 131 })

    // 采用后父草稿变脏但尚未保存：仍不提交
    expect(await state.adoptScheme()).toBe(true)
    await state.handlePathNavigate(0)
    await flushPromises() // 返回已存上层后等待编辑占用申请完成
    expect(state.currentDirty).toBe(true)
    expect(wrapper.emitted('primary-change')).toBeUndefined()

    // 父层保存后链可达：深层主方案随产品BOM保存提交
    expect(await state.saveScheme()).toBe(true)
    const emitted = wrapper.emitted('primary-change')?.at(-1)?.[0] as any
    expect(emitted).toEqual({ materialId: 350, bomId: 131 })
    expect(state.pendingPrimary[350]).toBeUndefined()
  })

  it('顶层设主方案仍然直接交给产品BOM，不调用任何写接口', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    state.markPrimary(scheme({ bomId: 125 }))
    expect(wrapper.emitted('primary-change')?.at(-1)?.[0]).toEqual({
      materialId: 349,
      bomId: 125,
    })
    expect(state.isPrimaryScheme(125)).toBe(true)
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
  })

  it('没有产品上下文时不给设主方案，也不发事件', async () => {
    const wrapper = await dialog({ productId: null, productCode: '', productName: '' })
    const state = wrapper.vm as any
    state.markPrimary(scheme())
    expect(wrapper.emitted('primary-change')).toBeUndefined()
    expect(mocks.warning).toHaveBeenCalledWith('尚未选择产品：请先选择产品，再设置主方案')
  })
})

describe('跨单位换算依据：同单位免填，跨单位必填并回显', () => {
  it('档案单位与投入单位相同时不需要系数', async () => {
    archives.set(109, {
      materialCode: 'RM000105',
      materialName: 'PC-800 茶色',
      specification: '195mm*80mm',
      unit: 'KG',
    })
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item({ materialId: 109, unit: 'KG' })] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    const row = state.current.draft.items[0]
    expect(row.archiveUnit).toBe('KG')
    expect((table.vm as any).needsUnitFactor(row)).toBe(false)
    expect((table.vm as any).validate()).toBe('')
    expect((table.vm as any).referenceQuantity(row)).toBe('0.1667')
  })

  it('历史单位不一致要求人工确认，不能用换算系数绕过；重新选料后带入档案单位', async () => {
    archives.set(109, {
      materialCode: 'RM000105', materialName: 'PC-800 茶色', specification: '195mm*80mm', unit: 'M',
    })
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item({ materialId: 109, unit: 'KG' })] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    const row = state.current.draft.items[0]
    expect(row.archiveUnit).toBe('M')
    const warning = '第1行BOM单位 KG 与档案单位 M 不一致，请人工确认并重新选料'
    expect((table.vm as any).validate()).toBe(warning)
    row.unitFactor = 12.5; row.unitFactorBasis = '历史换算依据'
    expect((table.vm as any).validate()).toBe(warning)
    expect(row.unit).toBe('KG')
    ;(table.vm as any).$.setupState.handleSelect(row, { materialId: 109, ...archives.get(109) })
    expect(row.unit).toBe('M')
    expect(row.baseQty).toBeUndefined()
    expect(row.moduleQty).toBeUndefined()
    expect(row.quantity).toBe(0)
    expect((table.vm as any).validate()).toBe('')
  })
})

describe('参考用量与损耗率：只展示后端回读值，不在前端重算', () => {
  it('未保存的比例改动只标“待保存”，不改写 row.quantity，也不提交旧参考值', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item({ quantity: 0.1667 })] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    const row = state.current.draft.items[0]
    expect((table.vm as any).referenceQuantity(row)).toBe('0.1667')

    row.baseQty = 3
    ;(table.vm as any).markQuantityStale(row)
    expect((table.vm as any).referenceQuantity(row)).toBe('待保存后更新')
    expect(row.quantity).toBe(0.1667) // 前端不计算、不回写
    const payload = state.buildPayload()
    expect(payload.items[0].quantity).toBeUndefined()
    expect(payload.items[0].baseQty).toBe(3)
  })

  it('损耗率保留两位小数：0.5通过，超范围和超精度被拒绝', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    const table = wrapper.findComponent(ManufacturingBomItemTable)
    state.current.draft.items = [item({ lossRate: 0 })]
    await flushPromises()
    expect((table.vm as any).validate()).toBe('')

    state.current.draft.items[0].lossRate = 0.5
    expect((table.vm as any).validate()).toBe('')
    state.current.draft.items[0].lossRate = 120
    expect((table.vm as any).validate()).toBe('第1行损耗率须在0~100之间，最多2位小数')
    state.current.draft.items[0].lossRate = 0.555
    expect((table.vm as any).validate()).toBe('第1行损耗率须在0~100之间，最多2位小数')
    state.current.draft.items[0].lossRate = 50
    expect((table.vm as any).validate()).toBe('')
    expect(state.buildPayload().items[0].lossRate).toBe(50)
  })
})

describe('产出身份与方案显示：用物料档案与编码+版本，不显示数据库ID/实现文字', () => {
  it('产出头部规格/单位取物料档案，不被父BOM行覆盖后的单位冒充', async () => {
    archives.set(349, {
      materialCode: 'SFG000345',
      materialName: '中间件（档案）',
      specification: '档案规格X',
      unit: 'PCS',
    })
    const wrapper = await dialog({
      material: { ...material, unit: 'M', specification: '父行覆盖规格' },
    })
    const state = wrapper.vm as any
    expect(state.outputInfo).toMatchObject({
      materialCode: 'SFG000345',
      materialName: '中间件（档案）',
      specification: '档案规格X',
      unit: 'PCS',
    })
    expect(wrapper.text()).not.toContain('MATERIAL')
    expect(wrapper.text()).not.toContain('可靠产品ID')
  })

  it('已选/主方案用编码+版本展示，不把 #数据库ID 当标题', async () => {
    mocks.manufacturingOptions.mockResolvedValue({
      data: [scheme(), scheme({ bomId: 125, bomCode: 'SFG000345-MFG-B', bomVersion: 'V2.0' })],
    })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124, primaryBomId: 125 })
    const state = wrapper.vm as any
    expect(state.currentSchemeLabel).toContain('MFG-124')
    expect(state.currentSchemeLabel).toContain('V1.0')
    expect(state.primarySchemeLabel).toContain('SFG000345-MFG-B')
    expect(state.primarySchemeLabel).toContain('V2.0')
    expect(state.primarySchemeLabel).not.toContain('#125')
  })
})

describe('权限矩阵：查看 / 新增 / 编辑 / 采用分开', () => {
  it('view-only：能看候选与明细，但不能新建/保存/采用/设主', async () => {
    permission.perms = new Set<string>()
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    expect(state.current.options).toHaveLength(1)
    expect(state.canCreate).toBe(false)
    expect(state.canEditStored).toBe(false)
    expect(state.editable).toBe(false)
    expect(state.canAdoptVisible).toBe(false)
    expect(await state.saveScheme()).toBe(false)
    expect(await state.adoptScheme()).toBe(false)
    expect(mocks.adoptManufacturingBom).not.toHaveBeenCalled()
    state.markPrimary(scheme())
    expect(wrapper.emitted('primary-change')).toBeUndefined()
    expect(mocks.warning).toHaveBeenCalledWith('当前用户没有编辑产品BOM的权限，不能设置主方案')
  })

  it('add-only：可新建并采用，但不能编辑已存方案、不能设主', async () => {
    permission.perms = new Set(['engineering:bom:add'])
    const wrapper = await dialog()
    const state = wrapper.vm as any
    expect(state.canCreate).toBe(true)
    expect(state.canEditStored).toBe(false)
    expect(state.editable).toBe(true) // 空白新方案可写
    state.current.draft.items = [item({ itemId: -1, materialId: 109 })]
    expect(await state.saveScheme()).toBe(true)

    // 已存方案：可看可采用，但字段不可编辑、不能再保存
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    const existing = await dialog({ subBomId: 124 })
    const existingState = existing.vm as any
    expect(existingState.current.draft.bomId).toBe(124)
    expect(existingState.editable).toBe(false)
    existingState.markPrimary(scheme())
    expect(existing.emitted('primary-change')).toBeUndefined()
    expect(await existingState.saveScheme()).toBe(false)
    expect(await existingState.adoptScheme()).toBe(true)
    expect(mocks.adoptManufacturingBom).toHaveBeenCalledWith(124)
  })

  it('edit-only：可编辑/采用已存方案，但不能新建方案', async () => {
    permission.perms = new Set(['engineering:bom:edit'])
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    expect(state.canCreate).toBe(false)
    expect(state.canEditStored).toBe(true)
    expect(state.editable).toBe(true)

    // “新建方案”对 edit-only 无效果：仍停在已存方案，不发新增请求
    state.startNewScheme()
    await flushPromises()
    expect(state.current.draft.bomId).toBe(124)
    expect(mocks.saveManufacturingBom).not.toHaveBeenCalled()

    // 已存方案可保存（PUT）
    await state.selectSchemeByBomId(124)
    expect(state.editable).toBe(true)
    state.current.draft.remark = '编辑已存方案'
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.updateManufacturingBom).toHaveBeenCalledWith(124, expect.any(Object))
  })
})

function permissionsKeep() {
  permission.perms = new Set(['engineering:bom:add', 'engineering:bom:edit'])
}


describe('制造定额小数损耗', () => {
  it('保存0.5%损耗时保留小数，不截断成0', async () => {
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.items = [item({ itemId: -1, lossRate: 0.5 })]
    expect(await state.saveScheme()).toBe(true)
    expect(mocks.saveManufacturingBom.mock.calls[0][0].items[0].lossRate).toBe(0.5)
  })
})

// ==================== 编辑占用 + 服务端内容版本（dev-20261111-018） ====================

describe('制造BOM：编辑占用与服务端内容版本', () => {
  it('编辑已保存方案时请求带 expectedContentRevision 与 editToken，保存后不释放租约', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    await state.selectSchemeByBomId(124)
    await flushPromises()
    expect(state.editSession.state.value).toBe('granted')
    expect(state.editSession.readOnly.value).toBe(false)

    state.current.draft.remark = '带版本保存'
    expect(await state.saveScheme()).toBe(true)
    const [, payload] = mocks.updateManufacturingBom.mock.calls[0]
    expect(payload.expectedContentRevision).toBe('rev-124')
    expect(payload.editToken).toBe('lease-124')
    // 保存成功：用后端返回的新指纹继续编辑，且不释放租约
    expect(state.current.draft.contentRevision).toBe('rev-124-next')
    expect(mocks.releaseBomEditSession).not.toHaveBeenCalled()
  })

  it('已保存方案被其他窗口占用时只读并禁止保存，草稿保留', async () => {
    mocks.manufacturingOptions.mockResolvedValue({ data: [scheme()] })
    mocks.listEngineeringBomItem.mockResolvedValue({ data: [item()] })
    mocks.acquireBomEditSession.mockResolvedValue({
      data: {
        leaseAvailable: true,
        acquired: false,
        occupied: true,
        ownerDisplayName: '李四',
        message: '该BOM正由 李四 编辑，本窗口为只读',
      },
    })
    const wrapper = await dialog({ subBomId: 124 })
    const state = wrapper.vm as any
    await state.selectSchemeByBomId(124)
    await flushPromises()
    expect(state.editSession.readOnly.value).toBe(true)
    // 只读不是只禁保存按钮：明细表与表头字段都必须禁用（修复卡 F6b）
    expect(state.editable).toBe(false)
    expect(wrapper.findComponent(ManufacturingBomItemTable).props('readonly')).toBe(true)

    state.current.draft.remark = '只读草稿'
    expect(await state.saveScheme()).toBe(false)
    expect(mocks.updateManufacturingBom).not.toHaveBeenCalled()
    expect(state.current.draft.remark).toBe('只读草稿')
  })

  it('新建方案首次保存后立即申请该 bomId 的编辑占用，第二次保存带 token 与内容指纹', async () => {
    mocks.saveManufacturingBom.mockImplementation(async (payload: any) => ({
      data: {
        bomId: 130,
        bomCode: payload.bomCode,
        bomName: payload.bomName,
        bomVersion: payload.bomVersion || 'V1.0',
        outputType: 'MATERIAL',
        outputId: payload.outputId,
        approveStatus: 1,
        contentRevision: 'rev-130',
      },
    }))
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.items = [item({ itemId: -1, materialId: 109 })]
    expect(await state.saveScheme()).toBe(true)
    await flushPromises()

    // 首次保存后必须为新 bomId 申请租约，否则第二次保存缺 token 会被后端 409
    expect(state.current.draft.bomId).toBe(130)
    expect(mocks.acquireBomEditSession).toHaveBeenCalledWith(130, expect.any(String))
    expect(state.editSession.token.value).toBe('lease-130')
    expect(state.editSession.readOnly.value).toBe(false)

    state.current.draft.remark = '第二次保存'
    expect(await state.saveScheme()).toBe(true)
    const [bomIdArg, payload] = mocks.updateManufacturingBom.mock.calls[0]
    expect(bomIdArg).toBe(130)
    expect(payload.editToken).toBe('lease-130')
    expect(payload.expectedContentRevision).toBe('rev-130')
    // 保存成功不释放租约
    expect(mocks.releaseBomEditSession).not.toHaveBeenCalled()
  })

  it('新建方案保存后拿不到编辑占用：转只读并保留草稿', async () => {
    mocks.acquireBomEditSession.mockResolvedValue({
      data: {
        leaseAvailable: true,
        acquired: false,
        occupied: true,
        ownerDisplayName: '李四',
        message: '该BOM正由 李四 编辑，本窗口为只读',
      },
    })
    const wrapper = await dialog()
    const state = wrapper.vm as any
    state.current.draft.items = [item({ itemId: -1, materialId: 109 })]
    expect(await state.saveScheme()).toBe(true) // 保存本身成功
    await flushPromises()

    expect(state.editSession.readOnly.value).toBe(true)
    expect(state.editable).toBe(false)
    expect(state.current.draft.bomId).toBe(130)
    expect(state.current.draft.items).toHaveLength(1) // 草稿保留
    expect(mocks.warning).toHaveBeenCalled()
  })
})
