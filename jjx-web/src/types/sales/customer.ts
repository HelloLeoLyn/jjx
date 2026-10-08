/** 客户查询参数 */
export interface CustomerQueryParams {
  pageNum: number
  pageSize: number
  customerCode?: string
  customerName?: string
  customerType?: number
  customerStatus?: number
  orderByColumn?: string
  isAsc?: 'asc' | 'desc'
}

/** 客户表单数据（新增/修改） */
export interface CustomerFormData {
  customerId?: number
  customerCode: string
  customerName: string
  customerShortName: string
  customerType?: number
  customerLevel?: number
  customerStatus?: number
  industryCategory: string
  customerSource?: number
  contactPerson: string
  contactPhone: string
  contactEmail: string
  fax: string
  country?: string
  province?: string
  city?: string
  address: string
  postalCode?: string
  creditLimit: number
  usedCreditLimit: number
  customerScore: number
  paymentMethod?: number
  paymentTermType?: string
  creditDays?: number
  creditStartBasis?: string
  vip: boolean
  remark: string
}

/** 客户列表项 */
export interface CustomerItem {
  customerId: number
  customerCode: string
  customerName: string
  customerShortName?: string
  customerType?: number
  customerLevel?: number
  customerStatus: number
  industryCategory?: string
  customerSource?: number
  contactPerson: string
  contactPhone: string
  contactEmail?: string
  fax?: string
  country?: string
  province?: string
  city?: string
  postalCode?: string
  address?: string
  creditLimit: number
  usedCreditLimit: number
  customerScore: number
  paymentMethod?: number
  paymentTermType?: string
  creditDays?: number
  creditStartBasis?: string
  vip: boolean
  remark?: string
  createTime: string
  updateTime?: string
}

/** 客户详情 */
export interface CustomerDetail extends CustomerItem {
  createTime: string
  updateTime: string
}

/** 客户搜索返回（下拉选择用） */
export interface CustomerSearchVO {
  customerId: number
  customerCode: string
  customerName: string
  customerShortName?: string
  contactPerson: string
  contactPhone: string
  email: string
  country?: string
  province?: string
  city?: string
  postalCode?: string
  address: string
  creditLimit: number
  status: number
  remark: string
}

/** 客户VO（后端返回完整信息） */
export interface CustomerVO {
  customerId: number
  customerCode: string
  customerName: string
  customerShortName?: string
  customerType?: number
  customerLevel?: number
  customerStatus: number
  industryCategory?: string
  customerSource?: number
  contactPerson: string
  contactPhone: string
  contactEmail?: string
  fax?: string
  address: string
  creditLimit: number
  usedCreditLimit: number
  customerScore: number
  paymentMethod?: number
  paymentTermType?: string
  creditDays?: number
  creditStartBasis?: string
  vip: boolean
  remark: string
  createTime: string
  updateTime: string
}

/** 客户收货地址（地址簿，dev-20261008-029） */
export interface SalesCustomerAddress {
  addressId?: number
  customerId?: number
  /** 地址标签，如 上海总部/东莞仓 */
  label?: string
  contactPerson?: string
  contactPhone?: string
  country?: string
  province?: string
  city?: string
  /** 详细地址 */
  address: string
  postalCode?: string
  /** 是否默认 0否 1是 */
  isDefault?: number
  remark?: string
  updateTime?: string
}
