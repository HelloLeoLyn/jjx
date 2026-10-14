# 迁移文件缺失盘点：账本 ↔ 仓内 差集清单（dev-20261014-010）

> 任务码：dev-20261014-010 ｜ 日期：2026-10-14 ｜ 执行：Hermes ｜ 性质：**只读取证**（未恢复任何文件、未动库、未改门禁）
> 卡面：`jjx-docs/task-docs/dev-20261014-010-plan.md`（第 2 节第 1 步：生成差集清单）

---

## 1）结论

1）仓内迁移文件**大面积缺失属实**：账本 211 号（66…279），仓内只有 25 个文件（256…280）→ **缺失 187 个号**（`66-98, 100-211, 214-255`）。
2）缺失的 187 个里，**185 个可从 git 历史原样取回**；**真正不可恢复的只有 2 个：192、194**。
3）**卡面原估"187 个号不可恢复/工作量大"需修正**：早期迁移文件名是**2 位前缀**（`66_…`、`100_…`），按 3 位正则扫描会误判为"从未进过历史"；按 1~3 位通配后 66-98 全部可恢复。
4）**仍被仓内代码/脚本/文档引用的缺失文件只有 10 个**，全部可恢复（含 `193_collation_unify_utf8mb4_unicode_ci.sql`，711 行，实测可取回）。
5）另有两条独立发现（不在本卡修复范围，建议单独登记）：账本自身**断号 99 / 212 / 213**；历史里存在**同号多文件 25 组**（撞号证据）。

---

## 2）判据与复跑命令（全只读）

1）账本 = `sys_config.ops.schema.applied`（AGENTS.md：是否执行过以账本为准）：
   `SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';`
2）仓内文件 = `jjx-docs/sql/migrations/` + `jjx-docs/sql/migrations/applied/`。
3）可恢复性 = `git log --all --pretty=format: --name-only --diff-filter=AD -- 'jjx-docs/sql/migrations/*.sql'` 里是否有该文件。
4）引用扫描 = `grep -rnoE 'migrations/(applied/)?[0-9]{3}_[A-Za-z0-9_]+\.sql' scripts/ jjx-docs/ AGENTS.md`。
5）复跑脚本：本次为 `/tmp` 临时件（未入库，随会话清理）；口径即上面 1~4 条，可据此重跑。

---

## 3）数据总览

| 项 | 值 |
|---|---|
| 账本号 | 211 个（66…279） |
| 仓内文件号 | 25 个（256…280） |
| 缺失（账本有、仓里无） | **187** 个 → `66-98, 100-211, 214-255` |
| 可恢复 | **185** 个 → `66-98, 100-191, 193, 195-211, 214-255` |
| 不可恢复 | **2** 个 → `192, 194` |
| 未记账（仓内有、账本无） | 1 个 → `280`（并行会话在制，尚未执行，属正常） |
| 账本自身断号 | `99, 212, 213` |
| 历史有、账本无（除断号） | `1-65`（早期 2 位号段，账本从 66 起）+ `280` |

---

## 4）仍被引用的缺失文件（10 个，全部可恢复）

| 号 | 文件名 | 引用点 | 恢复来源（`git show <add>:`） |
|---|---|---|---|
| 100 | `100_split_sample_order.sql` | `jjx-docs/history/sample-order-split-dev-20260912-001.md` | `ae0975db` |
| 148 | `148_role_menu_fix_system_orphans.sql` | `jjx-docs/standards/CONVENTIONS.md` | `67820b33` |
| 193 | `193_collation_unify_utf8mb4_unicode_ci.sql` | `jjx-docs/task-docs/dev-20261014-006-plan.md` | `9c5bdd58`（711 行，实测可取回） |
| 204 | `204_purchase_receipt_doc_perms.sql` | `jjx-docs/design/purchase-receiving-migration-dev-20260923-003.md` | `cbc4786e` |
| 207 | `207_rename_prod_order_prefix_pm_wo.sql` | `jjx-docs/design/doc-no-batch5-workorder-merge-dev-20260923-029.md` | `d45878e7` |
| 246 | `246_link_sales_outbound_delivery_item.sql` | `jjx-docs/history/merged-delivery-real-data-dev-20261008-024.md` | `377a70a5` |
| 247 | `247_sales_customer_address.sql` | `jjx-docs/history/customer-shipping-address-book-dev-20261008-029.md` | `306dfffb` |
| 248 | `248_sales_quotation_shipping_fee.sql` | `jjx-docs/history/quotation-shipping-fee-dev-20261009-007.md` | `390e0eaa` |
| 249 | `249_sales_order_shortage_check_permission.sql` | `jjx-docs/history/sales-order-shortage-permission-dev-20261009-009.md` | `f696ac37` |
| 254 | `254_fix_material_sequence_to_1601.sql` | `jjx-docs/history/engineering-drawing-workspace-dev-20261009-029.md` | `0bb84bae` |

补充：`148` 在历史里有**两个同名号文件**（`148_display_name_normalize.sql` / `148_role_menu_fix_system_orphans.sql`），`254` 与 `253/255` 也相互纠缠 —— 恢复时**逐号辨别**（见第 6 节），不要按目录批量覆盖。

---

## 5）不可恢复的 2 个

