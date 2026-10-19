import { ProcessCategoryEnum } from '@/enums/product'

/**
 * 工序分类业务顺序（面板 → 上线 → 下线 → 其他），以 ProcessCategoryEnum 定义顺序为唯一来源。
 * 图标区 tab 与下方组合工序表格 tab 共用这一份顺序，不再各自按数据到达顺序排列。
 */
const PROCESS_CATEGORY_ORDER: string[] = ProcessCategoryEnum.items.map((item) => item.value)

export const UNCATEGORIZED_LABEL = '未分类'

/**
 * 未分类/未知分类统一归为空字符串，其余分类保持原值。
 * 未知分类不能被当成一个新的业务分类插到前面。
 */
export function normalizeProcessCategory(category?: string | null): string {
  return category && PROCESS_CATEGORY_ORDER.includes(category) ? category : ''
}

/** 排序权重：面板 0 / 上线 1 / 下线 2 / 其他 3，未分类与未知分类固定末位。 */
export function processCategorySortOrder(category?: string | null): number {
  const index = PROCESS_CATEGORY_ORDER.indexOf(normalizeProcessCategory(category))
  return index === -1 ? PROCESS_CATEGORY_ORDER.length : index
}

/** tab 标签：未分类与未知分类都显示为「未分类」。 */
export function processCategoryLabel(category?: string | null): string {
  const key = normalizeProcessCategory(category)
  return key ? ProcessCategoryEnum.getLabel(key) : UNCATEGORIZED_LABEL
}

export interface ProcessCategoryGroup<T> {
  key: string
  label: string
  options: T[]
}

/**
 * 按业务顺序把工序收进分类分组（图标区 tab 用）。
 * 同一分类合并为一组，未分类/未知分类合并到空 key 组并固定末位；组内保留传入顺序。
 */
export function groupProcessesByCategory<T>(
  items: T[],
  getCategory: (item: T) => string | undefined | null
): ProcessCategoryGroup<T>[] {
  const groups = new Map<string, T[]>()
  items.forEach((item) => {
    const key = normalizeProcessCategory(getCategory(item))
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(item)
  })
  return Array.from(groups.entries())
    .sort((a, b) => processCategorySortOrder(a[0]) - processCategorySortOrder(b[0]))
    .map(([key, options]) => ({ key, label: processCategoryLabel(key), options }))
}

/** 修改、详情与审核共用工序结构页签，OTHER 和空值均归入未分类。 */
export const ROUTE_STRUCTURE_TABS = [
  ...PROCESS_CATEGORY_ORDER.filter((value) => value !== 'OTHER').map((value) => ({
    value,
    label: processCategoryLabel(value),
  })),
  { value: '', label: UNCATEGORIZED_LABEL },
]

export function routeStructureTabValue(category?: string): string {
  return ROUTE_STRUCTURE_TABS.some((tab) => tab.value === category) ? category! : ''
}
