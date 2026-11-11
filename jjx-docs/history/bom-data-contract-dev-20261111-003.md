# 多层BOM-A：数据模型与接口契约

任务：dev-20261111-003（总任务 dev-20261111-002 子任务 A）。执行者：OpenClaw。验收者：Codex（独立验收）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：A 阶段交付，只读核查与契约设计；没有实施 B 至 G，没有写业务源码、没有执行迁移、没有新增表、没有改库、没有启动或重启服务。
本卡写入白名单：jjx-docs/history/bom-data-contract-dev-20261111-003.md、jjx-docs/history/INDEX.md、.tmp/bom-dispatch/（本任务证据，gitignored）；其余业务源码、迁移、库和运行态只读。

## 交付边界与结论先行

本文件交付四类内容：一是实际 DDL 与现有入口的现场核查；二是优先复用表的数据模型与字段约束、迁移兼容和回退设计；三是方案保存、采用、父保存边界与接口契约；四是 B 至 G 的精确文件白名单、共享作者、入口保存矩阵和 001 的 37 场景归属。

结论先行：

1）现有 BOM 主表不能直接承载“MATERIAL 产出”。engineering_bom.product_id 为 NOT NULL，生产订单 production_order.product_id 也为 NOT NULL，库存成品入库固定走 PRODUCT 身份；必须增加显式产出身份，不能继续用 product_id 冒充所有半成品。
2）现有 BOM 明细的 parent_material_id 只是同一 BOM 内的分组/父子行，不能复用为“由哪个子 BOM 制造”的关系。跨 BOM 关系必须新增显式引用 sub_bom_id，并做图环路校验。
3）现有精度链路会把 1/6 先存成 0.1667，再乘 300 得 50.01，整批向上取整成 51；正确结果应为 300×1÷6=50。新契约要求保存模数、基数原始值，除法只在需求计算末次执行。
4）现有库存物品身份与物料主数据存在实际错位：7 个 BOM 引用物料全部在 inventory_item 中按 source_id 命中“别的物料”，而库存与流水当前均为 0 行。零库存使历史未被污染，但 F 阶段直接按 material_id 调 ensure(MATERIAL, material_id) 会拿错身份，必须先完成身份核对/映射，不能把旧 inventory_item 当新库使用。
5）多方案必须按“输出对象 + 方案 BOM”建立，产品主方案单独按 product_id × material_id 保存；不能把半成品档案上的某个全局 is_current 当成所有产品的主方案。
6）新表只提出一项：product_material_scheme（产品主方案）。该表尚未批准、未创建；替代方案和否决理由见“新表提案与替代评估”。其余需求分配优先扩展现有 order_material_reserve，不另建第二套库存/预留表。
7）B 至 G 的文件白名单、共享作者和 37 场景归属已给出；共享文件一律先 B 后 D、先 C 后 G、先 D 后 E、先 D 后 F，禁止并行抢写。

## 现场核查环境与基线

执行前已把 git 状态存档到 .tmp/bom-dispatch/git-status-baseline-20261111.txt，基线为：HEAD 5a329faf42da8c86d2dfc9909e0dee307a40d4b3，分支 dev，工作区干净。

数据库只读核查使用本机 jjx_ro，库 jjx_erp_db@127.0.0.1:3306；MySQL 版本 8.4.10-0ubuntu0.26.04.1；sql_mode 含 ONLY_FULL_GROUP_BY、STRICT_TRANS_TABLES、NO_ZERO_DATE 等严格模式。迁移账本 ops.schema.applied 最大号 355，jjx-docs/sql/migrations 目录最大号 355，待执行 0、已记账待归档 0；下一次迁移编号在实际执行时取“账本最大号与目录最大号较大者 + 1”，当前为 356。

本文件中的“现有”均指上述 HEAD 与本次只读查询结果；“建议/提案/契约”不是已实现能力，也不是已批准的新表。

### 现场数据规模

1）engineering_bom：4 行。bom_id=1、2 的 product_id=1；bom_id=3 的 product_id=3（测试中间件产品身份）；bom_id=4 的 product_id=2（测试成品）。bom_id=1 为已批准且 is_current=1；bom_id=2 为草稿且 is_current=0，其明细仍为草稿行；bom_id=3、4 为已批准但 is_current=0。
2）engineering_bom_item：12 行。item_id=1、5、9、10 的 module_qty/base_qty/quantity 分别为 6/1/0.1667、3/1/0.3333、6/1/0.1667、3/1/0.3333；item_id=13 的 source_type=make、material_id=349，但没有 material_code，也没有任何字段指向 bom_id=3。
3）inventory_material：291 行；inventory_item：1562 行；inventory_stock、inventory_stock_item、inventory_transaction 均为 0 行；inventory_inbound_item、inventory_outbound_item 均为 0 行；production_order 2 行；purchase_order_item 0 行。
4）当前没有真实库存和库存流水，因此本次发现的物料身份错位尚未形成历史流水污染；但这也意味着不能把“零库存”当成“身份没问题”。

### 实际 DDL 摘要

以下仅摘录与本契约相关的字段，不替代 SHOW CREATE TABLE 原文；完整原文可在 .tmp/bom-dispatch/ 的核查记录中复核。

engineering_bom（主表）：

| 字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| bom_id | bigint PK auto_increment | 稳定 BOM 身份 |
| bom_code | varchar(50) NOT NULL | 与 bom_version 组成 UNIQUE uk_bom_code_version |
| product_id | bigint NOT NULL，idx_product_id | 现状只能表达产品产出；无法承载 MATERIAL 产出 |
| bom_version / version | varchar(20) NOT NULL / varchar(20) default V1.0 | 双字段并存，现有代码写入时同步；新契约继续双写同步 |
| bom_type | varchar(20) default 'manufacturing' | 字符串 engineering/manufacturing；Java 另有 BomType(1/2) 枚举，二者不一致，不能拿它当产出类型 |
| is_current | tinyint(1) default 1 | 同一 product_id 的当前版本；无数据库级“每输出唯一当前”约束 |
| approve_status | bigint NOT NULL default 1 | 1 草稿/2 审核中/3 已批准/4 已驳回；生产取数只认 3 |
| effective_date / expiry_date | date NULL | 已存但 ProductionBomResolver 当前不校验生效窗口 |
| parent_bom_id | bigint NULL | 同编码父版本链，不是跨 BOM 制造关系 |
| create_by/create_time/update_by/update_time/remark | 常规审计列 | 可用于并发复核，但没有 revision 列 |

engineering_bom_item（明细）：

| 字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| item_id | bigint PK auto_increment | 稳定行 ID；跨 BOM 引用不得复用为 sub_bom_id |
| bom_id | bigint NOT NULL，idx_bom_id | 无外键；服务层校验归属 |
| parent_material_id | bigint NULL | 指向本表 item_id，只做同 BOM 分组/父子行；不能当制造关系 |
| material_id | bigint NULL，idx_material_id | 关联 inventory_material.material_id；快速模式可空 |
| material_code/material_name | varchar NULL / NOT NULL | 冗余展示；批准时要求 material_id 有效 |
| quantity | decimal(12,4) NOT NULL | 现有“单位用量”派生值，按 base/module 四舍五入存 4 位；新契约只作展示快照 |
| applied_qty | decimal(14,4) NULL | 含损耗应用料快照；当前按 double 计算 |
| actual_issue_qty | decimal(14,4) NULL | 单位应用料快照；领料 batchDemand 以它乘工单数量 |
| module_qty / base_qty | decimal(12,4) NULL | 当前审核要求 ≥1；新契约要求 >0，允许正小数 |
| loss_rate | decimal(5,2) default 0.00 | DB 支持 2 位小数，但 Java/DTO 为 Integer，当前只能整百分比 |
| min_issue_qty | decimal(12,4) NULL | 最低投料；只在领料批次生效，不应冒充净需求 |
| unit | varchar(20) default PCS | 自由文本，不与 material.unit 做一致性校验 |
| source_type | varchar(20) default 'buy' | 字符串 buy/make；现有领料对非 buy 行直接 continue |
| substitute_json | json NULL | 替代料数组；替代比例/损耗在领料预览中处理 |
| process_id/process_name | bigint/varchar NULL | 标准工序引用；可作 MATERIAL 工序来源的候选，但当前只用于项目展示 |
| review_status | tinyint default 1 | 1 草稿/2 待审核/3 通过/4 驳回/5 待移除 |
| approved_fingerprint | ascii SHA-256 | 行级审核指纹 |
| width_mm/length_mm | decimal(10,2) NULL | 行级规格快照，不是单位换算依据 |

inventory_material（物料档案）：

| 字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| material_id | bigint PK | BOM 投入/产出物料身份 |
| product_id | bigint NULL | 现有代码未使用；可作为“半成品产品执行身份”的桥接候选，但本次不作为主方案 |
| material_code | varchar(50) NOT NULL UNIQUE | 业务物料编码（RM/AUX/SFG） |
| material_name/specification | varchar(200)/varchar(500) | 展示与匹配 |
| unit / unit_alt / unit_conv | varchar(20)/varchar(20)/decimal(10,4) | 只有单标量换算，无法表达张→㎡、卷→㎡、按卷实长等换算 |
| width_mm/length_mm/thickness_mm | decimal | 静态尺寸；卷料实际长度不在档案 |
| material_form | varchar(20) NULL | 形态字段存在，但当前无统一枚举使用说明 |
| safe_stock/reorder_point/lead_time | decimal/int | 需求与采购建议的现有输入 |
| status | tinyint default 1 | 1 启用；需求计算只取启用物料 |

inventory_item / inventory_stock / inventory_stock_item / inventory_transaction：

| 表 | 关键字段 | 契约影响 |
|---|---|---|
| inventory_item | inventory_item_id PK；item_type MATERIAL/PRODUCT；source_id；UNIQUE(item_type, source_id)、UNIQUE(item_code) | 库存身份唯一入口；但本次核查发现 MATERIAL 的 source_id 与 inventory_material.material_id 实际错位 |
| inventory_stock | inventory_item_id UNIQUE；material_id；total_quantity；total_reserved；available_quantity 生成列 | 汇总余额；查询同时可按 material_id 与 inventory_item_id |
| inventory_stock_item | item_id PK；inventory_item_id；material_id；warehouse_id/location_id/batch_no；quantity；reserved_quantity | 批次余额；FIFO 查询主要按 material_id |
| inventory_transaction | transaction_id PK；inventory_item_id；material_id；transaction_type；source_type/source_id；quantity/before_quantity/after_quantity；lot_id/batch_no | append-only 流水；applyDelta 唯一写入口 |

production_order（生产订单）：

