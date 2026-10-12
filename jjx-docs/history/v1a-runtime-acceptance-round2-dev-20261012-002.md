# 公共附件 V1-A 运行时验收报告（第二轮·补充）

- 任务码：dev-20261012-002
- 日期：2026-10-12（承接 dev-20261012-002 的首轮报告 `v1a-runtime-acceptance-dev-20261012-002.md`）
- 前置确认：后端在跑，且运行版本含 V1-A 代码（证据见 §1）
- 本轮范围：只补验 7 项运行时功能；17 条质量模板历史断链保留为独立遗留，不在本轮修复
- 结论：**NO-GO**（新增 1 个明确缺陷：临时上传接口 100% 失败）

## 1. 运行版本确认（含 V1-A 代码）

```sh
ps -eo pid,lstart,cmd | grep '[j]ava' | grep -v vscode
# 3698304 Mon Oct 12 11:02:41 2026 java -jar /home/administrator/jjx/jjx-server/target/jjx-server-1.0.0.jar
ls -l --time-style=+%F_%H:%M jjx-server/target/jjx-server-1.0.0.jar   # 2026-10-12 11:02
ls jjx-server/target/classes/com/jjx/system/service/ | grep -E 'Attachment(Lifecycle|Access|SourcePolicy)'
ss -ltn | grep 8080      # LISTEN 0.0.0.0:8080
```

- 进程启动时间 11:02:41 > jar 构建时间 11:02 → 跑的是含 V1-A 的构建；
- 运行日志已出现 V1-A 专有文案（`无权查看附件或历史来源尚未确认`、`附件清理预演 actor=…`）→ 代码确实生效。

## 2. 本轮逐项判定

| # | 验收项 | 判定 |
|---|---|---|
| 1 | 临时上传、批量上传、绑定、解绑、跨业务复用 | **FAIL**（临时上传缺陷；其余子项 PASS） |
| 2 | SHA-256 重复检测 | PASS |
| 3 | 分类、展示名、备注及来源过滤 | PASS |
| 4 | 五类业务来源的附件权限 | PASS（权限强制）/ BLOCKED（sales_order、sample 无数据，无法正向验证） |
| 5 | 已锁定单据禁止修改附件 | PASS |
| 6 | 旧列表、下载、删除接口兼容 | PASS |
| 7 | 临时 7 天、解绑 30 天保留期接口 | **FAIL**（两条分支均不可达） |

测试口径：全部使用**隔离测试数据**（新建附件/引用），历史附件 12/13 与其引用 1/2 仅被读取；未启停服务、未执行迁移、未做任何物理清理。

## 3. 第 1 项 FAIL：临时上传接口不可用

```
POST /system/attachment/refs/temp  (file=v1a-A.png, requestKey=v1a-test-key-0001)
-> HTTP 200 / body.code=500
   Field 'biz_type' doesn't have a default value
   ### INSERT INTO sys_attachment ( file_name,file_path,file_size,file_type,sha256,upload_state,expires_at,upload_key,upload_owner,create_by,create_time,deleted )
```

- 根因：`sys_attachment.biz_type` / `biz_id` 是 `NOT NULL` 且**无默认值**（271 未放开），而 `AttachmentLifecycleService.upload()`（AttachmentLifecycleService.java:77-82）构造的 TEMP 行不含这两列 → 插入必然失败。**每一次临时上传都失败**，与文件内容/键无关（换文件、换 key 结果相同）。
- 副作用：事务回滚但**磁盘文件已落盘**，`upload/attachment-temp/` 残留 5 个 `.bin`（各 70B，时间戳 11:34）；日志有对应告警：
  `WARN AttachmentLifecycleService - 临时上传事务回滚，孤立文件仅登记待人工核查，不自动删除：…/56df2853-….bin`
- 连带影响：**依赖 TEMP 的路径全部不可用**——临时上传、TEMP→BOUND 绑定链、以及第 7 项的「临时 7 天」保留期分支（详见 §8）。
- 其余子项 PASS（用旧上传接口新建的隔离附件 24/26 验证）：

