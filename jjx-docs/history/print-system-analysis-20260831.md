# JJX ERP 打印体系项目分析报告

- **报告日期**：2026-08-31
- **范围**：质量记录模板注册表（100 份）+ 全系统单据打印能力
- **数据来源**：`quality_template_registry` 表实查、前后端代码实扫

---

## 一、结论摘要

1. **模板注册已完成，联动能力严重滞后**：100 份模板全部入库，41 份挂了 `biz_type` 占位标识，但真正实现"数据联动打印"的只有 **6 份**（`category='data'`），其余 94 份仍是 `blank`（下载空白表自己填）。占位标识与实际能力的差距是 **35 份**。
2. **打印基础设施已经具备，不需要造轮子**：`A4Canvas` + `PrintDialog` + `PrintCompanyHeader` 三件套 + `quality_template_print_log` 留痕表 + 20 个已落地打印页，说明"业务数据 → A4 渲染 → 打印留痕"这条链路已经被验证过 6 次（品管 5 份 + 生产日报）。新增模板属于**照抄复制**，不是开荒。
3. **最大的实施缺口在销售模块**：销售是全系统打印页最多的模块（报价单/发票/收款/样品单 4 个 print.vue），但**送货单没有打印页**——而送货单是纸质流转最高频的单据（`deliveryApi.exportPdf` 已经留了空壳接口，后端未实现）。
4. **59 份模板永远不会有联动**（文控 19 + 行政 8 + 无数据源 32），这部分不该投入开发，只需保证"空白表能下载能打印能留痕"，现状已满足。

**一句话**：项目不缺基础设施，缺的是把 35 份占位标识逐个落成实现，且应从销售送货单开始。

---

## 二、现状盘点（实测数据）

### 2.1 模板注册表

| 维度 | 数量 | 说明 |
|---|---|---|
| 总模板数 | 100 | `AUTO_INCREMENT=101`，QR-001 ~ QR-100 |
| `category='data'`（真联动） | **6** | 已实现数据联动打印 |
| `category='blank'`（空白表） | **94** | 只能下载/空白打印 |
| 有 `biz_type` 占位标识 | **41** | 08-30 批量补的标识 |
| 无标识（纯管理类） | 59 | 文控/行政/无数据源 |

**关键矛盾**：41 有标识 − 6 有实现 = **35 份"标了但没做"**。

### 2.2 biz_type 占位分布（41 份）

| biz_type | 份数 | 归属模块 | 该模块打印页现状 |
|---|---|---|---|
| `operation_execution` | 8 | 生产报工 | ✅ 已有 3 份实现（日报/首件×2） |
| `inventory_inbound` | 7 | 库存入库 | ✅ 有 `inbound/print.vue` 可复用 |
| `production_equipment` | 5 | 设备管理 | ❌ 无打印页 |
| `purchase_supplier` | 4 | 供应商 | ❌ 无打印页 |
| `quality_inspection` | 4 | 质检 | ✅ 已有 IQC/FQC/返工 3 份实现 |
| `production_order` | 2 | 生产工单 | ✅ 有 `production/order/print.vue` |
| `sales_delivery` | 2 | 销售发货 | ❌ **无打印页（最大缺口）** |
| `inventory_outbound` | 2 | 库存出库 | ✅ 有 `outbound/print.vue` |
| `sales_order_review` | 2 | 订单评审 | ❌ 无打印页 |
| `purchase_order` | 1 | 采购订单 | ✅ 有 `purchase/order/print.vue` |
| `engineering_change` | 1 | 工程变更 | ❌ 无打印页 |
| `sales_contract` | 1 | 合同 | ❌ 无打印页 |
| `sales_inquiry` | 1 | 询价 | ✅ 有 `quotation/print.vue` 可参照 |
| `product` | 1 | 产品档案 | ✅ 有 `bom/BomPrintPreview.vue` |

### 2.3 已有打印基础设施

**通用组件（3 个，已稳定）**
- `components/A4Canvas/index.vue` — A4 纸张画布容器
- `components/PrintDialog/index.vue` — 打印弹窗（6.8KB，功能较完整）
- `components/PrintCompanyHeader.vue` — 公司抬头统一组件

**已落地打印页（20 个）**

| 模块 | 打印页 | 数量 |
|---|---|---|
| 库存 | inbound / outbound / stocktake / transfer | 4 |
| 销售 | invoice / quotation / receipt / sample-order | 4 |
| 生产 | order / quality / label-print | 3 |
| 采购 | order / plan | 2 |
| 品管专用 | fqc-report / iqc-report / daily-report / first-piece / rework-form | 5 |
| 其他 | BomPrintPreview / PrintProcessPanel | 2 |

**打印中心（`/production/quality-print`）**
- 列表页支持按类别/部门/关键字筛选
- `blank` 类 → 跳 `print.vue` 空白表打印 + 附件下载
- `data` 类 → 提示"请到对应业务模块打印"（**不提供跳转，是个体验缺口**）
- 5 个专用打印页各自独立注册路由

**留痕机制**
- 表：`quality_template_print_log`（template_id / record_no / operator / print_time）
- 接口：`POST /quality-templates/{id}/print-log`
- 索引：`idx_template_id`、`idx_print_time`

### 2.4 后端 PDF 能力

