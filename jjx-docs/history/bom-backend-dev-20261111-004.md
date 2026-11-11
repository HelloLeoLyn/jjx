# 制造BOM后端能力（B阶段）实施记录

任务：dev-20261111-004（总任务 dev-20261111-002 子任务 B）。执行者：OpenClaw；验收者：Codex（独立验收）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：后端源码、定向测试与迁移文件已准备；迁移未执行、服务未启动、库存身份未订正。运行态未验证。

## 交付边界

本卡只交付 B 制造BOM后端能力，不写前端、不实现 D 需求引擎、不实现 F 生产库存闭包。写入白名单严格按 003 文档 B 节现有/新增文件，另含 Product 实体/Mapper/ServiceImpl（仅主方案存取）、本文件、INDEX、迁移 356 与本任务 .tmp 证据。

主方案按 Codex 派发约束采用产品级 JSON 映射：product.material_scheme_json + scheme_revision，不建 product_material_scheme 表，不修改 model-baseline 或清理规则。

## 实际改动

1）产出身份：engineering_bom、engineering_routing、product_work_spec_version 增加 output_type/output_id；PRODUCT 兼容历史 product_id，MATERIAL 使用 material_id 且 product_id 允许为空。新增 ManufacturingOutputType 枚举，不复用 BomType/bom_type。
2）制造方案：新增 /engineering/bom/manufacturing 保存/编辑、/manufacturing/options 候选方案、/{bomId}/adopt 采用 token、/{bomId}/scheme-choices 已保存选择。保存复用 IEngineeringBomService 同一事务和行锁，不自动发布、不改父 BOM、不生成库存/工单。
3）显式下层引用：engineering_bom_item.sub_bom_id + 索引；保存时校验下层存在、产出 MATERIAL 且 output_id=本行 material_id、自指/祖先环（迭代三色环校验，不设固定业务深度）；提交/批准再次全图校验并要求下层已批准。buy 挂方案、make 无方案分别阻断；既有未改 make 行保留可保存行为。
4）主方案同事务：父 BOM 新增/编辑时对 product 行 SELECT ... FOR UPDATE，按 scheme_revision 乐观锁更新 JSON；采用 token 不落库；父取消不删除独立方案；父提交/批准校验 JSON 引用方案已批准且产出匹配。
5）精度与单位：模数/基数要求 >0、最多 4 位小数，超精度明确报 MODULE_QTY_INVALID/BASE_QTY_INVALID，不落库静默归零；quantity/applied/actual_issue 仍为展示快照，权威是 module/base/loss。新行/变更行跨单位必须有 unit_factor>0 与依据；历史未改行不强制重解释单位。
6）路线/规范：路线按 output 身份失效/查询当前版本，PRODUCT 才同步 product.current_route_id；作业规范发布写入 PRODUCT 身份，并新增按 output 解析最新发布版本与显式版本归属校验（MATERIAL 缺失返回 MATERIAL_WORK_SPEC_NOT_PUBLISHED）。
7）迁移 356_bom_manufacturing_scheme.sql：幂等加列/回填/生成列/唯一索引；风险 high；未执行。编号按当次账本 355 与目录最大号取 356。

## 验证

执行 `MAVEN_OPTS=-Xmx2g mvn -o -q -Dtest=ManufacturingBomSchemeTest,ManufacturingBomCycleTest,BomDecimalRatioTest,ManufacturingRouteBindingTest,WorkSpecMaterialVersionTest test`，16 项全过（3+4+4+3+2）。覆盖保存回读、多方案产品隔离、采用不落库、草稿引用/提交阻断、乐观锁冲突、自指/祖先环/产出不一致/买挂方案/自制无方案、历史未改行可保存、正小数与超精度拒绝、6模1基数×300=50、单位依据、路线 PRODUCT/MATERIAL 身份与当前版本隔离、作业规范按 output 解析与跨类型拒绝。

