// src/enums/inventory/WarehouseEnum.ts
import { createEnum } from '../base'

/**
 * 仓库类型枚举
 */
export const WarehouseTypeEnum = createEnum({
  items: [
    { value: 'normal', label: '普通仓库', tagProps: { type: 'primary' } },
    { value: 'quality', label: '质检仓库', tagProps: { type: 'primary' } },
    { value: 'finished', label: '成品仓库', tagProps: { type: 'primary' } },
    { value: 'scrap', label: '废品仓库', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'primary' },
})

/**
 * 仓库状态枚举
 */
export const WarehouseStatusEnum = createEnum({
  items: [
    { value: '0', label: '正常', tagProps: { type: 'primary' } },
    { value: '1', label: '停用', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'primary' },
})

/**
 * 仓库列表枚举（静态数据，替代API调用）
 * 用于 WarehouseSelector 等组件
 */
export const WarehouseListEnum = createEnum({
  items: [
    { value: 1, label: '原材料仓库', tagProps: { type: 'primary' } },
    { value: 2, label: '半成品仓库', tagProps: { type: 'primary' } },
    { value: 3, label: '成品仓库', tagProps: { type: 'primary' } },
    { value: 4, label: '质检仓库', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'primary' },
})

/**
 * 仓库列表数据（含编码），供 WarehouseSelector 使用
 */
export const WAREHOUSE_LIST = [
  { warehouseId: 1, warehouseName: '原材料仓库', warehouseCode: 'RAW', status: 0 },
  { warehouseId: 2, warehouseName: '半成品仓库', warehouseCode: 'SEMI', status: 0 },
  { warehouseId: 3, warehouseName: '成品仓库', warehouseCode: 'FINISHED', status: 0 },
  { warehouseId: 4, warehouseName: '质检仓库', warehouseCode: 'QC', status: 0 },
]

/**
 * 仓库相关枚举统一导出
 */
export const WarehouseEnum = {
  type: WarehouseTypeEnum,
  status: WarehouseStatusEnum,
  list: WarehouseListEnum,
}