| 字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| order_id | bigint PK | 工单身份 |
| order_type | varchar(20) NOT NULL | PLAN/WORK_ORDER |
| parent_order_id | bigint NULL，FK 自表 | 计划→工单、上层工单→补做工单的父链 |
| product_id | bigint NOT NULL | 只能表达产品产出；MATERIAL 产出需要新增显式字段 |
| product_code/product_name/product_spec/product_unit | 冗余展示 | 可在 MATERIAL 工单中承载物料编码/名称/单位，但不能替代身份字段 |
| bom_id/bom_code | bigint/varchar NULL | 工单创建时快照；ProductionBomResolver 优先按它取数 |
| routing_id/routing_code | bigint/varchar NULL | 工艺路线；MATERIAL 产出没有物料级路线身份 |
| planned_quantity/completed_quantity/finished_quantity/remaining_quantity | decimal(18,4) | 计划/完工/剩余；供给计算使用 remaining_quantity |
| order_status | tinyint default 0 | 0 草稿/1 待审核/2 已审核/4 已计划/5 待开始/6 进行中/8 已完成/9 已取消/10 已关闭/11 已超期 |
| material_status | tinyint default 0 | 0 未领料/1 待发料或部分/2 已领料 |
| work_spec_version_id | bigint NULL | 作业规范发布版本；产品域绑定，MATERIAL 无对应键 |
| trace_id | varchar(64) | 上下游链路；工单→领料→入库继承 |

production_task / production_work_report / production_operation_execution：

| 表 | 关键字段 | 契约影响 |
|---|---|---|
| production_operation_execution | execution_id、order_id、process_id/process_name、process_order、planned/actual time、input/output/qualified/defective quantity、execution_status、is_final_process | 工序执行事实；MATERIAL 工单需要工序来源 |
| production_task | task_id、task_no、execution_id、parent_task_id、task_quantity、status、task_type | 工序任务树；与产出身份无关，按 order_id/execution_id 关联 |
| production_work_report | report_id、order_id、execution_id、task_id、qualified/defective quantity、report_status | 报工；完工入库按质检合格数量，不按报工理论数 |

供需/预留/采购现有表：

| 表 | 关键字段 | 可复用点 |
|---|---|---|
| order_material_reserve | id、order_id、material_id、reserve_quantity、status、reserve_days、expire_time、release_* | 已有订单级物料预占/占用；建议扩展供需分配字段，不另建第二套预留表 |
| sales_order_stock_reserve | order_id、inventory_item_id、product_id、material_id、reserve_quantity、status、reserve_type | 销售成品预留；不是半成品制造供需 |
| purchase_order_item | order_id、material_id、quantity、received_quantity、receipt_status、expected_delivery_date | 在途采购供给；现有 InventoryAlertServiceImpl 已按物料/到货日聚合 |
| inventory_alert_log | alert_type、material_id、order_no、current_stock、safe_stock、suggestion、status | 缺料/安全库存预警；可作为需求计划结果展示的现有载体 |

### 现场核查发现的关键缺陷

以下不是推断，均有只读查询或源码行号对应；它们决定 B 至 F 的实现边界。

1）精度缺陷已可复现。engineering_bom_item.item_id=1 的 module_qty=6、base_qty=1、quantity=0.1667、actual_issue_qty=0.1667；按现有 batchDemand，300×0.1667=50.01，向上取整为 51；按原始比例 300×1÷6=50。源码位置：EngineeringBomServiceImpl.calculatedUnitQuantity 1299 行把除法四舍五入到 4 位；InventoryOutboundServiceImpl.batchDemand 1449 行用 actual_issue_qty 乘数量后 setScale(0, UP)。
2）模数/基数下限仍是 ≥1。EngineeringBomServiceImpl.checkBom 1443/1446 行明确写“模数不能为空且须 ≥1”“基数不能为空且须 ≥1”，与用户“模数、基数均允许大于 0 的正小数”冲突；当前前端和数据库也按 4 位存储，尚未支持小于 1 的合法值。
3）损耗率类型不一致。DB 为 decimal(5,2)，Java 实体和 DTO 为 Integer（EngineeringBomItem.lossRate、EngineeringBomItemDTO.lossRate），计算用 double：loss/100.0。新契约必须改 BigDecimal，并只在本层计一次。
4）单位没有一致性校验。engineering_bom_item.unit 是自由文本；BOM 1 的 item_id=1 用 PCS，而 material_id=109 档案单位是 M；BOM 2 的 item_id=11 用 M。现有保存/审核不校验 BOM 单位与 material.unit，也没有可靠的面积/卷长换算依据；不能把 1 PCS 当 1 M 或 1 ㎡。
5）make 行没有下层引用且被领料直接跳过。InventoryOutboundServiceImpl 667、967、994、1239、1253、1403 行均有 if (!"buy".equals(sourceType)) continue；item_id=13 为 make、material_id=349，但无 sub_bom 关联，bom_id=3 也没有任何行指向它。新契约必须把“make 但无显式下层引用”作为可定位错误，不能静默当采购或静默漏算。
6）产出身份只支持 PRODUCT。engineering_bom.product_id NOT NULL；ProductionOrderCreateDTO.productId @NotNull；ProductionOrderServiceImpl.validateOrderData 872 行只校验 productId；InventoryInboundServiceImpl.createFromProduction 3398 行固定 ensure(PRODUCT, prodOrder.getProductId())，3692 行同样固定 PRODUCT。MATERIAL 产出的工单和入库没有闭环字段。
7）物料身份错位是实际存在的。对 7 个 BOM 引用 material_id 逐一和 inventory_item(MATERIAL, source_id) 比对，7 个全部 MISMATCH；1562 个 inventory_item 中 1271 个没有对应 inventory_material 行；按 item_code 也无法与 inventory_material.material_code 匹配（291 个物料 0 个命中，1562 个物品 0 个命中）。示例：material 109=RM000105 PC-800，但 inventory_item 109=MTR202608130110 3.0mm透明PC(贯昌）。当前 stock/transaction 均为 0 行，所以错误尚未落成历史流水；但 F 阶段若直接 ensure(MATERIAL, material_id)，会返回错误的库存物品。
8）领料“已领量”统计键不一致。InventoryOutboundServiceImpl 999、1405 行用 pickedMap.getOrDefault(bomItem.getItemId())，而 sumPickedByMaterial 1463/1474 行按 material_id 聚合。除非 item_id 恰好等于 material_id，否则已领量被当作 0，剩余需求会重复计算。该缺陷应在 D 阶段一并修正，并以真实数据回归。
9）BOM 类型存在双口径。DB bom_type 存 'manufacturing' 字符串，Java ProductEnums.BomType 是 1/2 枚举，现有代码未统一；新增产出类型不得复用 bom_type，应单独定义 output_type。
10）生效窗口未参与生产取数。engineering_bom.effective_date/expiry_date 已存在，但 ProductionBomResolver 只校验 bom_id、product_id 和 approve_status=3，不校验生效/失效日期；新契约要明确“何时校验”和“历史工单不重算”。
11）上层领料只取 buy 行，make 行即使已有半成品库存也不会被领用。InventoryOutboundServiceImpl 667/967/994/1239/1253/1403 行均对非 buy 行 continue；这与 001 的 P01“成品工单领原有140片与新产出合格片”冲突。新契约必须允许 make 行按可用库存/已有生产供给领用，但不得在领料时重复生成下级工单。

### 现有入口与调用链

前端 BOM 主入口：

| 入口 | 当前文件 | 现状 |
|---|---|---|
| BOM 管理页新增/编辑整单 | jjx-web/src/views/product/bom/components/BomFormDialog.vue；jjx-web/src/views/product/bom/index.vue | doSave 在 670 行；编辑走 PUT /engineering/bom（697 行），新增走 POST /engineering/bom（700 行）；整单保存，材料行由 BomItemEditor 提供 |
| BOM 材料行编辑 | jjx-web/src/components/BomItemEditor.vue | 共享行编辑器；现有字段含 parentMaterialId、materialId、moduleQty、baseQty、lossRate、unit、sourceType、substituteJson |
| BOM 详情/审核/打印 | BomDetail.vue、BomDetailTable.vue、BomItemReviewTable.vue、BomPrintPreview.vue | 查看与审核展示；审核行状态走行级审核接口 |
| 产品列表配置 BOM | jjx-web/src/views/product/list/components/BomConfigDialog.vue | 只选择已审批 BOM 并写产品 current_bom_id，不编辑 BOM 明细 |
| 作业规范中打开 BOM | jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue | 通过 BomFormDialog 编辑当前 BOM；整单保存仍走原入口 |
| 工单用料计算 | jjx-web/src/views/production/order/material-calculation/index.vue + model.ts | 现有前端按领料预览做需求/分配/替代料换算；存在第二套业务计算风险 |
| 生产订单新增/编辑 | jjx-web/src/views/production/order/components/OrderFormDialog.vue + index.vue | 只选产品，不选 MATERIAL 产出；提交走 /production/order |
| 领料预览/建单 | jjx-web/src/views/production/order/components/PickPreviewDialog.vue；InventoryOutboundController | GET /inventory/outbound/pick-preview/{workOrderId}；POST /create-from-production/{workOrderId} |
| 作业规范打印 | ProductSpecPreview.vue、WorkSpecPaper.vue、BomPrintPreview.vue | 展示 BOM/路线/结构图；不得在此反向改档案 |

后端入口：

| 接口 | 当前行为 |
|---|---|
| GET /engineering/bom/page | BOM 列表；按 product_id/bom_type/approve_status 过滤 |
| GET /engineering/bom/{bomId} | BOM 详情 |
| GET /engineering/bom/items/{bomId} | 明细列表 |
| POST /engineering/bom | 新增 BOM；createBomReturnId 214 行；默认 approve_status=草稿、is_current=false |
| PUT /engineering/bom | 修改 BOM；updateBomWithDetail；整单行合并 |
| PUT /engineering/bom/submit/{bomId} | 提交审核；checkBomForSubmit |
| PUT /engineering/bom/approve/{bomId} | 批准；checkBomForApprove；setDefault 由单独接口控制 is_current |
| PUT /engineering/bom/setDefault/{bomId} | 设为当前；要求 approve_status=3 且所有行审核通过；同步 product.current_bom_id |
| POST /engineering/bom/{bomId}/copy | 复制新版本；is_current=false、行重新草稿、校验原 BOM 内 parent 环 |
| GET /production/order/page | 生产订单列表；product_id 为唯一产出键 |
| POST /production/order | 新建生产订单；只校验 productId/plannedQuantity |
| POST /production/order/convert-plan-to-work-orders | 计划转工单；继承 plan.bom_id/routing_id，调用 ProductionBomResolver |
| GET /inventory/outbound/pick-preview/{workOrderId} | 按工单绑定 BOM 生成领料预览；只取 buy 行 |
| POST /inventory/outbound/create-from-production/{workOrderId} | 生成领料单；只取 buy 行；不足允许部分领料 |
| POST /inventory/outbound/confirm/{outboundId} | 确认发料；applyDelta 扣减批次/汇总/流水 |
| POST /inventory/inbound/... | 采购/生产完工入库；生产完工由 createFromProduction 固定 PRODUCT 身份 |

## 目标数据模型与契约

### 产出身份

契约采用“双身份显式化”，不是继续让 product_id 冒充所有产出：

1）BOM 产出身份用 output_type + output_id 表达。output_type 取 PRODUCT 或 MATERIAL；PRODUCT 时 output_id=product_id；MATERIAL 时 output_id=material_id。product_id 保留给 PRODUCT 产出，MATERIAL 产出允许为空。
2）生产订单同样增加 output_type + output_id（MATERIAL 时写 output_material_id 或统一 output_id，二者择一；本契约建议统一 output_id，另保留 product_id 兼容列）。PRODUCT 工单行为不变；MATERIAL 工单不再要求 product_id 非空。
3）MATERIAL 产出必须有 inventory_material 档案，status=1；PRODUCT 产出必须有 product 档案。两种身份不能互相顶替。
4）库存身份仍由 inventory_item 唯一表承载：PRODUCT 产出 ensure(PRODUCT, product_id)，MATERIAL 产出 ensure(MATERIAL, material_id)。但 F 开工前必须完成“inventory_item MATERIAL 身份与 inventory_material 的对齐/映射”，否则 ensure 会命中错误行；该对齐是数据订正，必须单独授权和备份，A 不执行。
5）半成品的“产出物料”就是 MATERIAL；不强制新建 product 记录。若后续要求半成品拥有独立工艺路线或作业规范版本，需另行批准路线/作业规范的物料身份扩展，不能在本阶段用 product 记录绕过。

替代方案（不推荐，仅记录）：给每个半成品建一个 product 记录，用 product_id 作为执行身份，再用 inventory_material.product_id 做桥接，库存入库仍落 MATERIAL 身份。该方案能复用现有路线/作业规范，但会污染产品档案、需要 product_role 标记、且 product_id 与 material_id 双写容易再次错位。除非用户明确要求半成品必须走产品路线/作业规范，否则不采用。

### BOM、方案与版本

1）engineering_bom 仍是方案主表。同一输出对象可有多个方案，每个方案一行主表 + 多行明细；方案身份是 bom_id，方案编码是 bom_code，方案版本是 bom_version/version。
2）engineering_bom.product_id 只在 output_type=PRODUCT 时必填；MATERIAL 方案必须 output_type=MATERIAL、output_id=material_id、product_id=NULL。
3）同一 output_type+output_id 的“输出默认方案”仍可用 is_current 表达，但产品上下文的主方案不能只看它；产品主方案由 product_material_scheme 表按 product_id × material_id 保存，is_primary=1 唯一。
4）bom_code + bom_version 继续唯一；版本复制沿用 copyAsNewVersion 的既有规则：新版本 is_current=false、行重新草稿、清审核痕迹、parent 行号两遍映射。
5）bom_type 继续表示 engineering/manufacturing，不承载产出类型；新增 output_type 单独定义枚举，避免与现有 BomType(1/2) 和 DB 字符串混用。
6）approved 状态仍是 approve_status=3；草稿可保存、可被产品 BOM 引用，但不得被生产取数。父 BOM 提交/批准时，所有非空 sub_bom_id 的引用方案必须 approve_status=3，否则拒绝父批准；产品 BOM 保存阶段允许引用草稿方案，但要在响应中返回警告。
7）is_current 的并发控制：同一 output_type+output_id 只允许一个 is_current=1。建议在迁移中增加生成列 current_scope（is_current=1 时拼接 output_type:output_id，否则 NULL）并建唯一索引；数据库不允许两个当前方案，服务层再做一次校验并返回可定位冲突。
8）关系边界：同一 BOM 分组或同组两行不产生制造依赖；背胶、离型纸各自是用料，不推断配套/贴合/来源关系；其他材料不因规格、模数、型号相同而推断缺少制造层级或自动补下层；本阶段不扩不同物料多产出/联产品，不强制把旧大料改成半成品。对应 001 议题 8/9，不在本契约重新当疑问提出。

### 明细与显式下层引用

1）parent_material_id 继续只做同一 BOM 内的分组/父子行；不得写入其他 BOM 的 item_id，也不得作为制造关系。现有 checkBom 只校验本 BOM 内父节点存在且非自指，保留该语义。
2）新增 engineering_bom_item.sub_bom_id（bigint NULL，索引）。含义：本行投入的物料由哪一份制造方案 BOM 产出。规则如下：
   A) sub_bom_id 非空时，source_type 必须为 make；source_type=buy 时禁止。
   B) 被引用 BOM 的 output_type 必须为 MATERIAL，且 output_id 必须等于本行 material_id；产品 BOM 不得被当作材料下层方案引用。
   C) 被引用 BOM 必须存在、未作废、approve_status=3、在需求日期处于生效窗口；草稿/驳回/过期/越权都拒绝，并返回 bomId 与路径。
   D) sub_bom_id 为空且 source_type=make 的行：历史行允许只读展示；新保存/提交/批准/需求计算一律阻断，错误码 MAKE_WITHOUT_SCHEME，不得静默当 buy。
   E) sub_bom_id 为空且 source_type=buy 的行：按现有直接采购/领料路径处理，不展开下层。
   F) sub_bom_id 非空且 source_type=make 的行：需求计划先扣可用半成品库存/已有生产供给；上层领料时必须把该行纳入候选，按库存可用量领用（material_id 身份），库存不足部分只保留缺口，不因领料动作重复创建下级工单。make 行没有 sub_bom_id 时按 MAKE_WITHOUT_SCHEME 阻断，不参与领料。
