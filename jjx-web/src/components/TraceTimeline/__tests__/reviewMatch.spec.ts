import { describe, expect, it } from 'vitest'
import { matchReview } from '../reviewMatch'

const first = { flowId: 'qf-1', actionCode: 'APPROVE', createTime: '2026-10-26 15:44:14', comment: '第一轮' }
const second = { flowId: 'qf-2', actionCode: 'APPROVE', createTime: first.createTime, comment: '第二轮' }
const event = { bizType: 'quotation', time: first.createTime, result: 1 }

describe('报价审核详情精确关联', () => {
  it('同秒多条审核按明确流水ID匹配，不取数组中的第一条', () => {
    expect(matchReview([first, second], { ...event, reviewFlowId: 'qf-2' })).toBe(second)
  })
  it('明确ID未找到时不回退猜测', () => {
    expect(matchReview([first], { ...event, reviewFlowId: 'qf-3' })).toBeNull()
  })
  it('历史记录仅接受同秒唯一审核，忽略提交审核动作', () => {
    expect(matchReview([{ ...first, actionCode: 'SUBMIT_REVIEW' }, second], event)).toBe(second)
    expect(matchReview([first, second], event)).toBeNull()
    expect(matchReview([first], { ...event, time: '2026-10-26 15:44:15' })).toBeNull()
  })
  it('历史记录用明确审核结果区分同秒通过和驳回', () => {
    const rejected = { ...first, flowId: 'qf-3', actionCode: 'REJECT' }
    expect(matchReview([first, rejected], { ...event, operParam: '{"approved":false}' })).toBe(rejected)
  })
  it('失败审核不借用后续成功审核的意见或附件', () => {
    expect(matchReview([first], { ...event, result: 0 })).toBeNull()
  })
})