既有回归：BomGroupPersistenceTest、BomItemBatchReviewTest、BomItemReviewFlowTest、BomGroupedPageTest、BomMaterialBatchLinkTest、BomCommonMaterialAndSplitTest、EngineeringRoutingCopyParentRemapTest、EngineeringRoutingRemarkTest、ProductWorkSpecVersionPublishTest、WorkSpecDraftLifecycleTest、ProductWorkSpecBatchStatusTest、BomListExportTest、BomItemBatchReviewPermissionTest 共 81 项，全过。测试使用隔离 H2 与真实 MyBatis Mapper/Spring 事务，不写业务 MySQL。

为保持既有 H2 分页测试与新增列一致，仅对 BomGroupedPageTest 的一行建表 DDL 补 output_type/output_id 测试夹具；无业务源码改动。

必跑：git diff --check、npm run check:docs（jjx-web）。纯后端未跑前端大构建。

## 退修复验（2026-11-11 14:1x，同卡 dev-20261111-004）

Codex 独立复跑 16 项通过后审查退修，本次在原卡、原白名单内修正，不新建卡、不扩展 D/F。

1）主方案按 product+material 隔离：`applySchemeChoices` 不再批量清空 JSON 全部 `isPrimary`，只写当前 materialId 节点；同产品两种材料可各自保留主方案，改一个不影响另一个，不同产品互不影响。
2）父 BOM 实际采用与产品主方案分离：`isPrimary=false/null` 仅表示父 BOM 采用，实际采用只落 `engineering_bom_item.sub_bom_id`，不覆盖产品主方案；`isPrimary=true` 只替换本物料主方案；新增可选 `cancelPrimary=true` 只移除本物料主方案（与设主不能同时提交）。只有实际写 JSON 才要求 `schemeRevision`，缺失返回 400 `SCHEME_REVISION_REQUIRED`，旧版本返回 409 `CONCURRENT_MODIFICATION`；读写 revision 一致。旧前端不传 `schemeChoices` 的行为不变；候选仍由 `/manufacturing/options` 按 output 全量查询，不塞入 JSON。
3）提交/批准只校验本 BOM 实际引用（行 `sub_bom_id`）及本 BOM 投入物料对应的主方案；未被本 BOM 使用的候选草稿不再阻断其他 BOM。坏 JSON 不再 catch 后返回空：读取/校验明确抛出 `SCHEME_JSON_INVALID` 并保留原数据。
4）移除 `MAX_SUB_BOM_DEPTH=32` 固定业务上限：改为迭代显式栈 + 三色标记环校验，节点查询按 bomId 缓存；共享 DAG 不误判，深链不栈溢出。
5）`POST /engineering/bom/manufacturing/{bomId}/adopt` 由 `engineering:bom:view` 改为 `engineering:bom:add` / `engineering:bom:edit` OR；只读仍可看 options/detail/scheme-choices 但不能 adopt。service 读取方案状态复用 `ProductEnums.BomStatus.APPROVED`，无新增数字状态字面量。

稳定接口变更清单：
- 权限：adopt 从 view 改为 add/edit OR（options、detail、scheme-choices 保持 view）。
- DTO：`ManufacturingSchemeChoiceDTO.cancelPrimary` 新增可选布尔；其余字段不变。
- 保存语义：`isPrimary=false/null` 不再写产品 JSON；写 JSON 必带 `schemeRevision`；坏 JSON 返回 `SCHEME_JSON_INVALID`。
- 环校验：不再有 32 层上限，返回 `BOM_CYCLE` 的深链闭环照旧阻断。

验证证据：
- 失败前：`MAVEN_OPTS=-Xmx2g mvn -o -q -Dtest=ManufacturingBomSchemeTest,ManufacturingBomCycleTest -DargLine=-Xmx512m -DforkCount=1 test` → 16 项，5 failures + 3 errors（跨物料主方案被清、非主采用覆盖主方案、缺 revision 通过、坏 JSON 静默通过、view 可 adopt、40 层链触发 BOM_DEPTH_EXCEEDED）。
- 通过后：同命令 18 项（补旧调用/采用不落库 2 项后），0 failures / 0 errors；定向回归 17 个既有测试类 108 项，0 failures / 0 errors / 1 skipped。
- 新增反例覆盖：同产品两材料各主方案与改一个不影响另一个、两产品隔离；非主采用不改主、主方案变更/取消；未用草稿不阻断其他 BOM submit、坏 JSON 阻断且原文保留、缺/旧 revision 拒绝；40 层合法链通过、40 层闭环拒绝、共享子图不误判；view-only adopt 403 且 service 不被调用、edit 可 adopt。

