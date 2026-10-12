# V1-A 历史附件数据修复前核查报告（只读）

- 任务码：dev-20261012-002
- 日期：2026-10-12
- 方式：**只读**（备份文件解析 + SELECT）。未执行 INSERT/UPDATE/DELETE、未迁移、未清理文件、未重启服务、未 commit
- 结论先说：① 附件 5~11 是**真实的产品工程/客供文件**（非质量模板文件），原件与磁盘文件都在，可恢复；② 19 条模板 `file_id` **从来没有指向模板文件**——建议全部置空，模板文件由业务重新上传；③ 恢复附件行必须同时修正 `biz_id`（product id 已漂移）

## 1. 附件 ID 5~11 的完整原始记录

来源：最新删除前快照 `jjx-docs/sql/backups/jjx_erp_db_backup_20261011-1930.sql`（列名映射解析；10-10 快照字段一致，仅 id=6 当时还是 `deleted=0`）。

| id | file_name | file_path | biz_type | biz_id | category | create_time | file_size | 快照 deleted | 内容指纹 |
|---|---|---|---|---|---|---|---|---|---|
| 5 | YL-1025PWOO(YL-208ND)-客供稿.jpg | product/YL1025PWOO/客供稿/2026-10-10/… | product | 1 | 客供稿 | 2026-10-10 11:36:17 | 415785 | 0 | 磁盘 sha256 `36abb933…ae8c1`（size 一致） |
| 6 | 样品.jpg | product/YL1025PWOO/样品照片/2026-10-10/… | product | 1 | 样品照片 | 2026-10-10 12:19:29 | 216201 | **1**（10-11 前已软删） | 磁盘 sha256 `a51f5fd5…0f88de` |
| 7 | 工程图5 (1).jpg | product/YL1025PWOO/结构图/2026-10-10/… | product | 1 | 结构图 | 2026-10-10 15:39:43 | 134215 | 0 | 磁盘 sha256 `398d9f74…dacabe` |
| 8 | 工程图.jpg | product/YL1025PWOO/面板图/2026-10-10/… | product | 1 | 面板图 | 2026-10-10 17:49:50 | 197826 | 0 | 磁盘 sha256 `77817e58…5d47f1` |
| 9 | 工程图2.jpg | product/YL1025PWOO/线路图/2026-10-10/… | product | 1 | 线路图 | 2026-10-10 17:49:50 | 112320 | 0 | 磁盘 sha256 `fd919aaf…c083b7` |
| 10 | 工程图3.jpg | product/YL1025PWOO/刀模参数图/2026-10-10/… | product | 1 | 刀模参数图 | 2026-10-10 17:49:50 | 140087 | 0 | 磁盘 sha256 `c359fdd5…c4bb2be` |
| 11 | 工程图4.jpg | product/YL1025PWOO/结构图/2026-10-10/… | product | 1 | 结构图 | 2026-10-10 17:49:50 | 131639 | 0 | 磁盘 sha256 `1b4928c9…3efae2` |

内容指纹的可信度说明：**备份里没有历史指纹**——10-10 快照连 `sha256` 列都还没有（25 列），10-11 快照有该列但 7 行全为 `NULL`（264 迁移已建列、未回填）。当前可用的指纹只有两条：① 快照 `file_size` 与磁盘文件字节数 **7/7 完全一致**；② 我按磁盘文件现算的 sha256（上表）。它们能证明"磁盘上这个文件就是当时那个文件"，但**不是**入库时记录的指纹。

## 2. 真实业务归属判定

- 7 行的 `biz_type` 全部是 `product`，`category` 为客供稿/样品照片/结构图/面板图/线路图/刀模参数图 → 是**产品工程与客供资料**，不是质量记录模板（QR）文件。
- 路径与产品编码一致：`upload/product/YL1025PWOO/...`；当时产品表只有 1 条：`product_id=1 = YL1025PWOO`（10-10 10:02:50 建），所以快照里的 `biz_id=1` 是**当时正确**的归属。
- **产品 ID 已漂移**：当前 `product` 表为 10-12 重建的 3 条 —— `1=YL001MEOO`(10-12 10:01:57)、`2=YL002MEOO`(10-12 10:03:25)、`3=YL1025PWOO`(10-12 10:21:03)。即老 `biz_id=1` 现在指向**另一个产品**；恢复时必须把 `biz_id` 改成当前 `YL1025PWOO` 的 `product_id=3`（或按 `product_code` 重新定位），否则文件会挂到 YL001MEOO 名下。

