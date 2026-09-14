# 产品档案聚合查看业务流转附件（dev-20260901-086）

- 任务来源：sys_task dev-20260901-086（P2）
- 需求：产品资料页除自身文件库（ProductFileLibrary，productCode 直传）外，聚合展示业务流转附件（询价图纸/报价附件/订单确认书），一个入口看全

## 实证（已核实）

- 关联字段：sales_inquiry.product_id、sales_quotation_item.product_id/product_code、sales_order_product.product_id/product_code
- 附件查询现成：GET /system/attachment/list?bizType=&bizId=（SysAttachmentController:60-65）
- 前端：ProductDetail.vue（productId prop）附件区已有 ProductFileLibrary 组件（views/product/list/components/ProductDetail.vue:286）
- 附件 bizType 实际值需核查上传点（inquiry/quotation/order/sample_order 等，存在历史漂移——以 sys_attachment 现有行与上传点代码为准，写个常量表）

## 改动

### 后端（1 端点）

`ProductController`（com.jjx.product.controller）加：

```java
@Operation(summary = "产品业务流转附件聚合（询价/报价/订单）")
@GetMapping("/{productId}/biz-attachments")
public Result<List<Map<String, Object>>> bizAttachments(@PathVariable Long productId)
```

实现（可放 ProductServiceImpl 或新方法）：
1. 查 product 取 productCode；
2. 反查引用该产品的单据（三张表，product_id 精确 eq；product_code 兜底 like 全等）：
   - sales_inquiry（主表）→ sourceType=inquiry，附件 bizType 用实际值
   - sales_quotation_item（product_id/product_code）→ 需 JOIN sales_quotation 主表？**简化**：只聚合到"报价单"层需要 quotation_id——quotation_item 有 quotation_id 则 join 主表取 quotationNo 与 id；若字段不全，退化为按明细 product_code 反查 quotationId 集合
   - sales_order_product → JOIN sales_order 取 orderNo/orderId
3. 每个命中单据调 attachmentService.getAttachments(bizType, bizId)（或批量一次查再内存分组，性能优先批量）
4. 返回结构：[{ sourceType: 'inquiry'|'quotation'|'order', sourceId, sourceNo, files: SysAttachment[] }]，按单据时间倒序
5. **附件 bizType 常量先代码核查上传点**：询价=inquiry、报价=quotation、订单=order 或 sales_order（以实际为准），写死对应；查不到的来源静默跳过

### 前端（ProductDetail.vue）

- ProductDetail.vue 附件区（ProductFileLibrary 下方或同 Tab）加"业务流转附件"区：
  - productApi 加 bizAttachments(productId) 方法（GET /product/{productId}/biz-attachments）
  - 渲染：按来源分组折叠（询价单 INQ-xxx / 报价单 QT-xxx / 订单 SO-xxx），每组文件列表（下载链接/预览），复用 ProductFileLibrary 的附件展示样式（attachmentApi.downloadUrl）
  - 空态：无业务附件显示空提示
- api 方法加在 jjx-web/src/api/product.ts（或 product 现有 api 文件，Codex 看结构）

### 不做

- 样品单附件聚合（样品单→报价单→产品多一跳，后置）
- 附件"跨单据复制/移动"到产品文件库（只读聚合，不迁移数据）
- 产品文件库与业务附件合并管理

## 风险

- 工作区有用户 WIP，只改本 spec 列出的文件（ProductController/ProductServiceImpl 或新 service 方法、api/product.ts、ProductDetail.vue）
- 不要 git commit；无 migration
- 附件 bizType 漂移：以代码上传点为准，报告里列出实际 bizType 值
- vue-tsc 报错先区分用户 WIP

## 验证

- mvn -o clean test-compile
- npx vue-tsc --noEmit（自己文件零错误）
- 实测建议（Codex 报告，不执行）：给某询价单/报价单/订单传附件后调聚合接口看分组
- 报告剩余问题
