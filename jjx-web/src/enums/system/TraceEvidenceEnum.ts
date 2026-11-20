import { createNamedEnum } from '../base'
/** BOM流水的证据来源；档案补全不宣称原操作成功。 */
export const TraceEvidenceSourceEnum = createNamedEnum({
  OPERATION: { value: 'OPER_LOG', label: '操作日志', tagProps: { type: 'success' } },
  ARCHIVE: { value: 'BOM_VERSION_ARCHIVE', label: '历史档案补全', tagProps: { type: 'info' } },
  LEGACY_REQUEST: { value: 'BOM_LEGACY_REQUEST', label: '历史请求记录', tagProps: { type: 'warning' } },
}, { type: 'info' })
