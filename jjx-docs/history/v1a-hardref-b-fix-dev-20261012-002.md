# V1-A 历史附件硬引用修复（代码侧 B）

- 任务码：dev-20261012-002
- 日期：2026-10-12
- 范围：只做代码侧 B（防 ID 复用时被误认/误展示）；数据侧 A 只出提案与 SQL 草案
- 本轮未执行：UPDATE/DELETE、迁移、服务重启、清理测试附件或孤立文件、全量测试、Git commit/push
- 结论：B 已落代码并通过离线编译；19 条异常引用逐条归类完成（其中 8 条**可恢复**）；数据侧待拍板

## 1. 硬引用保护逻辑核查

`AttachmentAccessService.protectedFile(SysAttachment)` 是"受控/历史硬引用"的唯一判据，被 `checkUnlink`（解绑、旧删除、清理预演）与清理预演调用。判定分支：

| 分支 | 判据 | 本次是否改动 |
|---|---|---|
| 业务标记 | `is_controlled=1` 或 `released=1` 或 `is_current=1` | 未改 |
| 硬引用 1 | `product_work_spec_item` 中 `resource_type='DRAWING' AND source_id=附件id` | 未改（实测 0 引用、0 碰撞） |
| 硬引用 2 | `quality_template_registry.file_id=附件id` | **本次加"建档时间不晚于模板"条件** |
| 硬引用 3 | `engineering_film.file_id=附件id` | 未改（实测 0 引用、0 碰撞） |
| 硬引用 4 | `product.work_spec_json.$.structureFileId=附件id` | 未改 |
| 日志证据 | `trace_id` 非空 | 未改 |
| 正则证据 | `review_flow` / `sales_quotation_flow` / `sales_sample_round` / `sales_order_review` / `sales_customer` 文本里出现该 id（保守数字边界） | 未改 |

兄弟表碰撞核查（只读）：`product_work_spec_item(DRAWING)` 与 `engineering_film` 当前**引用数均为 0**，故其判定本轮无行为差异，也未加时间条件（避免在无证据的情况下改动保护语义）。

## 2. 代码侧 B 变更（2 文件）

1) `jjx-server/.../system/service/AttachmentAccessService.java`
   - 质量模板硬引用改为：仅当"该 `file_id` 指向的附件存在，且其建档时间 ≤ 模板建档时间"时才算硬引用：
     `(SELECT a2.create_time FROM sys_attachment a2 WHERE a2.id=q.file_id) <= q.create_time`
   - 子查询取不到值（附件缺失）→ 比较为 NULL → 不计入 → 不再误保护。
2) `jjx-server/.../production/service/impl/QualityTemplateRegistryServiceImpl.java`
   - `fillHasFile()` 由"`file_id != null` 即有文件"改为**有效性判定**：附件存在、`deleted=0`、且建档时间 ≤ 模板建档时间；无效则 **置空返回的 `fileId`** 并 `hasFile=false`。
   - 新增 `JdbcTemplate` 依赖（Lombok `@RequiredArgsConstructor` 注入）。

## 3. 时间条件的定位（明确声明）

时间条件**只是异常防护**：它挡住"历史 `file_id` 被后来新建的附件复用"这一类误认（本次 19 条即此成因）。
它**不构成附件身份证明**：时间匹配不等于内容一致；真正的身份一致需要绑定时的**内容指纹**（如 sha256）。
`quality_template_registry` 未存 sha256/file_path，所以即使通过时间校验，仍必须人工复核（代码注释中已同样声明，避免后来者把它当成身份校验）。

## 4. 未关闭保护（真实硬引用仍受保护）

- 四个硬引用分支、业务标记（受控/已发布/现行）、`trace_id`、五张文本证据表的正则判定**全部保留**，`checkUnlink` 的拒绝文案未变。
- 只读复算（同一 SQL，改前 vs 改后）：附件 12/13/14/20/25 由"算受控=1"变为 0 —— 这 5 个号位对应的模板引用经核对全部是坏引用（见 §6），不是真实历史引用；附件 2/3/4 前后均为 0，未受影响。
- 真实有效引用当前数量：`0` —— 也就是说本次改动**没有削弱任何一条真实保护**，只是摘掉了误判。

## 5. hasFile / 查看 / 下载路径核查

| 路径 | 位置 | 风险 | 处置 |
|---|---|---|---|
| `hasFile` 标记 | `QualityTemplateRegistryServiceImpl.fillHasFile`（page:46、detail:54 调用） | 原实现只看 `file_id != null` → 坏引用也显示"有文件" | 改为有效性判定（§2-2） |
| 质量模板列表"下载" | `jjx-web/src/views/production/quality-template/index.vue:48` → `attachmentApi.downloadUrl(row.fileId)` | 坏引用会拼出**错误文件**的下载链接 | 后端对无效引用置空 `fileId` → 前端 `v-if="row.fileId"` 自然不再渲染链接（**未改前端**） |
| 质量打印页 | `jjx-web/src/views/production/quality-print/index.vue:40,48` → 同上 | 同上（"已上传·打开"/"下载"） | 同上（该页以 `row.hasFile && row.fileId` 为条件，双重收敛） |
| 附件下载接口 | 后端 `GET /system/attachment/download/{id}` 与 `/refs/{refId}/download` | 只按 id 取文件，无法识别"id 是否真的属于该模板" | 本轮不动（保护由"绑定侧留痕"C 解决，见 §7 建议） |

