# 迁移撞号复盘：账本断号 99/212/213 与同号多文件 25 组

> 任务码：dev-20261014-037 ｜ 日期：2026-10-14 ｜ 执行：OpenClaw（subagent）｜ 性质：**只读取证**（未改库、未补记账、未恢复文件、未重跑、未覆盖写）
> 卡面：`sys_task.dev-20261014-037`（来源 dev-20261014-010 第 6 节遗留）
> 前置：`jjx-docs/history/migration-file-gap-inventory-dev-20261014-010.md` 第 6 节、`jjx-docs/standards/CONVENTIONS.md` §3

---

## 1）结论摘要

**问题一（账本自身断号 99 / 212 / 213）**

| 号 | 文件（任务码） | 结论 | 关键证据 |
|---|---|---|---|
| 99 | `99_optimize_sample_order_page.sql`（dev-20260911-009） | **从未执行**（且已过时） | 目标索引 `idx_sample_list`/`idx_sample_sales_manager` 在库中均不存在；全历史无任何迁移 DROP 这两个索引；其依赖列 `sample_status` 已被迁移 100 删除 → 执行窗口（98 后、100 前）内未执行，之后又不可能执行 |
| 212 | `212_inventory_realtime_and_transaction_ledger.sql`（dev-20260924-001） | **执行过但未记账** | menu 26 已改名「即时库存」且 remark 内含 `dev-20260924-001`；menu 126 = 「收发明细」`route_name=InventoryTransactionLedger`（该 token 全历史仅此文件含）——结构已落库，账本无 212 |
| 213 | `213_work_order_supplemental_issue_dispatch.sql`（dev-20260924-002） | **执行过但未记账** | `inventory_outbound_order` 的 4 个 supplement_* 列 + `idx_outbound_supplement_ncr` 在库；`production_task` 的 `task_type`/`supplement_group_no`/`source_ncr_id`/`source_outbound_id` + `uk_task_supplement_outbound_exec` 在库（token `supplement_reason_type` 全历史仅此文件含） |

> 附：账本 `ops.schema.applied` 串内**未排序段**（`…255,256,266,267,257,258…`）表明账本历史上被人工编辑过——212/213 极可能在一次人工同步/整理中被漏写（与"执行过但未记账"一致）。

**问题二（同号多文件 25 组）**

1. 25 组里 **18 组**（41/43/82/128/129/136/145/148/154/155/156/157/167/199/253/254/255/261）实际是**"撞号→改号整改"**：后建文件被**改号重命名**到新号（有明确改号提交），**两份都执行过**、各自落在不同号上（见 §4 改号映射）。→ 账本 `NN` = **保留了该号的那一份**。
2. 余 **7 组**（52/53/63/68/219/234/240）为**未解决的同号并存**：两份文件在历史里同时存在、都被执行（库内两份效果均在），**账本号（≤255 无 per-file 指纹）无法区分先后**——列为遗留（§5）。
3. **重要修正**：010 号文档设想的"后跑者被账本判已应用而**永久跳过**"，在**当前这套库**里**没有发生**——25 组中每一份的效果都能在库内找到（无一丢失）。该风险是**潜在**的：若在**全新库**上按同号批量执行，`db-migrate.sh` 按号判在，第二份会被跳过 → 这才是我方需要防的坑。
4. 5 个号 **41/43/52/53/63 低于账本下界 66**（账本从 66 起），**根本不在账本覆盖范围内**——"账本记的那个号"对它们不适用，只能用库内效果判定。

---

## 2）判据与复跑命令（全只读）

1）账本：`SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';`
　（`ops.schema.file.<NN>` 指纹 **仅 256–281 存在 26 条**，66–255 无指纹 → 老号无法用指纹定文件）
2）库内效果：`information_schema.{COLUMNS,STATISTICS,TABLES}` + 业务表/`sys_menu`/`sys_config`/`sys_event_config`/`sys_role_menu` 实测。
3）历史文件与改号：`git log --all --diff-filter=R --name-status -- 'jjx-docs/sql/migrations/*.sql'`（改号）
4）文件内容/任务码：`git show <add-commit>:jjx-docs/sql/migrations/<name>.sql`
5）本次复跑件为 `/tmp/sp037_*`（未入库，随会话清理）；口径即上 1–4 条。

