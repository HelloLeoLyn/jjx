import type { ApprovalResult } from '@/enums/common/ApprovalEnum'

export interface ApprovalOpinion {
  result: ApprovalResult
  remark: string
}