结论：在数据侧未清理前，页面不会再因为坏 `file_id` 而给出错误下载入口；模板行将如实显示"未上传"。

## 6. 19 条异常引用逐条对账清单

判定口径：`文件缺失` = `file_id` 无对应附件行；`ID碰撞` = 附件存在但建档时间晚于模板建档时间；`仍有效` = 存在且建档时间 ≤ 模板建档时间。当前：**文件缺失 8 条 / ID碰撞 11 条 / 仍有效 0 条**。

| 模板ID | 记录编号 | 模板名 | file_id | 判定 | 现指向 | 可恢复性 |
|---|---|---|---|---|---|---|
| 37 | JJX-QR-037 | 进料检验报告 | 5 | 文件缺失 | — | **可能恢复**（备份有原行 id=5 `product/YL1025PWOO/客供稿/2026-10-10/YL-1025PWOO(YL-208ND)-客供稿.jpg`，磁盘文件存在） |
| 5 | JJX-QR-005 | 制造指令单 | 6 | 文件缺失 | — | **可能恢复**（备份原行 id=6 `product/YL1025PWOO/样品照片/2026-10-10/样品.jpg`，磁盘存在） |
| 30 | JJX-QR-030 | 工程变更通知（ECN） | 7 | 文件缺失 | — | **可能恢复**（id=7 `结构图/2026-10-10/工程图5 (1).jpg`，磁盘存在） |
| 24 | JJX-QR-024 | 采购订单 | 8 | 文件缺失 | — | **可能恢复**（id=8 `面板图/2026-10-10/工程图.jpg`，磁盘存在） |
| 65 | JJX-QR-065 | 样品需求单 | 9 | 文件缺失 | — | **可能恢复**（id=9 `线路图/2026-10-10/工程图2.jpg`，磁盘存在） |
| 71 | JJX-QR-071 | 变更记录表 | 10 | 文件缺失 | — | **可能恢复**（id=10 `刀模参数图/2026-10-10/工程图3.jpg`，磁盘存在） |
| 26 | JJX-QR-026 | 送货单 | 11 | 文件缺失 | — | **可能恢复**（id=11 `结构图/2026-10-10/工程图4.jpg`，磁盘存在） |
| 78 | JJX-QR-078 | 加班申请单 | 11 | 文件缺失 | — | 同上（与 QR-026 共用同一个 file_id 11，恢复后两条同时"有文件"，需人工确认归属） |
| 19 | JJX-QR-019 | 加工贴合治具明细表 | 12 | ID碰撞 | 客供稿.jpg（10-12 10:01:57 建档的**真实客供资料**） | 不可恢复；需重新上传模板文件或置空 |
| 39 | JJX-QR-039 | 成品检验报告 | 13 | ID碰撞 | 客供稿2.jpg（10-12 10:03:25 的真实客供资料） | 同上 |
| 90 | JJX-QR-090 | 出货查检表 | 14 | ID碰撞 | v1a-A.png（**验收测试附件**） | 同上（测试附件清理后会退回"文件缺失"） |
| 49 | JJX-QR-049 | 客户投诉一览表 | 15 | ID碰撞 | v1a-B.png（测试） | 同上 |
| 50 | JJX-QR-050 | 纠正及预防措施表 | 16 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 58 | JJX-QR-058 | QC日报表 | 17 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 59 | JJX-QR-059 | 特采联络书 | 18 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 48 | JJX-QR-048 | 合同记录一览表 | 19 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 35 | JJX-QR-035 | 生产设备保养记录表 | 20 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 54 | JJX-QR-054 | 内部异常联络单 | 22 | ID碰撞 | v1a-A.png（测试） | 同上 |
| 31 | JJX-QR-031 | 领料单 | 25 | ID碰撞 | v1a-B.png（测试） | 同上 |

“可能恢复”的证据（只读）：

