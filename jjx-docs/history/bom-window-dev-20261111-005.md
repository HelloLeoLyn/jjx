# 制造BOM独立窗口（C阶段）：前端窗口、方案选择、采用回填与路径返回

任务：dev-20261111-005（总任务 dev-20261111-002 子任务 C）。执行者：Hermes；验收者：Codex（独立验收）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：前端代码与定向组件/API 测试已完成；**运行态联调与 C 验收未进行**，等待 B（dev-20261111-004）主方案/防环等退修复验通过后由 Codex 解锁。

## 授权与交付边界

用户本次授权：“继续推进 C（制造BOM独立窗口），只基于既有稳定 DTO/API 完成前端代码与定向组件测试准备”。

1）只写前端与本文档：派发白名单内的 10 个既有前端文件（实际改动 7 个）、bom/components 下新增 3 个组件、新增 4 个测试文件、`history/INDEX.md` 登记；新增路径均先在 `sys_task.remark/description` 登记后才创建。
2）未改后端、迁移、备份、baseline；未暂存或覆盖他人在制（开工基线里 OpenClaw 的 356 归档、备份日志与 004 退修源文件全程未动）。
3）未启动/重启任何服务，未改运行态；未执行迁移 356。
4）B 尚未整卡验收：本文所有“通过”均指前端编译/定向测试，不等于真实接口联通，更不等于 C 验收。

## 现场证据（只读读取，行号以当次源码为准）

1）`jjx-server/.../engineering/controller/BomController.java:47-82`：`GET /engineering/bom/manufacturing/options`、`POST /engineering/bom/manufacturing`、`PUT /engineering/bom/manufacturing/{bomId}`、`POST /engineering/bom/manufacturing/{bomId}/adopt`；`:176-182` `GET /engineering/bom/{bomId}/scheme-choices`；权限 `engineering:bom:view/add/edit`。
2）`.../product/service/ManufacturingBomService.java`：options/save/update/adopt/getSchemeChoices 五个能力，无“删除方案”“自动发布”入口。
3）DTO/VO：`ManufacturingBomSaveDTO`（bomCode/bomName/bomVersion/outputType/outputId/effectiveDate/expiryDate/remark/items）、`ManufacturingBomVO`、`ManufacturingSchemeVO`（含 approveStatus/isCurrent/isPrimary/itemCount/selectionToken）、`ManufacturingSchemeChoiceDTO`（materialId/bomId/isPrimary）。
4）父 BOM 契约：`EngineeringBomDTO` 已含 `schemeChoices` 与 `schemeRevision`；`EngineeringBomVO`（getBomDetail）返回 `schemeRevision` 与 `schemeChoices`，见 `EngineeringBomServiceImpl.getBomDetail`。
5）主方案写入语义（`EngineeringBomServiceImpl.applySchemeChoices`）：按 `materialId` 节点写 `bomId`；`isPrimary=true` 时清除其他节点标记；**显式 `isPrimary=false` 会把该物料原主降级**；`schemeRevision` 非空时做乐观锁校验，冲突返回 409 `CONCURRENT_MODIFICATION`。
6）前端入口事实：`BomFormDialog.vue` 的调用方只有 `views/product/bom/index.vue:216` 与 `views/engineering/product-spec/components/ProductWorkSpecPanel.vue:481-486`；`BomItemEditor.vue` 的生产调用方只有 `BomFormDialog.vue:130`。

## 入口 × 新增/编辑 × 父保存矩阵（本次逐项核对）

| 入口 | 新增 | 编辑 | 保存对象 | 本次处理 |
|---|---|---|---|---|
| BOM管理页 → BomFormDialog（整单） | 有 | 有 | 父 BOM 主表+明细+schemeChoices（显式调整时） | 材料行操作列新增“制造BOM”入口；采用只回填草稿；父保存才持久化 |
| 作业规范页 → BomFormDialog（整单） | 无（仅按 bomId 打开编辑） | 有 | 同上 | 与上一入口共用同一组件，行为一致；未新增第二条保存链 |
| BomItemEditor（共享材料行） | 有 | 有 | 不保存 | 只新增入口按钮与 `updateItem` 回填方法；分组/行审核/复制删除/规格只读/快捷建档/导入均未改语义 |
| 制造BOM独立窗口 | 新建方案 | 编辑方案 | 制造方案自身（engineering_bom + 明细） | 独立保存；不发布、不改父 BOM、不设 isCurrent |
| 制造BOM窗口 → 采用此方案 | — | — | 仅前端父草稿 | 调 adopt 取 token 结果后回填该行 `subBomId/sourceType` |
| 制造BOM窗口 → 设为主方案 | — | — | 仅前端父草稿 | 仅记录显式调整，随父保存提交 |
| 产品BOM整单保存 | 有 | 有 | 父 BOM + 字段 | 有显式主方案调整时附带 `schemeChoices`+`schemeRevision` |

