# 制造BOM独立窗口（C阶段）：前端窗口、方案选择、逐层采用回填与路径返回

任务：dev-20261111-005（总任务 dev-20261111-002 子任务 C）。执行者：Hermes；验收者：Codex（独立验收）。
日期：2026-11-11（Asia/Shanghai），本文最后更新 2026-11-11 15:05 左右（退修第二轮）。工作目录：/home/administrator/jjx。
状态：前端代码与定向组件/API 测试已完成（**只代表代码与组件测试范围**）；**运行态联调与 C 验收未进行**，等 B（dev-20261111-004）复验通过后由 Codex 解锁。

## 授权与交付边界

1）用户当前明确指令（原话）：“那如果没什么问题,那就继续推进”——同意继续推进本任务，不是对具体功能细节的逐字授权。
2）本卡的“前端专卡范围”（只写哪 15 个前端/测试路径、只改 `types/product/index.ts` 的 `ProductVo.schemeRevision` 可选字段、不改 INDEX）来自 **Codex 派发内容**，不是用户逐字原话；两者分开记录，避免把派工文本写成用户承诺。
3）只写前端与本文档；未改后端、未改迁移/备份/baseline；未暂存或覆盖他人在制（开工与退修基线里 OpenClaw 004 的 `ProductVo.java`、`EngineeringBomServiceImpl.java`、备份日志、356 归档从头到尾未动）。
4）未启动/重启任何服务，未改运行态；**未执行迁移 356**（见“现场事实复核”第 3 条）。
5）退修第二轮（本文档更新范围）：新建下层入口、逐层采用回填、新建 BOM 的真实 `schemeRevision`、跨单位换算依据 UI、移除用量计算副本、权限矩阵拆分、预审文字清理、文档事实纠正。

## 现场事实复核（2026-11-11 14:52–15:05 只读）

1）`jjx-server/.../engineering/controller/BomController.java:47-82`：`GET /engineering/bom/manufacturing/options`、`POST /engineering/bom/manufacturing`、`PUT /engineering/bom/manufacturing/{bomId}`、`POST /engineering/bom/manufacturing/{bomId}/adopt`；`:176-182` `GET /engineering/bom/{bomId}/scheme-choices`；权限 `engineering:bom:view/add/edit`。本轮未改这些路径。
2）`EngineeringBomServiceImpl.applySchemeChoices`（`:1974-2056`）当前语义：`isPrimary=true` **只写/替换该 `materialId` 节点**，其他物料节点保持原样；`isPrimary=false/null` 仅代表父 BOM 实际采用、不改主；`cancelPrimary=true` 才移除该物料主方案；`dto.schemeRevision` 与库内 `product.scheme_revision` 不等返回 409 `CONCURRENT_MODIFICATION`；真正写 JSON 而 `schemeRevision` 为空返回 400 `SCHEME_REVISION_REQUIRED`。
   → 2026-11-11 14:36 上一版记录里“设一个主方案会清掉其他节点”的现场快照**已过时**，以本节为准（该改动属 OpenClaw 004 范围，不是本卡改的）。
3）迁移账本：`sys_config.ops.schema.version = 356`，`ops.schema.applied` 含 356，`update_time = 2026-11-11 14:07:35`。即**356 已执行并记账**，执行者不是本卡（本卡未执行任何 DDL），此前“356 尚未执行”的说法作废。
4）可达性校验：`usedMaterialIdsForScheme`（`:2063-2095`）从本次 DTO 根行沿 `sub_bom_id` 逐层展开，只有该显式关联图可达的投入物料才允许写主方案；不可达时抛“父 BOM 未使用该物料，不能设置/取消主方案”。
5）单位依据：`EngineeringBomItemDTO` 有 `unitFactor/unitFactorBasis(≤200)/unitFactorSource(≤100)`；`validateUnitBasis`（`:1747-1777`）同单位不强制系数，跨单位要求 `unitFactor>0`（≤8 位小数）且 `unitFactorBasis` 非空。
6）`ProductVo` 已含 `schemeRevision` 字段；`GET /product/{id}` 把它回传由 004 补（本卡只读前端 `types` 可选声明 + 直接 `import { getProductInfo }`，未改该接口）。
7）`lossRate` 后端仍是 `Integer`（`EngineeringBomItemDTO:81`）：本窗口只收整数，精度改造列入 dev-20261111-006，不在本卡放宽。

## 入口 × 新增/编辑 × 父保存矩阵（本轮逐项复核）