```
POST /system/attachment/batch-upload (2 文件, bizType=inquiry, bizId=1) -> ids=[16..21] / [24,25]   PASS 批量上传
POST /system/attachment/refs {attachmentId:24, sourceType:inquiry,   sourceId:1} -> refId=4
POST /system/attachment/refs {attachmentId:24, sourceType:inquiry,   sourceId:1} -> refId=4          PASS 幂等（同附件同来源同产品返回原 refId）
POST /system/attachment/refs {attachmentId:24, sourceType:quotation, sourceId:2} -> refId=5          PASS 跨业务复用（一份文件两个业务）
POST /system/attachment/refs {attachmentId:24, sourceType:product,   sourceId:1} -> refId=6
DELETE /system/attachment/refs/5 -> code=200                                                          PASS 解绑
DELETE /system/attachment/refs/5 -> code=200（重复解绑不报错、不移动保留期时钟）                      PASS 解绑幂等
```

DB 复核：`sys_attachment_ref` 生成 id 4/5/6（createBy=1），解绑后 ref 4/5 `deleted=1` + `deleted_at` 落值，ref 6 仍 active；附件 24 行的 `file_path`/`sha256` 未被改写。

## 4. 第 2 项 PASS：SHA-256 重复检测（不去重）

```
GET /system/attachment/by-sha256?sha256=<A>&bizType=inquiry&bizId=1     -> 返回同范围同内容附件（含新建件）
POST /system/attachment/upload (A.png, inquiry:1)  -> 响应头 X-Attachment-Duplicate-Count: 8
POST /system/attachment/batch-upload (A.png ×2)    -> 响应头 X-Attachment-Duplicate-Count: 20
GET /system/attachment/refs/by-sha256?sha256=<A>&sourceType=inquiry&sourceId=1  -> [{"refId":4,"attachmentId":24,…}]
GET /system/attachment/refs/by-sha256?sha256=<A>&sourceType=quotation&sourceId=2 -> [{"refId":5,"attachmentId":24,…}]
GET /system/attachment/refs/by-sha256?sha256=nothex&…  -> code=500 "SHA-256必须为64位十六进制"
```

- 按范围命中：同一附件 24 在 inquiry:1 / quotation:2 两个范围各返回一条引用，**不跨来源泄漏**。
- **不做物理去重**（DB 证据）：`a4bcd7b8…` 共 9 个附件行、`COUNT(DISTINCT file_path)=9`；`f9789207…` 3 行 / 3 路径；磁盘 `upload/inquiry/2026-10-12/` 下文件各自独立存在。

## 5. 第 3 项 PASS：分类、展示名、备注、来源过滤

```
GET /system/attachment/refs/document-types
-> [{attachment_category, general, 通用资料}, {attachment_document_type, general_document, 一般资料,
    ext_data={"parent":"general","sources":["product","inquiry","quotation","sales_order","sample","purchase_order","production_order","material"]}}]

PUT /system/attachment/refs/4 {"documentType":"general_document","displayName":"V1A运行验收-通用资料","remark":"隔离测试数据","sortOrder":2}
-> code=200；回读 GET /refs?sourceType=inquiry&sourceId=1 得到 documentType/displayName/remark/sortOrder 全部落值，updateBy=1

PUT /system/attachment/refs/4 {"documentType":"not_in_dict"} -> code=500 "资料类型不存在或已停用"   PASS 二级类型校验
PUT /system/attachment/refs/4 {"sortOrder":-1}                -> code=400 "must be greater than or equal to 0" PASS 排序校验

GET /refs?sourceType=inquiry&sourceId=1   -> 只返回 ref 1、4（均为 inquiry:1）
GET /refs?sourceType=quotation&sourceId=2 -> 只返回 ref 2、5（均为 quotation:2）
GET /refs/product/1                       -> 只返回 ref 6
```

- 来源过滤按 `source_type + source_id` 精确生效，未见跨来源串数；`displayName` 与「客供资料整理名」是两套字段，未互相覆盖。

## 6. 第 4 项：权限强制 PASS / 五类来源正向 BLOCKED

正向（有数据的来源）：

```
inquiry:id=1     -> 绑定成功（ref 4）
quotation:id=2   -> 绑定成功（ref 5）
product:id=1     -> 绑定成功（ref 6）；GET /refs/product/1 返回该引用
```

