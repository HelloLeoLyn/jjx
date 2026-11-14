# dev-20261114-011 完成报告：BOM产品侧批量整套审核与材料BOM独立维护

- 任务码：dev-20261114-011
- 执行者：OpenClaw（大黄）
- 会话：agent:main:jjx-bom-review-dev-20261114-011
- 开工基线：dev @ ec72d4ee（开工时工作区仅有 5 项既有 backups 改动）
- 分支：dev

## 目标与结果

BOM管理主列表拆为「产品BOM / 材料BOM」两个页签，默认产品BOM。产品侧一行一个产品，展示当前生效版本、最新维护版本及状态、实际引用下层方案摘要、整套问题数、最近修改时间、版本数，并提供查看整套、版本维护、送审、审批。产品页可勾选多个产品做批量送审/批量审核：先展示范围确认，再执行，最后按产品返回成功/失败清单，可只重试失败产品。材料页一行一个产出物料，保留独立版本维护、送审、审批、驳回；材料驳回前展示受影响的产品/版本。

整套范围只沿工程BOM行实际 sub_bom_id 递归展开，共享节点去重、防环，不按主方案 JSON、候选清单或固定层数扩大/截断。已批准且未变化的方案只读复用，不重写审核人/时间；未批准且本次实际采用的方案允许同产品整套送审，批准时从下层到上层完成校验。每个产品一个独立根事务，全成功或全回滚，产品之间互不连带回滚。

## 后端改动

1. 接口（BomController）：
   - POST /engineering/bom/review/package/plan（action + rootBomIds，只读范围预览）
   - POST /engineering/bom/review/package/submit（engineering:bom:edit）
   - POST /engineering/bom/review/package/approve（engineering:bom:approve）
   - POST /engineering/bom/review/package/reject（engineering:bom:reject）
   - GET /engineering/bom/{bomId}/references（材料/公共方案引用影响）
2. 产品页聚合行新增 latestStatus/currentStatus/subBomSummary/subBomCount/issueCount/itemCount；product-page 支持 outputType 过滤（PRODUCT/MATERIAL）。问题数按整套送审预检汇总，已批准版本按 0。
3. 整套范围与执行（EngineeringBomServiceImpl）：
   - collectPackage 迭代 DFS + 三色标记，后序节点表，共享 DAG 每节点只展开一次。
   - PackageContext 仅放行本次实际引用集合中的未批准节点；范围外方案仍按原严格口径阻断。
   - 执行前用全局图锁（与保存同一锁协议）+ 明细行锁；重新发现范围并逐节点比对 bomId 集合、版本、包内容指纹（不含审核状态的 SHA-256）。集合/版本/内容变化一律整产品失败。
   - 送审：节点草稿/驳回→审核中、行草稿/驳回→待审核；仅根产品触发 bom.submitted，避免下层方案分散待办；已有待审共享方案只复用不重复提交。
   - 批准：从下层到上层，待审核行在本次一次通过（含物料有效性校验）；待移除行仍必须已人工确认；已批准节点与已批准行跳过，不重写审核时间；通过后用既有严格 checkBom 复检，再改状态、写 review_flow、发 bom.approved。
   - 驳回：只退回根产品BOM；不连带公共下层方案。
   - 共享方案只处理一次；结果中的 sharedNodes 列出共享方案及涉及产品；同一请求重试使用旧预览时不重复写日志、不把外部变化当成功。
   - 审核通过不自动切当前版本、不自动设主方案、不改其他产品采用版本。
4. 未新增表、未改 DDL、未迁移、未改库存写入；只使用既有 engineering_bom / engineering_bom_item / review_flow / 既有事件。

## 前端改动

1. index.vue：产品/材料页签；产品多选列与批量送审/审核；产品行显示当前生效版本（状态+生效日）、最新维护版本（状态）、实际引用方案、问题数；材料行独立送审/审批/驳回/版本维护；材料驳回弹窗先列受影响产品/方案版本并要求驳回原因。
2. 新增 BomReviewScope.vue：加载范围预览，展示每个产品的根版本、本次动作数量、复用数量、阻断问题、节点明细（可展开行明细），确认后只提交可执行产品；纯函数 buildReviewPackageScopes 由测试覆盖。
3. 新增 BomBatchReviewDialog.vue：执行整套动作，按产品展示成功/失败、共享方案处理，失败产品可只重试失败产品；纯函数 extractFailedRootIds 由测试覆盖。
4. 未改共享 VersionMaintenanceDialog / 物料选择器 / BOM 编辑器；未改产品档案状态流转。

## 行为验收对照

