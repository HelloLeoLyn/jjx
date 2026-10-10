# 产品作业规范抽屉布局、组件化与客供资料（来源/类型）讨论结论

- 任务：dev-20261010-032
- 日期：2026-10-10
- 状态：**讨论结论记录**。除「样品页签移位」「占位页签」两处布局微调已实施外，其余**均未实施**，待用户后续提需求。
- 范围：产品电子文档集（产品作业规范抽屉，`jjx-web/src/views/engineering/product-spec/`）。
- 说明：本文是讨论快照，不保证反映当前实现；实施以当时最新代码为准。

## 1. 抽屉页签布局（结论）

- **一维页签结构保持不变**（不分组、不拆二级）——用户明确「不用理，就这样」。
- **命名不改**（抽屉标题与「产品作业规范」页签重名、「样品」与「样品需求单」易混）——用户明确「不用理」。
- **文档集覆盖不全**（油墨调配记录表未进在线预览/打印章节）——讨论后「跳过」；建议待油墨表落地保存/数据后，再按分色检查表的方式接入，不先打空白表。
- **页签顺序**：调整「样品」到「样品需求单」之后（样品家族相邻）。

**当前页签顺序**：客供资料 → 样品需求单 → 样品 → 领料单 → 产品作业规范 → 印刷规范 → 油墨调配记录表 → 工程图集 → 分色检查表。

**已实施**
- dev-20261010-030：新增「样品需求单」「领料单」占位页签。
- dev-20261010-031：「样品」页签移到「样品需求单」之后。

## 2. 「预览 / 打印文档集」按钮归属（待实施）

按钮现在位于抽屉内容区顶部 `index.vue` 的 `spec-head`（左侧产品信息 `el-descriptions` + 右侧按钮），在 `el-tabs` 之上。
→ 组件化后应归属**产品作业规范组件的 header**（即抽出的 `ProductSpecHeader`，放其 `#actions` 插槽）。

## 3. 组件化需求（待实施）

### 3.1 产品基本信息 → 公共组件（跨模块复用，产品详情也用）
- 接口：`<ProductBasicInfo :product="ProductVo" :loading :columns />` —— **接收对象、组件自身不发请求**；按钮用 `slot#actions` 抛出；只依赖 `ProductVo`（产品列表行同型可复用）。
- 字段：标识（产品编码/名称/类型标准·定制/单位/产品状态）· 归属（客户/产品分类）· 工程（当前BOM版本/当前路线版本/规格）· 审计（创建人时间/更新人时间）；商务（最小起订量/交期）按需；**售价/成本敏感，默认不展示**（要展示按权限）。
- **不放入**：菲林 / 网框 / 网版（`engineering_film` / `engineering_screen_frame` / `engineering_screen_plate`，属工艺资源台账，归印刷规范或单独页签）。
- **顺手修 bug**：抽屉头用 `as any` 取错 key —— `currentBomVersion` / `currentRoutingVersion` / `specification` **不在 `ProductVo`**（VO 为 `bomVersion` / `routeVersion`；规格是 `specJson`）→ 一直显示 `-`。组件化时对齐 VO 字段名。
- 拆分：`ProductBasicInfo`（信息）→ `ProductSpecHeader`（信息 + `#actions`）→ `ProductSpecTabs`（页签容器 + 插槽）；抽屉外壳（`el-drawer`/loading/离开守卫）留在页面 `index.vue`。

### 3.2 子页签内容公共组件化（待实施）
- 位置统一：页签内容组件挪到公共层 `src/components/product/`（现多为页面私有，跨模块复用不了）。
- 接口统一：props 统一 `productId`（+ 按需 `productCode`/`productName`），emit 统一 `busy` / `updated`。
- 仍内联的抽出：客供资料的询价/报价附件表 → `CustomerDocPanel`；页签容器 → `ProductSpecTabs`。
- **页签注册表配置化**（推荐）：一份 `{key,label,note,component,权限}` 配置，产品作业规范页与产品详情页共用，避免各维护一份。
- 预览/打印 `ProductSpecPreview` 一并归公共。