## 3. 8 条质量模板引用：是否有足够证据恢复

判定依据（本轮的**决定性证据**）：质量模板文件在上传时走 `AttachmentUploader biz-type="quality_template" :biz-id="模板id"`（`jjx-web/src/views/production/quality-template/index.vue:87`），因此真正的模板文件应表现为 `sys_attachment.biz_type='quality_template'`。

```
三份全库快照里 biz_type='quality_template' 的附件行数：20261010-1932 = 0，20261011-1930 = 0，20261012-1028 = 0
```

结论：**这 8 条（乃至全部 19 条）文件引用都没有可恢复的模板文件**——`file_id` 5~11 在当时指向的是产品工程图（§1），不是模板文件；模板文件从未存在过。

**这里更正我上一轮的措辞**：上一轮把 8 条写成"可能恢复"，理由是"原件与磁盘文件都在"。那是把"能恢复**附件行**"错当成"能恢复**模板引用**"。准确说法是：附件行可恢复（对产品文件库有意义），但**模板引用不可恢复**——恢复附件行后，模板仍会指向一份产品工程图，内容依旧不对。

| 模板ID | 记录编号 | 模板名 | file_id | 当时该 id 的真实内容 | 是否有模板文件可恢复 |
|---|---|---|---|---|---|
| 37 | JJX-QR-037 | 进料检验报告 | 5 | 产品客供稿 jpg | **无** |
| 5 | JJX-QR-005 | 制造指令单 | 6 | 产品样品照片 jpg | **无** |
| 30 | JJX-QR-030 | ECN | 7 | 产品结构图 jpg | **无** |
| 24 | JJX-QR-024 | 采购订单 | 8 | 产品面板图 jpg | **无** |
| 65 | JJX-QR-065 | 样品需求单 | 9 | 产品线路图 jpg | **无** |
| 71 | JJX-QR-071 | 变更记录表 | 10 | 产品刀模参数图 jpg | **无** |
| 26 | JJX-QR-026 | 送货单 | 11 | 产品结构图 jpg | **无** |
| 78 | JJX-QR-078 | 加班申请单 | 11 | 同上一行（共用 id） | **无** |

## 4. QR-026 与 QR-078 共用 file_id=11 的原因

19 条模板全部建于同一秒 `2026-08-28 18:00:47`，`create_by` 为 NULL（批量 seed 特征），`file_id` 呈连续号段：`5,6,7,8,9,10,11,11,12,13,14,15,16,17,18,19,20,22,25`。

- 即当时按"行序 + 偏移"批量填了一个**占位号段**，第 8 行与第 7 行都填了 `11`（号段重复），后续又缺 `21/23/24`。
- 两条模板的 `biz_type` 互不相同（QR-026 = `sales_delivery`，QR-078 为 NULL），没有任何"共用同一份表"的业务含义。
- 结论：**是 seed 脚本的占位重复，不是真实的共享文件**；两条都应各自置空、各自重新上传。其余 17 条同理。

## 5. 两个独立决策（必须分开定）

| 决策 | 对象 | 证据 | 我的建议 |
|---|---|---|---|
| **决策 1：恢复 `sys_attachment` 原始行** | 附件 5~11（7 行） | 快照有完整字段、磁盘文件 7/7 存在且字节一致、业务归属清晰（product/YL1025PWOO） | 若产品 YL1025PWOO 的工程/客供文件仍需保留 → **恢复**，但 `biz_id` 必须改为当前 `product_id=3`，并把 id=6 按快照保持 `deleted=1` |
| **决策 2：恢复 `quality_template_registry.file_id`** | 19 条模板引用 | 任何快照都不存在 `biz_type='quality_template'` 的附件；`file_id` 是张冠李戴的占位号段 | **不恢复，全部置空**（含 8 条"缺失"与 11 条"碰撞"），模板文件由业务重新上传 |

