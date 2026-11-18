export type MeasurementMode = 'SAME' | 'PIECE_AREA' | 'LENGTH_AREA' | 'ROLL_AREA' | 'PIECE_WEIGHT' | 'AREA_WEIGHT' | 'MEASURED_WEIGHT'
export interface MeasurementPolicy {
  physicalUnit: string
  priceUnit: string
  mode: MeasurementMode
  unitWeightG?: number | null
}
export interface MeasurementInput {
  physicalQuantity: number
  widthMm?: number | null
  lengthMm?: number | null
  rollLengthM?: number | null
  unitWeightG?: number | null
  grammage?: number | null
  actualWeightKg?: number | null
}
export interface MeasurementResult {
  physicalUnit: string
  priceUnit: string
  mode: MeasurementMode
  physicalQuantity: number
  priceQuantity: number
  formula: string
  actual: MeasurementInput
}
