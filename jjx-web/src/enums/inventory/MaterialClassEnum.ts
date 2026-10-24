import { createEnum } from '../base'
import type { MaterialClassStatusCode } from '@/types/inventory/classify'

/** 物料归类三态（与后端 MaterialClassStatus 一致，dev-20261024-004 阶段三）。 */
export const MaterialClassStatusEnum = createEnum<MaterialClassStatusCode>({
  items: [
    { value: 'unclassified', label: '未归类', tagProps: { type: 'danger' } },
    { value: 'rule_unconfirmed', label: '规则命中未确认', tagProps: { type: 'warning' } },
    { value: 'confirmed', label: '人工确认', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/** 列表行 → 三态；未归类优先，其次人工确认，规则命中未确认。 */
export function resolveClassStatus(row: {
  categoryId?: number | null
  classConfirmed?: boolean | null
  classSource?: string | null
}): MaterialClassStatusCode {
  if (row.categoryId == null) return 'unclassified'
  if (row.classConfirmed) return 'confirmed'
  if (row.classSource === 'RULE') return 'rule_unconfirmed'
  return 'confirmed'
}

/** 规则关键词建议：取原名称/当前名称，去掉括号内容与空白后截前 6 字，可由用户改。 */
export function suggestRuleKeyword(names: Array<string | null | undefined>): string {
  const source = names.map((name) => (name || '').trim()).find(Boolean) || ''
  const cleaned = source
    .replace(/[（(][^）)]*[）)]/g, '')
    .replace(/\s+/g, '')
    .replace(/[【】\[\]]/g, '')
  return cleaned.slice(0, 6)
}
