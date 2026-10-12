# V1-A 运行时 P0 修复报告

- 任务码：dev-20261012-002
- 日期：2026-10-12
- 范围：只修第二轮运行时验收发现的 P0；不扩大 V1-A 功能面
- 本轮未执行：数据库迁移、服务重启、物理清理、Git commit/push、全量测试
- 结论：两项 P0 已落代码与迁移 SQL（待人工执行/重启）；第三项（硬引用误伤）只出方案不改动

## 1. 修改文件及原因

| 文件 | 类型 | 原因 |
|---|---|---|
| `jjx-docs/sql/migrations/273_attachment_business_nullable.sql` | 新增迁移 | `biz_type`/`biz_id` 放开为可空（TEMP 附件无业务来源），修复「Field 'biz_type' doesn't have a default value」 |
| `jjx-server/.../system/service/impl/SysAttachmentServiceImpl.java` | 修改 | ① 旧上传建档路径显式落 `upload_state=BOUND`（区分新上传 vs 真历史）；② `deleteAttachment`/`restoreAttachment` 对空 `biz_type` 给明确错误，避免 `canonical(null)` 抛「附件来源不能为空」的误导信息 |
| `jjx-server/.../system/service/AttachmentLifecycleService.java` | 修改 | 清理预演的「历史保守处理」判定改为只看 `upload_state ∈ {null, LEGACY}`，不再用 `biz_type != null / upload_owner == null` 当历史标志 |

未改：271/272 迁移文件（已执行，按规则禁改）；`SysAttachment` 实体、Controller、mapper（本会话未动，仍是 V1-A 交付件）；任何历史数据；物理清理开关。

## 2. 新增迁移 SQL 与执行前置条件

文件：`jjx-docs/sql/migrations/273_attachment_business_nullable.sql`

```sql
ALTER TABLE sys_attachment
  MODIFY COLUMN biz_type varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '业务类型（如: sales_order, product等）；TEMP/未绑定引用时为空',
  MODIFY COLUMN biz_id bigint NULL
    COMMENT '业务记录ID；TEMP/未绑定引用时为空';
```

- 只放开可空性，**不改数据、不改索引、不重建表**；历史行的 `biz_type/biz_id` 原值原样保留（`idx_biz`、`idx_attachment_drawing`、`idx_attachment_hash_scope`、`idx_customer_doc_source` 四个索引允许 NULL，无需改动）。
- 幂等：重复执行即 MODIFY 为同一目标定义，无副作用。
- 执行前置条件：① 按用户规则先做**手工全库备份**（排除 `hr_employee`，放 `jjx-docs/sql/backups/`，只留最新一份）；② 用 `bash scripts/db-migrate.sh 273_attachment_business_nullable.sql --yes --task dev-20261012-002 --backup <备份.sql>`；③ 执行后重启后端（本报告不代做）。
- 迁移未执行前，TEMP 上传仍会报同一个错；代码改动本身不依赖迁移先后（可先迁移后重启）。

## 3. TEMP 上传修复方式

1）依赖排查（动手前先查「谁假设 biz_type/biz_id 非空」）：

| 检查点 | 结论 |
|---|---|
| 旧业务列表/查询 | `getAttachments`/`findBySha256`/`customerDocs` 全部按**请求参数**带 bizType+bizId 查，不读行的这两列；`findBySha256` 已显式要求完整范围并给出「必须指定完整的业务范围」 |
| 下载/权限 | `AttachmentAccessService.canRead` 已用 `file.getBizType() == null ? null : sources.canonical(...)` 兜住空值；`protectedFile` 只用 `id` |
| 上传 | 旧上传路径本来就带 bizType/bizId；只有新的 TEMP 路径不带 → 唯一缺口 |
| 其他读取点 | `FileBackupService:243` 已判空（`未分类`）；`resolveBizTypeName(null)` 返回「附件」；`ProductServiceImpl:136` 只在 product 范围聚合 |
| 真正会误报的点 | `deleteAttachment`/`restoreAttachment` 直接 `canonical(bizType)` → 空值时报「附件来源不能为空」（误导）→ 本轮已改为明确提示 |
| 库侧 | `biz_type/biz_id` 参与 4 个索引，均可含 NULL；无 CHECK/唯一约束依赖非空 |

2）修复方式：
- TEMP 附件允许 `biz_type/biz_id` 为空（迁移 273）；
- 正式业务关联统一走 `sys_attachment_ref`（不变）；
- `AttachmentLifecycleService.upload()` 保持只写 `upload_state=TEMP` + `expires_at=now+7d` + `upload_key`/`upload_owner`，不伪造占位业务来源；
- 历史附件与业务关联均未改动（迁移只改列的可空性与注释）。

