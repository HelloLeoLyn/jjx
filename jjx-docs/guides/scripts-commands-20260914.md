# 脚本命令手册（唯一入口）

状态：✅已实施
任务：dev-20260914-005
被取代 / 取代：无
什么情况看这篇：要跑仓库里的脚本（迁移/备份/导出/清理/自检）时，先看这一页
最后复核：2026-09-14
（2026-10-07 复核：备份口径同步为 CONVENTIONS §2 2026-09-28 版——脚本不再自动备份，改为校验手工备份存在；见 §2/§4/§6）

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
| 执行一个迁移 | `bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN --backup <备份.sql>` | 🔴 |
| 批量补齐待执行迁移 | `bash scripts/db-migrate.sh --all --yes --task dev-YYYYMMDD-NNN --backup <备份.sql>` | 🔴 |
| 补记账/归档（不重跑） | `bash scripts/db-migrate.sh --record <NN> --yes --task dev-YYYYMMDD-NNN` | 🟡 |
| 迁移工具回归测试 | `python3 scripts/test-db-migrate.py` | 🟢 |
| 清理测试数据 | `bash scripts/db-clean-test-data.sh --execute` | 🔴 |
| 库存三本账对账 | `bash scripts/check-stock-summary.sh` | 🟢 |
| 入库单/检验批+数量守恒巡检 | `bash scripts/check-inbound-lot-integrity.sh` | 🟢 |
| IQC/质量处置跨表对账 | `bash scripts/check-quality-ledger.sh` | 🟢 |
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

## 2. scripts/db-migrate.sh —— 迁移唯一执行通道（预览 → 备份校验 → 逐条执行/记账 → 同名归档）

- 用途：迁移的**唯一**入口。默认只预览；执行时按已应用集合补齐待办，**逐条执行、记账回查成功后把文件移入 `jjx-docs/sql/migrations/applied/`**。
- 危险等级：🟢 默认/`--status`（只读）／🔴 `--all`/单文件/`--retry`（执行 SQL）／🟡 `--record`（补记账+归档）。
- 前置：迁移文件放 `jjx-docs/sql/migrations/`（`NN_<描述>.sql`，含 `applied/`）；**已有手工全库备份**（默认仓库内 `jjx-docs/sql/backups/`，或 `--backup`/`JJX_MIGRATE_BACKUP` 指定）——2026-09-28 起脚本**不再自动备份**，只校验手工备份存在才放行；备份必须排除 `hr_employee`。写操作必须带真实任务码；需要 `bash`/`python3`/`mysql`/`flock`。
- 命令：
  - 查待执行/待归档：`bash scripts/db-migrate.sh --status`
  - 批量执行待办：`bash scripts/db-migrate.sh --all --yes --task dev-YYYYMMDD-NNN --backup <备份.sql>`
  - 执行单个：`bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-... --backup <备份.sql>`
  - 人工核实已生效后补记账/归档（不重跑 SQL）：`bash scripts/db-migrate.sh --record <NN> --yes --task dev-...`
  - 失败/中断后显式重试：`bash scripts/db-migrate.sh --retry <NN> --yes --task dev-... --backup <备份.sql>`
- 输出怎么读：先列「待执行 / 已记账待归档 / 恢复记录」，再逐条执行；结尾「执行摘要」给成功 / 已记账未重跑 / 失败待恢复 / 未执行。
- 退出码：0=成功，非 0=中止（**失败即停，SQL 可能部分生效，不自动回滚**）。
- 注意：**不要直接 `mysql < 文件`**；失败/中断禁止自动重跑——用 `--record`（已生效）或 `--retry`（确需重试）。断点记录在仓库 `.tmp/db-migrate/`（按目标库隔离），未恢复前别清。

## 2b. scripts/test-db-migrate.py —— 迁移工具隔离回归（2026-10-11，任务 dev-20261011-014）

- 用途：`db-migrate.sh` + `pre-commit` 归档闸门的隔离回归（**不连库、不执行正式 SQL、不启服务**；用 mock mysql + 临时目录）。
- 危险等级：🟢 只写临时目录。
- 前置：`python3`/`bash`/`git`/`flock`。
- 命令：`python3 scripts/test-db-migrate.py`（20 条用例，覆盖预览无副作用、按号排序且补低号遗漏、SQL/记账/读取失败即停、只补归档续跑、另一库扫归档、重复号拒绝、备份/任务校验、缺账本不从最大号推断、连接/账本异常 fail-closed、指纹变更拒绝、同库锁、归档门禁同名放行/改内容拒绝）。
- 退出码：0=全通过，非 0=有用例失败。

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

