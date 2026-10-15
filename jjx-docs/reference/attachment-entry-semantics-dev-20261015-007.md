# 附件只读入口语义定义（mode=readonly / readonly 属性 / 无编辑权限 / 业务锁定）

状态：生效（2026-10-15）
任务：dev-20261015-007（组件改造前置：只读入口语义定义）
被取代或取代：落地 dev-20261014-009 分析卡 §4「入口」待核对项；与 dev-20261015-001（附件状态词表）同族、互为配套
什么情况看这篇：要判断"这个附件入口算不算只读""该不该给上传/编辑按钮""为什么这里禁用了"，或改 `AttachmentCenter` / `AttachmentDocList` 入口前
最后复核：2026-10-15

---

## 1）一句话结论

四类"只读"不是同一个东西，**来源不同、撤销方式不同，禁止合并成一个 `disabled` 条件**：

1）**模式只读（mode=readonly）**＝调用方在模板里选择"这个入口只展示"，由**调用方传参**决定；
2）**只读属性（readonly）**＝调用方按页面语义传的"本页只读"能力位，由**调用方传参**决定；
3）**无编辑权限**＝"你是谁"（RBAC 权限点），由**权限点**决定；
4）**业务锁定**＝"这张单据当前是什么状态"（提交中 / 已核销 / 未选产品…），由**业务状态**决定。

并且：**业务锁定 ≠ 没权限**——锁定是"单据现在不能改"，权限是"你不能改"，两者独立、可同时成立，锁定解除后权限仍在。**只读也不等于有查看权限**：`canEdit` 与 `canView` 是两条独立计算（见 §2 证据）。

---

## 2）四类情形：定义 / 判定来源 / 落点 / 效果

代码基准：jjx-web `11014e7f` 起的当前 HEAD；行号仅作定位，实施时重核。

| 类别 | 定义 | 判定来源（谁决定） | 代码落点 | 关闭了什么 | 未关闭什么 | 撤销方式 |
|---|---|---|---|---|---|---|
| ① 模式只读 `mode="readonly"` | 调用方在模板里选定的**展示模式**：只要列表与只读段，不要上传入口 | **调用方传参**（组件 `mode` prop） | `AttachmentCenter.vue:152`（prop）、`:331`（`canUpload`） | 上传入口（header/落区的上传器） | **行内编辑仍由权限决定**（`canEdit` 不受 mode 影响） | 改调用方模板里的 `mode` 值 |
| ② 只读属性 `:readonly="…"` | 调用方给的**页面级只读能力位**（如路由 `?readonly=true`、详情页固定只读） | **调用方传参**（组件 `readonly` prop） | `AttachmentCenter.vue:153`（prop）；`useAttachmentRefs.ts:128,173-175`（`canEdit` 直接判 false） | 上传 + 全部编辑动作（批量改类型/改名/移除、行内改类型/展示名、重试/移除待绑定、更多、解绑） | 查看 / 下载 / 预览 | 调用方不再传 `readonly` |
| ③ 无编辑权限 | 当前用户**没有该来源的编辑权限点** | **权限点**（RBAC；`editPerm` 显式传入，或来源前缀 `…:edit` 兜底；真源在后端 `AttachmentSourcePolicy`） | `useAttachmentRefs.ts:169-177`（`canView` / `canEdit`）；映射表 `enums/system/AttachmentEnum.ts` 的 `ATTACHMENT_SOURCE_PERMISSION` | 上传 + 全部编辑动作 | **查看 / 下载 / 预览照旧**（`canView` 独立） | 授权（后端）/ 换账号 |
| ④ 业务锁定 `:locked` / `:upload-blocked` | 单据**业务状态**决定的临时禁写（提交处理中 / 核销后才可传 / 未选关联产品…） | **业务状态**（调用方按单据状态算好后传入；组件本身不做业务判断） | `AttachmentCenter.vue:154`（`locked` prop）、`:172`（`uploadBlocked` prop）、`:342-345`（`uploadBlockedReason`）；`useAttachmentRefs.ts:129,174`（`locked` → `canEdit` false） | `locked`：上传 + 全部编辑；`uploadBlocked`：仅上传入口（附带一句原因文案） | 查看 / 下载 / 预览 | 业务状态变化 / 调用方清空该传参 |

补充说明：

