# ATT-V1B-05 产品附件入口盘点（只读，dev-20261012-030）

> 任务码：dev-20261012-030（包内 ATT-V1B-05）｜ 日期：2026-10-12 ｜ 状态：只读盘点完成，**未改任何代码/库**
> 依赖：ATT-V1B-04（dev-20261012-029）已交付 ｜ 关联：dev-20261012-002（总任务）、dev-20261012-024（类型显示口径）、dev-20261012-025（资料类型字典）

## 0. 结论摘要

1. 产品模块**没有"通用附件上传"入口**（无 `AttachmentCenter` 落点）——所以"接入"这件事在产品模块**没有可替换的上传入口**。
2. 产品模块**有 1 处"通用附件只读聚合"**：产品详情页「业务流转附件」分组区（展示关联单据的附件）。它的数据源是 **legacy `biz_type/biz_id`**，不是 V1-A/V1-B 的 refs 通道 → 列为**接入候选**（含语义差异，待拍板）。
3. 其余 4 处全是**专用能力**（产品文件库 / 客供资料聚合 / 菲林 / 作业规范文档集），替换即丢功能 → **不接入**。
4. 待拍板 3 项（见 §3）。

**接入 1 项（候选）+ 不接入 5 项 + 待拍板 3 项；本轮不改造。**

## 1. 接入清单（候选，1 项）

### A) 产品详情「业务流转附件」只读区

| 层 | 位置 | 现状 |
|---|---|---|
| 前端 | `jjx-web/src/views/product/list/components/ProductDetail.vue:271-315`（渲染）、`:456-468 loadBizAttachments`、`:460 productApi.bizAttachments(productId)`、`:470 sourceTypeLabel`（仅 inquiry/quotation/order） | 按来源单据分组只读展示附件，链接用 `attachmentApi.downloadUrl(file.id)`（`:291`） |
| 后端 | `ProductController:88` `GET /product/{productId}/biz-attachments` → `ProductServiceImpl.getBizAttachments(~78 行)` | ① 先按 `product_id` 或 `product_code` 找关联单据（inquiry / quotation_item / sales_order_product）；② 再用 `LambdaQueryWrapper<SysAttachment>` 按 **legacy `biz_type` + `biz_id`** 查附件并分组 |

现状缺口（相对 V1-B 之后的通道）：

1. **不走 refs**：V1-B 之后新建的引用（`sys_attachment_ref`）如果没有同时写 `biz_type/biz_id`，就不会出现在这里 → 产品页会"少显示"。
2. **无类型/展示名**：不显示 `document_type`，与 024 定的"一级｜二级"口径不一致。
3. **无逐来源权限过滤**：refs 通道按来源 `:view` 过滤，这里没有。

迁移方案（**待拍板**，不建议直接替换）：

1. 保持现状（不改）——零风险，但保留"新附件不显示"缺口。
2. **混读**：`refs ∪ legacy`（按 `attachmentId` 去重），前端只读区同时消费两段——能补齐新附件，但需要处理两套元数据口径。
3. 后端补"按产品聚合引用（含通过来源单据间接关联）"的查询——最彻底，但属**后端改造**，需单独立项。
   ⚠ 语义差异必须先说清：`GET /refs/product/{productId}`（refsByProduct）只返回「`ref.product_id` = 该产品」或「`source_type='product'` 且 `source_id` = 该产品」的引用；而现有聚合是"通过来源单据间接关联"的文件。**两者覆盖集合不同，直接换成 refsByProduct 会让页面显示变少**。

## 2. 不接入清单（5 项，保持专用）

| 入口 | 位置 | 为什么不接入 |
|---|---|---|
| 产品文件库 `ProductFileLibrary` | `components/product/ProductFileLibrary.vue`（`:322 productFiles`、`:358 uploadProductFile`、`:389 remove`、`:405 setCurrent`、`:421 release`；权限 `uploadPerm/deletePerm/releasePerm`，产品详情传 `product:edit`/`product:delete`） | 走 `/attachment/upload-product` + 图纸元数据（`drawing_no`/`version`/`file_role`/`is_current`/`released`），带**受控/下发/现行版本**语义；换通用附件列表即丢这些能力 |
| 客供资料聚合 `CustomerDocPanel` | `components/product/customer-doc/CustomerDocPanel.vue:67`、`:138/139 customerDocs+productFiles`、`:202 uploadProductFile('客供稿')` | 写 `customer_product_code`/`customer_doc_type`/序号/整理名（产品维度 + 自动命名），并被产品详情/作业规范复用 |
| 菲林上传 | `views/product/film/index.vue:100/253/344/438`（`engineering_film.file_id`） | 菲林用**专用字段** `file_id` 关联，不是通用附件模型 |
| 作业规范文档集 | `views/engineering/product-spec/components/docset.ts:189/190`（附件来源 = `customerDocs` + `productFiles`）、`work_spec_json.structureFileId` | 文档集纸张/打印链路依赖它；`structureFileId` 是**硬引用**（受 protectedFile 保护） |
| 产品作业规范抽屉 `ProductSpecDrawer` | `components/product/ProductSpecDrawer.vue:32 CustomerDocPanel`、`:73 ProductFileLibrary` | 是上面两项的容器，无独立附件逻辑 |

## 3. 待拍板（3 项）

1. 「业务流转附件」是否切 refs：**保持现状 / 混读 / 后端补聚合查询**（三选一；建议先选"混读"，能以纯前端成本补齐新附件，见 §1）。
2. 产品详情是否需要**新增**"通用附件上传"入口（现在只有只读）——若需要，属新功能，建议单独立项，不在本包内自行新增。
3. 若统一只读口径：产品页附件是否也要显示"一级｜二级"类型（同 024 口径；依赖 275 字典已上线，纯前端）。

## 4. 范围与合规声明

- 本轮**只读盘点**：未改前端/后端代码、未改数据库、未新增入口、未替换任何专用组件、未启停服务、未 commit。
- 盘点范围（只读）：`jjx-web/src/views/product/**`、`jjx-web/src/components/product/**`、`jjx-web/src/views/engineering/product-spec/**`，以及后端 `ProductController` / `ProductServiceImpl.getBizAttachments`。
- 未纳入：工程图纸库（`views/engineering/drawing/components/EngineeringDrawingLibrary.vue`）属 engineering 模块，不在产品模块范围，未盘点。
