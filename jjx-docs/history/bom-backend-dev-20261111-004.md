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
3）显式下层引用：engineering_bom_item.sub_bom_id + 索引；保存时校验下层存在、产出 MATERIAL 且 output_id=本行 material_id、自指/祖先环/深度 32；提交/批准再次全图 DFS 并要求下层已批准。buy 挂方案、make 无方案分别阻断；既有未改 make 行保留可保存行为。
4）主方案同事务：父 BOM 新增/编辑时对 product 行 SELECT ... FOR UPDATE，按 scheme_revision 乐观锁更新 JSON；采用 token 不落库；父取消不删除独立方案；父提交/批准校验 JSON 引用方案已批准且产出匹配。
5）精度与单位：模数/基数要求 >0、最多 4 位小数，超精度明确报 MODULE_QTY_INVALID/BASE_QTY_INVALID，不落库静默归零；quantity/applied/actual_issue 仍为展示快照，权威是 module/base/loss。新行/变更行跨单位必须有 unit_factor>0 与依据；历史未改行不强制重解释单位。
6）路线/规范：路线按 output 身份失效/查询当前版本，PRODUCT 才同步 product.current_route_id；作业规范发布写入 PRODUCT 身份，并新增按 output 解析最新发布版本与显式版本归属校验（MATERIAL 缺失返回 MATERIAL_WORK_SPEC_NOT_PUBLISHED）。
7）迁移 356_bom_manufacturing_scheme.sql：幂等加列/回填/生成列/唯一索引；风险 high；未执行。编号按当次账本 355 与目录最大号取 356。

## 验证

执行 `MAVEN_OPTS=-Xmx2g mvn -o -q -Dtest=ManufacturingBomSchemeTest,ManufacturingBomCycleTest,BomDecimalRatioTest,ManufacturingRouteBindingTest,WorkSpecMaterialVersionTest test`，16 项全过（3+4+4+3+2）。覆盖保存回读、多方案产品隔离、采用不落库、草稿引用/提交阻断、乐观锁冲突、自指/祖先环/产出不一致/买挂方案/自制无方案、历史未改行可保存、正小数与超精度拒绝、6模1基数×300=50、单位依据、路线 PRODUCT/MATERIAL 身份与当前版本隔离、作业规范按 output 解析与跨类型拒绝。

既有回归：BomGroupPersistenceTest、BomItemBatchReviewTest、BomItemReviewFlowTest、BomGroupedPageTest、BomMaterialBatchLinkTest、BomCommonMaterialAndSplitTest、EngineeringRoutingCopyParentRemapTest、EngineeringRoutingRemarkTest、ProductWorkSpecVersionPublishTest、WorkSpecDraftLifecycleTest、ProductWorkSpecBatchStatusTest、BomListExportTest、BomItemBatchReviewPermissionTest 共 81 项，全过。测试使用隔离 H2 与真实 MyBatis Mapper/Spring 事务，不写业务 MySQL。

为保持既有 H2 分页测试与新增列一致，仅对 BomGroupedPageTest 的一行建表 DDL 补 output_type/output_id 测试夹具；无业务源码改动。

必跑：git diff --check、npm run check:docs（jjx-web）。纯后端未跑前端大构建。

## 未验证项与遗留

1）迁移 356 未执行。依据用户 2026-09-28 备份口径与本次派发约束，结构变更需用户手工全库备份后才能通过 scripts/db-migrate.sh 执行；本卡没有备份证据，不执行 DDL。
2）未启动/重启任何服务，未调用真实 HTTP 接口。代码依赖 356 的新列，迁移前不得部署或启动新构建。
3）未做真实业务库保存/审批/回读；H2 测试不能冒充真实 DB 闭环。
4）未订正 inventory_item MATERIAL 身份错位（7/7 编码名称不一致），F 阶段仍被阻断。
5）MATERIAL 作业规范的完整发布编辑链未在 B 实现（B 提供身份字段、按 output 解析与归属校验）；G/F 接入时需补齐发布入口，不能用产品规范顶替。
6）push 既有旧表门禁红（inventory_material_spec_attr_def/spec_value 未登记批准），不扩基线、不默认 --no-verify；推送结果以任务记录为准。

## 接续

下一步由 Codex 独立验收本卡。迁移执行、真实库回读、前端 C 接入、D 需求引擎与 F 生产库存均需各自授权；不因本卡代码准备完成而解锁。
