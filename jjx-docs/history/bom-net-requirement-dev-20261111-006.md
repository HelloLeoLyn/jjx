# 多层制造净需求（D阶段）只读试算实施记录

任务：dev-20261111-006（总任务 dev-20261111-002 子任务 D）。执行者：OpenClaw；验收者：Codex（独立验收）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：第一步只读试算代码、服务端可信供给适配与定向测试已准备；未执行 DDL、未建表、未启动服务、未备份、未订正库存身份；持久 confirm/并发分配未实现。整卡保持进行中，不宣称 D 全通过。

## 交付边界

本卡第一步只写 003 D 节新增文件白名单及本报告、INDEX、.tmp 本卡证据：

1）ManufacturingRequirementService / Impl
2）ManufacturingSupplySnapshotProvider / Impl
3）ManufacturingSupplySnapshotMapper
4）RequirementPreviewRequest / RequirementConfirmRequest（后者仅契约占位）
5）RequirementNodeVO / RequirementLineVO / RequirementSummaryVO
6）ExactQuantity / ManufacturingDemandSnapshot
7）ManufacturingRequirementController
8）ManufacturingRequirementPrecisionTest / NettingTest / SupplyTest

未改 003 列出的 InventoryOutboundServiceImpl、InventoryAlertServiceImpl、ProductionBomResolver、既有 Entity/DTO/Mapper、前端、迁移 SQL、备份、356 归档和 002 追踪 WIP。未新增表、未写迁移、未启动或重启任何服务。

## 实现要点

1）精确有理数：ExactQuantity 用 BigInteger 分子/分母，decimal 按 unscaled/10^scale 精确转换并在构造时约简；加乘除全程保持比例，只在最终展示生成 12 位近似值。6 模 1 基数×300 为 50/1；1/3 中间值再乘 3 为 1/1，不在中间层 CEILING 或转 DECIMAL128。
2）损耗：loss_rate 由新只读查询按 BigDecimal 读取，避免旧实体 Integer 把 0.50 截断为 0；损耗因子 1+loss/100 为有理数，0.50% 精确为 201/200，只在本层计一次。
3）展开关系：只按 engineering_bom_item.sub_bom_id 展开，不推断 parent_material_id 或 source_type 背后的隐藏关系。DFS 检测循环/悬空；校验下层 BOM 产出身份为 MATERIAL 且 output_id=本行 material_id；校验批准状态、生效/失效日期；make 无方案返回 MAKE_WITHOUT_SCHEME，buy 挂方案、未知 source_type 明确报路径，不静默跳过。
4）多层汇齐与净额：静态构建 BOM 依赖图与父 BOM 计数，等同一物料的全部上游贡献汇齐后再净额；所有来源先换算到库存档案单位，按需求日期、稳定来源顺序确定性分配库存、生产供给、采购在途；每个来源的剩余缺口按各自 sub_bom_id 展开，不因按物料汇总而任选一个 BOM。公共库存跨分支、不同深度同料只分配一次。
5）供给口径：inventory_stock.total_quantity − total_reserved；材料级再减 inventory_material.safe_stock（与 InventoryAlertServiceImpl 现行口径一致）。仓库维度用 inventory_stock_item 汇总，不硬扣材料级安全库存并明确告警。库存抵扣只读，不写余额/预留/流水。
6）采购供给（第二步已修口径）：`purchase_order_item.quantity − COALESCE(received_quantity,0)` 按行取 `max(…,0)` 后求和，订单收货状态 IN (0 待收货, 1 部分收货)，因此部分收货只计未收部分；只有 `approval_status=已批准(3)` 计入确定供应 `purchaseSupply`，草稿(1)/待审批(2) 只进 `plannedPurchaseSupply` 展示、不抵扣缺口，已驳回(4)/已取消(5) 在 SQL 层排除（不沿用旧 `IN (1,3,4)`）；超收行按 0 计并告警，负数不冲抵其他行；采购行单位与库存单位不可比且无 `unit_alt/unit_conv` 依据时排除并告警，卷类一律指向 013。第一步曾把草稿/待审批当作可用，已由本步纠正。
7）生产供给兼容读适配（第二步已修口径）：production_order 无 MATERIAL output 列，查询通过工单绑定 engineering_bom 的 output_type=MATERIAL/output_id 识别物料产出，不用 product_id 猜物料；只计入未绑定 sales_order_id/parent_order_id 的 WORK_ORDER，避免把已承担其他需求的产出重复抵扣。只认 `o.approval_status=已批准(3)`（ApproveStatusEnum，见 ProductionOrder 实体字段注释 PENDING/APPROVED/REJECTED/CANCELLED）且绑定 BOM 已批准、`effective_date ≤ 需求日 ≤ expiry_date`（空日期只告警不阻断）；产出量 = `planned_quantity − finished_quantity`（合格/入库口径：QualityFinishServiceImpl 回写 finished，InventoryInboundServiceImpl 2854 行明确入库用成品检验合格数而非工序汇总 completed），不用 remaining_quantity（下达后不随完工递减，会与已入库成品重复）。工单状态只取 4 已计划/5 待开始/6 进行中；8 已完成/9 已取消/10 已关闭/11 已超期不计，超期工单实际进度不可核实，按在制计入会高估供给。顶层产品需求不抵扣既有产品工单，因为没有需求分配关联。
8）只读接口：POST /production/requirement/preview，复用 production:order:view 或 inventory:alert:view，Result 封装。不暴露 confirm 成功接口；RequirementConfirmRequest 只保留契约字段与幂等键说明。
9）输出分层：理论耗用、损耗后毛需求、聚合展示值、库存/生产/采购分配、缺口、最低投料、生产批量、实领粒度分列；生产批量与实领粒度没有可信配置时保持空值，不拿 min_order_qty 或 min_issue_qty 冒充。精确比例与展示近似值分开返回。

