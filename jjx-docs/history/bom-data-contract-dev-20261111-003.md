# 多层BOM-A：数据模型与接口契约

任务：dev-20261111-003（总任务 dev-20261111-002 子任务 A）。执行者：OpenClaw。验收者：Codex（独立验收）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：A 阶段交付，只读核查与契约设计；没有实施 B 至 G，没有写业务源码、没有执行迁移、没有新增表、没有改库、没有启动或重启服务。
本卡写入白名单：jjx-docs/history/bom-data-contract-dev-20261111-003.md、jjx-docs/history/INDEX.md、.tmp/bom-dispatch/（本任务证据，gitignored）；其余业务源码、迁移、库和运行态只读。

修订：2026-11-11 v2（退回修复，任务仍是 dev-20261111-003）。本版按 Codex 预审六点逐条修正：路线边界改为复用现有 engineering_routing / product_work_spec_version，不由 BOM 行生成工序；主方案新表重新做必要性评估并给出 JSON 映射的并发/版本/保存取消语义；库存身份异常补齐可复核原始输出；精确比例改为有理数精确约简；数据分配改为请求级幂等 + 多行唯一键；迁移编号取消预占，G 补后端快照归属，状态标注复用枚举，并删除文档中的数据库口令。原 A 版结论保留可核对处，被本版修正的旧表述以“v2 修正”覆盖，不另建卡、不派后继。

本版证据文件（均在 .tmp/bom-dispatch/，gitignored）：evidence-ddl-20261111.txt（14 张相关表 SHOW CREATE TABLE 原文）、evidence-inventory-identity-20261111.txt（身份比对原始 SQL 与输出）、evidence-schema-20261111.txt（迁移账本与目录现状）、code-refs-20261111.txt（代码行号摘录）。

Codex 六点与本版对应：

| 退修点 | 本版处理位置 | 结论 |
|---|---|---|
| 1 路线边界 | “MATERIAL 路线与作业规范接入”、缺陷 12/13、G1/G2、U06 | 工序只由 engineering_routing_item 生成；BOM 行 process_id 仅展示；BOM 主表不加路线编辑器 |
| 2 新表必要性 | “产品主方案：存储方案与必要性评估”、提案一/二 | A/B/C 三方案均给出并发、版本隔离、保存/取消；C 是条件必要提案，不把未确认假设当既定要求 |
| 3 库存身份证据 | 缺陷 7、风险 1、evidence-inventory-identity-20261111.txt | 原始 SQL 与输出已存；比对表/键/行数明确；区分 BOM 记录、无库存流水、仍有历史单据引用 |
| 4 精确比例 | “比例与精度（有理数精确约简）”、单位取整策略、净需求算法 | 有理数 num/den 精确约简；终点/确认批量才取整；给多层抵消反例；理论/批量/实领分列；安全库存复用现有口径 |
| 5 分配幂等 | “供需分配：请求级幂等 + 多来源分配行” | 请求头 UNIQUE(request_key) + 分配行 UNIQUE(request_id, allocation_line_no)；空 key 兼容明确 |
| 6 迁移/G/枚举/口令 | “迁移兼容与回退”、G1/G2、状态枚举真源、现场证据命令 | 不预占 356/357/358；M1/M2/M3 按依赖；G 分后端快照+前端；状态复用枚举；文档/证据无口令 |

## 交付边界与结论先行

本文件交付四类内容：一是实际 DDL 与现有入口的现场核查；二是优先复用表的数据模型与字段约束、迁移兼容和回退设计；三是方案保存、采用、父保存边界与接口契约；四是 B 至 G 的精确文件白名单、共享作者、入口保存矩阵和 001 的 37 场景归属。

结论先行：

1）现有 BOM 主表不能直接承载“MATERIAL 产出”。engineering_bom.product_id 为 NOT NULL，生产订单 production_order.product_id 也为 NOT NULL，库存成品入库固定走 PRODUCT 身份；必须增加显式产出身份，不能继续用 product_id 冒充所有半成品。
2）现有 BOM 明细的 parent_material_id 只是同一 BOM 内的分组/父子行，不能复用为“由哪个子 BOM 制造”的关系。跨 BOM 关系必须新增显式引用 sub_bom_id，并做图环路校验。
3）现有精度链路会把 1/6 先存成 0.1667，再乘 300 得 50.01，整批向上取整成 51；正确结果应为 300×1÷6=50。新契约要求保存模数、基数原始值，除法只在需求计算末次执行。
4）现有库存物品身份与物料主数据存在实际错位：7 个 BOM 引用物料按 inventory_item(item_type='MATERIAL', source_id) 都能命中一行，但 7/7 行的 item_code/item_name 与 inventory_material.material_code/material_name 不一致；按 item_code 直接匹配 291 个物料为 0 行命中，1562 个 inventory_item 中有 1271 个按 source_id 找不到物料。当前 inventory_stock、inventory_stock_item、inventory_transaction、inventory_inbound_item、inventory_outbound_item 均为 0 行，但这只证明“没有库存流水”，不能证明所有历史单据完全未污染：inventory_alert_log 有 20 行引用 material_id 109—113，production_order 有 2 行绑定含 material_id 349 的 BOM，sales_sample_bom 有 5 行引用 109—113。F 阶段直接按 material_id 调 ensure(MATERIAL, material_id) 会拿错身份，必须先完成身份核对/映射，不能把旧 inventory_item 当新库使用。原始 SQL 与输出见 evidence-inventory-identity-20261111.txt。
5）多方案必须按“输出对象 + 方案 BOM”建立；产品主方案是否必须跨 BOM 版本独立保存，001 没有明确确认，因此不能把“跨版本独立生命周期”当成既定业务要求来排除 BOM 内存储或产品级 JSON 映射。v2 保留 product_material_scheme 作为推荐提案，同时给出 product 级 JSON 映射和 BOM 行上下文两种可实现替代，并分别说明并发、版本隔离、保存/取消语义；最终按用户对“跨版本持久 + 独立审计”的选择定存储，不强行降级。
6）新表提案保留两项：product_material_scheme（产品主方案）与 requirement_confirm_request（请求级幂等头，若采用“请求头 + 多分配行”策略）。两表均未批准、未创建；替代方案和理由见“新表提案与替代评估”。需求分配优先扩展现有 order_material_reserve；若分配不绑定销售订单，则用 production_requirement_allocation 承载生产分配，但不复制库存余额/流水，不形成第二套库存真源。
7）B 至 G 的文件白名单、共享作者和 37 场景归属已给出；共享文件一律先 B 后 C/D，先 C 后 G，先 D 后 E；F 的 production_order 输出身份与 D 的 MATERIAL 净需求存在运行时依赖，迁移与派工按依赖排序，不按固定编号或卡序硬排。

## 现场核查环境与基线

执行前已把 git 状态存档到 .tmp/bom-dispatch/git-status-baseline-20261111.txt，基线为：HEAD 5a329faf42da8c86d2dfc9909e0dee307a40d4b3，分支 dev，工作区干净。

数据库只读核查使用本机已配置的只读账号 jjx_ro（库 jjx_erp_db@127.0.0.1:3306），连接通过仓库外、权限 0600 的本地 defaults 文件完成，文档和证据不保存口令。MySQL 版本 8.4.10-0ubuntu0.26.04.1；sql_mode 含 ONLY_FULL_GROUP_BY、STRICT_TRANS_TABLES、NO_ZERO_DATE 等严格模式。2026-11-11 10:32 CST 观察：迁移账本 sys_config['ops.schema.applied'] 最大号 355，migrations/applied 归档最大号 355，待办目录待执行 0、已记账待归档 0；这是观察值，不是预留号。迁移编号在实际编写时重新核对“账本最大号与（待办+归档）目录最大号的较大者 + 1”，并按依赖顺序执行；本文件不再使用 356/357/358 之类的固定编号。

本文件中的“现有”均指上述 HEAD 与本次只读查询结果；“建议/提案/契约”不是已实现能力，也不是已批准的新表。

### 现场数据规模

1）engineering_bom：4 行。bom_id=1、2 的 product_id=1；bom_id=3 的 product_id=3（测试中间件产品身份）；bom_id=4 的 product_id=2（测试成品）。bom_id=1 为已批准且 is_current=1；bom_id=2 为草稿且 is_current=0，其明细仍为草稿行；bom_id=3、4 为已批准但 is_current=0。
2）engineering_bom_item：12 行。item_id=1、4、9 的 module_qty/base_qty/quantity 为 6/1/0.1667；item_id=5、10 为 3/1/0.3333。item_id=13 属于 bom_id=4（TOP），source_type=make、material_id=349，但 material_code 为空，且没有任何 sub_bom 引用；bom_id=3（SEMI）没有 item 13，也不存在 output 为 material 349 的 BOM。v2 修正 A 版“item_id=13 没有字段指向 bom_id=3”的含糊表述。
3）inventory_material：291 行；inventory_item：1562 行（全部 item_type=MATERIAL）；inventory_stock、inventory_stock_item、inventory_transaction 均为 0 行；inventory_inbound_order/item、inventory_outbound_order/item 均为 0 行；production_order 2 行；purchase_order_item 0 行；inventory_alert_log 20 行；sales_sample_bom 5 行。
4）当前没有真实库存和库存流水；按 source_id 比对，7 个 BOM 引用物料 7/7 命中但 code/name 全不一致；1562 个 item 中 1271 个按 source_id 找不到物料，按 item_code 对 291 个物料 0 命中。零库存只能证明“没有库存流水”，不能证明历史单据完全未污染：20 行 alert_log、2 张 production_order、5 行 sales_sample_bom 仍引用这些 material_id。原始 SQL 与表格输出见 evidence-inventory-identity-20261111.txt。

### 实际 DDL 摘要

