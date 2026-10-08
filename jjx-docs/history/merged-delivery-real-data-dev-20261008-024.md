# 发货管理接入真实数据（dev-20261008-024）

日期：2026-10-08。状态：业务接入及视觉/打印续改代码完成；待审核。最新续改按用户要求未测试、未编译；迁移、运行服务更新及验收由用户执行。

## 业务与入口

发货管理上区通过 GET /sales/deliveries/available-lines 查询销售订单真实明细；下区展示真实发货记录。仅生产中订单且订单行剩余待安排量大于零出现；产品身份、订单金额未完善或暂无可用成品时提示原因并阻止选择。收货地址按订单→客户档案带出，缺失时在发货管理本次建单中填写，不回退生产中订单，不改写订单或客户档案。多个已有不同地址的订单不能合并。

订单待安排量 = 订单行数量 − 待发货占用 − 已发货/已签收数量。本次可建单量 = min(订单待安排量，本订单可用于发货的已入库成品量)，不足部分展示“尚缺成品”。已拒收与已作废不占用。只计实预留，不把待生产虚占当成已入库库存。

成品预算先扣其他订单预留，再扣所有待发货凭证：各订单待发占用优先消耗其自有实预留，超出部分消耗共享成品。同订单同产品多行、跨订单同产品共享成品均按合计校验，不逐行重复使用预算。建单排序锁来源订单及成品库存汇总行，READ_COMMITTED下取最新已提交占用校验后插入凭证；只锁行，不新增库存预留、不扣库存。后端创建入口校验相同预算，页面无法绕过；仓库实际出库时仍重新校验。

销售订单列表删除原建单弹窗与413行相关页面逻辑，仅保留“查看发货”，携带orderId精确筛选发货管理上下两区，可清除筛选查看全部。兼容单订单API复用同一创建服务，默认数量也受已入库成品限制，不再维护第二套页面。

2026-10-08只读核对 SO261008003：订单1000，成品实库存700，实预留700，待生产虚占300，待发凭证0；新口径可建700、尚缺300，地址由客户档案带出“苏州工业园区星湖街328号”。未改真实业务数据。建200后：订单待安排800，可再建500、尚缺300；仓库确认200后库存变500、已发200，可再建仍500，不重复扣待发占用。

合并条件：同客户、同收货地址、同币种；本次交货方式统一选择。来源均缺地址时在建单中填写统一地址，有地址的多订单不允许改成不同地址。来源订单没有独立交货方式字段，不把合同 delivery_terms 充当交货方式。不同交期提醒核对，允许合并。同产品跨订单保持独立行。客户料号为空先默认产品名称（用户当次指示），保留已有客户料号。没有真实客户采购单号字段，不填示例数据。

POST /sales/deliveries 使用订单明细ID和本次数量建单，服务端从真实订单取产品、价格和金额，不信任请求中的来源/金额。排序锁住来源订单后检查累计占用，防并发超发。兼容单订单API调用相同业务服务，原页面入口已移除。

## OQC与仓库实际出库

建单只形成待发货与逐行 OQC，每批关联真实来源销售订单及发货明细。OQC入口仍为原出货检验工作台，按发货单号可检索；来源订单检索与展示改为每批对应订单。

OQC最新版本需要判定通过、已判定/已关闭、已检数量大于零、检验批量覆盖本次数量，且复核未挂起。销售“安排出库”生成待仓库确认的出库凭证并幂等返回已存在单号；不立即记已发、不扣库存。仓库确认时再锁发货单、排序锁来源订单、锁检验批，校验逐行来源和数量，再释放各来源订单对应既有预留，调用 InventoryStockMutationService.applyDelta 完成 FIFO 出库。最后更新已发状态、逐来源订单重算已发数量，全部行发满才使订单转已发货。事件带 inventoryPosted 防二次扣库。

沿用现有订单预留，不新增建发货单锁成品库存。签收入口保持原业务；整单拒收按本次明细回库，再重算每个来源订单，不把总数量记到兼容表头订单。

待发货允许整单作废：填写原因、关闭关联OQC、取消未实际出库凭证、保留发货单/明细，通过已作废状态释放待安排数量。已实际出库禁止作废，按原拒收/退货/红冲处理。关闭来源后禁止修改OQC，并隐藏操作按钮。备注与作废原因合计超过500字会提示缩短原因，避免截断原记录。

## 数据模型与迁移评审

复用 sales_delivery、sales_delivery_item 和 sales_order_product，无新表。sales_delivery.order_id 保留主来源兼容值；全部来源以 delivery_item.order_product_id 关联，不把表头当唯一来源。订单查询、销售工作台未签收计数、打印和Excel按真实行来源处理。

新增唯一字段 inventory_outbound_item.delivery_item_id（nullable BIGINT，普通索引），指向 sales_delivery_item.item_id。作用是同产品跨销售订单的不可歧义追溯，产品ID和表头source_id不能替代。历史行留NULL，历史订单型出库仍使用原路径；新发货型出库须有完整逐行关系。新来源标识 SALES_DELIVERY，source_id=发货单ID；库存流水 source_id 仍为出库凭证ID。发货状态6为已作废，使用前后端命名枚举。

迁移：[246_link_sales_outbound_delivery_item.sql](../sql/migrations/246_link_sales_outbound_delivery_item.sql)。编号由当前已应用最大245与目录最大号共同确定。仅加列/索引，不更改历史数量或库存流水。应用之前用户手工备份；agent未生成备份、未应用迁移。回退应用代码后可保留nullable列，不自动删列或任何原始凭证。

## 验证结果与待办

