import { describe, it, expect } from 'vitest'
import {
  buildReviewPackageScopes,
  summarizeReviewPackages,
  reviewActionLabel,
} from '../BomReviewScope.vue'
import type { BomReviewPackage } from '@/types/product/bom'

const plan = (overrides: Partial<BomReviewPackage> = {}): BomReviewPackage => ({
  rootBomId: 10,
  rootBomCode: 'BOM-P10',
  rootBomVersion: 'V1.1',
  rootStatus: 1,
  actionCount: 2,
  reuseCount: 1,
  pendingReuseCount: 0,
  executable: true,
  blockingIssues: [],
  nodes: [
    {
      bomId: 21,
      bomCode: 'AUX000347-MFG',
      bomVersion: 'V1.0',
      status: 1,
      packageRevision: 'rev-21',
      plannedAction: 'SUBMIT',
    },
    {
      bomId: 10,
      bomCode: 'BOM-P10',
      bomVersion: 'V1.1',
      status: 1,
      packageRevision: 'rev-10',
      plannedAction: 'SUBMIT',
    },
    {
      bomId: 22,
      bomCode: 'AUX000348-MFG',
      bomVersion: 'V1.0',
      status: 3,
      packageRevision: 'rev-22',
      plannedAction: 'REUSE',
    },
  ],
  ...overrides,
})

describe('BOM 整套审核范围', () => {
  it('只提交可执行产品，并按预览结果原样带出方案版本/状态/内容指纹', () => {
    const scopes = buildReviewPackageScopes([plan()], 'SUBMIT')
    expect(scopes).toHaveLength(1)
    expect(scopes[0].rootBomId).toBe(10)
    expect(scopes[0].expectedVersion).toBe('V1.1')
    expect(scopes[0].expectedNodes.map((x) => x.bomId)).toEqual([21, 10, 22])
    expect(scopes[0].expectedNodes[0]).toEqual({
      bomId: 21,
      version: 'V1.0',
      status: 1,
      packageRevision: 'rev-21',
    })
  })

  it('驳回只带根产品BOM，不连带公共下层方案', () => {
    const scopes = buildReviewPackageScopes([plan()], 'REJECT')
    expect(scopes[0].expectedNodes.map((x) => x.bomId)).toEqual([10])
  })

  it('阻断产品不进入执行范围；缺失版本或指纹也不得执行', () => {
    const blocked = plan({ rootBomId: 11, executable: false, blockingIssues: [{ message: '引用范围已变化' }] })
    const noVersion = plan({ rootBomId: 12, rootBomVersion: '' })
    const noRevision = plan({
      rootBomId: 13,
      nodes: [{ bomId: 13, bomVersion: 'V1.0', status: 2, packageRevision: '' }],
    })
    expect(buildReviewPackageScopes([blocked, noVersion, noRevision], 'SUBMIT')).toEqual([])
    expect(summarizeReviewPackages([plan(), blocked])).toEqual({ executable: 1, blocked: 1 })
  })

  it('动作名称稳定，供范围确认与结果页复用', () => {
    expect(reviewActionLabel('SUBMIT')).toBe('整套送审')
    expect(reviewActionLabel('APPROVE')).toBe('整套通过')
    expect(reviewActionLabel('REJECT')).toBe('整套驳回')
  })
})
