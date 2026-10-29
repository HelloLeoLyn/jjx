import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
vi.mock('@/components/Selector/BomMaterialSelector.vue', () => ({
  default: { template: '<span class="material-selector" />' },
}))
import request from '@/utils/request'
import { loadBomOriginalValues, originalValuesFromDetail } from '@/api/product/bomOriginalValues'
import { BomSaveLogResult } from '@/types/product/bom'
import { flushPromises, mount } from '@vue/test-utils'
import BomItemReviewTable from '../BomItemReviewTable.vue'

const change = (itemId: number, oldValue: string) => ({ itemId, field: 'materialName', oldValue, newValue: '现名' })
const saved = (oldValue: string) => ({ action: '修改BOM', result: BomSaveLogResult.SUCCESS, detail: { itemChanges: [change(10, oldValue)] } })
const get = vi.mocked(request.get)
beforeEach(() => get.mockReset())

describe('最近一次BOM保存的结构化原值', () => {
  it('同码/空码多行各按itemId取值，特殊字符及空串不经中文句子解析', () => {
    expect(originalValuesFromDetail(JSON.stringify({ itemChanges: [change(10, '旧]→名\n甲'), change(11, ''), { ...change(12, '旧规格'), field: 'specification' }] })))
      .toEqual({ '10': { materialName: '旧]→名\n甲' }, '11': { materialName: '' }, '12': { specification: '旧规格' } })
    expect(originalValuesFromDetail({ changes: ['材料名称[M]:旧→新'] })).toEqual({})
  })
  it('升序两次保存取最后一次，失败修改不覆盖最近成功保存', async () => {
    get.mockResolvedValueOnce({ data: { records: [saved('最早'), saved('最近'), { ...saved('失败'), result: 0 }], total: 3 } } as any)
    expect(await loadBomOriginalValues(5)).toEqual({ '10': { materialName: '最近' } })
  })
  it('超过一页从末页回查，最近一页无修改时继续回查上一页', async () => {
    get.mockImplementation(async (_url, config: any) => {
      if (!config?.params?.pageNum) return { data: {} } as any
      const page = config.params.pageNum
      return { data: { total: 201, totalPages: 3, records: page === 2 ? [saved('末次保存')] : [{ action: '行审核', result: BomSaveLogResult.SUCCESS }] } } as any
    })
    expect(await loadBomOriginalValues(5)).toEqual({ '10': { materialName: '末次保存' } })
    expect(get.mock.calls.map(call => (call[1] as any).params.pageNum)).toEqual([1, 3, 2])
  })
  it('最近成功保存无改名记录或历史无结构化记录时返回空，不借用较早前值', async () => {
    for (const detail of [null, { itemChanges: [] }, { changes: ['旧日志'] }]) {
      get.mockResolvedValueOnce({ data: { records: [saved('过时'), { ...saved('无改动'), detail }], total: 2 } } as any)
      expect(await loadBomOriginalValues(5)).toEqual({})
    }
  })
  it('审核表原材料名称按当前行值显示，不读取流水原值', async () => {
    const rows = [{ itemId: 10, materialCode: '同码', materialName: '新甲' }, { itemId: 11, materialCode: '同码', materialName: '新乙' }, { itemId: 12, materialCode: '', materialName: '新丙' }]
    const wrapper = mount(BomItemReviewTable, { props: { items: rows as any } })
    await flushPromises()
    expect(wrapper.text()).toContain('新甲')
    expect(wrapper.text()).toContain('新乙')
    expect(wrapper.text()).toContain('新丙')
    expect(wrapper.text()).not.toContain('旧甲')
    wrapper.unmount()
  })
})