决策 1 不依赖决策 2，反之亦然：恢复附件行**不会**让这 19 条模板变成有效引用（新代码 B 的时间防护仍判其为无效，`hasFile` 仍为 false）。

## 6. 建议清单

### 6.1 建议恢复的附件记录（决策 1）

| id | file_name | category | version/drawing_no/file_role | file_size | create_time | deleted | is_controlled/released/is_current |
|---|---|---|---|---|---|---|---|
| 5 | YL-1025PWOO(YL-208ND)-客供稿.jpg | 客供稿 | — | 415785 | 2026-10-10 11:36:17 | 0 | 0/0/0 |
| 6 | 样品.jpg | 样品照片 | — | 216201 | 2026-10-10 12:19:29 | **1** | 0/0/0 |
| 7 | 工程图5 (1).jpg | 结构图 | A / 工程图5 (1) / PRINT | 134215 | 2026-10-10 15:39:43 | 0 | **1/1/1**（released_at 10-10 15:40:08, released_by=1） |
| 8 | 工程图.jpg | 面板图 | A / 工程图 / PRINT | 197826 | 2026-10-10 17:49:50 | 0 | **1/1/1** |
| 9 | 工程图2.jpg | 线路图 | A / 工程图2 / PRINT | 112320 | 2026-10-10 17:49:50 | 0 | **1/1/1** |
| 10 | 工程图3.jpg | 刀模参数图 | A / 工程图3 / PRINT | 140087 | 2026-10-10 17:49:50 | 0 | **1/1/1** |
| 11 | 工程图4.jpg | 结构图 | A / 工程图4 / PRINT | 131639 | 2026-10-10 17:49:50 | 0 | **1/1/1** |

要点：① `biz_id` 由快照的 1 改为**当前** `YL1025PWOO` 的 `product_id=3`；② id=6 按快照保持 `deleted=1`（不静默复活已删文件）；③ 7~11 在快照里就是"已下发/现行受控图纸"，是否照旧恢复成现行版本需拍板（详见 §9）。

### 6.2 建议置空的质量模板引用（决策 2，19 条）

```
id=37 (JJX-QR-037, file_id=5)   id=5  (JJX-QR-005, 6)   id=30 (JJX-QR-030, 7)   id=24 (JJX-QR-024, 8)
id=65 (JJX-QR-065, 9)           id=71 (JJX-QR-071, 10)  id=26 (JJX-QR-026, 11)  id=78 (JJX-QR-078, 11)
id=19 (JJX-QR-019, 12)          id=39 (JJX-QR-039, 13)  id=90 (JJX-QR-090, 14)  id=49 (JJX-QR-049, 15)
id=50 (JJX-QR-050, 16)          id=58 (JJX-QR-058, 17)  id=59 (JJX-QR-059, 18)  id=48 (JJX-QR-048, 19)
id=35 (JJX-QR-035, 20)          id=54 (JJX-QR-054, 22)  id=31 (JJX-QR-031, 25)
```

置空后模板 `hasFile=false`、页面显示"未上传"，由业务通过"上传模板"（`biz_type='quality_template'`）重新建立正确引用。

## 7. 274 数据迁移设计草案（未执行）

前置：① 人工全库备份（排除 `hr_employee`，只留最新一份）；② 表级 guard 备份 `quality_template_registry_stale_file_refs_YYYYMMDD-HHmm.sql`；③ 迁移号 274（现值 273）；④ 两个决策各自独立成文件/独立执行（274a 模板、274b 附件），不要混在一起。