3）数量字段职责：module_qty、base_qty 是原始比例，必须保存原始值；quantity、applied_qty、actual_issue_qty 是展示/历史快照，不再作为新需求计算的权威输入。新保存时仍可重算快照，但需求计算必须读 module/base/loss。
4）单位换算快照：若 BOM 单位与 material.unit 不一致，新增 unit_factor、unit_factor_basis、unit_factor_source 三个字段，保存明确的换算依据；没有依据时保存/批准/需求计算均阻断。卷料实际长度属于批次/013 范围，不能只在 BOM 上写一个静态系数。
5）替代料沿用 substitute_json，但替代比例、损耗、取料步长只影响领料，不改变 BOM 原始需求；替代料不得生成第二套净需求。

### 产品主方案（新表提案）

逻辑对象：product_material_scheme。用途：记录某产品在产品上下文中，对某投入物料选择了哪个制造方案、哪个是本产品主方案。产品 A 选方案甲不影响产品 B 选方案乙。

建议 DDL（提案，未执行、未批准）：

```sql
CREATE TABLE product_material_scheme (
  scheme_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主方案记录ID',
  product_id BIGINT NOT NULL COMMENT '产品上下文 product.product_id',
  material_id BIGINT NOT NULL COMMENT '投入物料 inventory_material.material_id',
  bom_id BIGINT NOT NULL COMMENT '被选择的制造方案 engineering_bom.bom_id',
  is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '本产品该物料的主方案：0否1是',
  primary_marker TINYINT GENERATED ALWAYS AS (IF(is_primary=1,1,NULL)) STORED COMMENT '唯一主方案辅助列',
  revision INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(50) NOT NULL DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(50) NOT NULL DEFAULT '',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  remark VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (scheme_id),
  UNIQUE KEY uk_pms_product_material_bom (product_id, material_id, bom_id),
  UNIQUE KEY uk_pms_primary (product_id, material_id, primary_marker),
  KEY idx_pms_bom (bom_id),
  KEY idx_pms_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品×投入物料制造方案选择';
```

约束与行为：

1）同一 product_id+material_id+bom_id 只允许一行；同一 product_id+material_id 只允许一个 is_primary=1（primary_marker 为 NULL 的行不受唯一键限制，可保存多个候选）。
2）保存父产品 BOM 时在同一事务内写选择；bom_id 必须存在且 output_type=MATERIAL、output_id=material_id；允许草稿方案作为候选，但 is_primary=1 的方案必须已批准，否则父 BOM 保存返回警告、提交/批准阻断。
3）删除/作废方案时，若仍被选择引用，拒绝或要求先解除；不得级联删除产品选择。
4）查询必须带 product_id 上下文；禁止只按 material_id 取“全局主方案”。

替代评估：

| 方案 | 结论 | 原因 |
|---|---|---|
| 存在 engineering_bom_item.sub_bom_id + is_primary | 否决为主方案存储 | 主方案会随产品 BOM 版本复制/删除漂移；同一产品多个 BOM 版本无法保证唯一；数据库无法跨行约束唯一主方案 |
| 存在 product JSON / sys_config | 否决 | 无外键、无唯一约束、无并发控制、无法按物料查询，违反 CONVENTIONS §14/§15 |
| 半成品档案全局 is_current | 否决 | 直接违反“主方案针对本产品”，产品 A 的选择会被产品 B 覆盖 |
| 新建 product_material_scheme | 推荐 | 独立关系对象、会被需求/工单引用、需要唯一约束和变更留痕；满足 §15.2 的第 1/2/3/5/6 条 |

该表尚未得到用户拍板，不得在 B 阶段直接建表；B 只能把它作为迁移提案的一部分，等待批准、登记 scripts/model-baseline.json 的 approvedNewTables、同步清理归属后再执行。

### 供需分配：优先扩展现有 order_material_reserve

需求计算的“结果”不落库，确认后的“分配”落库。优先复用 order_material_reserve，避免第二套库存/预留表。建议新增字段（提案）：

| 字段 | 类型 | 用途 |
|---|---|---|
| source_bom_item_id | bigint NULL | 需求来源 BOM 行，支持跨层追溯 |
| source_path | varchar(500) NULL | 压缩路径（bomId/itemId/bomId/itemId），只作审计展示 |
| supply_type | varchar(20) NULL | STOCK/PURCHASE/PRODUCTION，空=旧预留 |
| supply_id | bigint NULL | 库存批次/采购明细/生产工单 ID |
| required_date | date NULL | 需求日期；用于时间轴分配 |
| allocated_quantity | decimal(12,4) NULL | 本供给已分配数量 |
| request_key | varchar(64) NULL | 确认幂等键；唯一索引 |
| demand_order_id | bigint NULL | 上层需求工单/销售订单；与 order_id 区分 |

约束：UNIQUE(request_key)、KEY(order_id, material_id, supply_type, supply_id)、KEY(source_bom_item_id)。旧行这些字段全 NULL，行为不变；新逻辑只处理 supply_type 非空行。若用户不接受扩列，再评估独立分配表；但独立表会与现有预留/占用产生第二真源，默认不推荐。

