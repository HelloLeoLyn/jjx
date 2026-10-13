# 发布快照 P2：完整内容原子固化与引用资源保留（返修版）

> 任务码：dev-20261013-010。父任务：dev-20261013-008；依赖 dev-20261013-009（P1，`2e4011a5`）。
> 首版 `9c1c8da9`；本文件为返修后版本（客供采集 / 结构图 / 批准口径 / 图标冻结 / 并发一致性）。
> 交付 P2 代码、验证结果与**供 P3 消费的字段契约**；运行时/浏览器/实际打印未验证。

## 1. 目标与交付

新发布版本一次性冻结：产品信息、规范正文与确认信息、BOM 树（含材料图标字节）、完整工艺路线（图标/参数）、
客供资料、样品与全部工程图及其文件元数据与字节身份；工作态变化不改变旧版；缺件/解析失败/哈希不符/源并发变化
不生成可见半成品版本。

- 复用 `product_work_spec_version` / `product_work_spec_item`，新增 `DOCSET_JSON`（完整快照）与
  `DOCSET_FILE`（文件保留索引）；保留旧 `SPEC_JSON/BOM/ROUTING/DRAWING` 供旧消费者。
- 未新增表、未改表结构，无迁移。

## 2. DOCSET_JSON 快照契约（P3 消费）

位置：`GET /product/work-spec/versions/{versionId}` → `items[]` 中 `resourceType='DOCSET_JSON'` 的 `snapshotJson`。

```json
{
  "schemaVersion": 1,
  "productId": 3, "productCode": "YL1025PWOO", "productName": "…", "customerId": 9, "customerName": "…", "productType": "standard",
  "product": { "productId":3, "productCode":"…", "productName":"…", "customerId":9, "customerName":"…",
               "productType":"…", "categoryId":1, "unit":"…", "specJson":"…",
               "currentBomVersion":"V1.0", "currentRoutingVersion":"V1.0" },
  "spec": { "…": "规范正文（工作态读取口径，含 revision/sourceRevision/approved/confirmedBy/confirmedAt）" },
  "confirmation": { "approved": true, "confirmedBy":"…", "confirmedAt":"…", "issueUnit":"工程部", "issueDate":"2026-10-12", "sourceRevision":"…" },
  "publication": { "versionNo":"V1.0", "publishedBy":"系统管理员", "publishedAt":"2026-10-12T16:43:39", "changeSummary":"…" },
  "bom": { "…": "EngineeringBomVO；含 processId 的条目已补 icon 与 iconSha256" },
  "routing": { "…": "EngineeringRoutingVO；同上" },
  "icons": { "面板": { "value":"面板", "kind":"bundled", "symbolId":"icon-面板", "attachmentId":null,
                        "sha256":"<图标字节 SHA-256>", "asset":"icon-archive/<sha256>.svg" } },
  "files": [ { "attachmentId":40, "section":"atlas", "category":"结构图", "fileName":"工程图4.jpg", "displayName":null,
               "version":"A", "drawingNo":"工程图4", "fileRole":"PRINT", "mime":"image/jpeg", "size":12345,
               "sha256":"<发布时字节 SHA-256>", "source":"product-library", "customerDocType":null, "sortOrder":0 } ],
  "counts": { "files":6, "icons":4, "bomItems":12, "routingItems":8 },
  "manifestHash": "<除 manifestHash 外全部内容的 SHA-256>"
}
```

约定：
1）`schemaVersion` 当前为 `1`（结构变更加字段时递增）。
2）`confirmation.approved` 采用**工作态口径**：`approved = confirmedSource == sourceRevision`
    （`sourceRevision=digest(sourceData)`，`sourceData` 含产品/BOM/路线/结构图）；**不直接读原始正文 `approved` 字段**（该字段从不落库）。
