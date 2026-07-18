// views/inventory/material/config.ts
import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'
import { MaterialTypeEnum, MaterialStatusEnum } from '@/enums/inventory/MaterialEnum'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'materialCode',
    label: '物料编码',
    type: 'input',
  },
  {
    prop: 'materialName',
    label: '物料名称',
    type: 'input',
  },
  {
    prop: 'specification',
    label: '规格型号',
    type: 'input',
  },
  {
    prop: 'materialType',
    label: '物料类型',
    type: 'select',
    options: MaterialTypeEnum.items,
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: MaterialStatusEnum.items,
  },
]

export const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增物料',
    type: 'primary',
    icon: 'Plus',
    // permission: 'inventory:material:add',
  },
  {
    key: 'import',
    label: '导入',
    type: 'success',
    icon: 'Upload',
    // permission: 'inventory:material:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    // permission: 'inventory:material:export',
  },
]
// 表格列配置
export const tableOptions: TableOptions[] = [
  {
    label: '物料编码',
    prop: 'materialCode',
    width: 150,
  },
  {
    label: '物料名称',
    prop: 'materialName',
    width: 200,
  },
  {
    label: '物料类型',
    prop: 'materialType',
    width: 100,
    align: 'center',
    slot: 'materialType',
  },
  {
    label: '规格型号',
    prop: 'specification',
    width: 150,
  },
  {
    label: '单位',
    prop: 'unit',
    width: 80,
    align: 'center',
  },
  {
    label: '当前库存',
    prop: 'currentStock',
    width: 120,
    align: 'right',
    slot: 'currentStock',
  },
  {
    label: '安全库存',
    prop: 'safeStock',
    width: 100,
    align: 'right',
  },
  {
    label: '标准单价',
    prop: 'standardPrice',
    width: 120,
    align: 'right',
    slot: 'standardPrice',
  },
  {
    label: '状态',
    prop: 'status',
    width: 80,
    align: 'center',
    slot: 'status',
  },
]