3）一致性（避免继续产生孤立文件）：现有顺序是「先落盘 → 再 insert → 事务回滚时只记日志不删文件」，这是**故意设计**（防丢盘上已存在的同名文件），但也意味着任何 insert 失败都会留孤儿文件。本轮**没有改删除策略**（V1-A 不自动物理删除），改为在 §5 给出人工处置建议；迁移 273 生效后该失败路径本身不再触发。

## 4. 30 天保留期状态转换规则（修复后）

| 附件来源 | 建档时 upload_state | 建立引用 | 解除最后一条引用 | 清理预演结果 |
|---|---|---|---|---|
| 真历史行（271 之前存在，库默认 LEGACY） | `LEGACY` | 保持 `LEGACY`（不解除隔离） | 保持 `LEGACY` | `LEGACY_REVIEW_REQUIRED`（deadline=None，保守） |
| 新 TEMP 上传（`/refs/temp`） | `TEMP` + `expires_at=+7d` | `BOUND` + `expires_at=NULL` | `ORPHAN` + `expires_at=+30d` | 无引用时按 `max(deleted_at)+30d` 判 `RETENTION_PERIOD` / `ELIGIBLE_PREVIEW_ONLY`，未绑定 TEMP 按 `create_time+7d` |
| 新经旧接口上传（`/attachment/upload`、`batch-upload`、`upload-product`、`customer-doc-upload`） | `BOUND`（本次修复新增） | 已是 BOUND | `ORPHAN` + `expires_at=+30d` | 同上，30 天期限可上屏 |

判定顺序（`AttachmentLifecycleService.preview`）：活动引用 → 受控/历史硬引用 → `state ∈ {null, LEGACY}`（真历史，保守） → 同路径共享 → 否则算 deadline（引用历史 `max(deleted_at)+30d`，TEMP 且无引用历史 `create_time+7d`），并保留「不因过期状态/元数据时钟缩短保留期」的兜底。

配套改动（`AttachmentRefService` 未改，规则本就正确）：`create()` 对非 LEGACY 行写 `BOUND` 并清 `expires_at` → **重新绑定即取消清理资格**；`unlink()` 仅在「最后一条活动引用被解除」时写 `ORPHAN`+30 天，重复解绑不移动时钟。
物理清理继续关闭（`permanentDelete`/`permanentDeleteExpired` 仍直接抛「V1-A 仅允许清理预演」）。

## 5. 孤立文件后续处理建议

现状：`upload/attachment-temp/` 有 5 个 `.bin`（各 70B，11:34），是临时上传失败（insert 报错、事务回滚）留下的产物；DB 中无任何记录指向它们。

表格化建议（**本轮不执行**）：

| 项 | 建议 |
|---|---|
| 判定孤立 | 按「`upload/attachment-temp/*` 文件名不在 `sys_attachment.file_path` 中」筛（temporary 路径唯一，不会误伤正式文件） |
| 处置方式 | 迁移 273 + 后端重启后，先重跑一次临时上传确认不再产生新孤儿；旧孤儿文件由**人工确认后手工删除**（V1-A 不自动物理删除，且不引入定时清理） |
| 防复发 | 保留「先写盘后入库」顺序，但把失败日志升级为可点名的待核查清单；如后续要做自动清理，必须走独立提案（涉及"自动删除"这一 V1-A 明确关闭的能力） |
| 不做的 | 不改 `TransactionSynchronization` 的注册逻辑；不在本轮删除任何文件；不新增定时任务 |

## 6. 历史硬引用误伤的安全处理方案（只分析，未改动）

事实（本轮实测）：

```sql
SELECT COUNT(*) AS 模板总数, SUM(file_id IS NOT NULL) AS 有file_id,
       SUM(file_id IS NOT NULL AND file_id NOT IN (SELECT id FROM sys_attachment)) AS 指向不存在,
       SUM(file_id IS NOT NULL AND EXISTS(SELECT 1 FROM sys_attachment a
            WHERE a.id=q.file_id AND a.create_time>q.create_time)) AS 指向后建附件
FROM quality_template_registry q;
-- 100 | 19 | 8 | 11
```

- 100 张质量模板里 19 张有 `file_id`；其中 **8 张指向完全不存在**的附件（原 17 张，本轮测试附件恰好占用了 14/15/16/17/18/19/20/22/25 中 9 个号位，暂时"看起来存在"），**11 张指向"建档时间晚于模板本身"的附件** —— 含 10-10 的客供资料 12/13（JJX-QR-019、JJX-QR-039）与本轮验收测试文件（QR-090/049/050/058/059/048/035/054/031）。
- 模板全部建档于 2026-08-28，这批 `file_id` 是**早期批量写入的号段**，其目标附件早已随清库消失；后续任何新附件只要占到这些号位，就会"继承"这条硬引用。

