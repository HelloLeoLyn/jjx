import { describe, expect, it, vi } from 'vitest'
import { BomStatusEnum } from '@/enums/product'
import type { BomCheckIssue } from '@/types/product/bom'
import {
  buildSubmitCheckView,
  describeIssueLocation,
  loadSchemeDetails,
  mergeSubmitIssues,
  normalizeSchemeDetail,
  parseSubBomIssue,
  schemeDisplayName,
} from '../bomSubmitCheck'
import type { SchemeDetailResult } from '../bomSubmitCheck'

const rowIssue = (bomId = 4): BomCheckIssue => ({
  itemId: 9,
  materialCode: 'RP-W195-L80',
  materialName: '离型纸 RP-W195-L80',
  field: 'subBomId',
  message: `SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=${bomId}）`,
})
const graphIssue = (bomId = 4): BomCheckIssue => ({
  field: 'subBomId',
  message: `SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=${bomId}）`,
})
const schemeIssue = (bomId = 4): BomCheckIssue => ({
  field: 'schemeChoices',
  message: `SUB_BOM_NOT_APPROVED：产品主方案未批准（bomId=${bomId}）`,
})

const okDetail = (bomId: number, status = BomStatusEnum.DRAFT.value, over: Record<string, any> = {}) => ({
  ok: true as const,
  detail: {
    bomId,
    name: '离型纸 RP-W195-L80制造方案',
    code: 'AUX000348-MFG',
    version: 'V1.0',
    status,
    outputType: 'MATERIAL',
    outputId: 352,
    ...over,
  },
})
const details = (...items: SchemeDetailResult[]) => new Map(items.map((item) => [item.ok ? item.detail.bomId : item.bomId, item]))

describe('BOM 提交预检：子方案提示解析与去重', () => {
  it('同一次预检的三条同因提示合并为一项，并保留命中次数', () => {
    const merged = mergeSubmitIssues([rowIssue(), graphIssue(), schemeIssue()])
    expect(merged).toHaveLength(1)
    expect(merged[0]).toMatchObject({
      kind: 'sub-bom',
      code: 'SUB_BOM_NOT_APPROVED',
      bomId: 4,
      count: 3,
    })
  })

  it('不同子方案分别保留，不能误合并', () => {
    const merged = mergeSubmitIssues([rowIssue(4), rowIssue(8), schemeIssue(8)])
    expect(merged.filter((item) => item.kind === 'sub-bom')).toHaveLength(2)
    expect(merged[0]).toMatchObject({ bomId: 4, count: 1 })
    expect(merged[1]).toMatchObject({ bomId: 8, count: 2 })
  })

  it('相同方案的不同阻断原因不合并', () => {
    const merged = mergeSubmitIssues([
      rowIssue(4),
      { field: 'subBomId', message: 'SUB_BOM_NOT_FOUND：下层方案不存在（bomId=4）' },
    ])
    expect(merged).toHaveLength(2)
    expect(merged.map((item) => (item.kind === 'sub-bom' ? item.code : ''))).toEqual([
      'SUB_BOM_NOT_APPROVED',
      'SUB_BOM_NOT_FOUND',
    ])
  })

  it('其他校验错误完整保留并带定位信息', () => {
    const merged = mergeSubmitIssues([
      rowIssue(),
      { itemId: 11, materialCode: 'M-11', materialName: '面板膜', field: 'quantity', message: '用量必须大于0' },
      { itemId: 12, field: 'parentMaterialId', message: '父节点不存在' },
    ])
    const view = buildSubmitCheckView(merged, details(okDetail(4)))
    expect(view.schemeItems).toHaveLength(1)
    expect(view.otherItems).toHaveLength(2)
    expect(view.otherItems[0].message).toBe('用量必须大于0')
    expect(view.otherItems[0].location).toContain('M-11')
    expect(view.otherItems[0].location).toContain('用量')
    expect(view.otherItems[1].message).toBe('父节点不存在')
    expect(view.otherItems[1].location).toContain('明细 #12')
  })

  it('没有 message 的其他问题也不能无声消失', () => {
    const view = buildSubmitCheckView(mergeSubmitIssues([{ itemId: 20, field: 'items' }]), new Map())
    expect(view.otherItems).toHaveLength(1)
    expect(view.otherItems[0].message).toContain('未描述的问题')
  })

  it('解析不到 bomId 或不是两类提示时按其他问题保留', () => {
    expect(parseSubBomIssue({ message: 'SUB_BOM_NOT_APPROVED：未给出方案' })).toBeNull()
    expect(parseSubBomIssue({ message: 'BOM_CYCLE：显式制造关系形成环' })).toBeNull()
    expect(parseSubBomIssue(null)).toBeNull()
    expect(parseSubBomIssue({ message: 'sub_bom_not_approved：x（bomId=4）' })).toBeNull()
    expect(parseSubBomIssue({ message: 'SUB_BOM_NOT_FOUND：产品主方案引用不存在（bomId=7）' })).toEqual({
      code: 'SUB_BOM_NOT_FOUND',
      bomId: 7,
    })
  })
})