| 入口 | 新增 | 编辑 | 保存对象 | 本轮处理 |
|---|---|---|---|---|
| BOM管理页 → BomFormDialog（整单） | 有 | 有 | 父 BOM 主表+明细+schemeChoices（显式调整时） | 材料行操作列“制造BOM”入口；采用只回填草稿；新建 BOM 设主用 `GET /product/{id}` 真实 revision |
| 作业规范页 → BomFormDialog（整单） | 无（仅按 bomId 打开编辑） | 有 | 同上 | 共用组件，行为一致；编辑用 `getBomDetail` 的 revision |
| BomItemEditor（共享材料行） | 有 | 有 | 不保存 | 入口仅对已建档、非待移除、**非只读**行显示；`updateItem(itemId, patch)` 按稳定身份回填 |
| 制造BOM独立窗口（顶层） | 新建方案 | 编辑方案 | 制造方案自身（engineering_bom + 明细） | 独立保存；不发布、不改父 BOM、不设 isCurrent |
| 制造BOM窗口 → 下层（有 subBomId） | — | 查看/编辑下层方案 | 下层方案自身 | 逐层查看与返回，上层草稿保留 |
| 制造BOM窗口 → 下层（无 subBomId，已建档物料） | 新建下层方案 | — | 下层方案自身 | 入口“制造BOM”：按该物料拉候选并新建；只读/无权限不进入 |
| 采用此方案（顶层） | — | — | 仅前端父草稿 | 发 `adopt` 事件给产品BOM行 |
| 采用此方案（下层） | — | — | 仅直接上层草稿行 | 只回填 `frame.parent` 对应的那一行 `subBomId/sourceType`，父草稿变 dirty |
| 设为主方案（顶层） | — | — | 仅前端父草稿 | 立即发 `primary-change`，随产品BOM保存提交 |
| 设为主方案（下层） | — | — | 挂起 | 先记录，显式引用链逐层保存且回到根、图可达后才发 `primary-change` |
| 产品BOM整单保存 | 有 | 有 | 父 BOM + 字段 | 有显式主方案调整时带 `schemeChoices`+真实 `schemeRevision`；409 保留草稿 |

未改动但仍属白名单的文件：`BomDetailTable.vue`、`BomItemReviewTable.vue`、`views/product/bom/index.vue`（无需变更，避免顺手改动）。

## 权限矩阵（本轮拆分，沿用既有 BOM 权限枚举）

| 角色 | 查看候选/明细 | 新建方案 | 编辑已存方案 | 采用 | 设为主方案 |
|---|---|---|---|---|---|
| view-only（无 add/edit） | 有 | 无 | 无 | 无 | 无 |
| add-only | 有 | 有（且可保存新方案） | 无（字段只读、保存被拦） | 有 | 无（属修改产品主方案） |
| edit-only | 有 | 无 | 有 | 有 | 有 |
| add+edit | 有 | 有 | 有 | 有 | 有 |

实现：`canCreate = !readonly && hasPermi('engineering:bom:add')`；`canEditStored = !readonly && hasPermi('engineering:bom:edit')`；`editable = draft.bomId ? canEditStored : canCreate`；采用 = add 或 edit；只读用户无 subBomId 时不进入新建下层。

## 本轮实际改动（相对提交 6842d30a）

1）`ManufacturingBomItemTable.vue`：新增“档案单位/投入单位”分列；跨单位时同列提供 `unitFactor`（正数，≤8 位小数）/`unitFactorBasis`（≤200）/`unitFactorSource`（≤100）填写与回显，改投入单位即清空旧系数避免误用；下层列对已建档但无 `subBomId` 的行提供“制造BOM”入口；移除 `calculateBomQuantity` 计算副本与 `handleRatioChange` 写 `row.quantity`，参考用量只取保存回读值、比例改动只标“待保存后更新”；损耗率限整数并明确校验。
2）`ManufacturingBomDialog.vue`：`drillDown` 允许已建档物料无 `subBomId` 进入候选并新建（无权限/只读不许）；帧保存父投入行稳定身份（`frameKey + itemId + materialId`），深层采用只回填直接上层目标行并令父草稿 dirty，顶层才 `emit('adopt')`；深层设主挂起，`chainReachable` 校验逐层已保存且图可达后才 `emit('primary-change')`；栈改 `activeIndex` 指针，返回上层后下层帧仍保留用于可达核对；产出头部编码/名称/规格/单位取物料档案（`materialApi.getInfo`），不被父 BOM 行覆盖值冒充；权限矩阵拆分；清理 MATERIAL/可靠产品ID/后端递增/无界等实现文字，方案显示编码+版本。
3）`BomFormDialog.vue`：新建 BOM 首次设主方案改用 `GET /product/{id}` 的真实 `schemeRevision`（迟到响应按 token+当前产品丢弃，读不到即阻止保存，不默认 0）；编辑继续用 `getBomDetail` 的 revision；409 冲突保留草稿并提示重新核对；换产品/清空产品清空制造方案上下文。
4）`types/product/bom.ts`：新增 `ManufacturingSchemeLabel`、`ManufacturingBomSaveItem`（quantity 可空，后端重算）；`EngineeringBomItem` 增前端展示用 `archiveUnit/quantityStale`（不落库）。
5）`types/product/index.ts`：仅新增 `ProductVo.schemeRevision?: number | null`。
6）`ManufacturingBomPath.vue`：提示与回退文字改为业务表述，不再用 `物料 {id}`/`方案 {id}`。
7）`BomItemEditor.vue`：制造BOM 入口增加非只读条件。
8）测试：`manufacturingBomDialog.spec.ts`（16）、`bomFormManufacturing.spec.ts`（12）、`BomItemEditor.spec.ts`（入口只读用例）、`manufacturingBomPath.spec.ts`（回退文字）、`api/product/__tests__/manufacturingBomApi.spec.ts`（契约未变）。