---

## 3）问题一：账本自身断号 99 / 212 / 213（详证）

账本实测（解析后 213 个号）：`…97,98,100…`（缺 99）、`…210,211,214,215…`（缺 212、213）。

### 3.1 99_optimize_sample_order_page.sql → 从未执行

- 文件内容（dev-20260911-009，提交 `449132b4`，2026-09-12 00:23）：
  `ALTER TABLE sales_order ADD INDEX idx_sample_list(order_type,deleted,sample_status,create_time), ADD INDEX idx_sample_sales_manager(order_type,deleted,sales_manager_id,create_time);`
- 库内实测 `sales_order` 现有索引：`PRIMARY,order_no,idx_customer_id,idx_order_date,idx_order_status,idx_prod_status,idx_urgent,idx_payment_status` —— **无任何 `idx_sample_*`**。
- 决定性推理：`idx_sample_sales_manager` 的列 `(order_type,deleted,sales_manager_id,create_time)` **不含** `sample_status`；而下一个迁移 `100_split_sample_order.sql`（dev-20260912-001，提交 `ae0975db`）只 `DROP COLUMN sample_status/sample_round/…`，**不删任何索引**；全历史也**没有任何迁移 DROP `idx_sample_*`**。→ 若 99 执行过，`idx_sample_sales_manager` 必然留存至今。它不存在 ⇒ **99 未执行**。
- 旁证：99 依赖列 `sample_status` 已被 100 删除（样品单迁至独立表 `sales_sample_order`）→ 99 设计已被 100 取代；即便事后补跑也会因未知列而失败。
- 对照：同批的 98（`98_add_sales_order_customer_short_name.sql`，列 `customer_short_name`）**在库**，说明 98 执行、99 被跳过。（现存备份 `…_backup_20260911-2359_before-sample-order-page.sql` 只证明"曾准备执行"，不证明已执行。）

### 3.2 212_inventory_realtime_and_transaction_ledger.sql → 执行过但未记账

- 文件内容（dev-20260924-001，提交 `729d5eb7`）：改名 menu 26（→「即时库存」）、把 menu 126 设为「收发明细」（`path=transaction` / `component=views/inventory/transaction/index.vue` / `route_name=InventoryTransactionLedger` / `icon=List`）。
- 库内实测 `sys_menu`：
  - menu 26：`menu_name=即时库存`，`remark='dev-20260924-001：原库存台账更名…'`；
  - menu 126：`menu_name=收发明细`，`route_name=InventoryTransactionLedger`，`path=transaction`，`component=views/inventory/transaction/index.vue`，`icon=List`。
- `route_name=InventoryTransactionLedger` 在全历史迁移文件中**仅** `212_…` 出现 → 效果唯一归因 212 ⇒ **执行过**；账本无 212 ⇒ **未记账**。

### 3.3 213_work_order_supplemental_issue_dispatch.sql → 执行过但未记账

- 文件内容（dev-20260924-002，提交 `c558db65`）：给 `inventory_outbound_order` 加 `supplement_reason_type/supplement_reason/supplement_ncr_id/supplement_production_quantity` + `idx_outbound_supplement_ncr`；给 `production_task` 加 `task_type/supplement_group_no/supplement_reason/source_ncr_id/source_outbound_id` + `idx_task_supplement_group` + `uk_task_supplement_outbound_exec`。
- 库内实测：上述列与索引**全部在库**（`inventory_outbound_order` 4 列 + 索引；`production_task` 对应列 + 两索引）。token `supplement_reason_type` 全历史迁移文件**仅** 213 含 → 效果唯一归因 213 ⇒ **执行过**；账本无 213 ⇒ **未记账**。
- 注：`production_task.supplement_root_flag` 是**后续**迁移追加的，不在 213 内，勿混算。

