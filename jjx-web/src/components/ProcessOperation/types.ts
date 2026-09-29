export interface ProcessOperationItem {
  key?: string | number
  icon?: string
  processName: string
  indexNumber?: number | null
  hasIndex?: number
  hasWorkInstruction?: number
  workInstruction?: string
}
