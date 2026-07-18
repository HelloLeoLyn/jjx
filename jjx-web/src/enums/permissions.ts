/**
 * 权限标识枚举
 * 统一管理所有权限标识，避免硬编码字符串
 *
 * 命名规范：
 * - 枚举名：模块_功能_操作（全大写，下划线分隔）
 * - 枚举值：模块:功能:操作（全小写，冒号分隔）
 *
 * 标准CRUD操作：
 * - view: 查看（包含列表、详情、查询）
 * - add: 新增
 * - edit: 编辑
 * - delete: 删除
 * - export: 导出（可选）
 * - import: 导入（可选）
 *
 * 特殊状态操作使用动词描述，如 product:status:release
 */
export const Permissions = {
  // ==================== 系统管理 ====================
  /** 用户管理 - 查看 */
  SYSTEM_USER_VIEW: 'system:user:view',
  /** 用户管理 - 新增 */
  SYSTEM_USER_ADD: 'system:user:add',
  /** 用户管理 - 编辑 */
  SYSTEM_USER_EDIT: 'system:user:edit',
  /** 用户管理 - 删除 */
  SYSTEM_USER_DELETE: 'system:user:delete',
  /** 用户管理 - 导出 */
  SYSTEM_USER_EXPORT: 'system:user:export',
  /** 用户管理 - 重置密码 */
  SYSTEM_USER_RESET_PWD: 'system:user:resetPwd',

  /** 角色管理 - 查看 */
  SYSTEM_ROLE_VIEW: 'system:role:view',
  /** 角色管理 - 新增 */
  SYSTEM_ROLE_ADD: 'system:role:add',
  /** 角色管理 - 编辑 */
  SYSTEM_ROLE_EDIT: 'system:role:edit',
  /** 角色管理 - 删除 */
  SYSTEM_ROLE_DELETE: 'system:role:delete',

  /** 菜单管理 - 查看 */
  SYSTEM_MENU_VIEW: 'system:menu:view',
  /** 菜单管理 - 新增 */
  SYSTEM_MENU_ADD: 'system:menu:add',
  /** 菜单管理 - 编辑 */
  SYSTEM_MENU_EDIT: 'system:menu:edit',
  /** 菜单管理 - 删除 */
  SYSTEM_MENU_DELETE: 'system:menu:delete',

  /** 部门管理 - 查看 */
  SYSTEM_DEPT_VIEW: 'system:dept:view',
  /** 部门管理 - 新增 */
  SYSTEM_DEPT_ADD: 'system:dept:add',
  /** 部门管理 - 编辑 */
  SYSTEM_DEPT_EDIT: 'system:dept:edit',
  /** 部门管理 - 删除 */
  SYSTEM_DEPT_DELETE: 'system:dept:delete',

  /** 字典管理 - 查看 */
  SYSTEM_DICT_VIEW: 'system:dict:view',
  /** 字典管理 - 新增 */
  SYSTEM_DICT_ADD: 'system:dict:add',
  /** 字典管理 - 编辑 */
  SYSTEM_DICT_EDIT: 'system:dict:edit',
  /** 字典管理 - 删除 */
  SYSTEM_DICT_DELETE: 'system:dict:delete',

  // ==================== 库存管理 ====================
  /** 物料管理 - 查看 */
  INVENTORY_MATERIAL_VIEW: 'inventory:material:view',
  /** 物料管理 - 新增 */
  INVENTORY_MATERIAL_ADD: 'inventory:material:add',
  /** 物料管理 - 编辑 */
  INVENTORY_MATERIAL_EDIT: 'inventory:material:edit',
  /** 物料管理 - 删除 */
  INVENTORY_MATERIAL_DELETE: 'inventory:material:delete',
  /** 物料管理 - 导出 */
  INVENTORY_MATERIAL_EXPORT: 'inventory:material:export',
  /** 物料管理 - 导入 */
  INVENTORY_MATERIAL_IMPORT: 'inventory:material:import',

  /** 物料分类 - 查看 */
  INVENTORY_CATEGORY_VIEW: 'inventory:category:view',
  /** 物料分类 - 新增 */
  INVENTORY_CATEGORY_ADD: 'inventory:category:add',
  /** 物料分类 - 编辑 */
  INVENTORY_CATEGORY_EDIT: 'inventory:category:edit',
  /** 物料分类 - 删除 */
  INVENTORY_CATEGORY_DELETE: 'inventory:category:delete',

  /** 仓库管理 - 查看 */
  INVENTORY_WAREHOUSE_VIEW: 'inventory:warehouse:view',
  /** 仓库管理 - 新增 */
  INVENTORY_WAREHOUSE_ADD: 'inventory:warehouse:add',
  /** 仓库管理 - 编辑 */
  INVENTORY_WAREHOUSE_EDIT: 'inventory:warehouse:edit',
  /** 仓库管理 - 删除 */
  INVENTORY_WAREHOUSE_DELETE: 'inventory:warehouse:delete',
  /** 仓库管理 - 导出 */
  INVENTORY_WAREHOUSE_EXPORT: 'inventory:warehouse:export',

  /** 库位管理 - 查看 */
  INVENTORY_STORAGE_LOCATION_VIEW: 'inventory:storage-location:view',
  /** 库位管理 - 新增 */
  INVENTORY_STORAGE_LOCATION_ADD: 'inventory:storage-location:add',
  /** 库位管理 - 编辑 */
  INVENTORY_STORAGE_LOCATION_EDIT: 'inventory:storage-location:edit',
  /** 库位管理 - 删除 */
  INVENTORY_STORAGE_LOCATION_DELETE: 'inventory:storage-location:delete',
  /** 库位管理 - 导出 */
  INVENTORY_STORAGE_LOCATION_EXPORT: 'inventory:storage-location:export',

  /** 入库管理 - 查看 */
  INVENTORY_INBOUND_VIEW: 'inventory:inbound:view',
  /** 入库管理 - 新增 */
  INVENTORY_INBOUND_ADD: 'inventory:inbound:add',
  /** 入库管理 - 编辑 */
  INVENTORY_INBOUND_EDIT: 'inventory:inbound:edit',
  /** 入库管理 - 删除 */
  INVENTORY_INBOUND_DELETE: 'inventory:inbound:delete',
  /** 入库管理 - 导出 */
  INVENTORY_INBOUND_EXPORT: 'inventory:inbound:export',

  /** 出库管理 - 查看 */
  INVENTORY_OUTBOUND_VIEW: 'inventory:outbound:view',
  /** 出库管理 - 新增 */
  INVENTORY_OUTBOUND_ADD: 'inventory:outbound:add',
  /** 出库管理 - 编辑 */
  INVENTORY_OUTBOUND_EDIT: 'inventory:outbound:edit',
  /** 出库管理 - 删除 */
  INVENTORY_OUTBOUND_DELETE: 'inventory:outbound:delete',
  /** 出库管理 - 导出 */
  INVENTORY_OUTBOUND_EXPORT: 'inventory:outbound:export',

  /** 库存管理 - 查看 */
  INVENTORY_STOCK_VIEW: 'inventory:stock:view',
  /** 库存管理 - 导出 */
  INVENTORY_STOCK_EXPORT: 'inventory:stock:export',

  /** 库存项管理 - 查看 */
  INVENTORY_STOCK_ITEM_VIEW: 'inventory:stock-item:view',
  /** 库存项管理 - 导出 */
  INVENTORY_STOCK_ITEM_EXPORT: 'inventory:stock-item:export',

  /** 库存预警 - 查看 */
  INVENTORY_ALERT_VIEW: 'inventory:alert:view',
  /** 库存预警 - 编辑 */
  INVENTORY_ALERT_EDIT: 'inventory:alert:edit',
  /** 库存预警 - 导出 */
  INVENTORY_ALERT_EXPORT: 'inventory:alert:export',

  /** 库存盘点 - 查看 */
  INVENTORY_STOCKTAKE_VIEW: 'inventory:stocktake:view',
  /** 库存盘点 - 新增 */
  INVENTORY_STOCKTAKE_ADD: 'inventory:stocktake:add',
  /** 库存盘点 - 编辑 */
  INVENTORY_STOCKTAKE_EDIT: 'inventory:stocktake:edit',
  /** 库存盘点 - 删除 */
  INVENTORY_STOCKTAKE_DELETE: 'inventory:stocktake:delete',
  /** 库存盘点 - 导出 */
  INVENTORY_STOCKTAKE_EXPORT: 'inventory:stocktake:export',

  /** 库存调拨 - 查看 */
  INVENTORY_TRANSFER_VIEW: 'inventory:transfer:view',
  /** 库存调拨 - 新增 */
  INVENTORY_TRANSFER_ADD: 'inventory:transfer:add',
  /** 库存调拨 - 编辑 */
  INVENTORY_TRANSFER_EDIT: 'inventory:transfer:edit',
  /** 库存调拨 - 删除 */
  INVENTORY_TRANSFER_DELETE: 'inventory:transfer:delete',
  /** 库存调拨 - 导出 */
  INVENTORY_TRANSFER_EXPORT: 'inventory:transfer:export',

  /** 库存交易 - 查看 */
  INVENTORY_TRANSACTION_VIEW: 'inventory:transaction:view',
  /** 库存交易 - 导出 */
  INVENTORY_TRANSACTION_EXPORT: 'inventory:transaction:export',

  /** 库存报表 - 查看 */
  INVENTORY_REPORT_VIEW: 'inventory:report:view',
  /** 库存报表 - 导出 */
  INVENTORY_REPORT_EXPORT: 'inventory:report:export',

  // ==================== 销售管理 ====================
  /** 销售订单 - 查看 */
  SALES_ORDER_VIEW: 'sales:order:view',
  /** 销售订单 - 新增 */
  SALES_ORDER_ADD: 'sales:order:add',
  /** 销售订单 - 编辑 */
  SALES_ORDER_EDIT: 'sales:order:edit',
  /** 销售订单 - 删除 */
  SALES_ORDER_DELETE: 'sales:order:delete',
  /** 销售订单 - 导出 */
  SALES_ORDER_EXPORT: 'sales:order:export',
  /** 销售订单 - 审核 */
  SALES_ORDER_REVIEW: 'sales:order:review',
  /** 销售订单 - 状态变更 */
  SALES_ORDER_STATUS: 'sales:order:status',

  /** 客户管理 - 查看 */
  SALES_CUSTOMER_VIEW: 'sales:customer:view',
  /** 客户管理 - 新增 */
  SALES_CUSTOMER_ADD: 'sales:customer:add',
  /** 客户管理 - 编辑 */
  SALES_CUSTOMER_EDIT: 'sales:customer:edit',
  /** 客户管理 - 删除 */
  SALES_CUSTOMER_DELETE: 'sales:customer:delete',
  /** 客户管理 - 导出 */
  SALES_CUSTOMER_EXPORT: 'sales:customer:export',
  /** 客户管理 - 导入 */
  SALES_CUSTOMER_IMPORT: 'sales:customer:import',

  /** 报价管理 - 查看 */
  SALES_QUOTATION_VIEW: 'sales:quotation:view',
  /** 报价管理 - 新增 */
  SALES_QUOTATION_ADD: 'sales:quotation:add',
  /** 报价管理 - 编辑 */
  SALES_QUOTATION_EDIT: 'sales:quotation:edit',
  /** 报价管理 - 删除 */
  SALES_QUOTATION_DELETE: 'sales:quotation:delete',
  /** 报价管理 - 导出 */
  SALES_QUOTATION_EXPORT: 'sales:quotation:export',

  /** 操作日志 - 查看 */
  SALES_LOG_VIEW: 'sales:log:view',
  /** 操作日志 - 删除 */
  SALES_LOG_DELETE: 'sales:log:delete',
  /** 操作日志 - 导出 */
  SALES_LOG_EXPORT: 'sales:log:export',

  // ==================== 产品管理 ====================
  /** 产品管理 - 发布 */
  PRODUCT_STATUS_RELEASE: 'product:status:release',
  /** 产品管理 - 提交审核 */
  PRODUCT_STATUS_SUBMIT: 'product:status:submit',
  /** 产品管理 - 审核通过 */
  PRODUCT_STATUS_APPROVE: 'product:status:approve',
  /** 产品管理 - 驳回 */
  PRODUCT_STATUS_REJECT: 'product:status:reject',
  /** 产品管理 - 停用 */
  PRODUCT_STATUS_OBSOLETE: 'product:status:obsolete',

  /** 产品管理 - 查看 */
  PRODUCT_VIEW: 'product:view',
  /** 产品管理 - 新增 */
  PRODUCT_ADD: 'product:add',
  /** 产品管理 - 编辑 */
  PRODUCT_EDIT: 'product:edit',
  /** 产品管理 - 删除 */
  PRODUCT_DELETE: 'product:delete',
  /** 产品管理 - 导出 */
  PRODUCT_EXPORT: 'product:export',
  /** 产品管理 - 导入 */
  PRODUCT_IMPORT: 'product:import',

  /** BOM管理 - 查看 */
  PRODUCT_BOM_VIEW: 'product:bom:view',
  /** BOM管理 - 新增 */
  PRODUCT_BOM_ADD: 'product:bom:add',
  /** BOM管理 - 编辑 */
  PRODUCT_BOM_EDIT: 'product:bom:edit',
  /** BOM管理 - 删除 */
  PRODUCT_BOM_DELETE: 'product:bom:delete',
  /** BOM管理 - 审批 */
  PRODUCT_BOM_APPROVE: 'product:bom:approve',
  /** BOM管理 - 驳回 */
  PRODUCT_BOM_REJECT: 'product:bom:reject',
  /** BOM管理 - 导出 */
  PRODUCT_BOM_EXPORT: 'product:bom:export',

  // ==================== 采购管理 ====================
  /** 采购订单 - 查看 */
  PURCHASE_ORDER_VIEW: 'purchase:order:view',
  /** 采购订单 - 新增 */
  PURCHASE_ORDER_ADD: 'purchase:order:add',
  /** 采购订单 - 编辑 */
  PURCHASE_ORDER_EDIT: 'purchase:order:edit',
  /** 采购订单 - 删除 */
  PURCHASE_ORDER_DELETE: 'purchase:order:delete',
  /** 采购订单 - 导出 */
  PURCHASE_ORDER_EXPORT: 'purchase:order:export',
  /** 采购订单 - 审核 */
  PURCHASE_ORDER_APPROVE: 'purchase:order:approve',

  /** 供应商管理 - 查看 */
  PURCHASE_SUPPLIER_VIEW: 'purchase:supplier:view',
  /** 供应商管理 - 新增 */
  PURCHASE_SUPPLIER_ADD: 'purchase:supplier:add',
  /** 供应商管理 - 编辑 */
  PURCHASE_SUPPLIER_EDIT: 'purchase:supplier:edit',
  /** 供应商管理 - 删除 */
  PURCHASE_SUPPLIER_DELETE: 'purchase:supplier:delete',
  /** 供应商管理 - 导出 */
  PURCHASE_SUPPLIER_EXPORT: 'purchase:supplier:export',
  /** 供应商管理 - 导入 */
  PURCHASE_SUPPLIER_IMPORT: 'purchase:supplier:import',

  /** 采购收货 - 查看 */
  PURCHASE_RECEIPT_VIEW: 'purchase:receipt:view',
  /** 采购收货 - 新增 */
  PURCHASE_RECEIPT_ADD: 'purchase:receipt:add',
  /** 采购收货 - 编辑 */
  PURCHASE_RECEIPT_EDIT: 'purchase:receipt:edit',
  /** 采购收货 - 删除 */
  PURCHASE_RECEIPT_DELETE: 'purchase:receipt:delete',
  /** 采购收货 - 导出 */
  PURCHASE_RECEIPT_EXPORT: 'purchase:receipt:export',
  /** 采购收货 - 导入 */
  PURCHASE_RECEIPT_IMPORT: 'purchase:receipt:import',

  /** 采购发票 - 查看 */
  PURCHASE_INVOICE_VIEW: 'purchase:invoice:view',
  /** 采购发票 - 新增 */
  PURCHASE_INVOICE_ADD: 'purchase:invoice:add',
  /** 采购发票 - 编辑 */
  PURCHASE_INVOICE_EDIT: 'purchase:invoice:edit',
  /** 采购发票 - 删除 */
  PURCHASE_INVOICE_DELETE: 'purchase:invoice:delete',
  /** 采购发票 - 导出 */
  PURCHASE_INVOICE_EXPORT: 'purchase:invoice:export',
  /** 采购发票 - 导入 */
  PURCHASE_INVOICE_IMPORT: 'purchase:invoice:import',

  /** 采购付款 - 查看 */
  PURCHASE_PAYMENT_VIEW: 'purchase:payment:view',
  /** 采购付款 - 新增 */
  PURCHASE_PAYMENT_ADD: 'purchase:payment:add',
  /** 采购付款 - 编辑 */
  PURCHASE_PAYMENT_EDIT: 'purchase:payment:edit',
  /** 采购付款 - 删除 */
  PURCHASE_PAYMENT_DELETE: 'purchase:payment:delete',
  /** 采购付款 - 导出 */
  PURCHASE_PAYMENT_EXPORT: 'purchase:payment:export',
  /** 采购付款 - 导入 */
  PURCHASE_PAYMENT_IMPORT: 'purchase:payment:import',
  /** 采购付款 - 审批 */
  PURCHASE_PAYMENT_APPROVE: 'purchase:payment:approve',

  // ==================== 生产管理 ====================
  /** 生产工单 - 查看 */
  PRODUCTION_ORDER_VIEW: 'production:order:view',
  /** 生产工单 - 新增 */
  PRODUCTION_ORDER_ADD: 'production:order:add',
  /** 生产工单 - 编辑 */
  PRODUCTION_ORDER_EDIT: 'production:order:edit',
  /** 生产工单 - 删除 */
  PRODUCTION_ORDER_DELETE: 'production:order:delete',
  /** 生产工单 - 导出 */
  PRODUCTION_ORDER_EXPORT: 'production:order:export',
  /** 生产工单 - 审核 */
  PRODUCTION_ORDER_APPROVE: 'production:order:approve',

  /** 工序执行 - 查看 */
  PRODUCTION_OPERATION_EXECUTION_VIEW: 'production:operation-execution:view',
  /** 工序执行 - 新增 */
  PRODUCTION_OPERATION_EXECUTION_ADD: 'production:operation-execution:add',
  /** 工序执行 - 编辑 */
  PRODUCTION_OPERATION_EXECUTION_EDIT: 'production:operation-execution:edit',
  /** 工序执行 - 删除 */
  PRODUCTION_OPERATION_EXECUTION_DELETE: 'production:operation-execution:delete',
  /** 工序执行 - 导出 */
  PRODUCTION_OPERATION_EXECUTION_EXPORT: 'production:operation-execution:export',
  /** 工序执行 - 导入 */
  PRODUCTION_OPERATION_EXECUTION_IMPORT: 'production:operation-execution:import',

  /** 工序记录 - 查看 */
  PRODUCTION_OPERATION_RECORD_VIEW: 'production:operation-record:view',
  /** 工序记录 - 新增 */
  PRODUCTION_OPERATION_RECORD_ADD: 'production:operation-record:add',
  /** 工序记录 - 编辑 */
  PRODUCTION_OPERATION_RECORD_EDIT: 'production:operation-record:edit',
  /** 工序记录 - 删除 */
  PRODUCTION_OPERATION_RECORD_DELETE: 'production:operation-record:delete',
  /** 工序记录 - 导出 */
  PRODUCTION_OPERATION_RECORD_EXPORT: 'production:operation-record:export',
  /** 工序记录 - 导入 */
  PRODUCTION_OPERATION_RECORD_IMPORT: 'production:operation-record:import',
} as const

export type PermissionValue = (typeof Permissions)[keyof typeof Permissions]