## 验证

执行（jjx-server，先查内存：本机 available 约 4.2G，未与 005 vue-tsc 重叠）：

    MAVEN_OPTS='-Xmx768m' mvn -o -Dtest='ManufacturingRequirement*Test' \
      -DfailIfNoTests=false -DargLine='-Xmx512m' test

结果：26 项全过（Precision 7、Netting 13、Supply 6），0 失败 0 错误。覆盖：

1）D01 6模1基数×300=50 不是 51；D02 上层 1 片、下层 6 模 1 基数不再除 6；D03 正小数 2.5/0.5；D04 0.5% 损耗不截断为 0；模数 0、负损耗拒绝。
2）N01 需求300库存200占用60→可用140缺160；N02 按期未占用采购/生产供给60→缺100；N03 两分支各100库存140→总缺60且来源分配100/40；N06 同物料第二/第四层汇齐后只扣一次库存；同物料两个方案按各自 sub_bom_id 展开 40/100。
3）0 负数、坏单位、卷材缺批次长度（013）、深环、悬空、自制无方案、根 BOM 未批准、越权注解、只读 preview 无假 confirm。
4）SupplyTest 使用隔离 H2（MySQL 模式）+ 真实 MyBatis Mapper/Provider/Service，验证库存分量、安全库存、仓库维度、采购状态/日期/已收扣减、生产 BOM 产出身份与已分配工单排除；重复 preview 前后库表行数与库存数量不变。

边界：H2 与 Mockito 只证明代码/查询逻辑，不冒充真实 MySQL 写入并发、真实 BOM 生产闭环或 F/H 验收；当前真实库 4 个 BOM 均无 sub_bom_id 引用，未用真实 BOM 跑多层闭环。未启动服务，因此未调用 HTTP 接口；未跑 npm run validate/check:stock:strict，因为本卡没有库存写入路径，只做只读查询，不满足“改了写入路径”的前提。

## 未验证与限制

1）真实 MySQL 上未执行 preview HTTP 调用；只读 SQL 已在 H2 等价表验证，真实库字段/权限需运行态复核。
2）真实数据无显式 sub_bom_id 制造方案，未做真实多层生产闭环；实际库存身份错位未订正，也不以零库存证明身份已对齐。
3）卷材逐卷实际长度/余量属于 013，本卡遇到宽度有、批次长度缺失时阻断，未用统一 50m 代替。
4）仓库维度未分摊材料级安全库存，已在响应告警；分配规则需后续统一。
5）顶层产品需求不抵扣既有产品工单；跨请求/跨产品的物理库存共享只做单次只读试算，不承诺持久分配。
6）持久确认、请求级幂等、分配行唯一键、事务锁、失效供给重算、迁移兼容回退与替代评估均未实现，属于后半步。

## 持久适配的精确影响文件清单（待 Codex 补派后修改）

loss_rate 库列为 decimal(5,2)，旧 Java 类型仍为 Integer 且部分计算使用 double。本卡新模型已用 BigDecimal/有理数，不改既有类型；后续统一需评估：

1）jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringBomItem.java（lossRate Integer→BigDecimal）
2）jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringBomItemDTO.java（字段类型/校验）
3）jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomItemVO.java（回显类型）
4）jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringBomServiceImpl.java（损耗/应用料/派生数量计算）
5）jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java（实领/批量取整与损耗）
6）jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryAlertServiceImpl.java（可用量与在途口径复用）
7）jjx-web 侧 BOM 比例/损耗类型（005 范围内，需与后端统一后接入小数）
8）相关既有测试夹具与前端类型定义

## 后半步停止条件

确认/分配需要新表或订正时，先交完整可审阅方案与不可复用证据，不执行 DDL/新表、不暴露假 confirm 成功接口、不把整卡置完成。至少需明确：请求头表与请求级幂等唯一键、分配行 UNIQUE(request_id, allocation_line_no)、同键不同请求体冲突、事务锁、失效供给重算、迁移兼容回退及替代评估；order_material_reserve 现有销售订单语义，不能塞假销售订单。

## 证据

本卡 git status 基线：.tmp/bom-dispatch/git-status-20261111-1507.txt。
定向测试日志：.tmp/bom-dispatch/（本轮补齐）与 /tmp/d-test4.log。