以下仅摘录与本契约相关的字段，不替代 SHOW CREATE TABLE 原文；14 张相关表的完整 SHOW CREATE TABLE 原文已保存到 .tmp/bom-dispatch/evidence-ddl-20261111.txt（不含口令），可逐表复核。

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
| process_id/process_name | bigint/varchar NULL | 标准工序引用；仅项目展示/参考，不是 MATERIAL 工单工序来源；工序只由 engineering_routing_item 生成 |
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

engineering_routing / engineering_routing_item（工艺路线，001 已定“路线管顺序”）：

| 表/字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| engineering_routing.routing_id | bigint PK | 路线身份；production_order.routing_id 引用 |
| engineering_routing.product_id | bigint NOT NULL | 现状只能表达产品路线；MATERIAL 路线无法绑定 |
| engineering_routing.routing_code/routing_version | varchar NOT NULL，UNIQUE(routing_code,routing_version) | 版本身份；路线编码+版本唯一 |
| engineering_routing.is_current | tinyint default 1 | 当前版本；无数据库级“每输出唯一当前”约束 |
| engineering_routing.approve_status | tinyint default 1 | 复用 ProductEnums.RouteStatus / ApproveStatusEnum：1草稿/2待审批/3已批准/4已驳回；生产取数只认 3 |
| engineering_routing.parent_routing_id | bigint NULL | 父版本链；复制新版本沿用 |
| engineering_routing_item.routing_id | bigint NOT NULL，FK | 路线明细归属 |
| engineering_routing_item.process_id | bigint NULL，FK engineering_standard_process | 标准工序；**这是工序唯一来源** |
| engineering_routing_item.process_name/major_category/process_order/workflow_seq | varchar/int | 自定义工序、组内顺序；生产工序按 (workflow_seq, process_order) 排序 |
| engineering_routing_item.parent_id/group_id/group_order | bigint/int | 父行=工序、子行=组合作业；生成 execution 时合并为 jobs 快照 |

product_work_spec_version / product_work_spec_item / work_spec_usage（作业规范，001 已定“作业规范管展示”）：

| 表/字段 | 类型/约束 | 现状与契约影响 |
|---|---|---|
| product_work_spec_version.id | bigint PK | 发布版本身份；production_order.work_spec_version_id 引用 |
| product_work_spec_version.product_id | bigint NOT NULL，UNIQUE(product_id,version_no) | 现状只能按产品发布；MATERIAL 无法拥有发布版本 |
| product_work_spec_version.version_no | varchar(20) NOT NULL | 产品内版本号 |
| product_work_spec_version.status | varchar(20) default PUBLISHED | PUBLISHED/RETIRED；历史读取按持久化 ID，不按“最新”推断 |
| product_work_spec_item.spec_version_id | bigint NOT NULL | 发布条目归属 |
| product_work_spec_item.resource_type/source_id/source_version_id | varchar/bigint | SPEC_JSON/BOM/ROUTING/DRAWING；BOM/路线目前存引用+版本号，不复制整棵快照 |
| product_work_spec_item.snapshot_json/content_hash | mediumtext/char(64) | 规范正文完整快照；BOM/路线引用与哈希；发布后不可变 |
| work_spec_usage.work_order_id/spec_version_id | bigint NOT NULL | 工单实际使用版本；换版区间记录，历史按 ID 读取 |
| work_spec_usage.qty_from/qty_to/start_time/end_time | decimal/datetime | 生效区间；换版追溯 |

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
5）make 行没有下层引用且被领料直接跳过。InventoryOutboundServiceImpl 667、967、994、1239、1253、1403 行均有 if (!"buy".equals(sourceType)) continue；item_id=13 属于 bom_id=4（TOP），为 make、material_id=349，但无 sub_bom 关联；bom_id=3（SEMI）没有该行，也不存在 output 为 material 349 的 BOM。新契约必须把“make 但无显式下层引用”作为可定位错误，不能静默当采购或静默漏算。
6）产出身份只支持 PRODUCT。engineering_bom.product_id NOT NULL；ProductionOrderCreateDTO.productId @NotNull；ProductionOrderServiceImpl.validateOrderData 872 行只校验 productId；InventoryInboundServiceImpl.createFromProduction 3398 行固定 ensure(PRODUCT, prodOrder.getProductId())，3692 行同样固定 PRODUCT。MATERIAL 产出的工单和入库没有闭环字段。
7）物料身份错位是实际存在的，且本轮已保存原始 SQL 输出。比对链：engineering_bom_item.material_id（12 行、7 个 distinct）→ inventory_material.material_id → inventory_item(item_type='MATERIAL', source_id)；比较键为 material_code/material_name 与 item_code/item_name。结果：7/7 全部 MISMATCH；示例 material 109=RM000105 PC-800（单位 M），inventory_item 109=MTR202608130110 3.0mm透明PC(贯昌）（单位 PCS）。覆盖：inventory_item 1562 行，按 source_id 有 291 行能命中物料、1271 行找不到物料；按 item_code 对 291 个物料 0 行命中。原始查询、计数和逐行结果见 .tmp/bom-dispatch/evidence-inventory-identity-20261111.txt。当前 stock/stock_item/transaction/inbound/outbound 均为 0 行，只能说明没有库存流水；inventory_alert_log（20 行）、production_order（2 行，BOM 含 349）、sales_sample_bom（5 行）仍引用这些 material_id，不能据此认定所有历史单据完全未污染。F 阶段若直接 ensure(MATERIAL, material_id)，会返回错误的库存物品。
8）领料“已领量”统计键不一致。InventoryOutboundServiceImpl 999、1405 行用 pickedMap.getOrDefault(bomItem.getItemId())，而 sumPickedByMaterial 1463/1474 行按 material_id 聚合。除非 item_id 恰好等于 material_id，否则已领量被当作 0，剩余需求会重复计算。该缺陷应在 D 阶段一并修正，并以真实数据回归。
9）BOM 类型存在双口径。DB bom_type 存 'manufacturing' 字符串，Java ProductEnums.BomType 是 1/2 枚举，现有代码未统一；新增产出类型不得复用 bom_type，应单独定义 output_type。
10）生效窗口未参与生产取数。engineering_bom.effective_date/expiry_date 已存在，但 ProductionBomResolver 只校验 bom_id、product_id 和 approve_status=3，不校验生效/失效日期；新契约要明确“何时校验”和“历史工单不重算”。
11）上层领料只取 buy 行，make 行即使已有半成品库存也不会被领用。InventoryOutboundServiceImpl 667/967/994/1239/1253/1403 行均对非 buy 行 continue；这与 001 的 P01“成品工单领原有140片与新产出合格片”冲突。新契约必须允许 make 行按可用库存/已有生产供给领用，但不得在领料时重复生成下级工单。
12）路线与作业规范都只有产品身份。engineering_routing.product_id、product_work_spec_version.product_id 均 NOT NULL；production_order.routing_id/work_spec_version_id 只能指向产品路线/产品规范。半成品 MATERIAL 工单没有可绑定的路线/规范，F 若继续用产品身份就是拿别的产品路线冒充，不能作为契约。必要工程扩展是给两者增加 output_type/output_id（MATERIAL 时 output_id=material_id），不是从 BOM 行 process_id 生成工序，也不是在 BOM 主表加路线编辑器。
13）BOM 行 process_id/process_order 只是项目展示属性，不是制造工序来源。现有生产工序只由 ProductionOrderServiceImpl.generateOperationExecutions(routingId,...) 读取 engineering_routing_item 生成；BOM 的 process_id 未被该方法使用。把 BOM 行当工序来源会与 001“路线管顺序、作业规范管展示”冲突，v2 删除该路径。
14）已批准 BOM/路线不可编辑，历史可依赖版本不变性。EngineeringBomServiceImpl.updateBomWithDetail 在 approve_status 非草稿/驳回时抛“BOM已审批，不允许修改”；EngineeringRoutingServiceImpl 125 行对不可编辑状态拒绝更新。因此已批准版本是稳定历史依据；新增/改版走 copyAsNewVersion，不原地改。作业规范发布版本 PUBLISHED 亦不重写，work_spec_usage 记录工单实际版本。v2 的 G 后端快照读取这些持久化事实，不再只做前端拼装。

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
2）生产订单同样增加 output_type + output_id（统一用 output_id，不新增 output_material_id；另保留 product_id 兼容列）。PRODUCT 工单行为不变；MATERIAL 工单不再要求 product_id 非空。
3）MATERIAL 产出必须有 inventory_material 档案，status=1；PRODUCT 产出必须有 product 档案。两种身份不能互相顶替。
4）库存身份仍由 inventory_item 唯一表承载：PRODUCT 产出 ensure(PRODUCT, product_id)，MATERIAL 产出 ensure(MATERIAL, material_id)。但 F 开工前必须完成“inventory_item MATERIAL 身份与 inventory_material 的对齐/映射”，否则 ensure 会命中错误行；该对齐是数据订正，必须单独授权和备份，A 不执行。
5）半成品的“产出物料”就是 MATERIAL；不强制新建 product 记录。001 已定“路线管顺序、作业规范管展示”，所以半成品必须能有自己的路线和作业规范版本：给 engineering_routing、product_work_spec_version 增加与 BOM 同形的 output_type/output_id（MATERIAL 时 output_id=material_id，product_id 可空），复用现有路线明细、版本、批准/发布、工单绑定与发布快照机制。这是已定业务下的必要工程扩展，不需要重新确认“半成品是否要有路线/作业规范”；也不在 BOM 主表加路线编辑器，更不从 BOM 行 process_id/行顺序生成工序。

替代方案（不推荐，仅记录）：给每个半成品建一个 product 记录，用 product_id 作为执行身份，再用 inventory_material.product_id 做桥接，库存入库仍落 MATERIAL 身份。该方案能复用现有路线/作业规范，但会污染产品档案、需要 product_role 标记、且 product_id 与 material_id 双写容易再次错位。v2 不采用；改为一等 MATERIAL 身份扩展。

### MATERIAL 路线与作业规范接入（已定业务的工程扩展）

1）engineering_routing 增加 output_type varchar(20) NOT NULL DEFAULT 'PRODUCT'、output_id bigint NULL。PRODUCT 行 output_id=product_id；MATERIAL 行 product_id=NULL、output_id=material_id。迁移先加列、回填 PRODUCT/product_id，再允许 MATERIAL 写入；不改 routing_item 的工序语义。
2）engineering_routing 的当前版本与生命周期：沿用 is_current + approve_status（ProductEnums.RouteStatus）。同一 output_type+output_id 只允许一个 is_current=1、approve_status=3 的当前路线；数据库用生成列 route_scope（is_current=1 时 output_type:output_id，否则 NULL）+ 唯一索引兜底，服务层再校验并返回可定位冲突。路线批准后不可原地改，改版走既有 copyAsNewVersion。
3）product_work_spec_version 增加 output_type/output_id；MATERIAL 时 product_id=NULL、output_id=material_id。发布/退役沿用 status=PUBLISHED/RETIRED；发布条目仍写 product_work_spec_item，BOM/ROUTING 引用改为按 output 身份解析。作业规范发布后不可变，历史按 spec_version_id 读取。
4）工单绑定：
   A) 新建/计划转工单时，按工单 output_type+output_id 解析路线和作业规范；显式 routingId/workSpecVersionId 优先，否则取该输出当前已批准路线/当前已发布规范。
   B) MATERIAL 工单必须绑定 MATERIAL 路线和 MATERIAL 作业规范；缺任一返回 MATERIAL_ROUTING_NOT_FOUND / MATERIAL_WORK_SPEC_NOT_PUBLISHED，阻断创建，不用产品路线/产品规范顶替。
   C) production_order 继续持久化 routing_id/routing_code、work_spec_version_id，并新增 routing_version 快照列（若现有路由编码已带版本，则记录 routing_version 字段值）；生成工序时只读 engineering_routing_item，不读 BOM 行。
