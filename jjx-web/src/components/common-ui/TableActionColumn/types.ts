import type { Component } from 'vue'

export type TableActionButtonType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
export type TableActionDisplay = 'text' | 'icon' | 'icon-text'

export interface TableActionContext<Row> {
  row: Row
  index: number
}

export type TableActionValue<Row, Value> = Value | ((context: TableActionContext<Row>) => Value)

export interface TableAction<Row = Record<string, unknown>> {
  key: string
  label: TableActionValue<Row, string>
  display?: TableActionDisplay
  type?: TableActionValue<Row, TableActionButtonType>
  icon?: TableActionValue<Row, Component>
  permission?: string | string[]
  role?: string | string[]
  visible?: TableActionValue<Row, boolean>
  disabled?: TableActionValue<Row, boolean>
  disabledReason?: TableActionValue<Row, string>
  loading?: TableActionValue<Row, boolean>
  tooltip?: TableActionValue<Row, string>
  confirm?: TableActionValue<Row, string>
  order?: number
}
