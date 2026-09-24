# 脚本命令手册（唯一入口）

状态：✅已实施
任务：dev-20260914-005
被取代 / 取代：无
什么情况看这篇：要跑仓库里的脚本（迁移/备份/导出/清理/自检）时，先看这一页
最后复核：2026-09-14

> 规则：**新增或修改 `scripts/` 下的脚本，必须同步更新这一页。**（规范见 `standards/CONVENTIONS.md`）
> 危险等级：🟢 只读（随时可跑） / 🟡 写文件或本地配置（可回退） / 🔴 改数据库（必须走"备份 → 人工确认 → 执行"）

## 0. 常用速查

| 我要干嘛 | 命令 | 等级 |
|---|---|---|
| 开工先自检 | `bash scripts/agent-preflight.sh` | 🟢 |
| 看库迁移版本差多少 | `bash scripts/db-migrate.sh --status` | 🟢 |
| 看初始化快照还新不新 | `bash scripts/db-export-init-subset.sh --verify` | 🟢 |
| 清理前先看会删什么 | `bash scripts/db-clean-test-data.sh` | 🟢 |
| 装 git 闸门（每 clone 一次） | `bash scripts/install-hooks.sh` | 🟡 |
| 重出初始化数据子集 | `bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN` | 🟡 |
| 立刻拍一份全库快照 | `bash scripts/db-backup.sh --tag before-xxx --task dev-YYYYMMDD-NNN` | 🟡 |
| 看全库备份会落哪/会清谁 | `bash scripts/db-backup.sh --dry-run` | 🟢 |
| 执行一个迁移 | `bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN` | 🔴 |
| 清理测试数据 | `bash scripts/db-clean-test-data.sh --execute` | 🔴 |
| 库存三本账对账 | `bash scripts/check-stock-summary.sh` | 🟢 |
| 入库单/检验批+数量守恒巡检 | `bash scripts/check-inbound-lot-integrity.sh` | 🟢 |
| 业务单号规则巡检 | `bash scripts/check-doc-no.sh` | 🟢 |
| 改完代码/文档自查 | `cd jjx-web && npm run validate` | 🟢 |

---

## 1. scripts/agent-preflight.sh —— 开工自检

- 用途：动手前一条命令看全局——git 闸门是否装、库迁移版本是否与代码一致、只读账号是否可用、文档门禁过不过、工作区有没有别人的 WIP。
- 危险等级：🟢 只读（不写库、不写文件；只调用 `db-migrate.sh --status` 与 `node scripts/check-docs.mjs`）。
- 前置：在仓库内执行；数据库可连（连不上会标 ✘，但不会改任何东西）。
- 命令：`bash scripts/agent-preflight.sh`
- 输出怎么读：5 段逐项 ✓ / ⚠ / ✘ —— **✘＝阻塞项（挡开工）**，**⚠＝提醒项（不挡开工）**；结尾会写清是"自检通过"还是"未通过"，通不过就先处理标 ✘ 的项。
- 退出码：`0`=无阻塞项（可能带 ⚠ 提醒）；`1`=有阻塞项 ✘。
- 两类常见 ⚠：① 工作区有未提交改动（只是提醒你提交时别混别人的 WIP）② `jjx_ro` 不可用（只影响 commit-msg 的任务码真实性校验）。
- 备注（2026-09-14 修复，任务 dev-20260914-012）：曾误报「库中未记录已应用版本」——原因是抓的字段名和 `db-migrate.sh --status` 实际输出（`已应用: 66,67,…`）对不上；现已按"取集合最大值 vs 目录最大号"比较，"首次登记前"才提示未记录，且只算 ⚠。

## 2. scripts/db-migrate.sh —— 迁移唯一执行通道