### 迁移兼容与回退

迁移编号在实际执行时取账本最大号与目录最大号较大者 +1；当前为 356，但不得在 A 阶段预占或生成 SQL。

建议拆成三段，分别由 B、D、F 执行，避免一个任务同时改三类事实：

| 段 | 归属 | 内容 | 兼容/回退 |
|---|---|---|---|
| 356（B） | 制造BOM后端能力 | engineering_bom 增加 output_type/output_id/current_scope；product_id 改为 NULL；engineering_bom_item 增加 sub_bom_id、unit_factor、unit_factor_basis、unit_factor_source；若新表获批，建 product_material_scheme | 先加列并回填 output_type='PRODUCT'、output_id=product_id；确认无 MATERIAL 行后再改 product_id NULL；回退时先禁用/删除 MATERIAL 方案行，恢复 product_id NOT NULL，再删列/表。旧 4 行 BOM 与 12 行明细语义不变 |
| 357（F） | 半成品生产库存闭环 | production_order 增加 output_type/output_id；product_id 改为 NULL；必要时给 inventory_inbound_item.material_id 建索引 | 先加列并回填 PRODUCT/product_id；确认无 MATERIAL 工单后改 NULL；回退时删除/归档 MATERIAL 工单，恢复 NOT NULL，再删列。历史 PRODUCT 工单不变 |
| 358（D） | 精确耗用与净需求 | order_material_reserve 增加供需分配字段与唯一键 | 加列可空、默认 NULL；回退先删除/归档新分配行，再删列/索引；旧预占行不受影响 |

兼容规则：

1）所有新增列先可空或带默认值，旧客户端不传时保持旧行为；新接口才使用。
2）回填必须幂等：只对 output_type IS NULL 的行回填；重复执行不改变已回填行。
3）改 NOT NULL 前必须用只读 SQL 证明无 NULL 冲突；迁移文件内先查 information_schema/数据，再执行 ALTER。
4）回退必须由用户手工备份后执行；A/B 不自动备份、不生成备份文件。
5）新表若获批，必须同步 scripts/model-baseline.json 的 approvedNewTables（任务码/日期/提案链接）和 jjx-docs/sql/00_clean_test_data.sql 的清理归属；未同步前 pre-push 建表闸会红。
6）所有字符串列显式 utf8mb4/utf8mb4_unicode_ci；生成列/唯一索引需在迁移中写明理由。

## 版本适用、防环与保存边界

### 版本适用

1）生产工单创建时快照 bom_id/bom_code；ProductionBomResolver 优先按 order.bom_id 取，其次兼容历史空值按 product 当前已批准版本取。历史工单永远按快照，不因产品切换当前版本而重算。
2）新需求预览的解析顺序：请求显式 bomId > product.current_bom_id > 该产品最新已批准且 is_current=1 的 BOM；若都找不到，错误 BOM_NOT_FOUND，不静默用任意版本。
3）选中 BOM 必须 approve_status=3，且所有有效行 review_status=3；effective_date/expiry_date 非空时，demandDate 必须落在窗口内，否则 BOM_NOT_EFFECTIVE。历史空日期视为无界，保持旧 BOM 可用。
4）产品 BOM 行引用 sub_bom_id 时，子方案按固定 bom_id 使用，不按“最新版本”自动替换；子方案未批准/过期/输出不匹配时，预览和工单创建都阻断，并返回路径。
5）本产品主方案只影响“新加行/试算默认选择”，不自动改写已保存行的 sub_bom_id；已保存行必须显式保存其选择。
6）版本切换不回写历史工单、历史领料、历史批次；V03 要求的历史依据由 order.bom_id、领料 source_bom_item_id、批次 source_* 保留。
7）生产中发现 BOM 错误时，不强制“原单变更”或“取消重开”中的某一种；按现有系统能闭环的路径处理，但必须补齐未加工部分、已领补退、已加工返工/报废、供应释放和修改前后依据。不得用覆写历史流水伪装闭环。

### 防环

1）图节点是 BOM（bom_id）。边是 engineering_bom_item.sub_bom_id。只在显式引用上建边；parent_material_id 不参与跨 BOM 图。
2）保存方案时校验：子 BOM 存在、输出类型/ID 与本行 material_id 一致、不是自身、不是祖先。
3）提交/批准父 BOM 时做全图 DFS：用“当前路径集合”检测重复节点；同一 BOM 在不同分支重复出现是 DAG，允许；同一路径重复即环，拒绝并返回完整路径。
4）需求预览/工单创建时再做一次运行时 DFS；默认最大深度 32，超过返回 BOM_DEPTH_EXCEEDED，不静默截断为两层。
5）自引用（A 引用 A）、直接环（A→B→A）、悬空子 BOM、越权/未批准子 BOM 都必须有反例测试；错误码和路径可定位。
6）复制 BOM 版本时保留 sub_bom_id，但重新校验子 BOM 输出与批准状态；不得把旧版本行号带入新版本。

### 保存、采用、父保存边界

| 动作 | 写入什么 | 不写入什么 | 事务/校验 |
|---|---|---|---|
| 新建制造BOM→保存制造BOM | 新 engineering_bom + 明细；返回 bomId、bomVersion、approve_status=1 | 不自动发布、不改产品 BOM、不生成库存/工单、不设 is_current | 单事务；校验 output、module/base>0、单位/换算、sub_bom 存在且无环 |
| 编辑制造BOM→保存制造BOM | 该 bom_id 的主表/明细；行内容变更时按既有规则回到草稿/待审核 | 不改父产品 BOM 的草稿；不改其他产品选择；不自动批准 | 行锁/版本冲突检查；已批准行的修改按现有行审核规则处理 |
| 制造BOM→采用此方案 | 不落库；返回 selectionToken（bomId、output、materialId、版本） | 不写 product_material_scheme、不写父 BOM、不设主方案 | 只允许已保存方案；未保存方案不能生成有效 token |
| 产品BOM→保存 | 父 BOM 主表/明细、items[].sub_bom_id、schemeChoices（含 isPrimary）同一事务 | 不自动批准子方案；不删除独立已保存方案；不改其他产品选择 | 父 BOM 行锁；校验 scheme 输出一致；草稿引用允许但返回警告 |
| 产品BOM→提交/批准 | 父 BOM 状态；校验所有 sub_bom 已批准/生效、所有行审核通过 | 不自动批准子方案；不自动改子方案版本 | 子方案未批准→阻断，错误码 SUB_BOM_NOT_APPROVED |
| 父BOM取消/关闭 | 父 BOM 未保存草稿丢弃；已保存父 BOM 保持原状 | 不删除独立制造方案；不回滚已保存方案；不删除已持久化选择 | 只影响当前草稿/会话 |
| 只读/权限不足 | 无写入 | 不产生方案、选择、需求、工单、库存 | 前后端一致校验 engineering:bom:view 等权限 |

关键边界：制造BOM保存成功、父BOM随后取消时，独立方案仍存在；父BOM未保存的 sub_bom_id/主方案修改不生效。采用动作本身不是持久化，父保存才是。

### 完整入口保存矩阵

| 入口 | 新增/编辑 | 保存对象 | 保存边界与不应发生的结果 | 归属卡 |
|---|---|---|---|---|
| BOM管理页→BomFormDialog 整单 | 新增 | 产品BOM主表+全部明细+schemeChoices | 一次事务；不自动批准/不生成工单/不改其他产品；新增时 can 为空 | B（接口）、C（窗口/入口） |
| BOM管理页→BomFormDialog 整单 | 编辑 | 同上，按 bomId 更新 | 行锁/版本冲突；已批准行修改按行审核；不静默覆盖他人在制 | B、C |
| BOM管理页→制造BOM独立窗口 | 新增方案 | 制造BOM主表+明细（MATERIAL 或 PRODUCT 产出） | 不改父BOM；不设 is_current；不生成库存/工单 | B、C |
| BOM管理页→制造BOM独立窗口 | 编辑方案 | 该方案主表+明细 | 不改父BOM；不自动发布；子引用重新防环 | B、C |
| 制造BOM窗口→采用此方案 | 选择 | 仅前端父BOM草稿；返回 token | 不落库、不设主方案、不提前改别的产品 | C |
| 产品BOM整单保存 | 新增/编辑 | 父BOM+items.sub_bom_id+schemeChoices | 采用结果随父保存持久化；父取消不删除独立方案 | B、C |
| 产品列表→配置BOM | 选择 | product.current_bom_id/current_bom_version | 只选已批准BOM；不编辑明细；不改变方案主数据 | 现有能力，不改 |
| 作业规范→打开BOM | 编辑 | 仍走BomFormDialog整单保存 | 不新增第二条保存链；不反向改物料档案规格 | C、G |
| BOM详情/审核/打印 | 只读 | 无 | 不产生需求/工单/库存；审核只改行状态与流水 | 现有能力，B回归 |
| 生产订单新增/编辑 | 新增/编辑 | 工单主表（PRODUCT 或 MATERIAL 产出、bom 快照） | 不保存BOM方案；不改父BOM；不自动领料 | F |
| 计划转工单 | 新增 | 子工单+工序执行 | 继承计划 bom_id/routing；不重选BOM；MATERIAL 例外见F | F |
| 领料预览 | 只读 | 无 | 不扣库存、不建单、不写流水 | D |
| 生成领料单/确认发料 | 新增/确认 | 出库单/明细+流水+余额 | buy 行按现有；make 行仅在已有可用半成品库存/已完工产出时按库存领用，缺口不领料、不重复开下级工单；applyDelta 唯一入口 | D、F |
| 完工入库 | 新增/确认 | 入库单/明细+批次+流水+余额 | PRODUCT 用 PRODUCT 身份；MATERIAL 用 MATERIAL 身份；按实际合格数，不按理论数 | F |
| 作业规范/打印 | 只读 | 无 | 不写BOM/路线/档案；只读展示 | G |

## 接口契约

所有接口沿用现有 Result 包裹（code/data/message），权限沿用 engineering:bom:*、production:order:*、inventory:outbound:*、inventory:alert:* 等；新增接口不新建权限点，除非另行批准独立管理页。