负向（强制生效，全部来自真实调用）：

```
未确认来源 inbound        -> code=500 "附件来源权限映射未确认，禁止操作：inbound"    PASS 白名单 fail-closed
别名 order（归一 sales_order）  -> code=500 "业务来源不存在或不唯一"                 PASS 归一后走来源存在性校验
别名 sample_order（归一 sample）-> code=500 "业务来源不存在或不唯一"                 PASS 同上
非管理员 yuanhui 读 inquiry 引用 -> code=500 "无此权限：sales:inquiry:view"            PASS 来源级读权限
非管理员 yuanhui 读 product 引用 -> code=500 "无此权限：product:view"                  PASS 来源级读权限
非管理员 yuanhui 写 purchase_order -> code=500 "业务来源不存在或不唯一"               PASS 权限通过后仍校验来源存在
```

BLOCKED 部分：`sales_order`、`sample` 两类在当前库内**单据行数为 0**（`sales_order` 0、`sales_sample_order` 0），无法做正向绑定验证；这与代码缺陷无关，属数据限制。按「不得把未验证项记为通过」，本项只给 PASS（权限强制）+ BLOCKED（销售订单/样品正向）。

## 7. 第 5 项 PASS：受控/历史硬引用附件禁止改动

选一个**确实被硬引用**的历史附件做负向测试（无副作用：预期被拒）：

```sql
-- 附件 12（历史客供图纸）被 quality_template_registry 引用 → protectedFile(12)=true
SELECT COUNT(*) FROM quality_template_registry WHERE file_id=12;   -- 1
```

```
DELETE /system/attachment/refs/1
-> code=500 "受控、已发布或历史硬引用附件禁止解除；需由原业务流程处理"     PASS
DELETE /system/attachment/14   （旧删除入口，14 命中悬挂 registry file_id）
-> code=500 "受控、已发布或历史硬引用附件禁止解除；需由原业务流程处理"     PASS 新旧入口同一 guard
```

副作用核查（DB）：`sys_attachment_ref` id=1 仍 `deleted=0`；附件 12 仍 `deleted=0, upload_state=LEGACY, expires_at=NULL` → 被拒的调用**没有产生任何变更**。

⚠ 同轮发现的次生现象（记录，不在本轮修）：quality_template_registry 的 17 条悬挂 `file_id` 会**把新附件误判为"受控/历史硬引用"**——本轮新建的附件 14~20、22、25 一建出来就在清理预演里被判 `CONTROLLED_OR_HISTORICAL_REFERENCE`（因为新行 id 恰好等于那批悬挂 file_id），旧删除入口对它们一律拒绝。这既是遗留断链的副作用放大，也说明「按 id 匹配的硬引用」在 id 复用场景下会误伤。

## 8. 第 6 项 PASS：旧列表 / 下载 / 删除接口兼容

```
GET  /system/attachment/list?bizType=inquiry&bizId=1
-> 返回该业务附件（含历史客供资料 id12 的 customerDocType/displayName 元数据）        PASS 旧列表
GET  /system/attachment/download/24    -> http=200 bytes=70（真实字节）                 PASS 自有附件可下载
GET  /system/attachment/download/2     -> code=500 "无权查看附件或历史来源尚未确认"      PASS 未确认来源的历史 icon 被拒（V1-A 明确的设计变更）
GET  /system/attachment/refs/4/download -> http=200 bytes=70 Content-Type: application/octet-stream
GET  /system/attachment/refs/4/preview  -> http=200 bytes=70 Content-Type: image/png     PASS 预览白名单 inline
DELETE /system/attachment/24（旧删除）  -> code=200；DB：唯一匹配引用 ref4 被解绑（deleted=1），附件行仍 deleted=0   PASS 旧删除=解绑指定引用，不删行
DELETE /system/attachment/permanent/21   -> code=500 "V1-A仅允许清理预演；物理删除已禁用，请使用refs/cleanup-preview"
POST   /system/attachment/permanent-expired?days=0 -> code=500 "V1-A仅允许清理预演；旧过期清理入口已禁用"
```

