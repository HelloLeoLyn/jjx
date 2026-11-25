# 生产领料库位分配任务书（方案 C）

- 任务码：dev-20261125-004
- 版本：v2（2026-11-25，按用户本轮确认修正）
- 执行：Codex
- 前置阅读：/home/administrator/jjx/AGENTS.md、jjx-docs/standards/multi-agent-protocol.md、jjx-docs/standards/CONVENTIONS.md

## 1. 目标

生产领料单生成后，把每条物料需求按 FIFO 分配到具体库存批次与库位，形成可执行的拣货依据；支持打印「领料单」和「拣货单」；跨供应商混批需审批。**本方案不做库存预占**，不影响缺料预警、齐套检查与采购建议。

## 2. 已定口径（方案 C）

1）分配只写单据字段，不写预留表、不改 reserved 数量、不改库存汇总
2）缺料预警/齐套/采购建议的可用量口径保持不变
3）发料确认时按明细批次扣减；若批次已被别的单领走，提示「批次不足，请重排」，不静默改扣其他批次
4）跨供应商混批：提示并需审批；同供应商多批次只提示
5）预留（预占）不在本次范围，后续单独立项

## 3. 当前代码现场（必须先读，不要重复实现）

- 提交 `ff2d8a6f`：已新增 `allocateProductionPick`（后端）
  - 位置：`InventoryOutboundService(Impl)`
  - 行为：按 FIFO 跨库位取批次、仅取本单仓库、已分配行跳过（幂等）、需求跨批拆行、未分配部分保留空库位欠料行、完成后重算单据合计（原基线存在欠料重复、跨行重复分配等缺陷，以本轮守恒规则修正）
  - 生成领料单时自动调用，失败仅告警
  - 接口：`POST /inventory/outbound/{outboundId}/allocate`（权限 `inventory:outbound:edit`）
- 提交 `4044ff92`：出库详情弹窗已加「按 FIFO 分配库位/批次」按钮，调用上述接口并刷新
- 原基线未完成：领料单/拣货单打印布局、跨供应商检测与审批、发料按批次校验；本轮在基线上修复，不回滚两个提交

## 4. 范围

### 4.1 后端

1）分配能力完善（在现有 `allocateProductionPick` 基础上）
   - 校验：只允许 `outbound_type=production`；状态在 待发料/已批准 前可分配
   - 同仓库校验：批次仓库必须等于单据仓库，不一致不分配并计入欠料
   - 幂等及重排：允许覆盖未发料状态的旧分配；重复调用不增加需求和行数，同一批次跨需求累计分配不得超过可用量
   - 拆行：同一需求跨批次时按批次拆行，保留 `sourceBomItemId`，`coverageQuantity` 只保留在原行，避免重复抵扣
   - 欠料行：同一需求只保留一条，数量=原需求−已分配；stock_item_id/location_id/batch_no 为空。严禁保留原行全量后再叠加欠料；拆行+欠料合计必须等于原需求

2）跨供应商混批检测
   - 判定：同一领料单内，同一 material_id 命中的库存批次涉及 ≥2 个不同供应商
   - 数据来源：`inventory_stock_item` 的供应商字段（若批次表无供应商，需从入库明细/检验批追溯，实施前先核对）
   - 结果：返回 mixedSupplier=true 及明细（物料、供应商、批次、数量）；供应商未知单列，禁止当同供应商，审批和发料均阻断

3）混批审批
   - 新增权限点：`outbound:pick:mixed-batch:approve`
   - 审批动作走现有 `review_flow`（意见写流水，不覆盖业务表 remark）
   - 状态：新增出库单状态「待混批审批」（枚举新增，不改表结构）；通过后转现有「已批准」可发料状态，驳回转现有「已驳回」并必填原因；审批绑定分配内容指纹，改批次/改数量后失效
   - 未通过审批不得发料
   - 审批人由角色授权决定，不写死

