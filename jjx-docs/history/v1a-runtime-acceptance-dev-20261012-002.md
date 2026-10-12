# 公共附件 V1-A 运行时验收报告

- 任务码：dev-20261012-002（关联回填任务 dev-20261012-003）
- 日期：2026-10-12
- 验收对象：271_attachment_references.sql / 272_attachment_reference_backfill.sql 执行后的运行态
- 执行人：Hermes Agent（只读取证 + 只读接口/DB 探针；未启停服务、未改数据、未 commit/push）
- 结论：**NO-GO**（后端进程未运行，10 项中 7 项运行时未验；另发现 17 条质量模板硬引用断链）

## 0. 前置条件核对

前置条件「271/272 已执行成功，数据库已备份」——**成立**，证据：

```sql
SELECT config_key,config_value FROM sys_config
WHERE config_key IN ('ops.schema.version','ops.schema.file.271','ops.schema.file.272');
-- ops.schema.version      | 272
-- ops.schema.file.271     | {"file":"271_attachment_references.sql","sha256":"d7ef41f2…80a563"}
-- ops.schema.file.272     | {"file":"272_attachment_reference_backfill.sql","sha256":"969ddb54…490e3"}
```

- 两份迁移均已记账并归档到 `jjx-docs/sql/migrations/applied/`，`.tmp/db-migrate/` 无 inflight 残留。
- 272 实际执行时间 2026-10-12 11:01:53（`sys_attachment_ref.create_by='migration-272'`）。
- 备份：`jjx-docs/sql/backups/jjx_erp_db_backup_20261012-1028.sql`（4,748,412B / 126 张表 / 不含 hr_employee）。

## 1. 逐项判定

| # | 验收项 | 判定 |
|---|---|---|
| 1 | 迁移后表结构、历史引用数量及回填异常 | PASS |
| 2 | 临时上传、批量上传、绑定、解绑、跨业务复用 | BLOCKED |
| 3 | SHA-256 重复检测（不做物理文件去重） | BLOCKED |
| 4 | 附件分类、展示名称、备注及来源过滤 | BLOCKED（数据侧 PASS） |
| 5 | 询价单/报价单/销售订单/样品单/产品的附件权限 | BLOCKED |
| 6 | 已锁定单据禁止修改附件 | BLOCKED |
| 7 | 旧附件列表、下载、删除接口的兼容性 | BLOCKED |
| 8 | 已发布产品作业规范及其他历史硬引用仍可读取 | **FAIL** |
| 9 | 临时文件 7 天、解绑文件 30 天的清理资格计算 | BLOCKED（实现+数据复算 PASS） |
| 10 | 物理删除功能仍然关闭 | PASS |

## 2. 通过项的证据

### 2.1 第 1 项 PASS：结构 / 引用数量 / 回填异常

结构（271 全量生效）：

```sql
SELECT column_name,column_type,is_nullable,column_default FROM information_schema.columns
WHERE table_schema='jjx_erp_db' AND table_name='sys_attachment'
  AND column_name IN ('upload_state','expires_at','upload_key','upload_owner');
-- upload_state varchar(16) NO LEGACY / expires_at datetime YES NULL
-- upload_key char(64) YES NULL / upload_owner varchar(64) YES NULL
```

- `uk_attachment_upload_key(upload_key)` 存在；`trg_attachment_v1_no_delete` 存在；
  `sys_attachment_ref` 已建：19 列、`uk_attachment_ref_active`(attachment_id,source_type,source_id,active_product_id,active_marker)、
  外键 `fk_attachment_ref_file`/`fk_attachment_ref_product`、CHECK `chk_attachment_ref_ids`/`chk_attachment_ref_deleted`。
- 字典：`attachment_category/general`、`attachment_document_type/general_document` 均在。

引用数量与回填对账（按 272 脚本口径复算）：

