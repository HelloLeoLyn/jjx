import { createNamedEnum } from '../base'
/** 即时库存筛选交互，提交时映射现有后端布尔筛选。 */
export const StockSearchStateEnum = createNamedEnum({
  LOW: { value: 'low', label: '低库存', tagProps: { type: 'warning' } },
  EXPIRING: { value: 'expiring', label: '临期', tagProps: { type: 'danger' } },
  OBSOLETE: { value: 'obsolete', label: '呆滞', tagProps: { type: 'info' } },
}, { type: 'info' })
