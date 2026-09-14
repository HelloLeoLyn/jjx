# LogActions 常量草案（阶段2 铺码清单，dev-20260904-007）

形态：将来落为 com/jjx/common/constant/LogActions.java，每行
`public static final String XXX = "中文文案";`，注解写 `action = LogActions.XXX`。
文案相同可共用同一常量（下面已合并）。文案措辞待用户过目修改。

## com/jjx/biz/controller/BizRequirementController.java（业务需求 8）
| 行 | 方法 | 常量 | 文案 |
|----|----|----|----|
| 130 | add | REQ_CREATE | 新增需求单 |
| 138 | edit | REQ_EDIT | 修改需求单 |
| 147 | remove | REQ_DELETE | 删除需求单 |
| 156 | submit | REQ_SUBMIT | 提交评审 |
| 165 | approval | REQ_COUNTERSIGN | 四部门会签 |
| 183 | upgrade | REQ_UPGRADE | 变更升版 |
| 192 | execute | REQ_EXECUTE | 开始执行 |
| 201 | close | REQ_CLOSE | 关闭需求单 |

## com/jjx/engineering/controller/BomController.java（BOM 7）
| 142 | add | BOM_CREATE | 新增BOM |
| 155 | edit | BOM_EDIT | 修改BOM |
| 167 | remove | BOM_DELETE | 删除BOM |
| 179 | submit | BOM_SUBMIT | 提交BOM审核 |
| 190 | approve | BOM_APPROVE | BOM审核通过 |
| 201 | reject | BOM_REJECT | BOM审核驳回 |
| 212 | setDefault | BOM_SET_DEFAULT | 设为默认BOM |

## com/jjx/engineering/controller/EngineeringController.java（工程 3）
| 38 | save | ENGINEERING_CREATE | 新增工程记录 |
| 47 | update | ENGINEERING_EDIT | 修改工程记录 |
| 56 | delete | ENGINEERING_DELETE | 删除工程记录 |

## com/jjx/inventory/controller/InventoryAlertController.java（库存预警 11）
| 39 | executeAlertCheck | ALERT_EXECUTE_CHECK | 执行预警检查 |
| 48 | checkSafeStockAlert | ALERT_CHECK_SAFE_STOCK | 检查安全库存预警 |
| 57 | checkMaxStockAlert | ALERT_CHECK_MAX_STOCK | 检查最高库存预警 |
| 66 | checkExpiryAlert | ALERT_CHECK_EXPIRY | 检查保质期预警 |
| 75 | checkObsoleteAlert | ALERT_CHECK_OBSOLETE | 检查呆滞料预警 |
| 84 | checkOrderShortage | ALERT_CHECK_ORDER_SHORTAGE | 订单齐套缺料检查 |
| 92 | checkGlobalShortage | ALERT_CHECK_GLOBAL_SHORTAGE | 全局汇总缺料检查 |
| 107 | markRead | ALERT_MARK_READ | 标记预警已读 |
| 115 | batchMarkRead | ALERT_BATCH_MARK_READ | 批量标记预警已读 |
| 123 | processAlert | ALERT_PROCESS | 处理预警 |
| 133 | batchProcessAlert | ALERT_BATCH_PROCESS | 批量处理预警 |

## com/jjx/inventory/controller/InventoryInboundController.java（入库 10）
| 46 | create | INBOUND_CREATE | 创建入库单 |
| 54 | confirm | INBOUND_CONFIRM | 确认入库 |
| 64 | cancel | INBOUND_CANCEL | 取消入库单 |
| 73 | submitApprove | INBOUND_SUBMIT | 提交入库审批 |
| 81 | approve | INBOUND_APPROVE | 入库审批通过 |
| 92 | reject | INBOUND_REJECT | 入库审批驳回 |
| 103 | createFromPurchase | INBOUND_FROM_PURCHASE | 从采购订单创建入库单 |
| 111 | createFromProduction | INBOUND_FROM_PRODUCTION | 从生产工单创建入库单 |
| 142 | updateStatus | INBOUND_UPDATE_STATUS | 更新入库单状态 |
| 151 | exportPdf | INBOUND_EXPORT_PDF | 导出入库单PDF |