- 旧删除在「多个引用上下文」时应报 `附件具有多个引用上下文，请使用refId解除指定引用`（代码 `SysAttachmentServiceImpl.deleteAttachment` 分支），本轮用唯一上下文触发了解绑路径，多上下文分支未单独触发。
- 附件 21 走旧删除后 `deleted=1`、`expires_at=now+30d`，**磁盘文件仍在**（`upload/inquiry/2026-10-12/7d91ba15-….png` 存在）→ 逻辑删除，非物理删除。

## 9. 第 7 项 FAIL：保留期两条分支均不可达

接口本身可用（`POST /system/attachment/refs/cleanup-preview`，admin 角色），日志有 38 条 `附件清理预演 actor=… reason=… deadline=… physicalDelete=false` 审计行：

```
2/3/4   LEGACY_REVIEW_REQUIRED None  eligible=False physicalDelete=False
12/13   ACTIVE_REFERENCE       None  eligible=False physicalDelete=False
14~20,22,25 CONTROLLED_OR_HISTORICAL_REFERENCE None eligible=False physicalDelete=False
21      LEGACY_REVIEW_REQUIRED None  eligible=False physicalDelete=False
24      ACTIVE_REFERENCE       None  eligible=False physicalDelete=False
```

- 「临时 7 天」分支：**完全不可达**——只有 TEMP 行才走 `create_time+7d`，而 TEMP 行无法创建（§3 缺陷）。
- 「解绑 30 天」分支：解绑**确实**写入 `expires_at=now+30d`（附件 21 走旧删除、附件 26 解绑后实测 `expires_at=2026-11-11 11:35:50`），但：
  1. `upload_state` 只在「原状态非 LEGACY」时才写 `ORPHAN`；经旧上传接口建档的行一律是 LEGACY → 永不进 ORPHAN；
  2. 预演里 `LEGACY_REVIEW_REQUIRED` 分支优先（条件含 `biz_type != null`，旧行都有 biz_type）→ **deadline 一律显示 None**，30 天期限不上屏。
- 结论：保留期计算代码存在且写入正确，但「7 天 / 30 天」作为**可观测的清理资格**在现有数据与代码组合下都取不到，不能记 PASS。

## 10. GO / NO-GO

**NO-GO。** 本轮新增 1 个必须修的缺陷，另有 1 项数据限制：

1. **[必修] 临时上传接口不可用**：`sys_attachment.biz_type`/`biz_id` NOT NULL 无默认值 vs `AttachmentLifecycleService.upload()` 不含这两列 → 每次调用都 500，并产生孤立文件。修法二选一（需拍板）：
   A) 迁移放开 `biz_type`/`biz_id` 为可空（TEMP 阶段无业务来源，语义正确）；或
   B) 代码在 TEMP 写入时显式落一个占位值（语义更差，不推荐）。
   修完需重启后端（服务生命周期由用户执行）。
2. **[数据限制] sales_order / sample 无单据**：无法正向验证这两类来源的绑定/权限，需造测试单据后再补验，否则第 4 项只能维持「部分验证」。
3. **[遗留] 质量模板 17 条悬挂 file_id**：按用户指示留作独立问题；但已确认它会**误伤新附件**（§7 的次生现象），建议在 V1-B 前处理，否则任何新建附件只要 id 落进 17 个号段就会被判"受控"。

## 11. 本轮产生的测试数据（未清理，按约束不物理清理）

| 类型 | 明细 |
|---|---|
| 新建附件 | 14~29（除 21 已逻辑删除；均 `biz_type=inquiry,biz_id=1`，来源=验收测试） |
| 新建引用 | ref 4/5/6/7（4/5/7 已解绑，6 仍 active） |
| 孤立临时文件 | `upload/attachment-temp/*.bin` 5 个（临时上传缺陷产物，待人工核查） |
| 历史数据 | 附件 12/13 与引用 1/2 **未被修改**（只读） |

清理方式（需用户拍板后再执行）：这批附件可直接按 id 逻辑删除或整行删除；`attachment-temp/*.bin` 为无 DB 记录的孤立文件，确认后可手工删除（V1-A 不自动删）。

