import { beforeEach, describe, expect, it, vi } from 'vitest'

const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: http }))

import { productBomApi } from '../bom'
import type { EngineeringBomItem, ManufacturingBomSavePayload } from '@/types/product/bom'

const row: EngineeringBomItem = {
  itemId: 1,
  materialId: 109,
  materialCode: 'RM000105',
  materialName: 'PC-800',
  specification: '195mm',
  unit: 'M',
  quantity: 0.1667,
  moduleQty: 6,
  baseQty: 1,
  lossRate: 0,
  sortOrder: 1,
}

const payload: ManufacturingBomSavePayload = {
  bomCode: 'SFG000345-MFG-A',
  bomName: '中间件制造方案-甲',
  outputType: 'MATERIAL',
  outputId: 349,
  items: [row],
}

beforeEach(() => {
  Object.values(http).forEach((fn) => fn.mockReset())
})

describe('制造BOM API 契约（路径/方法/request·Result 封装沿用既有约定）', () => {
  it('候选方案：GET /engineering/bom/manufacturing/options，查询参数用 outputType/outputId/productId', () => {
    productBomApi.manufacturingOptions({ outputType: 'MATERIAL', outputId: 349, productId: 1 })
    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/engineering/bom/manufacturing/options', {
      params: { outputType: 'MATERIAL', outputId: 349, productId: 1 },
    })
  })

  it('没有产品上下文时不伪造 productId', () => {
    productBomApi.manufacturingOptions({ outputType: 'MATERIAL', outputId: 349 })
    expect(http.get.mock.calls[0][1].params).toEqual({
      outputType: 'MATERIAL',
      outputId: 349,
    })
  })

  it('新建/编辑制造方案：POST /manufacturing、PUT /manufacturing/{bomId}', () => {
    productBomApi.saveManufacturingBom(payload)
    expect(http.post).toHaveBeenCalledWith('/engineering/bom/manufacturing', payload)
    productBomApi.updateManufacturingBom(124, payload)
    expect(http.put).toHaveBeenCalledWith('/engineering/bom/manufacturing/124', payload)
  })

  it('采用方案：POST /manufacturing/{bomId}/adopt，无请求体', () => {
    productBomApi.adoptManufacturingBom(124)
    expect(http.post).toHaveBeenCalledWith('/engineering/bom/manufacturing/124/adopt', null)
  })

  it('父BOM已保存选择：GET /engineering/bom/{bomId}/scheme-choices', () => {
    productBomApi.getBomSchemeChoices(9)
    expect(http.get).toHaveBeenCalledWith('/engineering/bom/9/scheme-choices')
  })

  it('制造BOM保存与父产品BOM保存是两条路径，互不替代', () => {
    productBomApi.updateManufacturingBom(124, payload)
    productBomApi.editEngineeringBom({
      bomCode: 'P42-BOM',
      bomName: 'P42-BOM',
      productId: 42,
      productCode: 'P42',
      productName: '产品甲',
      isCurrent: false,
      items: [row],
      schemeChoices: [{ materialId: 109, bomId: 124, isPrimary: true }],
      schemeRevision: 7,
    })
    expect(http.put.mock.calls[0][0]).toBe('/engineering/bom/manufacturing/124')
    expect(http.put.mock.calls[1][0]).toBe('/engineering/bom')
    // 没有任何删除/发布入口被顺带调用
    expect(http.delete).not.toHaveBeenCalled()
  })
})
