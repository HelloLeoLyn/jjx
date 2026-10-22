/**
 * BOM 导入：规格及模数单列反推（dev-20261022-016）。
 * 口径：specification = 「跳」或「=」之前的整段文本；split = 跳与 = 之间；
 *       moduleQty = =nPCS 的 n；baseQty = N个/PCS 的 N，否则缺省 1。
 */
export interface ParsedSpecModule {
  specification: string
  split: string
  moduleQty: number
  baseQty: number
}

export function parseSpecModule(raw: unknown): ParsedSpecModule | null {
  const text = String(raw ?? '').trim()
  if (!text) return null

  // 计件材料：6个/PCS = 模数1、基数6
  const pieces = text.match(/^(\d+(?:\.\d+)?)\s*个\s*\/\s*PCS$/i)
  if (pieces) {
    return { specification: '', split: '', moduleQty: 1, baseQty: Number(pieces[1]) }
  }

  // 通用：规格[跳跳距]=模数PCS
  const combined = text.match(/^(.*?)(?:跳(.*?))?=\s*(\d+(?:\.\d+)?)\s*PCS$/i)
  if (!combined) return null
  const specification = (combined[1] || '').trim()
  const split = (combined[2] || '').trim()
  const moduleQty = Number(combined[3])
  if (!Number.isFinite(moduleQty) || moduleQty <= 0) return null
  if (!specification && !split) return null
  return { specification, split, moduleQty, baseQty: 1 }
}
