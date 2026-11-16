import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BomReviewScope, { buildReviewPackageScopes } from '../BomReviewScope.vue'
import { buildBomReviewTree, type BomReviewTreeRow } from '../bomReviewTree'
import { BomStatusEnum } from '@/enums/product'
import type { BomReviewPackage } from '@/types/product/bom'

const api = vi.hoisted(() => ({
  planReviewPackages: vi.fn(),
  submitReviewPackages: vi.fn(),
  approveReviewPackages: vi.fn(),
  rejectReviewPackages: vi.fn(),
  getBomReferenceImpact: vi.fn(),
}))
const permissions = vi.hoisted(() => ({ allowed: ['*'] }))
vi.mock('@/api/product/bom', () => ({ productBomApi: api }))
vi.mock('@/directives', () => ({
  hasPermi: (permission: string) =>
    permissions.allowed.includes('*') || permissions.allowed.includes(permission),
}))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn() },
}))
const Dialog = defineComponent({
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
})
const plan = (action = 'SUBMIT'): BomReviewPackage => ({
  rootBomId: 7,
  rootBomCode: 'YL1026MEOO-BOM',
  rootBomVersion: 'V1.1',
  productCode: 'YL1026MEOO',
  outputType: 'PRODUCT',
  actionCount: action === 'REJECT' ? 1 : 2,
  reuseCount: 1,
  pendingReuseCount: 0,
  executable: true,
  blockingIssues: [],
  nodes: [
    {
      bomId: 1,
      bomCode: 'AUX000347-MFG',
      outputName: '3M 9080-W195-L80',
      bomVersion: 'V1.0',
      status: 3,
      plannedAction: 'REUSE',
      packageRevision: 'rev-1',
      items: [
        {
          itemId: 1,
          materialCode: 'AUX000106',
          materialName: '3M 9080',
          quantity: 0.0156,
          unit: '㎡',
        },
      ],
    },
    {
      bomId: 5,
      bomCode: 'AUX000351-MFG',
      outputName: 'PET保护膜 PFPET T4901',
      bomVersion: 'V1.0',
      status: action === 'SUBMIT' ? 1 : 2,
      plannedAction: action === 'SUBMIT' ? 'SUBMIT' : 'APPROVE',
      packageRevision: 'rev-5',
      items: [
        {
          itemId: 9,
          materialCode: 'AUX000108',
          materialName: 'PET保护膜 PFPET-T4901',
          quantity: 0.1667,
          unit: 'PCS',
        },
      ],
    },
    {
      bomId: 7,
      bomCode: 'YL1026MEOO-BOM',
      outputName: 'YL1026MEOO',
      bomVersion: 'V1.1',
      status: action === 'SUBMIT' ? 1 : 2,
      plannedAction: action === 'SUBMIT' ? 'SUBMIT' : 'APPROVE',
      packageRevision: 'rev-7',
      items: [
        { itemId: 12, materialName: '3M 9080-W195-L80', quantity: 1, unit: 'PCS', subBomId: 1 },
        {
          itemId: 13,
          materialName: 'PET保护膜 PFPET T4901',
          quantity: 1,
          unit: 'PCS',
          subBomId: 5,
        },
      ],
    },
  ].filter((node) => action !== 'REJECT' || node.bomId === 7) as BomReviewPackage['nodes'],
})
const success = () => ({
  data: {
    successes: [{ rootBomId: 7, rootBomVersion: 'V1.1', nodes: [] }],
    failures: [],
    sharedNodes: [],
  },
})
const mountScope = async (action: 'SUBMIT' | 'APPROVE' = 'SUBMIT') => {
  const wrapper = mount(BomReviewScope, {
    props: { modelValue: true, action, rootBomIds: [7] },
    global: { stubs: { ElDialog: Dialog }, directives: { loading: () => {} } },
  })
  await flushPromises()
  return wrapper
}
const button = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll('button').find((node) => node.text() === label)!
const flatten = (rows: BomReviewTreeRow[]): BomReviewTreeRow[] =>
  rows.flatMap((row) => [row, ...flatten(row.children)])