## com/jjx/inventory/controller/InventoryMaterialCategoryController.java（物料分类 4）
| 51 | add | MAT_CATEGORY_CREATE | 新增物料分类 |
| 61 | update | MAT_CATEGORY_EDIT | 修改物料分类 |
| 71 | delete | MAT_CATEGORY_DELETE | 删除物料分类 |
| 79 | updateStatus | MAT_CATEGORY_STATUS | 更新分类状态 |

## com/jjx/inventory/controller/InventoryMaterialController.java（物料 6）
| 89 | add | MATERIAL_CREATE | 新增物料 |
| 110 | update | MATERIAL_EDIT | 修改物料 |
| 130 | delete | MATERIAL_DELETE | 删除物料 |
| 141 | updateStatus | MATERIAL_STATUS | 更新物料状态 |
| 153 | batchUpdateStatus | MATERIAL_BATCH_STATUS | 批量更新物料状态 |
| 199 | importMaterial | MATERIAL_IMPORT | 导入物料 |

## com/jjx/inventory/controller/InventoryOutboundController.java（出库 12）
| 46 | create | OUTBOUND_CREATE | 创建出库单 |
| 54 | confirm | OUTBOUND_CONFIRM | 确认出库 |
| 64 | update | OUTBOUND_EDIT | 修改出库单 |
| 72 | cancel | OUTBOUND_CANCEL | 取消出库单 |
| 81 | submitApprove | OUTBOUND_SUBMIT | 提交出库审批 |
| 89 | approve | OUTBOUND_APPROVE | 出库审批通过 |
| 100 | reject | OUTBOUND_REJECT | 出库审批驳回 |
| 111 | createFromProduction | OUTBOUND_FROM_PRODUCTION | 从生产工单创建出库单 |
| 127 | createProductionPick | OUTBOUND_PRODUCTION_PICK | 追加生产领料 |
| 143 | createFromSales | OUTBOUND_FROM_SALES | 从销售订单创建出库单 |
| 174 | updateStatus | OUTBOUND_UPDATE_STATUS | 更新出库单状态 |
| 183 | exportPdf | OUTBOUND_EXPORT_PDF | 导出出库单PDF |

## com/jjx/inventory/controller/InventoryStockController.java（库存 1）
| 123 | batchImport | STOCK_BATCH_IMPORT | 批量导入库存 |

## com/jjx/inventory/controller/InventoryStocktakeController.java（盘点 9）
| 46 | create | STOCKTAKE_CREATE | 创建盘点单 |
| 54 | startStocktake | STOCKTAKE_START | 开始盘点 |
| 62 | inputStocktakeData | STOCKTAKE_INPUT | 录入盘点数据 |
| 78 | confirmResult | STOCKTAKE_CONFIRM | 确认盘点结果 |
| 86 | processDiff | STOCKTAKE_PROCESS_DIFF | 处理盘盈亏 |
| 96 | closeStocktake | STOCKTAKE_CLOSE | 关闭盘点单 |
| 104 | submitApprove | STOCKTAKE_SUBMIT | 提交盘点审批 |
| 112 | approve | STOCKTAKE_APPROVE | 盘点审批通过 |
| 137 | updateStatus | STOCKTAKE_UPDATE_STATUS | 更新盘点单状态 |

## com/jjx/inventory/controller/InventoryStorageLocationController.java（库位 6）
| 96 | add | LOCATION_CREATE | 新增库位 |
| 120 | update | LOCATION_EDIT | 修改库位 |
| 149 | delete | LOCATION_DELETE | 删除库位 |
| 160 | updateStatus | LOCATION_STATUS | 更新库位状态 |
| 177 | batchUpdateStatus | LOCATION_BATCH_STATUS | 批量更新库位状态 |
| 375 | importStorageLocation | LOCATION_IMPORT | 导入库位 |

