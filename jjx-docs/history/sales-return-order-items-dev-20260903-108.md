# 退货来源订单明细加载断链修复方案（任务 1315 / dev-20260903-108）

- 日期：2026-09-03
- 状态：待拍板（方案 A 推荐）
- 关联：1297（送货单打印同死 API）、1235（退货单据 6b865f1/95a2d96 已实现）、产品≠物料红线

## 一、问题

退货单新增弹窗选订单 → `onOrderChange` 调 `orderProductApi.getListByOrderId(orderId)` → 请求
`GET /sales/orders/product/order/{orderId}` → **后端从来就没有这个路由**（NoResourceFound→500，前端 catch 后提示"明细加载失败，可手动填写汇总"，明细实际带不出来）。

该 API 定义于 `orderProductApi.ts`，自 initial commit 即存在 = **死 API**。同 API 还被送货单打印页（1297）使用，同样坏。

## 二、查证事实（2026-09-03 代码+库实查）

| 项 | 结论 |
|---|---|
| 死 API | `orderProductApi.getListByOrderId` → `/sales/orders/product/order/{id}`，OrderController 无此路由（只有 /{orderId}、/customer/{id}、/quotation/{id} 等）|
| 可用替代 | `GET /sales/orders/{orderId}`（selectOrderById）→ SalesOrderVO **含 items**（List\<SalesOrderProductVO\>）产品级明细 ✅ |
| 订单明细粒度 | sales_order_product = **产品级**（product_id/product_code/product_name/quantity/unit_price/specification，**无 material 字段**）|
| 退货明细粒度 | sales_return_item = **物料级**（material_id/material_code/material_name/material_spec/unit/quantity/unit_price）——入库按物料 |
| 物料现状 | inventory_material：F 成品 2 条**且都有 product_id 专用映射**；R 原料 1535 条无映射 |
| 出库口径参照 | 销售出库（createFromSales）明细即物料级（outbound item.material_id），F+专用产品时同步扣产品库存——**出/退链路本就按物料走** |

## 三、方案对比

### 方案 A（推荐）：后端补退货专用接口 + 物料解析

新增 `GET /sales/orders/{orderId}/return-items`（权限 sales:return:add）：
1. 查订单（存在/可退校验）+ items（产品级）
2. 每行解析专用成品物料：`inventory_material WHERE product_id = 行.product_id AND material_type = 'F' LIMIT 1`
3. 返回合并行：
   ```
   { productId, productCode, productName, specification, unit, unitPrice,
     quantity(订单量), materialId?, materialCode?, materialName?, noMaterial }
   ```
   解析不到 F 物料的行 noMaterial=true
4. 前端：`onOrderChange` 改调此接口；行结构不变，`submitCreate` 现有 material 映射逻辑**零改动**；noMaterial 行禁用并提示"该产品无成品物料，无法退货入库"（红线：产品不入库，无 F 物料即无物可退）

优点：口径收敛在后端（产品→F 物料解析一处），前端只换 URL；退货入库（approve 联动）无需再解析。
缺点：需写 1 个后端接口（含查物料 mapper，半小时量级）。

### 方案 B：改读订单详情 + 入库时再解析

前端换调 `GET /sales/orders/{id}` 拿产品行 → sales_return_item 按产品行落库（物料字段留空）→ 审核通过联动入库时再解析 F 物料。

缺点：sales_return_item 物料字段空着违背现表结构语义；入库处新增解析逻辑（把口径散到两处）；退货单打印/明细展示没有物料信息。**不推荐**。

## 四、待拍板项

1. **方案 A 还是 B**？（推荐 A）
2. **可退数量口径**：v1 建议不做上限（前端手填 returnQty，退货审核环节把关）；严格版 = 已发货 shipped_quantity − 累计已通过退货量（需多查一张表，后置）
3. **无 F 物料行**：禁用提示（推荐）还是放行？

## 五、实施清单（方案 A 拍板后）

- 后端：OrderController（或 SalesReturnController）加 return-items 接口；Service 查订单+明细+物料解析
- 前端：`return/index.vue` onOrderChange 改调 + noMaterial 行禁用提示
- 1297 联动：送货单打印页同死 API → 改读订单详情取 items（打印只需产品级，无需物料）——1297 任务卡执行时一并处理，本任务只修退货页
- migration：无（纯接口代码）
- TC：补"退货单新增选订单加载明细（物料级）"用例

## 六、验证路径

1. 有 F 物料的订单（产品 1 JST001POOO）→ 退货新增选单 → 明细带出含 materialId
2. 无 F 物料产品行 → 禁用提示
3. 提交退货 → approve → 入库按物料（现有 1235 链路）
