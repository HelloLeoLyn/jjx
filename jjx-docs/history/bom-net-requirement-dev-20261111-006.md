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
6）采购在途：purchase_order_item.quantity − received_quantity，收货状态待收货，预计到货日非空且 ≤ 需求日；状态复用 ApproveStatusEnum 的草稿/待审批/已批准。既有 InventoryAlertServiceImpl 的 SQL 字面量 IN (1,3,4) 按 ApproveStatusEnum 实际含义会把已驳回(4)计入、漏掉待审批(2)，本卡新查询按枚举修正并在测试覆盖，不改旧服务。
7）生产供给兼容读适配：当前 production_order 无 MATERIAL output 列，查询通过工单绑定 engineering_bom 的 output_type=MATERIAL/output_id 识别物料产出，不用 product_id 猜物料；只计入未绑定 sales_order_id/parent_order_id 的 WORK_ORDER，避免把已承担其他需求的产出重复抵扣。顶层产品需求不抵扣既有产品工单，因为没有需求分配关联，直接抵扣会把其他订单产出误算给本次。
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