- 用途：执行迁移的**唯一**入口（内部固定顺序：备份 → 执行 → 写 `sys_config.ops.schema.*`；备份失败或执行失败都不写版本）。
- 危险等级：🔴 改数据库（执行迁移）／🟡 只写版本记录（`--record`）／🟢 只读（`--status`）。
- 前置：迁移文件放在 `jjx-docs/sql/migrations/`（`NN_<描述>.sql`）；`JJX_BACKUP_DIR` 可写（2026-09-23 起默认**仓库内** `jjx-docs/sql/backups/`，全库快照默认排除 `hr_employee`）；要动库必须带真实任务码。备份按风险分级：高风险→全库快照，低风险→只备本次涉及的表（文件头 `-- risk: high|low` 可覆盖），任何情况都不许零备份；全库快照每日只留最新一份、超 `KEEP_DAYS`(默认14天)自动清理。
- 命令：
  - 查版本：`bash scripts/db-migrate.sh --status`
  - 执行：`bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN`
  - 接管已有库只登记版本：`bash scripts/db-migrate.sh --record <NN> --yes`
- 输出怎么读：三段 1/3 备份（给字节数/表数/md5）→ 2/3 执行 → 3/3 记录版本；中途报错即中止且不记版本。
- 退出码：0=成功，非 0=中止（未执行或未记版本）。
- 注意：**不要直接 `mysql < 文件`** 绕过本脚本（等于没有备份、也没有版本记录）。

## 3. scripts/db-export-init-subset.sh —— 初始化数据子集（滚动重出）

- 用途：按清单导出"进入初始化脚本的正式数据子集"快照（给新环境开账用，**不是全库备份**）；也可只读校验最新快照是否还与新库一致。
- 危险等级：🟡 只读库 + 写仓库文件（产出新快照，可回退）／`--verify` 为 🟢 纯只读。
- 前置：清单 `jjx-docs/sql/init/init-subset-tables.txt` 存在且其中的表都在库里（有已下线的表会直接中止）；`--task` 必须是真实任务码。
- 命令：
  - 重出：`bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN`
  - 只体检：加 `--dry-run`（打印逐表行数，不落文件）
  - 只读校验：`bash scripts/db-export-init-subset.sh --verify`（✓最新 / ⚠行数有差异 / ✘缺表或表集合不一致）
- 输出怎么读：逐表行数 + 合计 → 文件路径/字节数/md5/表数/总行数 → 列出旧份（**不自动删**，删旧份要人工确认后单独做）。
- 退出码：0=成功或快照一致，1=失败或有差异。
- 注意：产出会进 git（属 CONVENTIONS §2 例外备案），git 里**只留最新一份**。

## 4. scripts/db-clean-test-data.sh —— 清理测试数据入口

- 用途：`jjx-docs/sql/00_clean_test_data.sql`（整表 TRUNCATE + 1 条 DELETE，表清单以脚本实际解析为准）的**唯一**入口。
- 危险等级：🟢 无参数=只读体检（不写库）／🔴 `--execute` 真清理（固定顺序：体检 → 全库备份 → 人工确认 → 执行）。
- 前置：`--execute` 必须**在终端手工执行**（agent/管道一律拒绝）；确认方式=手工输入库名 `jjx_erp_db`；`JJX_BACKUP_DIR` 可写。
- 命令：
  - 体检：`bash scripts/db-clean-test-data.sh`
  - 真清：`bash scripts/db-clean-test-data.sh --execute`
- 输出怎么读：① TRUNCATE 组（有数据的表逐条列出行数 + 合计）② DELETE 组（将删/保留条数）③ 与初始化清单交叉（非 0 要警惕）④ **覆盖率校验**（库表是否都有归宿：清理清单 ∪ 保留白名单；有未登记的表即**阻断清理**，提示是加进清理段还是补进保留清单/`RETAINED_TABLES`）→ 顺带自动跑一次快照校验 → 执行后打印备份路径/md5，并在 `$JJX_BACKUP_DIR/clean-test-data-log.txt` 留痕（脚本会清空 `sys_oper_log`，库里留不下痕迹）。
- 退出码：0=体检通过或清理成功，1=拒绝执行/中止/失败。

## 5. scripts/clean-archive-ocr-data.sh —— 清档案 OCR 测试数据（并行会话产出）

- 用途：只删历史档案 OCR 的测试档案及其草稿产物（范围很窄，1~2 条）。
- 危险等级：🔴 改数据库。**注意：本脚本用 `--yes` 放行，与上面"🔴 必须手输库名"的统一口径不一致**（待用户决定是否统一）。
- 前置：默认只预览；`--yes` 才备份并删除。
- 命令：`bash scripts/clean-archive-ocr-data.sh`（预览）／加 `--yes`（真删）。
- 退出码：0=成功，非 0=中止。