**真实历史引用 vs 已失效悬挂引用的区分口径**（推荐）：同一条 `file_id`，若目标附件存在且 `attachment.create_time <= template.create_time` → 视为真实历史引用；否则一律视为**失效悬挂引用**（含指向后建附件的误伤）。

建议方案（三选一或组合，需拍板后另行提案执行，本轮不动）：

1. **A 数据侧隔离（最稳）**：为这 19 条模板把 `file_id` 置 `NULL` 并在 `remark` 写明「历史附件已失效，待补传」，同时让 `hasFile` 如实显示"无文件"；不删除模板、不删除附件、不触碰历史附件 ID。
   - 前置：需要一条**迁移**（改数据）+ 表级 guard 备份；`QualityTemplateRegistryServiceImpl:83` 的 `hasFile` 会随之为 false，属预期。
2. **B 代码侧加"身份校验"（治本，防再次误伤）**：`AttachmentAccessService.protectedFile` 里的硬引用判定改为**带时间条件**——只有"附件建档时间 ≤ 引用行建档时间"的引用才算硬引用；`quality_template_registry`/`product_work_spec_item`/`engineering_film` 三处统一点查。这样 id 复用时新附件不会被判受控，也不会被旧模板"借用"。
3. **C 绑定侧留痕（配套 B）**：模板绑定附件时同时记录 `sha256`（或 `file_path`），以后校验"号位 + 内容指纹"双匹配；不做则该风险类留一个残余口子。

不采纳的做法：删除质量模板；自动清空/改写 `file_id`；修改历史附件 ID；把测试附件删掉当作"修好了"；关闭硬引用保护来绕开误判。

影响面提示：后端目前只在 `hasFile` 与写入时用到 `file_id`（`QualityTemplateRegistryServiceImpl:83/113`），前端质量模板页面未检索到直接用 `fileId` 拼下载的代码 → **当前症状是"保护误判 + hasFile 误示真"**；一旦后续做"模板附件下载"就会取到错误文件（如把 70B 的验收测试 PNG 当成"进料检验报告"），所以建议在 V1-B 之前处理。

## 7. 最小验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端离线编译 | `mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true compile -q` | 通过（exit 0，未做 clean，保留运行中 jar） |
| 建档路径静态断言 | `grep -n setUploadState` 于 `SysAttachmentServiceImpl` / `AttachmentLifecycleService` | `SysAttachmentServiceImpl:121`（旧上传）、`:434`（产品文件）= `BOUND`；`AttachmentLifecycleService:80` = `TEMP` |
| NOT NULL 写入闸（咨询模式） | `python3 scripts/check-notnull-writes.py` | `sys_attachment` 无 ① 命中（该闸抓的是「实体缺列」，抓不到本例的"属性存在但单条路径不赋值"，故本轮同时补了静态断言） |
| 文档门禁 | `npm run check:docs`（jjx-web） | 通过（见文末登记） |
| 未跑 | 全量测试、迁移执行、服务重启、物理清理、commit/push | 按约束不执行 |

未做（受约束限制，属人工步骤）：迁移 273 的真实执行、重启后的 TEMP 上传回归、30 天保留期的运行态复验（需迁移+重启后才能验，验收清单见 §8）。

## 8. 下一步人工操作顺序

1）**全库备份**（用户手工）：`mysqldump ... jjx_erp_db --ignore-table=jjx_erp_db.hr_employee > jjx-docs/sql/backups/jjx_erp_db_backup_<YYYYMMDD-HHmm>.sql`，按「只留最新一份」删旧份。
2）**执行迁移 273**：
   `bash scripts/db-migrate.sh 273_attachment_business_nullable.sql --yes --task dev-20261012-002 --backup jjx-docs/sql/backups/<刚生成的备份>.sql`
   （迁移目录现最大号 272，273 递增；执行后自动记账 + 归档 applied/）
3）**重新编译打包 + 重启后端**（用户执行；本会话不启停服务）：
   `mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true package -DskipTests` → 重启 `java -jar jjx-server/target/jjx-server-1.0.0.jar`
4）**回归验证（建议照抄）**：
   a) TEMP 上传：`POST /system/attachment/refs/temp`（file + 16~100 位 requestKey）→ 期望 `code=200` 且返回新 attachmentId，`sys_attachment.biz_type/biz_id` 为 NULL、`upload_state=TEMP`、`expires_at=now+7d`；`upload/attachment-temp/` 不再新增孤儿；
   b) 临时附件绑定：`POST /system/attachment/refs` → `BOUND` + `expires_at=NULL`；
   c) 解绑最后一条引用：`DELETE /system/attachment/refs/{refId}` → `ORPHAN` + `expires_at=now+30d`；
   d) 清理预演 `POST /system/attachment/refs/cleanup-preview` → 该附件应为 `RETENTION_PERIOD` 且 deadline 非空、`physicalDeleteEnabled=false`，历史行仍 `LEGACY_REVIEW_REQUIRED`；
   e) 旧接口回归：`/attachment/upload`、`/batch-upload` 新建行应为 `BOUND`（不再默认 LEGACY）。
