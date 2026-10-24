export interface TableOptions {
  label: string
  prop?: string
  width?: number
  minWidth?: number
  align?: 'left' | 'center' | 'right'
  sortable?: boolean
  fixed?: boolean | 'left' | 'right'
  slot?: string
  enumObj?: any
  tagSize?: 'large' | 'default' | 'small'
  formatter?: (row: any, column: any, cellValue: any, index: number) => string
  hidden?: boolean
}

export interface ToolbarOptions {
  key: string
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  plain?: boolean
  icon?: any
  disabled?: boolean
  loading?: boolean
  permission?: string
  permissions?: string[]
  role?: string
  onClick?: () => void
}

export interface SearchOptions {
  prop: string
  label: string
  type: 'input' | 'select' | 'daterange' | 'date' | 'custom' | 'tree'
  options?: Array<{ value: any; label: string }>
  /** 是否独占一行（custom 类字段如标签筛选常用，dev-20260912-009） */
  block?: boolean
  /** 高级筛选：默认折叠，有值时自动展开（dev-20261024-025） */
  advanced?: boolean
  /** 控件宽度（px），不传保持原默认（input 180 / select 150）（dev-20261024-026） */
  width?: number
}

export interface FormOptions {
  prop: string
  label: string
  type:
    | 'input'
    | 'textarea'
    | 'number'
    | 'select'
    | 'radio'
    | 'checkbox'
    | 'switch'
    | 'date'
    | 'datetime'
    | 'tree'
    | 'slot'
  placeholder?: string
  required?: boolean
  /** 是否禁用，支持布尔值或根据表单数据动态计算的函数 */
  disabled?: boolean | ((formData: Record<string, any>) => boolean)
  /** 是否隐藏，支持布尔值或根据表单数据动态计算的函数 */
  hidden?: boolean | ((formData: Record<string, any>) => boolean)
  readonly?: boolean
  maxlength?: number
  showWordLimit?: boolean
  inputType?: string
  rows?: number
  min?: number
  max?: number
  step?: number
  precision?: number
  options?: Array<{ value: any; label: string; children?: any[] }>
  multiple?: boolean
  clearable?: boolean
  activeValue?: any
  inactiveValue?: any
  span?: number
  slotName?: string
  nodeKey?: string
}