```sql
SELECT a.id,a.biz_type, CASE
  WHEN a.deleted<>0 THEN 'DELETED_ATTACHMENT'
  WHEN a.file_path IS NULL OR TRIM(a.file_path)='' OR a.file_name IS NULL OR TRIM(a.file_name)='' THEN 'MISSING_FILE_METADATA'
  WHEN a.biz_id IS NULL OR a.biz_id<=0 THEN 'INVALID_SOURCE_ID'
  WHEN a.biz_type NOT IN ('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material') THEN 'UNKNOWN_SOURCE'
  ELSE 'CHECK_INSERTED' END AS reason_272 FROM sys_attachment a ORDER BY a.id;
-- 5 rows: id2/3/4 engineering_jjx_icon(biz_id=0) => INVALID_SOURCE_ID
--         id12 inquiry / id13 quotation          => CHECK_INSERTED
SELECT COUNT(*) FROM sys_attachment_ref;  -- 2
```

- 结果一致：可回填 2 条（inquiry 1 / quotation 2）→ `sys_attachment_ref` 2 行；
  3 条 `engineering_jjx_icon`（biz_id=0）按 `INVALID_SOURCE_ID` 跳过，`document_type`/`product_id` 均 NULL，符合「不猜产品/分类」。
- 回填异常 0：272 只执行 INSERT（脚本内无 UPDATE/DELETE 语句），附件身份（file_path/sha256/biz_type/biz_id）未被改写；
  现存 `sys_attachment` 5 行 = 10-10 清库（删 product 附件 id5-11）之后的稳定集合。
- 清理候选复算：`SELECT COUNT(*) FROM sys_attachment WHERE upload_state='TEMP'` → 0。

### 2.2 第 10 项 PASS：物理删除仍关闭

- 触发器实测拦截（真删一次、自动回滚）：

```sql
BEGIN; DELETE FROM sys_attachment WHERE id=12; ROLLBACK;
-- ERROR 1644 (45000): V1-A: physical attachment deletion disabled; preview only
SELECT COUNT(*) FROM sys_attachment WHERE id=12;  -- 1（行未被删除）
```

- 两个旧清理入口写模式禁用：`scripts/db-clean-test-data.sh:59` 硬拒 `--execute`、`scripts/clean-archive-ocr-data.sh:33` 硬拒 `--yes`。
- 清理预演恒定 `physicalDeleteEnabled=false`（`AttachmentLifecycleService.java:123`）。

### 2.3 第 4 项数据侧 / 第 9 项实现与数据复算

- 客供资料元数据可读：id12 `INQ261012001-YL001MEOO-客户图纸-01.jpg`、id13 `QT261012002-YL002MEOO-其他资料-01.jpg`，
  customer_product_code / customer_doc_type / customer_doc_seq / display_name 四列齐全。
- 保留期规则实现（`AttachmentLifecycleService.java:100-120`）：
  1. 有活动引用 → `ACTIVE_REFERENCE`；
  2. 受控/历史硬引用 → `CONTROLLED_OR_HISTORICAL_REFERENCE`；
  3. upload_state 为 NULL/LEGACY、或 biz_type 非空、或 upload_owner 为空 → `LEGACY_REVIEW_REQUIRED`；
  4. 同 file_path 多 ID 共享 → `SHARED_PATH_REVIEW_REQUIRED`；
  5. 否则：有引用历史 → `MAX(deleted_at)+30 天`；无引用历史且 TEMP → `create_time+7 天`；
     deadline 为空 → `RETENTION_START_UNKNOWN`；未到期 → `RETENTION_PERIOD`；到期 → `ELIGIBLE_PREVIEW_ONLY`（且仅预览）。
  6. 另有「不因过期状态/元数据时钟缩短保留期」的兜底（:118）。
- 用现存 5 行复算：全部 `upload_state=LEGACY` 且 biz_type 非空 → 全部落入 `LEGACY_REVIEW_REQUIRED`，
  无 TEMP、无解绑历史、无 ELIGIBLE 行，与「历史文件一律人工复核、禁止自动清理」一致。
- 运行时未验原因见第 3 节（第 9 项的接口面同样未验）。

## 3. 阻塞（BLOCKED）根因：后端未运行