```sh
# 做法：从 dump 里定位 `INSERT INTO \`sys_attachment\` … VALUES`，按括号切分元组、按当前列序取值（dump 无列清单），
# 取出 id=5..11 各行的 file_name/file_path，再核对磁盘存在性。（一次性脚本，未入库）
# 结果：两个快照均命中 7 行，file_name/file_path 完整，磁盘文件存在=True
ls upload/product/YL1025PWOO/结构图/2026-10-10/   # 工程图4.jpg / 工程图5 (1).jpg 仍在磁盘
```

即：这 8 条模板引用的**原件（DB 行 + 物理文件）都还在**，属于"可恢复"而非"彻底丢失"；11 条 ID 碰撞则必须重新上传或置空。

## 7. 数据侧 A：修复方案与 SQL 草案（未执行）

前置（按仓库规范）：① 人工全库备份（排除 `hr_employee`，放 `jjx-docs/sql/backups/`，只留最新一份）；② `quality_template_registry` 表级 guard 备份（批量 UPDATE 前必做）；③ 拍板后走独立迁移号（现值 273，下一号 274）。

方案 A1（推荐：先恢复、再收口）

```sql
-- (a) 从备份 dump 恢复被清掉的 7 行附件（id 5..11），字段/路径照 20261011-1930 快照写回；
--     物理文件已确认仍在 upload/product/YL1025PWOO/... 下，恢复后 QR-037/005/030/024/065/071/026/078 立即重新有效。
--     ⚠ 恢复脚本必须显式指定 id（含 AUTO_INCREMENT 影响面确认），并先核对 sys_attachment 当前 MAX(id) 以防撞号。
--     ⚠ 若产品/单据上下文已变化，需人工确认这 7 个文件当前仍应挂在该产品下。
-- (b) 只隔离剩余的 11 条 ID 碰撞（以及人工判定不再需要的行）：
UPDATE quality_template_registry q
   SET q.remark = CONCAT(COALESCE(q.remark,''), '｜[V1-A 2026-10-12] 原 file_id=', q.file_id,
                         ' 为失效引用（附件缺失或ID被后建附件复用），已隔离待重新上传'),
       q.file_id = NULL
 WHERE q.file_id IS NOT NULL
   AND (NOT EXISTS (SELECT 1 FROM sys_attachment a WHERE a.id=q.file_id)
        OR EXISTS (SELECT 1 FROM sys_attachment a WHERE a.id=q.file_id AND a.create_time > q.create_time));
-- (c) 回查（必须全 0）：
SELECT COUNT(*) FROM quality_template_registry q WHERE q.file_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_attachment a WHERE a.id=q.file_id AND a.deleted=0 AND a.create_time<=q.create_time);
```

方案 A2（只收口、不恢复）：跳过 (a)，直接执行 (b)(c) —— 19 条全部置空，模板显示"未上传"，由业务重新上传。

不采纳：删除质量模板行；直接改写 `file_id` 指向别的附件；修改历史附件 ID；删除测试附件当作"修好了"；关闭硬引用保护绕过误判。

方案 C（配套，建议后续单独立项）：`quality_template_registry` 增加 `file_sha256`（或 `file_path`）在绑定/换版时写入，校验"号位 + 内容指纹"双匹配；这是唯一能把"身份一致"从人工复核变成机器判定的做法——**仅靠时间条件不具备证明力**（§3）。

## 8. 最小验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 离线编译 | `mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true compile -q` | 通过（exit 0；未 clean，保留运行中 jar） |
| 判定翻转核对（只读） | 新旧口径同 SQL 对比（附件 2/3/4/12/13/14/20/25/30~33） | 12/13/14/20/25 由"算受控=1"→0；2/3/4 前后皆 0；30~33 皆 0 |
| 真实保护未被削弱 | `SELECT COUNT(*) … a.create_time<=q.create_time`（有效模板引用） | 0 —— 无真实引用被误伤 |
| 兄弟表不误伤 | `product_work_spec_item(DRAWING)` / `engineering_film` 碰撞数 | 均为 0（故未改其判定） |
| 19 条归类 | 对账 SQL | 文件缺失 8 / ID碰撞 11 / 仍有效 0 |
| 可恢复证据 | 备份 dump 提取 + 磁盘存在性 | 7 个 id 原行命中、磁盘文件全部存在 |
| 未跑 | 全量测试、迁移、重启、UPDATE/DELETE、清理、commit/push | 按约束不执行 |

首次静态核对曾发现我把时间比较方向写反（`>=` → 判定无变化），已改为 `<=` 并重新编译 + 复核（上方"判定翻转核对"即修正后的结果）。

## 9. 待人工确认项

1. **A1 还是 A2**：先恢复 5~11 原件（原件与磁盘文件都在）再只隔离 11 条，还是直接把 19 条全部置空？
2. **QR-026 与 QR-078 共用 file_id=11**：恢复后两条会同时"有文件"，需业务确认真实归属（是否应各自一份）。
3. **QR-019 / QR-039 与客供资料 12/13 的碰撞**：这两条模板现在指向 10-10 的真实客供图纸，恢复模板文件前建议先隔离，避免误展示。
4. **方案 C 是否立项**（绑定侧留痕 sha256）。
5. 测试附件（14~29、30~33）与 5 个孤儿 `.bin` 的清理时点——建议与本次隔离**同批**执行，避免先清测试附件导致 9 条碰撞行再退回"文件缺失"、二次核对。

