# 1262 实施：销售收款单补 update/delete 端点 + 订单付款状态回写联动（dev-20260901-077）

依据：sales-gap-analysis-20260902.md §4；任务描述（补 PUT/DELETE+回写联动，052 口径）。

## 现状（已核实）

- SalesReceiptController：page/get/print-log/create/export —— 无 PUT/DELETE
- SalesReceiptService/Impl：只有 create（含回写 updateOrderPaymentStatus(orderId)）——
  无 update/delete 方法（与发票不同，需新建）
- 回写口径（052，SalesReceiptServiceImpl.create/updateOrderPaymentStatus）：
  收款单 insert 后若 orderId 非空且 status != 0(作废) → 重算该订单付款状态：
  汇总该订单 status=1(正常) 收款单 actual_amount（null 兜底 receipt_amount）为 paid；
  target = final_amount → total_amount_with_tax → total_amount 依次 fallback；
  paid<=0→UNPAID(1)、paid>=target→PAID(3)、否则 PARTIAL_PAID(4)；
  写 sales_order.payment_status/paid_amount/unpaid_amount（部分更新对象）
- 收款单行状态：int 0=作废/1=正常（impl 私有常量 VOID/NORMAL），无状态枚举
- 收款单无 @Log bizStatus（create 同款潜在 500 雷，见 1261 spec 说明）
- 前端 views/sales/receipt/index.vue 有"新增收款"入口，无编辑/删除按钮 → UI 缺口另议，
  本次后端范围

## 改动点（后端）

### 1. 新建 SalesReceiptStatusEnum（照 1261 SalesInvoiceStatusEnum 模板）
com.jjx.sales.enums.SalesReceiptStatusEnum implements BizStatusEnum：
NORMAL(1,"正常")/VOID(0,"作废")，含 getByValue(Integer)。

### 2. SalesReceiptService + Impl 新增两个方法
- `boolean update(SalesReceipt receipt)`：
  a) 先查旧行（拿旧 orderId/旧 status）；旧行不存在 → 返回 false
  b) 兜底：receipt.actualAmount==null → 用 receiptAmount（同 create 的 DEV-934 兜底）；
     receipt.status==null → 用旧行 status（保证 @Log 的 SpEL 可取到 label）
  c) updateById
  d) 回写联动：新 orderId 非空且新 status != 0 → updateOrderPaymentStatus(新 orderId)；
     若旧 orderId 非空且 != 新 orderId → updateOrderPaymentStatus(旧 orderId)（旧单要重算扣除）
- `boolean delete(Long id)`：
  a) 查旧行（拿 orderId）；不存在 → 返回 false
  b) deleteById
  c) 旧 orderId 非空 → updateOrderPaymentStatus(旧 orderId)（删除后重算，收款额减少）
  （两方法 @Transactional，照 create）
- updateOrderPaymentStatus 已是私有方法，直接复用；不改其逻辑

### 3. SalesReceiptController 补两个端点
- `@PutMapping("/{receiptId}")`：入参 @PathVariable Long receiptId + @RequestBody
  SalesReceipt receipt（controller 内 receipt.setReceiptId(receiptId)）→ service.update 失败
  Result.error；权限 sales:order:edit
- `@DeleteMapping("/{receiptId}")`：权限 sales:order:delete（若与 create 权限码一致可统一
  sales:order:edit，以仓库其他 DELETE 的权限码惯例为准，报告里说明）
- @Log：
  - PUT：module 销售收款 / UPDATE / bizType "'receipt'" / bizId "#receipt.receiptId" /
    bizStatus "T(com.jjx.sales.enums.SalesReceiptStatusEnum).getByValue(#receipt.status)?.label"
    （service.update 保证 status 非空后求值才不落空）
  - DELETE：module 销售收款 / DELETE（无 bizType/bizStatus —— 行已删无状态可快照，
    按无状态机接口约定；避免 e33d5f2 成功即 500 的雷）

## 明确不做

- ❌ 前端收款页编辑/删除 UI（另行登记）
- ❌ 作废(改 status=0)专用流程设计（本次硬删+更正通道；作废语义可后续单独任务）
- ❌ 改 updateOrderPaymentStatus 既有逻辑、改 create、新建 VO/迁移/表
- ❌ 触碰工作区其他在改文件（并行会话施工：biz/requirement、SalesQuotationAddDTO、
  BoardTaskController、OperationPreviewDialog、WebConfig、SysUserDTO 等一律不碰）
- ❌ git commit

## 验证

1. mvn -o clean compile（主代码）
2. 手测清单（交付说明）：
   - 造订单+收款单（status=1）：改金额→订单 paid/unpaid/payment_status 重算正确
   - 收款单改挂另一订单 → 旧单重算扣除、新单重算累加
   - 删除收款单 → 订单回写金额减少、状态正确回落
   - sys_oper_log：UPDATE 行 biz_status=正常 落库不 500；DELETE 行有 module/businessType
   - 作废单(status=0)不参与任何回写（照 create 既有口径）
3. 已知遗留：前端无编辑/删除入口（报告点名，建议另登记 UI 任务）

## 关联
- sys_task 1262 dev-20260901-077 P1 待开始 → 实施中 → 待审核
