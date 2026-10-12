# 公共附件历史兼容与迁移策略收口（A段 refs / B段 LEGACY）

- 状态：现行结论（收口）
- 任务：dev-20261012-031（ATT-V1B-06；依赖 dev-20261012-030）
- 被取代或取代：无（收口 V1-A 引入的 A/B 双段兼容，不改变其行为）
- 什么情况看这篇：判断"历史附件为何显示/不显示、能否解绑、要不要迁移"
- 最后复核：2026-10-12

## 1. 背景与范围

公共附件 V1-A 引入引用表 `sys_attachment_ref`（下称 **A段 refs**，多态引用），与 `sys_attachment` 老行的 `biz_type/biz_id` 硬引用（下称 **B段 LEGACY**）并存。本卡收敛二者的**读取 / 展示 / 解绑边界**与**后续迁移策略**，不改行为、不做数据迁移。

- 依赖：ATT-V1B-05（dev-20261012-030，产品附件入口梳理）。
- 关联：dev-20261012-003（历史附件回填脚本）、dev-20261012-011（质量模板历史引用，**不纳入本包实施**）。

## 2. 现状证据

1）表数据（2026-10-12 只读实测）

- `sys_attachment`：38 行（未软删 35）；`upload_state` 分布 = **LEGACY 21 / BOUND 12 / TEMP 3 / ORPHAN 2**。
- `sys_attachment_ref`：14 行（**活动 6 / 已解绑 8**）。

2）代码落点（行号为定位线索，以源码为准）

- 后端合并规则 `SysAttachmentServiceImpl.getAttachments()`（约 line 322-331）：
  `WHERE biz_type=? AND biz_id=? AND ( NOT EXISTS(该附件指向该来源的任何 ref) OR EXISTS(该附件指向该来源的活动 ref) )`
  → **无 ref ⇒ 按 B段 硬引用展示；有过 ref（含已解绑）⇒ 不再以 B段 复活**。
- 前端合并规则 `AttachmentCenter.vue`（V1-B WP1/WP2 §2.3）：A段 refs（主键 `refId`）**全部保留**；B段 `GET /attachment/list`（主键 `attachmentId`）**仅隐藏"本单据已有活动 ref"** 的记录，其余保留并标「历史附件」只读——**无解绑按钮、不自动建引用、不改写 `upload_state`**。**refId ≠ attachmentId**。
- 权限：`AttachmentSourcePolicy`（8 来源白名单 `product/inquiry/quotation/sales_order/sample/purchase_order/production_order/material` + `:view/:edit`，未知来源 **fail-closed**）；`AttachmentAccessService.canRead / checkUnlink / protectedFile`。
- 状态机：`AttachmentUploadState{LEGACY,TEMP,BOUND,ORPHAN}`；新上传建档 = BOUND（`SysAttachmentServiceImpl` 建档处）；`AttachmentRefService.create()`/`unlink()` 对 LEGACY 一律**保持 LEGACY**。

## 3. 五项核查结论

1）合并去重（A段↔B段）
- 判据 = **该附件是否对当前来源存在任何 ref 记录（含已解绑）**：无 → B段 兜底展示；有 → 只听 refs，B段 不再兜底。
- 前端二次去重：同一 `attachmentId` 若在本单据已有活动引用，则不再从 B段 列出（避免同文件两个入口）。合并按 `refId` 与 `attachmentId` **分别去重**，不混主键。

2）同一附件多个业务引用的展示规则
- 一个附件可挂多条 source 引用（跨单据复用）；**分来源、分产品各自展示**，各自独立 `refId`。
- 下载/预览按**该条 ref 的来源**鉴权，**不能用另一个可见引用绕过**（`AttachmentRefService.readable` + 按 refId 的 download/preview）。

3）历史附件只读与权限
- 读取统一走来源白名单 + `:view`；写走 `:edit`；未知来源拒绝。
- `canRead` 回退规则：TEMP/ORPHAN 仅上传人且未过期可读；否则有活动 ref 且来源可读；**显式解绑过的 legacy 关联永不放行**（仅当该 (source,id) 无任何 ref 历史时，才回退 `biz_type/biz_id` 判权）。
- `checkUnlink/protectedFile` 拦截解绑：受控/已发布/现行文件、所有规范版本 `DRAWING`、质量模板 `file_id`、菲林 `file_id`、产品结构图、`trace_id` 证据、历史审批/样品/客户附件硬引用。

4）LEGACY 不自动转 BOUND
- `create()`：`LEGACY ? LEGACY : BOUND`；`unlink()`：`LEGACY ? LEGACY : ORPHAN` ⇒ **LEGACY 状态永不因建/解引用而改变**。
- 所有 LEGACY 文件标人工复核，**不进自动清理候选**；物理删除全库由 DELETE 触发器 + `cleanup-preview` 拦（V1-A）。

5）是否需要迁移
- V1-A 只读预演：可回填 8 / 无效来源 3 / 已删附件 1，**实际回填 0 条**；现 refs 14 行中活动 6、已解绑 8（数据非固定预期，以执行时脚本为准）。
- **默认不迁移**：无明确收益时保留 A段 refs + B段 LEGACY 兼容读取。

## 4. 兼容规则（定稿）

- **R1** A段优先、B段兜底：无 ref 才显示 B段；有 ref（含已解绑）不再回退。
- **R2** 解绑 = 软删 ref，**绝不删除/改写 B段 legacy 硬引用**；被显式解绑后不以 legacy 复活。
- **R3** 多引用按来源/产品隔离展示，下载按 ref 来源鉴权。
- **R4** 只读 + 权限：来源白名单 + view/edit，fail-closed；受控/硬引用禁解绑。
- **R5** LEGACY 冻结：不自动转 BOUND、不进自动清理，人工复核。
- **R6** `refId ≠ attachmentId`：前端/接口不得混用主键。

## 5. 迁移决策

- **结论：默认不执行历史数据迁移**，保留兼容读取。
- 收益（若迁移）：B段 legacy 收敛为统一 refs，入口/权限/解绑模型单一化。
- 风险/成本：历史 JSON/路径硬引用难以穷举（保守数字边界可能多保护）；附件**身份**无法由时间戳证明（需 sha256，现表未存）；物理文件缺失需人工清单核查。→ 收益不足以覆盖风险。
- 触发条件（将来重议）：需要统一行级授权、或出现必须按 refs 统计的业务诉求时，再评估。
- 若执行：走 dev-20261012-003 回填脚本（**只 INSERT 引用**，不覆盖元数据、不复活解绑），必须人工备份 + 维护窗口，幂等可重跑。

## 6. 验证与边界

- 本轮为**核查+收口**：只读查库 + 代码走查；**未执行迁移、未改代码/数据、未重启服务**。
- 边界：前端 A段文件名/大小依赖 B段列表回填（已知限制，建议后端在 refs 列表补 `file_name/file_size`，见 WP1 文档 §已知限制）。

## 7. 待确认点

1）是否维持"默认不迁移"（本文结论）；若要迁移，先定业务收益与人工窗口。
2）质量模板 19 条异常引用 / 历史硬引用清单 → 由 dev-20261012-011 单列处置。
3）B段 文件名回填缺口的后端补字段，是否另立卡。