## 6. scripts/db-backup.sh —— 独立全库备份（2026-09-14 新增，任务 dev-20260914-027）

- 用途：不触发任何库内变更，只想**立刻拿一份全库快照**时用（补上此前只能手敲 `mysqldump` = 绕过规范入口的缺口）。
- 与其它脚本的边界：迁移/清测试数据各自**内部自带**全库备份（本脚本不替代它们）；`db-export-init-subset.sh` 出的是初始化交付物、**不是备份**。
- 危险等级：🟡 只读数据库 + 写备份文件（可回退）；`--dry-run` 为 🟢 纯预览（不落盘）。
- 前置：`mysqldump` 可用；数据库可达；`JJX_BACKUP_DIR`（2026-09-23 起默认**仓库内** `jjx-docs/sql/backups/`）可写。
- 默认排除：导出**默认带 `--ignore-table=jjx_erp_db.hr_employee`**（人事档案表含身份证密文/住址/电话；2026-09-23 用户口径：只排除它，其余都能入库）；要包含用 `JJX_BACKUP_EXCLUDE_DEFAULT=` 显式覆盖。
- 命令：
  - 例行：`bash scripts/db-backup.sh`
  - 带来源/任务码：`bash scripts/db-backup.sh --tag before-xxx --task dev-YYYYMMDD-NNN`
  - 只看不写：`bash scripts/db-backup.sh --dry-run`
  - 常用开关：`--reason <文案>`、`--out-dir <dir>`、`--keep-days <N>`（默认 14）、`--no-clean`、`--exclude-table <表名>`（可重复）
- `--exclude-table`（2026-09-14 加，任务 dev-20260914-028）：导出时跳过该表（内部转 `mysqldump --ignore-table`），如 `--exclude-table hr_employee`；**排除后的产物不是可完整恢复的全库备份**，只当剪裁快照用（脚本会打 ⚠ 提醒）。
- 输出怎么读：报告库/目录/文件名/原因/任务码/清理策略 → 备份后给 **字节数 / 表数 / md5** + 恢复命令 → 再走过期清理段。
- 产物：`jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql`，同名冲突自动加 `-2/-3`，**不覆盖**；文件头 1~3 行=备份人/原因/任务码，并追加一行到 `<备份目录>/db-backup-log.txt` 留痕。
- 清理口径：只删 `<备份目录>` 下超过 `--keep-days`（默认 14 天）的 `jjx_erp_db_backup_*.sql`（= 全库备份）；guard 表级备份、`.tar.gz` 等**只提示不删**。
- 退出码：0=备份成功；1=前置不满足 / 备份失败 / 产物异常（<1KB 或 0 张表）。
- 注意：备份默认落**仓库内** `jjx-docs/sql/backups/` 并默认排除 `hr_employee`，**随任务提交推送**（CONVENTIONS §2，2026-09-23 恢复口径）；巨型历史 dump 仍按 §2 保留策略（每日只留最新 + 14 天）清理。

## 7. scripts/install-hooks.sh —— 安装 git 闸门

- 用途：把本仓库的 `pre-commit` + `commit-msg` 钩子挂上（`core.hooksPath=scripts/hooks`）。
- 危险等级：🟡 只写本地 git 配置，不碰数据库；可卸载。
- 前置：在仓库内执行；`scripts/hooks/*` 存在。
- 命令：`bash scripts/install-hooks.sh`；卸载：`git config --unset core.hooksPath`
- 输出怎么读：确认 `core.hooksPath = scripts/hooks` + 已启用钩子清单。
- 退出码：0=成功。

## 8. scripts/check-stock-summary.sh —— 库存三本账对账（2026-09-23 扩查，任务 dev-20260923-017）

- 干什么：只读对账，抓「库存三本账」不一致 —— ①汇总表 `inventory_stock` ≠ 批次明细合计；②有明细无汇总；③有汇总无明细；
  **④批次结存 `inventory_stock_item.quantity` ≠ 流水派生结存（`inventory_transaction` 按批次求和）；⑤有流水、无批次行（孤儿流水）**。
