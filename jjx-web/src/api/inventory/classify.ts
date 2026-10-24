import request from '@/utils/request'
import type { R } from '@/types'
import type {
  ApplyRulesResult,
  ClassifyBatchRequest,
  ClassifyBatchResult,
  MaterialClassifyRule,
} from '@/types/inventory/classify'

const base = '/inventory/material/classify'
export const materialClassifyApi = {
  rules: () => request.get<R<MaterialClassifyRule[]>>(`${base}/rules`),
  createRule: (rule: MaterialClassifyRule) =>
    request.post<R<MaterialClassifyRule>>(`${base}/rules`, rule),
  updateRule: (rule: MaterialClassifyRule) =>
    request.put<R<MaterialClassifyRule>>(`${base}/rules`, rule),
  deleteRule: (id: number) => request.delete<R<void>>(`${base}/rules`, { params: { id } }),
  batchAssign: (data: ClassifyBatchRequest) =>
    request.post<R<ClassifyBatchResult>>(`${base}/batch`, data),
  applyRules: (confirm = false) =>
    request.post<R<ApplyRulesResult>>(`${base}/apply-rules`, null, { params: { confirm } }),
  confirm: (materialId: number) => request.post<R<void>>(`${base}/${materialId}/confirm`),
}