---

## 4）问题二：同号多文件 25 组对照表

### 4.0 方法：账本号 = "保留该号的文件"；后建者被"改号整改"

关键发现：历史上存在**多笔"撞号改号"提交**，把后建文件重命名到新号（`git --diff-filter=R`）：

| 改号提交 | 日期 | 改号映射（旧→新） |
|---|---|---|
| `96b491f3` | 2026-09-03 | `41_biz_requirement_event_config`→**46**、`43_menu_287_dead_initial_assign`→**47** |
| `c08298c9` | 2026-09-10 | `82_account_dept_adjust`→**84** |
| `d66c2930` | 2026-09-18 | `136_unify_event_templates`→**138** |
| `ab3e9c48` | 2026-09-22 | `128_archive_composite_icon_mapping`→**183**、`129_merge_icon_component_samples`→**184**、`145_role_menu_fix_production_outbound`→**185**、`148_display_name_normalize`→**186**、`154_sales_customer_sample_templates_bizno`→**187**、`155_iqc_batch_lineage_cleanup`→**188**、`156_iqc_batch_lineage_hardening`→**189**、`157_iqc_batch_child_material_backfill`→**190**、`167_inbound_event_template_source`→**191** |
| `2b3c17a4` | 2026-09-22 | `199_add_history_archive_ai_menu`→**200** |
| `0bb84bae` | 2026-10-09 | `253_fix_material_sequence_to_1601`→**254**、`254_product_drawing_identity`→**255**、`255_production_pick_material_allocation`→**256** |
| `34ee0771` | 2026-10-11 | `261_attachment_sha256`→**264** |

因此对"已改号"的组：**账本 `NN` = 保留该号的那一份**；改号那一份落在**新号**上（账本里对应的是**新号**，也已记录）。

### 4.1 主对照表（25 组）

> 列「账本 NN 对应」= 账本里 `NN` 实际对应的文件；「另一份」= 同号文件及其去向。库内证据 = 本次实测（§2 判据）。