- 用途：`jjx-docs/sql/00_clean_test_data.sql`（整表 TRUNCATE + 条件 DELETE，表清单以脚本实际解析为准）的**唯一**入口。
- 危险等级：🟢 无参数/`--domains`=只读体检（不写库）／🔴 `--execute` 真清理（固定顺序：体检 → 校验手工备份 → 人工确认 → 执行）。
- 前置：`--execute` 必须**在终端手工执行**（agent/管道一律拒绝）；确认方式=手工输入库名 `jjx_erp_db`；**已有今天的全库备份**（默认 `JJX_BACKUP_DIR`＝仓库内 `jjx-docs/sql/backups/`，或 `--backup`/`JJX_CLEAN_BACKUP` 指定）——2026-09-28 起脚本**不再自动备份**、只校验手工备份存在才放行；备份必须排除 `hr_employee`。
- 域参数：`--domains <逗号分隔>`，可选 `purchase` / `inventory` / `quality`，可组合；域模式跳过 `sys_task` 清理。
- 产品参数：`--include-products` 仅限全量清理，不能与 `--domains` 混用。默认保留产品；显式启用才清空 `product_config_option`、`product_config_model`、`product`、`product_category`，随后清理无业务引用的 PRODUCT 库存身份，MATERIAL 身份保留。此选项清空所有产品资料，不能自动区分正式产品和测试产品，也不是仅清理产品的独立模式。
- 产品关联保护：预览及人工确认后检查保留表的 `product_id` / `product_code` 和产品相关标签关系，存在引用（含已有悬空引用）即中止；关联业务表先清，产品资料后清。查询失败即中止，显示数据库错误。启用后清理完核验四张产品表及 PRODUCT 库存身份均为零，并在执行日志记录 `include_products` / `domains`。
- 命令：
  - 体检：`bash scripts/db-clean-test-data.sh`
  - 含产品的全量预览：`bash scripts/db-clean-test-data.sh --include-products`
  - 含产品的全量清理：`bash scripts/db-clean-test-data.sh --include-products --execute`（须手工备份、终端输入库名；会清空全部产品）
  - 只体检三域：`bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality`
  - 真清：`bash scripts/db-clean-test-data.sh --execute`（用今天的全库备份；无备份即中止）
  - 真清三域：`bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality --execute`（仍须终端手输库名）
  - 指定备份：`bash scripts/db-clean-test-data.sh --execute --backup <手工全库备份.sql>`
- 输出怎么读：① TRUNCATE 组（有数据的表逐条列出行数 + 合计）② DELETE 组（将删/保留条数）③ **覆盖率校验**（库表是否都有归宿：清理清单 ∪ 保留白名单；有未登记的表即**阻断清理**，提示是加进清理段还是补进保留清单/`RETAINED_TABLES`）→ `--execute` 时先**校验手工备份存在**并打印备份路径/md5 → 执行后在 `$JJX_BACKUP_DIR/clean-test-data-log.txt` 留痕（脚本会清空 `sys_oper_log`，库里留不下痕迹）。
- 退出码：0=体检通过或清理成功，1=拒绝执行/中止/失败。

## 5. scripts/clean-archive-ocr-data.sh —— 清档案 OCR 测试数据（并行会话产出）

- 用途：只删历史档案 OCR 的测试档案及其草稿产物（范围很窄，1~2 条）。
- 危险等级：🔴 改数据库。**注意：本脚本用 `--yes` 放行，与上面"🔴 必须手输库名"的统一口径不一致**（待用户决定是否统一）。
- 前置：默认只预览；`--yes` 才备份并删除。
- 命令：`bash scripts/clean-archive-ocr-data.sh`（预览）／加 `--yes`（真删）。
- 退出码：0=成功，非 0=中止。

## 6. scripts/db-backup.sh —— 独立全库备份（2026-09-14 新增，任务 dev-20260914-027）

- 用途：不触发任何库内变更，只想**立刻拿一份全库快照**时用（补上此前只能手敲 `mysqldump` = 绕过规范入口的缺口）。
- 与其它脚本的边界：迁移/清测试数据脚本（§2/§4）**已不再内部自带备份**，都改为「校验手工备份存在才放行」（2026-09-28 口径）——本脚本仍是想立刻拿一份全库快照时的独立入口；`db-export-init-subset.sh` 出的是初始化交付物、**不是备份**。
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

