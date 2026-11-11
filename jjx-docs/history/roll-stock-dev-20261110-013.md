# 卷材逐卷收发退实施与验收交接（dev-20261110-013）

执行：Codex-roll-013；独立验收：OpenClaw-lead（用户最新分工，替代早先主Codex验收；原主Codex仅转述协调）。隔离分支 `task/dev-20261110-013`，基线 `e05e6d90`，核心阶段提交 `b1776358`。本报告记录代码/迁移准备及隔离测试，不宣称已完成真实库部署和工单全链验证。

## 已实施的业务路径

1）原采购收货生成的入库凭证，沿用既有 IQC 提交、逐项审核及允收判定。全部审核通过、原明细尚未过账时，在入库详情的「卷计划」中录入每卷的卷号、宽度 mm、实际长度 m。后端按库存统一精度计算各卷面积，合计必须等于原明细 `accepted_quantity`，而不是原 `quantity`。
2）确认原入库凭证：沿用原采购/IQC/差异收货资格校验，锁原单和原明细；每卷生成库存位置行和对应流水，共用原来源明细/IQC/采购来源，原明细 `posted_quantity` 只更新一次。原数量、价格、质量判定和来源不变，不删原行、不另建 other 入库单绕流程。
3）普通库存出库新增/草稿编辑时搜索具体卷，实发为该卷完整当前面积，按真实库存行成本过账。未选择卷的旧 FIFO 路径遇卷材会明确拒绝，避免隐式拆卷。
4）余卷退仓：即时库存主列表保持面积汇总；在批次明细的已发卷发起「余卷退料」，关联最近一次已完成原出库行，输入实测余长、退入仓库/库位和原因。新增普通退料入库凭证和流水，沿用原卷编号与原发料成本；库外未退部分不会自动认定为实际消耗。
5）红冲：从卷流水创建反向凭证，保留原始入/出库量。已再发或预留的退卷不能红冲；发料存在净退料时先冲退料；已后续再发不能冲旧出库；重复/错单/错卷/超退均拦截。
6）工单整卷实发与 BOM 需求覆盖/超领/预留/生产成本由008接入。013保留旧生产规则；没有受控来源 BOM 明细的 work_order 卷行禁止用普通 create 绕过。不能把普通库存测试解释为已完成整套生产链。

新增入库逐行录单卷也可使用，但采购补录以原采购凭证卷计划为正式路径。部分允收只录实际允许入库的物理卷尺寸，未允收部分继续原隔离流程；系统不会按面积自动推断实物如何切分。

## 数据模型与边界

1）库存以㎡为唯一余额，所有数量写入仍通过 `InventoryStockMutationService.applyDelta`，流水 append-only。后端面积公式：`width_mm * length_m / 1000`，面积统一4位小数。剩余长度展示值：`quantity * 1000 / roll_width_mm`，不另存余长余额。
2）`inventory_stock_item.item_id` 表示卷所在仓库/库位的库存行；`roll_no` 是稳定物理卷身份。出库后原行可保留0余额，换位置退回可新建位置行。卷号与供应商/IQC批号独立，不占用质量 `lot_id`。
3）原始卷宽、首次实收卷长、原始入库明细保留，退回不把原始50m改成25m，不生成新规格料号。结构化主档有宽度时，实收宽度须匹配该料号宽度。面积单位和统一库存身份必须匹配主档，不能自动修旧身份。
4）同一采购来源明细可以生成多卷：各卷 `source_inbound_item_id` 和初始流水 `source_item_id` 均指向原明细；原明细不冒充只有一个 `stock_item_id`。单卷明细保留该便捷关联，多卷用卷计划及流水/库存行追溯。
5）`roll_plan_json` 只保存计划卷号/原始宽长，不保存可独立修改的面积副本。保存只写该计划字段；过账后计划冻结。确认会按当前允收量重新核对计划，允收变更、占用卷号或任一卷失败都整事务回滚。
6）退料单价来自原已过账出库流水，不取当前物料价格或零余额库存的当前估价。原始出库单数量不减写，反向单使用负数，库存流水保持正确符号。

迁移357仅扩展现有四表，无新表：

| 表 | 新字段/索引 |
| --- | --- |
| inventory_stock_item | roll_no、roll_width_mm、original_roll_length_m、source_inbound_item_id；roll_scope扩展原位置批次唯一索引；active_roll_key保证同物理卷最多一处正余额 |
| inventory_transaction | stock_item_id、source_item_id、roll_no及逐卷索引；不回填/修改历史流水 |
| inventory_inbound_item | stock_item_id、roll_no、roll_width_mm、roll_length_m、source_outbound_item_id、reversal_of_item_id、roll_plan_json |
| inventory_outbound_item | stock_item_id、roll_no、reversal_of_item_id |

正式脚本：`jjx-docs/sql/migrations/357_roll_stock_metadata.sql`。主会话已分配编号，写前只读核对目标 `ops.schema.version=356`、主仓库及隔离工作区迁移最大号均356。脚本使用幂等列/索引检查；未执行真实DDL。唯一索引修改为高风险变更，执行前须主会话确认用户目标库人工备份。已有逐卷数据后，旧代码的同批单行查询不兼容多卷，不可只退应用代码或直接删列/还原旧唯一索引，需要单独评估回退方案。

## 给008的稳定接口