| 号 | 文件①（任务码 / 去向） | 文件②（任务码 / 去向） | 库内证据 | 账本 NN 对应 |
|---|---|---|---|---|
| **41** | `41_biz_requirement_event_config`（任务1248，**→46**） | `41_ink_material_filing_template`（dev-20260902-103/104，**留41**） | `sys_event_config` 有 `biz.requirement.submitted/approved/rejected` 3 行（≈46）；41_ink 为**全注释模板**、无库内效果 | 41_ink（**空操作模板**）；改号件 46 生效。⚠41/46 均＜账本下界66 |
| **43** | `43_menu_287_dead_initial_assign`（任务1267，**→47**） | `43_purchase_report_menu`（dev-20260901-085，**留43**） | menu 287 已删；`sys_menu(324)=采购报表 route_name=PurchaseReport` 在库 | 43_purchase_report；改号件 47 生效。⚠43/47＜66 |
| **52** | `52_sys_attachment_file_type_width`（无任务码） | `52_sys_task_test_cases`（任务1320） | `sys_attachment.file_type=varchar(255)` **且** `sys_task.test_cases` 存在 | **未解决**：两份均在库；账本号不可分。⚠52＜66 |
| **53** | `53_biz_change_ledger`（dev-20260902-101） | `53_qr024_purchase_order_print_go_live`（任务1318） | `sys_menu(327)=变更记录 route_name=BizChanges` 在库 **且** `quality_template_registry(JJX-QR-024).category=data` | **未解决**：两份均在库；账本号不可分。⚠53＜66 |
| **63** | `63_event_close_source_events_phase2`（dev-20260905-018） | `63_routing_item_parent_structure`（dev-20260905-016） | `sys_event_config` 中 `sample.transferred`/`inventory.alert.processed` 的 close_source 值与 phase2 完全一致 **且** `engineering_routing_item.parent_id`+`idx_routing_item_parent` 在库 | **未解决**：两份均在库；账本号不可分。⚠63＜66 |
| **68** | `68_fix_purchase_submenu_paths`（dev-20260908-017） | `68_purchase_payment_workflow`（dev-20260908-001 等） | menu 165/172/180 path=invoice/payment/receipt、visible=0 **且** `purchase_payment.approval_status/approver_name/approval_comment` 在库 | **未解决**：两份均在库；账本号不可分（创建序：payment 09-08 09:21 早于 submenu 16:26） |
| **82** | `82_account_dept_adjust`（dev-20260910-013，**→84**） | `82_hr_position_to_dict`（dev-20260910-011，**留82**） | `hr_position` 表已删 + `sys_dict('hr_position')` 在 + `hr_employee.position varchar(64)`（≈82）；`sys_user(admin/gudy).dept_id=2`（≈84） | 82_hr_position_to_dict；改号件 84 生效 |
| **128** | `128_retire_legacy_fqc_menu`（用户09-17定，**留128**） | `128_archive_composite_icon_mapping`（dev-20260917-008，**→183**） | 264/265 现行不存在（128 先置 hidden，129 再删→效果被覆盖）；`engineering_process_icon_component` 表曾由 183 建、已被 184 `DROP`（现不存在） | 128_retire_legacy_fqc_menu；改号件 183 生效 |
| **129** | `129_rename_and_remove_legacy_menus`（用户09-17定，**留129**） | `129_merge_icon_component_samples`（dev-20260917-009，**→184**） | menu 264/265/376 已删、377=成品检验（≈129）；`engineering_process_icon_sample` 加 `parent_sample_id/component_order/work_instruction`+`idx_icon_parent_component`（≈184） | 129_rename_and_remove_legacy_menus；改号件 184 生效 |
| **136** | `136_iqc_rework_child_batch_lineage`（dev-20260918-008，**留136**） | `136_unify_event_templates`（dev-20260918-009，**→138**） | `inventory_iqc_batch` 表 + `inventory_iqc_rework_order.child_batch_id` 在库（≈136）；`purchase.received` 等标题已按统一模板改写（≈138） | 136_iqc_rework_child_batch_lineage；改号件 138 生效 |
| **145** | `145_sample_ready_title_use_bizno`（dev-20260921-002，**留145**） | `145_role_menu_fix_production_outbound`（dev-20260921-006，**→185**） | `sample.ready` 标题=`样品单【{bizNo}】{productName}/{productCode} 已制作完成`（≈145）；`sys_role_menu`(28,18)(32,18) 在库（≈185） | 145_sample_ready_title_use_bizno；改号件 185 生效 |
| **148** | `148_role_menu_fix_system_orphans`（dev-20260921-006，**留148**） | `148_display_name_normalize`（dev-20260921-007，**→186**） | `sys_role_menu`(role6: 250/251/56/299) 在库（≈148）；显示名归一残留≈0（≈186，数据侧不可强证） | 148_role_menu_fix_system_orphans；改号件 186 生效 |
| **154** | `154_sample_task_close_semantics`（dev-20260921-017，**留154**） | `154_sales_customer_sample_templates_bizno`（dev-20260921-013，**→187**） | `sample.accepted/started` close=NULL、`sample.ready` close=`sample.created`（≈154）；`sales.customer.*`/`sample.transferred` 标题已去 `{bizId}`（≈187） | 154_sample_task_close_semantics；改号件 187 生效 |
| **155** | `155_engineering_templates_use_bizno`（dev-20260921-013，**留155**） | `155_iqc_batch_lineage_cleanup`（dev-20260921-019，**→188**） | `bom.*`/`product.film.*`/`product.routing.*` 标题均 `{bizNo}`（≈155）；`inventory_iqc_batch` 批次 1–9/11/15 已清空（≈188） | 155_engineering_templates_use_bizno；改号件 188 生效 |
| **156** | `156_inventory_templates_use_bizno_inbound_outbound`（dev-20260921-013，**留156**） | `156_iqc_batch_lineage_hardening`（dev-20260921-020，**→189**） | `inventory.inbound/outbound` 标题 `{bizNo}`（≈156）；`inventory_iqc_batch` 加 `source_inbound_id/disposed_quantity`+`uk_iqc_batch_material_no`（≈189） | 156_inventory_templates_use_bizno_inbound_outbound；改号件 189 生效 |
| **157** | `157_inventory_templates_use_bizno_transfer_stocktake_master`（dev-20260921-013，**留157**） | `157_iqc_batch_child_material_backfill`（dev-20260921-020，**→190**） | `inventory.transfer/stocktake` 标题 `{bizNo}`（≈157）；子批次待回填行=0（≈190，已无可回填） | 157_inventory_templates_use_bizno_transfer_stocktake_master；改号件 190 生效 |
| **167** | `167_quarantine_dispose_permission_scope`（dev-20260921-028，**留167**） | `167_inbound_event_template_source`（dev-20260921-040，**→191**） | role 23/24 对 menu 333 授权已撤（≈167）；`inventory.inbound.confirmed` 内容含 `{sourceDesc}`、`created_from_production` 标题含 `{sourceNo}`/`{bizNo}`（≈191） | 167_quarantine_dispose_permission_scope；改号件 191 生效 |
| **199** | `199_rename_quality_menus`（dev-20260922-014，**留199**） | `199_add_history_archive_ai_menu`（dev-20260922-024，**→200**） | menu 333=来料不合格处置、378=产品不良台账（≈199）；`sys_menu(392) path=archive-ai` 在库（≈200） | 199_rename_quality_menus；改号件 200 生效 |
| **219** | `219_quality_sampling_plan_to_config`（dev-20260924-021） | `219_scrap_order_loss_amount`（dev-20260924-010） | `sys_config('quality.sampling_plan')` 在库（create_time 09-24 15:27）**且** `quality_scrap_order` 加 `loss_material/loss_labor/loss_total/loss_basis` 在库；**但 `quality_sampling_plan` 表未删（仍 8 行）** | **未解决**：两份效果均在库；账本号不可分（且见 §5 异常） |
| **234** | `234_routing_item_workflow_seq`（dev-20260929-027） | `234_standard_root_only_unique`（dev-20260930-009） | `engineering_routing_item` 加 `workflow_seq`+`uk_routing_workflow_process` **且** `production_task.first_level_flag` 注释=STANDARD 根任务口径 + `uk_exec_first` 在库 | **未解决**：两份均在库；账本号不可分 |
| **240** | `240_outbound_page_routes`（dev-20260930-037） | `240_retire_product_backup_20260809`（dev-20260930-010） | menu 247/248 parent=18、route=OutboundCreate/OutboundEdit **且** `product_backup_20260809` 表已不存在 | **未解决**：两份均在库；账本号不可分 |
| **253** | `253_engineering_drawing_categories`（dev-20261009-024，**留253**） | `253_fix_material_sequence_to_1601`（dev-20261009-032，**→254**） | menu(`engineering:drawing:view`)=工程图纸 + `sys_dict('product_file_category')` 在库（≈253）；物料序列 `material` 行当前不存在（≈254，不可强证） | 253_engineering_drawing_categories；改号件 254 |
| **254** | `254_fix_material_sequence_to_1601`（dev-20261009-032，**改号落入254**） | `254_product_drawing_identity`（dev-20261009-029，**→255**） | 254 真身见上（序列行不存在，不可强证）；`sys_attachment.drawing_no/drawing_name/file_role`+`idx_attachment_drawing` 在库（≈255） | 254_fix_material_sequence_to_1601（账本254）；改号件 255 生效 |
| **255** | `255_product_drawing_identity`（dev-20261009-029，**改号落入255**） | `255_production_pick_material_allocation`（dev-20261009-056，**→256**） | 见上（≈255 在库）；`inventory_outbound_item` 加 6 个 allocation 列 + `idx_outbound_item_source_bom`（≈256） | 255_product_drawing_identity（账本255）；改号件 256 生效 |
| **261** | `261_sample_order_source`（dev-20261010-036，**留261**） | `261_attachment_sha256`（dev-20261011-007，**→264**） | `sales_sample_order` 加 `source_type/source_no`（≈261）；`sys_attachment.sha256` 在库（≈264） | 261_sample_order_source；改号件 264 生效 |