A) YL1025PWOO 只读核对（2026-11-14 17:50 实查）：产品 YL1025PWOO / YL-208NDW；BOM V1.0（bom_id=3，已批准，is_current=1）、V1.1（bom_id=4，草稿，is_current=0）；两者各 5 行，均引用 AUX000347-MFG V1.0、AUX000348-MFG V1.0（两个子方案均已批准）。产品页应按 PRODUCT 显示 1 行，材料页另按 MATERIAL 各 1 行；两个已批准子方案在整套中标记复用、不要求重审。页面运行态未查看（未获服务生命周期授权）。
B) 隔离夹具：新增 BomReviewPackageTest 覆盖两层以上未批准链一次预览/一次通过范围；新增 BomReviewPackageTransactionTest 用 H2 真实事务验证单产品全成或全回滚、共享节点只批准一次。未在生产 MySQL 造业务夹具。
C) 两产品共享子方案：BomReviewPackageTransactionTest.sharedNodeIsApprovedOnceAndSecondProductReusesIt 断言共享方案 2 只写一次 APPROVE 日志、两个产品根各自批准；重复提交（retryWithStalePreviewDoesNotDuplicateLogsOrOverwrite）不追加日志。真实多线程并发未跑（H2 替身不适合模拟 MySQL 行锁），以全局图锁+状态 CAS 作为并发防线。
D) 驳回只退根产品：plan REJECT 只含根节点；executeRejectPackage 只改根状态，不遍历下层。材料驳回影响范围由 /references 返回。已批准复用节点/行不改审核人时间（代码跳过）。
E) 预览后修改/引用链变化/版本变化/内容指纹变化/权限缺失/未关联物料/非法参数/环/未确认移除/停用材料均有明确阻断与定位；共享待审方案复用不重复生成/删除无关待办。
F) 浏览器证明未执行：没有可用运行服务且未获启动/重启授权；本次交付代码与定向测试，运行态/真实事务缺口见「未验证」。
G) 最小验证命令与结果见下。

## 验证命令与结果

1. 前端定向 Vitest（systemd-run MemoryMax=1G，timeout 120）：
   ProductVersionList / bomReviewPackage / bomBatchReview / bomSubmitCheck / BomSubmitCheckDialog / bomReviewGroups / bomApproveBatchButton
   → 7 个文件、50 个用例全通过，退出码 0。
2. vue-tsc --noEmit（MemoryMax=3G，NODE_OPTIONS=--max-old-space-size=2304，timeout 180）→ 退出码 0。
3. npm run check:status-enums → 通过（存量 105 处，新增 0 处）。
4. npm run check:docs → 通过（history 285 / modules 4 / task-docs 66）。
5. 后端定向 mvn -o -Dtest=BomReviewPackageTest,BomReviewPackageTransactionTest,BomReviewPackagePermissionTest,BomItemBatchReviewTest test（MemoryMax=2G/Swap0，timeout 240，flock 互斥）→ 17 个用例全通过，退出码 0。
6. 基线失败记录：BomItemReviewFlowTest.saveKeepsItemIdAndNeverDeletesWholeBom 在本卡开工基线 ec72d4ee 的独立 worktree 中同样失败（expected 3 but was 2），确认非本卡引入；本卡未修改该链路，保留失败记录。
7. git diff --check 待提交前复查。

## 提交与回写风险披露

开工后 2026-11-14 17:47，Owner 账号（Administrator）在仓库内执行了一次备份/提交，产生并推送了提交 a2977deb（message 为 “nc”，未带任务码），把本卡当时工作区的全部在制代码（前后端与 BomReviewPackageTest）连同 backups 日志一起提交。该提交不是 OpenClaw 发起，且已推送；按仓库规则不得 reset/force-push 或改写他人提交。OpenClaw 的收尾提交只包含剩余本卡产物（BomReviewPackageTransactionTest、BomReviewPackagePermissionTest、本完成报告与 INDEX 登记），提交信息带本卡任务码。本卡代码实际分布在 a2977deb 与收尾提交两处，特此披露。

## 未验证 / 遗留

1. 浏览器实际操作未验证：未启动/重启前后端，未做 E2E。
2. 真实 MySQL 事务与并发未验证：事务测试基于 H2 + DataSourceTransactionManager，mapper 为替身；不宣称生产库已完成真实事务验证。
3. 材料页面的当前版本语义：材料没有产品主方案解析入口，仅展示已指定且符合资格规则的 is_current 版本；材料页主要展示最新维护版本与状态。
4. 事件中心配置中 bom.submitted / bom.approved 的 target_role 仍为 [16]（空置角色），本次未改事件配置；整套送审只发根产品事件，通知是否可达取决于既有事件配置，不在本卡范围。
5. 页面运行后需重点复核：分页/筛选/页签切换、批量范围确认、失败重试、材料驳回影响列表、权限按钮可见性。
