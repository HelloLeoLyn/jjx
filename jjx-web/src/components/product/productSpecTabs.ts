/**
 * 产品作业规范抽屉页签注册表（dev-20261010-032）
 * 抽屉页签的 name/label/顺序/「未纳入发布版本」提示由本表驱动；
 * 各页签内容仍按 name 渲染（内容需要的 props 各不相同，不适合全部塞进配置）。
 */
export interface ProductSpecTab {
  /** el-tab-pane 的 name（同时作为内容渲染的分支键） */
  name: string
  label: string
  /** 查看历史发布版本时的「未纳入本次发布」提示；空 = 该页签随发布版本快照 */
  notIncluded?: string
}

export const PRODUCT_SPEC_TABS: ProductSpecTab[] = [
  {
    name: 'customer',
    label: '客供资料',
    notIncluded: '未纳入本次发布：客供资料仅反映当前工作态，不随发布版本快照。',
  },
  {
    name: 'sample',
    label: '样品',
    notIncluded: '未纳入本次发布：样品需求单仅为当前执行记录，不随发布版本快照（样品资料随版本发布）。',
  },
  {
    name: 'pick-order',
    label: '领料单',
    notIncluded: '未纳入本次发布：领料单为执行记录，不随发布版本快照。',
  },
  { name: 'spec', label: '作业规范' },
  {
    name: 'ink',
    label: '油墨调配记录表',
    notIncluded: '未纳入本次发布：油墨调配记录为执行记录，不随发布版本快照。',
  },
  { name: 'atlas', label: '工程图集' },
  { name: 'color', label: '分色检查表' },
]
