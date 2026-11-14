import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BomSubmitCheckDialog from '../BomSubmitCheckDialog.vue'
import { BomStatusEnum } from '@/enums/product'
import type { BomCheckIssue } from '@/types/product/bom'

const api = vi.hoisted(() => ({
  getEngineeringBomInfo: vi.fn(),
  submitEngineeringBom: vi.fn(),
  approveEngineeringBom: vi.fn(),
}))
vi.mock('@/api/product/bom', () => ({ productBomApi: api }))

const ElDialogStub = defineComponent({
  name: 'ElDialog',
  props: ['modelValue', 'title', 'width'],
  template: '<div class="el-dialog-stub" :data-title="title"><slot /><slot name="footer" /></div>',
})
const ElButtonStub = defineComponent({
  name: 'ElButton',
  props: ['type', 'link'],
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})
const ElTagStub = defineComponent({
  name: 'ElTag',
  template: '<span class="el-tag-stub"><slot /></span>',
})
const ElAlertStub = defineComponent({
  name: 'ElAlert',
  props: ['title', 'type'],
  template: '<div class="el-alert-stub">{{ title }}</div>',
})

const rowIssue = (bomId: number): BomCheckIssue => ({
  itemId: 9,
  materialCode: 'RP-W195-L80',
  materialName: '离型纸 RP-W195-L80',
  field: 'subBomId',
  message: `SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=${bomId}）`,
})
const graphIssue = (bomId: number): BomCheckIssue => ({
  field: 'subBomId',
  message: `SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=${bomId}）`,
})
const schemeIssue = (bomId: number): BomCheckIssue => ({
  field: 'schemeChoices',
  message: `SUB_BOM_NOT_APPROVED：产品主方案未批准（bomId=${bomId}）`,
})

const detail = (bomId: number, over: Record<string, any> = {}) => ({
  data: {
    bomId,
    bomCode: 'AUX000348-MFG',
    bomName: '离型纸 RP-W195-L80制造方案',
    bomVersion: 'V1.0',
    approveStatus: BomStatusEnum.DRAFT.value,
    outputType: 'MATERIAL',
    outputId: 352,
    ...over,
  },
})

const deferred = <T = any>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: any) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const mountDialog = (issues: BomCheckIssue[], targetName = 'V1.0') =>
  mount(BomSubmitCheckDialog, {
    props: { modelValue: true, issues, targetName },
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElButton: ElButtonStub,
        ElTag: ElTagStub,
        ElAlert: ElAlertStub,
      },
    },
  })

const viewButtons = (wrapper: ReturnType<typeof mountDialog>) =>
  wrapper.findAll('button').filter((node) => node.text() === '查看生产BOM')

beforeEach(() => {
  api.getEngineeringBomInfo.mockReset()
  api.submitEngineeringBom.mockReset()
  api.approveEngineeringBom.mockReset()
})