1）**组件层已自证**：`AttachmentCenter.vue:126` 注释写明"readonly / locked 只是能力位，不定义业务规则；后端始终是权限最终裁决方"。本表把这句话拆成可判定的四类。
2）**① 与 ② 的差别（最容易混）**：`mode="readonly"` 只掐掉"上传入口"（`canUpload = props.mode !== 'readonly' && canEdit.value`，`:331`），**不动行内编辑**；`readonly` 属性才把 `canEdit` 直接压成 false（`useAttachmentRefs.ts:173-175`）。所以"完全只读"必须传 `readonly`，只传 `mode="readonly"` 不够——现状差异见 §5。
3）**③ 的兜底**：调用方不传 `editPerm` 时，`canEdit` 回退到"来源前缀 `:edit`"；来源前缀未登记的来源（如 `task`）回退为"默认允许编辑"（`useAttachmentRefs.ts:176`），此时仍受 ①②④ 约束。
4）**④ 的两种形态**：`locked`（整块禁写，含编辑）与 `uploadBlocked`（只锁上传 + 展示一句原因）。后者是"给上传按钮禁用并解释"，不是权限、不是模式。

### 为什么四者不能合并成一个条件

若图省事写成 `disabled = readonly || locked || !hasEdit || mode === 'readonly'`，会丢掉"**为什么禁用**"，直接后果：

1）**权限误报**：业务锁定的页面只显示"无权限"，用户明明有权限却以为被收权；
2）**引导错误**：应提示"核销后才能传 / 提交中"的场景被写成"无编辑权限"，用户找不到解法；
3）**审计失真**：无法区分"系统按业务规则锁"（会自动解除）与"人没有权限"（需授权）；
4）**范围失控**：`mode` 是渲染选择、`readonly` 是页面能力位、权限是身份、`locked` 是状态——它们的变化时机不同（换页面 / 单据流转 / 授权 / 状态推进），合并后无法单独撤销。

---

## 3）逐调用方归类

归类口径：按"该入口在正常业务态下，编辑能力**主要由哪个条件决定**"归一类；附带的前置/瞬时限制在"说明"列标注。组件为 `AttachmentCenter`。

### 3.1 归入 ① 模式只读（2 处）

| 调用方（文件:行） | 关键入参 | 说明 |
|---|---|---|
| `views/sales/order/components/OrderDetailDrawer.vue:293` | `mode="readonly"`、仅 `view-perm="sales:order:view"` | 订单详情抽屉的纯查看入口。**风险**：未传 `edit-perm` 也未传 `readonly`，`canEdit` 会回退到 `sales:order:edit`——有该权限的用户仍可行内编辑/解绑（见 §5）。 |
| `components/product/customer-doc/CustomerDocPanel.vue:11` | `:mode="readonly ? 'readonly' : 'panel'"`、`view-perm="product:view"`、`edit-perm="product:edit"` | 用 mode 表达"只读/可编辑"。**注意**：调用方（`ProductSpecDrawer.vue`）从不传该组件的 `readonly`，且未传则恒 `mode="panel"`；即便传了也只关上传、不关行内编辑。 |

### 3.2 归入 ② 只读属性（3 处）

| 调用方（文件:行） | 关键入参 | 说明 |
|---|---|---|
| `views/engineering/sample-workbench/components/SampleInfoCard.vue:92` | `:readonly="readonly"`（上层 `workbench.vue:5` 传 `readonlyMode`）、`view/edit-perm="sales:sample:*"` | `readonlyMode = route.query.readonly === 'true'`（`composables/useSampleWorkbench.ts:22`），属**页面/路由级只读**。 |
| `views/purchase/payment/index.vue:287` | `readonly`（硬编码 true）、来源 `purchase_payment` | 付款详情抽屉里的转账回单，固定只读。 |
| `views/purchase/order/components/OrderDetailDialog.vue:184` | `readonly`（硬编码 true）、来源 `purchase_payment` | 采购订单详情里的付款回单，固定只读。 |

### 3.3 归入 ③ 无编辑权限（16 处）

这些入口显式传 `view-perm` / `edit-perm`（或依赖来源前缀回退），编辑能力完全由权限点决定，未使用 ①②④。