4）发料校验与重排
   - 确认发料时按明细 `stock_item_id` 扣减，校验该批次可用（结存 − 他单预留）；自有预留只读识别，不消费或释放，不修改 reserved_quantity/order_material_reserve
   - 不足：返回明确错误（物料、批次、缺口），提供「重排」接口（重新执行分配）
   - 不得静默改扣其他批次

5）详情返回
   - `OutboundItemVO` 增加：库位名称、批次、卷号、供应商（用于打印与混批提示）
   - 明确不返回预占归属（本方案不预占）

### 4.2 前端

1）领料单生成/详情页
   - 主表保持一物料一行：物料编码/名称/项目/规格/单位/跳距/需求/实发/库存/状态
   - 父件（半成品）作为灰色分组行，不可编辑
   - 点击物料行 → 下方明细区只显示该物料的批次

2）下方批次明细
   - 列：批次号/卷号/原始尺寸/仓库/库位/结存/可用/本次出库数量/操作
   - 「本次出库数量」可编辑；上限 = 结存 − 他单预留；同一批次扣除本单其他需求已分配量；卷材只能整卷，禁止拆半卷或自动超需求分配
   - 行内「填满/清零」；顶部「按 FIFO 重新分配」「清空本次数量」
   - 合计行：需求/已分配/差额，差额非 0 红字
   - 修改数量通过新增受控分配保存接口落明细，禁止复用删行重建的 update；保留 sourceBomItemId/coverageQuantity 及替代依据，保存带分配指纹，拦截过期草稿

3）混批提示
   - 物料行标「混批/跨供应商」徽标，悬浮显示供应商与批次
   - 默认样式：标签放物料名称后面（`混批` / `跨供应商` 小标签），不单独占列
   - 手工保存跨供应商分配前弹确认，自动分配生成跨供应商分配时进入待混批审批；未知供应商阻断审批和发料

4）打印
   - 领料单：一物料一行（序号/物料编码/名称/单位/需求/实发），带签字行
   - 拣货单：按库位排序（库位/物料/批次·卷号/拣货数量），同库位连续
   - 只打印当前选中的单据
   - 默认入口：页面右上角「打印」下拉（领料单 / 拣货单），不在行内放打印按钮

### 4.3 明确不做

1）不写 `order_material_reserve`、不改 `reserved_quantity`；生产领料建单不调用旧预占，确认/取消/驳回/重提不消费或释放预占。打样与销售原有逻辑保持
2）不改缺料预警、齐套检查、采购建议的可用量口径
3）不做预占释放逻辑
4）不改销售订单预留逻辑

## 5. 验收标准

1）生成领料单后自动分配：明细按批次/库位拆行，字段有值；重复执行不产生重复行
2）同仓库过滤生效；库存不足时保留欠料行，不伪造分配
3）缺料预警/齐套/采购建议在分配前后结果一致（用同一数据对比）
4）跨供应商混批：未审批不可发料；审批通过后可发料；审批记录可在 review_flow 查到
5）发料时批次不足，返回明确错误并可重排；不出现静默改扣
6）打印：领料单一物料一行；拣货单按库位排序；打印内容与页面一致
7）静态验证：`mvn -o compile`、`vue-tsc --noEmit`、相关定向单测通过

## 6. 风险与注意事项

1）批次供应商字段来源需先核实（stock_item 是否直接带 supplier_id）
2）新增出库单状态值需检查前端状态映射与列表筛选
3）发料按批次扣减会改变现有 FIFO 行为，需覆盖回归：普通出库、样品领料、补料
4）打印模板改动涉及 QR-031 领料单留痕，需保持编号与模板一致
5）不要修改/回滚他人未提交改动；提交只含本任务文件

## 7. 用户补充验收与实施顺序

