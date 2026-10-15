# 打样工作台四阶段优化：中途收口与交接

任务码：dev-20261015-019。操作员：Codex。日期：2026-10-15。

用户要求：分阶段登记任务实施；随后要求额度不足以完成时尽快收口、留下任务交接文档。本次停止扩展实现，不把第三阶段的半成品当成完成。

## 接手入口与状态

先读 `/home/administrator/jjx/AGENTS.md`、`/home/administrator/jjx/jjx-docs/standards/CONVENTIONS.md`、[任务书指南](../guides/agent-task-brief.md)。任务书仅引用指南，不复制模板。接手017前回查sys_task并记录自己的操作者和开始时间。所有阶段任务的description已写范围、依赖和验收。

| 阶段任务 | 状态 | 提交与实际结果 |
|---|---|---|
| dev-20261015-015 前端职责拆分 | 2 待审核 | `9c244bd9` 已push；拆出计划持久化、复制、材料搜索、执行逻辑，卡片拆内容/材料组件，计划控制器收口；保存入口明确返回成功/失败 |
| dev-20261015-016 卡片精简 | 2 待审核 | `27591117` 已push；紧凑卡片、材料摘要展开、编辑与更多、取消修改恢复、完成编辑保留草稿、顶部统一保存，删除计入未保存提示 |
| dev-20261015-017 BOM编辑和转移适配 | 1 进行中，待接手 | **未完成、未提交**；代码和迁移草稿保留在当前共享工作区，不可发布 |
| dev-20261015-018 执行保存前置 | 0 待开始 | 未修改执行规则；现有未保存开始问题尚未解决 |
| dev-20261015-019 本交接 | 文档交付后置2 | 只提交本文件与INDEX，不夹带017的半成品 |

前两阶段浏览器交互均未验证；待审核不等于用户验收通过。当前工作区含017改动，不是27591117的原样运行代码。

## 已确认需求（后续不得遗漏）

1）组件化：按业务划分组件，内部按职责拆逻辑；草稿和保存归计划所有；不引入试产、审批、执行版本等额外流程。

2）卡片：保留序号/工序组合图标下标/有内容的备注；材料摘要默认紧凑、明细可展开；执行状态和耗时集中时间线。编辑与更多在右上，删除在更多；取消修改恢复进入编辑前数据，完成编辑只结束输入，统一保存工序计划。材料新增模数，手填用量改为基数，语义对齐标准BOM，不能只改列标题。

3）BOM：材料｜项目｜规格｜模数｜基数｜单位。BOM手工新增不必选来源工序；项目非必填，可选多个标准工序。工序带入默认完整来源单工序/组合项目。来源关联可内部记录，不要求增加可见列。卡片和BOM编辑同一份材料草稿；BOM独立新增材料也必须真实保存回读，不可丢弃或强行暂挂首工序。关联档案后规格/单位只读。模数/基数均至少1的整数，单位用量=基数÷模数，四位小数。

组合项目保留多个并提醒“资料转移时需选择一个标准BOM项目”。转移时逐材料从组合选择一个，不能默认第一道、不能按项目复制多条材料；空项目补选或明确整单通用。转移选择不覆盖打样原组合。标准BOM仍为单个processId，不扩展标准BOM为多选。

4）执行：当前工序未保存/有修改/保存中/失败时禁止开始和完成；组合所有成员须真实落库。其他工序或独立BOM草稿不阻断当前可执行工序，执行刷新不得覆盖这些草稿。按钮/函数统一校验、防重复提交、失败与部分成功不能提示全部成功；历史轮次/只读保持禁操作。

## 验证记录（区分版本）

| 代码状态 | 命令/结果 |
|---|---|
| 015提交前 | `cd jjx-web && npx vue-tsc --noEmit` 通过；`npm run check:status-enums`、`npm run check:docs`通过；临时定向检查4项通过：只读、重复保存、保存失败、材料序列化 |
| 016提交前 | vue-tsc/状态枚举/check:docs通过；`node --test src/views/engineering/sample-workbench/composables/planDraft.test.cjs` 4项通过：取消恢复且不覆盖执行状态、完成编辑保留材料、删除与执行字段区别、损坏JSON兼容 |
| 017当前后端 | `cd jjx-server && mvn -q -DskipTests compile` 退出0；**仅编译，不代表业务验证，相关单元/接口测试未写完未跑** |
| 收口时当前前端 | `cd jjx-web && npx vue-tsc --noEmit` 退出2；AttachmentRefList.vue:40/45/63/64 四条TS2367（text/displayName/time判别不匹配）；当前另有他人改动attachmentFields.ts，未触碰或还原；不能声称全仓类型绿 |
| 数据库/运行态 | 未执行迁移285；未改业务数据；未启停、重启或替换任何服务；浏览器/E2E未验证 |

## 017当前半成品做了什么

后端：为现有sales_sample_bom准备materialId/materialCode/projectIds/moduleQty/baseQty字段；计划DTO允许附带独立材料，计划保存事务内保存独立BOM；预览补项目候选、数量参数和独立材料；自动转移草稿增加用户单项目确认、整单通用以及标准BOM字段写入。

新增SampleMaterialQuantity纯计算类，尝试将历史正小数用量约分为整数基数/模数（如0.25→1/4），零/负数或无效历史数据要求人工核对。此兼容算法只是实现草稿，尚未完成边界测试；不得宣称历史数据已经迁移。

