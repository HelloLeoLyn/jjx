import request from '@/utils/request'
import type { R } from '@/types'
import type { MeasurementPolicy, MeasurementInput, MeasurementResult } from '@/types/inventory/measurement'

export const materialMeasurementApi = {
  preview: (data: { materialId?: number; policy?: MeasurementPolicy | null; actual: MeasurementInput }) =>
    request.post<R<MeasurementResult>>('/inventory/material/measurement/preview', data),
}