## com/jjx/inventory/controller/InventoryTransferController.java（调拨 8）
| 46 | create | TRANSFER_CREATE | 创建调拨单 |
| 54 | submitApprove | TRANSFER_SUBMIT | 提交调拨审批 |
| 62 | approve | TRANSFER_APPROVE | 调拨审批通过 |
| 73 | reject | TRANSFER_REJECT | 调拨审批驳回 |
| 84 | confirmOut | TRANSFER_CONFIRM_OUT | 调出确认 |
| 94 | confirmIn | TRANSFER_CONFIRM_IN | 调入确认 |
| 104 | cancel | TRANSFER_CANCEL | 取消调拨单 |
| 127 | updateStatus | TRANSFER_UPDATE_STATUS | 更新调拨单状态 |

## com/jjx/inventory/controller/InventoryWarehouseController.java（仓库 5）
| 88 | add | WAREHOUSE_CREATE | 新增仓库 |
| 109 | update | WAREHOUSE_EDIT | 修改仓库 |
| 135 | delete | WAREHOUSE_DELETE | 删除仓库 |
| 146 | updateStatus | WAREHOUSE_STATUS | 更新仓库状态 |
| 163 | batchUpdateStatus | WAREHOUSE_BATCH_STATUS | 批量更新仓库状态 |

## com/jjx/inventory/controller/OrderMaterialReserveController.java（材料预占 3）
| 28 | reserve | RESERVE_CREATE | 材料预占 |
| 37 | extend | RESERVE_EXTEND | 延迟预占 |
| 46 | release | RESERVE_RELEASE | 释放预占 |

## com/jjx/kanban/controller/BoardTaskController.java（看板任务 2）
| 209 | updateTaskStatus | KANBAN_TASK_STATUS | 更新任务状态 |
| 232 | updateTaskInfo | KANBAN_TASK_INFO | 修改任务内容 |

## com/jjx/product/controller/EngineeringRoutingController.java（工艺路线 8）
| 56 | create | ROUTING_CREATE | 创建工艺路线 |
| 66 | update | ROUTING_EDIT | 修改工艺路线 |
| 80 | delete | ROUTING_DELETE | 删除工艺路线 |
| 94 | copyAsNewVersion | ROUTING_COPY_VERSION | 复制为新版本 |
| 108 | setCurrentVersion | ROUTING_SET_CURRENT | 设置当前版本 |
| 153 | submitApprove | ROUTING_SUBMIT | 提交工艺路线审批 |
| 165 | approve | ROUTING_APPROVE | 工艺路线审批通过 |
| 179 | reject | ROUTING_REJECT | 工艺路线审批驳回 |

## com/jjx/product/controller/ProductController.java（产品 9）
| 90 | obsolete | PRODUCT_OBSOLETE | 产品停产 |
| 100 | cancel | PRODUCT_CANCEL | 取消产品 |
| 110 | add | PRODUCT_CREATE | 新增产品 |
| 122 | edit | PRODUCT_EDIT | 修改产品 |
| 154 | remove | PRODUCT_DELETE | 删除产品 |
| 166 | release | PRODUCT_RELEASE | 发布产品 |
| 178 | submit | PRODUCT_SUBMIT | 提交产品审核 |
| 191 | approve | PRODUCT_APPROVE | 产品审核通过 |
| 203 | reject | PRODUCT_REJECT | 产品审核驳回 |

## com/jjx/product/controller/ProductStandardProcessController.java（标准工序 1）
| 145 | importProcesses | PROCESS_IMPORT | 导入标准工序 |

## com/jjx/production/controller/ProductionLabelPrintController.java（标签打印 1）
| 20 | printLog | LABEL_PRINT_LOG | 记录标签打印 |

