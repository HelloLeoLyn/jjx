/**
 * 字典项 ext_data 工具：品牌缩写 abbr 的读写（dev-20261107-014）。
 *
 * 背景：品牌字典的 item_value 是物料表存量引用（不改），缩写放 ext_data.abbr，
 * 命名规则「品牌·缩写」勾选项取它；这里只做 JSON 读写，避免页面里裸 operation。
 */
export function parseExtObject(ext?: string): Record<string, unknown> {
  if (!ext) return {}
  try {
    const parsed = JSON.parse(ext)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}

/** 取品牌缩写；未配置或格式异常返回空串。 */
export function parseAbbr(ext?: string): string {
  const abbr = parseExtObject(ext).abbr
  return typeof abbr === 'string' ? abbr : ''
}

/** 写入/清除缩写：保留 ext_data 里其它键；无任何键时返回 undefined（不写空 JSON）。 */
export function withAbbr(ext: string | undefined, abbr: string): string | undefined {
  const next = { ...parseExtObject(ext) }
  const value = abbr.trim().toUpperCase()
  if (value) next.abbr = value
  else delete next.abbr
  return Object.keys(next).length ? JSON.stringify(next) : undefined
}

/** 品牌缩写格式：字母或数字，1-8 位（保存时统一转大写）；空串视为合法（未配置）。 */
export function isValidAbbr(abbr: string): boolean {
  const value = abbr.trim()
  return value === '' || /^[A-Za-z0-9]{1,8}$/.test(value)
}
