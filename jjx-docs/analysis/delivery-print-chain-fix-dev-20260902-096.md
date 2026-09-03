# 送货单打印数据链修复（任务 1297 / dev-20260902-096）

2026-09-03 核查定案：print 壳子与模板齐备，但明细数据源断链 → 打印表格恒「无订单明细」。
用户批准按建议修复（断链改源 + 模板缺列补齐）。

## 根因（证据）
- 明细来源 orderProductApi.getListByOrderId → GET /sales/orders/product/order/{id}
  → 后端无此映射（实测 NoResourceFoundException → 500「系统繁忙」）；
  git 全史搜 'orders/product' 后端零命中 → api/sales/orderProduct.ts 自 initial commit 起为死 API。
- 替代路径实测可用：GET /sales/orders/{orderId}（orderApi.getOrder, api/sales/order.ts:31）
  内嵌 items[]（id/quantity/amount/productId/unit/unitPrice/productCode/productName/
  specification/remark/lineRemark），并含 orderNo。

## 改动清单（仅前端 2 文件，后端零改动）
1. views/sales/delivery/print.vue
   - 明细源换 orderApi.getOrder(orderId)，取 res.data.items（orderNo 一并取，行内不再各查）；
     保留 deliveryApi.getById 拿抬头；加 try/catch，明细失败表格显示空态提示不阻断打印。
   - 明细表按模板 送货单.xlsx 补列，最终 9 列：
     序号(=index+1) / 品名(料号)(productName||productCode) / 规格(specification)
     / 单位(unit) / 数量(quantity) / 单价(unitPrice) / 金额(amount) / 订单号码(orderNo)
     / 备注(remark||lineRemark)
   - 移除 orderProductApi import。
2. views/sales/delivery/index.vue:105 showDetail
   - orderProductApi.getListByOrderId(row.orderId) → orderApi.getOrder(row.orderId) 取 items；
     移除 orderProductApi import（若文件无其他引用）。

## 明确不做
- return/index.vue:249（同断链但目标字段是物料级 materialId/materialCode/materialName，
  而订单明细是产品级数据 → 不能照搬换源，修法待定）→ 已另登记 follow-up。
- 模板「送货单位经手人」行与「10 天内异议」条款：纸版复刻未选（用户按建议走，本轮不加）。
- 不新增后端接口、不改 orderProductApi.ts 本身、不动 1233 的其余待审核内容。

## 验证
1. codex 自报 + 我复核：npx vue-tsc --noEmit 仅看这两个文件无 error TS（其他文件并行 WIP 不算）。
2. 运行态（后端已跑）：打开 /sales/delivery/print?deliveryId=1（DL260903001，order 1 有 1 行
   明细 JST001POOO×10 PCS）→ 表格应出 1 行、9 列、合计 1000.00；发货单列表详情抽屉同验。
3. 用户打印验收后 1297 → 10。