### 接口清单

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | /engineering/bom/manufacturing/options?outputType=MATERIAL&outputId=349 | 查询某输出对象的候选制造方案（已保存，含草稿标记） | engineering:bom:view |
| POST | /engineering/bom/manufacturing | 新建制造方案并保存明细 | engineering:bom:add |
| PUT | /engineering/bom/manufacturing/{bomId} | 编辑制造方案 | engineering:bom:edit |
| POST | /engineering/bom/manufacturing/{bomId}/adopt | 生成采用 token，不落库 | engineering:bom:view |
| PUT | /engineering/bom/{bomId} | 父产品BOM整单保存（含 items.subBomId 与 schemeChoices） | engineering:bom:edit |
| GET | /engineering/bom/{bomId}/scheme-choices | 查看父BOM已保存选择 | engineering:bom:view |
| POST | /production/requirement/preview | 多层净需求只读试算 | production:order:view 或 inventory:alert:view |
| POST | /production/requirement/confirm | 确认缺口，创建分配/补做工单 | production:order:add |
| GET | /production/requirement/{requestId} | 查询试算/确认结果，幂等回查 | production:order:view |
| POST | /production/order/manufacturing | 直接创建 MATERIAL 补做工单（可被 confirm 内部调用） | production:order:add |

### 请求响应 JSON 示例

新建制造方案：

```json
POST /engineering/bom/manufacturing
{
  "bomCode": "SFG000345-MFG-A",
  "bomName": "中间件制造方案-甲",
  "outputType": "MATERIAL",
  "outputId": 349,
  "bomVersion": "V1.0",
  "effectiveDate": "2026-11-11",
  "expiryDate": null,
  "remark": "A阶段契约示例，不落库",
  "items": [
    {
      "clientLineId": "l1",
      "materialId": 109,
      "materialCode": "RM000105",
      "materialName": "PC-800 茶色 单面加硬",
      "unit": "M",
      "moduleQty": 6,
      "baseQty": 1,
      "lossRate": 0,
      "sourceType": "buy",
      "processId": 21,
      "itemOrder": 1
    },
    {
      "clientLineId": "l2",
      "materialId": 350,
      "unit": "PCS",
      "moduleQty": 1,
      "baseQty": 1,
      "lossRate": 0,
      "sourceType": "make",
      "subBomId": 124,
      "itemOrder": 2
    }
  ]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bomId": 124,
    "bomCode": "SFG000345-MFG-A",
    "bomVersion": "V1.0",
    "outputType": "MATERIAL",
    "outputId": 349,
    "approveStatus": 1,
    "isCurrent": false,
    "items": [
      { "itemId": 201, "clientLineId": "l1", "materialId": 109, "unit": "M", "moduleQty": 6, "baseQty": 1, "quantity": 0.1667, "sourceType": "buy" },
      { "itemId": 202, "clientLineId": "l2", "materialId": 350, "unit": "PCS", "moduleQty": 1, "baseQty": 1, "subBomId": 124, "sourceType": "make" }
    ]
  }
}
```

采用方案（不落库）：

```json
POST /engineering/bom/manufacturing/124/adopt
{
  "productId": 1,
  "materialId": 349
}
```

```json
{
  "code": 0,
  "data": {
    "selectionToken": "adopt-8f2c1d9e",
    "productId": 1,
    "materialId": 349,
    "bomId": 124,
    "bomCode": "SFG000345-MFG-A",
    "bomVersion": "V1.0",
    "outputType": "MATERIAL",
    "outputId": 349,
    "persisted": false
  }
}
```

父产品BOM整单保存（采用结果随父保存持久化）：

```json
PUT /engineering/bom
{
  "bomId": 1,
  "bomCode": "YL1025PWOO-BOM",
  "bomName": "YL1025PWOO-BOM",
  "bomVersion": "V1.0",
  "productId": 1,
  "revisionTime": "2026-11-11 10:20:00",
  "items": [
    {
      "itemId": 1,
      "materialId": 349,
      "materialCode": "SFG000345",
      "materialName": "中间件",
      "unit": "PCS",
      "moduleQty": 1,
      "baseQty": 1,
      "lossRate": 0,
      "sourceType": "make",
      "subBomId": 124,
      "itemOrder": 1
    }
  ],
  "schemeChoices": [
    { "materialId": 349, "bomId": 124, "isPrimary": true }
  ]
}
```

成功响应应包含持久化后的 subBomId、schemeChoices 和 revisionTime；若子方案未批准，保存成功但返回 warning 数组，例如：

```json
{
  "code": 0,
  "data": {
    "bomId": 1,
    "success": true,
    "warnings": [
      { "code": "SUB_BOM_NOT_APPROVED", "materialId": 349, "bomId": 124, "message": "子方案尚未批准，父BOM可保存但不可提交/批准" }
    ]
  }
}
```

多层净需求试算：

```json
POST /production/requirement/preview
{
  "requestId": "req-20261111-0001",
  "productId": 1,
  "bomId": 1,
  "quantity": 300,
  "demandDate": "2026-11-20",
  "warehouseId": null,
  "includeExistingSupply": true
}
```

```json
{
  "code": 0,
  "data": {
    "requestId": "req-20261111-0001",
    "calculatedAt": "2026-11-11T10:30:00+08:00",
    "root": {
      "outputType": "PRODUCT",
      "outputId": 1,
      "bomId": 1,
      "bomVersion": "V1.0",
      "requiredQty": 300,
      "lines": [
        {
          "bomItemId": 1,
          "materialId": 109,
          "materialCode": "RM000105",
          "unit": "M",
          "ratio": { "baseQty": 1, "moduleQty": 6, "text": "1/6" },
          "lossRate": 0,
          "grossQty": "50.0000",
          "stockAvailable": "140.0000",
          "purchaseInTransit": "0.0000",
          "productionSupply": "0.0000",
          "allocatedQty": "50.0000",
          "gapQty": "0.0000",
          "sourcePath": ["bom:1/item:1"]
        },
        {
          "bomItemId": 5,
          "materialId": 349,
          "materialCode": "SFG000345",
          "unit": "PCS",
          "ratio": { "baseQty": 1, "moduleQty": 1, "text": "1/1" },
          "lossRate": 0,
          "grossQty": "300.0000",
          "stockAvailable": "200.0000",
          "reservedQty": "60.0000",
          "purchaseInTransit": "0.0000",
          "productionSupply": "0.0000",
          "allocatedQty": "140.0000",
          "gapQty": "160.0000",
          "subBomId": 124,
          "sourcePath": ["bom:1/item:5"]
        }
      ]
    },
    "summary": [
      { "materialId": 349, "unit": "PCS", "grossQty": "300.0000", "gapQty": "160.0000", "action": "MAKE", "schemeBomId": 124 },
      { "materialId": 109, "unit": "M", "grossQty": "50.0000", "gapQty": "0.0000", "action": "NONE" }
    ],
    "warnings": [],
    "blockers": []
  }
}
```

确认缺口并生成补做工单：

```json
POST /production/requirement/confirm
{
  "requestId": "req-20261111-0001",
  "previewVersion": "2026-11-11T10:30:00+08:00",
  "idempotencyKey": "confirm-20261111-0001",
  "items": [
    {
      "materialId": 349,
      "gapQty": "160",
      "action": "MAKE",
      "schemeBomId": 124,
      "plannedStartDate": "2026-11-12",
      "plannedEndDate": "2026-11-15",
      "priority": "MEDIUM"
    }
  ]
}
```

```json
{
  "code": 0,
  "data": {
    "requestId": "req-20261111-0001",
    "idempotencyKey": "confirm-20261111-0001",
    "duplicate": false,
    "workOrders": [
      {
        "orderId": 3,
        "orderNo": "WO20261111001",
        "orderType": "WORK_ORDER",
        "parentOrderId": 1,
        "outputType": "MATERIAL",
        "outputId": 349,
        "productCode": "SFG000345",
        "productName": "[TST] 中间件",
        "productUnit": "PCS",
        "bomId": 124,
        "plannedQuantity": 160,
        "orderStatus": 4
      }
    ],
    "allocations": [
      { "materialId": 349, "supplyType": "STOCK", "supplyId": 9001, "allocatedQty": "140.0000", "requiredDate": "2026-11-20" }
    ]
  }
}
```

重复 idempotencyKey 时返回同一结果，duplicate=true，不新增工单、不重复占用。

### 错误码

| 错误码 | 触发 | 处理 |
|---|---|---|
| BOM_NOT_FOUND | 请求/产品上下文找不到已批准 BOM | 提示选择版本，不自动猜 |
| BOM_NOT_EFFECTIVE | 需求日期不在 effective/expiry 窗口 | 提示版本失效 |
| SUB_BOM_NOT_FOUND | sub_bom_id 指向不存在 BOM | 阻断保存/提交/预览 |
| SUB_BOM_OUTPUT_MISMATCH | 子 BOM 输出与投入 material_id 不一致 | 阻断并返回两者 |
| SUB_BOM_NOT_APPROVED | 子 BOM 未批准 | 父保存可警告，提交/批准/生产阻断 |
| MAKE_WITHOUT_SCHEME | source_type=make 且 sub_bom_id 为空 | 阻断需求计算，不静默当 buy |
| BUY_WITH_SUB_BOM | source_type=buy 且 sub_bom_id 非空 | 阻断保存 |
| BOM_CYCLE | 显式引用形成环 | 返回完整路径 |
| BOM_DEPTH_EXCEEDED | 超过最大层数 | 阻断，不截断 |
| MODULE_QTY_INVALID / BASE_QTY_INVALID | <=0、空、非法数 | 阻断保存 |
| LOSS_RATE_INVALID | 不在 0..100 | 阻断保存 |
| UNIT_MISMATCH / UNIT_FACTOR_MISSING | BOM 单位与物料单位不一致且无依据 | 阻断保存/领料 |
| UNIT_POLICY_MISSING | 单位不在取整策略表 | 阻断，不猜精度 |
| INVENTORY_IDENTITY_CONFLICT | material_id 对应的 inventory_item 身份冲突 | 阻断出入库，要求先核对 |
| SUPPLY_CHANGED | 确认时供给被其他事务占用 | 重新试算，不部分提交 |
| DUPLICATE_REQUEST | 同 idempotencyKey 已在处理 | 返回已有结果或 409 |

## 精确比例、单位与净需求契约

### 比例与精度

1）所有数量/比例使用 BigDecimal，禁止 double/float。模数、基数保存为 decimal(12,4)，必须 >0；允许 0.5、2.5 等正小数，拒绝 0、负数、空值、NaN/Infinity、非数字。
2）损耗率改为 decimal(5,2) 对应 BigDecimal，范围 0..100（含 0，不含 100 以上）；只在本行所属层计一次：
   gross = Q × baseQty ÷ moduleQty × (1 + lossRate ÷ 100)
