# 迁移账本遗留深挖：7 组同号不可定位 / 219 异常 / 253-254 不可强证

> 任务码：dev-20261014-040 ｜ 日期：2026-10-15 ｜ 执行：OpenClaw（subagent）｜ 性质：**只读取证**（未改库、未补记账、未覆盖写、未重跑迁移）
> 卡面：`sys_task.dev-20261014-040`（来源 dev-20261014-037 第 5 节遗留）
> 打底：`jjx-docs/history/migration-number-collisions-dev-20261014-037.md`（25 组对照表 + §5 遗留，**本卡不重做 25 组**）
> 参照：`jjx-docs/history/migration-file-gap-inventory-dev-20261014-010.md`、`jjx-docs/standards/CONVENTIONS.md` §3

---

## 0）总答：真实风险 vs 审计可读性

**这三项里没有一项构成"真实功能/安全风险"**——库内效果要么齐全（7 组同号两份都跑过），要么现行代码已不依赖旧结构（219 走配置），要么影响的是个易失序号（253/254）。三项的共性问题是**审计可读性**：账本 ≤255 无 per-file 指纹 + 清库会清 `sys_oper_log`/`sys_number_sequence`，导致"账本号 ↔ 具体文件""某条数据订正是否发生"无法回看。

| 遗留项 | 功能/安全风险 | 审计可读性 | 是否需要动作 |
|---|---|---|---|
| ① 7 组同号（52/53/63/68/219/234/240） | **无**（两份效果均在库） | **中**（≤255 账本号不可分文件） | **只需留档**，无需动作 |
| ② 219 旧表未删 | **无**（现行代码读 `sys_config`） | **低-中**（执行流水缺失，清空不可归因） | 留档 + 建议人工核流水 / 另立退役卡 |
| ③ 253/254 不可强证 | **无**（一次性数据订正） | **中**（序号行易失，缺证据） | **只需留档** |

> 唯一"潜在"风险不在本库、也非这三项本身：**在全新库上把同号两份文件一起交给 `db-migrate.sh`（按号判在）会静默跳过第二份**——037 已记（§5.5），本卡仅复述、不新增处置。

---

## 1）判据与复跑命令（全只读）

1）账本：`SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';`（下界 **66**，上界 284）
2）库内效果：`information_schema.{COLUMNS,STATISTICS,TABLES}` + 业务表/`sys_menu`/`sys_config`/`sys_dict`/`sys_number_sequence` 实测
3）历史文件与提交时间：`git log --all --diff-filter=A --format='%h|%ci|%s' --name-only -- 'jjx-docs/sql/migrations/NN_*.sql'`
4）文件内容/任务码：`git show <add-commit>:jjx-docs/sql/migrations/<name>.sql`
5）表历史状态：`jjx-docs/sql/backups/` 内各期 dump（含未入库的 `jjx_erp_db_backup_20261015-0942.sql`，dump 时间 09:42:06）+ `clean-test-data-log.txt`
6）本次复跑件均为只读查询；口径即上 1–5 条。

---

## 2）遗留①：7 组未解决同号并存的逐组只读复核

**方法**：对每组两份文件，用库内**两份效果同时可见**判"都被执行过"；用 **git 增文件提交时间**（+ 文件头注释里的任务码）作先后排序辅证。账本号对 ≤255 无 per-file 指纹，故**账本号本身不能定位到具体文件**（这是 037 已定的根因，不再重复论证）。