5）工序生成：沿用 ProductionOrderServiceImpl.generateOperationExecutions(routingId,...) 的现有逻辑（按 workflow_seq、process_order 排序；父行生成 execution，子行合并 jobs）。BOM 只决定用料，不决定工序；BOM 行 process_id 仅作展示/参考，禁止作为工序来源。
6）作业规范展示：沿用 product_work_spec_item 的 SPEC_JSON/BOM/ROUTING/DRAWING 条目；MATERIAL 规范的展示入口与产品规范一致，只读发布版本；G1 后端按工单持久化 ID 组装快照。
7）生命周期与历史：路线/规范版本批准或发布后不可改；工单创建时快照 routing_id、routing_version、work_spec_version_id，历史工单不因当前版本切换重算；换版走 work_spec_usage 的工单区间，不覆盖历史绑定。

### BOM、方案与版本

1）engineering_bom 仍是方案主表。同一输出对象可有多个方案，每个方案一行主表 + 多行明细；方案身份是 bom_id，方案编码是 bom_code，方案版本是 bom_version/version。
2）engineering_bom.product_id 只在 output_type=PRODUCT 时必填；MATERIAL 方案必须 output_type=MATERIAL、output_id=material_id、product_id=NULL。
3）同一 output_type+output_id 的“输出默认方案”仍可用 is_current 表达，但产品上下文的主方案不能只看它。若用户批准新表，产品主方案由 product_material_scheme 按 product_id × material_id 保存，is_primary=1 唯一；若不批准新表，则用 product 级 JSON 映射或 BOM 行上下文实现，语义与代价见“新表提案与替代评估”，不得把任一方案说成“技术上不可行”。
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

### 产品主方案：存储方案与必要性评估

语义：产品 A 对投入物料 349 选方案甲，产品 B 选方案乙；A 的选择不影响 B。001 的 G04 只要求“按产品隔离”，没有确认主方案必须跨 BOM 版本独立保存、必须逐条审计、必须被其他模块独立引用。因此不能把“跨版本独立生命周期”当成既定业务要求，也不能用“JSON 无并发控制/无法按物料查”这种不成立的论点排除 BOM 内存储。

三种可实现方案（均未实施）：

A）product 级 JSON 映射（不新建表）

1）在 product 增加 material_scheme_json JSON、scheme_revision INT；文档形如 {"349":{"bomId":124,"isPrimary":true},"351":{...}}。亦可存数组并由服务层转 map。
2）并发：保存父 BOM 时在同一事务 SELECT ... FOR UPDATE 锁 product 行；写入用乐观锁 UPDATE product SET material_scheme_json=?, scheme_revision=scheme_revision+1 WHERE product_id=? AND scheme_revision=?；影响行数 0 → 409 CONCURRENT_MODIFICATION，提示重新加载。行锁与乐观锁都在现有 MySQL/服务事务内可实现。
3）版本隔离：映射以 product_id+material_id 为键，天然跨 BOM 版本；BOM 版本复制不会自动改映射；需要换方案时显式更新。若产品复制或方案作废，服务层校验 bom_id 存在且 output 匹配，不自动跟随“最新版本”。
4）保存/取消：采用 token 仍不落库；只有父 BOM 显式保存才在同一事务写 JSON；父 BOM 取消/关闭不写，已保存值不变；方案独立保存不影响父 BOM。
5）查询：按物料查用 JSON_EXTRACT/JSON_TABLE；高频物料可加生成列+索引，或由服务层缓存；数据量是产品×物料选择，不是全库库存，无需把 JSON 说成“不能查”。代价是无数据库级 (product_id,material_id) 唯一/外键，只能服务层校验；审计只有 product 行 update_by/update_time，逐物料变更历史需另存 JSON 历史数组或审计日志。
6）适用：如果用户只要求“按产品隔离、跨版本保持”，JSON 映射是更小改动，不需要新表提案。

B）BOM 行上下文（不新建表）

1）在 engineering_bom_item 增加 is_primary TINYINT；主方案=某产品当前 BOM 版本中该 material 行的 sub_bom_id + is_primary。
2）并发：保存父 BOM 时按 bom_id 行锁/乐观 revision 校验；同一 BOM 内唯一主方案可由服务层校验，数据库可用生成列 (bom_id,material_id,is_primary=1) 部分唯一（MySQL 生成列+唯一索引）兜底。
3）版本隔离：选择随 BOM 版本走；复制新版本会复制/重选，跨版本不自动保持；产品有多个 BOM 版本时，“当前主方案”必须绑定某一版本，不能跨版本唯一。若业务接受“版本内选择”，这是最简单的实现。
4）保存/取消：父 BOM 保存即持久化；取消不写；方案独立保存仍需独立 BOM。
5）适用：如果主方案只需在某个 BOM 版本内生效，不要求跨版本稳定，BOM 行上下文足够，且不需要新表。

C）product_material_scheme 新表（推荐提案，但非唯一可行）

1）独立关系对象，UNIQUE(product_id,material_id,bom_id) + 生成列唯一主方案；逐行 revision、create/update 审计；可被需求/工单直接引用。
2）并发：唯一键+行锁；主方案切换在同一事务内先清旧主再写新主，唯一索引兜底；冲突返回可定位错误。
3）版本隔离：product_id+material_id 为键，跨 BOM 版本稳定；bom_id 指向具体方案版本，不自动跟随最新。
4）保存/取消：父 BOM 保存同事务写选择；采用 token 不落库；取消不写；方案作废时若仍被引用则拒绝或要求先解除。
5）代价：新表需用户批准、迁移、model-baseline approvedNewTables、清理归属、权限/审计登记；若用户不确认“跨版本独立 + 逐条审计”，它不是必须项。

对照表：

| 方案 | 并发控制 | 版本隔离 | 保存/取消 | 查询/约束 | 结论 |
|---|---|---|---|---|---|
| A product JSON | 行锁 + scheme_revision 乐观锁 | 产品级跨版本稳定 | 父保存同事务写；取消不写 | JSON_EXTRACT/JSON_TABLE；无 DB 唯一/外键，服务校验 | 可行，改动最小；无逐物料审计 |
| B BOM 行上下文 | bom 行锁/版本校验 + 生成列唯一 | 版本内，不跨版本 | 父保存写；取消不写 | 可按 bom_id+material_id 查；跨版本唯一困难 | 可行，适合版本内选择 |
| C 新表 | 唯一键 + 行锁 + revision | 产品级跨版本稳定 | 父保存同事务写；取消不写 | 唯一/索引/审计完整 | 推荐提案；需批准，不是唯一解 |

建议 DDL（仅方案 C，提案，未执行、未批准；若用户选 A/B 则不建表）：

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

结论：C 是推荐提案，不是“JSON 不可行”推导出的唯一解。若用户只确认“按产品隔离”，A 可落地且无需新表；若用户确认“跨版本独立 + 逐条审计 + 被其他对象引用”，C 的独立生命周期才有必要性。B 只适合版本内选择。A/B/C 都不改变“产品 A 不串产品 B”的既定语义。

### 供需分配：请求级幂等 + 多来源分配行

需求计算的“结果”不落库，确认后的“分配”落库。优先复用 order_material_reserve，避免第二套库存/预留表；但 v1 的 UNIQUE(request_key) 有一个实现错误：一次 confirm 产生多条分配行时，每行都带同一个 request_key，第二行就会被唯一索引挡住。v2 改为“请求头唯一 + 分配行按请求内行号唯一”。

方案（推荐，需新表批准）：requirement_confirm_request + order_material_reserve 扩列。

1）请求头表 requirement_confirm_request（提案，未创建）：