3）除法不提前落库。保存时 quantity 可写展示快照（4 位），但需求计算必须重新用 base/module 原始值；禁止把 0.1667 当权威比例。
4）中间计算用 MathContext.DECIMAL128 或 scale 12；最终按单位策略一次取整。比较用 compareTo，不用 equals。
5）示例：Q=300、base=1、module=6、loss=0 → 300×1÷6=50.0000；不是 300×0.1667=50.01→51。Q=160、base=1、module=6 → 26.6667（连续单位）或按件取整前先聚合。
6）损耗只在所属层计一次：上层展开得到下层需求后，下层再按自己的 loss 计；不得在父层和子层对同一用量重复加损耗。
7）最低投料 min_issue_qty 与生产批量不是净需求：净需求是事实缺口；补做建议量按生产批量向上取整；领料量按 min_issue_qty 下限与单位取整。三者分列返回，不能合并成一个数。

### 单位与换算

1）权威记账单位是 inventory_material.unit；BOM 投入单位应与之一致。不一致时必须满足以下之一，否则阻断：
   A) unit_alt 非空、unit_conv>0，且 BOM 单位等于 unit_alt 或 material.unit，按明确系数换算；
   B) 使用静态尺寸换算（张/片→㎡）：material.width_mm×length_mm÷1,000,000，且必须有尺寸；不得用规格文本猜；
   C) 卷料→㎡：只能使用该卷批次的实际宽度×实际长度；批次长度不在 BOM 上，必须由 013/批次字段提供，否则阻断。
2）BOM 行新增 unit_factor/unit_factor_basis/unit_factor_source，保存时固化换算依据；物料档案后续改尺寸不得静默重解释历史 BOM/流水。
3）1 PCS 不得直接当 1 M 或 1 ㎡；单位不兼容时返回 UNIT_MISMATCH，不做“就近单位”猜测。
4）面积换算只换单位，不重算模数；确认的排版模数/基数决定耗用，规格变化不得自动改模数。
5）013（卷材逐卷余量）仍独立；本契约只定义“没有批次实际长度就阻断”，不实现逐卷余量、不启动 013。

### 单位取整策略

| 单位类别 | 示例 | 取整规则 |
|---|---|---|
| 计件 | PCS、个、件、片、张、只、套、PIN | 聚合后向上取整到整数（CEILING）；不得逐行取整后相加 |
| 连续长度 | M、m、米 | 4 位小数，HALF_UP；不向上取整 |
| 面积 | M2、㎡、平方米 | 4 位小数，HALF_UP；不向上取整 |
| 重量 | KG、kg、G、g | 4 位小数，HALF_UP |
| 体积 | L、ML | 4 位小数，HALF_UP |
| 未登记 | 其他 | UNIT_POLICY_MISSING，阻断并登记策略后再算 |

取整只发生在“同一物料 + 同一单位 + 同一需求日期桶”的聚合之后；明细行保留未取整值用于追溯。计件单位若必须整批领用，额外返回 batch_rounded_qty，不能覆盖 exact_qty。

### 净需求算法

1）读取本次采用的 BOM：显式 bomId 优先；校验 approve_status、行审核、生效窗口、权限、物料/单位/换算；构建显式 sub_bom 图并 DFS 防环。
2）按拓扑依赖处理：先汇齐同一输出物料的上游贡献，再对兼容的“material_id+单位+需求日期桶”汇总；不同单位/不同版本/不同仓库不盲目合并；保留每条来源路径。
3）计算总需求 gross；读取可用库存、其他需求占用、按需求日期可用的采购在途、已有生产供给。
4）可用库存口径：available = max(0, inventory_stock.total_quantity - inventory_stock.total_reserved - material.safe_stock)。安全库存为 0 时不影响；不使用 inventory_stock.available_quantity 后再重复扣减。
5）其他需求占用：物理预留以 inventory_stock_item.reserved_quantity / inventory_stock.total_reserved 为准；order_material_reserve 只作计划占用，若已进入 total_reserved 则不重复扣。现有 InventoryAlertServiceImpl 已注明旧账停扣 order_material_reserve，新逻辑要统一口径并记录。
6）采购在途：purchase_order_item.quantity - received_quantity，条件 purchase_order.receipt_status=0、approval_status IN (1,3,4)、expected_delivery_date ≤ demandDate；日期为空不覆盖有日期需求。
7）生产供给：production_order.remaining_quantity，条件 order_type=WORK_ORDER、order_status IN (4,5,6)、输出身份一致、plan_end_date ≤ demandDate、排除当前需求自身/已取消；已有生产供给的原料需求按原工单追踪，不重复生成。
8）分配顺序：库存 → 已有生产供给 → 采购在途 → 新补做；同一供给只能分配一次；跨分支共享库存时先聚合再分配，不能两个分支都认为无缺口。
9）gap = max(0, gross - 已分配库存 - 已分配生产 - 已分配采购)；gap 不小于 0。
10）若 gap>0 且该物料是 MAKE：必须存在已批准且生效的 sub_bom_id，否则 MAKE_WITHOUT_SCHEME；按生产批量得到 recommended_production_qty，再以该数量展开下层；原有库存/已有供给覆盖的部分不展开。
11）若 gap=0：不展开下层；但仍返回来源路径与已分配明细。
12）试算只读：不扣库存、不建工单、不写流水；确认后才创建分配与补做工单，并在事务内重新校验供给，失败回滚，不出现半套单据。
13）幂等：confirm 必须带 idempotencyKey；数据库唯一键 + 行锁；重复请求返回同一结果，不重复占用/开单。
14）同一半成品出现在不同深度：按“material_id+单位+日期桶”汇齐兼容需求后统一净额与展开；不能因深度不同重复分配库存或漏算后到需求。
15）库存变化后重新试算；前端不得拉全库库存自算采购需求，也不得保留第二套权威算法。

### 生产工单 MATERIAL 身份闭环路径

1）需求确认识别 material_id=349 的缺口 160，选择已批准方案 bom_id=124。
2）创建 production_order：order_type=WORK_ORDER；parent_order_id=上层工单；output_type=MATERIAL；output_id=349；product_id=NULL（迁移后允许）；product_code/product_name/product_unit 从 inventory_material 冗余写入，仅作展示；bom_id=124；routing_id=NULL；trace_id 继承上层；planned_quantity=160；order_status=4（已计划）。
3）F 生成工序：从方案 BOM 的有效行按 process_id/process_order 去重排序生成 production_operation_execution；若方案没有任何 process 行，阻断 MATERIAL_SCHEME_WITHOUT_PROCESS，要求先补工艺项目；不得凭空造工序。
4）开工/领料：buy 行按现有规则生成领料；make 行有可用半成品库存/已完工产出时必须可被上层领用（material_id 身份、FIFO 批次），库存不足部分只保留缺口，由需求计划生成/复用下级补做工单，领料动作本身不得重复创建下级工单。make 行没有 sub_bom_id 时阻断，不静默跳过。
5）完工：工序完成 + FQC 合格后 completeOrder；createFromProduction 对 MATERIAL 产出执行 ensure(MATERIAL, material_id=349)，inbound_item.material_id=349、inventory_item_id=MATERIAL 身份、batch_no=BATCH-{orderNo}；applyDelta 写批次/汇总/流水，source_type=PRODUCTION、source_id=orderId。
6）上层领用：上层 BOM 行 material_id=349，FIFO 按 material_id 找到该批次；出库明细 material_id=349、batch_no 一致；成品入库仍走 PRODUCT 身份。原料批次→半成品工单→半成品批次→上层领料→成品批次链路保留。
7）异常：合格入库不足 160 时，上层只能领实际可用量，剩余缺口继续显示；不得虚增；红冲/退料按现有反向单据，不更新原流水。
8）前置阻断：inventory_item MATERIAL 身份未对齐时，F 不得开工入库；先由用户批准的数据订正建立 material_id → inventory_item_id 的可核对映射。当前零库存，不可用旧 inventory_item 直接当新库。

## B 至 G 精确白名单

通用约束：每个任务只改本卡白名单；白名单外的源码/库只读；迁移 SQL 只在用户批准并手工备份后由执行者通过 scripts/db-migrate.sh 执行；不自动备份；不启动/停止/重启服务；C/D、D/E、D/F 共享文件按“共享作者”串行。

### 共享作者与串行关系

| 文件/目录 | 唯一作者 | 串行规则 |
|---|---|---|
| jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringBomServiceImpl.java | B（OpenClaw） | D/F 只调用，不编辑；需要改动由 B 追加小卡 |
| jjx-server/src/main/java/com/jjx/engineering/controller/BomController.java | B | C 只调接口；E/F 不改 |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java | D（OpenClaw） | F 不改；E 只读 API；D 完成并通过验收后才可 F |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryAlertServiceImpl.java | D | E 只读 API/前端，不改后端 |
| jjx-server/src/main/java/com/jjx/production/service/ProductionBomResolver.java | D | F 使用该接口；F 需要产出身份参数时由 D 先扩展并验收 |
| jjx-server/src/main/java/com/jjx/production/service/impl/ProductionOrderServiceImpl.java | F（OpenClaw） | D 不改；E 不改 |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java | F | D 不改；E 不改 |
| jjx-web/src/api/product/bom.ts、jjx-web/src/types/product/bom.ts | C（Hermes） | E/F 各自新建 API 文件，不改这两个文件 |
| jjx-web/src/views/product/bom/components/BomFormDialog.vue、BomItemEditor.vue | C | G 只读/打印，不改保存链 |
| jjx-web/src/views/product/bom/components/BomDetailTable.vue | C 先，G 后 | C 完成方案展示并通过验收后，G 才能加打印/作业规范展示 |
| jjx-web/src/views/production/order/index.vue、OrderFormDialog.vue | F | E 只改 material-calculation 子目录，不改订单主表单 |
| jjx-web/src/views/purchase/plan/index.vue、jjx-web/src/api/purchase/order.ts | E | F/G 不改 |
| jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue | G | C 只通过现有 BomFormDialog 打开，不改该文件 |

### B 制造BOM后端能力（OpenClaw）

现有文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringBom.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringBomItem.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringBomDTO.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringBomItemDTO.java
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomVO.java
6）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomItemVO.java
7）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomEditVO.java
8）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/converter/EngineeringBomConverter.java
9）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/IEngineeringBomService.java
10）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringBomServiceImpl.java
11）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/EngineeringBomMapper.java
12）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/EngineeringBomItemMapper.java
12a）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/query/EngineeringBomQuery.java
12b）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomExportVO.java
13）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/engineering/controller/BomController.java
14）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/enums/ProductEnums.java
15）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/BomGroupPersistenceTest.java（回归，不扩大断言）

新增文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/ManufacturingBomSaveDTO.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/ManufacturingSchemeChoiceDTO.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/ManufacturingBomVO.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/ManufacturingSchemeVO.java
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/ManufacturingBomService.java
6）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/impl/ManufacturingBomServiceImpl.java
7）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/entity/ProductMaterialScheme.java（仅新表获批后）
8）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/ProductMaterialSchemeMapper.java（仅新表获批后）
9）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/ManufacturingBomSchemeTest.java
10）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/ManufacturingBomCycleTest.java
11）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/BomDecimalRatioTest.java