| 调用方（文件:行） | 关键入参 | 说明 |
|---|---|---|
| `components/product/ProductDocPanel.vue:5` | `mode="upload-only"`、`view-perm/edit-perm` | 产品资料视图，只给上传入口。 |
| `components/OperationPreviewDialog/index.vue:91` | `mode="upload-only"`、`view/edit-perm` 由 `operation.bizType` 推得 | 通用"操作确认"证据上传；被多入口复用。 |
| `views/sales/sample-order/components/SampleForm.vue:161` | `view/edit-perm="sales:sample:*"`、`draft-id`、`drop-zone` | 样品单表单内附件。 |
| `views/sales/sample-order/components/EngineeringWorkbench.vue:221` | 来源 `sample`、`drawing-meta`、未传 perm（回退 `sales:sample:*`） | 工程工作台"本单图纸"。 |
| `views/sales/delivery/index.vue:171` | `mode="panel"`、`view-perm="sales:delivery:doc:view"`、`edit-perm="sales:delivery:doc:add"` | 发货详情抽屉回签件。 |
| `views/sales/delivery/index.vue:256` | 同上（签收弹窗） | 回签件第二入口。 |
| `views/sales/quotation/components/QuotationFormDialog.vue:321` | `view/edit-perm="sales:quotation:*"`、`draft-id`、`trace-id`、`product-code`、`drop-zone` | 报价单行级附件。 |
| `views/sales/quotation/index.vue:181` | `mode="dialog"`、`view/edit-perm="sales:quotation:*"` | 报价单列表附件弹窗。 |
| `views/sales/order/components/GeneratePlanDialog.vue:20` | `mode="upload-only"`、`view/edit-perm="sales:order:*"` | 生成生产计划附件。 |
| `views/sales/order/components/OrderForm.vue:289` | `view/edit-perm="sales:order:*"`、`draft-id`、`trace-id` | 销售订单表单附件。 |
| `views/sales/order/components/ReviewDialog.vue:56` | `mode="upload-only"`、`view/edit-perm="sales:order:*"` | 订单审核附件。 |
| `views/sales/order/index.vue:257` | `mode="panel"`、`view/edit-perm="sales:order:*"` | 确认凭证附件弹窗。 |
| `views/purchase/payment/index.vue:247` | 未传 perm（回退 `purchase:payment:*`）、`source-id` 可空 | 确认付款弹窗的转账回单上传；未保存时由 `draftReady` 拦住上传。 |
| `views/purchase/order/components/OrderDetailDialog.vue:118` | `view/edit-perm="purchase:order:*"`、`drop-zone` | 采购订单详情凭证。 |
| `views/production/quality-archive/index.vue:92` | `mode="panel"`、`view-perm="production:quality-archive:doc:view"`、`edit-perm="…:doc:add"` | 质量档案回传扫描件。 |
| `views/production/quality-template/index.vue:88` | `mode="panel"`、`view/edit-perm="production:quality-template:doc:*"` | 质量模板文件。 |

### 3.4 归入 ④ 业务锁定（6 处）

这些入口的写能力由**业务状态**显式压住（`locked` 或 `uploadBlocked`），与权限叠加。

| 调用方（文件:行） | 关键入参 | 说明 |
|---|---|---|
| `views/purchase/components/PurchaseReceiveDialog.vue:79` | `mode="upload-only"`、`:locked="submitting"`、`auto-bind=false`、`view/edit-perm="purchase:receipt:doc:*"` | 提交处理中瞬时锁定。 |
| `views/purchase/invoice/index.vue:67` | `mode="upload-only"`、`:locked="submitting"`、`view/edit-perm="purchase:invoice:doc:*"` | 生成发票弹窗，提交中锁定。 |
| `views/purchase/invoice/index.vue:99` | `mode="upload-only"`、`:locked="submitting"`、`view/edit-perm="purchase:invoice:doc:*"` | 核销弹窗，提交中锁定。 |
| `views/purchase/invoice/index.vue:134` | `:upload-blocked="'发票文件在核销时上传；此处可查看、下载、解除引用'"` | 详情页：按业务时点只锁上传入口并给原因；查看/解绑仍开放（解绑由 `edit-perm` 决定）。 |
| `views/product/film/index.vue:242` | `mode="upload-only"`、`:upload-blocked="form.productId ? '' : '请先选择关联产品'"` | 表单前置条件锁定（未选产品禁传）。 |
| `views/sales/inquiry/components/InquiryForm.vue:145` | `:upload-blocked="form.inquiryId ? '' : '请先点「保 存」生成单据，再上传附件'"`、`view/edit-perm="sales:inquiry:*"` | 未保存前禁传；保存后编辑能力回到 ③。 |

### 3.5 另一类：结构性只读（`AttachmentDocList`，5 处）

`AttachmentDocList` 是**参数驱动的纯只读组件**：它没有 `mode` / `readonly` / `locked` 传参，也**没有任何编辑路径**（无上传、无批量、无解绑；只有预览/下载）。因此它的调用方**不属于 ①②③④ 中的任何一类**——它是"组件结构上没有编辑能力"，不是"某条件把编辑关掉了"。这正是"不能合并成一个条件"的反向例证：这里的只读不来自任何条件，而来自组件本身。

