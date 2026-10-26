import { OperLogStatusEnum } from '@/enums/system/LogEnum'

export interface ReviewHistory {
  flowId?: string
  roundNo?: number
  actionCode?: string
  actionName?: string
  fromStatus?: string | number | null
  toStatus?: string | number | null
  operatorName?: string
  comment?: string
  attachmentIds?: string
  attachmentRefs?: { refId: number; fileName?: string }[]
  createTime?: string
}

interface ReviewEvent {
  time?: string
  bizType?: string
  result?: number
  reviewFlowId?: string
  operParam?: string
}

function timestamp(value?: string) {
  return value ? new Date(value.replace(' ', 'T')).getTime() : NaN
}

export function matchReview(reviews: ReviewHistory[], row: ReviewEvent): ReviewHistory | null {
  if (row.result !== undefined && row.result !== OperLogStatusEnum.SUCCESS.value) return null
  if (row.reviewFlowId) return reviews.find((review) => review.flowId === row.reviewFlowId) || null
  const target = timestamp(row.time)
  if (row.bizType === 'quotation') {
    // 历史报价日志没有明确 ID：只接受同秒且唯一的审核动作，不取最近一条。
    let approved: boolean | undefined
    try { approved = JSON.parse(row.operParam || '{}').approved } catch { /* 历史参数可能是摘要文本。 */ }
    const candidates = reviews.filter((review) => timestamp(review.createTime) === target
      && (approved === true ? review.actionCode === 'APPROVE'
        : approved === false ? review.actionCode === 'REJECT'
          : ['APPROVE', 'REJECT'].includes(review.actionCode || '')))
    return candidates.length === 1 ? candidates[0] : null
  }
  // 其它业务的既有匹配方式保持兼容。
  if (!Number.isFinite(target)) return reviews[reviews.length - 1] || null
  let best: ReviewHistory | null = null
  let bestDiff = Number.MAX_SAFE_INTEGER
  for (const review of reviews) {
    const time = timestamp(review.createTime)
    if (!Number.isFinite(time)) continue
    const diff = Math.abs(time - target)
    if (diff < bestDiff) { best = review; bestDiff = diff }
  }
  return best || reviews[reviews.length - 1] || null
}