## com/jjx/production/controller/ProductionOperationExecutionController.java（工序执行 10）
| 40 | createExecution | OP_EXEC_CREATE | 创建工序执行记录 |
| 49 | updateExecution | OP_EXEC_EDIT | 修改工序执行记录 |
| 58 | deleteExecution | OP_EXEC_DELETE | 删除工序执行记录 |
| 67 | batchDeleteExecution | OP_EXEC_BATCH_DELETE | 批量删除工序执行记录 |
| 110 | startExecution | OP_EXEC_START | 开始工序执行 |
| 120 | pauseExecution | OP_EXEC_PAUSE | 暂停工序执行 |
| 129 | qualityCheck | OP_EXEC_QUALITY_CHECK | 工序质量检查 |
| 143 | completeExecution | OP_EXEC_COMPLETE | 完成工序执行 |
| 152 | cancelExecution | OP_EXEC_CANCEL | 取消工序执行 |
| 177 | importExecutionData | OP_EXEC_IMPORT | 导入工序执行数据 |

## com/jjx/production/controller/ProductionOperationRecordController.java（工序记录 5）
| 36 | createRecord | OP_RECORD_CREATE | 创建工序记录 |
| 45 | updateRecord | OP_RECORD_EDIT | 修改工序记录 |
| 54 | deleteRecord | OP_RECORD_DELETE | 删除工序记录 |
| 63 | batchDeleteRecord | OP_RECORD_BATCH_DELETE | 批量删除工序记录 |
| 120 | importRecordData | OP_RECORD_IMPORT | 导入工序记录数据 |

## com/jjx/production/controller/ProductionOrderController.java（生产工单 18）
| 43 | createOrder | PROD_ORDER_CREATE | 创建生产工单 |
| 52 | updateOrder | PROD_ORDER_EDIT | 修改生产工单 |
| 61 | deleteOrder | PROD_ORDER_DELETE | 删除生产工单 |
| 70 | batchDeleteOrder | PROD_ORDER_BATCH_DELETE | 批量删除生产工单 |
| 107 | startOrder | PROD_ORDER_START | 启动生产工单 |
| 118 | pauseOrder | PROD_ORDER_PAUSE | 暂停生产工单 |
| 127 | completeOrder | PROD_ORDER_COMPLETE | 完成生产工单 |
| 138 | retryInbound | PROD_ORDER_RETRY_INBOUND | 重试完工入库 |
| 146 | cancelOrder | PROD_ORDER_CANCEL | 取消生产工单 |
| 155 | closeOrder | PROD_ORDER_CLOSE | 关闭生产工单 |
| 185 | copyOrder | PROD_ORDER_COPY | 复制生产工单 |
| 196 | importOrderData | PROD_ORDER_IMPORT | 导入生产工单数据 |
| 204 | exportOrderData | PROD_ORDER_EXPORT | 导出生产工单Excel |
| 241 | exportPdf | PROD_ORDER_EXPORT_PDF | 导出生产工单PDF |
| 272 | updateGanttData | PROD_ORDER_GANTT | 更新甘特图排期 |
| 296 | convertPlanToWorkOrders | PROD_ORDER_PLAN_CONVERT | 计划转工单 |
| 305 | updateOrderStatus | PROD_ORDER_STATUS | 更新工单状态 |
| 318 | batchUpdateOrderStatus | PROD_ORDER_BATCH_STATUS | 批量更新工单状态 |

## com/jjx/production/controller/QualityTemplateRegistryController.java（质量记录打印 1）
| 45 | printLog | QUALITY_TEMPLATE_PRINT_LOG | 记录质量记录打印 |

## com/jjx/purchase/controller/PurchaseInvoiceController.java（采购发票 10）
| 72 | add | PUR_INVOICE_CREATE | 新增采购发票 |
| 87 | edit | PUR_INVOICE_EDIT | 修改采购发票 |
| 99 | remove | PUR_INVOICE_DELETE | 删除采购发票 |
| 123 | verify | PUR_INVOICE_VERIFY | 发票核销 |
| 218 | batchVerify | PUR_INVOICE_BATCH_VERIFY | 批量核销发票 |
| 235 | importInvoice | PUR_INVOICE_IMPORT | 导入采购发票 |
| 382 | batchDeleteFiles | PUR_INVOICE_DELETE_FILES | 批量删除发票附件 |
| 446 | uploadTempFile | PUR_INVOICE_UPLOAD_TEMP | 上传发票临时文件 |
| 468 | batchConfirm | PUR_INVOICE_BATCH_CONFIRM | 批量确认发票 |
| 483 | deleteTempFile | PUR_INVOICE_DELETE_TEMP | 删除发票临时文件 |