5）**清理本轮测试数据**（需用户点头）：验收附件 14~29、引用 4/5/6/7，以及 `upload/attachment-temp/` 的 5 个孤儿 `.bin`。
6）**决策项**：§6 的硬引用误伤按 A / B / C 哪种口径处理（建议 B 治本 + A 收口存量），拍板后另行提案与迁移。

## 9. 遗留与边界

- 代码改动集中在 2 个文件 + 1 个新迁移；未触碰 271/272、未改前端、未新增测试类。
- `SysAttachmentServiceImpl.java` 同时承载 V1-A 会话的在制品改动，本轮的改动已按最小片段叠加（3 处小改），未回滚他人内容。
- 本轮结论只覆盖 P0-1/P0-2 的修复与 P0-3 的方案；两项修复的**运行态有效性需迁移 273 + 重启后按 §8-4 复验**，验收报告届时可结为 GO/NO-GO。

## 10. 运行时补验结果（273 已执行 + 后端已重打包重启后）

运行态确认：`java -jar` 进程启动 2026-10-12 12:24:54 > jar 构建 12:24 → 跑的是含本轮修复的构建；`ops.schema.version=273`、`applied` 含 273、273 已归档 `applied/`；`sys_attachment.biz_type/biz_id` 均为 `YES`（可空）。

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | TEMP 上传成功，biz 为空、状态 TEMP、保留期 7 天 | **PASS** | `POST /refs/temp` → `code=200`，新附件 33：`biz_type=NULL, biz_id=NULL, upload_state=TEMP, expires_at=2026-10-19 12:25:45`（`TIMESTAMPDIFF(DAY,create_time,expires_at)=7`）；同键同内容重传返回同一 id（30→30）；同键异内容 `code=500 同一requestKey不能上传不同文件` |
| 2 | 绑定后 BOUND 且清除过期时间 | **PASS** | 32 绑定 refId=9 → `upload_state=BOUND, expires_at=NULL` |
| 3 | 解除最后一个引用后 ORPHAN、保留期 30 天 | **PASS** | 32 解绑 → `ORPHAN, expires_at=2026-11-11 12:25:34`（剩余 30 天）；ref 9 `deleted=1, deleted_at=12:25:34`；30 同样落到 ORPHAN |
| 4 | 预演显示 RETENTION_PERIOD + 截止时间，物理删除关闭 | **PASS** | `POST /refs/cleanup-preview` → `30 RETENTION_PERIOD deadline=2026-11-11T12:25:21 eligible=False physicalDelete=False`；重新绑定 32 → 回到 `BOUND, expires_at=NULL`（清理资格取消） |
| 5 | 旧上传新建=BOUND；历史 LEGACY 仍受保护 | **PASS** | 旧 `/attachment/upload` 新建 31 → `upload_state=BOUND`；历史 2/3/4/12/13 仍 `LEGACY, deleted=0`；旧下载 31 → `http=200 bytes=70` |

附加核查（无副作用）：

- **不再产生新孤立文件**：`upload/attachment-temp/` 磁盘 8 个文件、其中 3 个有 DB 记录（30/32/33 的正常 TEMP 文件），孤立文件仍是原有那 5 个（文件名与修复前完全一致）。
- **19 条模板异常引用未被动过**：复算仍为 19（8 悬挂 + 11 指向后建）。
- 全程使用隔离测试数据（新建附件 30/31/32/33 与引用 8/9/10），未清理既有测试数据、未执行迁移/重启/物理清理、未 commit。

### 剩余阻塞项

1. **19 条质量模板异常引用（未处理，按约束保留）**：8 条指向不存在附件、11 条指向"建档时间晚于模板"的附件；V1-B 前需按 §6 的 A/B/C 拍板。
2. **测试数据未清**：本轮 30~33 + 引用 8/9/10，加上前两轮的附件 14~29 与 5 个孤儿 `.bin`；按约束不清理，待人工确认后处置。
3. **本轮范围外的未验项**：双用户越权、并发绑定/解绑、TEMP 跨用户与过期时钟边界；`sales_order`/`sample` 两类来源仍因库内 0 单据无法正向验证。
4. **未提交**：`scripts/model-baseline.json` 的批准登记、本报告与 INDEX 改动仍在工作区（pre-push 门禁已绿）。