```sql
-- ============ 274a_quality_template_stale_file_refs.sql（决策 2）============
-- (0) 执行前校验：19 条的 (id,file_id) 必须逐行精确匹配，否则中止
SELECT COUNT(*) FROM quality_template_registry WHERE (id,file_id) IN
 ((37,5),(5,6),(30,7),(24,8),(65,9),(71,10),(26,11),(78,11),(19,12),(39,13),
  (90,14),(49,15),(50,16),(58,17),(59,18),(48,19),(35,20),(54,22),(31,25));   -- 期望 19
SELECT COUNT(*) FROM quality_template_registry;                                -- 期望 100（行数不变）

-- (1) 逐行精确更新：每条都带 id + 原 file_id 双条件（不用宽泛 WHERE）
UPDATE quality_template_registry SET file_id=NULL,
       remark=CONCAT(COALESCE(remark,''),'｜[V1-A 2026-10-12 dev-20261012-002] 原 file_id=5 为失效占位引用，模板文件需重新上传')
 WHERE id=37 AND file_id=5;
-- …其余 18 条同型（id=5/file_id=6、30/7、24/8、65/9、71/10、26/11、78/11、19/12、39/13、
--   90/14、49/15、50/16、58/17、59/18、48/19、35/20、54/22、31/25）

-- (2) 执行后核对（必须全 0 / 100）
SELECT COUNT(*) FROM quality_template_registry WHERE id IN
 (37,5,30,24,65,71,26,78,19,39,90,49,50,58,59,48,35,54,31) AND file_id IS NOT NULL;   -- 0
SELECT COUNT(*) FROM quality_template_registry WHERE file_id IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM sys_attachment a WHERE a.id=file_id AND a.deleted=0
                  AND a.create_time<=quality_template_registry.create_time);           -- 0
SELECT COUNT(*) FROM quality_template_registry;                                         -- 100
```

```sql
-- ============ 274b_restore_attachment_rows.sql（决策 1，仅在拍板"恢复"后）============
-- (0) 执行前校验：id 5~11 必须全部空闲；磁盘文件必须存在
SELECT COUNT(*) FROM sys_attachment WHERE id IN (5,6,7,8,9,10,11);                       -- 期望 0
SELECT MAX(id) FROM sys_attachment;                                                      -- 期望 33（不高于 11，插入无撞号）
SELECT product_id FROM product WHERE product_code='YL1025PWOO';                           -- 期望 3（biz_id 目标）

-- (1) 逐行显式 INSERT（显式列 + 显式 id；biz_id 按当前 product_id=3）
INSERT INTO sys_attachment (id,biz_type,biz_id,category,file_name,file_path,file_size,file_type,
                            sort_order,remark,create_by,create_time,deleted,is_current,is_controlled,released)
VALUES (5,'product',3,'客供稿','YL-1025PWOO(YL-208ND)-客供稿.jpg',
        'product/YL1025PWOO/客供稿/2026-10-10/YL-1025PWOO(YL-208ND)-客供稿.jpg',415785,'image/jpeg',0,'','admin','2026-10-10 11:36:17',0,0,0,0);
INSERT INTO sys_attachment (id,biz_type,biz_id,category,file_name,file_path,file_size,file_type,
                            sort_order,remark,create_by,create_time,deleted,is_current,is_controlled,released)
VALUES (6,'product',3,'样品照片','样品.jpg','product/YL1025PWOO/样品照片/2026-10-10/样品.jpg',216201,'image/jpeg',0,'','admin','2026-10-10 12:19:29',1,0,0,0);
-- …id=7~11 同型（category/version/drawing_no/drawing_name/file_role/file_size/create_time 照快照；
--   7~11 的 is_current/is_controlled/released 取值见 §9-1 拍板结果）

-- (2) 执行后核对
SELECT id,file_size,deleted,is_current,is_controlled,released FROM sys_attachment WHERE id IN (5,6,7,8,9,10,11);
SELECT COUNT(*) FROM sys_attachment;                          -- 24 + 7 = 31
SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_name='sys_attachment';  -- 估算值，仅参考；以 MAX(id)=33 为准
-- 对每个恢复行核对磁盘：size = file_size 且 sha256 = §1 表内记录
```

不含宽泛 UPDATE：274a 每条都带 `id + 原 file_id`，274b 每条都是显式 id 的 INSERT，均不做"按条件批量改"。

## 8. 恢复原 ID 的影响面核查

