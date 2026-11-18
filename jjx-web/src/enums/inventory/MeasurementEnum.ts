import { createNamedEnum } from '../base'

export const MeasurementModeEnum = createNamedEnum({
  SAME: { value: 'SAME', label: '同单位，数量不变', tagProps: { type: 'info' } },
  PIECE_AREA: { value: 'PIECE_AREA', label: '片数×宽×长→面积', tagProps: { type: 'info' } },
  LENGTH_AREA: { value: 'LENGTH_AREA', label: '米数×宽→面积', tagProps: { type: 'info' } },
  ROLL_AREA: { value: 'ROLL_AREA', label: '按卷实际宽长→面积', tagProps: { type: 'info' } },
  PIECE_WEIGHT: { value: 'PIECE_WEIGHT', label: '数量×单重→公斤', tagProps: { type: 'info' } },
  AREA_WEIGHT: { value: 'AREA_WEIGHT', label: '面积×克重→公斤', tagProps: { type: 'info' } },
  MEASURED_WEIGHT: { value: 'MEASURED_WEIGHT', label: '按本次实际净重', tagProps: { type: 'info' } },
}, { type: 'info' })