- 危险等级：🟢 只读（只跑 SELECT，不改库、不写文件）。
- 前置：mysql 可连（连不上只提示不阻塞）；库不可达时退出码 0。
- 命令：
  ```bash
  bash scripts/check-stock-summary.sh            # 咨询模式：只报告，永远 exit 0
  bash scripts/check-stock-summary.sh --strict   # 有不一致则 exit 1（已接进 npm run validate）
  ```
- 输出怎么读：看 5 个计数是否全为 0；第 ④ 类不一致 = 有代码绕过唯一入口 `InventoryStockMutationService.applyDelta`（或只改余额没写流水）；
  第 ⑤ 类 = 批次行被删/未建但流水已写。修复口径：按批次明细重算汇总，并补齐/冲销流水（参见 `CONVENTIONS` 库存口径铁律）。
- 退出码：0=一致或咨询模式；1=`--strict` 且有不一致。

## 9. scripts/check-inbound-lot-integrity.sh —— 入库单/检验批 + 数量守恒巡检（九查）

- 干什么（只读巡检，两类共八查）：
  **A. 单据/批次谱系三查**（任务 dev-20260923-010）：① 生产来源入库明细 `lot_id` 覆盖率；② 同一 lot 被多张未取消单据重复计账（明细合计 ≤ 批合格量）；③ 已过账明细 = 入库侧流水（按 `inventory_item_id + batch_no`）。
  **B. 数量守恒六查**（④~⑧ 为任务 dev-20260923-023；⑨ 为 dev-20260923-032，2026-09-23 新增）：④ 判定数量守恒（`pass+fail=inspected ≤ lot_quantity`）；⑤ 不良台账守恒（批 `fail` = Σ NCR 不良；未作废处置量 ≤ NCR 不良量）；⑥ 有效批合格量 ≤ 可判上限（批量 − 该批自身已报废未回收 − 让步未确认，与判定护栏 dev-20260923-021 同口径）；⑦ 工单完工 = 有效批合格累计（防"复检换代不重算"复发）；⑧ `stored_quantity ≤ pass_quantity`；**⑨ 有效 FQC 批（`pass>0` 且无后继版本）必须能查到挂在其 `lot_id` 上、未取消(`order_status<>9`)的生产入库明细**（兜住"该出的单没出"——含完工入库单 FI 序号定长导致静默不出单，看板 2250）。
- 危险等级：🟢 只读（只跑 SELECT）。
- 前置：mysql 可连（连不上只提示不阻塞）；可选 `JJX_LOTID_CHECK_SINCE=YYYY-MM-DD` 只巡检该时间后的入库单（存量基线用）。
- 命令：
  ```bash
  bash scripts/check-inbound-lot-integrity.sh            # 咨询模式：只报告，永远 exit 0
  bash scripts/check-inbound-lot-integrity.sh --strict   # 有不一致则 exit 1（已接进 npm run validate）
  ```
- 输出怎么读：九个计数必须全 0；任一 > 0 会列出明细行。
  ⑥ 的口径说明：按「有效批自身」聚合，不按整条批链 —— 本系统是"整批重判(差额)"模型，每个新版本都会重新声明整批不良，链级聚合会重复计入（实测同一物理 2 件在两次复检里各记一次）。
- 退出码：0=一致或咨询模式；1=`--strict` 且有不一致。

## 10. scripts/check-doc-no.sh —— 业务单号规则巡检（2026-09-23 接进门禁，任务 dev-20260923-030）

- 干什么（只读巡检，三项）：
  ① **规则键缺失**：`sys_config` 里 20 个业务域的 `biz_no_rule.<bizType>` 必须各恰好 1 条启用（缺失即列出）；
  ② **容量预警**：`sys_number_sequence` 的当前值 ≥ 该域配置位数的 80%（如 3 位 → ≥ 800）时列出，提示进位/扩容；
  ③ **时间戳式编号回归**：`jjx-server` 的 quality/inventory 域里出现 `yyyyMMddHHmmssSSS` 或 `CAPA.*currentTimeMillis` 即失败（单号必须走 `sys_number_sequence`）。