```sh
ps -eo pid,lstart,cmd | grep '[j]ava' | grep -v vscode   # 无输出（无后端进程）
ss -ltn | grep -E ':8080|:3000'                          # 只有 3000（vite dev），无 8080
tail -5 logs/jjx-server.log
# 2026-10-12 11:02:16 GracefulShutdown 开始 → HikariPool-1 Shutdown completed
```

- 后端于 2026-10-12 11:02:16 已优雅关闭，此后未再启动；`jjx-server/target/jjx-server-1.0.0.jar` 构建时间 11:02，
  `target/classes` 内已含 V1-A 类（`SysAttachmentRefController`、`AttachmentLifecycleService`、`AttachmentSourcePolicy`、`SysAttachmentRefMapper`、`SysAttachmentRef`）→ 代码已编译，**缺的只是运行实例**。
- 受影响：第 2、3、5、6、7 项以及第 4 项的接口面、第 9 项的预演接口，全部无法执行；
  按验收约束「遇到真实阻塞问题，记录证据并停止，不擅自绕过」，本轮到此停止，未启动服务（服务生命周期属用户操作）。

## 4. FAIL 项证据：第 8 项 硬引用断链

| 硬引用来源 | 断链数 | 判定 |
|---|---|---|
| `product_work_spec_item`（resource_type='DRAWING'.source_id） | 0 | PASS |
| `engineering_film`（file_id） | 0 | PASS |
| `quality_template_registry`（file_id） | **17** | FAIL |

```sql
SELECT COUNT(*) FROM quality_template_registry q
LEFT JOIN sys_attachment a ON a.id=q.file_id
WHERE q.file_id IS NOT NULL AND a.id IS NULL;   -- 17
```

明细（全部 `status=1`）：JJX-QR-005 制造指令单(file 6)、024 采购订单(8)、026 送货单(11)、030 ECN(7)、031 领料单(25)、
035 设备保养记录表(20)、037 进料检验报告(5)、048 合同记录一览表(19)、049 客户投诉一览表(15)、050 纠正及预防措施表(16)、
054 内部异常联络单(22)、058 QC日报表(17)、059 特采联络书(18)、065 样品需求单(9)、071 变更记录表(10)、078 加班申请单(11)、090 出货查检表(14)。

- 根因：**不是 271/272 造成的**。271 只增列/增表/增触发器/增字典，272 只 INSERT 引用；
  这些 file_id 指向的附件行是**此前清库**删掉的（id5-11 由 dev-20261012-001 的 product 附件清理删除，14/15/16/17/18/19/20/22/25 属更早批次）。
- 影响：上述 17 张质量记录/报表模板的「查看/下载/打印附件」链路取不到文件（走新引用体系时会 404）；
  这既是 V1-A 声称的「历史硬引用受保护」的现实反例，也是需要在 V1-B 前拍板处理的数据缺口。

## 5. 未取证的项（本轮不做）

- 未做：HTTP 联调、双用户越权、并发绑定/解绑、TEMP 跨用户与过期时钟边界、NULL 唯一性/外键边界。
- 未做：任何写操作、任何全量测试、任何新增测试类、任何数据修复（符合验收约束）。

## 6. GO / NO-GO

**NO-GO。** 放行条件（两项，缺一不可）：

1. 后端启动到含 V1-A 代码的构建（jar 11:02 已具备，仅需启动）→ 重跑第 2、3、4-接口面、5、6、7、9-接口面；
2. 第 8 项 17 条质量模板硬引用断链给出处理口径（补文件 / 置失效并标记 / 明确接受），否则「历史硬引用可读」不成立。

清除后可将本报告更新为 GO，并进入 V1-B 前端开发。

## 7. 遗留未决项（待登记）

1. 17 条 `quality_template_registry.file_id` 断链的处置口径（需用户拍板）。
2. 第 2/3/5/6/7/9 项的运行时验收需在后端启动后补验。
3. `sys_task` 中 dev-20261012-002/003 仍为 status=0，待本验收结论后按流程收口。