beforeEach(() => {
  vi.clearAllMocks()
  permissions.allowed = ['*']
  api.planReviewPackages.mockImplementation(async (action) => ({ data: [plan(action)] }))
  api.submitReviewPackages.mockResolvedValue(success())
  api.approveReviewPackages.mockResolvedValue(success())
  api.rejectReviewPackages.mockResolvedValue(success())
  api.getBomReferenceImpact.mockResolvedValue({ data: [] })
})

describe('整套BOM引用关系展示', () => {
  it('根版本在前，生产BOM挂到实际引用它的材料行，保留本层数量和原料', () => {
    const tree = buildBomReviewTree(plan())
    expect(tree[0].bomId).toBe(7)
    expect(tree[0].children.map((row) => row.item?.subBomId)).toEqual([1, 5])
    expect(tree[0].children[0].children[0].bomId).toBe(1)
    expect(tree[0].children[0].children[0].children[0].item?.quantity).toBe(0.0156)
    expect(tree[0].children[1].children[0].children[0].item?.quantity).toBe(0.1667)
  })
  it('共享方案显示每条所属路径，请求仍只包含服务端去重后的一个节点', () => {
    const p = plan()
    p.nodes
      .find((node) => node.bomId === 7)!
      .items!.push({ itemId: 14, materialName: '第二处胶', subBomId: 1 })
    const rows = flatten(buildBomReviewTree(p))
    const shared = rows.filter((row) => row.kind === 'scheme' && row.bomId === 1)
    expect(shared).toHaveLength(2)
    expect(new Set(rows.map((row) => row.key)).size).toBe(rows.length)
    expect(
      buildReviewPackageScopes([p], 'SUBMIT')[0].expectedNodes.filter((node) => node.bomId === 1)
    ).toHaveLength(1)
  })
  it('任意深层引用、行内父子关系和问题都保留；循环不无限展开，缺失引用明确提示', () => {
    const p = plan()
    p.nodes[1].items!.push({
      itemId: 20,
      parentMaterialId: 9,
      materialName: '下层材料',
      subBomId: 30,
    })
    p.nodes.push(
      { bomId: 30, bomVersion: 'V1.0', items: [{ itemId: 30, subBomId: 31 }] },
      {
        bomId: 31,
        bomVersion: 'V1.0',
        items: [
          { itemId: 31, subBomId: 7 },
          { itemId: 32, subBomId: 999 },
        ],
      }
    )
    p.blockingIssues.push({ bomId: 5, itemId: 20, message: '请核对材料' })
    const rows = flatten(buildBomReviewTree(p))
    expect(rows.find((row) => row.item?.itemId === 20)?.issues).toContain('请核对材料')
    expect(rows.some((row) => row.bomId === 31)).toBe(true)
    expect(rows.some((row) => row.issues.some((message) => message.includes('循环')))).toBe(true)
    expect(rows.some((row) => row.issues.some((message) => message.includes('缺少此方案')))).toBe(
      true
    )
  })
})

