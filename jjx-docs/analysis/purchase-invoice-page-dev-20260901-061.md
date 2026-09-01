# 采购发票管理前端页面（dev-20260901-061）

- 任务来源：sys_task dev-20260901-061（P1）
- 根因：PurchaseInvoiceController 后端 40+ 端点就绪，api/purchase/invoice.ts 已封装，但 views/purchase 无发票页面，菜单 165（采购发票，purchase:invoice:view）component 为 NULL。发票管理无法操作。

## 现状核查结论（2026-09-01）

1. 菜单 165 采购发票已存在（含 add/edit/delete/export/import 全套 F 权限点），只缺 route_name+component——同 051 的 menu 218 模式。
2. 发票实体 = PurchaseDocument（document_type='invoice' 区分），无独立实体。
3. DocumentStatus：0 待处理 / 1 已核验 / 2 已归档（前端 InvoiceStatusEnum 是历史遗留字符串 pending/issued/verified/cancelled，**未对齐，需改**）。
4. 发现 2 个后端问题（本次一并修）：
   a. `PurchaseInvoiceController.page()` return Result.success(null)——查询实现了（selectDocumentList）但不返回，列表无法用。
   b. `batchConfirmDocuments` 硬编码 documentType="receipt"、单号 RCP——发票批量确认会建成收货单；且 page() 未过滤 documentType，发票列表会混入收货单。
5. `exportDocumentList` 是 TODO 空壳（不生成文件）——061 不做导出按钮。
6. api/purchase/invoice.ts 缺 uploadTemp/diskFiles/batchConfirm/deleteTempFile 封装（需补）。

## 后端改动（5 处）

1. `PurchaseDocumentDTO` 加 `private Integer pageNum = 1; private Integer pageSize = 10;`（照 SalesDeliveryQueryDTO 风格）。
2. `PurchaseInvoiceController.page()`：开头 `dto.setDocumentType("invoice")`；调 selectDocumentList 后手动分页：
   `List<PurchaseDocument> list = documentService.selectDocumentList(dto);` → subList 切页 → `Result.success(PageResult.build(records, list.size()))`。
3. `IPurchaseDocumentService.batchConfirmDocuments` 签名加 `String documentType`；`PurchaseDocumentServiceImpl` 实现：`setDocumentType(documentType)` + `generateDocumentNo(documentType)`（替代硬编码 "receipt"）。
4. `PurchaseInvoiceController.batchConfirm` 调用处传 `"invoice"`。
5. 其他调用方核查：batchConfirmDocuments 全库仅发票控制器 1 处调用，无其他影响。

## 前端改动（3 处）

1. `api/purchase/invoice.ts` 补 4 个函数：uploadTemp(orderId, file) POST FormData、diskFiles(orderId)、batchConfirm(orderId, supplierId, files) POST JSON、deleteTempFile(fileUrl) DELETE。检查是否已有未导出封装，避免重复。
2. `enums/purchase/invoice.ts`：InvoiceStatusEnum 对齐后端（createEnum 数字值）：0 待处理(warning) / 1 已核验(success) / 2 已归档(info)。InvoiceTypeEnum 保留不动（当前无对应数据字段，页面不使用）。
3. 新建 `views/purchase/invoice/index.vue`：
   - 筛选：发票号（documentNo like）、供应商（下拉，supplierApi.list）、状态（0/1/2）、开票日期范围（documentDate）
   - 列表列：发票号 / 供应商 / 金额 / 币种 / 开票日期 / 状态 tag / 核销日期 / 操作（详情、核销、删除）
   - 新增弹窗（批量开票场景）：选采购订单（getPendingInvoiceOrders 下拉，选中带出 supplierId）→ el-upload 多文件（uploadTemp，自动上传）→ 文件列表（可删 temp-file）→ 提交 batchConfirm(orderId, supplierId, files)
   - 核销弹窗：核销日期（默认今天）/ 核销人 / 备注 → verifyInvoice
   - 详情抽屉：字段 + 附件（有 fileName 显示下载按钮 → downloadInvoiceFile）
   - 删除：多选 delInvoice；权限：view 看列表、add 新增、edit 核销、delete 删除（v-hasPermi）
   - **不做**：导出按钮（后端 TODO 空壳）、统计卡片、导入、报表页

## 菜单（migration 27）

```sql
UPDATE sys_menu SET route_name = 'PurchaseInvoice', component = 'views/purchase/invoice/index.vue'
WHERE menu_id = 165 AND component IS NULL;
```

## 验证

- mvn -o clean compile（后端）
- vue-tsc --noEmit（前端，过滤用户 WIP）
- npm run check:status-enums（枚举改动门禁）
- migration 27 应用两遍（幂等）+ check-menu-integrity.sh

## 明确不做

- 导出按钮（exportDocumentList TODO 空壳，报告待办）
- 导入/批量校验（import/batch-check，后续）
- 统计面板（today/week/month/statistics 等 10+ 统计端点，后续）
- 附件预览页（preview 端点保留，页面用 download）