| 号 | 文件 A（增文件提交时间 / 任务码） | 文件 B（增文件提交时间 / 任务码） | 库内**两份效果**实测 | 作者序（先→后） |
|---|---|---|---|---|
| **52** | `52_sys_attachment_file_type_width.sql`（2026-09-03 19:16:54 / 无任务码，头注"加宽 sys_attachment.file_type"） | `52_sys_task_test_cases.sql`（2026-09-03 18:36:53 / 任务1320） | `sys_attachment.file_type = varchar(255)` ✅ **且** `sys_task.test_cases varchar(255)` 存在 ✅ | B → A |
| **53** | `53_biz_change_ledger.sql`（2026-09-03 18:50:49 / dev-20260902-101） | `53_qr024_purchase_order_print_go_live.sql`（2026-09-03 20:19:44 / 任务1318） | `sys_menu(327)`=变更记录 `route_name=BizChanges` ✅ **且** `quality_template_registry(JJX-QR-024).category=data` ✅ | A → B |
| **63** | `63_event_close_source_events_phase2.sql`（2026-09-05 18:29:14 / 任务1472 二期） | `63_routing_item_parent_structure.sql`（2026-09-05 18:34:20 / dev-20260905-016） | `sys_event_config`：`sample.transferred.close_source_events='sample.transfer.remind,sample.confirmed'`、`inventory.alert.processed.close_source_events='stock.shortage,stock.low,stock.over'` **与 63_phase2 文件逐值一致** ✅ **且** `engineering_routing_item.parent_id` + `idx_routing_item_parent` 存在 ✅ | A → B |
| **68** | `68_purchase_payment_workflow.sql`（2026-09-08 09:21:04 / dev-20260908-001 等） | `68_fix_purchase_submenu_paths.sql`（2026-09-08 16:26:24 / dev-20260908-017） | `purchase_payment` 有 `approval_status/approver_name/approval_comment` ✅ **且** menu 165/172/180 `path=invoice/payment/receipt`、`visible=0` ✅ | A → B |
| **219** | `219_quality_sampling_plan_to_config.sql`（2026-09-24 15:29:00 / dev-20260924-021） | `219_scrap_order_loss_amount.sql`（2026-09-24 16:50:15 / dev-20260924-010） | `sys_config('quality.sampling_plan')` 在库 ✅ **且** `quality_scrap_order.loss_material/loss_labor/loss_total/loss_basis` 在库 ✅（另有旧表异常，见 §3） | A → B |
| **234** | `234_routing_item_workflow_seq.sql`（2026-09-29 19:28:34 / dev-20260929-027） | `234_standard_root_only_unique.sql`（2026-09-30 09:03:25 / dev-20260930-009） | `engineering_routing_item.workflow_seq` + `uk_routing_workflow_process` 在库 ✅ **且** `production_task.first_level_flag`（注释含 STANDARD 根任务口径）+ `uk_exec_first` 在库 ✅ | A → B |
| **240** | `240_outbound_page_routes.sql`（2026-09-30 16:14:17 / dev-20260930-037） | `240_retire_product_backup_20260809.sql`（2026-09-30 18:04:24 / dev-20260930-010） | menu 247/248 `parent_id=18`、`route_name=OutboundCreate/OutboundEdit` ✅ **且** `product_backup_20260809` 表已不存在 ✅ | A → B |

**逐组结论**：

- **52/53/63 低于账本下界 66**：它们**根本不在账本覆盖范围内**（账本自 66 起），所谓"账本记的是哪一份"对它们**不适用**——这三组是纯**早期手工时代的文件名复用**，只能用库内效果判定。**不应表述为"账本内同号"**（修正 037/010 的措辞）。
- **68/219/234/240 在账本内**，两份效果均在库；因 ≤255 无 `ops.schema.file.NN` 指纹，账本 `NN` 只能表示"该号已应用"，**不能定位到具体文件**。作者序（先→后）显示每组里"先建者"多与账本落号一致的概率更高，但**这是推断、非证明**。

**判定**：7 组**功能上无风险**（两份效果都落库，无一丢失）；**仅"审计不可分"**。
**是否需动作**：**不需要**（只需留档）。理由：① 当前库功能完整；② 这些文件按 CONVENTIONS §3「历史缺失文件不回填」已不在仓内工作树（实测 7 组 14 个文件在 `migrations/`、`applied/` 均不存在，只在 git 历史里）；③ 若追求"零扯皮"把其中一份改到空闲号，需动共享 `jjx-docs/sql/**`，**须用户拍板后另开任务**，本卡不做。

---

## 3）遗留②：219 异常（`quality_sampling_plan` 旧表未删）复核

### 3.1 现状实测

| 对象 | 实测 | 证据 |
|---|---|---|
| `sys_config('quality.sampling_plan')` | **在库**；`create_time=2026-09-24 15:27:51`；config_value 1993 字符；为合法 JSON，含 planId **2–9（8 项）** IQC/OQC 方案 | `SELECT config_key,create_time,LENGTH(config_value)` |
| `quality_sampling_plan` 表 | **仍在库**；结构含 `del_flag`、`idx_sampling_match`；`AUTO_INCREMENT=13`；**当前 0 行**；`CREATE_TIME=2026-10-10 09:55:07`；`UPDATE_TIME=2026-10-15 09:46:02` | `information_schema.TABLES` + `SHOW CREATE TABLE` |
| 8 条遗留行（09-24 前） | 备份实测 `plan_id 2–9`，`create_time` **2026-09-17 11:53:27（Hermes）/ 2026-09-22 10:06:49（Codex）** | `jjx_erp_db_backup_20261015-0942.sql` 等 dump |

