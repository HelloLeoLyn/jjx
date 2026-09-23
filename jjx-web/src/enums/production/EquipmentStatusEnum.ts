import { createNamedEnum } from '../base'

/** 生产设备运行状态，对应后端 EquipmentStatusEnum。 */
export const EquipmentStatusEnum = createNamedEnum(
  {
    STANDBY: { value: 0, label: '待机中', tagProps: { type: 'info' } },
    RUNNING: { value: 1, label: '运行中', tagProps: { type: 'success' } },
    MAINTENANCE: { value: 2, label: '维护中', tagProps: { type: 'warning' } },
    FAULT: { value: 3, label: '故障中', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

export const AVAILABLE_EQUIPMENT_STATUSES = [
  EquipmentStatusEnum.STANDBY.value,
  EquipmentStatusEnum.RUNNING.value,
] as const