- `192`、`194`：在 `jjx-docs/sql/migrations/` 的**全部 git 历史**里都没有对应文件（其余 185 个号都有）。
- 处置建议：保持"不可恢复"，在 `CONVENTIONS.md` 记一句「以账本为准，文件不可取回，不重跑、不臆造」；**不得**按当前库结构反推伪造"新 192/194"（卡面反例 3）。

---

## 6）两条独立发现（不在本卡范围，建议单独登记）

1）**账本自身断号 99 / 212 / 213**：历史里存在 `99_optimize_sample_order_page.sql`、`212_*`、`213_*`，但账本没有这三个号 → 需要单独判定是"执行过未记账"还是"从未执行"（口径：查库结构 + 这些文件内容）。**不要**顺手补记账。
2）**同号多文件 25 组**（撞号历史证据；多 agent 并发各建同号迁移，后执行者被 `ops.schema.applied` 判为"已应用"而永久跳过 —— 与 CONVENTIONS 里记的 145/148 事故同源）：
   `41, 43, 52, 53, 63, 68, 82, 128, 129, 136, 145, 148, 154, 155, 156, 157, 167, 199, 219, 234, 240, 253, 254, 255, 261`
   恢复时**同名号组必须逐个判断哪一个是账本记录的那个**（用文件头注释/README/提交信息对齐），不能覆盖写。

---

## 7）恢复方式（待拍板后执行，本次未做）

1）命令形状（原样取回，不改内容、不改文件名）：
   `git show <add-commit>:jjx-docs/sql/migrations/<name>.sql > jjx-docs/sql/migrations/applied/<name>.sql`
2）落 `applied/`（账本已记 = 已应用态；`pre-commit` 只放行同名同内容的归档移动，本操作是"新增尚未在 index 的文件"，不受该拦截影响）。
3）**禁止**：重跑任何迁移；`db-migrate.sh --status` 里不得因此出现"待执行"；不顺手删其它文件；不臆造 192/194。

---

## 8）待用户拍板（二选一）

**A（最小）**：只恢复第 4 节的 10 个被引用文件 + 把 `scripts/check-collation.sh` 的提示改到**存在**的文件/新口径 + 在 `CONVENTIONS.md` 记「历史文件缺失以账本为准，不重跑」+ 记 192/194 不可恢复。

**B（全量）**：恢复全部 185 个可恢复文件（机械 `git show`，约 1 个脚本、零 DB 动作），另记 192/194 不可恢复 + 修门禁指向。
- 费用：仓内多 185 个历史 SQL 文件（体积小，纯 DDL/种子）；收益：账本 ⊆ 仓内，审计与"这个号当时干了什么"可自查。
- 风险：低（都是已应用态、不执行）；注意第 6 节的同号组需逐个辨别。

**我的建议**：选 **B**，理由是本轮用户口径强调"要有记录、防扯皮"——185 个机械可恢复，成本远低于卡面原估（原估基于"大部分不可恢复"的误判）；门禁指向与 192/194 说明无论 A/B 都要做。

---

## 9）验证口径（拍板后按此验收）

1）`grep -rn "193_collation" scripts/ jjx-docs/` → 提示指向的文件真实存在（或提示不再依赖该文件）。
2）`bash scripts/db-migrate.sh --status` → 不出现新的"待执行"（恢复文件均为已记账）。
3）`cd jjx-web && npm run check:docs` 通过；清单文件带 BOM 且登记 `jjx-docs/history/INDEX.md`。
4）反例自检：不重跑迁移、不伪造文件、不因为"文件回来了"就宣称"库结构已修复"。

---

## 10）拍板与落地（2026-10-14，用户口径）

**用户拍板：不清仓内、不恢复历史** —— 原话「清空历史，只保留未执行的，同步更新门禁」，二选一时选 **B**：保留 `applied/` 里已有的 24 个已执行归档，**只保证不再新增历史文件**，并把门禁口径同步改掉。

已执行的改动（本卡范围内）：

1）`scripts/check-collation.sh`：背景注释不再指向已不在仓的 `193_collation_unify_utf8mb4_unicode_ci.sql`（改为「首见记录」中性表述 + 指向现行 `278_attachment_ref_collation_unify.sql`）；`--strict` 用法提示由"迁移 193 执行后改用这个"改为"全库漂移为 0 时启用；validate 走这个"。
2）`jjx-docs/standards/CONVENTIONS.md` §3 新增条款：**历史缺失文件不恢复（66–255 共 187 个不回填；185 个虽可取回也不恢复；192/194 本无）**，以账本为准，禁止按当前库结构反推伪造。
3）`AGENTS.md` 迁移条目追加同一句，并指向本清单。
4）**未改**：`db-migrate.sh`（实测 `--status` 只列 1 个待执行 `280`、0 个待归档；缺失文件不影响它按账本判定）、`pre-commit`（B 方案不删任何 `jjx-docs/sql/**` 文件，无需放开拦截）。

验证（实跑）：

- `bash scripts/check-collation.sh` → 退出 0，全库字符串列已统一（漂移 0）。
- `npm run check:migration-collation` → 通过（扫描 1 个待办迁移）。
- `npm run check:docs` → 通过（history 250 篇）。
- `grep -rn "193_collation|db-migrate.sh 19[0-9]_" scripts/ AGENTS.md` → **0 命中**（死引用已清；仅本清单与两张卡文件作为记录提及文件名）。

遗留：断号 `99 / 212 / 213` 与同号多文件 25 组（第 6 节）**不在本卡处理**，建议单独登记。
