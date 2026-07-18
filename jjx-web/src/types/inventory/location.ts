// 库位相关类型定义

// 库位基本信息
export interface InventoryStorageLocation {
  locationId: number
  locationCode: string
  locationName: string
  warehouseId: number
  warehouseName: string
  areaCode: string
  shelfCode: string
  levelCode: string
  positionCode: string
  locationType: string
  capacity: number
  currentQuantity: number
  status: string
  remark: string
  createTime: string
  updateTime: string
}

// 库位查询参数
export interface InventoryStorageLocationQueryParams {
  pageNum: number
  pageSize: number
  locationCode?: string
  locationName?: string
  locationType?: string
  warehouseId?: number | undefined
  areaCode?: string
  status?: string
}

// 库位保存DTO
export interface StorageLocationSaveDTO {
  locationCode: string
  locationName: string
  warehouseId: number
  areaCode: string
  shelfCode: string
  levelCode: string
  positionCode: string
  locationType: string
  capacity: number
  status: string
  remark: string
}

// 库位更新DTO
export interface StorageLocationUpdateDTO extends StorageLocationSaveDTO {
  locationId: number | undefined
}