### 4.2 分类说明

- **已改号整改（18 组）**：41/43/82/128/129/136/145/148/154/155/156/157/167/199/253/254/255/261。
  - 账本 `NN` 稳定对应"保留该号"的那份；改号件在**新号**（46/47/84/138/183–191/200/254/255/256/264）上另记——**两份都执行过**，非"永久跳过"。
  - 其中 41/43 的"保留号"＜账本下界 66，故只是文件层面归位，与账本无关。
- **未解决同号并存（7 组）**：52/53/63/68/219/234/240。
  - 历史里两份文件**同时存在**且**都被执行**（库内两份效果均可见）；从未被改号。
  - 52/53/63＜账本下界 66，本就不在账本内（手工时代）。
  - 68/219/234/240 在账本内，但 ≤255 **无 per-file 指纹** → 仅凭账本号**无法判定**记录的是哪一份，列入遗留。

---

## 5）遗留与风险（如实列出）

1. **P1｜7 组未解决同号并存的"账本无法区分"**：`52,53,63,68,219,234,240`。
   两份文件都执行过、库内两份效果均在；账本号只能表示"该号已应用"，**不能定位到具体文件**。若要"零扯皮"，需以改号提交的方式**历史性收口**（把其中一份改到空闲新号并在文件头留痕）——但**会动到共享 `jjx-docs/sql/**`，须用户拍板后另开任务**，本卡不做。