- `common/utils/pdf/PdfTemplateConfig.java` — 唯一的 PDF 配置类
- `sales/deliveries/export-pdf/{id}` — 前端已定义、**后端未实现**（空壳）
- 报价单 / 销售订单 `exportPdf` 同样是空壳（MEMORY 记录的 P1 待办）

**判断**：后端 PDF 生成体系基本没起来。现有 20 个打印页走的都是**前端 A4Canvas + 浏览器打印**路线。

---

## 三、问题诊断

### P0 — 占位标识与实现严重脱节
35 份模板标了 `biz_type` 但 `category` 仍是 `blank`。用户在打印中心看到标识，点进去只能下空白表，容易产生"这个已经能联动了"的误判。

**建议**：打印中心增加第三态显示（`已联动` / `规划中` / `空白表`），或在 `data` 类模板上加跳转链接。

### P1 — 销售送货单缺打印页（最痛）
`sales_delivery` 表存在、`SalesDeliveryController` 有 `getById` / `listByOrderId`、前端 `deliveryApi` 齐全，唯独**没有 print.vue**。送货单是客户签收凭证，纸质流转刚需。

### P1 — 两条打印技术路线并存
- 路线 A：前端 A4Canvas + 浏览器打印（20 个页面在用，已验证）
- 路线 B：后端 PDF 导出（3 个空壳接口，未实现）

两条路线并存但都不完整，需要**定死一条**。基于现状，**建议放弃路线 B**，除非有"归档 PDF / 邮件发送客户"的硬需求。

### P2 — 打印中心 data 类无跳转
`index.vue:37` 对 `data` 类只显示提示文案 `请到对应业务模块打印`，用户得自己找路。已有 6 份实现完全可以配置跳转路由。

### P2 — 打印日志只记模板级，不记业务单据
`quality_template_print_log` 只有 `template_id` + `record_no`，**没有业务单据 ID**。意味着无法回答"这张送货单被打印过几次、谁打的"——质量追溯场景下这是缺项。

**建议**：加 `biz_type` + `biz_id` 两个字段。

### P3 — 模板文件上传率未知
`file_id` 关联 `sys_attachment`，前端区分"已上传/未上传"，但 94 份空白表里实际上传了多少份 PDF/Excel 原件没有统计。未上传的空白表点打印等于空转。

---

## 四、实施路径建议

### 阶段一：打通销售送货单全链路（1 份，样板工程）

1. 前端新建 `views/sales/delivery/print.vue`，参照 `invoice/print.vue` + `A4Canvas`
2. 版式：公司抬头 + 送货单标题 + 客户信息区 + 明细表 + 签收签名区
3. 数据：`deliveryApi.getById(deliveryId)`（接口已有，零后端改动）
4. 入口：送货单列表"打印"按钮 + 打印中心 QR-026 跳转
5. 留痕：调 `print-log` 接口
6. 收尾：`quality_template_registry` 里 QR-026 的 `category` 改 `data`

**价值**：验证"占位 → 实现"的标准动作，后续 34 份照抄。

### 阶段二：补齐已有打印页模块的联动标识（约 15 份）

库存入库 7 份、出库 2 份、生产工单 2 份、采购订单 1 份、质检 1 份、报工 5 份——这些模块**打印页已存在**，工作量是"把模板套进现有打印页 + 挂 print-log + 改 category"，不需要新建页面。

### 阶段三：新建缺失模块打印页（约 13 份）

设备管理 5 份、供应商 4 份、订单评审 2 份、工程变更 1 份、合同 1 份。每个模块需要新建 print.vue，工作量最大。

### 阶段四：体验与追溯补强

- 打印中心 data 类加跳转
- 打印日志加 `biz_type` + `biz_id`
- 三态标识显示（已联动/规划中/空白表）
- 统计 94 份空白表的附件上传率，补齐缺失原件

### 不做

- 59 份无数据源模板（文控 19 / 行政 8 / 无支撑 32）不投入联动开发
- 后端 PDF 导出路线（除非出现归档/邮件硬需求）

---

## 五、优先级汇总

| 优先级 | 事项 | 影响面 |
|---|---|---|
| P0 | 送货单打印页（QR-026） | 客户签收刚需，接口已齐 |
| P0 | 技术路线定死（弃后端 PDF） | 避免继续分裂 |
| P1 | 阶段二 15 份联动（复用现有打印页） | 投入产出比最高 |
| P1 | 打印日志加 biz_id | 追溯能力缺项 |
| P2 | 打印中心 data 类跳转 | 体验 |
| P2 | 空白表附件上传率排查 | 避免空转打印 |
| P3 | 阶段三 13 份新建打印页 | 工作量大，可分批 |

---

## 六、风险提示

1. **版式风险**：现有 6 份联动打印的版式是按业务常规做的，**未经纸质原件比对**。如果客户/审核要求与实际纸质模板一致，35 份铺开前需要先拿到原件校对，否则会返工。
2. **打印留痕合规风险**：ISO 质量记录通常要求可追溯到具体单据。当前日志只到模板级别，**若外审关注这一点，是实质缺陷**。
3. **模板版本管理**：`version` 字段存在但无版本历史表。模板改版后，历史打印记录对应的是哪一版无法追溯。

---

*报告基于 2026-08-31 数据库与代码实际状态生成，所有数量均为实查结果。*
