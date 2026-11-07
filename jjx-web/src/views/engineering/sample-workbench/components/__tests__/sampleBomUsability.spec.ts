import { describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import BomPanel from '../BomPanel.vue'
import { ElMessageBox } from 'element-plus'

vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessageBox: { confirm: vi.fn() },
}))

function panel(readonly = false) {
  return shallowMount(BomPanel, {
    props: { bomList: [], projects: [], readonly, showTransfer: false },
    global: { stubs: { ElCard: { template: '<section><header><slot name="header" /></header><slot /></section>' } } },
  })
}

describe('打样BOM新增及删除提示', () => {
  it('空表仍能新增并说明整单保存；只读不提供新增', async () => {
    const wrapper = panel()
    expect(wrapper.text()).toContain('新增材料')
    expect(wrapper.text()).toContain('工序、印刷、BOM和工程备注一起保存')
    wrapper.findComponent({ name: 'ElButton' }).vm.$emit('click')
    expect(wrapper.emitted('add')).toHaveLength(1)
    wrapper.unmount()
    const readonly = panel(true)
    expect(readonly.findComponent({ name: 'ElButton' }).exists()).toBe(false)
    expect(readonly.text()).toContain('只读查看')
    readonly.unmount()
  })

  it('取消删除不发事件，确认明确联动源工序并发出删除', async () => {
    const wrapper = panel()
    const row = { ownerUid: 'card-1', name: 'PET', sourceLabel: '工序：冲型' }
    const remove = (wrapper.vm as any).removeMaterial
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await remove(row)
    expect(wrapper.emitted('remove')).toBeUndefined()
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')
    await remove(row)
    expect(ElMessageBox.confirm).toHaveBeenLastCalledWith(expect.stringContaining('同步删除工序：冲型'), expect.any(String), expect.any(Object))
    expect(wrapper.emitted('remove')).toEqual([[row]])
    wrapper.unmount()
  })
})