1）原需求=已分配+欠料，重复两次调用行数和数量不增加
2）同物料多条需求共享批次时累计分配≤结存−他单预留
3）重排确实覆盖未发料的旧批次，普通批次不走整卷分支，卷材无半卷
4）未知供应商阻断审批和发料，跨供应商改分配后旧审批失效
5）分配不新增或释放预占，缺料预警、齐套、采购建议口径与结果不变
6）实施顺序：分配守恒 → 受控保存 → 指定批次发料/重排/边界 → 混批审批 → 页面及两种打印
7）分层：分配、身份、需求守恒、审批、发料校验在后端；页面物料聚合、打印聚合、库位排序和编辑草稿在前端，不增加展示持久字段
8）相关白名单：InventoryOutboundServiceImpl/Controller、ProductionPickAllocationService、InventoryStockItemMapper、前后端出库枚举、outbound API/types、出库详情/确认/打印组件、定向测试、本任务文档和权限配置SQL；其余只读
9）沿用 FF2D8A6F/4044FF92；PickDetailTable.vue、PickIssueDialog.vue 已有未提交改动经用户明确确认可承接提交；不包含无关备份改动

## 8. 交付

1）代码 + 定向测试 + 必要的迁移（本次预计无新表；若新增状态枚举则无需迁移）
2）提交信息带任务码 `dev-20261125-004`
3）完成后回写任务卡：status=2、completed_time、操作人、remark（改动清单 + commit + 验证结果 + 遗留）

## 9. 本轮交付与验证证据（2026-11-25）

1）实现：ProductionPickAllocationService 负责锁单、跨需求批次累计、普通批次/整卷分配、唯一欠料、受控保存、指定批次校验及混批审批。原 allocateProductionPick/接口保留并委托该服务，不回滚 ff2d8a6f、4044ff92。生产各建单入口只分配，不再新增预占；确认、取消、驳回、重新提交不消费或释放预占。打样与销售逻辑不变。
2）保存：PUT /inventory/outbound/{outboundId}/allocation，带 expectedFingerprint 和 demandItemId/stockItemId/quantity；保留 BOM/替代/抵扣依据，显式清空过期批次绑定，抵扣量只保留在组内原行。GET 同路径返回来源批次、供应商、库位与可用量。
3）审批：POST /inventory/outbound/{outboundId}/mixed-batch-review，权限 outbound:pick:mixed-batch:approve。新增 MIXED_BATCH_PENDING 枚举；review_flow 的 biz_type=production_pick_mix，意见写新流水，extra_json 记录分配指纹。修改分配后既有混批审批失效；未知供应商阻断审批及发料。
4）供应商：从批次 source_inbound_item_id（缺失时沿 iqc_batch_id 的 source_inbound_item_id）到入库单 supplier_id/supplier_name 追溯。没有可信来源时标未知，不按名称或批次号猜供应商。
5）前端：详情/确认共用 ProductionPickAllocationPanel，一物料一行、保留虚拟半成品分组，下方按需求来源展示候选批次与编辑数量。普通批次数量上限同时受本需求剩余量和该批次可用量约束；卷材只能选择整卷。领料单按物料聚合需求/实发，拣货单按库位排序，使用当前 URL 单据 ID；保留既有打印留痕。未发料单的实发为0，分配数量单独展示。
6）配置：production_pick_mixed_permission_dev-20261125-004.sql 是幂等权限配置新增，已执行并只读回查得到 sys_menu.menu_id=437、parent_id=33，无DDL、不写迁移账本、不改任何既有角色授权。实际审批人由管理员在角色管理授权；沿用超级管理员现有权限机制。

### 验证结果