| 能力 | 接口/约束 |
| --- | --- |
| 整卷选择与确认校验 | InventoryRollService.issueStock(order,item)：明确stockItemId，完整当前面积、所属物料/仓库/库位匹配、无未释放预留；工单行须受控sourceBomItemId |
| 余额变动 | InventoryStockMutationService.applyDelta：卷材必须有原卷元数据、源明细；写stock_item_id/source_item_id/roll_no，不允许无追溯键的旧路径拆卷 |
| 普通余卷退料 | POST /inventory/roll/return：sourceOutboundItemId、rollNo、returnedLengthM、warehouseId/locationId、reason；原成本/上限/最近发料由后端取得 |
| 退料/发料红冲 | POST /inventory/roll/reverse-return/{itemId}、/reverse-issue/{itemId}：原因必填，原单不改，事务性反向凭证 |
| 原采购明细计划 | POST /inventory/inbound/roll-plan/{itemId}：rolls数组仅卷号/宽mm/长m；原IQC批准且未过账，合计匹配accepted_quantity |

008不得把包装实发面积直接等同 BOM 覆盖/实际消耗；应从受控生产领料路径生成来源 BOM 行，再衔接包装覆盖、逐卷预留、生产成本净额。013的普通退料只改变库存凭证/流水，不重算工单既有 materialCost。

## 同批多卷的门禁

`scripts/check-stock-summary.sh` 保持原五类检查：

1）汇总与生效库存明细合计一致；2）有明细无汇总；3）有汇总无明细。
4）先按 `inventory_item_id + batch_no` 聚合所有库存位置行，再与批次流水合计比较；新增按 `stock_item_id + roll_no` 的逐卷余额检查，历史无行ID流水不能给新卷凑余额。
5）保留批次孤儿流水检查，再校验新卷流水库存行、物品、批次、仓库、库位、卷号及源明细身份。

逐卷结构未应用时明确打印旧结构检查说明；部分迁移或SQL失败在严格模式失败，不跳过原检查。没有弱化旧批次合计，也没有给历史流水补键。

## 验证证据与实际边界

隔离 H2 使用真实 Mapper、真实事务代理和统一库存入口；质量审核已批准记录/外部质量服务为测试夹具，不代表真实 IQC API 全流程。关键用例：

1）原采购汇总明细487.5㎡挂50卷195mm×50m计划；确认后50库存行、50对应流水，原行不拆不改。出2卷后468㎡，其中1卷退25m后472.875㎡，48完整卷+1余卷、49物理卷在库。
2）原收货29.25㎡、允收19.5㎡，计划2卷各9.75㎡；确认仅入19.5㎡，原quantity/IQC/source不变，质量stored回写净允收量。
3）换仓库/库位退回卷号稳定，保留原尺寸与原入库来源；最近一次发料关联、错卷/超退/重复/红冲限制及原成本冻结。
4）第二卷流水失败时原单状态/posted、全部库存行、汇总与流水回滚；并发双发、双退、双confirm以及保存计划/confirm串行化，不重复入卷。
5）门禁测试直接提取脚本SQL，H2仅替换null-safe比较方言执行：同批多卷正常；故意给新卷掺历史无行ID流水时批次虽平账，逐卷检查仍报错；错库位身份由孤儿检查抓到。
6）非卷材原路径回归；旧FIFO不静默拆卷；普通create不能伪装工单卷材领料。

后端最终定向8组39例通过，前端2组4例通过，vue-tsc --noEmit通过；详情以任务remark及本次执行日志为准。验证命令受原资源限额：Maven768M+fork512M/180s；Vitest单worker1G/120s；vue-tsc3G/300s。首次33例和vue-tsc阶段证据已通过；采购扩展最终复跑39例无失败/错误。

`npm run validate` 已执行：仍在仓库原有两张未批准表 `inventory_material_spec_attr_def`、`inventory_material_spec_value` 基线处失败，未扩baseline/绕门禁。其余独立门禁status-enums/result-case/docs/collation/migration-collation/notnull/stock/lot/iqc-lineage/quality-ledger/doc-no通过；真实库stock strict五项0且明确提示逐卷结构未应用，只能证明当前空库一致性。

真实只读核查：本地 `inventory_stock_item`、`inventory_transaction` 本次只读核查均0行。AUX000106主档material110、M2、width_mm195、length_mm空；库存身份110却仍是MTR202608130111/PCS。AUX000107/347主档单位M、旧库存身份也是MTR/PCS。013未清理身份，也未改主档单位；不匹配会被明确拒绝。不得把H2数据或空库门禁称为正式库生命周期完成。

## 交接与遗留

1）代码和357迁移准备供OpenClaw-lead独立审查；最终提交见任务码git记录。普通push如被旧表基线拒绝，原样报告，不使用no-verify。
2）真实DDL、人工备份确认及已知库存身份映射/订正另由主会话取得具体授权后处理。本次未执行迁移、未启停任何服务。
3）迁移和身份问题处理后，仍需真实MySQL/API从原采购凭证录卷→确认→整卷出库→余卷退回/红冲→stock strict全0验收。H2不替代真实迁移/索引/并发锁行为验收。
4）008负责生产包装覆盖、受控领料预留和材料成本净额；013不宣称已完成生产工单端到端。无自动选卷、裁切排版或多产出扩展。

交接时已按要求读取最新sys_task013全记录；主仓`.tmp/bom-dispatch/to-roll-013.md`截至本次收尾检查尚未生成。未改变已批文件和工程边界。