未改动但仍属白名单的文件：`BomDetailTable.vue`、`BomItemReviewTable.vue`、`views/product/bom/index.vue`（无需变更，避免顺手改动；其分组/审核/入口能力保持原状）。

## 实际改动

1）`src/types/product/bom.ts`：新增 `ManufacturingOutputType`、`ManufacturingScheme`、`ManufacturingBomDetail`、`ManufacturingBomSavePayload`、`ManufacturingSchemeChoice`；`EngineeringBomItem` 增 `subBomId/sourceType/unitFactor*/itemOrder`；`EngineeringBomFormData` 增 `outputType/outputId/schemeChoices/schemeRevision`；`EngineeringBomVO` 增 `schemeRevision/schemeChoices`。
2）`src/api/product/bom.ts`：新增 5 个方法，路径与大小写严格照 BomController（`manufacturingOptions/saveManufacturingBom/updateManufacturingBom/adoptManufacturingBom/getBomSchemeChoices`），统一 `R<T>` 封装，adopt 无请求体。父 BOM 回显本次用 `getBomDetail` 一次拿全（含 `schemeRevision/schemeChoices`）；`getBomSchemeChoices` 封装按契约保留，本次 UI 未调用（避免多一次请求）。
3）`src/enums/product/index.ts`：新增 `ManufacturingOutputTypeEnum`（PRODUCT/MATERIAL，非状态），状态显示复用既有 `BomStatusEnum`；未新增页面级状态映射。
4）新增 `components/ManufacturingBomPath.vue`：只渲染显式引用路径与“返回上层”，不推断制造关系。
5）新增 `components/ManufacturingBomItemTable.vue`：投入行编码/名称/档案规格(只读)/单位/模数/基数/跳距/损耗/最低投料/参考用量/下层方案/备注 + 复制、删除、快捷建档；模数基数 >0、最多 4 位小数；无供给方式列、无工艺路线编辑。
6）新增 `components/ManufacturingBomDialog.vue`：产出身份、候选方案（真实版本与状态、主方案标记）、方案草稿编辑、保存（POST/PUT）、采用、设为主方案、下层下钻与返回；切换/关闭的未保存处理；无产品上下文时明确提示先保存产品。
7）`components/BomItemEditor.vue`：操作列新增“制造BOM”入口（仅已建档、非待移除行）；新增 `manufacturing` 事件、`updateItem(itemId, patch)`、`canManufacturing` 属性；模数/基数输入放开为 >0、4 位小数，并在 `validateItems` 增加 >0 校验（仅对已填值生效，历史空值行不误拦）。
8）`components/BomFormDialog.vue`：接入独立窗口；采用结果只回填该行并触发未保存提示；显式主方案记录与随父保存提交（含 revision 阻断）；换产品/重置清空制造方案上下文。
9）测试：`BomItemEditor.spec.ts`（追加 3 组用例）、新增 `manufacturingBomPath.spec.ts`、`manufacturingBomDialog.spec.ts`、`bomFormManufacturing.spec.ts`、`api/product/__tests__/manufacturingBomApi.spec.ts`。

## 保存边界与主方案语义（实现口径）

1）保存制造BOM：新建 `POST /engineering/bom/manufacturing`，已保存则 `PUT .../{bomId}`；请求带 `outputType=MATERIAL`、`outputId=投入物料ID`；不发布、不改父 BOM、不生成库存/工单。
2）采用：先要求方案已保存（`bomId>0`）且无未保存修改，再 `POST .../{bomId}/adopt`，把 `{subBomId, sourceType:'make'}` 回填父草稿该行；未保存方案直接拒绝并提示，不会假成功。
3）父保存才持久化：父 BOM 保存时 `items[].subBomId` 落库；父取消/关闭只丢弃草稿，**不删除、不回滚任何已保存制造方案**（前端无删除调用）。
4）主方案按 product×material 独立：只有用户点“设为主方案”才写 `schemeChoices=[{materialId,bomId,isPrimary:true}]`；**从不提交 isPrimary=false**，因此采用非主方案不会把该物料原主覆盖掉；行实际采用的方案（subBomId）与主方案（schemeChoices）分开表达。
5）并发：编辑已保存父 BOM 时，只要提交主方案就必须带 `getBomDetail` 返回的 `schemeRevision`；缺失则阻止保存并提示重新打开（新增 BOM 首次保存尚无详情时如实不传 revision，由后端行锁兜底，已在代码注释说明）。
6）候选列表只用后端返回的 `approveStatus/version/isCurrent/isPrimary` 展示，不自动审核、不自动生效、不本地改状态。
7）规格/耗用：档案规格只读；参考用量由既有 `calculateBomQuantity` 展示，落库与耗用权威在后端（D 阶段），本窗口不新增比例/单位换算权威算法；跨单位换算依据沿用后端 `unit_factor*` 字段，本窗口不编辑。

