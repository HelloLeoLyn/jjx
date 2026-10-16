# 2026-10-16 sys_task 全表误刷事故复盘

任务码：dev-20261016-086。事故日期：2026-10-16；复盘与只读核查：2026-10-17（Asia/Shanghai）。

结论：071 完成回写时 UPDATE 缺少 WHERE，将 sys_task 当时全表 280 行刷成同一套完成信息。派卡方已通过 binlog before 镜像恢复 280 行，并对 071 自身行精确补回完成状态。**数据已恢复，不等于根因已修复；本卡仅登记复盘与预防提案，没有部署 SQL 护栏。**

## 1）范围与事实来源

本卡只新增本复盘及 history/INDEX.md 一行，不改业务代码、规范正文、数据库结构或事故数据，不重做恢复，不生成备份，不新增脚本护栏。任务领取和完成回写仅按任务卡授权修改 dev-20261016-086 自身记录，并使用精确 WHERE。

事故时间、280 行影响范围、binlog 解码与还原结果来自派卡方在任务卡中提供的“已核实事实”。本卡另做备份文件存在性/哈希、当前 binlog 配置和恢复后少量任务的只读核查；未重新下载解码 binlog、未逐行比对全部 before 镜像。下文明确区分两类证据，不把当前抽查写成独立完成全量恢复验收。

## 2）时间线

| 时间（Asia/Shanghai） | 事件 | 证据及确认范围 |
|---|---|---|
| 2026-10-16 23:46:33 | 071 完成回写执行无 WHERE 的 UPDATE，全表 280 行被覆盖；记录中的 update_by 为 Hermes | 派卡方已核实；完成备注指纹为 d164598a。update_by 是写入字段，不单独用于识别唯一操作者进程/会话 |
| 事故发现后，具体发现时刻未提供 | 发现 status、assignee_name、completed_time、remark 呈全表一致；description 未损 | 派卡方已核实；未补造发现时刻或发现人 |
| 2026-10-16 23:58（备份文件名及本机文件时间） | 恢复前制作 sys_task 表级 guard 备份 | `jjx-docs/sql/backups/sys_task_recover-overwrite_20261016-2358.sql`；是误刷后的恢复前保护点，不能代替事故前数据 |
| guard 备份后，具体恢复完成时刻未提供 | 从 binlog 000147 解码相关 ROW 事件，按 task_id 最早 before 镜像还原 280 行；随后精确补回 071 自身记录 | 派卡方已核实：after 镜像中指纹 d164598a 匹配 286 条，去重后 280 个 task_id |
| 2026-10-17 00:00:59～00:01:01 | 086 精确领取；只读核对当前 binlog、备份和 067/071 样本 | 本卡实际命令输出见 §5；不是再次执行恢复 |

## 3）影响面与根因

| 对象或列 | 事故影响 | 恢复与说明 |
|---|---|---|
| sys_task 全表 | 当时 280 个独立任务行受到影响 | 按 280 个 task_id 逐行还原；286 是日志指纹匹配条目数，不能当作独立受损行数 |
| status | 全部变成 2（待审核），真实待开始/进行中等状态丢失 | 已从 before 镜像恢复；不能将受损期间的 status=2 当作完成证据 |
| assignee_name | 全部变成 Hermes | 原执行者归属受损，不能据此归责每个任务 |
| completed_time | 全部变成 2026-10-16 23:46:33 | 原完成时间受损；071 自身完成时间按真实完成回写保留 |
| remark | 全部被同一段 484 字完成备注覆盖，含提交指纹 d164598a | 067 原始完成备注已从 binlog 救回；071 自身补回 484 字备注 |
| update_by | 此次误刷记录值为 Hermes | 恢复时需随 before 镜像核对其原值，不能把该字段当独立身份审计 |
| task_id / task_code / description | 派卡方确认未损 | 主键/任务码可用于精确定位；description 可保留原任务内容，但不能据此完整推算受损状态及备注 |

直接根因：071 的完成回写 UPDATE 未带 WHERE，原本只应修改一张任务卡的语句作用于 sys_task 全表。该根因由派卡方核实；ROW 日志直接证明行前后变化，本卡没有原始 SQL 全文或会话审计，不伪造原命令、行号或唯一执行进程。

防线缺口：此次写入未在产生全表影响前被有效阻止；恢复已消除数据损坏，但本卡没有改写入工具、权限或执行机制，不能宣称相同语句今后已被程序拦截。前期增加沟通规则也不能替代 SQL 范围检查。

## 4）证据与已执行的恢复手法

1）binlog 证据：日志序号/文件尾号为 **000147**；派卡方使用 `mysqlbinlog --read-from-remote-server --base64-output=DECODE-ROWS -v` 远程解出 ROW 行事件。完整日志文件名前缀、起止 position/GTID、解码输出文件路径未在任务卡给出，本卡不猜填。
2）筛选依据：定位事故时段 sys_task 的 UPDATE_ROWS 及 after 镜像中的 **d164598a** 指纹；相关匹配条目 **286 条**，按 task_id 去重为 **280 个**。指纹只是定位线索，必须结合表、时间、事务/事件边界及主键核对，不能仅全文搜索指纹就生成恢复写入。
3）恢复前保护点：`jjx-docs/sql/backups/sys_task_recover-overwrite_20261016-2358.sql`。本机核查文件存在，大小 **1,402,075 字节**；SHA-256：`52f805e9d23ffbfc9e87cb5f74cf9cf598f880ee1132b36062ae81cae1887bf4`。备份是已有事故产物，不属于本任务提交，不读取正文外发，也不删除、改写或重出。
4）镜像还原：按日志顺序，对每个受损 task_id 取事故相关事件中**最早 before 镜像**，逐行恢复到该行第一次被覆盖前的状态；不能取后续已经含误刷值的 before 镜像。按当时表列定义核对镜像列序与完整性，不能靠现有统一备注反推原值。
5）业务完成记录补回：恢复原状态后，对 **dev-20261016-071** 使用精确 WHERE 单独补回 status=2 及 484 字真实完成备注；067 原始完成备注通过镜像恢复。恢复动作本身应逐项核对影响行数与结果，不能再用无 WHERE 的“统一修正”。