以下是视觉/打印续改之前的验证记录，不代表最新代码已经通过验证。

- 41项后端专项测试、6项前端数量测试通过：DeliveryCapacityServiceTest 8、SalesDeliveryFlowTest 15、MergedDeliveryWorkflowTest 10、InventoryOutboundInvariantTest 4、SalesDeliveryExcelServiceTest 4。覆盖部分发货、占用、防超发、合并条件、逐行OQC来源、最新复检/复核拦截、实际仓库扣库顺序、重复出库、签收、Excel分页/格式。
- 前端状态枚举门禁通过；本次6个Vue组件编译通过；全量vue-tsc通过。
- 库存严格门禁五项全0通过（当前数据库只读检查，不替代迁移后的真实链路验收）。补跑NOT NULL、检验批完整性、IQC来源、质量台账及单号门禁均通过。
- npm run validate 在既有 collation 漂移处失败：基表6列、1表默认未统一。未修改无关库结构或扩基线。
- npm run build 在未改动的 inventory/material/components/MaterialCategory.vue（没有template/script）及 system/eventConfig/index.vue:239（插值表达式语法）失败。已完成本任务组件独立编译；只读解析HEAD原文件同样重现两处问题，不修补其他页面。
- 未运行写真实业务数据的HTTP验收；未启停/重启任何服务；迁移未应用，运行验收仍待执行；代码提交后登记待审核及遗留项。

待用户确认手工备份就绪后，以当前迁移脚本执行：

```bash
bash scripts/db-migrate.sh 246_link_sales_outbound_delivery_item.sql --yes --task dev-20261008-024 --backup <用户手工备份路径>
```

部署时需要用户自行更新运行服务（当前请求未授权生命周期操作）。更新后验证：真实待安排明细→选两个同客户同地址同币种来源→部分建单→逐行OQC→安排出库→仓库确认→逐来源已发及库存→打印/Excel→签收；另验证待发整单作废释放占用和已发整单拒收回库。

代码变更清单集中在销售发货查询/建单/状态服务、出库来源追溯与实际确认、OQC来源保护、销售工作台聚合、合并前端和原打印/Excel，以及相关专项测试与迁移；不包含其他会话的备份、清理、API场景测试方案文件。

专项验证命令：`mvn -o -f /tmp/jjx-pom-024.xml -Dtest=DeliveryCapacityServiceTest,SalesDeliveryFlowTest,MergedDeliveryWorkflowTest,SalesDeliveryExcelServiceTest,InventoryOutboundInvariantTest -DargLine=-javaagent:<本机mockito-core.jar> test`（临时POM仅将编译产物隔离到/tmp，避免其他会话编译冲突；常规在jjx-server/pom.xml执行相同测试即可）；`node jjx-web/scripts/test-delivery-quantity.cjs`。


## 2026-10-08 视觉与打印续改

用户要求恢复此前mock视觉布局，并明确assets/tmp/微信图片_20261008164733_100_13.jpg是最新打印单依据；测试、编译与重跑交给用户。本次继续任务024，不运行npm检查、Maven、浏览器或打印验收，不迁移、不启停服务。

- 恢复收货信息浅底分区、图标页签/明细数、蓝色可建量、右侧数量/订单数/金额分层概览、合并/交期提示与底部操作区。继续用真实API、部分数量、共享成品合计校验及OQC/出库流程；选择入口仍统一在主页面。
- 建单预览、A4和241×140三联纸均复用Qr026DeliverySheet。按照片显示物料料号、品名规格、单位、数量、销售单号、客户订单号码、可选金额/单重；每六行一组，不足补空，逐来源订单，不用合并单主订单号覆盖明细。
- 恢复“显示金额”“显示单重（g）”，默认金额隐藏/单重显示，与此前mock相同；选项在预览、打印和Excel导出之间共享，并保存浏览器偏好。公司名称/TEL/FAX只读系统配置，不写入照片中的示例公司/电话。照片没有二维码或单价/行备注栏，因此本送货单改用照片栏位。
- Excel导出接受同一组显示选项，导出副本按最新照片排为六行、独立销售单号与客户订单号码，不修改仓库中的旧Excel模板资产。完整单价、金额、费用、收货地址仍保留在“发货数据”页。原Excel五行/旧模板版式测试断言已属于旧口径，用户后续验证需按最新六行照片版同步。
- 真实sales_order/sales_order_product没有客户采购订单号字段，product没有单重字段；这两栏保留空白，不虚构值、不以系统销售单号冒充客户订单号。本次只增加前端可选字段，不建表、不加列；将来录入真实字段后再接入。
- 缺少本次发货快照时提示核对，不从订单全量明细替代本次发货打印；实际打印继续走原打印留痕及公共打印工具栏。A4左右15mm、上下12mm以及三联纸241×140尺寸沿用现有口径，实机效果待用户试打。

本次白名单：jjx-web/src/views/sales/delivery/{Qr026DeliverySheet.vue,print.vue,components/MergeDeliveryDialog.vue,components/MergeDeliveryWorkbench.vue,components/useDeliveryPrintOptions.ts}；jjx-web/src/api/sales/delivery.ts；jjx-web/src/composables/useCompanyConfig.ts（读取fax）；jjx-server/src/main/java/com/jjx/sales/{controller/SalesDeliveryController.java,service/SalesDeliveryExcelService.java}；本实施记录。仅这些文件进入本次提交。

用户验收重点：真实明细→合并弹窗层次→部分数量变化→六行预览→金额/单重开关→正式A4/三联纸打印与Excel同栏位→超过六行逐页销售单号。以上为待执行项，不宣称已通过。