```sql
CREATE TABLE requirement_confirm_request (
  request_id BIGINT NOT NULL AUTO_INCREMENT,
  request_key VARCHAR(64) NOT NULL COMMENT '幂等键，调用方生成',
  request_hash CHAR(64) NOT NULL COMMENT '规范化请求体 SHA-256，防同键不同体',
  request_status TINYINT NOT NULL DEFAULT 0 COMMENT '0处理中/1成功/2失败',
  preview_version VARCHAR(40) DEFAULT NULL,
  result_json MEDIUMTEXT DEFAULT NULL COMMENT '成功结果快照，重复请求原样返回',
  create_by VARCHAR(50) NOT NULL DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (request_id),
  UNIQUE KEY uk_rcr_request_key (request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

2）order_material_reserve 扩列：request_id BIGINT NULL、request_key VARCHAR(64) NULL、allocation_line_no INT NULL、source_bom_item_id BIGINT NULL、source_path VARCHAR(500) NULL、supply_type VARCHAR(20) NULL（STOCK/PURCHASE/PRODUCTION，空=旧预留）、supply_id BIGINT NULL、required_date DATE NULL、allocated_quantity DECIMAL(12,4) NULL、demand_order_id BIGINT NULL。
3）唯一键与索引：
   A) UNIQUE(request_id, allocation_line_no)：同一请求内行号唯一，允许多条分配行；不同请求可复用 line_no。
   B) UNIQUE(request_key) 只放在请求头表，不放在分配行表；分配行的 request_key 只建普通索引。
   C) KEY(order_id, material_id, supply_type, supply_id)、KEY(source_bom_item_id)。
   D) request_id/request_key/allocation_line_no 允许 NULL：旧行保持 NULL，MySQL 唯一索引对多个 NULL 不冲突；旧预占/占用行为不变。
4）事务与并发：
   A) confirm 开始先 INSERT requirement_confirm_request(request_key, request_hash, request_status=0)。唯一键保证同一 request_key 只有一个事务能插入成功。
   B) 插入冲突时读取已有行：status=1 → 直接返回 result_json，duplicate=true，不新增工单/分配；status=0 → 返回 409/Retry-After 或短事务等待后重读；status=2 且 request_hash 相同 → 允许重试，hash 不同 → 409 IDEMPOTENCY_KEY_REUSED。
   C) 插入成功后在同一事务创建分配行和补做工单，逐行 allocation_line_no 由请求 items 的稳定 clientLineId/顺序生成；同一请求内不得重复。全部成功后更新请求头 status=1、result_json=最终响应，提交。
   D) 任一步失败回滚整个事务（请求头插入也回滚），调用方可用同一 request_key 重试；若需要保留失败痕迹，用独立短事务写 status=2，不写半套分配/工单。
5）响应：返回 requestId/requestKey、duplicate、workOrders[]、allocations[]；重复请求返回首次成功结果，不重复占用库存、不重复开单。
6）空 key 历史兼容：旧行 request_id/request_key/allocation_line_no 全 NULL，不参与新幂等逻辑；不做全表回填。新接口拒绝空字符串/空 key，要求非空且长度≤64；若历史脏数据是空字符串，迁移时只把空字符串改为 NULL（需单独数据订正授权），不建 UNIQUE(request_key)。
7）不采用“把 UNIQUE(request_key) 放在分配行上”的做法：它只能防单行重复，不能防请求级重复，且会阻止一次确认的多来源分配。若用户不批准请求头新表，退路是 (request_key, allocation_line_no) 唯一 + 应用层 GET_LOCK('req:'+key) 串行化；该退路依赖连接池/会话锁，健壮性弱于请求头表，不作为默认。
8）表边界：现有 order_material_reserve 的 order_id/order_no 语义是“销售订单预占”，不是通用生产需求分配。若 confirm 的分配行不绑定销售订单，不能塞一个假销售订单号。两种可实现：
   A) 若所有需求确认都可关联销售订单/上层订单，扩展现有表，新增 demand_order_id 区分生产需求来源，order_id 仍写销售订单；适用于“以销定产”。
   B) 若存在无销售订单的半成品补做，建议新建 production_requirement_allocation（request_id、allocation_line_no、material_id、supply_type、supply_id、allocated_quantity、required_date、source_bom_item_id、demand_order_id），order_material_reserve 继续只管销售预占；这不是第二套库存真源，而是两类不同事实，避免把生产分配伪装成销售预占。若坚持单表，则必须把 order_id/order_no 改为可空并加 owner_type，迁移影响现有查询，需单独评估。
9）无论 A/B，请求级幂等都按第 4 条执行；分配行的唯一键都是 (request_id, allocation_line_no)，不是 UNIQUE(request_key)。

### 迁移兼容与回退

迁移编号不预占：实际编写时重新读取账本 sys_config['ops.schema.applied'] 与（待办+归档）目录最大号，取较大者 +1；文件名写 `NN_<desc>.sql`，其中 NN 由执行者当次填写。2026-11-11 10:32 CST 观察到的 355 只是现场值，不是 356/357/358 的预留；本文件不生成 SQL、不写死编号。

按依赖拆成三组，分别由 B、D、F 执行；编号按依赖顺序分配，不按卡序硬排：

| 逻辑组 | 归属 | 内容 | 依赖与兼容/回退 |
|---|---|---|---|
| M1 制造主数据身份 | B | engineering_bom 增加 output_type/output_id/current_scope，product_id 改为 NULL；engineering_bom_item 增加 sub_bom_id、unit_factor、unit_factor_basis、unit_factor_source；engineering_routing 增加 output_type/output_id/route_scope，product_id 改为 NULL；product_work_spec_version 增加 output_type/output_id，product_id 改为 NULL；若方案 C 获批，建 product_material_scheme | 先加列并回填 PRODUCT/product_id；确认无 MATERIAL 行后再改 NOT NULL→NULL；回退时先禁用/删除 MATERIAL 方案/路线/规范行，恢复 product_id NOT NULL，再删列/表。旧 4 行 BOM、12 行明细、路线与规范语义不变 |
| M2 需求分配与幂等 | D | order_material_reserve 增加 request_id/request_key/allocation_line_no/source_bom_item_id/source_path/supply_type/supply_id/required_date/allocated_quantity/demand_order_id；若请求头表获批，建 requirement_confirm_request；唯一键 UNIQUE(request_id, allocation_line_no) | 加列全部可空、默认 NULL；不引用 production_order/BOM 新列，可独立执行；回退先删除/归档新分配行与请求头，再删列/索引；旧预占行不受影响 |
| M3 生产输出身份 | F | production_order 增加 output_type/output_id/routing_version；product_id 改为 NULL；必要时给 inventory_inbound_item.material_id 建索引 | 先加列并回填 PRODUCT/product_id；确认无 MATERIAL 工单后改 NULL；回退时删除/归档 MATERIAL 工单，恢复 NOT NULL，再删列。历史 PRODUCT 工单不变 |

依赖说明：
1）M1 必须早于 M2、M3；M2 与 M3 之间没有数据库列依赖，可以按派工顺序单独执行，不要因为“D 在 F 前”就把 D 的迁移编号硬放到 F 之后。
2）D 的 PRODUCT 路径在 M2 后即可验收；D 的 MATERIAL 供给/补做分支需要 F 的 M3 与 F 服务接口，运行时若缺失返回 MAKE_WORK_ORDER_UNAVAILABLE 并整体回滚，不写半套数据。若要求 D 的 MATERIAL 分支一次验收通过，则先完成 F 的 M3/接口，再验收 D 的 MATERIAL 场景；这是运行时依赖，不是让 D 的迁移依赖 F 的后置迁移。
3）G 后端快照只读现有表，不新增迁移；若 G 需要订单快照列，必须回到 F 的 M3 统一加，不单独起第三套快照表。
4）实际执行仍按 CONVENTIONS：用户批准 + 手工全库备份 + scripts/db-migrate.sh 记账；编号冲突、同号多文件、内容变更一律中止。

兼容规则：

1）所有新增列先可空或带默认值，旧客户端不传时保持旧行为；新接口才使用。
2）回填必须幂等：只对 output_type IS NULL 的行回填；重复执行不改变已回填行。
3）改 NOT NULL 前必须用只读 SQL 证明无 NULL 冲突；迁移文件内先查 information_schema/数据，再执行 ALTER。
4）回退必须由用户手工备份后执行；A/B 不自动备份、不生成备份文件。
5）新表若获批，必须同步 scripts/model-baseline.json 的 approvedNewTables（任务码/日期/提案链接）和 jjx-docs/sql/00_clean_test_data.sql 的清理归属；未同步前 pre-push 建表闸会红。
6）所有字符串列显式 utf8mb4/utf8mb4_unicode_ci；生成列/唯一索引需在迁移中写明理由。

## 版本适用、防环与保存边界

### 版本适用

1）生产工单创建时快照 bom_id/bom_code、routing_id/routing_code/routing_version、work_spec_version_id；ProductionBomResolver 优先按 order.bom_id 取，其次兼容历史空值按 output 当前已批准版本取。历史工单永远按快照，不因产品/物料切换当前版本而重算。
2）新需求预览的解析顺序：请求显式 bomId > product.current_bom_id > 该产品最新已批准且 is_current=1 的 BOM；若都找不到，错误 BOM_NOT_FOUND，不静默用任意版本。
3）选中 BOM 必须 approve_status=3，且所有有效行 review_status=3；effective_date/expiry_date 非空时，demandDate 必须落在窗口内，否则 BOM_NOT_EFFECTIVE。历史空日期视为无界，保持旧 BOM 可用。
4）产品 BOM 行引用 sub_bom_id 时，子方案按固定 bom_id 使用，不按“最新版本”自动替换；子方案未批准/过期/输出不匹配时，预览和工单创建都阻断，并返回路径。
5）本产品主方案只影响“新加行/试算默认选择”，不自动改写已保存行的 sub_bom_id；已保存行必须显式保存其选择。
6）版本切换不回写历史工单、历史领料、历史批次；V03 要求的历史依据由 order.bom_id、order.routing_id/routing_version、order.work_spec_version_id、领料 source_bom_item_id、批次 source_* 保留。
7）历史可重建的前提是“已批准版本不可原地改”：现有 EngineeringBomServiceImpl.updateBomWithDetail 对非草稿/驳回状态抛错，EngineeringRoutingServiceImpl 对不可编辑路线拒绝更新，作业规范发布版本 PUBLISHED 不重写；B 只需保持该不变量并让复制新版本承担改版。若发现任一模块可原地改已批准版本，B/F 必须先修复该入口，不能让 G 前端“补快照”。
8）G 后端快照读取顺序：order 持久化 ID → 对应 BOM/路线/规范版本 → production_operation_execution（工单创建时生成的工序快照）→ product_work_spec_item（SPEC_JSON/引用+哈希）。任一层缺失返回 SNAPSHOT_PARTIAL 与缺失原因，不用当前最新版本顶替。
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
| 产品BOM→保存 | 父 BOM 主表/明细、items[].sub_bom_id、schemeChoices（含 isPrimary）同一事务；C：写 product_material_scheme；A：写 product.material_scheme_json；B：写 BOM 行 is_primary | 不自动批准子方案；不删除独立已保存方案；不改其他产品选择 | 父 BOM 行锁/版本校验；A 用 scheme_revision 乐观锁；校验 scheme 输出一致；草稿引用允许但返回警告 |
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
| 产品BOM整单保存 | 新增/编辑 | 父BOM+items.sub_bom_id+schemeChoices+存储选择（A/B/C） | 采用结果随父保存持久化；父取消不删除独立方案 | B、C |
| 工程路线/作业规范页（现有入口） | 新增/编辑 | engineering_routing/engineering_routing_item、product_work_spec_version/product_work_spec_item，按 output_type/output_id 绑定 PRODUCT 或 MATERIAL | 路线管工序顺序、规范管展示；不在 BOM 主表加路线编辑器；批准/发布后不可原地改 | B、G1 |
| 产品列表→配置BOM | 选择 | product.current_bom_id/current_bom_version | 只选已批准BOM；不编辑明细；不改变方案主数据 | 现有能力，不改 |
| 作业规范→打开BOM | 编辑 | 仍走BomFormDialog整单保存 | 不新增第二条保存链；不反向改物料档案规格 | C、G2 |
| BOM详情/审核/打印 | 只读 | 无 | 不产生需求/工单/库存；审核只改行状态与流水 | 现有能力，B回归 |
| 生产订单新增/编辑 | 新增/编辑 | 工单主表（PRODUCT 或 MATERIAL 产出、bom 快照） | 不保存BOM方案；不改父BOM；不自动领料 | F |
| 计划转工单 | 新增 | 子工单+工序执行 | 继承计划 bom_id/routing；不重选BOM；MATERIAL 例外见F | F |
| 领料预览 | 只读 | 无 | 不扣库存、不建单、不写流水 | D |
| 生成领料单/确认发料 | 新增/确认 | 出库单/明细+流水+余额 | buy 行按现有；make 行仅在已有可用半成品库存/已完工产出时按库存领用，缺口不领料、不重复开下级工单；applyDelta 唯一入口 | D、F |
| 完工入库 | 新增/确认 | 入库单/明细+批次+流水+余额 | PRODUCT 用 PRODUCT 身份；MATERIAL 用 MATERIAL 身份；按实际合格数，不按理论数 | F |
| 作业规范/打印 | 只读 | 无 | 不写BOM/路线/档案；只读展示，历史取 G1 快照 | G1、G2 |

## 接口契约

所有接口沿用现有 Result 包裹（code/data/message），权限沿用 engineering:bom:*、production:order:*、inventory:outbound:*、inventory:alert:* 等；新增接口不新建权限点，除非另行批准独立管理页。

状态枚举真源（JSON 示例只复用这些值，不新造“已批准态”）：

| 字段 | 复用枚举/取值 | 说明 |
|---|---|---|
| approveStatus（BOM） | ProductEnums.BomStatus：1草稿/2待审核/3已批准/4已驳回 | 生产取数只认 3；示例新建为 1，不显示未执行审批的 3 |
| approveStatus（路线） | ProductEnums.RouteStatus / ApproveStatusEnum：1草稿/2待审批/3已批准/4已驳回 | 同一套状态语义 |
| reviewStatus（BOM 行） | ProductEnums.BomItemReviewStatus：1草稿/2待审核/3审核通过/4驳回/5待移除 | 行级审核 |
| orderStatus | ProductionOrderStatusEnum：0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/8已完成/9已取消/10已关闭/11已超期 | 示例 confirm 默认 0，不凭空 4 |
| executionStatus | ExecutionStatusEnum：0待执行/1准备中/2执行中/3暂停/4完成/5跳过/6取消/7超期/8异常/9待确认 | 工单工序初始 0 |
| work spec status | product_work_spec_version.status：PUBLISHED/RETIRED | 只按持久化 ID 读取，不按“最新”推断 |
| outputType | 新增 manufacturing 枚举：PRODUCT/MATERIAL | 不复用 bom_type 的 engineering/manufacturing 字符串，也不复用 BomType(1/2) |

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
| POST | /production/requirement/confirm | 确认缺口，按 idempotencyKey 创建请求头、分配行和 DRAFT 补做工单 | production:order:add |
| GET | /production/requirement/{requestId} | 查询试算/确认结果，幂等回查 | production:order:view |
| POST | /production/order/manufacturing | 直接创建 MATERIAL DRAFT 补做工单（可被 confirm 内部调用） | production:order:add |
| GET | /production/order/{orderId}/manufacturing-snapshot?depth=32 | G 后端：按工单持久化 ID 组装多层 BOM/路线/规范/物料快照，只读 | production:order:view |

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
      "clientLineId": "gap-349",
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
        "routingId": 88,
        "routingVersion": "V1.0",
        "workSpecVersionId": 12,
        "plannedQuantity": 160,
        "orderStatus": 0
      }
    ],
    "allocations": [
      { "allocationLineNo": 1, "materialId": 349, "supplyType": "STOCK", "supplyId": 9001, "allocatedQty": "140.0000", "requiredDate": "2026-11-20" },
      { "allocationLineNo": 2, "materialId": 349, "supplyType": "PURCHASE", "supplyId": 7001, "allocatedQty": "20.0000", "requiredDate": "2026-11-20" }
    ]
  }
}
```

