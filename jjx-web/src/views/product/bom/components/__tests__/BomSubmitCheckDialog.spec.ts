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
    expect(text).toContain('请先审核离型纸 RP-W195-L80制造方案')
    expect(text).toContain('离型纸 RP-W195-L80 · V1.0')
    expect(text).toContain('草稿')
    expect(text).toContain('请先完成该制造方案的提交审核和批准，再回来提交当前 BOM')
    expect(text).not.toContain('SUB_BOM')
    expect(text).not.toContain('bomId=')

    const buttons = wrapper.findAll('button').map((node) => node.text())
    expect(buttons).toEqual(['查看制造方案', '关闭'])
    expect(api.submitEngineeringBom).not.toHaveBeenCalled()
    expect(api.approveEngineeringBom).not.toHaveBeenCalled()
  })

  it('点“查看制造方案”只发出目标对象信息，不提交也不批准', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(detail(4))
    const wrapper = mountDialog([rowIssue(4), graphIssue(4)])
    await flushPromises()

    const viewButton = wrapper.findAll('button').find((node) => node.text() === '查看制造方案')!
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

    const buttons = wrapper.findAll('button').filter((node) => node.text() === '查看制造方案')
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
    expect(text).toContain('制造方案信息读取失败')
    expect(text).toContain('403')
    expect(text).toContain('无法确认它是否已批准')
    expect(text).not.toContain('查看制造方案')
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
})
