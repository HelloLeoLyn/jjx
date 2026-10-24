/**
 * 打样工序计划左栏（标准工序库）宽度口径（dev-20261024-010）。
 * 默认 300px，可拖动调整，上限 400px；宽度记在本地，刷新后保持。
 */
export const PICKER_MIN_WIDTH = 220
export const PICKER_MAX_WIDTH = 400
export const PICKER_DEFAULT_WIDTH = 300
export const PICKER_WIDTH_STORAGE_KEY = 'jjx.sample.planPickerWidth'

/** 夹到 [220, 400]；非法值回落默认 300。 */
export function clampPickerWidth(value: unknown): number {
  if (value === null || value === undefined || value === '') return PICKER_DEFAULT_WIDTH
  const width = Number(value)
  if (!Number.isFinite(width)) return PICKER_DEFAULT_WIDTH
  return Math.min(PICKER_MAX_WIDTH, Math.max(PICKER_MIN_WIDTH, Math.round(width)))
}

type StorageLike = { getItem(key: string): string | null; setItem(key: string, value: string): void } | null | undefined

export function readPickerWidth(storage: StorageLike): number {
  if (!storage) return PICKER_DEFAULT_WIDTH
  try {
    return clampPickerWidth(storage.getItem(PICKER_WIDTH_STORAGE_KEY))
  } catch {
    return PICKER_DEFAULT_WIDTH
  }
}

export function savePickerWidth(storage: StorageLike, width: number): void {
  if (!storage) return
  try {
    storage.setItem(PICKER_WIDTH_STORAGE_KEY, String(clampPickerWidth(width)))
  } catch {
    // 隐私模式/配额满时忽略，不影响交互
  }
}
