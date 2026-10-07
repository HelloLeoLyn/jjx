import { ProcessCategoryEnum } from '@/enums/product'

/** 修改、详情与审核共用工序结构页签，OTHER 和空值均归入未分类。 */
export const ROUTE_STRUCTURE_TABS = [
  ...ProcessCategoryEnum.items
    .filter((item) => item.value !== 'OTHER')
    .map(({ value, label }) => ({ value, label })),
  { value: '', label: '未分类' },
]

export function routeStructureTabValue(category?: string): string {
  return ROUTE_STRUCTURE_TABS.some((tab) => tab.value === category) ? category! : ''
}
