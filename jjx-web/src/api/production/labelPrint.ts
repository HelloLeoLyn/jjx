import request from '@/utils/request'

export function recordLabelPrint(bizId: string) {
  return request.post('/production/label-print/log', null, { params: { bizId } })
}
