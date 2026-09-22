import { createNamedEnum } from '../base'
export const CapaStatus={PENDING_ANALYSIS:'PENDING_ANALYSIS',ACTION_IN_PROGRESS:'ACTION_IN_PROGRESS',PENDING_VERIFICATION:'PENDING_VERIFICATION',CLOSED:'CLOSED'} as const
export const CapaStatusEnum=createNamedEnum({
  PENDING_ANALYSIS:{value:CapaStatus.PENDING_ANALYSIS,label:'待分析',tagProps:{type:'warning'}},
  ACTION_IN_PROGRESS:{value:CapaStatus.ACTION_IN_PROGRESS,label:'措施执行中',tagProps:{type:'primary'}},
  PENDING_VERIFICATION:{value:CapaStatus.PENDING_VERIFICATION,label:'待验证',tagProps:{type:'info'}},
  CLOSED:{value:CapaStatus.CLOSED,label:'已关闭',tagProps:{type:'success'}},
},{type:'info'})