重复 idempotencyKey 时返回同一 result_json，duplicate=true，不新增工单、不重复占用。orderStatus=0 是 ProductionOrderStatusEnum.DRAFT，不是“已批准”；后续审批/下达由现有接口完成。allocations 允许同一请求多行，行号 allocationLineNo 在请求内唯一。

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
| DUPLICATE_REQUEST | 同 idempotencyKey 正在处理 | 返回已有结果或 409，不重复开单 |
| IDEMPOTENCY_KEY_REUSED | 同 idempotencyKey 对应不同 request_hash | 409，拒绝覆盖首次结果 |
| CONCURRENT_MODIFICATION | scheme_revision/行版本不匹配 | 409，提示重新加载后重试 |
| MATERIAL_ROUTING_NOT_FOUND | MATERIAL 工单找不到已批准当前路线 | 阻断创建，要求工程维护路线 |
| MATERIAL_ROUTING_EMPTY | MATERIAL 路线存在但没有工序行 | 阻断，不凭空造工序 |
| MATERIAL_WORK_SPEC_NOT_PUBLISHED | MATERIAL 没有已发布作业规范版本 | 阻断下达，不用产品规范顶替 |
| MAKE_WORK_ORDER_UNAVAILABLE | F 未就绪时 D 尝试创建 MATERIAL 补做工单 | 整体回滚，不写半套分配 |
| SNAPSHOT_PARTIAL | G 后端缺少某一层持久化依据 | 返回缺失原因，不用最新版本顶替 |

## 精确比例、单位与净需求契约

### 比例与精度（有理数精确约简）

1）所有数量/比例使用 BigDecimal，禁止 double/float。模数、基数保存为 decimal(12,4)，必须 >0；允许 0.5、2.5 等正小数，拒绝 0、负数、空值、NaN/Infinity、非数字。
2）每个比例按有理数保存和计算，不把除法提前落成有限小数：
   A) 将 decimal(12,4) 的 baseQty、moduleQty 各乘 10^4 转 BigInteger（精确，无舍入）。
   B) 行比例 = baseNum / moduleNum，用 gcd 约简；例如 1.0000/6.0000 → 10000/60000 → 1/6；0.5000/2.5000 → 5000/25000 → 1/5。
   C) 损耗率 decimal(5,2) 按百分比转有理数：lossRate=0.50 → 50/10000 = 1/200，损耗因子 = 201/200；lossRate=0 → 1/1。
   D) 逐层计算 gross = Q × (baseNum/moduleNum) × (lossFactorNum/lossFactorDen)，每步用 gcd 约简，保留 num/den；不在中间层转 decimal，也不在中间层 CEILING。
3）理论耗用、生产批量、实领粒度分别返回，不能混成一个数：
   A) theoreticalQty：精确有理数 num/den；另给 12 位小数近似值仅用于显示，标注 approximate=true，不能当权威值。
   B) aggregateQty：同一 material_id + 单位 + 需求日期桶的全部来源精确求和后，才按单位策略转换/取整一次。计件单位若业务要求整件，只在聚合终点 CEILING，并同时返回 exactQty；连续单位按 4 位 HALF_UP，同时返回 exactQty。
   C) productionBatchQty：确认补做时才按生产批量向上取整（例如 batch_size=50，gap=160 → 200）；单独返回，不覆盖 theoreticalQty。
   D) issueQty：实际领料时按 min_issue_qty 下限、包装/领料步长取整；单独返回，不覆盖 theoreticalQty/productionBatchQty。
4）反例（为什么不能逐层 CEILING 或 scale12 后 CEILING）：
   A) 顶层 Q=1 件，下层比例 1/6，再下层比例 6/1；精确终点 = 1×(1/6)×6 = 1 件。若在 1/6 处 CEILING 成 1，再乘 6 得 6 件，整值变多 5 件。
   B) Q=1，比例 1/3，再下层 3/1；scale12 得 0.333333333333，CEILING 成 1，再乘 3 得 3 件；精确值应为 1 件。DECIMAL128 只是精度更高，不能消除“提前取整”的语义错误。
   C) 顶层 300、比例 1/6 得 50 精确；若先存 0.1667 再乘 300 得 50.01，再 CEILING 成 51；原始比例 300×1÷6=50。这是现有缺陷 D01 的根因。
