import { describe, it, expect } from 'vitest'
import { extractFailedRootIds } from '../BomBatchReviewDialog.vue'
import type { BomReviewBatchResult } from '@/types/product/bom'

const result = (failures: BomReviewBatchResult['failures']): BomReviewBatchResult => ({
  action: 'SUBMIT',
  successes: [
    {
      rootBomId: 1,
      rootBomVersion: 'V1.1',
      productCode: 'YL1025PWOO',
      nodes: [{ bomId: 2, action: 'SUBMIT' }],
    },
  ],
  failures,
  sharedNodes: [],
})

describe('BOM 整套审核结果', () => {
  it('失败产品 ID 去重且忽略空值，供只重试失败产品', () => {
    expect(
      extractFailedRootIds(
        result([
          { rootBomId: 7, reason: '方案内容已变化' },
          { rootBomId: 7, reason: '重复失败' },
          { rootBomId: undefined, reason: '空根' },
          { rootBomId: 8, reason: '状态已变化' },
        ])
      )
    ).toEqual([7, 8])
  })

  it('全部成功时没有重试项', () => {
    expect(extractFailedRootIds(result([]))).toEqual([])
    expect(extractFailedRootIds(null)).toEqual([])
  })
})
