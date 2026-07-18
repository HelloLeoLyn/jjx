/**
 * 字典管理类型定义
 */

/** 字典类型 */
export interface SysDict {
  dictId?: number
  dictCode: string
  dictName: string
  remark?: string
  sortOrder?: number
  isActive?: number
  createTime?: string
  updateTime?: string
  items?: SysDictItem[]
}

/** 字典类型DTO */
export interface SysDictDTO {
  dictId?: number
  dictCode?: string
  dictName?: string
  remark?: string
  sortOrder?: number
  isActive?: number
}

/** 字典项 */
export interface SysDictItem {
  itemId?: number
  dictCode: string
  itemKey: string
  itemValue: string
  label?: string
  remark?: string
  sortOrder?: number
  isActive?: number
  extData?: string
  createTime?: string
  updateTime?: string
}

/** 字典项DTO */
export interface SysDictItemDTO {
  itemId?: number
  dictCode?: string
  itemKey?: string
  itemValue?: string
  label?: string
  remark?: string
  sortOrder?: number
  isActive?: number
  extData?: string
}