2. **P2｜219 异常**：`219_quality_sampling_plan_to_config` 的设计是"种子 config → **DROP TABLE quality_sampling_plan**"，但库内 **config 已种子（create_time 2026-09-24 15:27:51）而表仍在（8 行，行龄 09-17/09-22）**。推测：该文件执行时最后一条 `DROP TABLE` **未生效/被中断**，或其后有**缺失迁移**重建了该表。**无法凭现有只读证据定论**，需人工核对执行流水后判定（本卡不补、不改）。
3. **P3｜≤255 无 per-file 指纹**：`ops.schema.file.<NN>` 仅 256–281 存在。老号"账本号↔文件"只能靠库内结构/提交信息反推——这正是 25 组判定困难的根因。建议后续新迁移**坚持每号入指纹**（db-migrate.sh 已自动做）。
4. **P3｜账本被人工编辑过**：`ops.schema.applied` 串存在未排序段（`…255,256,266,267,257,258…`）。212/213 的漏记大概率源于此。建议**只经 `db-migrate.sh` 记账**，手工编辑必留痕。
5. **潜在坑（非本库）**：若在**全新库**上把同号两份文件一起交给 `db-migrate.sh`（按号判在），第二份会被**静默跳过** → 这才是"永久跳过"的真实触发场景；本库因最终各自落在不同号（改号）或已手工执行过，**未见效果丢失**。

---

## 6）验证口径 / 反例自检

1）复跑：`mysql … -e "SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';"` 确认断号 99/212/213（未变）。
2）复跑：§2 的 information_schema / 业务表查询，核对 §3 §4 证据。
3）复跑：`git log --all --diff-filter=R --name-status -- 'jjx-docs/sql/migrations/*.sql'` 核对 §4.0 改号映射。
4）**反例自检**：本卡**未**执行任何迁移、**未**补记账（未写 `ops.schema.applied`/`ops.schema.file.*`）、**未**恢复/覆盖任何文件、**未**改库。所有结论均为只读取证。

---

## 7）只读声明

本任务全程仅：`SELECT`/`information_schema` 只读查询 + `git log/show` 只读 + `grep`。**未**对数据库写入任何行；**未**改动 `jjx-docs/sql/**`；**未**重跑/补记任何迁移。