5）损耗只在所属层计一次：上层展开得到下层需求后，下层再按自己的 loss 计；不得在父层和子层对同一用量重复加损耗。损耗因子也是有理数，不提前四舍五入。
6）最低投料 min_issue_qty 与生产批量不是净需求：净需求是事实缺口；补做建议量按生产批量向上取整；领料量按 min_issue_qty 下限与单位取整。三者分列返回，不能合并成一个数。
7）比较用 compareTo/有理数交叉相乘，不用 equals；任何“先转 double 再比”的实现都视为缺陷。

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

| 单位类别 | 示例 | 理论精确值 | 聚合终点展示/执行取整 |
|---|---|---|---|
| 计件 | PCS、个、件、片、张、只、套、PIN | 有理数 num/den，原样返回 | 仅同一物料+单位+日期桶聚合后 CEILING 到整数；同时返回 exactQty，禁止逐行逐层 CEILING |
| 连续长度 | M、m、米 | 有理数 num/den | 4 位 HALF_UP；同时返回 exactQty；不向上取整 |
| 面积 | M2、㎡、平方米 | 有理数 num/den | 4 位 HALF_UP；同时返回 exactQty；不向上取整 |
| 重量 | KG、kg、G、g | 有理数 num/den | 4 位 HALF_UP；同时返回 exactQty |
| 体积 | L、ML | 有理数 num/den | 4 位 HALF_UP；同时返回 exactQty |
| 未登记 | 其他 | 有理数 num/den | UNIT_POLICY_MISSING，阻断并登记策略后再算 |

取整只发生在“同一物料 + 同一单位 + 同一需求日期桶”的聚合之后，且只影响 aggregateQty；theoreticalQty 永远保留精确有理数。明细行保留未取整值用于追溯。计件单位若必须整批领用，额外返回 productionBatchQty 或 issueQty，不能覆盖 exactQty。禁止在理论耗用层把 PCS/张全部 CEILING。

### 净需求算法

1）读取本次采用的 BOM：显式 bomId 优先；校验 approve_status、行审核、生效窗口、权限、物料/单位/换算；构建显式 sub_bom 图并 DFS 防环。
2）按拓扑依赖处理：先汇齐同一输出物料的上游贡献，再对兼容的“material_id+单位+需求日期桶”用有理数精确汇总；不同单位/不同版本/不同仓库不盲目合并；保留每条来源路径。中间层不取整，聚合终点才按单位策略转换。
3）计算总需求 gross（精确有理数 + 12 位近似展示值）；读取可用库存、其他需求占用、按需求日期可用的采购在途、已有生产供给。
4）可用库存口径复用现有实现，不新增刚性规则：InventoryAlertServiceImpl 352—356、638—641、1088 行当前口径是 available = inventory_stock.available_quantity − material.safe_stock；而 inventory_stock.available_quantity 是生成列 total_quantity − total_reserved（evidence-ddl-20261111.txt 已保存 DDL）。因此新契约返回 stockOnHand=total_quantity、reserved=total_reserved、safeStock=material.safe_stock、availableForDemand=max(0, available_quantity−safeStock) 四个分量，并标注 safeStockDeducted=true；不重复扣减两次，也不把“必须扣安全库存”写成新的业务规则。若用户要求毛可用量，直接取 available_quantity 分量；是否扣安全库存由现有口径/配置决定。
5）其他需求占用：物理预留以 inventory_stock_item.reserved_quantity / inventory_stock.total_reserved 为准；order_material_reserve 只作计划占用，若已进入 total_reserved 则不重复扣。现有 InventoryAlertServiceImpl 已注明旧账停扣 order_material_reserve，新逻辑要统一口径并记录。
6）采购在途：purchase_order_item.quantity - received_quantity，条件 purchase_order.receipt_status=0、approval_status IN (1,3,4)、expected_delivery_date ≤ demandDate；日期为空不覆盖有日期需求。
7）生产供给：production_order.remaining_quantity，条件 order_type=WORK_ORDER、order_status IN (4,5,6)、输出身份一致、plan_end_date ≤ demandDate、排除当前需求自身/已取消；已有生产供给的原料需求按原工单追踪，不重复生成。
8）分配顺序：库存 → 已有生产供给 → 采购在途 → 新补做；同一供给只能分配一次；跨分支共享库存时先聚合再分配，不能两个分支都认为无缺口。
9）gap = max(0, gross - 已分配库存 - 已分配生产 - 已分配采购)；gap 不小于 0。
10）若 gap>0 且该物料是 MAKE：必须存在已批准且生效的 sub_bom_id，否则 MAKE_WITHOUT_SCHEME；先返回 theoreticalGap 精确值，再按生产批量得到 productionBatchQty（单独字段），确认补做后以 productionBatchQty 作为下层展开的显式输入；原有库存/已有供给覆盖的部分不展开。不得把 productionBatchQty 写回 theoreticalGap。
11）若 gap=0：不展开下层；但仍返回来源路径与已分配明细。
12）试算只读：不扣库存、不建工单、不写流水；确认后才创建分配与补做工单，并在事务内重新校验供给，失败回滚，不出现半套单据。
13）幂等：confirm 必须带 idempotencyKey（请求头 request_key）；请求头唯一键 + 行锁 + 分配行 UNIQUE(request_id, allocation_line_no)；重复请求返回同一 result_json，不重复占用/开单。一次 confirm 允许多条分配行，不把 request_key 唯一键放在分配行上。
14）同一半成品出现在不同深度：按“material_id+单位+日期桶”汇齐兼容需求后统一净额与展开；不能因深度不同重复分配库存或漏算后到需求。
15）库存变化后重新试算；前端不得拉全库库存自算采购需求，也不得保留第二套权威算法。

### 生产工单 MATERIAL 身份闭环路径

1）需求确认识别 material_id=349 的缺口 160，选择已批准方案 bom_id=124。
2）创建 production_order：order_type=WORK_ORDER；parent_order_id=上层工单；output_type=MATERIAL；output_id=349；product_id=NULL（迁移后允许）；product_code/product_name/product_unit 从 inventory_material 冗余写入，仅作展示；bom_id=124；routing_id=MATERIAL 349 的已批准当前路线（不是 NULL，也不从 BOM 行推断）；routing_code/routing_version 按路线快照写入；work_spec_version_id=MATERIAL 349 的已发布规范版本；trace_id 继承上层；planned_quantity=160；order_status 使用 ProductionOrderStatusEnum：confirm 默认创建 DRAFT(0)，不自动批准/不凭空显示 PLANNED(4)；后续审批/下达走现有状态机。
3）F 生成工序：沿用现有 generateOperationExecutions(routingId,...)，只读 engineering_routing_item，按 workflow_seq、process_order 排序；父行生成 production_operation_execution，子行合并 jobs 快照。BOM 行 process_id/行顺序不参与工序生成；没有 MATERIAL 路线或路线无工序时阻断 MATERIAL_ROUTING_NOT_FOUND / MATERIAL_ROUTING_EMPTY，要求工程先维护路线，不得从 BOM 行造工序。
4）开工/领料：buy 行按现有规则生成领料；make 行有可用半成品库存/已完工产出时必须可被上层领用（material_id 身份、FIFO 批次），库存不足部分只保留缺口，由需求计划生成/复用下级补做工单，领料动作本身不得重复创建下级工单。make 行没有 sub_bom_id 时阻断，不静默跳过。
5）完工：工序完成 + FQC 合格后 completeOrder；createFromProduction 对 MATERIAL 产出执行 ensure(MATERIAL, material_id=349)，inbound_item.material_id=349、inventory_item_id=MATERIAL 身份、batch_no=BATCH-{orderNo}；applyDelta 写批次/汇总/流水，source_type=PRODUCTION、source_id=orderId。
6）上层领用：上层 BOM 行 material_id=349，FIFO 按 material_id 找到该批次；出库明细 material_id=349、batch_no 一致；成品入库仍走 PRODUCT 身份。原料批次→半成品工单→半成品批次→上层领料→成品批次链路保留。
7）异常：合格入库不足 160 时，上层只能领实际可用量，剩余缺口继续显示；不得虚增；红冲/退料按现有反向单据，不更新原流水。
8）前置阻断：inventory_item MATERIAL 身份未对齐时，F 不得开工入库；先由用户批准的数据订正建立 material_id → inventory_item_id 的可核对映射。当前零库存，不可用旧 inventory_item 直接当新库。零库存也不能证明 alert_log/历史单据未被错误身份引用，订正前先出映射清单与影响清单，不自动清理。
9）作业规范：MATERIAL 工单必须绑定 MATERIAL 作业规范发布版本；发布内容按 product_work_spec_item 的 SPEC_JSON/BOM/ROUTING/DRAWING 展示，BOM/路线引用按 output 身份解析；缺失时 MATERIAL_WORK_SPEC_NOT_PUBLISHED，不用产品规范顶替。

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
| jjx-web/src/views/product/bom/components/BomDetailTable.vue | C 先，G2 后 | C 完成方案展示并通过验收后，G2 才能加打印/作业规范展示 |
| jjx-web/src/views/production/order/index.vue、OrderFormDialog.vue | F | E 只改 material-calculation 子目录，不改订单主表单 |
| jjx-web/src/views/purchase/plan/index.vue、jjx-web/src/api/purchase/order.ts | E | F/G2 不改 |
| jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue | G2 | C 只通过现有 BomFormDialog 打开，不改该文件 |
| jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringRoutingServiceImpl.java | B | F/G1 只读；B 不改 BOM 主表路线编辑器 |
| jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecVersionService.java、WorkSpecBindingService.java | B | F/G1 只读；B 统一按 output 身份绑定 |
| jjx-server/src/main/java/com/jjx/production/service/ManufacturingHistorySnapshotService.java（新增） | G1（OpenClaw） | 只读快照；G2 只消费 API，不自行拼装历史 |

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
16）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/entity/EngineeringRouting.java
17）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/entity/EngineeringRoutingItem.java
18）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringRoutingDTO.java
19）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringRoutingItemDTO.java
20）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringRoutingVO.java
21）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringRoutingServiceImpl.java
22）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/EngineeringRoutingValidator.java
23）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/controller/EngineeringRoutingController.java
24）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/EngineeringRoutingMapper.java
25）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/EngineeringRoutingItemMapper.java
26）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/entity/ProductWorkSpecVersion.java
27）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/domain/entity/ProductWorkSpecItem.java
28）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecVersionService.java
29）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecService.java
30）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/service/WorkSpecBindingService.java
31）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/controller/ProductWorkSpecVersionController.java
32）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/controller/ProductWorkSpecController.java
33）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/ProductWorkSpecVersionMapper.java
34）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/mapper/ProductWorkSpecItemMapper.java

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
12）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/ManufacturingRouteBindingTest.java
13）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/product/service/impl/WorkSpecMaterialVersionTest.java
14）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/product/enums/ManufacturingOutputType.java（PRODUCT/MATERIAL，新增枚举；不复用 BomType）

