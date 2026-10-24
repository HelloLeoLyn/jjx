// views/inventory/material/config.ts
import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'
import { MaterialRootTypeEnum, MaterialStatusEnum } from '@/enums/inventory/MaterialEnum'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  // {
  //   prop: 'materialCode',
  //   label: '物料编码',
  //   type: 'input',
  // },
  {
    prop: 'materialName',
    label: '物料名称',
    type: 'input',
  },
  {
    prop: 'rootType',
    label: '物料类型',
    type: 'select',
    options: MaterialRootTypeEnum.items,
  },
  {
    prop: 'materialMeta',
    label: '物料材质',
    type: 'select',
    options: [],
  },
  {
    prop: 'categoryId',
    label: '品类',
    type: 'select',
    // 选项由新模型分类接口注入，只提供末级品类。
    options: [],
  },
  {
    prop: 'materialForm',
    label: '形态',
    type: 'select',
    options: [],
  },
  {
    prop: 'model',
    label: '型号',
    type: 'input',
  },
  {
    prop: 'color',
    label: '颜色',
    type: 'select',
    options: [],
  },
  {
    prop: 'brand',
    label: '品牌',
    type: 'select',
    options: [],
  },
  {
    prop: 'isCustomerSupplied',
    label: '客供专用',
    type: 'select',
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'isSample',
    label: '样品专用',
    type: 'select',
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'specification',
    label: '规格型号',
    type: 'input',
  },

  {
    prop: 'masterDataOnly',
    label: '档案范围',
    type: 'select',
    options: [
      { value: true, label: '新模型物料' },
      { value: false, label: '待归类物料' },
    ],
  },
  {
    prop: 'tagIds',
    label: '物料标签',
    type: 'custom',
    block: true,
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
    permission: 'inventory:material:add',
  },
  {
    key: 'master-settings',
    label: '分类与模板',
    type: 'info',
    icon: 'Setting',
    permission: 'inventory:category:edit',
  },
  {
    key: 'import',
    label: '导入',
    type: 'success',
    icon: 'Upload',
    permission: 'inventory:material:add',
  },
  {
    key: 'export',
    label: '导出新模型',
    type: 'warning',
    icon: 'Download',
    // permission: 'inventory:material:export', // 后端无导出接口/权限点，暂不启用
  },
  {
    key: 'migration-export',
    label: '导出待归类',
    type: 'info',
    icon: 'Download',
    permission: 'inventory:material:view',
  },
  {
    key: 'print-label',
    label: '打印标签',
    type: 'info',
    icon: 'Printer',
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
    label: '品类',
    prop: 'categoryName',
    width: 140,
  },
  {
    label: '材料',
    prop: 'materialGrade',
    width: 110,
  },
  {
    label: '型号',
    prop: 'model',
    width: 120,
  },
  {
    label: '物料类型',
    prop: 'categoryRootName',
    width: 100,
    align: 'center',
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