## 关键行为（本轮补齐的部分）

1）**真正新建多层**：投入半成品行即使没有 `subBomId`，也能进入该物料的候选并新建制造方案；不靠临时成品BOM绕路，也不从分组/规格推断关系。
2）**逐层采用**：下层采用只回填直接上层那一行（`itemId` 优先，物料已变则拒绝回填并提示），父草稿变 dirty；兄弟材料不变；未保存/取消按当前层提示，不串层。产品→A→B→原料的组件路径：A 保存→下钻 B 新建保存→B 采用回填 A 行→返回 A 保存→顶层采用交给产品BOM→产品BOM最后保存。
3）**新建 BOM 真实 revision**：新增保存主方案必须带 `GET /product/{id}` 读到的真实版本（用例断言 7 而非 0）；版本未知则阻止保存；切换产品用 token+产品校验丢弃迟到响应；409 保留草稿。
4）**跨单位换算依据**：同单位不需要系数；跨单位缺系数或缺少依据时前端定位到行提示，后端仍校验；有依据时完整回显并随请求提交；改投入单位后旧系数不整用；不自动计算面积/卷长。
5）**用量唯一权威在后端**：窗口不计算、不回写 `row.quantity`；未保存改动只显示“待保存后更新”，提交原始 `moduleQty/baseQty` 由后端重算。

## 验证（真实命令与结果，2026-11-11 14:5x–15:0x）

1）定向 Vitest（`systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run --no-file-parallelism`）：
   A) `manufacturingBomDialog.spec.ts + bomFormManufacturing.spec.ts` → 2 files / 28 tests 全过，exit 0。
   B) `manufacturingBomPath.spec.ts + api/product/__tests__/manufacturingBomApi.spec.ts + components/__tests__/BomItemEditor.spec.ts` → 3 files / 21 tests 全过，exit 0。
   C) 仍未把整个 `views/product/bom` 目录一次跑（1G 下曾 OOM），按定向小批拆分。
2）既有 node:test 回归：`node --test .../BomFormDialog.test.cjs .../BomItemEditorGroups.test.cjs` → 21/21 通过（15+6，未修改这两个文件）。
3）`node --max-old-space-size=2560 node_modules/vue-tsc/bin/vue-tsc.js --noEmit`（`MemoryMax=3072M`、300s 内、单独运行，未与 004 Maven 并行）→ exit 0。先用默认堆跑一次 OOM（exit 134，触发 1.5G 堆上限），显式提高堆后通过，如实记录。
4）`npm run check:status-enums` → 存量基线 105 处、新增 0 处；`npm run check:result-case` → 0 处；`npm run check:docs` → 通过；`git diff --check` → 干净。
5）未跑 `npm run validate` 全链（本卡不触及库存写入/迁移/表结构）；未跑后端 mvn（本卡不改后端）。

## 未验证项与遗留

1）**未做运行态联调**：未启动前后端服务、未调用真实 HTTP；所有接口行为均为组件测试替身，不能当作已联通证据。
2）**B（004）仍在退修**：主方案可达性校验、深层设主等由 OpenClaw 修复后由 Codex 复验；本轮前端按已读到的当前契约实现，若 004 改契约需重新对齐。
3）`lossRate` 小数精度属 dev-20261111-006 后端任务；本窗口当前只收整数，未擅自改后端。
4）D（精确耗用/净需求）未实现，本窗口不展示任何“已实时计算”的耗用或缺口。
5）C 验收依赖 B 复验后的真实联调；六行报告中的“验”仅代表前端编译与定向组件测试。

## 接续

下一步：Codex 复验 B 修复 → 真实接口联调（新建下层/保存/采用/父保存/scheme-choices/revision 409）→ C 独立验收。若验收要求按状态过滤候选或调整换算交互，另行派工，不在本卡范围外顺手扩展。