## 4. 客供资料（抽屉页签①）

### 4.1 来源（已接受）
- 现状（客供资料可见）：
  1. 询价单附件（`sys_attachment.biz_type=inquiry`，只读引用）
  2. 报价单附件（`biz_type=quotation`，只读引用）
  3. 工程上传（`biz_type=product`，类别=客供稿）
- 建议并**接受扩展**：
  4. 销售订单附件（`biz_type=sales_order`）
  5. 样品单附件（`biz_type=sample`）
  - 线下渠道（邮件/微信/当面）无系统入口 → 由工程上传承接（渠道归"上传"）。

### 4.2 类型（已接受：8 类正式文件类型命名）
1. 客户图纸（Customer Drawing）
2. 技术规格书（Specification / 技术协议）
3. 承认书（Approval Sheet）
4. 检验标准（Inspection Standard / 验收标准）
5. 变更通知（ECN / Change Notice）
6. 标准样品（Golden / Reference Sample）
7. 包装规范（Packaging Specification）
8. 认证报告（Compliance Report：RoHS/REACH/UL 等）
9. 其他资料

### 4.3 现状问题
- 引用段（询价/报价附件）`category` 为空 → 无类型；上传段只有字典单类「客供稿」（`product_file_category.customer_supplied`）。
- 后端 `customerDocs()` 里来源类型是**硬编码中文** `'询价单'/'报价单'`（`sourceType`），非枚举。
- 字段大小写/下划线混用（`file_name` 等下划线 + 别名 `sourceType/sourceNo` 驼峰），前端模板用一串 `||` 兜底（实际多余）。
- 只读附件表无 loading 态。
- 报价侧取的是**整张报价单的附件**（`sales_quotation_item.product_id` 仅用于筛"含该产品的报价单"），可能带出与产品无关的附件。

### 4.4 版本 / 覆盖现状（结论：无）
- 询价/报价附件：`SysAttachmentServiceImpl.uploadAttachment()` 为**纯新增**（`INSERT`，不设 `version`、不设 `is_current`）→ **无版本、无覆盖**；同名再传即多一条。
- 删除为软删（`deleted`）+ 回收站清理；`customerDocs()` 只取 `deleted=0`。
- 「版本 / 现行 / 下发 / 受控」（`version`+`is_current`+`is_controlled`+`released`+`drawing_no`）**仅在产品文件（`biz_type=product`）上**。
- 客供资料是 **JOIN 引用**（不复制）→ 源单据附件一改一删，客供资料视图跟着变，**无历史版本/快照**。
- 现状数据：库中 `biz_type=inquiry/quotation` 附件 **0 条**（客供资料当前为空）。

### 4.5 待用户拍板（未决）
- **A 纯引用**（现状，轻） vs **B 工程"接收"时归档受控副本（快照，带版本/接收人/时间）**。
- 类型存哪：复用 `sys_attachment.category`（新建字典，如 `客供资料类型`）还是其它。
- 引用段（询价/报价附件）的类型来源：源头上传时选，还是到客供资料再归类。
- 是否纳入销售订单 / 样品单来源。
- 是否对重复上传去重 / 版本化。
- 建议补充维度：接收信息（接收人/日期/提供人）、用途/适用（对应产品/工序）、状态（有效/作废/被替代）。

## 5. 关联

- 已实施：dev-20261010-030（占位页签）、dev-20261010-031（样品移位）。
- 相关任务/文档：dev-20261010-026（分色检查表，已实施）、dev-20261010-029（油墨调配记录表，前端原型）、dev-20261010-024（印刷规范）、dev-20261010-020/018/010（作业规范）。
- 来源依据：2026-10-10 与用户逐条讨论（见当日会话记录）。