## 未验证项与遗留

1）迁移 356 已由外部在目标库账本记账并归档；本次修复未重复执行、未改动 356 文件及备份 WIP。真实业务库保存/审批/回读仍未由本卡重新验证。
2）未启动/重启任何服务，未调用真实 HTTP 接口。代码依赖 356 的新列，迁移前不得部署或启动新构建。
3）未做真实业务库保存/审批/回读；H2 测试不能冒充真实 DB 闭环。
4）未订正 inventory_item MATERIAL 身份错位（7/7 编码名称不一致），F 阶段仍被阻断。
5）MATERIAL 作业规范的完整发布编辑链未在 B 实现（B 提供身份字段、按 output 解析与归属校验）；G/F 接入时需补齐发布入口，不能用产品规范顶替。
6）push 既有旧表门禁红（inventory_material_spec_attr_def/spec_value 未登记批准），不扩基线、不默认 --no-verify；推送结果以任务记录为准。

## 接续

下一步由 Codex 独立验收本卡。迁移执行、真实库回读、前端 C 接入、D 需求引擎与 F 生产库存均需各自授权；不因本卡代码准备完成而解锁。

## 前后端契约补齐（2026-11-11 14:5x，同卡 dev-20261111-004 第三轮）

Codex 独立复跑 06301838/7758f2e1 的 37 项测试全过后，本轮只补两个联调契约缺口，不重新设计、不改 D/F、不动前端与迁移。

1）产品详情暴露主方案版本：`ProductVo` 新增 `Integer schemeRevision`。`Product.schemeRevision` 已持久化，`ProductConverter` 为同名自动映射，`GET /product/{productId}` 经既有 `getProductDetail → productConverter.toVO` 直接回读，无新接口/表/列。未知版本保持 null，不默认 0；服务端写 JSON 必带 revision 的规则不变。首次新建产品 BOM 尚未有 bomId 时，前端可直接取该字段作为设主提交的 `schemeRevision`。

2）多层产品主方案上下文：`usedMaterialIdsForScheme` 从“只收父 BOM 直接行”改为“本次父 BOM 显式关联图可达投入集合”。根层使用本次 DTO 行（跳过待移除行，新增未保存行可参与），下层沿 `sub_bom_id` 读取已保存行逐层展开；按 bomId 缓存访问防重复/防环，只沿显式引用，不猜分组或物料来源。仅可达材料可设/取消主方案，无关材料仍拒绝；非主采用不改产品主方案；不改 quantity 或 source_type。根行删除或切换下层方案后，旧路径不再展开。

验证：
- `MAVEN_OPTS=-Xmx768m timeout 180 mvn -o -q -Dtest=ManufacturingBomSchemeTest -DargLine=-Xmx512m -DforkCount=1 test` → 23 项，0 failures / 0 errors / 0 skipped。
- `MAVEN_OPTS=-Xmx768m timeout 180 mvn -o -q -Dtest=ManufacturingBomCycleTest -DargLine=-Xmx512m -DforkCount=1 test` → 9 项，0 failures / 0 errors。
- 新增反例/正例：产品详情 VO 与 MVC 回读 revision=7、未知版本 null；产品→349→350 二层可达设主成功；同产品非主采用不改深层主且 revision 不增；无关材料设主拒绝并回滚；根行切换/待移除后旧 350 路径拒绝且事务回滚。
- 本轮未启动/重启服务，未调用真实 HTTP 业务库，未执行 DDL，未改前端/INDEX/备份/356 归档。

稳定接口变更清单（本轮）：
- `ProductVo.schemeRevision` 新增只读字段；`GET /product/{id}` 返回该值。
- 产品主方案设主上限从“直接投入”扩展为“显式制造图可达投入”；无 DTO/表结构变化。
- 005 新建产品 BOM 可直接使用 `ProductVo.schemeRevision` 取真实版本。