条件文件（仅用户批准新表/结构变更后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/NN_bom_manufacturing_scheme.sql（NN 执行时按账本重取，写 M1，不预占 356）
2）/home/administrator/jjx/scripts/model-baseline.json（approvedNewTables 增项，仅方案 C 获批时）
3）/home/administrator/jjx/jjx-docs/sql/00_clean_test_data.sql（新表清理归属，仅方案 C 获批时）

权限：沿用 engineering:bom:view/add/edit/approve/reject/delete；不新增权限点。

验证：mvn -o -pl jjx-server -am test 定向（2G/180 秒限制按卡面），至少覆盖方案保存回读、多方案隔离、sub_bom 输出匹配、环/自指/悬空、正小数/0/负数、单位不一致、MATERIAL 路线/作业规范 output 身份与发布绑定；不跑库存写入路径时不需要 check:stock:strict，若 B 触及库存只读查询则加跑。

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

条件文件（仅 M2 迁移批准后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/NN_requirement_allocation.sql（NN 执行时按账本重取，写 M2；不预占 358）
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/domain/OrderMaterialReserve.java（加字段）
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/inventory/mapper/OrderMaterialReserveMapper.java（唯一键/查询）
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/entity/RequirementConfirmRequest.java（仅请求头表获批）
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/mapper/RequirementConfirmRequestMapper.java（仅请求头表获批）

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

条件文件（仅 M3 迁移批准后）：

1）/home/administrator/jjx/jjx-docs/sql/migrations/NN_production_output_identity.sql（NN 执行时按账本重取，写 M3；不预占 357）

前置：F 开工前必须证明 inventory_item MATERIAL 身份已通过用户批准的数据订正建立可核对映射，且 MATERIAL 路线/作业规范已能按 output 身份绑定；否则只做代码/测试准备，不执行入库验证，报告阻塞。不得用产品路线/产品规范顶替 MATERIAL。

验证：mvn 定向 2G/180 秒；P01—P04、I01—I04、V02/V03 的真实事务测试；若改库存写入路径，npm run validate + check:stock:strict 五项全 0；未跑真实过账必须写“未验证”。

### G 作业规范与生产资料展示（拆为 G1 后端快照 + G2 前端展示）

G 不能只有前端。多层 BOM 版本、路线版本、作业规范版本、物料属性的历史依据必须由后端按工单持久化 ID 组装，前端只展示；否则 BOM/路线/规范/物料任一档案变化都会让历史页漂移。

#### G1 后端历史快照（OpenClaw）

新增文件：

1）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/ManufacturingHistorySnapshotService.java
2）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/service/impl/ManufacturingHistorySnapshotServiceImpl.java
3）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/controller/ManufacturingHistorySnapshotController.java
4）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/vo/ManufacturingSnapshotVO.java
5）/home/administrator/jjx/jjx-server/src/main/java/com/jjx/production/domain/vo/ManufacturingSnapshotNodeVO.java
6）/home/administrator/jjx/jjx-server/src/test/java/com/jjx/production/service/impl/ManufacturingHistorySnapshotTest.java

读取来源（只读，不新增表/迁移）：

1）production_order：order_id、output_type/output_id、bom_id/bom_code、routing_id/routing_code/routing_version、work_spec_version_id、planned/completed/remaining_quantity、trace_id。
2）engineering_bom + engineering_bom_item：按 order.bom_id 及 item.sub_bom_id 递归；只读已批准版本；输出 BOM 版本、行号、parent_material_id、sub_bom_id、material_id/material_code/name/unit、module/base/loss、process_id（仅展示）。
3）engineering_routing + engineering_routing_item：按 order.routing_id 读取路线头与工序行；工序顺序按 workflow_seq、process_order；这是历史路线依据。
4）production_operation_execution：按 order_id 读取工单创建时生成的工序执行行，作为“工单实际工序快照”；与路线行不一致时返回 SNAPSHOT_PARTIAL 并标出来源，不改历史。
5）product_work_spec_version + product_work_spec_item + work_spec_usage：按 order.work_spec_version_id、work_spec_usage 区间读取规范正文快照、BOM/ROUTING/DRAWING 引用及 content_hash；不按“最新版本”回退。
6）inventory_material：只读当前物料档案用于展示；历史单位/规格优先取 BOM 行冗余快照，缺失才用当前档案并标记 snapshotSource=current_master。

接口：GET /production/order/{orderId}/manufacturing-snapshot?depth=32，权限 production:order:view；返回 root（order 输出）、nodes[]（每层 BOM/路线/规范/物料）、sourcePath、snapshotSource、contentHash、warnings/blockers。depth 上限 32，超限返回 BOM_DEPTH_EXCEEDED；任一层缺失返回 SNAPSHOT_PARTIAL，不用当前最新版本顶替。

验证：mvn 定向 2G/180 秒；覆盖多层 BOM、MATERIAL 工单、路线/规范版本切换后历史不变、缺失层 SNAPSHOT_PARTIAL、无写操作。G1 不启动服务、不改数据。

#### G2 前端展示（Hermes）

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
11）/home/administrator/jjx/jjx-web/src/views/product/bom/components/BomDetailTable.vue（共享：C 先完成方案展示，G2 再加制造层/打印展示）

新增文件：

1）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/ManufacturingProcessSheet.vue
2）/home/administrator/jjx/jjx-web/src/views/engineering/product-spec/components/__tests__/manufacturingProcessSheet.spec.ts
3）/home/administrator/jjx/jjx-web/src/api/production/manufacturingSnapshot.ts

权限：engineering:product:spec:view / production:order:view；沿用既有。

验证：Vitest 单 worker/1G/120 秒；vue-tsc 3072M/300 秒；覆盖 PRODUCT 现有作业规范不回归、MATERIAL 展示 BOM+路线工序+规范版本、打印不写数据、G1 缺失字段显示 SNAPSHOT_PARTIAL；不启动服务。

归属：G1 由 OpenClaw 先完成并通过验收，G2 由 Hermes 消费 G1 接口；G2 不得自行拼装“最新 BOM/路线”或在前端补历史。

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
| U06 | 主表/物料规格/工艺路线边界 | C | B、G1/G2、H | BOM 主表不加供给列/路线编辑器；工序只由 engineering_routing 维护，BOM 行 process_id 仅展示；不改档案规格 |
| V01 | BOM V2生效后新需求 | B | D、H | 按生效规则与产品方案；可解释各层依据 |
| V02 | 生产中发现BOM错误 | F | D、H | 补退料/未做/已加工/供应释放闭环；不强制路径 |
| V03 | 历史工单/领料/批次追溯 | F | G1/G2、H | 按当时依据还原；不被当前BOM重算 |
| P01 | 补做160并供上层 | F | D、H | 工单→领料→合格入库→上层领用 |
| P02 | 仅合格150或分批 | F | H | 不虚增160；可领实际可用；缺口可见 |
| P03 | 再下层制造与原料批次追踪 | F | D、H | 原料批次→各层工单/产出→成品 |
| P04 | 完工/领料失败重试 | F | D、H | 事务回滚/幂等；无重复流水 |
| I01 | 195mm×50m整卷发9.75㎡只用12.8m | F | D、H | 实领/理论/实耗/余料分开 |
| I02 | 余卷退仓或留车间 | F | H | 去向/数量可追溯；库存不重复可用 |
| I03 | 红冲纠错/重算余额 | F | H | 原流水保留；反向记录；余额可重算 |
| I04 | 库存页面/报表/批次模型 | F | G1/G2、H | 收/发/结存三栏；批次不新增累计列 |
| C01 | 旧BOM无子BOM | B | D、H | 大料直接耗用/分组/审核/保存不回归 |
| C02 | 片/M/㎡或尺寸不全 | D | F、H | 有依据才换算；1PCS≠1M/㎡；无依据阻断 |
| C03 | 半成品新建/复制BOM/复制工单 | B | F、H | 身份/方案不串产品；MATERIAL 路线/规范按 output 身份绑定；复制按目标规则 |
| C04 | 物料单位订正与历史 | B | F、H | 历史保留原单位/依据；不静默重解释 |

覆盖核对：5+5+6+6+3+4+4+4=37，无遗漏；A 对全部场景给出归属，H 对全部场景做只读验收。

## 新表提案与替代评估

### 提案一：product_material_scheme（方案 C，按条件必要）

状态：提案，待用户拍板；A 未创建、未写迁移、未登记基线。建议归属 B；若用户选 A/B，本提案不执行。