### 3.2 关键时空证据

1）**8 条遗留行跨多期备份持续存在**：`20261012-2008`、`20261013-2343`、`20261014-2248`、`20261015-0942` 四份 dump 里，该表都带同一组 `plan_id 2–9`（create_time 09-17/09-22）。→ 迁移 219 于 **2026-09-24 15:29** 执行后，**至少到 2026-10-15 09:42，这 8 行仍原样在表内**，即 219 的 `DROP TABLE IF EXISTS quality_sampling_plan` **从未把表删掉**。
2）**无任何迁移/脚本重建该表**：全历史 `git log --all` 对 `*.sql`/`*.sh` 检索，**除备份 dump 外无 `CREATE TABLE quality_sampling_plan`**；仓内也没有引用该表的迁移（仅 219 一处）。→ **不是"后续重建"**。
3）**结构一致性**：live 表 DDL 与 `20261015-0942` 备份 DDL **列结构完全一致**（表仍在、结构未变）→ 它就是 219 之前的那张遗留表，非新建。
4）**清空时点**：表当前 0 行的最后改动时间为 **2026-10-15 09:46:02**，紧随当日 09:45 的一次测试数据清理（`clean-test-data-log.txt`）。但 `00_clean_test_data.sql` **并未把该表列入 TRUNCATE**（该表反而在清库脚本的**保留白名单** `RETAINED_TABLES` 里，仅用于覆盖率校验）——故"是谁清空的"**不能凭现有只读证据唯一归因**（清库会清空 `sys_oper_log`，库里留不下痕迹）。

### 3.3 结论与建议

- **结论：DROP 未生效**（旧表从未被 219 删除），**不是"后续迁移/文件重建"**。
- **清空 ≠ 迁移完整执行**：当前 0 行是后来的数据清理/运维动作的结果，**不能**作为"219 已完整执行"的证据；反过来，表仍在本身就是"219 的 DROP 没跑到/被中断"的证据。
- **功能影响：无**。现行代码（`QualitySamplingPlanServiceImpl`/`QualityOqcServiceImpl`/实体注释）明确"dev-20260924-021 起不再查表，改读 `sys_config.quality.sampling_plan`"。
- **建议**（本卡不做）：① 人工核对当时的执行流水/会话日志（库内 `sys_oper_log` 已被清）判定 DROP 为何未生效；② 若要正式退役该空遗留表，**另立卡**走新迁移 `DROP TABLE`（需 guard 备份 + 用户拍板），**不得**在下游按"表已空"直接认定迁移完成。

---

## 4）遗留③：253/254 不可强证复核

### 4.1 现状实测

| 对象 | 实测 | 说明 |
|---|---|---|
| `sys_number_sequence` 表 | **当前 0 行**；`SELECT ... WHERE sequence_key='material'` 返回 **0 行** | 关键：**material 序列行根本不存在** |
| 近期备份里的 `sys_number_sequence` | 仅含 `inquiry / quotation / sales_order / sample_order`（period_key 形如 20261012/20261014），**无 material** | 跨 `20261012-2008`、`20261015-0942` 一致 |
| 该表被清库重置 | `00_clean_test_data.sql` 第 429 行 `TRUNCATE sys_number_sequence;`（"单号流水清空重置"） | → material 序列（若曾存在）被**周期性清空** |
| 253 效果（图纸类别） | menu 411=`工程图纸`（`perms=engineering:drawing:view`）✅ **且** `sys_dict('product_file_category')` + **19** 条 dict_item ✅ | **可强证** |
| 254 对应效果（material 序列=1601） | 无宿主持久物可查 | **不可强证** |

### 4.2 分析

