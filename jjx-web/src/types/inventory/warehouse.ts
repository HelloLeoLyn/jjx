// 仓库相关类型定义

// 仓库基本信息
export interface InventoryWarehouse {
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  location: string
  manager: string
  contactPhone: string
  sortOrder: number
  status: string
  remark: string
  createTime: string
  updateTime: string
}

// 仓库查询参数
export interface InventoryWarehouseQueryParams {
  current: number
  pageSize: number
  warehouseCode?: string
  warehouseName?: string
  warehouseType?: string
  status?: string
}

// 仓库保存DTO
export interface WarehouseSaveDTO {
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  location: string
  manager: string
  contactPhone: string
  sortOrder: number
  status: string
  remark: string
}

// 仓库更新DTO
export interface WarehouseUpdateDTO extends WarehouseSaveDTO {
  warehouseId: number | undefined
}

// 仓库状态更新参数
export interface WarehouseStatusUpdateParams {
  warehouseId: number
  status: string
}

// 批量操作参数
export interface WarehouseBatchParams {
  ids: number[]
}
