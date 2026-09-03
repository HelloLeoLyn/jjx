# 销售退货单产品化改造方案（1235 设计修正，承接 1315）

- 日期：2026-09-03
- 状态：**已实施**（2026-09-03，migration 50 + 后端 + 前端，编译通过；待用户重启验证）
- 定位：修正 9-1 退货实现（6b865f1/95a2d96）的模型错误——销售单据被建成库存单据维度

## 一、根因（一句话）

9-1 做退货（1235）时，为让"审核收货→联动库存入库"实现简单，把退货明细表 `sales_return_item`
直接建成了**物料级**（material_id/code/name）——照库存入库单的模板建了销售单据。
退货单本应与订单同侧（**产品维度**，客户退的是产品），入库单才与库存同侧（物料维度）。

## 二、目标模型（红线对齐）

- 销售域单据全链路**产品维度**：销售订单 → 发货单 → **退货单** → 红字/退款（都是产品语言）
- 库存域单据**物料维度**：出入库单（inventory_inbound/outbound 保持物料级不动）
- 唯一衔接点：退货收货 → 自动入库时做一次 **产品→F 成品物料解析**（口径只在库存动作一处）
- 红线不变：产品不入物料库、inventory_stock 只有物料；产品看库存 = 经 F 物料间接看

## 三、现状影响面（已实证，规模很小）

| 项 | 现状 |
|---|---|
| 数据 | sales_return / sales_return_item **均 0 行**（dev 清洗，从未真实使用）|
| 表 | sales_return_item：material_id/code/name/spec/unit/quantity/unit_price/amount |
| 后端 | 退货模块自包含 11 文件（Service 单文件 300 行：create/approve/reject/receive/refund + 入库联动）；Controller 3 端点（page/{id}/{id}/items）|
| 前端 | 仅 1 页 views/sales/return/index.vue（363 行：列表+新建+审核+收货+详情）|
| 库存侧耦合 | 仅 receive() 内调 inboundService.create/confirm（sourceType=sales_return）一处 |
| 其它 | 无事件/通知/菜单/打印关联；1235 任务卡尚未验收 |

## 四、改造清单

### 1. 表结构（migration 50）
sales_return_item：
- 新增：`product_id bigint NULL`、`product_code varchar(64)`、`product_name varchar(200)`
- 删除（0 行数据，干净）：material_id / material_code / material_name / material_spec
- 保留：unit / quantity / unit_price / amount / remark
- 语义：退货行 = 订单产品行快照（产品编码/名称/规格留档，防订单后续改动）

### 2. 后端
- `SalesReturnItem` 实体：material 字段 → product 字段
- `SalesReturnServiceImpl.create`：items 解析改为 productId/productCode/productName/specification（来自前端订单产品行）；数量/单价逻辑不变
- `SalesReturnServiceImpl.receive`（核心改动）：联动入库段改为——
  1. 查退货产品行
  2. 每行解析专用 F 物料：`inventory_material WHERE product_id = 行.product_id AND material_type = 'F' LIMIT 1`
  3. 解析成功 → 组 inbound item（materialId/code/name/spec/unit/qty/price）
  4. 解析失败（该产品无 F 物料）→ 整单报错并提示行（"产品 X 无成品物料，无法自动入库"），退货状态回滚（事务）
- 口径收敛点：新增私有方法 `resolveFMaterial(productId)` 或独立 Mapper 查询——全系统仅此一处产品→物料翻译
- Controller 不变（page/{id}/{id}/items 端点照旧，返回行内容变产品）

### 3. 前端（return/index.vue）
- 新建弹窗：选订单 → 调**订单详情** `GET /sales/orders/{orderId}` 取 items（产品行，替代死 API getListByOrderId——1315 根因同消）→ 表格列改：产品编码/产品名称/规格/单价/可退数量
- 提交 items：productId/productCode/productName/specification/quantity/unitPrice
- 详情抽屉：明细列改产品
- 1315 原方案（return-items 物料解析接口）**作废**，不再需要

### 4. 1315/1297 收尾
- 1315：随本改造一起消解（退货页不再需要任何物料解析接口）→ 完成后置 2 并备注"由退货产品化改造吸收"
- 1297（送货单打印死 API）：单独修（改读订单详情），与退货无耦合，可在本任务顺带或留给 1297 自己——建议本任务顺带把 return 页改完时一并确认送货单 print 页（同款一行改动）

## 五、边界与风险
- 无历史数据迁移（0 行）
- 事务性：receive 里入库联动失败 → 退货单状态回滚（现有逻辑已 throw 回滚）——保持
- F 物料缺失产品（product 2 当前 F 物料 product_id=2 存在，实际不缺；将来新品无 F 物料时退货会提示，属正确行为）
- 产品规格：订单明细 specification 字段直接带出，无需物料 spec

## 六、验证与 TC
1. 建标准订单（产品 1）→ 审核/发货（有 F 物料）→ 退货新增选单 → 明细=产品行
2. 提交 → 审核 → 收货 → 自动入库成功，inventory_inbound sourceType=sales_return，明细物料=产品对应 F 物料，库存加回
3. 详情/打印显示产品
4. TC：更新退货 TC（现有 TC 若引用 material 列则同步改），新增"退货自动入库产品→F物料解析"用例
5. 红线复核：sales_return_item 无 material 列；inventory_stock 仍只有物料行

## 七、复杂度评估
- 后端：create/receive 两段 + 实体/表 + 解析方法 ≈ 2-3h
- 前端：新建/详情列 + 订单详情替换死 API ≈ 1-2h
- 验证 + TC ≈ 1h
- **合计约半天**；趁 1235 未验收、0 数据，成本最低
