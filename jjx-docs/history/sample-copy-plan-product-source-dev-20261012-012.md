# 打样工作台「复制打样方案」新增「成品方案」来源 —— 实现记录

> 任务码：dev-20261012-012 ｜ 日期：2026-10-12 ｜ 状态：已实现（待审核）
> 依据：dev-20261012-010 设计稿（Hermes）｜ 拍板：Leo 2026-10-12

---

## 1. 交付内容

### 后端（只读带出，不落库）

- 新接口：`GET /sales/sample-order/{orderId}/product-plan`
  - `SampleOrderController` / `ISampleOrderService` / `SampleOrderServiceImpl` + 新 VO `SampleProductPlanVO`
  - 逻辑：取样品单产品的「`is_current=1` 且 `approve_status=3`」BOM + 工艺路线 → 映射为打样工序行形状（复用 `SalesSampleProcess` 字段，返回临时列表不落库）
  - 卡片分组：工艺路线**父行（parent_id=null）为一张卡片**，子行随父行；`PRINT` 大类行进印刷表，其余进卡片
  - 材料：`engineering_bom_item` 按 `process_id` 挂到对应工序；JSON 键沿用现有约定 `name/spec/qty/unit/materialId/materialCode`
  - 不可带出情形（`eligible=false` + `reason`）：无产品档案 / 无 BOM / BOM 未批准 / 无路线 / 路线未批准

### 前端（弹窗第三来源）

- 文件：`workbench.vue`、`useSampleWorkbench.ts`、`api/sales/sampleOrder.ts`、`types/sales/sampleOrder.ts`
- 「📋 复制打样方案」弹窗新增第三 tab「成品方案」：展示产品、BOM 版本(已批准)、路线版本(已批准)、工序数、材料数；不符合条件时禁用 + 原因提示
- **追加/覆盖**：弹窗底部单选（默认追加），对三个来源（本单历史轮 / 其他样品单 / 成品方案）统一生效
- 抽出公共函数 `applyCopiedRows(list, overwrite)`，三个来源共用（避免三份拷贝，设计稿 4.7.2 建议）

## 2. 拍板口径（Leo 2026-10-12）

1. 一个 tab「成品方案」（路线 + 材料一起带）
2. 追加/覆盖由用户选（默认追加）
3. 材料编码：**不改结构**，沿用现有工序 materials JSON 的 `materialId/materialCode` 键
4. 说明字段：`description` 优先；`work_instruction` 并入备注
5. 只带「已批准」

## 3. 对设计稿 4.5 的更正（重要）

设计稿 4.5 称"工序 materials JSON 无 code"为**误判**：现状前端保存工序计划时**已写入** `materialId/materialCode`（`useSampleWorkbench.ts:626-627`，添加材料 `:889-890`、卡片材料表 `:933-934`，选物料/建档回填 `:1022/:1045`）。故带出时把 `engineering_bom_item.material_id/material_code` 直接塞进同样的 JSON 键即可，**零结构改动**。

残留（既有机制，不在本任务范围）：资料转移回成品 BOM 时后端**按名称重捞** code（`SampleOrderServiceImpl:1912-1920`），未读 JSON 里已有的 `materialId/materialCode`。

## 4. 规范更新（`jjx-docs/specs/data-inheritance-spec.md`）

- §二 新增转换点 6「样品单 → 打样工序『成品方案』带出」
- §三 补字段传承对照表 + 新增约束条款（新增带出入口必须登记）
- §五 更正过期条目「样品→量产不传承工艺路线」（实际已版本化传承，带 `source_sample_id`）

## 5. 验证（仅静态自查）

- 后端：`JAVA_HOME=java-21 mvn -o compile` 通过。
  - 注：主工作树因**他会话未提交**的 `AttachmentRefService.java`（未跟踪、`jdbcTemplate.query` 二义性）编译不过；已在 HEAD(9332ac2b) 的独立 worktree 内复制本任务 4 个文件后编译验证通过。
- 前端：`npx vue-tsc --noEmit` 通过；`check:result-case`、`check:docs` 通过。
- `check:status-enums` 报的 8 条为他会话新增文件（`usePrintFieldSuggest.ts`、`OrderBatchActions.vue`），与本任务无关。
- 未打包、未重启、未点页面（遵循只做静态自查）。

## 附. 复查修复（2026-10-12 16:0x）

**现象**：Leo 实测 SP261012001 复制后打样 BOM 面板只有 3 条（少了 `RM001609` 3M9080）。

**根因**：打样材料是**卡片级**模型——卡内各行共享一份材料（`savePlan` 把 `pc.materials` 写给卡内每一行；`loadBom`/`loadPlan` 只读组内首行材料）。而本任务后端按**工序行**逐行发材料，复制流水线 `makeCard({materials: first.materials})` 只取了**首行**材料 → 组合卡片（面板+面板背胶）里「面板背胶」的材料被「面板」的覆盖丢失。

**修复**：`applyCopiedRows` 增 `mergeCardMaterials(rows)`——卡片材料 = 卡内各行材料的**并集**（按 `materialId|name|spec` 去重）。现有打样源数据卡内各行材料本就相同，并集=原值，不影响既有复制路径；成品方案带出组合卡片不再丢料。

**再修（未挂工序材料）**：产品 BOM 5 条，带出仅 4 条——「透明易撕贴」的 `process_id` 为空，被「按工序挂料」逻辑静默跳过。后端 VO 增 `unlinkedMaterials`（未挂工序材料单独返回），前端成品方案 tab 出**警告**列出并在复制时**暂挂首道工序 + 弹窗提示核对**（不静默、不丢失）。commit `5d4abaea`。

> 遗留（另立任务）：源头治理——BOM 导入/保存时校验未挂工序材料（提示/拦截）、“整单通用材料”合法标记、使带出入口入规范登记。

## 6. 未做 / 遗留

- 未自动落库、未改 `createSample`、未改资料转移（设计非目标）
- 可选优化（未做）：资料转移优先读工序 JSON 的 `materialId/materialCode`、回退按名称
- 运行时验证（新增接口是否正常返回、弹窗第三 tab 实际渲染）需重启后端后由用户执行
