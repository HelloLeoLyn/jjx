import { createNamedEnum } from '@/enums/base'

export const ScreenFrameStatusEnum = createNamedEnum(
  {
    READY: { value: 'READY', label: '可用', tagProps: { type: 'success' } },
    EMPTY: { value: 'EMPTY', label: '空框', tagProps: { type: 'info' } },
    PLATED: { value: 'PLATED', label: '已制版', tagProps: { type: 'success' } },
    MAINTENANCE: { value: 'MAINTENANCE', label: '维护中', tagProps: { type: 'warning' } },
    SCRAPPED: { value: 'SCRAPPED', label: '已报废', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

export const DieStatusEnum = createNamedEnum(
  {
    AVAILABLE: { value: 'AVAILABLE', label: '可用', tagProps: { type: 'success' } },
    MAINTENANCE: { value: 'MAINTENANCE', label: '维护中', tagProps: { type: 'warning' } },
    STOPPED: { value: 'STOPPED', label: '停用', tagProps: { type: 'info' } },
    REPLACED: { value: 'REPLACED', label: '已重做', tagProps: { type: 'info' } },
    SCRAPPED: { value: 'SCRAPPED', label: '已报废', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

export const DieActionEnum = createNamedEnum(
  {
    REPAIR: { value: 'REPAIR', label: '送修', tagProps: { type: 'warning' } },
    ENABLE: { value: 'ENABLE', label: '恢复可用', tagProps: { type: 'success' } },
    STOP: { value: 'STOP', label: '停用', tagProps: { type: 'info' } },
    REMAKE: { value: 'REMAKE', label: '重做', tagProps: { type: 'primary' } },
    SCRAP: { value: 'SCRAP', label: '报废', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

/** 印刷资源缺口台账状态（print_kit_shortage.status）。 */
export const PrintKitShortageStatusEnum = createNamedEnum(
  {
    OPEN: { value: 0, label: '待处理', tagProps: { type: 'warning' } },
    CLOSED: { value: 1, label: '已关闭', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)