3）`section` ∈ `customer | sample | print | atlas | color | icon`。
4）`files[].source` ∈ `product-library`（产品文件库）/ `customer-source`（按 `customer_product_code` 归集）/ `icon`（上传图标附件）。
5）`icons[].kind` ∈ `bundled`（随应用打包的 SVG，字节归档于 `icon-archive/<sha256>.svg`）/ `uploaded`（引用附件，同时进入 `files[]` 受保护）。
6）`bom`/`routing` 缺失写 `null`；`files[]` 恒为数组。P3 只读本快照渲染，不再回查当前工作态。

## 3. 文件与图标保留

- 每个纳入快照的附件（含客供产品库类别、客户来源客供、样品、工程图、上传图标附件）各写一条
  `resourceType='DOCSET_FILE'` 条目：`source_id=attachmentId`、`operation_ref=section`、
  `snapshot_json={attachmentId,section,category,fileName,sha256}`、`content_hash=sha256`，作为不可变保留锚点。
- 打包图标 SVG 归档到受控存储 `icon-archive/<sha256>.svg`（内容寻址、不可变）；图标哈希写入 `icons`/条目 `iconSha256`。

## 4. 原子性与一致性

- 发布在单事务内、**写库前**构建并校验整份快照；任一必需内容缺失 / 文件缺失或不可读 / 哈希不符 /
  选定结构图缺失 / 图标无法解析为受控字节 / 源并发变化即抛 `BusinessException` 回滚 → 不落版本、不落条目。
- 文件身份取**实际字节** SHA-256，与库中记录比对。
- 并发：产品行锁（`FOR UPDATE`）覆盖 `work_spec/currentBomId/currentRouteId`；对 BOM 头+明细、路线头+明细、
  产品附件（含客供）、`engineering_standard_process` 行加 **`FOR SHARE` 锁读**（读最新已提交、并阻塞窗口内修改），
  并在构建首尾比对源令牌。注：普通一致读受 REPEATABLE READ 快照影响、双读无效；锁读绕过快照，故有效。

## 5. 资源保护（不扩大读取权限）

- 物理删除由触发器 `trg_attachment_v1_no_delete` 禁用；`AttachmentAccessService.protectedFile` 硬引用查询由
  `resource_type='DRAWING'` 扩为 `IN ('DRAWING','DOCSET_FILE')` → 快照内文件（含客供/样品/上传图标）在
  解除引用/软删/替换/清理入口受保护，`checkUnlink` 拒绝。
- 读取权限不变；下载仍走 `AttachmentAccessService.checkRead`。

## 6. 验证（自动化）

- `WorkSpecDocsetSnapshotServiceTest`（9）：四类文件（结构图显式 / 产品库客供 / 样品 / 客户来源）全部入集且分类正确；
  非现行结构图仍入集；批准口径（未批准不冒充）；图标字节冻结（打包归档+哈希、外部URL拒绝、打包缺失失败、上传图标入集）；
  文件缺失/哈希不符/BOM 缺失失败；源并发变化失败。
- `ProductWorkSpecVersionPublishTest`（2）：写入 `SPEC_JSON+BOM+DOCSET_FILE+DOCSET_JSON`；构建失败不落版本/条目。
- `AttachmentAccessServiceTest`（4）：快照文件受保护、禁止解除；未引用不误伤；受控/已下发短路。
- 全量：`mvn -o test` → Tests run: 326, Failures: 0, Errors: 0, Skipped: 3（BUILD SUCCESS）。

## 7. 未验证 / 遗留

- 真实接口、浏览器、实际打印未验证（遵循「仅代码与模拟测试」边界）。
- 打包图标依赖受控资源目录（`jjx.icon.assets.dir`，未配置时按 `jjx-web/src/icons/jjx` 等相对路径探测）；
  **部署到不含前端资源的机器需随包提供图标目录**，否则打包图标发布会失败（fail-closed）。
- 并发窗口的边界：`FOR SHARE` 覆盖已存在的来源行；发布期间**新增**附件/工序的间隙仍可能不被锁定（残差，记 P4）。
- 销售来源客供按 `customer_product_code` 归集；未登记归属的历史客供不猜、不纳入。
- 统一读取与完整性拦截在 P3（dev-20261013-011）。
