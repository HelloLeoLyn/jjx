# 发布快照 P2：完整内容原子固化与引用资源保留

> 任务码：dev-20261013-010。父任务：dev-20261013-008（方案见 `history/product-spec-full-snapshot-plan-dev-20261013-008.md`）。
> 依赖：dev-20261013-009（P1，已提交 `2e4011a5`）。状态：待审核。
> 本文交付 P2 代码、验证结果与**供 P3 消费的字段契约**；运行时/浏览器/实际打印未验证。

## 1. 目标与交付

新发布版本一次性冻结：产品信息、规范正文、确认信息、BOM 树（含材料图标）、完整工艺路线（含图标/参数）、
客供资料、样品与全部工程图及其文件元数据与字节身份。之后工作态变化不改变旧版。缺件/解析失败/哈希不符/
源并发变化时不生成可见半成品版本。

- 复用现有 `product_work_spec_version` / `product_work_spec_item`，新增 `DOCSET_JSON`（完整快照）与
  `DOCSET_FILE`（文件保留索引）两种 `resource_type`；保留旧 `SPEC_JSON/BOM/ROUTING/DRAWING` 供旧消费者。
- 未新增数据表、未改表结构（`resource_type` 为 varchar(30)，`snapshot_json` 为 mediumtext），故无迁移。

## 2. DOCSET_JSON 快照契约（P3 消费）

位置：`GET /product/work-spec/versions/{versionId}` 返回的 `items[]` 中 `resourceType='DOCSET_JSON'` 的
`snapshotJson`（字符串，解析后结构如下）。

```json
{
  "schemaVersion": 1,
  "productId": 3, "productCode": "YL1025PWOO", "productName": "…",
  "customerId": 9, "customerName": "…", "productType": "standard",
  "product": { "productId":3, "productCode":"…", "productName":"…", "customerId":9, "customerName":"…",
               "productType":"…", "categoryId":1, "unit":"…", "specJson":"…",
               "currentBomVersion":"V1.0", "currentRoutingVersion":"V1.0" },
  "spec": { "…": "规范正文全文（SPEC_JSON 原文，原样冻结）" },
  "confirmation": { "approved": true, "confirmedBy":"…", "confirmedAt":"…", "issueUnit":"工程部", "issueDate":"2026-10-12" },
  "publication": { "versionNo":"V1.0", "publishedBy":"系统管理员", "publishedAt":"2026-10-12T16:43:39", "changeSummary":"…" },
  "bom": { "…": "EngineeringBomVO；items 每个含 processId 的节点已补 icon（发布时冻结）" },
  "routing": { "…": "EngineeringRoutingVO；items 平铺含 icon / customProcessParams 等" },
  "files": [
    { "attachmentId":40, "section":"atlas", "category":"结构图", "fileName":"工程图4.jpg", "displayName":null,
      "version":"A", "drawingNo":null, "fileRole":"PRINT", "mime":"image/jpeg", "size":12345,
      "sha256":"<发布时字节 SHA-256>", "source":"product-library", "customerDocType":null, "sortOrder":0 }
  ],
  "counts": { "files":5, "bomItems":12, "routingItems":8 },
  "manifestHash": "<除 manifestHash 外全部内容的 SHA-256>"
}
```

约定：
1）`schemaVersion` 当前为 `1`；结构变更递增，旧消费者按此分支。
2）`section` ∈ `customer | sample | print | atlas | color`，与前端 `documentSections` 对齐。
3）`bom`/`routing` 为空时写 `null`（不省略键）；`files[]` 始终为数组。
4）`source` ∈ `product-library`（产品文件库）/ `customer-source`（按 `customer_product_code` 归集的客供来源）。
5）P3 只读本快照渲染，不再回查当前产品/BOM/路线/图标/附件集合；`bom`/`routing` 字典名以本快照为准。

## 3. DOCSET_FILE 保留契约

每个纳入快照的附件各写一条 `resourceType='DOCSET_FILE'` 的条目：`source_id=attachmentId`、
`operation_ref=section`、`snapshot_json={attachmentId,section,category,fileName,sha256}`、`content_hash=sha256`。
它是发布版本对文件字节的**不可变保留锚点**，也是权限判定依据（见 §5）。

## 4. 原子性与一致性

- 发布在单事务内、**写库前**构建并校验整份快照；任一必需内容缺失 / 正文 JSON 非法 / 文件缺失或不可读 /
  哈希不符即抛 `BusinessException`，事务回滚 → 不落版本行、不落任何条目（无半成品）。
- 产品行锁（`SELECT … FOR UPDATE`）覆盖 `work_spec_json` 与 `currentBomId/currentRouteId`；BOM/路线再做
  **双读一致性**：构建后重读，序列化结果不一致 → 发布失败（覆盖「锁产品行不等于锁住所有来源」）。
- 文件身份取**实际字节** SHA-256，与库中记录比对；不信任附件行上的旧 hash。

## 5. 资源保护（不扩大读取权限）

- 物理删除已被触发器 `trg_attachment_v1_no_delete` 禁用；软删/解绑保护由
  `AttachmentAccessService.protectedFile` 承担：其硬引用查询已由 `resource_type='DRAWING'` 扩为
  `IN ('DRAWING','DOCSET_FILE')`，故快照内文件（含客供、样品）在解除引用/软删/替换/清理入口均受保护，
  `checkUnlink` 拒绝。
- 读取权限不变：下载仍走 `AttachmentAccessService.checkRead` / `AttachmentDownloadService`，不因快照放宽。

## 6. 验证（自动化）

- 新增测试（均通过，JDK21）：
  - `WorkSpecDocsetSnapshotServiceTest`：完整性（产品/正文/确认/BOM 图标/路线/三类文件入集与分类、
    `schemaVersion`/`manifestHash`）、缺件失败、哈希不符失败、正文非法失败、BOM 缺失失败、源并发变化失败、
    旧快照字符串不随后续构建改变。
  - `ProductWorkSpecVersionPublishTest`：发布写入 `SPEC_JSON+BOM+DOCSET_FILE+DOCSET_JSON`；
    快照构建失败时**不落版本行、不落条目**（原子性）。
  - `AttachmentAccessServiceTest`：快照文件被保护且禁止解除；未引用文件不误伤；受控/已下发短路。
- 全量：`mvn -o test` → Tests run: 324, Failures: 0, Errors: 0, Skipped: 3（BUILD SUCCESS）。

## 7. 未验证 / 遗留

- 真实接口、浏览器、实际打印未验证（遵循「仅代码与模拟测试」边界）。
- 图标按**发布时解析到的名称**冻结（字典后续改名不影响旧版）；SVG 资源本身未内嵌归档，属 P4/后续项。
- 销售来源客供资料按 `customer_product_code` 归集；如存在未登记归属的历史客供，不猜测、不纳入。
- 本阶段只固化与保留，统一读取与完整性拦截在 P3（dev-20261013-011）。
