import request from '@/utils/request'

export interface EquipmentQuery {
  pageNum?: number
  pageSize?: number
  equipmentNo?: string
  equipmentName?: string
  equipmentType?: string
  status?: number
}

export interface ProductionEquipment {
  equipmentId: number
  equipmentNo: string
  equipmentName: string
  equipmentType?: string
  model?: string
  department?: string
  location?: string
  status: number
  utilization?: number
  lastMaintenance?: string
  nextMaintenance?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

// 分页查询设备
export function getEquipmentPage(params: EquipmentQuery) {
  return request({
    url: '/production/equipment/page',
    method: 'get',
    params,
  })
}

// 查询设备列表（全量，用于统计）
export function getEquipmentList(params?: EquipmentQuery) {
  return request({
    url: '/production/equipment/list',
    method: 'get',
    params,
  })
}

// 查询设备详情
export function getEquipmentById(id: number) {
  return request({
    url: `/production/equipment/${id}`,
    method: 'get',
  })
}

// 新增设备
export function createEquipment(data: Partial<ProductionEquipment>) {
  return request({
    url: '/production/equipment',
    method: 'post',
    data,
  })
}

// 修改设备
export function updateEquipment(data: Partial<ProductionEquipment>) {
  return request({
    url: '/production/equipment',
    method: 'put',
    data,
  })
}

// 删除设备
export function deleteEquipment(id: number) {
  return request({
    url: `/production/equipment/${id}`,
    method: 'delete',
  })
}