前端：新增sampleMaterials和useSampleBom，尝试投影卡片材料+独立材料；保存请求增加独立材料；卡片材料编辑加模数/基数；workbench向BomPanel接入新增/修改/删除事件。

**BomPanel本体尚未改，资料转移store/API/界面尚未改。当前界面与后端合同没有闭环，不能交付使用。**

## 017未提交文件白名单

下列文件是本会话017的改动，接手应先读diff，不要以覆盖整个工作区方式恢复。

```text
/home/administrator/jjx/jjx-docs/sql/migrations/285_sample_bom_projects_quantities.sql
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/domain/entity/SalesSampleBom.java
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/domain/vo/SampleTransferPreviewVO.java
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/dto/save/SampleProcessPlanDTO.java
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/dto/transfer/SampleTransferConfirmDTO.java
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java
/home/administrator/jjx/jjx-server/src/main/java/com/jjx/sales/service/impl/SampleMaterialQuantity.java
/home/administrator/jjx/jjx-web/src/types/sales/sampleOrder.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/components/ProcessMaterialEditor.vue
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/composables/usePlanPersistence.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/composables/useProcessMaterials.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/composables/useSampleWorkbench.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/composables/sampleMaterials.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/composables/useSampleBom.ts
/home/administrator/jjx/jjx-web/src/views/engineering/sample-workbench/workbench.vue
```

新增文件为迁移285、SampleMaterialQuantity.java、sampleMaterials.ts、useSampleBom.ts；其余为已跟踪文件修改。后续尚需修改的BomPanel.vue、SampleTransferDialog.vue、store/modules/sampleTransfer.ts、api/sales/sampleTransfer.ts和测试范围已在017描述中说明或补充。

## 接手必须先解决的具体缺口

1）完成BomPanel编辑UI与事件，不再只读表；材料搜索结果需要响应式状态。当前useSampleBom的computed返回普通临时行，直接给行写options/loading未必触发重绘；不要照搬未经验证的写法。更新项目时必须区分继承默认与用户选择；当前updateMaterial调用normalizeMaterial(value)没有传来源默认项目，继承标志为true时可能清空项目，必须修正。

2）统一材料草稿序列化：完成编辑、卡片保存、BOM修改、批量/常用物料、印刷材料、复制都须保留projectIds/moduleQty/baseQty。材料索引目前通过过滤后的materialValues生成，空白编辑行存在时要验证不会改错行。所有编辑路径需计算qty，不能只依赖保存后计算。

3）清空/复制/轮次：clearCurrentRound尚未清独立材料；历史轮次/跨单复制/成品方案带入没有完成独立BOM传承；轮次快照和readonly展示未接入新增字段。不可把这些遗漏交付为“BOM编辑完成”。

4）保存指纹：loadPlan与loadBom并行回读会影响savedFingerprint基准；首次进入已有独立材料时可能误判未保存。计划刷新和独立材料加载应协调成功后建立一次保存快照，失败不能清空数据伪装成功。草稿加载/回写需要明确失败结果。

5）资料转移：前端目前仍是旧自动模式仅传orderId，新后端需要materialProjects逐条确认；必须补齐候选单选、空项目补选/整单通用、组合提醒、禁提交和错误提示。自动映射仍保留旧材料展开后再clear/rebuild的重复逻辑，需审查去重、匹配和错误处理；不要让组合多次计料。检验前端旧预览与服务端最新材料不一致时能要求刷新。

6）测试：补齐数量约分/四位精度/非法值、独立材料无工序保存回读、组合项目保留/转移单选、重复物料不重复计量、保存回滚、轮次与复制、卡片BOM双向同步。当前只后端编译通过，不能替代以上验收。

7）迁移：当前285仅是ALTER TABLE草稿，未应用。实体已引用新列，**迁移前不可把017后端发布运行**。执行前重新读目标库账本和目录最大号，防止共享仓其他任务取号冲突；当前检查账本尾号为284、表无新列。结构变更按用户规则由用户手工备份（排除hr_employee），agent不自动备份。现有备份文件来自其他工作，不可擅自认定满足本次备份前置。遵循db-migrate.sh记账/归档流程，不自行移除迁移文件。

8）017自检和提交后再做018。018须先检查新建卡片/复制卡片是否把标准工序ID错当实际processId；当前makeCard/复制路径仍存在需要核实的ID来源。前端仅检查非空ID不足以证明已落库。当前advancePlan会全量loadPlan覆盖草稿，这正是018必须改的内容。

## 其他会话WIP（禁止纳入或还原）

开始任务时已有：jjx-docs/sql/backups/backup-index.tsv、clean-test-data-log.txt、db-backup-log.txt、jjx_erp_db_backup_20261015-0942.sql；迁移282/283/284从migrations到applied的删除/新增组合。收口时另见jjx-web/src/components/attachment/attachmentFields.ts修改。以上均不是本任务产物。INDEX在开始写本交接前无修改。

## 最短接手顺序

先读本文件和017任务描述，核对git status与上述白名单；接手017并记录操作者；完成BOM共享草稿/保存回读，再完成转移选择与校验，然后做复制/轮次、针对测试与必要检查；迁移和发布前处理手工备份及结构检查。不要启动或重启服务。017满足验收后独立提交push及回写，再接018。

若只需回看已提交版本，用git show读取9c244bd9/27591117；不要使用git reset --hard、git clean，也不要还原他人修改。017半成品仅在本共享工作区，尚未进入远端；接手必须使用本工作区或由原作者明确移交这些diff。