- 危险等级：🟢 只读（只跑 SELECT + grep）。
- 前置：mysql 可连、`rg`（ripgrep）存在（缺失会静默跳过第 ③ 项）；连不上库时按脚本退出码判定。
- 命令：
  ```bash
  bash scripts/check-doc-no.sh            # 直接当门禁：有不一致即 exit 1（无 --strict 参数）
  ```
- 输出怎么读：三项全部通过会打印 `Document number rule check passed.`；① 会逐行 `MISSING biz_no_rule.xxx`；② 会打印 `sequence_key/period_key/current_value/configured_digits` 表；③ 会打印命中的文件行。
- 退出码：0=通过；1=任一项不通过。
- ⚠️ 与单号方案（`design/doc-no-rules-dev-20260922-023.md`）联动：新增/改名业务域后，脚本里的 `required` 列表与 `sys_config` 必须同步，否则误报。

## 10b. scripts/task-register.sh —— 开发任务登记唯一入口（2026-09-24 立，任务 dev-20260924-032 同批）

- **用途**：登记 `dev-YYYYMMDD-NNN` 任务到 `sys_task`。**禁止再手写取号 SQL**（并行会话各自 `MAX+1` 必然撞号，且手写 SQL 出过两类事故：`LPAD(@base+n,3,'0')` 被当小数产出畸形码 `dev-YYYYMMDD-14.`；`INSERT…SELECT…FROM sys_task` 少聚合 → 插多行 → 整条回滚）。
- **危险等级**：🔴 改数据库（INSERT `sys_task`）+ 🟡 写备份文件/索引
- **用法**：`bash scripts/task-register.sh --title "标题" [--priority P2] [--desc-file <file>] [--by dahuang] [--dry-run]`
- **内部保证**：① 改库前 sys_task 表级 guard 备份（落 `jjx-docs/sql/backups/`）② 取号+插入用**单条聚合 SQL**（`CAST(... AS UNSIGNED)` 再 `LPAD`）③ 撞唯一约束**自动重试**（≤3 次）④ 登记后**回查** `task_code` 格式（`^dev-YYYYMMDD-NNN$`）与当日最大号，不符即报错 ⑤ 拿到真码后才写 `backup-index.tsv`
- **退出码**：0=成功（stdout 打印 `task_code`）；1=参数/前置/登记失败
- **注意**：撞号报错**不一定是别人抢先**——先看 SQL 是否插了多行；并发下以脚本回查结果为准（唯一约束是最后一道保险）

## 11. 自动跑（不用手敲）

| 钩子 | 什么时候跑 | 拦什么 |
|---|---|---|
| `scripts/hooks/pre-commit` | 每次 `git commit` | `jjx-docs/sql/`（除 backups/）与 `jjx-docs/standards/` 下的删除/移动；`status-magic-baseline.json` 放大 |
| `scripts/hooks/commit-msg` | 每次 `git commit` | 提交信息必须有任务码 `dev-YYYYMMDD-NNN`，且该码真实存在于 `sys_task`（用只读账号 `jjx_ro` 校验；库不可达时只提醒不阻塞） |

单次跳过：`git commit --no-verify`（确认后果再用）。

## 12. npm 门禁（在 `jjx-web/` 下跑）

| 命令 | 查什么 |
|---|---|
| `npm run check:status-enums` | 状态魔法值（必须用具名枚举，基线只许缩小） |
| `npm run check:docs` | 文档规则（`history/`：登记 INDEX、BOM、命名；`modules/`：不带日期 + BOM） |
| `npm run check:collation:strict` | 全库字符串列 collation 统一（防跨表 JOIN 报 1267） |
| `npm run check:stock:strict` | 库存三本账对账（= `scripts/check-stock-summary.sh --strict`，含流水派生结存校验） |
| `npm run check:lot:strict` | 入库单/检验批 + 数量守恒巡检（= `scripts/check-inbound-lot-integrity.sh --strict`，九查） |
| `npm run check:doc-no` | 业务单号规则巡检（= `scripts/check-doc-no.sh`：规则键缺失 / 当天用量达 80% 容量 / 时间戳式编号回归） |
| `npm run validate` | 上面六条 + `vue-tsc --noEmit`（提交前自查跑这个） |