| 调用方（文件:行） | 说明 |
|---|---|
| `components/BizFlowDetail/index.vue:52` | 业务流详情（trace 文档）。 |
| `views/sales/sample-order/components/SampleDetail.vue:67` | 样品详情。 |
| `views/sales/quotation/components/QuotationDetailDialog.vue:103` | 报价详情（仅 `trace-id`）。 |
| `views/sales/inquiry/components/InquiryDetailDialog.vue:58` | 询价详情（仅 `trace-id`）。 |
| `views/production/quality-archive/index.vue:108` | 质量档案"归档附件"（与 92 行同一来源的只读展示）。 |

### 3.6 计数小结

1）① 模式只读 2 处；② 只读属性 3 处；③ 无编辑权限 16 处；④ 业务锁定 6 处；另有 ⑤ 结构性只读（`AttachmentDocList`）5 处。
2）`AttachmentCenter` 实际挂载点合计 27 处（2+3+16+6），与只读 grep 一致。

---

## 4）与主分析 §4 的指针

本表是 `jjx-docs/task-docs/dev-20261014-009-analysis.md` **§4**「方案A实施前必须核对 · 1）入口」的配套清单。该分析卡归 Codex 维护，本任务**不改他文**；§4 需要的"入口语义定义 + 逐调用方归类"即本文档，主分析 §4 可直接指向本文件：

- 入口语义定义 → `jjx-docs/reference/attachment-entry-semantics-dev-20261015-007.md`（本文，§2 四类定义、§3 逐调用方归类）。

---

## 5）现状差异与待确认点（只读观察，未改代码；供实施卡核对）

1）**`mode="readonly"` 不关行内编辑**：`OrderDetailDrawer.vue:293` 只用 `mode="readonly"`，未传 `readonly`；`canEdit` 回退到 `sales:order:edit`，有该权限的用户可批量/解绑。是否要"完全只读"（补 `readonly` 或补 `edit-perm`）须在实施卡拍板。
2）**`CustomerDocPanel` 的 `readonly` 形同虚设**：`CustomerDocPanel.vue:16` 用 `:mode="readonly ? 'readonly' : 'panel'"`，但调用方 `ProductSpecDrawer.vue:42` 从不传 `readonly`（恒 `false` → 恒 `panel`）；即使传了也只关上传、不关编辑。
3）**`uploadBlocked` vs `locked` 的选用不统一**：`invoice:134`、`film:242`、`InquiryForm:145` 用 `uploadBlocked`（只锁上传），`PurchaseReceiveDialog` / `invoice:67,99` 用 `locked`（连编辑一起锁）。同属④，但覆盖范围不同，需在规范里写明何时用哪种。
4）**`inquiry/index.vue` 疑似死引用**：`views/sales/inquiry/index.vue:207/441` 导入并声明了 `attachmentCenterRef`，但模板中无 `AttachmentCenter` 挂载；实际入口在 `InquiryForm.vue:145`。建议实施卡一并核实清理。
5）**死件候选**：`components/attachment/AttachmentItem.vue` 为 0 字节（另见 dev-20261015-011）；`components/AttachmentUploader/index.vue`（旧上传组件）除自身注释与 `components.d.ts` 生成声明外无任何引用。

---

## 6）验证（本次）

1）本文档为**纯只读核查**：未改任何 `.vue/.ts/.java`，未改数据库，未动 `sys_task` 之外的库内容。
2）只读复核命令（可重跑）：

```bash
# 列出所有 AttachmentCenter / AttachmentDocList 挂载点
grep -rn "<AttachmentCenter" jjx-web/src --include=*.vue
grep -rn "<AttachmentDocList"  jjx-web/src --include=*.vue
# 四类传参分布
grep -rn 'mode="readonly"\|:readonly=\|:locked=\|upload-blocked=' jjx-web/src --include=*.vue
```

3）文档门禁：`cd jjx-web && npm run check:docs`（`reference/` 不在门禁扫描范围，按规范仍跑一次）。

---

## 7）相关

- `jjx-docs/task-docs/dev-20261014-009-analysis.md` —— 附件中心治理分析（§4 入口待核对项，本表的来源）。
- `jjx-docs/reference/attachment-state-vocabulary-dev-20261015-001.md` —— 附件状态词表与公共组件三条口径（配套）。
- `jjx-docs/task-docs/dev-20261014-009-plan.md` —— 附件模块整体重设计备用方案（未授权实施）。
- `jjx-web/src/components/attachment/AttachmentCenter.vue` —— 四类传参落点（`:152-172`、`:331`、`:337`、`:342`）。
- `jjx-web/src/composables/useAttachmentRefs.ts` —— `canView` / `canEdit` 判定（`:128-129`、`:169-177`）。