必要性条件（不是既定业务）：只有用户确认“主方案必须跨 BOM 版本独立保存 + 逐条审计 + 被需求/工单独立引用”时，独立表才有明确必要性。001 的 G04 只要求按产品隔离；若只需要这一点，product 级 JSON 映射（方案 A）即可，不能把 JSON 说成无并发/不可查。

准入对照（CONVENTIONS §15.2）：独立关系对象（1）、会被需求/工单引用（2）、需要唯一约束（3）、需要变更留痕（5）在“跨版本 + 审计”条件下成立；若只做产品级映射，§15.2 不构成建表理由。

回退：删除表前先删除/归档所有引用；用户手工备份；不自动回滚。若用户不批准，B 的替代实现是方案 A（product JSON）或方案 B（BOM 行上下文）；U03/U04/G04 的语义分别对应“跨版本保持”或“版本内选择”，由用户选择，不强行降级。

### 提案二：requirement_confirm_request（请求级幂等头）

状态：提案，待用户批准；用于解决“一次 confirm 多条分配行”的请求级幂等。没有它时，分配行 UNIQUE(request_key) 会阻止第二行；只有 (request_key, allocation_line_no) 唯一 + GET_LOCK 的退路，健壮性较弱。

回退：不建表时，confirm 用 (request_key, allocation_line_no) 唯一 + 应用层锁；但必须明确“请求级重复”只能靠锁/重查，不能靠分配行唯一键。

### 提案三（不新建表）：扩展 order_material_reserve

状态：提案，随 M2 迁移批准。相比新建分配表，优先扩列，原因是现有预占/占用/释放/超时任务已围绕该表，另建表会产生第二真源。字段与约束见“供需分配”节。若用户要求独立审计表，再另行评估；A 不建议。

### 不提案的部分

1）不新建“BOM 方案主表”之外的第二套 BOM 表；engineering_bom 继续作为唯一 BOM 主表。
2）不新建“需求结果表”保存试算；试算只读，确认后的分配落 order_material_reserve，工单落 production_order。
3）不新建“库存余额表”；库存仍以 inventory_stock/inventory_stock_item 派生，流水 append-only。
4）不新建“单位换算表”；本阶段用 BOM 行换算快照 + 物料尺寸；卷料批次长度依赖 013，不提前占表。
5）不新建“路线表/作业规范表”；复用 engineering_routing、engineering_routing_item、product_work_spec_version、product_work_spec_item，只加 output 身份列。
6）不新建“历史快照表”；G1 读取已批准版本 + 工单持久化 ID 组装；若发现版本可原地改，先修 B 的不变量，不另起快照表。

## 待批准、风险与未验证项

### 需要用户拍板

1）主方案存储选 A / B / C：A=product 级 JSON 映射（不新建表，行锁+scheme_revision 乐观锁，产品级跨版本）；B=BOM 行上下文（不新建表，版本内选择）；C=product_material_scheme 新表（跨版本独立 + 逐条审计）。001 只确认“按产品隔离”，没有确认“跨版本独立生命周期”，所以不要把 C 当成唯一可行解；若只要求按产品隔离，A 可落地。
2）是否批准 M1/M2/M3 三组结构变更：BOM/路线/作业规范 output 身份、product_id 可空、production_order 产出身份与 routing_version、order_material_reserve 分配字段。迁移编号实际编写时按账本重取，不预占 356/357/358；结构变更前用户手工备份。
3）MATERIAL 路线/作业规范是 001“路线管顺序、作业规范管展示”的必要工程扩展，不需要重新确认业务；但新增列/迁移仍按结构变更审批。若用户不同意加 output 身份列，则 MATERIAL 工单无法绑定路线/规范，F 只能停在代码准备，不能拿产品路线顶替。
4）是否接受 inventory_item MATERIAL 身份的数据订正（建立 material_id → inventory_item_id 可核对映射）；不订正则 F 不能真实入库。订正前先出映射清单、影响清单和备份方案，不自动清理。
5）是否批准 requirement_confirm_request 请求头表（提案二）；不批准则只能用 (request_key, allocation_line_no) 唯一 + 应用层锁，请求级幂等较弱。

### 风险

1）最大风险是物料身份错位：7/7 BOM 物料按 source_id 命中行但 code/name 全不一致；1562 个 item 中 1271 个按 source_id 找不到物料、按 item_code 对 291 个物料 0 命中；当前零库存只说明没有库存流水，20 行 alert_log、2 张 production_order、5 行 sales_sample_bom 仍引用这些 material_id。F 若直接 ensure(MATERIAL, material_id) 会把半成品入到错误物品上。必须先在只读环境产出映射清单，用户批准后另任务订正。
2）路线边界风险：若从 BOM 行 process_id/行顺序生成工序，会绕过 engineering_routing 的版本/批准/顺序，违反 001；v2 已删除该路径，B/C 不得在 BOM 主表加路线编辑器。
3）主方案存储选型风险：选 A 无逐物料审计、无 DB 唯一/外键；选 B 跨版本漂移；选 C 需要新表审批/基线/清理。三者都要显式选择，不能把未确认的“跨版本独立”当既定要求。
4）迁移顺序错误（先改 NOT NULL 再回填、或先部署不兼容代码）会造成写入失败；必须按“加列→回填→双写→改约束”的顺序，且 M1 早于 M2/M3。
5）前端 material-calculation 现有分配算法若保留，会与后端净需求形成第二权威；E 必须移除/降级。
6）013 卷材逐卷余量未完成，I01/I02 的卷料实际长度只能阻断或使用批次静态数据；不得声称所有余卷场景已通过。
7）历史快照依赖“已批准版本不可原地改”。现有 BOM/路线更新已拒绝已批准状态，但若后续新增入口绕过该检查，G1 会读到变化后的版本；B/F 必须守住该不变量，G1 缺层时返回 SNAPSHOT_PARTIAL 而不是猜。

### 未验证项（不得当成已完成）

1）没有执行任何迁移；M1/M2/M3 只是逻辑组，NN 在实际编写时按账本重取；356/357/358 不是预留号。
2）没有创建 product_material_scheme、requirement_confirm_request；没有登记 model-baseline/清理归属。
3）没有运行后端 mvn、前端 Vitest、vue-tsc；因为本任务只写文档，没有业务代码改动。
4）没有运行真实库存过账、真实工单/领料/入库、真实需求计算；所有 JSON 是契约示例，不是运行结果。
5）没有启动/停止/重启任何服务；没有调用真实 HTTP 接口。
6）现场核查使用只读 jjx_ro，通过本地 0600 defaults 文件连接，文档/证据不含口令；本次只读连接成功，但不代表业务库写入路径已验证。
7）没有修改 AGENTS.md、CONVENTIONS、multi-agent-protocol；没有改任务 002/004—010/013 的状态。
8）inventory_item 身份错位的根因未继续追溯（历史导入/旧 MES 映射），只确认结果；历史单据是否全部未污染未核实，根因排查需另任务。
9）G1 后端快照、G2 前端展示均未实现；本卡只给归属与接口契约。
10）没有做数据库口令轮换/清理历史文件；只保证本文件与本次证据不写口令，历史 .tmp 文件是否含口令需另行核查。

## 现场证据与复核命令

本任务证据目录：/home/administrator/jjx/.tmp/bom-dispatch/。已保存 git 基线、代码行号摘录、14 张表 DDL 原文、身份比对原始 SQL 与输出、迁移账本现状。文档和证据文件不含数据库口令；复核使用本机已配置的 0600 defaults 文件（路径不写进仓库），命令示例只引用环境变量。

关键复核命令（只读，无口令）：

```bash
cd /home/administrator/jjx
git rev-parse HEAD
git status --short
mysql --defaults-extra-file="$JJX_DB_CNF" -h127.0.0.1 -u jjx_ro -N -B jjx_erp_db -e "SELECT config_key,config_value FROM sys_config WHERE config_key IN ('ops.schema.version','ops.schema.applied')"
mysql --defaults-extra-file="$JJX_DB_CNF" -h127.0.0.1 -u jjx_ro -t jjx_erp_db -e "SELECT item_id,bom_id,material_id,module_qty,base_qty,quantity,actual_issue_qty FROM engineering_bom_item ORDER BY item_id"
mysql --defaults-extra-file="$JJX_DB_CNF" -h127.0.0.1 -u jjx_ro -t jjx_erp_db -e "SELECT bi.material_id,m.material_code,m.material_name,i.item_code,i.item_name,CASE WHEN i.inventory_item_id IS NULL THEN 'NO_INVENTORY_ITEM' WHEN i.item_code=m.material_code AND i.item_name=m.material_name THEN 'MATCH' ELSE 'MISMATCH' END AS compare_result FROM (SELECT DISTINCT material_id FROM engineering_bom_item WHERE material_id IS NOT NULL) bi LEFT JOIN inventory_material m ON m.material_id=bi.material_id LEFT JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=bi.material_id ORDER BY bi.material_id"
```

复核入口：evidence-inventory-identity-20261111.txt（身份）、evidence-ddl-20261111.txt（DDL）、evidence-schema-20261111.txt（账本）、code-refs-20261111.txt（代码行号）。

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

本卡完成后只把 dev-20261111-003 置为待审核（status=2），提交本卡两份文档（本文件与 INDEX 登记），推送 origin dev；不自行验收、不派 B 至 G、不改其他任务状态、不启动任何 agent/服务。Codex 独立验收通过后，才按 002 的波次派 B；B 与 C 可并行（C 依赖 B 的接口契约），F 的 M3/接口应在 D 的 MATERIAL 分支验收前完成，D 的 PRODUCT 分支可与 F 并行、MATERIAL 分支在 F 后验收；E 依赖 D；G1 在 B/F 后，G2 在 C/D 与 G1 后；H 最后只读验收。

若用户先批准新表/迁移，B 开工前仍需：按当次账本重取迁移编号、手工备份、批准记录写进 model-baseline、清理归属登记、检查同文件在制。任何一项缺失就停相关部分，不把“文档契约”当成“已经实施”。