## 第二步：供给口径修复（2026-11-11，执行者 Hermes）

按卡面 1–9 逐项修复，仍为只读试算（不扣库存、不预留、不写流水、不建工单、不新增表）：

1）采购确定供应只认 `approval_status=已批准(3)`。草稿(1)/待审批(2) 移入新字段 `plannedPurchaseSupply`（行 + summary 汇总），只展示、不抵扣缺口；summary 增加“未批准数量合计”告警与 limitations。已驳回/已取消在 SQL 层排除，不沿用旧 `IN (1,3,4)`。
2）部分收货按行扣减：`quantity − COALESCE(received_quantity,0)`，收货状态 IN (0 待收货, 1 部分收货)；超收（received > quantity）行按 0 计并告警，不让负数冲抵其他行。
3）采购行单位可比性：采购行单位 ≠ 库存单位时，只有物料档案 `unit_alt/unit_conv` 明确对应才折算（并在 warning 写明换算来源）；否则排除该行，warning 列出物料/订单/行/数量，关键字 UNIT_CONVERSION_MISSING。
4）仓库维度：`warehouseId` 非空时采购/生产供给不参与抵扣（两类订单都没有可核对的收货目的仓），仍在 `purchaseSupply`/`productionSupply`/`plannedPurchaseSupply` 展示，summary 给“无法归属仓库，已不计入本次抵扣”告警；现货仍按 `inventory_stock_item` 过滤。
5）生产供给：要求 `o.approval_status=已批准(3)`；绑定 BOM 需已批准且 `effective_date ≤ 需求日 ≤ expiry_date`，空日期只告警；产出改为 `planned_quantity − finished_quantity`（合格/入库口径，证据见 QualityFinishServiceImpl 回写与 InventoryInboundServiceImpl 2854 行），≤0 不计；状态白名单保持 4/5/6，8/9/10/11 不计，OVERDUE 在 limitations 写明“实际进度不可核实，按在制计入会高估”。另加单位可比性检查（production_order.product_unit）。
6）卷类换算：`卷/ROLL/COIL` 禁止用 `inventory_material.width_mm×length_mm` 自动换算；只有已保存 `unit_factor`+依据才折算，否则报 UNIT_CONVERSION_MISSING 并指向 013。非卷定尺件（片/张）保留原定尺换算，`unitFactorSource` 写 `inventory_material.width_mm/length_mm`。
7）`materialProductionAdapterApplied` 改为真实标志：仅当该物料存在 `output_type=MATERIAL` 的制造 BOM（确实走了产出身份兼容读）时为 true。
8）只读语义与“未分配”提示：VO/summary 明确“采购/生产供给未分配、可能被其他需求竞争”。
9）请求校验：`warehouseId` 非空时只读校验 `inventory_warehouse`，不存在或 `status≠'1'`（StatusEnum.NORMAL，与 InventoryWarehouseServiceImpl.getAllEnabled 同口径）直接报错。

VO 字段变更（同步前端契约时注意）：`RequirementLineVO` 新增 `plannedPurchaseSupply`，`purchaseSupply` 语义收紧为“仅已批准未收”，`materialProductionAdapterApplied` 改为真实值；`RequirementSummaryVO` 新增 `plannedPurchaseSupply` 汇总。

验证（jjx-server，先 `free -g`，`MAVEN_OPTS='-Xmx768m'`，fork `-Xmx512m`，`flock /tmp/jjx-heavy-check.lock`）：

    mvn -q -o -Dtest='ManufacturingRequirement*Test' -DfailIfNoTests=false -DargLine='-Xmx512m' test

结果：38 项全过（Precision 7 / Netting 13 / Supply 5 / SupplyPolicy 13），0 失败 0 错误。新增 SupplyPolicyTest 覆盖卡面测试 1–9（部分收货 60、草稿/待审批不抵扣、驳回/取消不出现、超收不倒挂、单位不一致排除/有 unit_conv 折算、仓库维展示不抵扣、工单审批与 BOM 有效期、planned−finished、卷料阻断与有 factor 折算、定尺片材保留），并把 H2 夹具抽到 AbstractManufacturingRequirementTest。

真实库只读核对（jjx_erp_db，仅 SELECT）：purchase_order 0 行、purchase_order_item 0 行 → 真实库采购供给恒为 0；production_order 2 行均为 approval_status=0、order_status=1，且绑定 BOM 非 MATERIAL 产出 → 生产供给恒为 0；inventory_warehouse 2 个仓库 status 均为 '1'。即本次口径修复在真实库暂无可观察差异样本，行为证据以 H2（MySQL 模式）SQL 语义测试为准；真实业务需补采购单/工单审批后才有可观察数据。

未验证：未启动服务，未调用 HTTP preview；未做真实并发/事务验证；confirm 仍只交设计提案 `bom-confirm-design-dev-20261111-006.md`，未执行 DDL、未建表；INDEX 登记待 OpenClaw-lead（当前 INDEX 在 013 手上）。