describe('BOM 提交预检弹窗', () => {
  it('三条同因提示合并为一条可读阻断项，展示名称/版本/实际状态/下一步', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(detail(4))
    const wrapper = mountDialog([rowIssue(4), graphIssue(4), schemeIssue(4)])
    await flushPromises()

    expect(api.getEngineeringBomInfo).toHaveBeenCalledTimes(1)
    expect(api.getEngineeringBomInfo).toHaveBeenCalledWith(4)
    expect(wrapper.findAll('[data-issue-code="SUB_BOM_NOT_APPROVED"]')).toHaveLength(1)
    const text = wrapper.text()
    expect(text).toContain('请先审核离型纸 RP-W195-L80生产BOM')
    expect(text).toContain('离型纸 RP-W195-L80 · V1.0')
    expect(text).toContain('草稿')
    expect(text).toContain('请先完成该生产BOM的提交审核和批准，再回来提交当前 BOM')
    expect(text).not.toContain('SUB_BOM')
    expect(text).not.toContain('bomId=')

    const buttons = wrapper.findAll('button').map((node) => node.text())
    expect(buttons).toEqual(['查看生产BOM', '关闭'])
    expect(api.submitEngineeringBom).not.toHaveBeenCalled()
    expect(api.approveEngineeringBom).not.toHaveBeenCalled()
  })

  it('点“查看生产BOM”只发出目标对象信息，不提交也不批准', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(detail(4))
    const wrapper = mountDialog([rowIssue(4), graphIssue(4)])
    await flushPromises()

    const viewButton = wrapper.findAll('button').find((node) => node.text() === '查看生产BOM')!
    await viewButton.trigger('click')

    expect(wrapper.emitted('navigate')?.[0]?.[0]).toEqual({
      bomId: 4,
      outputType: 'MATERIAL',
      outputId: 352,
      name: '离型纸 RP-W195-L80制造方案',
      version: 'V1.0',
    })
    expect(api.submitEngineeringBom).not.toHaveBeenCalled()
    expect(api.approveEngineeringBom).not.toHaveBeenCalled()
  })

  it('不同子方案分别展示、分别定位，不复用父 BOM 身份', async () => {
    api.getEngineeringBomInfo.mockImplementation(async (bomId: number) =>
      detail(bomId, { bomName: `方案${bomId}`, outputId: 300 + bomId })
    )
    const wrapper = mountDialog([rowIssue(4), rowIssue(8)])
    await flushPromises()

    expect(api.getEngineeringBomInfo.mock.calls.map(([id]) => id).sort()).toEqual([4, 8])
    expect(wrapper.findAll('[data-issue-code="SUB_BOM_NOT_APPROVED"]')).toHaveLength(2)

    const buttons = wrapper.findAll('button').filter((node) => node.text() === '查看生产BOM')
    expect(buttons).toHaveLength(2)
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('navigate')?.map(([target]) => target.outputId)).toEqual([304, 308])
  })

  it('详情 403/网络失败时明确提示、保留重新读取入口、不能当通过', async () => {
    api.getEngineeringBomInfo.mockRejectedValue(new Error('Request failed with status code 403'))
    const wrapper = mountDialog([rowIssue(4), graphIssue(4)])
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('生产BOM信息读取失败')
    expect(text).toContain('403')
    expect(text).toContain('无法确认它是否已批准')
    expect(text).not.toContain('查看生产BOM')
    expect(wrapper.findAll('button').map((node) => node.text())).toContain('重新读取')

    await wrapper.findAll('button').find((node) => node.text() === '重新读取')!.trigger('click')
    await flushPromises()
    expect(api.getEngineeringBomInfo).toHaveBeenCalledTimes(2)
  })

  it('其他校验错误继续显示并带定位，不被子方案去重吞掉', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(detail(4))
    const wrapper = mountDialog([
      rowIssue(4),
      { itemId: 11, materialCode: 'M-11', materialName: '面板膜', field: 'quantity', message: '用量必须大于0' },
    ])
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('其他需要处理的问题')
    expect(text).toContain('M-11')
    expect(text).toContain('用量必须大于0')
  })

  it('方案名称含 HTML 时按文本显示，不生成标签', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(
      detail(4, { bomName: '<img src=x onerror=alert(1)>离型纸制造方案' })
    )
    const wrapper = mountDialog([rowIssue(4)])
    await flushPromises()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>离型纸')
  })

  it('审核中状态如实显示，不写成草稿', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(
      detail(4, { approveStatus: BomStatusEnum.REVIEWING.value })
    )
    const wrapper = mountDialog([rowIssue(4)])
    await flushPromises()

    expect(wrapper.text()).toContain('审核中')
    expect(wrapper.text()).not.toContain('草稿')
  })

  it('关闭后为另一张 BOM 重新打开：新详情返回前不出现上一张方案、不能导航，返回后只展示新方案', async () => {
    const nextDetail = deferred()
    api.getEngineeringBomInfo.mockImplementation(async (bomId: number) =>
      bomId === 7 ? nextDetail.promise : detail(4)
    )
    const wrapper = mountDialog([rowIssue(4)], '当前版本')
    await flushPromises()
    expect(viewButtons(wrapper)).toHaveLength(1)
    expect(wrapper.text()).toContain('离型纸 RP-W195-L80 · V1.0')

    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(wrapper.text()).not.toContain('离型纸')
    expect(viewButtons(wrapper)).toHaveLength(0)

    await wrapper.setProps({ modelValue: true, issues: [rowIssue(7)] })
    await flushPromises()

    // 7 的详情还没回来：本轮只允许“正在读取”，旧方案 4 的名称/版本/按钮必须消失。
    expect(wrapper.text()).toContain('正在读取相关生产BOM信息')
    expect(wrapper.text()).not.toContain('离型纸')
    expect(wrapper.text()).not.toContain('RP-W195-L80')
    expect(wrapper.text()).not.toContain('V1.0')
    expect(viewButtons(wrapper)).toHaveLength(0)
    expect(wrapper.emitted('navigate')).toBeUndefined()
    expect(api.getEngineeringBomInfo).toHaveBeenCalledTimes(2)
    expect(api.getEngineeringBomInfo.mock.calls.map(([id]) => id).sort()).toEqual([4, 7])

    nextDetail.resolve(
      detail(7, { bomName: '另一物料制造方案', bomCode: 'AUX000349-MFG', outputId: 353 })
    )
    await flushPromises()

    expect(wrapper.text()).toContain('另一物料')
    expect(wrapper.text()).not.toContain('离型纸')
    expect(viewButtons(wrapper)).toHaveLength(1)
    await viewButtons(wrapper)[0].trigger('click')
    expect(wrapper.emitted('navigate')?.[0]?.[0]).toMatchObject({
      bomId: 7,
      outputType: 'MATERIAL',
      outputId: 353,
    })
  })

  it('旧方案详情晚返回不会覆盖新预检结果', async () => {
    const oldDetail = deferred()
    api.getEngineeringBomInfo.mockImplementation(async (bomId: number) =>
      bomId === 4
        ? oldDetail.promise
        : detail(7, { bomName: '另一物料制造方案', outputId: 353 })
    )
    const wrapper = mountDialog([rowIssue(4)])
    await flushPromises()
    expect(viewButtons(wrapper)).toHaveLength(0)

    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    await wrapper.setProps({ modelValue: true, issues: [rowIssue(7)] })
    await flushPromises()
    expect(wrapper.text()).toContain('另一物料')
    expect(viewButtons(wrapper)).toHaveLength(1)

    // 上一轮的 4 此时才返回，不能把 7 的结果盖掉，也不能多出可导航的旧按钮。
    oldDetail.resolve(detail(4))
    await flushPromises()

    expect(wrapper.text()).toContain('另一物料')
    expect(wrapper.text()).not.toContain('离型纸 RP-W195-L80 · V1.0')
    expect(viewButtons(wrapper)).toHaveLength(1)
    await viewButtons(wrapper)[0].trigger('click')
    expect(wrapper.emitted('navigate')?.map(([target]) => target.outputId)).toEqual([353])
  })

  it('详情较慢时立即显示本轮其他校验问题，且不给旧方案导航', async () => {
    const slowDetail = deferred()
    api.getEngineeringBomInfo.mockReturnValue(slowDetail.promise)
    const wrapper = mountDialog([
      rowIssue(4),
      { itemId: 11, materialCode: 'M-11', materialName: '面板膜', field: 'quantity', message: '用量必须大于0' },
    ])
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('正在读取相关生产BOM信息')
    expect(text).toContain('其他需要处理的问题')
    expect(text).toContain('M-11')
    expect(text).toContain('用量必须大于0')
    expect(viewButtons(wrapper)).toHaveLength(0)
    expect(wrapper.emitted('navigate')).toBeUndefined()
  })
})
