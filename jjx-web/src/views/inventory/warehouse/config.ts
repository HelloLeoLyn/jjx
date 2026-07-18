// views/inventory/warehouse/config.ts
import type { SearchOptions, TableOptions } from '@/components/common-ui/type'

// 搜索配置
export const searchConfig: SearchOptions[] = [
  {
    prop: 'warehouseCode',
    label: '仓库编码',
    type: 'input',
  },
  {
    prop: 'warehouseName',
    label: '仓库名称',
    type: 'input',
  },
  {
    prop: 'warehouseType',
    label: '仓库类型',
    type: 'select',
    options: [
      { value: 'normal', label: '普通仓库' },
      { value: 'quality', label: '质检仓库' },
      { value: 'finished', label: '成品仓库' },
      { value: 'scrap', label: '废品仓库' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { value: '0', label: '正常' },
      { value: '1', label: '停用' },
    ],
  },
]

// 表格列配置
export const tableColumns: TableOptions[] = [
  {
    label: '仓库编码',
    prop: 'warehouseCode',
    width: 120,
  },
  {
    label: '仓库名称',
    prop: 'warehouseName',
    width: 180,
  },
  {
    label: '仓库类型',
    prop: 'warehouseType',
    width: 100,
    align: 'center',
    slot: 'warehouseType',
  },
  {
    label: '仓库位置',
    prop: 'location',
    minWidth: 150,
  },
  {
    label: '负责人',
    prop: 'manager',
    width: 100,
  },
  {
    label: '联系电话',
    prop: 'contactPhone',
    width: 120,
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
    width: 80,
    align: 'center',
    slot: 'status',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 160,
  },
]

// 仓库类型配置
export const warehouseTypeConfig = {
  normal: { label: '普通仓库', type: 'primary' },
  quality: { label: '质检仓库', type: 'warning' },
  finished: { label: '成品仓库', type: 'success' },
  scrap: { label: '废品仓库', type: 'danger' },
}