## 验证（真实命令与结果）

1）定向 Vitest（单 worker、`MemoryMax=1G`、120s，`systemd-run --user --scope`）：
   A) `manufacturingBomDialog.spec.ts + manufacturingBomPath.spec.ts + bomFormManufacturing.spec.ts + api/product/__tests__/manufacturingBomApi.spec.ts` → 4 files / 33 tests 全过，exit 0。
   B) `BomItemEditor.spec.ts + Selector/__tests__/bomMaterialDraft.spec.ts + bom/components/__tests__/bomReviewGroups.spec.ts` → 3 files / 28 tests 全过，exit 0。
   C) 全 `views/product/bom` 目录一次性跑在 1G 限制下被 OOM 杀掉（exit 137），故按上述两批拆分执行；未放宽内存限制。
2）既有 node:test 回归：`node --test src/components/__tests__/BomItemEditorGroups.test.cjs` → 6/6 通过；`node --test src/views/product/bom/components/__tests__/BomFormDialog.test.cjs` → 15/15 通过（未修改该文件）。
3）`NODE_OPTIONS=--max-old-space-size=3072 npx vue-tsc --noEmit` → exit 0（3072M，300s 内）。
4）`npm run check:status-enums` → 存量基线 105 处、新增 0 处；`npm run check:result-case` → 0 处；`git diff --check` → 干净。
5）`npm run check:docs`（含本文件与 INDEX 登记）见任务记录；未跑 `npm run validate` 全链（本卡未触及库存写入/迁移/表结构）。

## 已覆盖的行为用例（组件级，模拟 API）

1）候选按 MATERIAL+物料+产品上下文请求，回显已采用方案、真实版本与草稿状态，不自动审核。
2）无已保存方案时为新草稿，采用按钮不可用且不产生 adopt 请求。
3）只读/无新增编辑权限：可看候选，保存/采用均不发请求。
4）两次保存：首次 POST（校验 payload 产出身份与空日期为 null），第二次 PUT 同一 bomId，且无发布/采用顺带调用。
5）采用只发出 `adopt` 事件、不调用任何写接口；未保存时先拦。
6）主方案：无产品ID 时提示先保存产品且不发事件；显式设主只发事件、不调写接口。
7）未保存切换/关闭：X 或“继续编辑”不切换/不关闭，“放弃修改”才继续；父保存失败仍留在弹窗。
8）下层：只对挂了 `subBomId` 的行下钻，按该行物料拉候选；返回上层后上层未保存草稿仍在；无 subBomId 不进入。
9）合法小数与旧数据：0.5/2.5 通过、0 与 5 位小数被拦、损耗率越界被拦；旧跳距原样保留并进入 payload；未建档行不许保存。
10）父级：采用只改目标行；行物料已变时拒绝回填；显式主方案 payload 含 `isPrimary:true`+revision 并用响应更新 revision；只采用不设主时 payload 不出现 schemeChoices/revision；缺 revision 时拦截；换产品清空上产品主方案草稿；父取消不产生写请求。

## 未验证项与遗留

1）**未做运行态联调**：未启动前后端服务、未调用真实 HTTP；所有接口行为均为模拟替身，不能当作已联通证据。
2）**B 退修未完成**：主方案语义（含“设一个主方案会清掉其他物料主标记”的后端实现）、并发、32 层环检测、只读采用等问题由 OpenClaw 修复后由 Codex 复验；本卡未依赖未实现能力，也未替 B 兜底。
3）迁移 356 未执行（本卡不执行 DDL）；前端保存路径依赖的 `sub_bom_id/unit_factor*` 字段需在迁移后生效。
4）跨单位换算依据（`unitFactor/unitFactorBasis`）本窗口只透传不编辑，跨单位新行/变更行由后端 `UNIT_MISMATCH/UNIT_FACTOR_MISSING` 阻断；是否补换算输入 UI 待 B 复验与用户口径。
5）D（精确耗用/净需求）未实现，本窗口不展示任何“已实时计算”的耗用或缺口。
6）C 验收依赖 B 复验通过后的真实联调；本次六行报告中的“验”仅代表前端编译与定向测试。

## 接续

下一步：Codex 复验 B 修复 → 真实接口联调（保存/回读/采用/父保存/scheme-choices）→ C 独立验收。前端如需补换算依据编辑、或候选列表需要按状态过滤，应在验收反馈里明确后再改，不在本卡范围外顺手扩展。