条件文件（仅用户批准新表/结构变更后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/356_bom_manufacturing_scheme.sql（编号执行时按账本重取）
2）/home/administrator/jjx/scripts/model-baseline.json（approvedNewTables 增项）
3）/home/administrator/jjx/jjx-docs/sql/00_clean_test_data.sql（新表清理归属）

权限：沿用 engineering:bom:view/add/edit/approve/reject/delete；不新增权限点。

验证：mvn -o -pl jjx-server -am test 定向（2G/180 秒限制按卡面），至少覆盖方案保存回读、多方案隔离、sub_bom 输出匹配、环/自指/悬空、正小数/0/负数、单位不一致；不跑库存写入路径时不需要 check:stock:strict，若 B 触及库存只读查询则加跑。

### C 制造BOM独立窗口（Hermes）

现有文件：

1）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomFormDialog.vue
2）/home/administrator/jjx/jjx-web/src/components/BomItemEditor.vue
3）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomDetailTable.vue
4）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomItemReviewTable.vue
5）/home/administrator/jjx/jjx-web/src/api/product/bom.ts
6）/home/administrator/jjx/jjx-web/src/types/product/bom.ts
6a）/home/administrator/jjx/jjx-web/src/views/product/bom/index.vue
6b）/home/administrator/jjx/jjx-web/src/enums/product/index.ts
7）/home/administrator/jjx/jjx-web/src/components/__tests__/BomItemEditor.spec.ts
8）/home/administrator/jjx/jjx-web/src/components/__tests__/BomItemEditorGroups.test.cjs

新增文件：

1）/home/administrator/jjx/jjx-web/src/views/product/bom/components/ManufacturingBomDialog.vue
2）/home/administrator/jjx/jjx-web/src/views/product/bom/components/ManufacturingBomItemTable.vue
3）/home/administrator/jjx/jjx-web/src/views/product/bom/components/ManufacturingBomPath.vue
4）/home/administrator/jjx/jjx-web/src/views/product/bom/components/__tests__/manufacturingBomDialog.spec.ts
5）/home/administrator/jjx/jjx-web/src/views/product/bom/components/__tests__/manufacturingBomPath.spec.ts

权限：engineering:bom:add/edit 控制保存/采用；engineering:bom:view 只读；前端隐藏按钮不能替代后端校验。

验证：Vitest 单 worker、1G、120 秒；vue-tsc 3072M、300 秒；覆盖 U01/U02/U03/U04/U05/U06 的组件行为；不启动 dev server。

### D 精确耗用与多层净需求（OpenClaw）

现有文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/ProductionBomResolver.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryAlertServiceImpl.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/ProductionOrderCreateDTO.java（只读，确认字段）
5）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/inventory/InventoryOutboundInvariantTest.java
6）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/ProductionBomResolverTest.java
7）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/inventory/InventoryAlertPurchaseSuggestionsTest.java

新增文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/ManufacturingRequirementService.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/impl/ManufacturingRequirementServiceImpl.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/RequirementPreviewRequest.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/RequirementConfirmRequest.java
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/vo/RequirementNodeVO.java
6）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/vo/RequirementLineVO.java
7）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/vo/RequirementSummaryVO.java
8）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/controller/ManufacturingRequirementController.java
9）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ManufacturingRequirementNettingTest.java
10）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ManufacturingRequirementPrecisionTest.java
11）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ManufacturingRequirementSupplyTest.java

条件文件（仅 358 迁移批准后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/358_requirement_allocation.sql
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/domain/OrderMaterialReserve.java（加字段）
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/mapper/OrderMaterialReserveMapper.java（唯一键/查询）

验证：mvn 定向 2G/180 秒；至少覆盖 D01—D05、N01—N06、C02、C04 的计算与反例；库存写入路径若改动，跑 npm run validate + check:stock:strict 五项全 0；否则明确写“未跑库存门禁，因为未改写入路径”。

### E 需求计划与采购接入（Hermes）

现有文件：

1）/home/administrator/jjx/jjx-web/src/views/production/order/material-calculation/index.vue
2）/home/administrator/jjx/jjx-web/src/views/production/order/material-calculation/model.ts
3）/home/administrator/jjx/jjx-web/src/views/production/order/components/PickPreviewDialog.vue
4）/home/administrator/jjx/jjx-web/src/views/inventory/alert/index.vue
5）/home/administrator/jjx/jjx-web/src/api/inventory/alert.ts
6）/home/administrator/jjx/jjx-web/src/views/purchase/plan/index.vue
7）/home/administrator/jjx/jjx-web/src/api/purchase/order.ts
8）/home/administrator/jjx/jjx-web/src/views/purchase/order/index.vue

新增文件：

1）/home/administrator/jjx/jjx-web/src/api/production/requirement.ts
2）/home/administrator/jjx/jjx-web/src/views/production/order/material-calculation/__tests__/requirementPreview.spec.ts
3）/home/administrator/jjx/jjx-web/src/views/inventory/alert/__tests__/demandPlan.spec.ts

权限：production:order:view/add、inventory:alert:view、purchase:order:add；沿用既有，不新建。

验证：Vitest 单 worker/1G/120 秒；vue-tsc 3072M/300 秒；覆盖 N02/N04/N05 前端确认幂等、日期/占用展示、采购建议跳转；不启动服务。

边界：E 只消费 D 的接口；前端不再保留可决定领料的独立算法；material-calculation/model.ts 中现有 allocations/quantity 计算要降级为展示/输入校验，权威结果来自后端。

### F 半成品生产库存闭环（OpenClaw）

现有文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/entity/ProductionOrder.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/ProductionOrderCreateDTO.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/ProductionOrderUpdateDTO.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/converter/ProductionOrderConverter.java
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/impl/ProductionOrderServiceImpl.java
6）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/ProductionBomResolver.java（只读调用；改动由 D 先）
7）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/controller/ProductionOrderController.java
8）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java
9）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/domain/InventoryInboundItem.java
10）/home/administrator/jjx/jjx-web/src/views/production/order/components/OrderFormDialog.vue
11）/home/administrator/jjx/jjx-web/src/views/production/order/index.vue
12）/home/administrator/jjx/jjx-web/src/views/production/order/composables/useProductionOrderCRUD.ts
13）/home/administrator/jjx/jjx-web/src/api/production/order.ts
14）/home/administrator/jjx/jjx-web/src/types/production/order.ts
15）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ProductionOrderBomInheritanceTest.java
16）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/ProductionSupplementEntryPointTest.java

新增文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/dto/ManufacturingWorkOrderCreateDTO.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/ManufacturingWorkOrderService.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/impl/ManufacturingWorkOrderServiceImpl.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/controller/ManufacturingWorkOrderController.java
5）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ProductionOrderMaterialOutputTest.java
6）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/inventory/ProductionMaterialInboundIdentityTest.java
7）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/SemiFinishedBatchTraceTest.java

条件文件（仅 357 迁移批准后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/357_production_output_identity.sql

前置：F 开工前必须证明 inventory_item MATERIAL 身份已通过用户批准的数据订正建立可核对映射；否则只做代码/测试准备，不执行入库验证，报告阻塞。

验证：mvn 定向 2G/180 秒；P01—P04、I01—I04、V02/V03 的真实事务测试；若改库存写入路径，npm run validate + check:stock:strict 五项全 0；未跑真实过账必须写“未验证”。

### G 作业规范与生产资料展示（Hermes）

现有文件：

1）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue
2）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/ProductSpecPreview.vue
3）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/WorkSpecPaper.vue
4）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/WorkSpecWorkflow.vue
5）/home/administrator/jjx/jjx-web/src/components/print/bomPrintLayout.ts
6）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomPrintPreview.vue
7）/home/administrator/jjx/jjx-web/src/views/production/order/components/WorkSpecUsageDialog.vue
8）/home/administrator/jjx/jjx-web/src/api/product/workSpec.ts
9）/home/administrator/jjx/jjx-web/src/api/production/workSpecUsage.ts
10）/home/administrator/jjx/jjx-web/src/components/print/__tests__/bomPrintLayout.spec.ts
11）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomDetailTable.vue（共享：C 先完成方案展示，G 再加制造层/打印展示）

新增文件：

1）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/ManufacturingProcessSheet.vue
2）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/__tests__/manufacturingProcessSheet.spec.ts

权限：engineering:product:spec:view / production:order:view；沿用既有。

验证：Vitest 单 worker/1G/120 秒；vue-tsc 3072M/300 秒；覆盖 PRODUCT 现有作业规范不回归、MATERIAL 展示 BOM+工序、打印不写数据；不启动服务。

### H 全流程集成验收（Codex-verifier）

只读验收，不改实施源码；依据 B—G 的实际提交、真实事务证据、37 场景逐项核对；卷材逐卷场景依赖 013 另行授权，未完成则在报告中列为未覆盖。

## 001 的 37 场景任务归属

A 负责把 37 场景映射到 B—G 并给出验收边界；下表“主导”指该场景的主要实现与证据责任，“参与”指必须一起回归的卡。H 对全部场景只读复核，不重复列。

