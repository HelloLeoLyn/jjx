# 全站 @Log 摸底原始清单（阶段2 铺码用，机械抽取）
共 316 处 / 45 文件（2026-09-04 抽取）

| # | 文件 | 行 | 方法 | Operation摘要 | module | businessType |
|---|----|----|----|----|----|----|
| 1 | com/jjx/biz/controller/BizRequirementController.java | 130 | add | 新增需求单 | 业务需求管理 | INSERT |
| 2 | com/jjx/biz/controller/BizRequirementController.java | 138 | edit | 修改需求单 | 业务需求管理 | UPDATE |
| 3 | com/jjx/biz/controller/BizRequirementController.java | 147 | remove | 删除需求单 | 业务需求管理 | DELETE |
| 4 | com/jjx/biz/controller/BizRequirementController.java | 156 | submit | 提交评审 | 业务需求管理 | UPDATE |
| 5 | com/jjx/biz/controller/BizRequirementController.java | 165 | approval | 四部门会签（同意/不同意+意见） | 业务需求管理 | UPDATE |
| 6 | com/jjx/biz/controller/BizRequirementController.java | 183 | upgrade | 变更升版（关联产品→复制 BOM/工艺路线新版本） | 业务需求管理 | UPDATE |
| 7 | com/jjx/biz/controller/BizRequirementController.java | 192 | execute | 开始执行（审核通过后） | 业务需求管理 | UPDATE |
| 8 | com/jjx/biz/controller/BizRequirementController.java | 201 | close | 关闭（登记执行结果） | 业务需求管理 | UPDATE |
| 9 | com/jjx/engineering/controller/BomController.java | 142 | add |  | 产品BOM管理 | INSERT |
| 10 | com/jjx/engineering/controller/BomController.java | 155 | edit |  | 产品BOM管理 | UPDATE |
| 11 | com/jjx/engineering/controller/BomController.java | 167 | remove |  | 产品BOM管理 | DELETE |
| 12 | com/jjx/engineering/controller/BomController.java | 179 | submit |  | 产品BOM管理 | UPDATE |
| 13 | com/jjx/engineering/controller/BomController.java | 190 | approve |  | 产品BOM管理 | APPROVE |
| 14 | com/jjx/engineering/controller/BomController.java | 201 | reject |  | 产品BOM管理 | APPROVE |
| 15 | com/jjx/engineering/controller/BomController.java | 212 | setDefault |  | 产品BOM管理 | UPDATE |
| 16 | com/jjx/engineering/controller/EngineeringController.java | 38 | save | 新增工程记录 | 工程管理 | INSERT |
| 17 | com/jjx/engineering/controller/EngineeringController.java | 47 | update | 更新工程记录 | 工程管理 | UPDATE |
| 18 | com/jjx/engineering/controller/EngineeringController.java | 56 | delete | 删除工程记录 | 工程管理 | DELETE |
| 19 | com/jjx/inventory/controller/InventoryAlertController.java | 39 | executeAlertCheck | 执行预警检查 | 库存预警 | UPDATE |
| 20 | com/jjx/inventory/controller/InventoryAlertController.java | 48 | checkSafeStockAlert | 检查安全库存预警 | 库存预警 | UPDATE |
| 21 | com/jjx/inventory/controller/InventoryAlertController.java | 57 | checkMaxStockAlert | 检查最高库存预警 | 库存预警 | UPDATE |
| 22 | com/jjx/inventory/controller/InventoryAlertController.java | 66 | checkExpiryAlert | 检查保质期预警 | 库存预警 | UPDATE |
| 23 | com/jjx/inventory/controller/InventoryAlertController.java | 75 | checkObsoleteAlert | 检查呆滞料预警 | 库存预警 | UPDATE |
| 24 | com/jjx/inventory/controller/InventoryAlertController.java | 84 | checkOrderShortage | 订单齐套检查（按BOM算料缺料预警，返回缺料明细含在途/实际缺口） | 库存预警 | UPDATE |
| 25 | com/jjx/inventory/controller/InventoryAlertController.java | 92 | checkGlobalShortage | 全局汇总缺料检查（082：在途订单BOM汇总→物料缺口预警，手动触发） | 库存预警 | UPDATE |
| 26 | com/jjx/inventory/controller/InventoryAlertController.java | 107 | markRead | 标记预警已读 | 库存预警 | UPDATE |
| 27 | com/jjx/inventory/controller/InventoryAlertController.java | 115 | batchMarkRead | 批量标记已读 | 库存预警 | UPDATE |
| 28 | com/jjx/inventory/controller/InventoryAlertController.java | 123 | processAlert | 处理预警 | 库存预警 | UPDATE |
| 29 | com/jjx/inventory/controller/InventoryAlertController.java | 133 | batchProcessAlert | 批量处理预警（采购计划确认后回写，关联采购订单号） | 库存预警 | UPDATE |
| 30 | com/jjx/inventory/controller/InventoryInboundController.java | 46 | create | 创建入库单 | 入库管理 | INSERT |
| 31 | com/jjx/inventory/controller/InventoryInboundController.java | 54 | confirm | 确认入库 | 入库管理 | UPDATE |
| 32 | com/jjx/inventory/controller/InventoryInboundController.java | 64 | cancel | 取消入库单 | 入库管理 | UPDATE |
| 33 | com/jjx/inventory/controller/InventoryInboundController.java | 73 | submitApprove | 提交审批 | 入库管理 | UPDATE |
| 34 | com/jjx/inventory/controller/InventoryInboundController.java | 81 | approve | 审批通过 | 入库管理 | APPROVE |
| 35 | com/jjx/inventory/controller/InventoryInboundController.java | 92 | reject | 审批驳回 | 入库管理 | APPROVE |
| 36 | com/jjx/inventory/controller/InventoryInboundController.java | 103 | createFromPurchase | 从采购订单创建入库单 | 入库管理 | INSERT |
| 37 | com/jjx/inventory/controller/InventoryInboundController.java | 111 | createFromProduction | 从生产工单创建入库单 | 入库管理 | INSERT |
| 38 | com/jjx/inventory/controller/InventoryInboundController.java | 142 | updateStatus | 更新入库单状态 | 入库管理 | UPDATE |
| 39 | com/jjx/inventory/controller/InventoryInboundController.java | 151 | exportPdf | 导出入库单PDF（单张表单） | 入库管理 | EXPORT |
| 40 | com/jjx/inventory/controller/InventoryMaterialCategoryController.java | 51 | add | 新增分类 | 物料分类管理 | INSERT |
| 41 | com/jjx/inventory/controller/InventoryMaterialCategoryController.java | 61 | update | 修改分类 | 物料分类管理 | UPDATE |
| 42 | com/jjx/inventory/controller/InventoryMaterialCategoryController.java | 71 | delete | 删除分类 | 物料分类管理 | DELETE |
| 43 | com/jjx/inventory/controller/InventoryMaterialCategoryController.java | 79 | updateStatus | 更新分类状态 | 物料分类管理 | UPDATE |
| 44 | com/jjx/inventory/controller/InventoryMaterialController.java | 89 | add |  | 物料管理 | INSERT |
| 45 | com/jjx/inventory/controller/InventoryMaterialController.java | 110 | update |  | 物料管理 | UPDATE |
| 46 | com/jjx/inventory/controller/InventoryMaterialController.java | 130 | delete |  | 物料管理 | DELETE |
| 47 | com/jjx/inventory/controller/InventoryMaterialController.java | 141 | updateStatus |  | 物料管理 | UPDATE |
| 48 | com/jjx/inventory/controller/InventoryMaterialController.java | 153 | batchUpdateStatus |  | 物料管理 | UPDATE |
| 49 | com/jjx/inventory/controller/InventoryMaterialController.java | 199 | importMaterial |  | 物料管理 | IMPORT |
| 50 | com/jjx/inventory/controller/InventoryOutboundController.java | 46 | create | 创建出库单 | 出库管理 | INSERT |
| 51 | com/jjx/inventory/controller/InventoryOutboundController.java | 54 | confirm | 确认出库 | 出库管理 | UPDATE |
| 52 | com/jjx/inventory/controller/InventoryOutboundController.java | 64 | update | 更新出库单（含明细） | 出库管理 | UPDATE |
| 53 | com/jjx/inventory/controller/InventoryOutboundController.java | 72 | cancel | 取消出库单 | 出库管理 | UPDATE |
| 54 | com/jjx/inventory/controller/InventoryOutboundController.java | 81 | submitApprove | 提交审批 | 出库管理 | UPDATE |
| 55 | com/jjx/inventory/controller/InventoryOutboundController.java | 89 | approve | 审批通过 | 出库管理 | APPROVE |
| 56 | com/jjx/inventory/controller/InventoryOutboundController.java | 100 | reject | 审批驳回 | 出库管理 | APPROVE |
| 57 | com/jjx/inventory/controller/InventoryOutboundController.java | 111 | createFromProduction | 从生产工单创建出库单 | 出库管理 | INSERT |
| 58 | com/jjx/inventory/controller/InventoryOutboundController.java | 127 | createProductionPick | 追加领料（033多次领料：Σ累计领料≤BOM需求量，剩余量校验） | 出库管理 | INSERT |
| 59 | com/jjx/inventory/controller/InventoryOutboundController.java | 143 | createFromSales | 从销售订单创建出库单 | 出库管理 | INSERT |
| 60 | com/jjx/inventory/controller/InventoryOutboundController.java | 174 | updateStatus | 更新出库单状态 | 出库管理 | UPDATE |
| 61 | com/jjx/inventory/controller/InventoryOutboundController.java | 183 | exportPdf | 导出出库单PDF（单张表单） | 出库管理 | EXPORT |
| 62 | com/jjx/inventory/controller/InventoryStockController.java | 123 | batchImport | 批量导入库存 | 库存管理 | IMPORT |
| 63 | com/jjx/inventory/controller/InventoryStocktakeController.java | 46 | create | 创建盘点单 | 盘点管理 | INSERT |
| 64 | com/jjx/inventory/controller/InventoryStocktakeController.java | 54 | startStocktake | 开始盘点 | 盘点管理 | UPDATE |
| 65 | com/jjx/inventory/controller/InventoryStocktakeController.java | 62 | inputStocktakeData | 录入盘点数据 | 盘点管理 | UPDATE |
| 66 | com/jjx/inventory/controller/InventoryStocktakeController.java | 78 | confirmResult | 确认盘点结果 | 盘点管理 | UPDATE |
| 67 | com/jjx/inventory/controller/InventoryStocktakeController.java | 86 | processDiff | 处理盈亏 | 盘点管理 | UPDATE |
| 68 | com/jjx/inventory/controller/InventoryStocktakeController.java | 96 | closeStocktake | 关闭盘点单 | 盘点管理 | UPDATE |
| 69 | com/jjx/inventory/controller/InventoryStocktakeController.java | 104 | submitApprove | 提交审批 | 盘点管理 | UPDATE |
| 70 | com/jjx/inventory/controller/InventoryStocktakeController.java | 112 | approve | 审批通过 | 盘点管理 | APPROVE |
| 71 | com/jjx/inventory/controller/InventoryStocktakeController.java | 137 | updateStatus | 更新盘点单状态 | 盘点管理 | UPDATE |
| 72 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 96 | add |  | 库位管理 | INSERT |
| 73 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 120 | update |  | 库位管理 | UPDATE |
| 74 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 149 | delete |  | 库位管理 | DELETE |
| 75 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 160 | updateStatus |  | 库位管理 | UPDATE |
| 76 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 177 | batchUpdateStatus |  | 库位管理 | UPDATE |
| 77 | com/jjx/inventory/controller/InventoryStorageLocationController.java | 375 | importStorageLocation |  | 库位管理 | IMPORT |
| 78 | com/jjx/inventory/controller/InventoryTransferController.java | 46 | create | 创建调拨单 | 调拨管理 | INSERT |
| 79 | com/jjx/inventory/controller/InventoryTransferController.java | 54 | submitApprove | 提交审批 | 调拨管理 | UPDATE |
| 80 | com/jjx/inventory/controller/InventoryTransferController.java | 62 | approve | 审批通过 | 调拨管理 | APPROVE |
| 81 | com/jjx/inventory/controller/InventoryTransferController.java | 73 | reject | 审批驳回 | 调拨管理 | APPROVE |
| 82 | com/jjx/inventory/controller/InventoryTransferController.java | 84 | confirmOut | 调出确认 | 调拨管理 | UPDATE |
| 83 | com/jjx/inventory/controller/InventoryTransferController.java | 94 | confirmIn | 调入确认 | 调拨管理 | UPDATE |
| 84 | com/jjx/inventory/controller/InventoryTransferController.java | 104 | cancel | 取消调拨单 | 调拨管理 | UPDATE |
| 85 | com/jjx/inventory/controller/InventoryTransferController.java | 127 | updateStatus | 更新调拨单状态 | 调拨管理 | UPDATE |
| 86 | com/jjx/inventory/controller/InventoryWarehouseController.java | 88 | add |  | 仓库管理 | INSERT |
| 87 | com/jjx/inventory/controller/InventoryWarehouseController.java | 109 | update |  | 仓库管理 | UPDATE |
| 88 | com/jjx/inventory/controller/InventoryWarehouseController.java | 135 | delete |  | 仓库管理 | DELETE |
| 89 | com/jjx/inventory/controller/InventoryWarehouseController.java | 146 | updateStatus |  | 仓库管理 | UPDATE |
| 90 | com/jjx/inventory/controller/InventoryWarehouseController.java | 163 | batchUpdateStatus |  | 仓库管理 | UPDATE |
| 91 | com/jjx/inventory/controller/OrderMaterialReserveController.java | 28 | reserve | 材料预占（按BOM展开原料，天数1~7默认3） | 材料预占 | UPDATE |
| 92 | com/jjx/inventory/controller/OrderMaterialReserveController.java | 37 | extend | 延迟预占（每次+3天） | 材料预占 | UPDATE |
| 93 | com/jjx/inventory/controller/OrderMaterialReserveController.java | 46 | release | 释放预占（取消/完成/手动） | 材料预占 | UPDATE |
| 94 | com/jjx/kanban/controller/BoardTaskController.java | 209 | updateTaskStatus | 更新看板任务状态 | 看板任务 | UPDATE |
| 95 | com/jjx/kanban/controller/BoardTaskController.java | 232 | updateTaskInfo | 更新看板任务内容 | 看板任务 | UPDATE |
| 96 | com/jjx/product/controller/EngineeringRoutingController.java | 56 | create | 创建工艺路线 | 工艺路线管理 | INSERT |
| 97 | com/jjx/product/controller/EngineeringRoutingController.java | 66 | update | 更新工艺路线 | 工艺路线管理 | UPDATE |
| 98 | com/jjx/product/controller/EngineeringRoutingController.java | 80 | delete | 删除工艺路线 | 工艺路线管理 | DELETE |
| 99 | com/jjx/product/controller/EngineeringRoutingController.java | 94 | copyAsNewVersion | 复制为新版本 | 工艺路线管理 | UPDATE |
| 100 | com/jjx/product/controller/EngineeringRoutingController.java | 108 | setCurrentVersion | 设置当前版本 | 工艺路线管理 | UPDATE |
| 101 | com/jjx/product/controller/EngineeringRoutingController.java | 153 | submitApprove | 提交审批 | 工艺路线管理 | UPDATE |
| 102 | com/jjx/product/controller/EngineeringRoutingController.java | 165 | approve | 审批通过 | 工艺路线管理 | APPROVE |
| 103 | com/jjx/product/controller/EngineeringRoutingController.java | 179 | reject | 审批驳回 | 工艺路线管理 | UPDATE |
| 104 | com/jjx/product/controller/ProductController.java | 90 | obsolete |  | 产品管理 | UPDATE |
| 105 | com/jjx/product/controller/ProductController.java | 100 | cancel |  | 产品管理 | UPDATE |
| 106 | com/jjx/product/controller/ProductController.java | 110 | add |  | 产品管理 | INSERT |
| 107 | com/jjx/product/controller/ProductController.java | 122 | edit |  | 产品管理 | UPDATE |
| 108 | com/jjx/product/controller/ProductController.java | 154 | remove |  | 产品管理 | DELETE |
| 109 | com/jjx/product/controller/ProductController.java | 166 | release |  | 产品管理 | UPDATE |
| 110 | com/jjx/product/controller/ProductController.java | 178 | submit |  | 产品管理 | UPDATE |
| 111 | com/jjx/product/controller/ProductController.java | 191 | approve |  | 产品管理 | UPDATE |
| 112 | com/jjx/product/controller/ProductController.java | 203 | reject |  | 产品管理 | UPDATE |
| 113 | com/jjx/product/controller/ProductStandardProcessController.java | 145 | importProcesses | 标准工序导入（2026-08-08，照物料导入模式：模板/校验/失败明细） | 标准工序管理 | IMPORT |
| 114 | com/jjx/production/controller/ProductionLabelPrintController.java | 20 | printLog | 记录标签打印留痕 | 标签打印 | OTHER |
| 115 | com/jjx/production/controller/ProductionOperationExecutionController.java | 40 | createExecution | 创建工序执行记录 | 工序执行管理 | INSERT |
| 116 | com/jjx/production/controller/ProductionOperationExecutionController.java | 49 | updateExecution | 更新工序执行记录 | 工序执行管理 | UPDATE |
| 117 | com/jjx/production/controller/ProductionOperationExecutionController.java | 58 | deleteExecution | 删除工序执行记录 | 工序执行管理 | DELETE |
| 118 | com/jjx/production/controller/ProductionOperationExecutionController.java | 67 | batchDeleteExecution | 批量删除工序执行记录 | 工序执行管理 | DELETE |
| 119 | com/jjx/production/controller/ProductionOperationExecutionController.java | 110 | startExecution | 开始工序执行（可选设备码，扫码C软校验） | 工序执行管理 | UPDATE |
| 120 | com/jjx/production/controller/ProductionOperationExecutionController.java | 120 | pauseExecution | 暂停工序执行 | 工序执行管理 | UPDATE |
| 121 | com/jjx/production/controller/ProductionOperationExecutionController.java | 129 | qualityCheck | 工序首检/巡检（DEV-371） | 工序执行管理 | UPDATE |
| 122 | com/jjx/production/controller/ProductionOperationExecutionController.java | 143 | completeExecution | 完成工序执行 | 工序执行管理 | UPDATE |
| 123 | com/jjx/production/controller/ProductionOperationExecutionController.java | 152 | cancelExecution | 取消工序执行 | 工序执行管理 | UPDATE |
| 124 | com/jjx/production/controller/ProductionOperationExecutionController.java | 177 | importExecutionData | 导入工序执行数据 | 工序执行管理 | IMPORT |
| 125 | com/jjx/production/controller/ProductionOperationRecordController.java | 36 | createRecord | 创建工序记录 | 工序记录管理 | INSERT |
| 126 | com/jjx/production/controller/ProductionOperationRecordController.java | 45 | updateRecord | 更新工序记录 | 工序记录管理 | UPDATE |
| 127 | com/jjx/production/controller/ProductionOperationRecordController.java | 54 | deleteRecord | 删除工序记录 | 工序记录管理 | DELETE |
| 128 | com/jjx/production/controller/ProductionOperationRecordController.java | 63 | batchDeleteRecord | 批量删除工序记录 | 工序记录管理 | DELETE |
| 129 | com/jjx/production/controller/ProductionOperationRecordController.java | 120 | importRecordData | 导入工序记录数据 | 工序记录管理 | IMPORT |
| 130 | com/jjx/production/controller/ProductionOrderController.java | 43 | createOrder | 创建生产工单 | 生产工单管理 | INSERT |
| 131 | com/jjx/production/controller/ProductionOrderController.java | 52 | updateOrder | 更新生产工单 | 生产工单管理 | UPDATE |
| 132 | com/jjx/production/controller/ProductionOrderController.java | 61 | deleteOrder | 删除生产工单 | 生产工单管理 | DELETE |
| 133 | com/jjx/production/controller/ProductionOrderController.java | 70 | batchDeleteOrder | 批量删除生产工单 | 生产工单管理 | DELETE |
| 134 | com/jjx/production/controller/ProductionOrderController.java | 107 | startOrder | 启动生产工单 | 生产工单管理 | UPDATE |
| 135 | com/jjx/production/controller/ProductionOrderController.java | 118 | pauseOrder | 暂停生产工单 | 生产工单管理 | UPDATE |
| 136 | com/jjx/production/controller/ProductionOrderController.java | 127 | completeOrder | 完成生产工单 | 生产工单管理 | UPDATE |
| 137 | com/jjx/production/controller/ProductionOrderController.java | 138 | retryInbound | 重试完工入库（056：入库失败打标后重试，成功清除标记） | 生产工单管理 | UPDATE |
| 138 | com/jjx/production/controller/ProductionOrderController.java | 146 | cancelOrder | 取消生产工单 | 生产工单管理 | UPDATE |
| 139 | com/jjx/production/controller/ProductionOrderController.java | 155 | closeOrder | 关闭生产工单 | 生产工单管理 | UPDATE |
| 140 | com/jjx/production/controller/ProductionOrderController.java | 185 | copyOrder | 复制生产工单 | 生产工单管理 | INSERT |
| 141 | com/jjx/production/controller/ProductionOrderController.java | 196 | importOrderData | 导入生产工单数据 | 生产工单管理 | IMPORT |
| 142 | com/jjx/production/controller/ProductionOrderController.java | 204 | exportOrderData | 导出生产工单数据(Excel) | 生产工单管理 | EXPORT |
| 143 | com/jjx/production/controller/ProductionOrderController.java | 241 | exportPdf | 导出生产工单PDF（单张表单） | 生产工单管理 | EXPORT |
| 144 | com/jjx/production/controller/ProductionOrderController.java | 272 | updateGanttData | 更新甘特图排期 | 生产工单管理 | UPDATE |
| 145 | com/jjx/production/controller/ProductionOrderController.java | 296 | convertPlanToWorkOrders | 计划转工单 | 生产工单管理 | INSERT |
| 146 | com/jjx/production/controller/ProductionOrderController.java | 305 | updateOrderStatus | 更新订单状态 | 生产工单管理 | UPDATE |
| 147 | com/jjx/production/controller/ProductionOrderController.java | 318 | batchUpdateOrderStatus | 批量更新订单状态 | 生产工单管理 | UPDATE |
| 148 | com/jjx/production/controller/QualityTemplateRegistryController.java | 45 | printLog | 记录打印留痕 | 质量记录打印 | OTHER |
| 149 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 72 | add |  | 采购发票管理 | INSERT |
| 150 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 87 | edit |  | 采购发票管理 | UPDATE |
| 151 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 99 | remove |  | 采购发票管理 | DELETE |
| 152 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 123 | verify |  | 采购发票管理 | UPDATE |
| 153 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 218 | batchVerify |  | 采购发票管理 | UPDATE |
| 154 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 235 | importInvoice |  | 采购发票管理 | IMPORT |
| 155 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 382 | batchDeleteFiles |  | 采购发票管理 | UPDATE |
| 156 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 446 | uploadTempFile |  | 采购发票管理 | UPDATE |
| 157 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 468 | batchConfirm |  | 采购发票管理 | INSERT |
| 158 | com/jjx/purchase/controller/PurchaseInvoiceController.java | 483 | deleteTempFile |  | 采购发票管理 | DELETE |
| 159 | com/jjx/purchase/controller/PurchaseOrderController.java | 84 | add | 查询物料在途采购量 | 采购订单管理 | INSERT |
| 160 | com/jjx/purchase/controller/PurchaseOrderController.java | 95 | edit |  | 采购订单管理 | UPDATE |
| 161 | com/jjx/purchase/controller/PurchaseOrderController.java | 106 | cancel |  | 采购订单管理 | UPDATE |
| 162 | com/jjx/purchase/controller/PurchaseOrderController.java | 119 | returnGoods | 采购退货 | 采购订单管理 | UPDATE |
| 163 | com/jjx/purchase/controller/PurchaseOrderController.java | 134 | submit |  | 采购订单管理 | UPDATE |
| 164 | com/jjx/purchase/controller/PurchaseOrderController.java | 145 | batchSubmit |  | 采购订单管理 | UPDATE |
| 165 | com/jjx/purchase/controller/PurchaseOrderController.java | 156 | approve |  | 采购订单管理 | APPROVE |
| 166 | com/jjx/purchase/controller/PurchaseOrderController.java | 169 | updateStatus |  | 采购订单管理 | UPDATE |
| 167 | com/jjx/purchase/controller/PurchaseOrderController.java | 181 | receive |  | 采购订单管理 | UPDATE |
| 168 | com/jjx/purchase/controller/PurchaseOrderController.java | 193 | updateReceiptStatus |  | 采购订单管理 | UPDATE |
| 169 | com/jjx/purchase/controller/PurchaseOrderController.java | 204 | updatePayment |  | 采购订单管理 | UPDATE |
| 170 | com/jjx/purchase/controller/PurchaseOrderController.java | 239 | confirmPlan | 确认计划单转正式采购单（已弃用） | 采购订单管理 | UPDATE |
| 171 | com/jjx/purchase/controller/PurchaseOrderController.java | 260 | planPrintLog | 记录采购计划打印 | 采购计划 | OTHER |
| 172 | com/jjx/purchase/controller/PurchaseOrderController.java | 272 | createPlanFromSuggestions | 缺料预警一键生成采购计划单（已弃用） | 采购订单管理 | INSERT |
| 173 | com/jjx/purchase/controller/PurchaseOrderController.java | 287 | createPlanFromAlerts | 选中预警一键生成采购计划单（已弃用） | 采购订单管理 | INSERT |
| 174 | com/jjx/purchase/controller/PurchaseOrderController.java | 298 | copy |  | 采购订单管理 | INSERT |
| 175 | com/jjx/purchase/controller/PurchaseOrderController.java | 308 | export |  | 采购订单管理 | EXPORT |
| 176 | com/jjx/purchase/controller/PurchaseOrderController.java | 318 | exportDetail |  | 采购订单管理 | EXPORT |
| 177 | com/jjx/purchase/controller/PurchaseOrderController.java | 328 | exportPdf | 导出采购订单PDF | 采购订单管理 | EXPORT |
| 178 | com/jjx/purchase/controller/PurchaseOrderController.java | 346 | deleteOrder |  | 采购订单管理 | DELETE |
| 179 | com/jjx/purchase/controller/PurchasePaymentController.java | 57 | add |  | 采购付款管理 | INSERT |
| 180 | com/jjx/purchase/controller/PurchasePaymentController.java | 68 | edit |  | 采购付款管理 | UPDATE |
| 181 | com/jjx/purchase/controller/PurchasePaymentController.java | 79 | remove |  | 采购付款管理 | DELETE |
| 182 | com/jjx/purchase/controller/PurchasePaymentController.java | 99 | approve |  | 采购付款管理 | APPROVE |
| 183 | com/jjx/purchase/controller/PurchasePaymentController.java | 113 | confirm |  | 采购付款管理 | UPDATE |
| 184 | com/jjx/purchase/controller/PurchasePaymentController.java | 124 | uploadVoucher |  | 采购付款管理 | UPDATE |
| 185 | com/jjx/purchase/controller/PurchasePaymentController.java | 216 | batchPayment |  | 采购付款管理 | INSERT |
| 186 | com/jjx/purchase/controller/PurchasePaymentController.java | 229 | batchApprove |  | 采购付款管理 | APPROVE |
| 187 | com/jjx/purchase/controller/PurchasePaymentController.java | 246 | importPayment |  | 采购付款管理 | IMPORT |
| 188 | com/jjx/purchase/controller/PurchaseReceiptController.java | 66 | add |  | 采购收货管理 | INSERT |
| 189 | com/jjx/purchase/controller/PurchaseReceiptController.java | 81 | edit |  | 采购收货管理 | UPDATE |
| 190 | com/jjx/purchase/controller/PurchaseReceiptController.java | 96 | remove |  | 采购收货管理 | DELETE |
| 191 | com/jjx/purchase/controller/PurchaseReceiptController.java | 115 | inspect |  | 采购收货管理 | UPDATE |
| 192 | com/jjx/purchase/controller/PurchaseReceiptController.java | 135 | confirm |  | 采购收货管理 | UPDATE |
| 193 | com/jjx/purchase/controller/PurchaseReceiptController.java | 276 | batchReceive |  | 采购收货管理 | INSERT |
| 194 | com/jjx/purchase/controller/PurchaseReceiptController.java | 294 | batchInspect |  | 采购收货管理 | UPDATE |
| 195 | com/jjx/purchase/controller/PurchaseReceiptController.java | 316 | importReceipt |  | 采购收货管理 | IMPORT |
| 196 | com/jjx/purchase/controller/PurchaseSupplierController.java | 63 | add | 新增供应商 | 供应商管理 | INSERT |
| 197 | com/jjx/purchase/controller/PurchaseSupplierController.java | 74 | edit | 修改供应商 | 供应商管理 | UPDATE |
| 198 | com/jjx/purchase/controller/PurchaseSupplierController.java | 85 | remove | 删除供应商 | 供应商管理 | DELETE |
| 199 | com/jjx/purchase/controller/PurchaseSupplierController.java | 107 | changeStatus | 更新供应商状态 | 供应商管理 | UPDATE |
| 200 | com/jjx/purchase/controller/PurchaseSupplierController.java | 122 | updateEvaluation | 更新供应商评估信息 | 供应商管理 | UPDATE |
| 201 | com/jjx/purchase/controller/PurchaseSupplierController.java | 194 | importSuppliers | 导入供应商数据 | 供应商管理 | IMPORT |
| 202 | com/jjx/sales/controller/CustomerController.java | 89 | addCustomer | 新增客户 | 客户管理 | INSERT |
| 203 | com/jjx/sales/controller/CustomerController.java | 110 | updateCustomer | 修改客户 | 客户管理 | UPDATE |
| 204 | com/jjx/sales/controller/CustomerController.java | 122 | deleteCustomers | 删除客户 | 客户管理 | DELETE |
| 205 | com/jjx/sales/controller/CustomerController.java | 148 | importCustomers | 导入客户 | 客户管理 | IMPORT |
| 206 | com/jjx/sales/controller/CustomerController.java | 181 | changeCustomerStatus | 变更客户状态 | 客户管理 | UPDATE |
| 207 | com/jjx/sales/controller/CustomerController.java | 192 | approveCustomers | 批量审核客户 | 客户管理 | APPROVE |
| 208 | com/jjx/sales/controller/CustomerController.java | 215 | updateCustomerCreditLimit | 更新客户信用额度 | 客户管理 | UPDATE |
| 209 | com/jjx/sales/controller/InquiryController.java | 69 | add | 新增询价单 | 询价单管理 | INSERT |
| 210 | com/jjx/sales/controller/InquiryController.java | 88 | edit | 修改询价单 | 询价单管理 | UPDATE |
| 211 | com/jjx/sales/controller/InquiryController.java | 99 | remove | 删除询价单 | 询价单管理 | DELETE |
| 212 | com/jjx/sales/controller/InquiryController.java | 120 | send | 发送询价（草稿/待处理 → 已发送） | 询价单管理 | UPDATE |
| 213 | com/jjx/sales/controller/InquiryController.java | 131 | accept | 客户确认询价（已发送 → 已确认） | 询价单管理 | UPDATE |
| 214 | com/jjx/sales/controller/InquiryController.java | 142 | reject | 客户拒绝询价（已发送 → 已拒绝） | 询价单管理 | UPDATE |
| 215 | com/jjx/sales/controller/OrderController.java | 114 | deleteOrders | 删除销售订单 | 销售订单管理 | DELETE |
| 216 | com/jjx/sales/controller/OrderController.java | 190 | createOrderInstances | 创建产品实例 | 销售订单管理 | UPDATE |
| 217 | com/jjx/sales/controller/OrderController.java | 201 | updateOrderPayment | 更新付款信息 | 销售订单管理 | UPDATE |
| 218 | com/jjx/sales/controller/OrderReviewController.java | 37 | submitOrderForReview | 提交订单审核 | 订单审核管理 | UPDATE |
| 219 | com/jjx/sales/controller/OrderReviewController.java | 52 | startOrderReview | 开始审核订单 | 订单审核管理 | UPDATE |
| 220 | com/jjx/sales/controller/OrderReviewController.java | 67 | approveOrder | 审核通过订单 | 订单审核管理 | APPROVE |
| 221 | com/jjx/sales/controller/OrderReviewController.java | 83 | rejectOrder | 审核驳回订单 | 订单审核管理 | APPROVE |
| 222 | com/jjx/sales/controller/OrderReviewController.java | 100 | returnOrderForModification | 退回订单修改 | 订单审核管理 | UPDATE |
| 223 | com/jjx/sales/controller/OrderReviewController.java | 117 | transferOrderReview | 转交审核 | 订单审核管理 | UPDATE |
| 224 | com/jjx/sales/controller/OrderReviewController.java | 133 | confirmOrderByCustomer | 客户确认订单 | 订单审核管理 | UPDATE |
| 225 | com/jjx/sales/controller/OrderReviewController.java | 149 | cancelOrderReview | 取消订单审核 | 订单审核管理 | UPDATE |
| 226 | com/jjx/sales/controller/OrderReviewController.java | 262 | batchSubmitForReview | 批量提交审核 | 订单审核管理 | UPDATE |
| 227 | com/jjx/sales/controller/OrderReviewController.java | 276 | batchApproveOrders | 批量审核通过 | 订单审核管理 | APPROVE |
| 228 | com/jjx/sales/controller/OrderReviewController.java | 290 | batchRejectOrders | 批量审核驳回 | 订单审核管理 | APPROVE |
| 229 | com/jjx/sales/controller/OrderStatusController.java | 36 | submitReview | 提交审核 | 订单状态管理 | UPDATE |
| 230 | com/jjx/sales/controller/OrderStatusController.java | 51 | startReview | 开始审核 | 订单状态管理 | UPDATE |
| 231 | com/jjx/sales/controller/OrderStatusController.java | 66 | approveOrder | 审核通过 | 订单状态管理 | APPROVE |
| 232 | com/jjx/sales/controller/OrderStatusController.java | 83 | rejectOrder | 审核驳回 | 订单状态管理 | APPROVE |
| 233 | com/jjx/sales/controller/OrderStatusController.java | 100 | resubmit | 重新提交审核（驳回后） | 订单状态管理 | UPDATE |
| 234 | com/jjx/sales/controller/OrderStatusController.java | 115 | cancelOrder | 取消订单 | 订单状态管理 | UPDATE |
| 235 | com/jjx/sales/controller/OrderStatusController.java | 158 | generatePlan | 生成生产计划（标准模式：SO→PLAN→审批→转工单） | 订单状态管理 | UPDATE |
| 236 | com/jjx/sales/controller/OrderStatusController.java | 173 | shipOrder | 发货（生产中→已发货，联动创建销售出库单并扣产品库存） | 订单状态管理 | UPDATE |
| 237 | com/jjx/sales/controller/OrderStatusController.java | 189 | completeOrder | 完成订单 | 订单状态管理 | UPDATE |
| 238 | com/jjx/sales/controller/OrderStatusController.java | 204 | confirmOrder | 客户确认订单 | 订单状态管理 | UPDATE |
| 239 | com/jjx/sales/controller/QuotationController.java | 76 | add | 新增销售报价单 | 报价单管理 | INSERT |
| 240 | com/jjx/sales/controller/QuotationController.java | 87 | edit | 修改销售报价单 | 报价单管理 | UPDATE |
| 241 | com/jjx/sales/controller/QuotationController.java | 100 | remove | 删除销售报价单 | 报价单管理 | DELETE |
| 242 | com/jjx/sales/controller/QuotationController.java | 126 | send | 发送报价单给客户 | 报价单管理 | UPDATE |
| 243 | com/jjx/sales/controller/QuotationController.java | 138 | convert | 报价单转为订单 | 报价单管理 | UPDATE |
| 244 | com/jjx/sales/controller/QuotationController.java | 183 | copy | 复制报价单 | 报价单管理 | INSERT |
| 245 | com/jjx/sales/controller/QuotationController.java | 194 | submitReview | 提交报价单审核 | 报价单管理 | UPDATE |
| 246 | com/jjx/sales/controller/QuotationController.java | 206 | review | 审核报价单 | 报价单管理 | APPROVE |
| 247 | com/jjx/sales/controller/QuotationController.java | 232 | changeStatus | 更新报价单状态 | 报价单管理 | UPDATE |
| 248 | com/jjx/sales/controller/QuotationController.java | 252 | confirm | 客户确认报价 | 报价单管理 | UPDATE |
| 249 | com/jjx/sales/controller/QuotationController.java | 264 | reject | 客户拒绝报价 | 报价单管理 | UPDATE |
| 250 | com/jjx/sales/controller/QuotationController.java | 276 | modify | 已完成报价单改单 | 报价单管理 | UPDATE |
| 251 | com/jjx/sales/controller/SalesDeliveryController.java | 54 | receive | 签收发货单 | 销售发货 | UPDATE |
| 252 | com/jjx/sales/controller/SalesInvoiceController.java | 49 | printLog | 记录发票打印 | 销售发票 | OTHER |
| 253 | com/jjx/sales/controller/SalesInvoiceController.java | 55 | create | 新增发票 | 销售发票 | INSERT |
| 254 | com/jjx/sales/controller/SalesInvoiceController.java | 63 | update | 修改发票 | 销售发票 | UPDATE |
| 255 | com/jjx/sales/controller/SalesInvoiceController.java | 75 | delete | 删除发票 | 销售发票 | DELETE |
| 256 | com/jjx/sales/controller/SalesReceiptController.java | 49 | printLog | 记录收款单打印 | 销售收款 | OTHER |
| 257 | com/jjx/sales/controller/SalesReceiptController.java | 55 | create | 新增收款 | 销售收款 | INSERT |
| 258 | com/jjx/sales/controller/SalesReceiptController.java | 63 | update | 修改收款 | 销售收款 | UPDATE |
| 259 | com/jjx/sales/controller/SalesReceiptController.java | 75 | delete | 删除收款 | 销售收款 | DELETE |
| 260 | com/jjx/sales/controller/SalesReturnController.java | 48 | create | 创建退货单（申请中） | 销售退货管理 | INSERT |
| 261 | com/jjx/sales/controller/SalesReturnController.java | 57 | approve | 审核通过 | 销售退货管理 | APPROVE |
| 262 | com/jjx/sales/controller/SalesReturnController.java | 70 | reject | 审核驳回 | 销售退货管理 | APPROVE |
| 263 | com/jjx/sales/controller/SalesReturnController.java | 82 | receive | 收货确认（联动退货入库） | 销售退货管理 | UPDATE |
| 264 | com/jjx/sales/controller/SalesReturnController.java | 95 | refund | 退款（回写订单付款状态） | 销售退货管理 | UPDATE |
| 265 | com/jjx/sales/controller/SampleOrderController.java | 34 | create | 新增样品单（直接选客户+产品明细，报价单可选） | 样品单管理 | INSERT |
| 266 | com/jjx/sales/controller/SampleOrderController.java | 42 | update | 更新样品单（驳回后编辑：仅样品需求已创建状态可编辑，明细全量替换） | 样品单管理 | UPDATE |
| 267 | com/jjx/sales/controller/SampleOrderController.java | 51 | createFromQuotation | 从报价单创建样品单 | 样品单管理 | INSERT |
| 268 | com/jjx/sales/controller/SampleOrderController.java | 67 | copy | 复制样品单（仅已完成/已取消终态单，一键生成新草稿单） | 样品单管理 | INSERT |
| 269 | com/jjx/sales/controller/SampleOrderController.java | 114 | submitRequest | 样品单申请打样 | 样品单管理 | UPDATE |
| 270 | com/jjx/sales/controller/SampleOrderController.java | 123 | approve | 样品单审核通过（进入工程打样） | 样品单管理 | APPROVE |
| 271 | com/jjx/sales/controller/SampleOrderController.java | 133 | rejectReview | 样品单审核驳回 | 样品单管理 | UPDATE |
| 272 | com/jjx/sales/controller/SampleOrderController.java | 143 | startEngineering | 工程接单（记录工程备注） | 样品单管理 | UPDATE |
| 273 | com/jjx/sales/controller/SampleOrderController.java | 152 | markReady | 工程标记样品完成（待送样） | 样品单管理 | UPDATE |
| 274 | com/jjx/sales/controller/SampleOrderController.java | 161 | sendSample | 销售送样登记 | 样品单管理 | UPDATE |
| 275 | com/jjx/sales/controller/SampleOrderController.java | 172 | confirm | 客户确认样品OK | 样品单管理 | UPDATE |
| 276 | com/jjx/sales/controller/SampleOrderController.java | 183 | rejectSample | 客户退回样品（多轮迭代） | 样品单管理 | UPDATE |
| 277 | com/jjx/sales/controller/SampleOrderController.java | 194 | convertToProduction | 样品转量产（生成标准订单，可传产品标准化items） | 样品单管理 | UPDATE |
| 278 | com/jjx/sales/controller/SampleOrderController.java | 206 | cancel | 样品单作废 | 样品单管理 | UPDATE |
| 279 | com/jjx/sales/controller/SampleOrderController.java | 218 | restartEngineering | 退回后重新打样 | 样品单管理 | UPDATE |
| 280 | com/jjx/sales/controller/SampleOrderController.java | 229 | acceptEngineering | 工程接单确认 | 样品单管理 | UPDATE |
| 281 | com/jjx/sales/controller/SampleOrderController.java | 240 | rejectEngineering | 工程拒单 | 样品单管理 | UPDATE |
| 282 | com/jjx/sales/controller/SampleOrderController.java | 252 | updateProcess | 更新打样当前工序（材料JSON走body，避免长URL，8-03改DTO） | 样品单管理 | UPDATE |
| 283 | com/jjx/sales/controller/SampleOrderController.java | 268 | saveProcessPlan | 保存打样工序计划（多选作业项目，整单覆盖当前轮次） | 样品单管理 | UPDATE |
| 284 | com/jjx/sales/controller/SampleOrderController.java | 281 | updateProcessItemStatus | 推进打样工序状态（开始/完成，可带耗时/说明/材料） | 样品单管理 | UPDATE |
| 285 | com/jjx/sales/controller/SampleOrderController.java | 322 | saveBom | 保存打样BOM物料清单 | 样品单管理 | UPDATE |
| 286 | com/jjx/sales/controller/SampleOrderController.java | 344 | recordCost | 录入打样成本/工时 | 样品单管理 | UPDATE |
| 287 | com/jjx/sales/controller/SampleOrderController.java | 358 | transfer | 产品资料转移（DEV-505：建档产品/BOM/工艺路线，状态初始化，事件通知+派任务） | 样品单管理 | UPDATE |
| 288 | com/jjx/sales/controller/SampleTransferController.java | 49 | confirm | 打样转标准-确认转移（接收前端编辑后的标准数据落库） | 样品单管理 | UPDATE |
| 289 | com/jjx/system/controller/system/EventConfigController.java | 76 | add |  | 事件配置 | INSERT |
| 290 | com/jjx/system/controller/system/EventConfigController.java | 88 | edit |  | 事件配置 | UPDATE |
| 291 | com/jjx/system/controller/system/EventConfigController.java | 98 | remove |  | 事件配置 | DELETE |
| 292 | com/jjx/system/controller/system/SysDeptController.java | 77 | add |  | 部门管理 | INSERT |
| 293 | com/jjx/system/controller/system/SysDeptController.java | 98 | edit |  | 部门管理 | UPDATE |
| 294 | com/jjx/system/controller/system/SysDeptController.java | 126 | remove |  | 部门管理 | DELETE |
| 295 | com/jjx/system/controller/system/SysMenuController.java | 71 | add |  | 菜单管理 | INSERT |
| 296 | com/jjx/system/controller/system/SysMenuController.java | 85 | edit |  | 菜单管理 | UPDATE |
| 297 | com/jjx/system/controller/system/SysMenuController.java | 99 | remove |  | 菜单管理 | DELETE |
| 298 | com/jjx/system/controller/system/SysMenuController.java | 143 | addAuthRoles |  | 菜单管理 | UPDATE |
| 299 | com/jjx/system/controller/system/SysRoleController.java | 66 | add |  | 角色管理 | INSERT |
| 300 | com/jjx/system/controller/system/SysRoleController.java | 87 | edit |  | 角色管理 | UPDATE |
| 301 | com/jjx/system/controller/system/SysRoleController.java | 111 | dataScope |  | 角色管理 | UPDATE |
| 302 | com/jjx/system/controller/system/SysRoleController.java | 126 | changeStatus |  | 角色管理 | UPDATE |
| 303 | com/jjx/system/controller/system/SysRoleController.java | 141 | remove |  | 角色管理 | DELETE |
| 304 | com/jjx/system/controller/system/SysRoleController.java | 177 | cancelAuthUser |  | 角色管理 | UPDATE |
| 305 | com/jjx/system/controller/system/SysRoleController.java | 190 | cancelAuthUserAll |  | 角色管理 | UPDATE |
| 306 | com/jjx/system/controller/system/SysRoleController.java | 200 | selectAuthUserAll |  | 角色管理 | UPDATE |
| 307 | com/jjx/system/controller/system/SysRoleController.java | 220 | selectAuthMenuAll |  | 角色管理 | UPDATE |
| 308 | com/jjx/system/controller/system/SysUserController.java | 141 | add |  | 用户管理 | INSERT |
| 309 | com/jjx/system/controller/system/SysUserController.java | 151 | edit |  | 用户管理 | UPDATE |
| 310 | com/jjx/system/controller/system/SysUserController.java | 162 | remove |  | 用户管理 | DELETE |
| 311 | com/jjx/system/controller/system/SysUserController.java | 172 | resetPwd |  | 用户管理 | UPDATE |
| 312 | com/jjx/system/controller/system/SysUserController.java | 182 | changeStatus |  | 用户管理 | UPDATE |
| 313 | com/jjx/system/controller/system/SysUserController.java | 201 | insertAuthRole |  | 用户管理 | UPDATE |
| 314 | com/jjx/system/controller/system/SysUserController.java | 221 | profile |  | 用户管理 | UPDATE |
| 315 | com/jjx/system/controller/system/SysUserController.java | 231 | updatePwd |  | 用户管理 | UPDATE |
| 316 | com/jjx/system/controller/system/SysUserController.java | 241 | avatar |  | 用户管理 | UPDATE |
