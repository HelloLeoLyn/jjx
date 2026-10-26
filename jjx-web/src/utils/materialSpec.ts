/**
 * 材料「规格及模数」文本的唯一实现（dev-20261026-009）。
 *
 * 作业规范正文/打印（docset.workSpecMaterialSpec）、BOM 打印（bomPrintLayout.bomMaterialSpec）
 * 与定版固化（jjx-server ProductWorkSpecService.projectMaterialParams）必须给出同一句话，
 * 这里收敛成一份实现；后端按同一判定顺序镜像，改动时三处一起过用例。
 *
 * 判定顺序（命中即停）：
 * 1）无尺寸、无跳距、单位 PCS/个、模数=1 → 「规格 + N个/PCS」
 *    （N = 基数 ÷ 模数，基数空缺按 1；计件材料没有拼版概念，不再打成「规格=1PCS」）。
 * 2）有跳距或规格 → 「规格(+跳X)=模数PCS」（卷料/拼版口径，保持不变）。
 * 3）无规格的计件兜底：基数=1 且模数为空或 0<模数<1 → 「1个/PCS」（dev-20261022-020 口径）。
 */
export interface MaterialSpecSource {
  specification?: string | null
  widthMm?: number | string | null
  lengthMm?: number | string | null
  split?: string | null
  moduleQty?: number | string | null
  baseQty?: number | string | null
  unit?: string | null
}

/** 去掉富文本标签与首尾空白，保留原文（打印唯一文本口径）。 */
export function plainText(value: unknown): string {
  return String(value ?? '')
    .replace(/<[^>]*>/g, '')
    .trim()
}

/** 规格纯零值（0/0.0/0.00）按空处理，避免打印出 0=1PCS。 */
export function isZeroSpec(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value.trim())
}

function decimalText(value: number): string {
  return String(Number(value.toFixed(4)))
}

export function composeMaterialSpec(row?: MaterialSpecSource | null): string {
  if (!row) return ''
  const width = Number(row.widthMm)
  const length = Number(row.lengthMm)
  const hasWidth = Number.isFinite(width) && width > 0
  const hasLength = Number.isFinite(length) && length > 0
  const rawSpecification = plainText(row.specification)
  const specification =
    hasWidth && hasLength
      ? width + '*' + length + 'mm'
      : isZeroSpec(rawSpecification)
        ? ''
        : rawSpecification
  const split = plainText(row.split)
  const moduleRaw = row.moduleQty
  const module = Number(moduleRaw)
  const moduleMissing = moduleRaw == null || plainText(moduleRaw) === '' || !Number.isFinite(module)
  const unit = plainText(row.unit).toUpperCase()
  const piece = unit === 'PCS' || unit === '个'
  const baseRaw = Number(row.baseQty)
  const base = Number.isFinite(baseRaw) && baseRaw > 0 ? baseRaw : 1

  // 1）计件材料：规格 + N个/PCS（模数=1 且无尺寸无跳距）
  if (piece && !moduleMissing && module === 1 && !split && !hasWidth && !hasLength)
    return specification + decimalText(base / module) + '个/PCS'
  // 2）卷料/拼版：规格(+跳距)=模数PCS
  if (split) return specification + '跳' + split + (module > 0 ? '=' + module + 'PCS' : '')
  if (specification) return specification + (module > 0 ? '=' + module + 'PCS' : '')
  // 3）无规格的计件兜底
  if (piece && base === 1 && (moduleMissing || (module > 0 && module < 1))) return '1个/PCS'
  return ''
}