- `scripts/check-result-case.mjs`：检查 PASS/FAIL 结果比较是否统一且大小写安全；退出码 `0`=绿、`1`=红。

## 10b. scripts/task-register.sh —— 开发任务登记唯一入口（2026-09-24 立，任务 dev-20260924-032 同批）

- **用途**：登记 `dev-YYYYMMDD-NNN` 任务到 `sys_task`。**禁止再手写取号 SQL**（并行会话各自 `MAX+1` 必然撞号，且手写 SQL 出过两类事故：`LPAD(@base+n,3,'0')` 被当小数产出畸形码 `dev-YYYYMMDD-14.`；`INSERT…SELECT…FROM sys_task` 少聚合 → 插多行 → 整条回滚）。
- **危险等级**：🔴 改数据库（INSERT `sys_task`）+ 🟡 写备份文件/索引
- **用法**：`bash scripts/task-register.sh --title "标题" [--priority P2] [--desc-file <file>] [--by dahuang] [--dry-run]`
- **内部保证**：① 改库前 sys_task 表级 guard 备份（落 `jjx-docs/sql/backups/`）② 取号+插入用**单条聚合 SQL**（`CAST(... AS UNSIGNED)` 再 `LPAD`）③ 撞唯一约束**自动重试**（≤3 次）④ 登记后**回查** `task_code` 格式（`^dev-YYYYMMDD-NNN$`）与当日最大号，不符即报错 ⑤ 拿到真码后才写 `backup-index.tsv`
- **退出码**：0=成功（stdout 打印 `task_code`）；1=参数/前置/登记失败
- **注意**：撞号报错**不一定是别人抢先**——先看 SQL 是否插了多行；并发下以脚本回查结果为准（唯一约束是最后一道保险）

## 10c. scripts/check-model-baseline.sh —— 表数基线门禁（2026-09-28，任务 dev-20260928-020）

- **用途**：只读核对 `jjx_erp_db` 的实际表名清单、已批准新表和类型后缀白名单；`--write-baseline` 受控更新基线并先备份原文件。
- **危险等级**：🟢 无参数/`--baseline`=只读数据库检查；`--write-baseline` 仅写基线文件（可回退），不改数据库。
- **用法**：`bash scripts/check-model-baseline.sh`；`bash scripts/check-model-baseline.sh --write-baseline`；`bash scripts/check-model-baseline.sh --baseline <path>`；`bash scripts/check-model-baseline.sh --help`。
- **退出码**：`0`=通过；`1`=检查失败、参数错误或数据库/文件读取失败。
- **检查项**：①未批准新表（库有、基线+批准例外里没有）②批准例外在库（防登记后忘了建）③基线表都在（防静默删表）④类型后缀闸（`_scrap/_rework/_return/_release` + 可选 `_order/_item` 结尾，白名单外即红）⑤白名单质量（debt 必带 retireTask/target，false-positive 必带 reason）⑥**批准例外字段**（每条 `approvedNewTables` 必须带 任务码/日期/提案链接，§15.7）⑦同构表告警（仅提示）。
- **接线（2026-09-28 复核补正 dev-20260928-045）**：`npm run validate`（`check:model-baseline`）+ `scripts/hooks/pre-push`（红则拦）+ `db-migrate.sh` 执行后复核（仅提示不阻断）。

## 11. 自动跑（不用手敲）

| 钩子 | 什么时候跑 | 拦什么 |
|---|---|---|
| `scripts/hooks/pre-commit` | 每次 `git commit` | `jjx-docs/sql/`（除 backups/）与 `jjx-docs/standards/` 下的删除/移动；`status-magic-baseline.json` 放大 |
| `scripts/hooks/commit-msg` | 每次 `git commit` | 提交信息必须有任务码 `dev-YYYYMMDD-NNN`，且该码真实存在于 `sys_task`（用只读账号 `jjx_ro` 校验；库不可达时只提醒不阻塞） |
| `scripts/hooks/pre-push` | 每次 `git push` | 建表闸（`scripts/check-model-baseline.sh`，表数基线 §15）红则拦；库不可达时只提醒不阻塞 |

单次跳过：`git commit --no-verify` / `git push --no-verify`（确认后果再用）。

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