## com/jjx/purchase/controller/PurchaseOrderController.java（采购订单 20）
| 84 | add | PUR_ORDER_CREATE | 新增采购订单 |
| 95 | edit | PUR_ORDER_EDIT | 修改采购订单 |
| 106 | cancel | PUR_ORDER_CANCEL | 取消采购订单 |
| 119 | returnGoods | PUR_ORDER_RETURN | 采购退货 |
| 134 | submit | PUR_ORDER_SUBMIT | 提交采购订单审核 |
| 145 | batchSubmit | PUR_ORDER_BATCH_SUBMIT | 批量提交采购订单审核 |
| 156 | approve | PUR_ORDER_APPROVE | 采购订单审核通过 |
| 169 | updateStatus | PUR_ORDER_STATUS | 更新采购订单状态 |
| 181 | receive | PUR_ORDER_RECEIVE | 采购收货 |
| 193 | updateReceiptStatus | PUR_ORDER_RECEIPT_STATUS | 更新收货状态 |
| 204 | updatePayment | PUR_ORDER_PAYMENT | 更新付款信息 |
| 239 | confirmPlan | PUR_ORDER_CONFIRM_PLAN | 确认计划转正式采购单 |
| 260 | planPrintLog | PUR_PLAN_PRINT_LOG | 记录采购计划打印 |
| 272 | createPlanFromSuggestions | PUR_PLAN_FROM_SUGGESTIONS | 缺料预警生成采购计划 |
| 287 | createPlanFromAlerts | PUR_PLAN_FROM_ALERTS | 选中预警生成采购计划 |
| 298 | copy | PUR_ORDER_COPY | 复制采购订单 |
| 308 | export | PUR_ORDER_EXPORT | 导出采购订单Excel |
| 318 | exportDetail | PUR_ORDER_EXPORT_DETAIL | 导出采购订单明细 |
| 328 | exportPdf | PUR_ORDER_EXPORT_PDF | 导出采购订单PDF |
| 346 | deleteOrder | PUR_ORDER_DELETE | 删除采购订单 |

## com/jjx/purchase/controller/PurchasePaymentController.java（采购付款 9）
| 57 | add | PUR_PAYMENT_CREATE | 新增采购付款 |
| 68 | edit | PUR_PAYMENT_EDIT | 修改采购付款 |
| 79 | remove | PUR_PAYMENT_DELETE | 删除采购付款 |
| 99 | approve | PUR_PAYMENT_APPROVE | 付款审核通过 |
| 113 | confirm | PUR_PAYMENT_CONFIRM | 确认付款 |
| 124 | uploadVoucher | PUR_PAYMENT_UPLOAD_VOUCHER | 上传付款凭证 |
| 216 | batchPayment | PUR_PAYMENT_BATCH | 批量付款 |
| 229 | batchApprove | PUR_PAYMENT_BATCH_APPROVE | 批量审核付款 |
| 246 | importPayment | PUR_PAYMENT_IMPORT | 导入采购付款 |

## com/jjx/purchase/controller/PurchaseReceiptController.java（采购收货 8）
| 66 | add | PUR_RECEIPT_CREATE | 新增采购收货单 |
| 81 | edit | PUR_RECEIPT_EDIT | 修改采购收货单 |
| 96 | remove | PUR_RECEIPT_DELETE | 删除采购收货单 |
| 115 | inspect | PUR_RECEIPT_INSPECT | 收货质检 |
| 135 | confirm | PUR_RECEIPT_CONFIRM | 确认收货 |
| 276 | batchReceive | PUR_RECEIPT_BATCH_RECEIVE | 批量收货 |
| 294 | batchInspect | PUR_RECEIPT_BATCH_INSPECT | 批量收货质检 |
| 316 | importReceipt | PUR_RECEIPT_IMPORT | 导入采购收货 |