| 场景 | 内容摘要 | 主导 | 参与 | 验收要点 |
|---|---|---|---|---|
| G01 | 背胶与离型纸同组不推断关系 | B | C、H | 保存/回读不产生制造引用；各自算料 |
| G02 | 旧BOM大料直接耗用不猜下层 | B | D、H | 旧行不改；需求仍按直接耗用 |
| G03 | 成品→组装半成品→基材片→大料多层 | D | C、G、F、H | 显式引用逐层展开；树/打印/工单一致 |
| G04 | 同物料产品A甲方案、产品B乙方案 | B | C、H | 主方案按产品隔离；切换不串 |
| G05 | 自引用/环/悬空/越权子BOM | B | D、H | 保存/批准/预览均拒绝并给路径 |
| D01 | 6模1基数300件=50不是51 | D | B、H | 原始比例计算；快照不权威 |
| D02 | 上层1片/下层6模1基数300片 | D | F、H | 下层50，不重复除6 |
| D03 | 模数100/2.5、0.5/1及非法值 | B | D、H | 正小数保存回读；0/负/空拒绝 |
| D04 | 损耗、最低投料与多层组合 | D | F、H | 损耗每层一次；最低投料不冒充产出 |
| D05 | 规格变化不改已确认模数 | D | C、H | 不按面积比自动改模数；缺尺寸阻断 |
| N01 | 需求300/库存200/占用60 | D | F、H | 可用140、补做160、只展开160 |
| N02 | 另有按期未占用供应60 | D | E、H | 补做100；既有工单原料不重复 |
| N03 | 两分支各100、公共库存140 | D | H | 合并分配一次，总缺口60，路径保留 |
| N04 | 供应晚于需求或被占用 | D | E、H | 不可用供应不覆盖；占用不重复减 |
| N05 | 并发确认/重复点击/重新试算 | D | E、H | 唯一键/行锁/回滚；不重复开单 |
| N06 | 同一半成品第二层和第四层 | D | H | 按依赖汇齐后净额，不重复/不漏 |
| U01 | 制造BOM保存后重开 | C | B、H | 方案/产出/投入/比例回显；规格不互覆盖 |
| U02 | 保存方案未保存父BOM | C | B、H | 方案独立落库；父引用仍草稿 |
| U03 | 采用后保存父BOM再打开 | C | B、H | 引用/版本/主方案一致 |
| U04 | 切换方案/下层返回/取消父BOM | C | B、H | 草稿不丢；父取消不删独立方案 |
| U05 | 只读/权限不足 | C | B、H | 前后端一致阻止；无副作用 |
| U06 | 主表/物料规格/工艺路线边界 | C | B、G、H | 不加供给列/路线编辑器；不改档案规格 |
| V01 | BOM V2生效后新需求 | B | D、H | 按生效规则与产品方案；可解释各层依据 |
| V02 | 生产中发现BOM错误 | F | D、H | 补退料/未做/已加工/供应释放闭环；不强制路径 |
| V03 | 历史工单/领料/批次追溯 | F | G、H | 按当时依据还原；不被当前BOM重算 |
| P01 | 补做160并供上层 | F | D、H | 工单→领料→合格入库→上层领用 |
| P02 | 仅合格150或分批 | F | H | 不虚增160；可领实际可用；缺口可见 |
| P03 | 再下层制造与原料批次追踪 | F | D、H | 原料批次→各层工单/产出→成品 |
| P04 | 完工/领料失败重试 | F | D、H | 事务回滚/幂等；无重复流水 |
| I01 | 195mm×50m整卷发9.75㎡只用12.8m | F | D、H | 实领/理论/实耗/余料分开 |
| I02 | 余卷退仓或留车间 | F | H | 去向/数量可追溯；库存不重复可用 |
| I03 | 红冲纠错/重算余额 | F | H | 原流水保留；反向记录；余额可重算 |
| I04 | 库存页面/报表/批次模型 | F | G、H | 收/发/结存三栏；批次不新增累计列 |
| C01 | 旧BOM无子BOM | B | D、H | 大料直接耗用/分组/审核/保存不回归 |
| C02 | 片/M/㎡或尺寸不全 | D | F、H | 有依据才换算；1PCS≠1M/㎡；无依据阻断 |
| C03 | 半成品新建/复制BOM/复制工单 | B | F、H | 身份/方案不串产品；复制按目标规则 |
| C04 | 物料单位订正与历史 | B | F、H | 历史保留原单位/依据；不静默重解释 |

覆盖核对：5+5+6+6+3+4+4+4=37，无遗漏；A 对全部场景给出归属，H 对全部场景做只读验收。

## 新表提案与替代评估

### 提案一：product_material_scheme

状态：提案，待用户拍板；A 未创建、未写迁移、未登记基线。建议归属 B；若 B 不动建表，则 003 的交付只能算“契约完成”，B 不能宣称主方案已落库。

为什么不能复用：

1）product.current_bom_id 只能表达产品级默认 BOM，不能按投入物料区分方案。
2）engineering_bom.is_current 是输出对象的当前版本，同一半成品被多个产品使用时无法表达“产品A甲、产品B乙”。
3）engineering_bom_item.sub_bom_id 是某一产品 BOM 版本上的实际引用，不是产品级主方案；版本复制/删除会让主方案漂移。
4）order_material_reserve 是数量占用，不是方案选择。
5）sys_config/JSON 无法提供 product_id+material_id 唯一约束、外键式校验和并发控制。

准入对照（CONVENTIONS §15.2）：独立关系对象（1）、会被需求/工单引用（2）、需要唯一约束（3）、需要变更留痕（5）、能证明无法复用（6），满足 5 条。

回退：删除表前先删除/归档所有引用；用户手工备份；不自动回滚。若用户不批准，B 的替代实现是只在 engineering_bom_item 上保存 sub_bom_id，不提供产品级主方案；U03/U04/G04 只能降级为“当前 BOM 版本内选择”，需用户明确接受该降级。

### 提案二（不新建表）：扩展 order_material_reserve

状态：提案，待与 358 迁移一起批准。相比新建分配表，优先扩列，原因是现有预占/占用/释放/超时任务已围绕该表，另建表会产生第二真源。字段与约束见“供需分配”节。若用户要求独立审计表，再另行评估；A 不建议。

### 不提案的部分

1）不新建“BOM 方案主表”之外的第二套 BOM 表；engineering_bom 继续作为唯一 BOM 主表。
2）不新建“需求结果表”保存试算；试算只读，确认后的分配落 order_material_reserve，工单落 production_order。
3）不新建“库存余额表”；库存仍以 inventory_stock/inventory_stock_item 派生，流水 append-only。
4）不新建“单位换算表”；本阶段用 BOM 行换算快照 + 物料尺寸；卷料批次长度依赖 013，不提前占表。

## 待批准、风险与未验证项

### 需要用户拍板

1）是否批准 product_material_scheme 新表（提案一）。不批准则主方案只能降级为 BOM 版本内选择。
2）是否批准 356/357/358 三段迁移及 product_id 可空、production_order 产出身份扩展。结构变更前必须由用户手工备份。
3）是否接受 MATERIAL 产出的工序来源为方案 BOM 的 process 行；若要求半成品拥有独立工艺路线/作业规范版本，需要另批路线/作业规范的物料身份扩展。
4）是否接受 inventory_item MATERIAL 身份的数据订正（建立 material_id → inventory_item_id 可核对映射）；不订正则 F 不能真实入库。

### 风险

1）最大风险是物料身份错位：7/7 BOM 物料在 inventory_item 中按 source_id 命中错误物料；当前零库存掩盖了问题。F 若直接 ensure(MATERIAL, material_id) 会把半成品入到错误物品上。必须先在只读环境产出映射清单，用户批准后另任务订正。
2）产品主方案若不做表，跨 BOM 版本/跨产品隔离无法保证；U03/U04/G04 会降级。
3）MATERIAL 产出的工序/作业规范若沿用产品域，会需要更大范围的身份扩展；本契约选择 BOM process 行作为最小闭环，可能与用户对“路线记录加工顺序”的期望有差距。
4）迁移顺序错误（先改 NOT NULL 再回填、或先部署不兼容代码）会造成写入失败；必须按“加列→回填→双写→改约束”的顺序。
5）前端 material-calculation 现有分配算法若保留，会与后端净需求形成第二权威；E 必须移除/降级。
6）013 卷材逐卷余量未完成，I01/I02 的卷料实际长度只能阻断或使用批次静态数据；不得声称所有余卷场景已通过。

### 未验证项（不得当成已完成）

1）没有执行任何迁移；356/357/358 只是建议编号与内容，实际执行时按账本重取。
2）没有创建 product_material_scheme；没有登记 model-baseline/清理归属。
3）没有运行后端 mvn、前端 Vitest、vue-tsc；因为本任务只写文档，没有业务代码改动。
4）没有运行真实库存过账、真实工单/领料/入库、真实需求计算；所有 JSON 是契约示例，不是运行结果。
5）没有启动/停止/重启任何服务；没有调用真实 HTTP 接口。
6）现场核查使用只读 jjx_ro；本次连接成功，未出现 001 所记的认证失败；但不代表业务库写入路径已验证。
7）没有修改 AGENTS.md、CONVENTIONS、multi-agent-protocol；没有改任务 002/004—010/013 的状态。
8）inventory_item 身份错位的根因未继续追溯（历史导入/旧 MES 映射），只确认结果；根因排查需另任务。

## 现场证据与复核命令

本任务证据目录：/home/administrator/jjx/.tmp/bom-dispatch/。已保存 git 基线、代码行号摘录；后续可追加 SQL 输出。

关键复核命令（只读）：

```bash
cd /home/administrator/jjx
git rev-parse HEAD
git status --short
MYSQL_PWD=jjx_ro_2026 mysql -h127.0.0.1 -u jjx_ro -N -B jjx_erp_db -e "SELECT config_value FROM sys_config WHERE config_key='ops.schema.version'"
MYSQL_PWD=jjx_ro_2026 mysql -h127.0.0.1 -u jjx_ro -t jjx_erp_db -e "SELECT item_id,module_qty,base_qty,quantity,actual_issue_qty FROM engineering_bom_item WHERE item_id=1"
MYSQL_PWD=jjx_ro_2026 mysql -h127.0.0.1 -u jjx_ro -t jjx_erp_db -e "SELECT bi.material_id,m.material_code,i.item_code,i.item_name FROM (SELECT DISTINCT material_id FROM engineering_bom_item WHERE material_id IS NOT NULL) bi LEFT JOIN inventory_material m ON m.material_id=bi.material_id LEFT JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=bi.material_id"
```

代码复核定位：

1）EngineeringBomServiceImpl.java：214 创建、899 setDefault、1007 copy、1154 approve、1299 单位用量、1315 应用料、1443/1446 ≥1 校验。
2）InventoryOutboundServiceImpl.java：999/1405 已领量键、1449 batchDemand、1463 sumPickedByMaterial、667/967/994/1239/1253/1403 make 跳过。
3）InventoryItemServiceImpl.java：26 ensure 按 item_type+source_id 回查/插入。
4）InventoryInboundServiceImpl.java：2675/2707 createFromProduction、3398/3692 固定 PRODUCT ensure。
5）ProductionOrderServiceImpl.java：84 创建、872 校验、1209 计划转工单、1275/1291 产品/BOM 继承。
6）ProductionBomResolver.java：21 resolve、22 空 bomId 回退、31 按 bomId 取数。
7）BomFormDialog.vue：670 doSave、697 编辑、700 新增。
8）material-calculation/model.ts：现有前端需求/分配计算。

## 接续说明

本卡完成后只把 dev-20261111-003 置为待审核（status=2），提交本卡两份文档（本文件与 INDEX 登记），推送 origin dev；不自行验收、不派 B 至 G、不改其他任务状态、不启动任何 agent/服务。Codex 独立验收通过后，才按 002 的波次派 B；B 通过后再 C/D；C/D 通过后 E/F；E 完成且 C/D 通过后再 G；H 最后只读验收。

若用户先批准新表/迁移，B 开工前仍需：确认 356 编号、手工备份、批准记录写进 model-baseline、清理归属登记、检查同文件在制。任何一项缺失就停相关部分，不把“文档契约”当成“已经实施”。