describe('BOM 提交预检：可读文案与真实状态', () => {
  it('草稿方案给出名称、版本、实际状态和下一步，主展示不含错误码与 bomId', () => {
    const view = buildSubmitCheckView(mergeSubmitIssues([rowIssue(), graphIssue(), schemeIssue()]), details(okDetail(4)))
    const item = view.schemeItems[0]
    expect(item.title).toBe('请先审核离型纸 RP-W195-L80生产BOM')
    expect(item.statusLabel).toBe('草稿')
    expect(item.message).toBe(
      '当前 BOM 使用的『离型纸 RP-W195-L80 · V1.0』当前状态为「草稿」，尚未批准，因此暂时不能提交审核。请先完成该生产BOM的提交审核和批准，再回来提交当前 BOM。'
    )
    const visible = [item.title, item.message, item.statusLabel || ''].join(' ')
    expect(visible).not.toContain('SUB_BOM')
    expect(visible).not.toContain('bomId=')
    expect(item.canNavigate).toBe(true)
    expect(item.target).toEqual({
      bomId: 4,
      outputType: 'MATERIAL',
      outputId: 352,
      name: '离型纸 RP-W195-L80制造方案',
      version: 'V1.0',
    })
  })

  it.each([
    [BomStatusEnum.REVIEWING.value, '审核中'],
    [BomStatusEnum.REJECTED.value, '已驳回'],
    [BomStatusEnum.VOIDED.value, '已作废'],
  ])('状态 %s 如实显示为「%s」，不一律说草稿', (status, label) => {
    const view = buildSubmitCheckView(mergeSubmitIssues([rowIssue()]), details(okDetail(4, status)))
    const item = view.schemeItems[0]
    expect(item.statusLabel).toBe(label)
    expect(item.message).toContain(`当前状态为「${label}」`)
    expect(item.message).not.toContain('草稿')
  })

  it('预检说未批准但详情已批准时不撒谎，要求刷新核对', () => {
    const view = buildSubmitCheckView(
      mergeSubmitIssues([rowIssue()]),
      details(okDetail(4, BomStatusEnum.APPROVED.value))
    )
    expect(view.schemeItems[0].message).toContain('系统当前显示为「已批准」')
    expect(view.schemeItems[0].message).toContain('请刷新后重新提交')
  })

  it('详情为 403/网络失败时明确提示、不能当通过、不能导航', () => {
    const failed = details({ ok: false, bomId: 4, error: 'Request failed with status code 403' })
    const view = buildSubmitCheckView(mergeSubmitIssues([rowIssue()]), failed)
    const item = view.schemeItems[0]
    expect(item.title).toBe('生产BOM信息读取失败')
    expect(item.message).toContain('403')
    expect(item.message).toContain('无法确认它是否已批准')
    expect(item.canNavigate).toBe(false)
    expect(item.error).toContain('403')
  })

  it('预检引用的方案确实不存在时给出修正指引', () => {
    const notFound = { field: 'subBomId', message: 'SUB_BOM_NOT_FOUND：下层方案不存在（bomId=4）' }
    const view = buildSubmitCheckView(mergeSubmitIssues([notFound]), new Map())
    expect(view.schemeItems[0].title).toBe('引用的生产BOM已不存在')
    expect(view.schemeItems[0].canNavigate).toBe(false)
    expect(view.schemeItems[0].message).toContain('暂时不能提交审核')
  })

  it('只读详情按不同 bomId 去重，每个至多请求一次', async () => {
    const fetch = vi.fn(async (bomId: number) => ({
      bomId,
      bomName: `方案${bomId}`,
      bomCode: `B${bomId}`,
      bomVersion: 'V1.0',
      approveStatus: BomStatusEnum.DRAFT.value,
      outputType: 'MATERIAL',
      outputId: 352,
    }))
    const result = await loadSchemeDetails([4, 4, 8], fetch)
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(fetch.mock.calls.map(([id]) => id).sort()).toEqual([4, 8])
    expect(result.size).toBe(2)
    expect(result.get(4)?.ok).toBe(true)
    expect(result.get(8)?.ok).toBe(true)
  })

  it('单个方案读取失败不吞掉其他方案', async () => {
    const fetch = vi.fn(async (bomId: number) => {
      if (bomId === 4) throw new Error('方案不存在或无权查看')
      return { bomName: '其他方案', bomVersion: 'V2.0', approveStatus: BomStatusEnum.REVIEWING.value, outputType: 'MATERIAL', outputId: 9 }
    })
    const result = await loadSchemeDetails([4, 8], fetch)
    expect(result.get(4)).toMatchObject({ ok: false })
    expect(result.get(8)).toMatchObject({ ok: true })
  })

  it('详情字段缺失时如实回退，不伪造版本或状态', () => {
    const detail = normalizeSchemeDetail(4, { bomName: '', approveStatus: undefined })
    expect(detail.name).toBe('未命名生产BOM')
    expect(detail.version).toBe('版本未知')
    expect(detail.status).toBeUndefined()
  })

  it('HTML 名称按原始文本进入文案，由 Vue 文本插值转义', () => {
    const view = buildSubmitCheckView(
      mergeSubmitIssues([rowIssue()]),
      details(okDetail(4, BomStatusEnum.DRAFT.value, { name: '<img src=x onerror=alert(1)>制造方案' }))
    )
    expect(view.schemeItems[0].message).toContain('<img src=x onerror=alert(1)>')
  })

  it('名称与字段定位辅助函数', () => {
    expect(schemeDisplayName('离型纸 RP-W195-L80制造方案')).toBe('离型纸 RP-W195-L80')
    expect(schemeDisplayName('普通方案')).toBe('普通方案')
    expect(describeIssueLocation({ itemId: 3, field: 'unknownField' })).toBe('明细 #3 · unknownField')
  })
})
