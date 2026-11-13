import { describe, it, expect, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import VersionMaintenanceDialog, {
  diffVersionDetails,
  type VersionMaintenanceRow,
} from '../VersionMaintenanceDialog.vue'
import { BomStatusEnum } from '@/enums/product'

const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Column = defineComponent({ props: ['label'], template: '<div :data-column="label" />' })
const Actions = defineComponent({ props: ['actions'], emits: ['action'], template: '<div />' })
const row = (
  approveStatus: number,
  extra: Partial<VersionMaintenanceRow> = {}
): VersionMaintenanceRow => ({
  id: 1,
  version: 'V1.0',
  approveStatus,
  isCurrent: false,
  canSetCurrent: false,
  ...extra,
})
const create = (extra: Record<string, unknown> = {}) =>
  mount(VersionMaintenanceDialog, {
    props: {
      modelValue: true,
      title: '产品甲',
      domain: 'bom',
      rows: [row(BomStatusEnum.DRAFT.value)],
      fetchDetail: async () => ({ items: [] }),
      ...extra,
    },
    global: {
      stubs: {
        ElDialog: Slot,
        ElTable: Slot,
        ElTableColumn: Column,
        TableActionColumn: Actions,
        ElSelect: Slot,
        ElOption: true,
        ElButton: Slot,
        ElAlert: true,
        ElEmpty: true,
      },
      directives: { loading: {} },
    },
  })
const visibleActions = (wrapper: ReturnType<typeof create>, data: VersionMaintenanceRow) =>
  wrapper
    .findComponent(Actions)
    .props('actions')
    .filter((a: any) => a.visible({ row: data }))
    .map((a: any) => a.key)

describe('版本维护按能力渲染及状态护栏', () => {
  it('工艺路线不生成日期/审核人空列，BOM 完整显示', async () => {
    const wrapper = create({
      domain: 'routing',
      supportsValidity: false,
      supportsReviewAudit: false,
    })
    const labels = () => wrapper.findAll('[data-column]').map((x) => x.attributes('data-column'))
    expect(labels()).toEqual(['版本号', '状态', '创建人', '创建时间', '备注'])
    await wrapper.setProps({ domain: 'bom', supportsValidity: true, supportsReviewAudit: true })
    expect(labels()).toEqual([
      '版本号',
      '状态',
      '生效日期',
      '失效日期',
      '创建人',
      '创建时间',
      '审核人',
      '审核时间',
      '备注',
    ])
    wrapper.unmount()
  })
  it('只有草稿可删除，驳回可编辑；服务器资格控制设为当前', () => {
    const wrapper = create()
    expect(visibleActions(wrapper, row(BomStatusEnum.DRAFT.value))).toContain('delete')
    expect(visibleActions(wrapper, row(BomStatusEnum.REJECTED.value))).toContain('edit')
    expect(visibleActions(wrapper, row(BomStatusEnum.REJECTED.value))).not.toContain('delete')
    expect(visibleActions(wrapper, row(BomStatusEnum.APPROVED.value))).not.toContain('setCurrent')
    expect(
      visibleActions(wrapper, row(BomStatusEnum.APPROVED.value, { canSetCurrent: true }))
    ).toContain('setCurrent')
    expect(visibleActions(wrapper, row(BomStatusEnum.VOIDED.value))).not.toContain('setCurrent')
    expect(
      visibleActions(wrapper, row(BomStatusEnum.APPROVED.value, { isCurrent: true }))
    ).toContain('void')
    const vm = wrapper.vm as any
    vm.handleAction('delete', row(BomStatusEnum.REJECTED.value))
    expect(wrapper.emitted('action')).toBeUndefined()
    vm.handleAction('edit', row(BomStatusEnum.DRAFT.value))
    expect(wrapper.emitted('action')?.[0]?.[0]).toBe('edit')
    wrapper.unmount()
  })
  it('任意两版本加载真实详情；切换选择后丢弃旧对比结果', async () => {
    let resolve: (data: any) => void = () => {}
    const fetch = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((r) => {
            resolve = r
          })
      )
      .mockResolvedValue({ items: [] })
    const wrapper = create({
      rows: [
        row(BomStatusEnum.DRAFT.value),
        row(BomStatusEnum.APPROVED.value, { id: 2, version: 'V1.1' }),
      ],
      fetchDetail: fetch,
    })
    const vm = wrapper.vm as any
    vm.handleAction('compare', row(BomStatusEnum.DRAFT.value))
    await nextTick()
    const pending = vm.compare()
    expect(fetch.mock.calls.map((x) => x[0])).toEqual([1, 2])
    vm.oldId = 2
    vm.newId = 1
    await nextTick()
    resolve({ items: [{ materialCode: 'M1', quantity: 2 }] })
    await pending
    expect(vm.compared).toBe(false)
    wrapper.unmount()
  })
})

describe('版本内容差异', () => {
  it('行号/审核痕迹变化不算内容变化，保留重复物料和组合子行', () => {
    const old = {
      items: [
        { itemId: 1, materialCode: 'G', quantity: 1 },
        { itemId: 2, parentMaterialId: 1, materialCode: 'M', quantity: 2, reviewStatus: 3 },
        { itemId: 3, parentMaterialId: 1, materialCode: 'M', quantity: 4 },
      ],
    }
    const same = {
      items: [
        { itemId: 10, materialCode: 'G', quantity: 1 },
        { itemId: 20, parentMaterialId: 10, materialCode: 'M', quantity: 2, reviewStatus: 1 },
        { itemId: 30, parentMaterialId: 10, materialCode: 'M', quantity: 4 },
      ],
    }
    expect(diffVersionDetails(old, same)).toEqual([])
    same.items[2].quantity = 5
    expect(diffVersionDetails(old, same)).toEqual([
      { path: 'G/M [2]', field: '单位用量', before: '4', after: '5' },
    ])
  })
  it('工艺路线嵌套子作业工时和 BOM 日期/备注变化均可见', () => {
    const diff = diffVersionDetails(
      {
        remark: '旧',
        items: [{ processId: 9, children: [{ processName: '贴合', customMachineHours: 1 }] }],
      },
      {
        remark: '新',
        items: [{ processId: 9, children: [{ processName: '贴合', customMachineHours: 2 }] }],
      }
    )
    expect(diff.map((x) => x.field)).toEqual(['备注', '自定义机器工时'])
    expect(
      diffVersionDetails(
        { effectiveDate: '2026-01-01', items: [] },
        { effectiveDate: '2026-02-01', items: [{ materialCode: '新增', quantity: 3 }] }
      ).map((x) => x.field)
    ).toEqual(['生效日期', '新增'])
  })
})
