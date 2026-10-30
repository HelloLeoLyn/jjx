// views/inventory/material/config.ts
import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'
import { MaterialRootTypeEnum, MaterialStatusEnum } from '@/enums/inventory/MaterialEnum'
import { MaterialClassStatusEnum } from '@/enums/inventory/MaterialClassEnum'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  // 统一模糊搜索（dev-20261024-024）：名称/编码/型号/别名/规格/分类，空格分词 AND
  {
    prop: 'keyword',
    label: '搜索词',
    type: 'input',
    width: 340,
  },
  {
    prop: 'rootType',
    label: '物料类型',
    type: 'select',
    advanced: true,
    options: MaterialRootTypeEnum.items,
  },
  {
    prop: 'materialMeta',
    label: '物料材质',
    type: 'select',
    advanced: true,
    options: [],
  },
  {
    prop: 'classStatus',
    label: '归类状态',
    type: 'select',
    options: MaterialClassStatusEnum.items.map((item) => ({ value: item.value, label: item.label })),
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
    advanced: true,
    options: [],
  },
  {
    prop: 'color',
    label: '颜色',
    type: 'select',
    advanced: true,
    options: [],
  },
  {
    prop: 'brand',
    label: '品牌',
    type: 'select',
    advanced: true,
    options: [],
  },
  {
    prop: 'isCustomerSupplied',
    label: '客供专用',
    type: 'select',
    advanced: true,
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'isSample',
    label: '样品专用',
    type: 'select',
    advanced: true,
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'batchControl',
    label: '批次控制',
    type: 'select',
    advanced: true,
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'lowStock',
    label: '低库存',
    type: 'select',
    advanced: true,
    // 低库存 = 安全库存>0 且现有库存小于安全库存（dev-20261030-018）
    options: [
      { value: true, label: '是' },
      { value: false, label: '否' },
    ],
  },
  {
    prop: 'createTimeRange',
    label: '创建时间',
    type: 'daterange',
    advanced: true,
  },

  {
    prop: 'tagIds',
    label: '物料标签',
    type: 'custom',
    block: true,
    advanced: true,
  },
  {
    prop: 'supplierId',
    label: '供应商',
    type: 'custom',
    advanced: true,
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
    key: 'classify-rules',
    label: '归类规则',
    type: 'info',
    icon: 'SetUp',
    permission: 'inventory:material:edit',
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