- 253/254 组纠结的是 `253_fix_material_sequence_to_1601.sql`（dev-20261009-032，后经 `0bb84bae` **改号落入 254**）是否执行过。该文件唯一效果是 `UPDATE sys_number_sequence SET current_value=1601 WHERE sequence_key='material'`——**一次性数据订正，无持久结构变化**。
- 承载它的 `material` 序列行：**现在不存在**，且从近期备份看该表**从来没有 material 行**；同时该表被清库脚本 **TRUNCATE 重置**（易失）。→ **无法回看该订正是否发生**。
- **旁证（弱、不足以强证）**：`inventory_material` 现存 **1611** 行、最大码 **RM001612**（> 1601），与"序列接续 1601 后继续递增"相符；但该现象**不能唯一归因** 254（也可能来自 dev-20261009-032 的代码路径或测试数据）。
- 参照对比：**253 分组的"图纸类别"效果可强证**（menu 411 + `product_file_category` 字典 19 项在库）；**253 分组里的"material 序列"这一半（即改号后的 254）不可强证**。

### 4.3 结论与缺证

- **结论：254（material 序列）不可强证**；缺的是"能回看的痕"。
- **缺哪些证据**：① `sys_number_sequence` 的 `material` 行（已被清库清空，且近期备份亦无）；② ≤255 的 per-file 账本指纹（`ops.schema.file.NN` 仅 256–281 存在）；③ `sys_oper_log` 执行流水（清库会清空，库里无痕）。
- **功能影响：无**（一次性数据订正；即便未做，也是"新增物料编码从 1 开始"的旧现象，与库结构/安全无关）。

---

## 5）遗留与风险（如实列出）

1. **不需动作的（留档即可）**：7 组同号（52/53/63/68/219/234/240）——两份效果均在库，功能无损，仅账本号不可定位文件；253/254——一次性数据订正，序号行易失，不可强证。均**无功能/安全风险**，属**审计可读性**问题。
2. **建议跟进（不在本卡执行）**：219 旧表未删——虽无功能风险，但"清库白名单里长期养着一张僵尸表"值得收口：由人工核执行流水后，**另立卡**以新迁移正式 `DROP` 退役（需 guard 备份 + 用户拍板）。
3. **根因未变（037 已记，本卡复述）**：≤255 无 `ops.schema.file.NN` 指纹 + 清库清 `sys_oper_log`，是"同号不可分 / 订正不可证"的共同根因。**建议**：新迁移坚持每号入指纹（`db-migrate.sh` 已自动做）；清库若需保留审计，预先导出 `sys_oper_log`/`sys_number_sequence`。
4. **潜在坑（非本库）**：全新库上同号两份文件一起交给 `db-migrate.sh`，第二份会被**静默跳过**（037 §5.5）。
5. **无法定论的点（如实列）**：① 219 旧表"当前 0 行"的确切清空动作与执行者——现有只读证据不足以唯一归因（不在清库 TRUNCATE 清单，却与当日清库同刻）；② 7 组中各自"账本号对应哪一份文件"——只有作者序推断，无 ≤255 指纹可证。

---

## 6）验证口径 / 反例自检

1）复跑账本：`SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';` 确认为 66–284、无 99/212/213（未变）。
2）复跑 §2 的 `information_schema`/业务表/`sys_menu`/`sys_dict`/`sys_number_sequence` 查询，核对 7 组"两份效果"。
3）复跑 §3：`SHOW CREATE TABLE quality_sampling_plan` + `SELECT create_time FROM sys_config WHERE config_key='quality.sampling_plan'` + 备份 dump 的 `INSERT INTO quality_sampling_plan`。
4）复跑 §4：`SELECT COUNT(*) FROM sys_number_sequence WHERE sequence_key='material'`（应为 0）。
5）复跑时间序：`git log --all --diff-filter=A --format='%h|%ci|%s' --name-only -- 'jjx-docs/sql/migrations/NN_*.sql'`（NN∈{52,53,63,68,219,234,240}）。
6）**反例自检**：本卡**未**执行任何迁移、**未**补记账（未写 `ops.schema.applied`/`ops.schema.file.*`）、**未**恢复/覆盖/改号任何文件、**未** `DROP`/`TRUNCATE`/`DELETE` 任何表或行。结论均为只读取证。

---

## 7）只读声明

本任务全程仅：`SELECT`/`information_schema` 只读查询 + `git log/show` 只读 + `grep`/`sed` 只读文件 + 只读体检（`db-clean-test-data.sh` 无参数、不写库）。**未**对数据库写入任何业务行；**未**改动 `jjx-docs/sql/**`；**未**重跑/补记任何迁移。（唯一落盘：本分析文档 + `history/INDEX.md` 登记行 + 卡面 `sys_task.dev-20261014-040` 的 remark 摘要。）
