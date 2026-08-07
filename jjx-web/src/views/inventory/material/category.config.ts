// views/inventory/material/category.config.ts

import type { SearchOptions, TableOptions } from '@/components/common-ui/type'

// 搜索配置
export const searchConfig: SearchOptions[] = [
  {
    prop: 'categoryCode',
    label: '分类编码',
    type: 'input',
  },
  {
    prop: 'categoryName',
    label: '分类名称',
    type: 'input',
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { value: '1', label: '正常' },
      { value: '0', label: '停用' },
    ],
  },
]

// 表格列配置
export const tableColumns: TableOptions[] = [
  {
    label: '分类编码',
    prop: 'categoryCode',
    width: 160,
    align: 'center',
  },
  {
    label: '分类名称',
    prop: 'categoryName',
    width: 180,
    align: 'center',
  },
  {
    label: '分类级别',
    prop: 'categoryLevel',
    width: 100,
    align: 'center',
  },
  {
    label: '排序',
    prop: 'sortOrder',
    width: 80,
    align: 'center',
  },
  {
    label: '状态',
    prop: 'status',
    width: 100,
    slot: 'status',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 180,
    align: 'center',
    slot: 'createTime',
  },
]

// 状态配置（StatusEnum: 1=正常/0=停用，2026-08-07 对齐）
export const statusConfig = {
  '1': { label: '正常', type: 'success' },
  '0': { label: '停用', type: 'danger' },
}

export const materialCategoryTypeConfig = {
  '0': { label: '原材料', type: 'danger' },
  '1': { label: '半成品', type: 'warning' },
  '2': { label: '成品', type: 'success' },
  '3': { label: '辅助材料', type: 'info' },
}
// 树形选择器配置
export const categoryNameTreeProps = {
  value: 'categoryName',
  label: 'categoryName',
  children: 'children',
}
