/**
 * 打样备料门禁（dev-20261110-003）
 *
 * 覆盖：
 * 1）未备料（NONE）时开始/完成工序被拦截，提示可操作（去销售→样品单生成领料单）；
 * 2）备料中（PARTIAL）与齐套（FULL）不拦截——缺料/部分发料只提示，不阻断打样；
 * 3）没有备料数据（接口失败/历史单）时不误拦。
 */
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useProcessExecution } from '../useProcessExecution'

function execution(pickState: string | null) {
  return useProcessExecution({
    readonlyMode: ref(false),
    orderId: ref(1),
    savingPlan: ref(false),
    refreshCard: async () => {},
    loadSummary: async () => {},
    pickStatus: ref(pickState ? { state: pickState, label: pickState } : null),
  })
}

const readyCard = () => ({ status: 0, items: [{ processId: 11 }] })
const printRow = { processId: 21 }

describe('打样备料门禁', () => {
  it('未备料时开始/完成工序被拦截，并给出可操作提示', () => {
    const { blockReason, printBlockReason } = execution('NONE')
    expect(blockReason(readyCard())).toContain('尚未生成领料单')
    expect(printBlockReason(printRow)).toContain('尚未生成领料单')
  })

  it('备料中与齐套都不拦截', () => {
    for (const state of ['PARTIAL', 'FULL']) {
      const { blockReason, printBlockReason } = execution(state)
      expect(blockReason(readyCard())).toBeNull()
      expect(printBlockReason(printRow)).toBeNull()
    }
  })

  it('没有备料状态数据时不拦截（历史单/接口失败不误伤）', () => {
    const { blockReason, printBlockReason } = execution(null)
    expect(blockReason(readyCard())).toBeNull()
    expect(printBlockReason(printRow)).toBeNull()
  })
})
