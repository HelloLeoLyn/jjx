// 库位相关类型定义

// 库位基本信息
export interface InventoryStorageLocation {
  locationId: number
  locationCode: string
  locationName: string
  warehouseId: number
  parentId?: number | null
  capacityEnabled?: boolean
  capacityUnit?: string | null
  width?: number | null
  height?: number | null
  depth?: number | null
  sortOrder?: number
  usedCapacity?: number | null
  warehouseName: string
  areaCode: string
  shelfCode: string
  levelCode: string
  positionCode: string
  locationType: string
  capacity: number | null
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
  parentId?: number | null
  capacityEnabled?: boolean
  capacityUnit?: string | null
  width?: number | null
  height?: number | null
  depth?: number | null
  sortOrder?: number
  locationCode: string
  locationName: string
  warehouseId: number
  areaCode: string
  shelfCode: string
  levelCode: string
  positionCode: string
  locationType: string
  capacity: number | null
  status: string
  remark: string
}

// 库位更新DTO
export interface StorageLocationUpdateDTO extends StorageLocationSaveDTO {
  locationId: number | undefined
}

export interface LocationBalance { unit: string; received: number; issued: number; quantity: number; reserved: number }
export interface LocationNode extends InventoryStorageLocation {
  leaf: boolean
  path: string
  balances: LocationBalance[]
  children: LocationNode[]
}
export interface LocationBatch {
  item_id: number
  material_code: string
  material_name: string
  batch_no: string
  quantity: number
  reserved_quantity: number
  unit: string
  roll_no?: string
}
export interface LocationSubdivision {
  children: { locationName: string; locationCode: string }[]
  allocations: { stockItemId: number; childIndex: number; quantity: number }[]
  confirmed: boolean
}