## com/jjx/purchase/controller/PurchaseSupplierController.java（供应商 6）
| 63 | add | SUPPLIER_CREATE | 新增供应商 |
| 74 | edit | SUPPLIER_EDIT | 修改供应商 |
| 85 | remove | SUPPLIER_DELETE | 删除供应商 |
| 107 | changeStatus | SUPPLIER_STATUS | 更新供应商状态 |
| 122 | updateEvaluation | SUPPLIER_EVALUATION | 更新供应商评估 |
| 194 | importSuppliers | SUPPLIER_IMPORT | 导入供应商 |

## com/jjx/sales/controller/CustomerController.java（客户 7）
| 89 | addCustomer | CUSTOMER_CREATE | 新增客户 |
| 110 | updateCustomer | CUSTOMER_EDIT | 修改客户 |
| 122 | deleteCustomers | CUSTOMER_DELETE | 删除客户 |
| 148 | importCustomers | CUSTOMER_IMPORT | 导入客户 |
| 181 | changeCustomerStatus | CUSTOMER_STATUS | 变更客户状态 |
| 192 | approveCustomers | CUSTOMER_APPROVE | 批量审核客户 |
| 215 | updateCustomerCreditLimit | CUSTOMER_CREDIT_LIMIT | 更新客户信用额度 |

## com/jjx/sales/controller/InquiryController.java（询价 6）
| 69 | add | INQUIRY_CREATE | 新增询价单 |
| 88 | edit | INQUIRY_EDIT | 修改询价单 |
| 99 | remove | INQUIRY_DELETE | 删除询价单 |
| 120 | send | INQUIRY_SEND | 发送询价 |
| 131 | accept | INQUIRY_ACCEPT | 客户确认询价 |
| 142 | reject | INQUIRY_REJECT | 客户拒绝询价 |

## com/jjx/sales/controller/OrderController.java（销售订单 3）
| 114 | deleteOrders | ORDER_DELETE | 删除销售订单 |
| 190 | createOrderInstances | ORDER_CREATE_INSTANCES | 创建产品实例 |
| 201 | updateOrderPayment | ORDER_PAYMENT | 更新订单付款信息 |

## com/jjx/sales/controller/OrderReviewController.java（订单审核 11）
| 37 | submitOrderForReview | ORDER_REVIEW_SUBMIT | 提交订单审核 |
| 52 | startOrderReview | ORDER_REVIEW_START | 开始审核订单 |
| 67 | approveOrder | ORDER_REVIEW_APPROVE | 订单审核通过 |
| 83 | rejectOrder | ORDER_REVIEW_REJECT | 订单审核驳回 |
| 100 | returnOrderForModification | ORDER_REVIEW_RETURN | 退回订单修改 |
| 117 | transferOrderReview | ORDER_REVIEW_TRANSFER | 转交订单审核 |
| 133 | confirmOrderByCustomer | ORDER_REVIEW_CUSTOMER_CONFIRM | 客户确认订单 |
| 149 | cancelOrderReview | ORDER_REVIEW_CANCEL | 取消订单审核 |
| 262 | batchSubmitForReview | ORDER_REVIEW_BATCH_SUBMIT | 批量提交订单审核 |
| 276 | batchApproveOrders | ORDER_REVIEW_BATCH_APPROVE | 批量审核通过订单 |
| 290 | batchRejectOrders | ORDER_REVIEW_BATCH_REJECT | 批量审核驳回订单 |

