/** IQC 原因/补充说明的精确清洗规则。 */
// 来源：现行界面提示串；数据库实测的人工转写变体。
export const BLACKLIST = [
  '勾选多行可整批合格；录入弹窗内 Tab 移动、Enter 保存、可"保存并下一行"；实测记录可留空',
  '选多行可整批合格',
] as const

export function sanitize(value: string | null | undefined): string {
  const normalized = (value || '').trim()
  return BLACKLIST.includes(normalized as (typeof BLACKLIST)[number]) ? '' : normalized
}

export function isValidSupplement(value: string | null | undefined, derived?: string | null): boolean {
  const supplement = sanitize(value)
  if (!supplement || supplement.length > 200) return false
  if (supplement === (derived || '').trim()) return false
  return /[\p{L}\p{N}]/u.test(supplement)
}