describe('整套提交审核与审核流程', () => {
  it('展示树形明细，只在统一确认后提交完整预览范围，成功返回版本维护', async () => {
    const wrapper = await mountScope()
    expect(wrapper.text()).toContain('3M 9080')
    expect(wrapper.text()).toContain('0.0156 ㎡')
    expect(wrapper.text()).toContain('已批准复用')
    expect(api.submitReviewPackages).not.toHaveBeenCalled()
    await button(wrapper, '确认整套提交').trigger('click')
    await flushPromises()
    expect(api.submitReviewPackages).toHaveBeenCalledWith({
      action: 'SUBMIT',
      products: buildReviewPackageScopes([plan()], 'SUBMIT'),
      remark: '',
    })
    expect(wrapper.emitted('done')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
  it('整套审核传递预览中每个方案的版本和指纹，无需逐行批准', async () => {
    const wrapper = await mountScope('APPROVE')
    await wrapper.get('textarea').setValue('用料及下层方案已核对')
    await button(wrapper, '整套审核通过').trigger('click')
    await flushPromises()
    expect(api.approveReviewPackages).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'APPROVE',
        remark: '用料及下层方案已核对',
        products: buildReviewPackageScopes([plan('APPROVE')], 'APPROVE'),
      })
    )
    expect(api.rejectReviewPackages).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('仅有驳回权限时仍能查看整套；必须填原因，驳回只提交根版本', async () => {
    permissions.allowed = ['engineering:bom:view', 'engineering:bom:reject']
    const wrapper = await mountScope('APPROVE')
    expect(wrapper.text()).toContain('3M 9080')
    expect(button(wrapper, '确认退回修改').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').some((node) => node.text() === '整套审核通过')).toBe(false)
    await wrapper.get('textarea').setValue('保护膜用量需要核对')
    await button(wrapper, '确认退回修改').trigger('click')
    await flushPromises()
    expect(api.rejectReviewPackages).toHaveBeenCalledWith({
      action: 'REJECT',
      remark: '保护膜用量需要核对',
      products: buildReviewPackageScopes([plan('REJECT')], 'REJECT'),
    })
    expect(api.approveReviewPackages).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('失败保留范围和原因，阻止重复提交旧指纹，重新检查后才允许重试', async () => {
    api.submitReviewPackages.mockResolvedValueOnce({
      data: { successes: [], failures: [{ rootBomId: 7, reason: '方案内容已变化，请重新预览' }] },
    })
    const wrapper = await mountScope()
    await button(wrapper, '确认整套提交').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('方案内容已变化')
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(button(wrapper, '确认整套提交').attributes('disabled')).toBeDefined()
    const next = plan()
    next.nodes.find((node) => node.bomId === 7)!.packageRevision = 'updated-revision'
    api.planReviewPackages.mockResolvedValue({ data: [next] })
    await button(wrapper, '重新检查').trigger('click')
    await flushPromises()
    await button(wrapper, '确认整套提交').trigger('click')
    await flushPromises()
    expect(
      api.submitReviewPackages.mock.calls[1][0].products[0].expectedNodes.find(
        (node: any) => node.bomId === 7
      ).packageRevision
    ).toBe('updated-revision')
    wrapper.unmount()
  })
  it('预检阻断和无权限均不能执行；问题仍显示在所属材料行', async () => {
    const blocked = plan()
    blocked.executable = false
    blocked.blockingIssues = [{ bomId: 5, itemId: 9, message: '保护膜需关联物料' }]
    api.planReviewPackages.mockResolvedValue({ data: [blocked] })
    const wrapper = await mountScope()
    expect(wrapper.text()).toContain('保护膜需关联物料')
    expect(button(wrapper, '确认整套提交').attributes('disabled')).toBeDefined()
    const process = wrapper.findAll('button').find((node) => node.text() === '打开处理')!
    await process.trigger('click')
    expect(wrapper.emitted('open-bom')?.[0]).toEqual([5, true])
    wrapper.unmount()
    permissions.allowed = ['engineering:bom:view']
    api.planReviewPackages.mockResolvedValue({ data: [plan()] })
    const readonly = await mountScope()
    expect(button(readonly, '确认整套提交').attributes('disabled')).toBeDefined()
    expect(api.submitReviewPackages).not.toHaveBeenCalled()
    readonly.unmount()
  })
  it('请求中禁止重复执行；切换版本的迟到预览不会覆盖当前版本', async () => {
    let resolve: (value: any) => void = () => {}
    api.planReviewPackages.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        })
    )
    const wrapper = await mountScope()
    const newer = plan()
    newer.rootBomId = 8
    newer.productCode = 'NEW-PRODUCT'
    newer.nodes = [{ ...newer.nodes[2], bomId: 8 }]
    api.planReviewPackages.mockResolvedValue({ data: [newer] })
    await wrapper.setProps({ rootBomIds: [8] })
    await flushPromises()
    resolve({ data: [plan()] })
    await flushPromises()
    expect(wrapper.text()).toContain('NEW-PRODUCT')
    expect(wrapper.text()).not.toContain('AUX000347-MFG')
    let finish: (value: any) => void = () => {}
    api.submitReviewPackages.mockImplementation(
      () =>
        new Promise((done) => {
          finish = done
        })
    )
    await button(wrapper, '确认整套提交').trigger('click')
    await button(wrapper, '确认整套提交').trigger('click')
    expect(api.submitReviewPackages).toHaveBeenCalledTimes(1)
    finish(success())
    await flushPromises()
    wrapper.unmount()
  })
})