## com/jjx/sales/controller/OrderStatusController.java（订单状态 10）
| 36 | submitReview | ORDER_STATUS_SUBMIT | 提交审核 |
| 51 | startReview | ORDER_STATUS_START | 开始审核 |
| 66 | approveOrder | ORDER_STATUS_APPROVE | 审核通过 |
| 83 | rejectOrder | ORDER_STATUS_REJECT | 审核驳回 |
| 100 | resubmit | ORDER_STATUS_RESUBMIT | 重新提交审核 |
| 115 | cancelOrder | ORDER_STATUS_CANCEL | 取消订单 |
| 158 | generatePlan | ORDER_STATUS_GENERATE_PLAN | 生成生产计划 |
| 173 | shipOrder | ORDER_STATUS_SHIP | 订单发货 |
| 189 | completeOrder | ORDER_STATUS_COMPLETE | 完成订单 |
| 204 | confirmOrder | ORDER_STATUS_CONFIRM | 客户确认订单 |

## com/jjx/sales/controller/QuotationController.java（报价单 12）
| 76 | add | QUOTATION_CREATE | 新增报价单 |
| 87 | edit | QUOTATION_EDIT | 修改报价单 |
| 100 | remove | QUOTATION_DELETE | 删除报价单 |
| 126 | send | QUOTATION_SEND | 发送报价单 |
| 138 | convert | QUOTATION_CONVERT | 报价转订单 |
| 183 | copy | QUOTATION_COPY | 复制报价单 |
| 194 | submitReview | QUOTATION_SUBMIT | 提交报价审核 |
| 206 | review | QUOTATION_REVIEW | 审核报价单 |
| 232 | changeStatus | QUOTATION_STATUS | 更新报价单状态 |
| 252 | confirm | QUOTATION_CONFIRM | 客户确认报价 |
| 264 | reject | QUOTATION_REJECT | 客户拒绝报价 |
| 276 | modify | QUOTATION_MODIFY | 已完成报价单改单 |

## com/jjx/sales/controller/SalesDeliveryController.java（销售发货 1）
| 54 | receive | DELIVERY_RECEIVE | 签收发货单 |

## com/jjx/sales/controller/SalesInvoiceController.java（销售发票 4）
| 49 | printLog | SALES_INVOICE_PRINT_LOG | 记录发票打印 |
| 55 | create | SALES_INVOICE_CREATE | 新增发票 |
| 63 | update | SALES_INVOICE_EDIT | 修改发票 |
| 75 | delete | SALES_INVOICE_DELETE | 删除发票 |

## com/jjx/sales/controller/SalesReceiptController.java（销售收款 4）
| 49 | printLog | SALES_RECEIPT_PRINT_LOG | 记录收款单打印 |
| 55 | create | SALES_RECEIPT_CREATE | 新增收款单 |
| 63 | update | SALES_RECEIPT_EDIT | 修改收款单 |
| 75 | delete | SALES_RECEIPT_DELETE | 删除收款单 |

## com/jjx/sales/controller/SalesReturnController.java（销售退货 5）
| 48 | create | SALES_RETURN_CREATE | 创建退货单 |
| 57 | approve | SALES_RETURN_APPROVE | 退货审核通过 |
| 70 | reject | SALES_RETURN_REJECT | 退货审核驳回 |
| 82 | receive | SALES_RETURN_RECEIVE | 退货收货确认 |
| 95 | refund | SALES_RETURN_REFUND | 退货退款 |