| 项 | 核查结果 |
|---|---|
| `AUTO_INCREMENT` | 现有 `MAX(id)=33`（`information_schema` 报 30，是**估算值**，不作准）。显式插入 5~11 不改变计数器（MySQL 取 `max(计数器, 最大id+1)`），下次自增仍 ≥34 → 不会撞号 |
| 引用表 `sys_attachment_ref` | 现无任何行指向 5~11（实测 0 行）；外键 `fk_attachment_ref_file` 是 `RESTRICT`，只约束删除、不拦插入。恢复**不会**自动产生引用行（引用仍须单独建立） |
| 其它硬引用 | `product_work_spec_item(DRAWING)`=0、`engineering_film`=0、`product.work_spec_json.structureFileId`=0 → 恢复后不会新增"受控"判定来源 |
| 受控标记 | 7~11 自带 `is_controlled/released/is_current=1`（快照事实）→ 恢复后它们本身就是受控现行图纸，属**真实**保护，不是误判 |
| 业务权限/可见性 | product 范围读需 `product:view`；`getProductFiles(productCode)` 按 `biz_type='product' AND biz_id=productId` 过滤（`SysAttachmentServiceImpl.getProductFiles`）→ `biz_id` 必须为 3，否则文件会挂到 YL001MEOO 名下 |
| 清理资格 | 恢复行若沿用快照 `upload_state` 为空/LEGACY → 预演按历史保守处理（`LEGACY_REVIEW_REQUIRED`，不自动清理） |
| 触发器 | `trg_attachment_v1_no_delete` 是 `BEFORE DELETE`，只拦删除，不影响 INSERT |
| 门禁 | 不加表/列 → `check:model-baseline` 不受影响；273 已使 `biz_type/biz_id` 可空，恢复时即使留空也不报错（但会失去产品文件库归属） |

## 9. 待人工确认的最终清单

1. **决策 1**：是否恢复附件 5~11 行？若恢复，7~11 的 `is_current/released` 按快照恢复成"现行受控图纸"，还是降为历史文件（0）？
2. **决策 2**：是否按建议把 19 条模板 `file_id` 全部置空（含 8 条"缺失"）？
3. **id=6（样品.jpg）**：按快照恢复为 `deleted=1`（进回收站）还是不要这一行？
4. **QR-026/078 共用 file_id=11**：确认是 seed 占位重复（本轮结论），两条各自重新上传。
5. **是否立项方案 C**（`quality_template_registry` 增加 `file_sha256`，绑定/换版时留痕，把"身份一致"从人工复核变成机器判定）。
6. **产品 3 的图纸引用**：当前 `product_work_spec_item(DRAWING)`=0，若恢复 7~11，是否需要同步建立规范/结构图引用？
7. **测试附件与孤儿文件清理时点**：建议与 274a 同批执行。

## 附录：证据命令（全部只读）

```sh
# 1) 从快照取 5~11 全字段（按 dump 自身 CREATE TABLE 列名映射，避免列序错位）
python3 /tmp/extract-attach3.py jjx-docs/sql/backups/jjx_erp_db_backup_20261011-1930.sql 5,6,7,8,9,10,11
python3 /tmp/extract-attach3.py jjx-docs/sql/backups/jjx_erp_db_backup_20261010-1932.sql 5,6,7,8,9,10,11
python3 /tmp/dump-row.py    jjx-docs/sql/backups/jjx_erp_db_backup_20261011-1930.sql 5,6,7,8,9,10,11
# 2) 快照全部附件行（证明无 biz_type='quality_template'）
for f in 20261010-1932 20261011-1930 20261012-1028; do python3 /tmp/list-attach.py jjx-docs/sql/backups/jjx_erp_db_backup_$f.sql; done
# 3) 当时/当前 product id↔code
python3 /tmp/extract-product.py jjx-docs/sql/backups/jjx_erp_db_backup_20261011-1930.sql
mysql -uroot jjx_erp_db -e "SELECT product_id,product_code,create_time FROM product ORDER BY product_id;"
# 4) 现库对 5~11 的引用面 + MAX(id)
mysql -uroot jjx_erp_db -e "SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id IN (5,6,7,8,9,10,11); SELECT MAX(id) FROM sys_attachment;"
# 5) 模板文件上传通道
grep -n AttachmentUploader jjx-web/src/views/production/quality-template/index.vue
```


