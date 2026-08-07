// views/system/menu/config.ts
import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'menuName',
    label: '菜单名称',
    type: 'input',
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { value: '0', label: '正常' },
      { value: '1', label: '禁用' },
    ],
  },
  {
    prop: 'perms',
    label: '权限标识',
    type: 'input',
  },
]
// 工具栏配置
export const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增',
    type: 'primary',
    icon: 'Plus',
    permission: 'system:menu:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:menu:export',
  },
]
// 表格列配置
export const tableOptions: TableOptions[] = [
  {
    label: '菜单名称',
    prop: 'menuName',
    minWidth: 200,
    align: 'left',
    slot: 'menuName',
  },
  {
    label: '图标',
    prop: 'icon',
    width: 100,
    align: 'center',
    slot: 'icon',
  },
  {
    label: '排序',
    prop: 'orderNum',
    width: 80,
    align: 'center',
  },
  {
    label: '权限标识',
    prop: 'perms',
    width: 200,
    align: 'center',
    slot: 'perms',
  },
  {
    label: '组件路径',
    prop: 'component',
    width: 200,
    align: 'center',
    slot: 'component',
  },
  {
    label: '状态',
    prop: 'status',
    width: 100,
    slot: 'status',
    align: 'center',
  },
  {
    label: '更新时间',
    prop: 'updateTime',
    width: 180,
    slot: 'updateTime',
    align: 'center',
  },
]

// 定义菜单字典项接口
interface MenuDictItem {
  label: string
  icon: string
  text: string
}

// 使用 Record 类型明确索引签名
export const menuDict: Record<string, MenuDictItem> = {
  user: { label: '用户管理', icon: 'User', text: '用户' },
  role: { label: '角色管理', icon: 'UserFilled', text: '角色' },
  dept: { label: '部门管理', icon: 'OfficeBuilding', text: '部门' },
  post: { label: '岗位管理', icon: 'Position', text: '岗位' },
  menu: { label: '菜单管理', icon: 'Menu', text: '菜单' },
  product: { label: '产品管理', icon: 'Goods', text: '产品' },
  order: { label: '订单管理', icon: 'ShoppingCart', text: '订单' },
  bom: { label: 'BOM管理', icon: 'Document', text: 'BOM' },
  route: { label: '工艺管理', icon: 'Guide', text: '工艺' },
  instance: { label: '实例管理', icon: 'DataAnalysis', text: '实例' },
  drawing: { label: '图纸管理', icon: 'Picture', text: '图纸' },
  customer: { label: '客户管理', icon: 'User', text: '客户' },
  quotation: { label: '报价管理', icon: 'PriceTag', text: '报价' },
  tracking: { label: '跟踪管理', icon: 'Location', text: '跟踪' },
  material: { label: '物料管理', icon: 'Box', text: '物料' },
  warehouse: { label: '仓库管理', icon: 'Warehouse', text: '仓库' },
  stock: { label: '库存管理', icon: 'Stock', text: '库存' },
  alert: { label: '预警管理', icon: 'Bell', text: '预警' },
  inbound: { label: '入库管理', icon: 'Import', text: '入库' },
  outbound: { label: '出库管理', icon: 'Export', text: '出库' },
  stocktake: { label: '盘点管理', icon: 'Document', text: '盘点' },
  transfer: { label: '调拨管理', icon: 'Switch', text: '调拨' },
  supplier: { label: '供应商管理', icon: 'User', text: '供应商' },
  receipt: { label: '收货管理', icon: 'Document', text: '收货' },
  payment: { label: '付款管理', icon: 'Money', text: '付款' },
  document: { label: '单据管理', icon: 'Document', text: '单据' },
  dashboard: { label: '仪表盘', icon: 'DataBoard', text: '仪表盘' },
  routing: { label: '工序管理', icon: 'Guide', text: '工序' },
  execution: { label: '生产执行管理', icon: 'Guide', text: '生产执行' },
  equipment: { label: '设备管理', icon: 'Guide', text: '设备' },
  quality: { label: '质量管理', icon: 'Guide', text: '质量' },
  operation: { label: '生产操作管理', icon: 'Guide', text: '生产操作' },
  production: { label: '生产管理', icon: 'Guide', text: '生产' },
  inquiry: { label: '询价管理', icon: 'Guide', text: '询价' },
  inventory: { label: '库存管理', icon: 'Guide', text: '库存' },
  purchase: { label: '采购管理', icon: 'Guide', text: '采购' },
  sales: { label: '销售管理', icon: 'Guide', text: '销售' },
}
