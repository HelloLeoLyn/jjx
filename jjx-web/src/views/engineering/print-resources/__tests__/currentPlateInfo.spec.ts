import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import CurrentPlateInfo from '../CurrentPlateInfo.vue'
import { useCurrentPlates } from '../useCurrentPlates'
import { ScreenPlateStatusEnum } from '@/enums/engineering/resource'

const api = vi.hoisted(() => ({ getPrintPlate: vi.fn(), getPrintFrame: vi.fn() }))
vi.mock('@/api/engineering/resource', () => ({ engineeringResourceApi: api }))
const sources = [{ filmId: 31, filmNo: 'FL-001', versionId: 21, artworkNo: 'PW-001', versionNo: 'V1', styles: [{ styleNo: 'A', name: '正面', inkColor: 'RED' }] },
  { filmId: 32, filmNo: 'FL-002', versionId: 22, styles: [{ styleNo: 'B', name: '背面', inkColor: 'BLUE' }] }]
const plate = { plateId: 41, frameId: 5, plateNo: 'PL-001', status: ScreenPlateStatusEnum.PENDING.value, sourcesJson: JSON.stringify(sources), history: [{ actionType: 'create', afterStatus: ScreenPlateStatusEnum.PENDING.value, operator: 'maker' }] }
const frame = { frameId: 5, stretchId: 7, stretches: [{ stretchId: 7, tensionNcm: 20 }] }
const row = { frameId: 5, plateId: 41, stretchId: 7 }
function render(data = plate, compact = false) {
  return shallowMount(CurrentPlateInfo, { props: { plate: data, compact }, global: { renderStubDefaultSlot: true, stubs: { ElTable: { name: 'ElTable', props: ['data'], template: '<div/>' } } } })
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getPrintPlate.mockImplementation(async (id: number) => ({ data: { ...plate, plateId: id, frameId: id - 36 } }))
  api.getPrintFrame.mockResolvedValue({ data: frame })
})

describe('当前网版展示', () => {
  it('明确待检验不可选，多张菲林的名称色号真实显示，保留网版检验记录', () => {
    const w = render()
    expect(w.text()).toContain('待检验')
    expect(w.text()).toContain('框体正常不代表网版已放行')
    for (const value of ['FL-001', 'FL-002', '正面', '背面', 'RED', 'BLUE']) expect(w.text()).toContain(value)
    expect(w.findComponent({ name: 'ElTable' }).props('data')).toEqual(plate.history)
    w.unmount()
  })
  it('已放行展示实际操作人和时间，来源异常不能伪装成正常样式', async () => {
    const w = render({ ...plate, status: ScreenPlateStatusEnum.RELEASED.value, releasedBy: 'inspector', releasedTime: '2026-11-28 12:00' } as any)
    expect(w.text()).toContain('inspector'); expect(w.text()).toContain('2026-11-28 12:00')
    expect(w.text()).not.toContain('暂不可用于工艺路线')
    await w.setProps({ plate: { ...plate, sourcesJson: '[{"styles":[null]}]' } })
    expect(w.find('el-alert-stub').attributes('title')).toBe('菲林样式资料格式异常')
    w.unmount()
  })
  it('列表摘要与详情区分，旧网版没有来源时明确未记录', async () => {
    const w = render(plate, true)
    expect(w.text()).toContain('FL-001'); expect(w.text()).toContain('背面')
    expect(w.text()).not.toContain('检验记录')
    await w.setProps({ plate: { ...plate, sourcesJson: null } })
    expect(w.text()).toContain('未记录菲林样式'); w.unmount()
  })
})

describe('当前页资料补读', () => {
  function host() {
    let data!: ReturnType<typeof useCurrentPlates>
    const w = shallowMount(defineComponent({ setup() { data = useCurrentPlates(); return () => null } }))
    return { w, data }
  }
  it('只读当前页关联资料，框版对应检查，失败可单独重试', async () => {
    const { w, data } = host()
    api.getPrintPlate.mockRejectedValueOnce(new Error('offline'))
    await data.load([row, { frameId: 6 }])
    expect(api.getPrintPlate).toHaveBeenCalledOnce(); expect(api.getPrintFrame).toHaveBeenCalledOnce()
    expect(data.states.value[41].error).toBe('offline'); expect(data.stretches.value[5].stretch.tensionNcm).toBe(20)
    await data.retry(row); expect(data.states.value[41].plate.plateNo).toBe('PL-001')
    api.getPrintPlate.mockResolvedValueOnce({ data: { ...plate, frameId: 999 } })
    await data.retry(row); expect(data.states.value[41].plate).toBeUndefined(); expect(data.states.value[41].error).toContain('不一致')
    api.getPrintFrame.mockRejectedValueOnce(new Error('stretch offline')); await data.retryStretch(row)
    expect(data.stretches.value[5].error).toBe('stretch offline')
    await data.retryStretch(row); expect(data.stretches.value[5].stretch.tensionNcm).toBe(20); w.unmount()
  })
  it('分页和卸载立即丢弃旧结果，空页不继续补读', async () => {
    const { w, data } = host()
    let finish!: (value: any) => void
    api.getPrintPlate.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const first = data.load([{ frameId: 5, plateId: 41 }])
    await data.load([]); finish({ data: plate }); await first
    expect(data.states.value).toEqual({})
    api.getPrintPlate.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const second = data.load([{ frameId: 5, plateId: 41 }]); w.unmount()
    finish({ data: plate }); await second; expect(data.states.value).toEqual({})
  })
  it('整页关联请求并发最多四个，切页后不继续旧页队列', async () => {
    const { w, data } = host()
    const finishes: ((value: any) => void)[] = []
    api.getPrintPlate.mockImplementation(() => new Promise(resolve => finishes.push(resolve)))
    const first = data.load(Array.from({ length: 12 }, (_, index) => ({ plateId: 41 + index, frameId: 5 + index })))
    await flushPromises(); expect(api.getPrintPlate).toHaveBeenCalledTimes(4)
    await data.load([]); finishes.forEach((finish, index) => finish({ data: { ...plate, plateId: 41 + index, frameId: 5 + index } }))
    await first; expect(api.getPrintPlate).toHaveBeenCalledTimes(4); w.unmount()
  })
})