## com/jjx/sales/controller/SampleOrderController.java（样品单 24）
| 34 | create | SAMPLE_CREATE | 新增样品单 |
| 42 | update | SAMPLE_EDIT | 修改样品单 |
| 51 | createFromQuotation | SAMPLE_FROM_QUOTATION | 从报价单创建样品单 |
| 67 | copy | SAMPLE_COPY | 复制样品单 |
| 114 | submitRequest | SAMPLE_SUBMIT | 提交打样申请 |
| 123 | approve | SAMPLE_APPROVE | 样品单审核通过 |
| 133 | rejectReview | SAMPLE_REJECT | 样品单审核驳回 |
| 143 | startEngineering | SAMPLE_ENG_START | 工程接单 |
| 152 | markReady | SAMPLE_ENG_READY | 工程标记样品完成 |
| 161 | sendSample | SAMPLE_SEND | 销售送样登记 |
| 172 | confirm | SAMPLE_CONFIRM | 客户确认样品OK |
| 183 | rejectSample | SAMPLE_CUSTOMER_REJECT | 客户退回样品 |
| 194 | convertToProduction | SAMPLE_TO_PRODUCTION | 样品转量产 |
| 206 | cancel | SAMPLE_CANCEL | 样品单作废 |
| 218 | restartEngineering | SAMPLE_ENG_RESTART | 退回后重新打样 |
| 229 | acceptEngineering | SAMPLE_ENG_ACCEPT | 工程接单确认 |
| 240 | rejectEngineering | SAMPLE_ENG_REJECT | 工程拒单 |
| 252 | updateProcess | SAMPLE_PROCESS_UPDATE | 更新打样当前工序 |
| 268 | saveProcessPlan | SAMPLE_PROCESS_PLAN | 保存打样工序计划 |
| 281 | updateProcessItemStatus | SAMPLE_PROCESS_ITEM_STATUS | 推进打样工序状态 |
| 322 | saveBom | SAMPLE_BOM_SAVE | 保存打样BOM |
| 344 | recordCost | SAMPLE_COST | 录入打样成本 |
| 358 | transfer | SAMPLE_TRANSFER | 产品资料转移 |
| --(SampleTransferController 49) | confirm | SAMPLE_TRANSFER_CONFIRM | 打样转标准确认 |

## com/jjx/system/controller/system/EventConfigController.java（事件配置 3）
| 76 | add | EVENT_CONFIG_CREATE | 新增事件配置 |
| 88 | edit | EVENT_CONFIG_EDIT | 修改事件配置 |
| 98 | remove | EVENT_CONFIG_DELETE | 删除事件配置 |

## com/jjx/system/controller/system/SysDeptController.java（部门 3）
| 77 | add | DEPT_CREATE | 新增部门 |
| 98 | edit | DEPT_EDIT | 修改部门 |
| 126 | remove | DEPT_DELETE | 删除部门 |

## com/jjx/system/controller/system/SysMenuController.java（菜单 4）
| 71 | add | MENU_CREATE | 新增菜单 |
| 85 | edit | MENU_EDIT | 修改菜单 |
| 99 | remove | MENU_DELETE | 删除菜单 |
| 143 | addAuthRoles | MENU_AUTH_ROLES | 授权菜单角色 |

## com/jjx/system/controller/system/SysRoleController.java（角色 7）
| 66 | add | ROLE_CREATE | 新增角色 |
| 87 | edit | ROLE_EDIT | 修改角色 |
| 111 | dataScope | ROLE_DATA_SCOPE | 修改角色数据权限 |
| 126 | changeStatus | ROLE_STATUS | 变更角色状态 |
| 141 | remove | ROLE_DELETE | 删除角色 |
| 177 | cancelAuthUser | ROLE_CANCEL_USER | 取消用户角色 |
| 190 | cancelAuthUserAll | ROLE_CANCEL_USER_ALL | 批量取消用户角色 |
| 200 | selectAuthUserAll | ROLE_AUTH_USER_ALL | 批量分配用户角色 |
| 220 | selectAuthMenuAll | ROLE_AUTH_MENU_ALL | 保存角色菜单权限 |

## com/jjx/system/controller/system/SysUserController.java（用户 9）
| 141 | add | USER_CREATE | 新增用户 |
| 151 | edit | USER_EDIT | 修改用户 |
| 162 | remove | USER_DELETE | 删除用户 |
| 172 | resetPwd | USER_RESET_PWD | 重置密码 |
| 182 | changeStatus | USER_STATUS | 变更用户状态 |
| 201 | insertAuthRole | USER_AUTH_ROLE | 分配用户角色 |
| 221 | profile | USER_PROFILE | 修改个人资料 |
| 231 | updatePwd | USER_UPDATE_PWD | 修改密码 |
| 241 | avatar | USER_AVATAR | 更换头像 |
