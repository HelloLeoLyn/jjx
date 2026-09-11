/**
 * 系统标签类型定义（dev-20260911-007）
 */

/** 标签（主数据） */
export interface SysTag {
  tagId?: number
  tagCode?: string
  tagName?: string
  tagGroup: string
  parentId?: number | null
  sortOrder?: number
  /** 1启用 0停用 */
  status?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 标签关联的业务类型常量 */
export const TAG_BIZ_TYPE = {
  /** 供应商 */
  SUPPLIER: 'purchase_supplier',
} as const

/** 标签分组编码常量（对应字典 sys_tag_group） */
export const TAG_GROUP = {
  /** 供应商供货品类 */
  SUPPLIER_GOODS: 'supplier_goods',
} as const