以上是对已执行恢复的记录，不是授权再次运行恢复 SQL。今后复用该手法，必须重新确认目标事故边界、后续合法写入及监督授权，不能把旧 before 镜像重放到当前表而覆盖事故后的正常工作。

## 5）本卡只读核查记录

查询账号为 jjx_ro。以下为本卡执行结果，除明确标出的任务领取外没有写事故数据。

```sql
SELECT @@GLOBAL.log_bin AS log_bin,
       @@GLOBAL.binlog_format AS binlog_format,
       @@GLOBAL.binlog_row_image AS binlog_row_image;
```

实际输出：`log_bin=1`、`binlog_format=ROW`、`binlog_row_image=FULL`。这证明核查时配置，不把当前值当作事故时配置的独立历史证据；事故时具备可恢复 before 镜像的事实来自 §4。

```sql
SELECT COUNT(*) AS current_task_rows,
       COUNT(DISTINCT status) AS distinct_statuses,
       COUNT(DISTINCT assignee_name) AS distinct_assignees,
       COUNT(DISTINCT remark) AS distinct_nonnull_remarks
FROM sys_task;
SELECT task_code,status,assignee_name,completed_time,
       CHAR_LENGTH(remark) AS remark_length
FROM sys_task
WHERE task_code IN ('dev-20261016-067','dev-20261016-071');
```

实际输出：

| current_task_rows | distinct_statuses | distinct_assignees | distinct_nonnull_remarks |
|---|---|---|---|
| 281 | 5 | 4 | 34 |

| task_code | status | assignee_name | completed_time | remark_length |
|---|---|---|---|---|
| dev-20261016-067 | 2 | dahuang | 2026-10-16 22:37:46 | 494 |
| dev-20261016-071 | 2 | Hermes | 2026-10-16 23:46:33 | 484 |

当前 281 行与事故时 280 个受损 task_id 属不同时间口径；本次只证明状态、执行者和备注已不是全表同值，并展示两行样本。它不能独立证明 280 行每个字段都已恢复；没有重新做全量镜像核对。

文件核查命令：`ls -l jjx-docs/sql/backups/sys_task_recover-overwrite_20261016-2358.sql`、`sha256sum jjx-docs/sql/backups/sys_task_recover-overwrite_20261016-2358.sql`，结果见 §4。

## 6）预防措施提案（待用户批准，未写入规范、未部署护栏）

以下三条用于后续审批与执行设计。本卡记录提案，不把它们表述为新增已生效的 CONVENTIONS；与现有规则重合的部分仍按现行真源执行。

1）**任务回写限定一行。**建议回写一律使用 `WHERE task_code='<精确码>'`，禁止 LIKE、范围匹配及无 WHERE。执行前 SELECT 精确任务码的现值、归属及 CHAR_LENGTH(remark)，确认唯一一行；保留既有备注并只追加本次记录，超过 500 字追加 description。执行后立即检查影响行数与精确行内容。预期只改一行却报告多行时停止后续操作；零行也须核实条件，不去掉 WHERE“重试”。未来若增回写工具，应在执行前验证精确任务码及预期行数；该脚本护栏需另立卡，本卡未实现。
2）**批量破坏性写入须有 guard 与监督。**建议任何批量 UPDATE/DELETE 在执行前先有目标表级 guard 备份，确认目标行集合、数量及恢复办法；不得在无人监督下执行。备份由用户按现行口径手工准备，放仓库内指定备份目录、保留策略遵循现行真源；agent 不擅自自动生成。正常任务登记、单行状态/备注回写仍按已有免备份规则，不用本提案扩大备份范围。
3）**保留可恢复的日志证据。**建议保持 binlog 开启且为 ROW 格式；还需核对行镜像能覆盖待恢复列，FULL 是本次只读核查值，不能认为只设 ROW 就必定能还原所有列。按 §4 保存日志文件标识、事故边界、主键、最早 before 镜像及恢复核对记录；日志保留期限与清理策略应另行明确，不能在留存足够日志前断言可恢复。本卡未修改 binlog 开关、格式或保留配置。

## 7）文档交付与未完成项

交付只有本复盘文档与 history/INDEX.md 一行，UTF-8 BOM。最小文档检查为 `cd jjx-web && npm run check:docs`；实际执行仍按本机护栏包在 `systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npm run check:docs` 内。最终命令退出码与提交记录写入 dev-20261016-086 的回写。

未完成：预防提案审批、任务回写脚本/权限护栏、事故原 SQL 与日志 position/GTID 的补充归档、独立逐行全量恢复复验。上述均未在本卡实施；**不能把本复盘完成、数据恢复完成写成根因已修复。**
