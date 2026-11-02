import { describe, expect, it } from 'vitest'
import {
  WORK_SPEC_EMPTY_BLOCK_KEYS,
  parseHiddenBlocks,
  serializeHiddenBlocks,
  workSpecEmptyBlocks,
} from '../workSpecBlocks'

describe('作业规范空块开关（dev-20261102-002）', () => {
  it('三块固定为 凹凸条件/备注/变更内容，默认全部显示', () => {
    expect(workSpecEmptyBlocks.map((block) => block.label)).toEqual(['凹凸条件', '备注', '变更内容'])
    expect(WORK_SPEC_EMPTY_BLOCK_KEYS).toEqual(['emboss', 'notes', 'change'])
    expect(serializeHiddenBlocks([])).toBe('')
    expect(serializeHiddenBlocks(undefined)).toBe('')
  })

  it('打印链接往返：只序列化合法键、顺序固定、忽略脏值', () => {
    expect(serializeHiddenBlocks(['notes', 'emboss'])).toBe('emboss,notes')
    expect(parseHiddenBlocks('emboss,change')).toEqual(['emboss', 'change'])
    expect(parseHiddenBlocks(' EMBOSS , notes ,,未知')).toEqual(['notes'])
    expect(parseHiddenBlocks('')).toEqual([])
    expect(parseHiddenBlocks(undefined)).toEqual([])
    expect(parseHiddenBlocks(serializeHiddenBlocks(['change']))).toEqual(['change'])
  })
})