1）后端正常编译：MAVEN_OPTS=-Xmx768m timeout 180 mvn -o -q -f jjx-server/pom.xml -DskipTests compile，退出0。
2）后端定向测试：56例通过，0失败、0错误。包含 ProductionPickAllocationTest 10、ProductionPickAllocationMysqlTest 5、ProductionMakePickTest 10、InventoryPickReservationLifecycleTest 13、InventoryOutboundInvariantTest 10、ProductionPickPrecisionTest 8。
   A) 常规 mvn test 在 testCompile 被既有 DeliveryCapacityServiceTest 第40/52行阻断：仍引用 selectByInventoryItemIdForUpdate，当前实际 Mapper/服务使用 lockByInventoryItemId。本卡未修改该销售测试或销售代码。
   B) 定向执行使用从原 pom 生成的临时 .tmp/pick-test-pom.xml，仅在 compiler.testExcludes 排除上述已有编译失败文件，sourceDirectory/testSourceDirectory/directory 指向 jjx-server 原目录，其他依赖和生产编译配置沿用。执行 JJX_PICK_MYSQL_TEST=1 MAVEN_OPTS=-Xmx768m timeout 240 mvn -o -q -f .tmp/pick-test-pom.xml -Dtest=ProductionPickAllocationTest,ProductionPickAllocationMysqlTest,ProductionMakePickTest,InventoryPickReservationLifecycleTest,InventoryOutboundInvariantTest,ProductionPickPrecisionTest -DargLine=-Xmx512m test。
   C) MySQL 用例在单连接内创建同构临时表遮蔽所需业务表，使用真实 Mapper、JdbcTemplate、事务及真实库存变动服务，连接关闭自动移除临时表，不改持久业务数据。验证两次分配守恒、跨行合计不超批次、清空绑定后唯一欠料、后续需求错误时前序写入回滚、现有 selectLowStock 查询前后结果完全一致，以及普通批次实际扣减/汇总/流水身份。不是生产HTTP闭环或浏览器验收。
   D) 其余替身测试覆盖未知供应商、他单预留、自有预留只读、整卷禁止拆分、混批审批及修改后失效、终态/非生产边界；生产建单/确认/取消/驳回/重新提交不写预占，打样原预占生命周期继续通过。
3）前端定向测试15例通过：pickAllocation 9、ProductionPickPrint 2、ProductionPickAllocationPanel 4。执行 systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 ./node_modules/.bin/vitest run src/views/inventory/outbound/__tests__/ --maxWorkers=1（jjx-web）。
4）完整 vue-tsc --noEmit 通过；npm run build 通过（含状态枚举门禁、vue-tsc、Vite），构建退出0。为避免内存不足，最终验证使用 Node 堆3G、MemoryMax=3584M、MemorySwapMax=512M及180/240秒超时；初次1G/2G类型检查堆不足失败，未将其记为通过。
5）check:docs、check:status-enums、表基线、result-case、collation:strict、migration-collation、notnull 均通过；git diff --check 通过。validate 在 check:stock:strict 停止，未宣称完整 validate 通过；后续 lot/iqc/quality/doc-no 检查未由此流水线执行。

### 用户批准的收尾例外与遗留

1）库存五项门禁当前为2/0/0/0/41，退出1。本轮未执行任何持久库存写入。
   A) 汇总2处是检查脚本只按 inventory_item_id 关联批次、未带 location_id 引起的误报：AUX000934 在库位110/111分别结存300/700、预留143.405/0，与各自批次完全一致；脚本却分别比较全物料1000。
   B) 41处全部为历史入库流水 location_id=NULL、当前库存行 location_id=114（未分配）。库存行、物料、批次、仓库、卷号和来源明细均匹配；不是41处数量错误或流水丢失。归位历史依据尚待独立核实，不修改原流水，不在本卡扩大数据订正范围。
2）用户已明确回复“允许”，批准记录上述既有库存门禁失败及销售测试编译阻塞后，本卡提交、推送并置待审核。此例外仅适用本卡，不改变库存唯一真源、不修改门禁或销售测试、不扩大为数据订正，不代表库存门禁已通过。
3）运行中服务未重启，实际页面操作、浏览器打印、生产库HTTP发料闭环与独立验收未验证；已验证范围如上。审批角色的实际授权由管理员操作。
4）未包含现场既有备份文件、backup-index.tsv/db-backup-log.txt 的改动；用户确认可承接的两组件改动随本任务一并交付。
