# 送货单打印改造 spec：删系统版 + 三联纸(241×140)版式 + 纸版对齐模板（dev-20260921-049）

- 日期：2026-09-21
- 任务码：dev-20260921-049（sys_task task_id=2111）
- 触发：用户 2026-09-21 口径 ——「系统版不要了；纸版做多一个复合三联纸打印模式；纸版样式跟模板有出入」
- 实施：交 Codex 执行；执行代理**只允许改 1 个文件**（见 §2 白名单）
- 参照物：`jjx-docs/print_template/JJX-QR-026送货单.xlsx`（Sheet「QF 新26年」A3:I3=公司名、D5:E5="送  货  单"、G5:I5=地址、H6=N O:、H7=DATE:、第 8 行 9 列表头、A14=说明行、A15/C15/F15=经手人）
- 非 A4 先例：`jjx-web/src/views/production/label-print/index.vue:40-42,173`（标签 50×30/40×30mm、`@page { size: auto; margin: 0 }`、打印时隐藏非单据内容、尺寸全用 mm）

---

## 1. 目标（三件，一次做完）

1. **删除「系统版」版式**（送货单只保留纸版 + 新增的三联版）
2. **新增「三联纸」版式**：241mm × 140mm 针式连续纸（用户已定 B 路 = 针式连续三联纸）
3. **纸版(QR-026) 与模板对齐 3 处**：地址移回标题行右侧 / 说明行改回繁体「貨」/ 合计金额行与单价金额列保留（第 3 项只是确认，不改）

---

## 2. 白名单（硬约束）

**允许修改**：`jjx-web/src/views/sales/delivery/print.vue`

**只读、不得修改**：`jjx-web/src/components/A4Canvas/index.vue`、`jjx-web/src/components/print/PrintToolbar.vue`、`jjx-web/src/composables/usePrint.ts`、`jjx-web/src/views/sales/delivery/index.vue`、`jjx-web/src/api/sales/delivery.ts`、后端任何文件、任何 SQL/迁移、`quality_template_registry`（DB 数据）

**红线**：
- 不许顺手"修复/优化/重命名/删除"白名单外的任何东西，即使看起来像 bug
- 不许改接口调用、不许加后端请求、不许动 DB
- 白名单外发现的问题写进最终报告，不要自己动手
- 不许改 route / 入口按钮 / 权限

---

## 3. 改动细节

### 3.1 删系统版

- 删掉模板里 `v-if="layout === 'system'"` 整段（现 :16-66，含 PrintCompanyHeader / h1 送货单 / info 块 / system 表格 / 系统版页脚）
- `usePrintLayout` 选项删 `{ value: 'system', ... }`
- **同时删掉因此失效的 import**：`PrintCompanyHeader`（删除后不再使用；不删会留下未使用 import）
- localStorage 旧值 `'system'` 由 `usePrintLayout` 的 `values.includes(stored)` 校验自动回退到第一项 —— 不需要额外兼容代码
- 删完后版式数组为：`[{ value: 'qr026', label: '纸版(QR-026)' }, { value: 'triplicate', label: '三联纸(241×140)' }]`（默认第一项 = 纸版）

### 3.2 新增三联纸版式（`layout === 'triplicate'`）

- **不用 A4Canvas**（它写死 210mm）。新建容器 `.triplicate-page`：
  - `width: 241mm`、`min-height: 140mm`、`box-sizing: border-box`
  - 左右内缩预留连续纸走纸孔位：padding 建议 `0 12mm`（垂直方向 3~4mm）
  - 字号/间距一律用 **mm**（参照 label-print：标题 3.2mm/行高 4mm、字段 2.2mm/行高 3mm 这一档，可按 9 列排布微调），不要用 px/pt
- **打印规则**（照 label-print 先例，写在 `<style scoped>` 的 `@media print` 里）：
  ```css
  @media print {
    @page { size: auto; margin: 0; }
    html, body, #app { margin: 0 !important; padding: 0 !important; background: #fff !important; }
    body * { visibility: hidden; }
    .triplicate-page, .triplicate-page * { visibility: visible; }
    .no-print { display: none !important; }
    .triplicate-page { position: absolute; inset: 0; }
  }
  ```
- **内容结构严格照模板**（自上而下）：
  1. 公司名一行（`company.name`，整行）
  2. 标题行：左「送　货　单」+ 右「地址：{{ company.address }}」
  3. `TO: {客户}`（左） + `NO: {deliveryNo}`（右）同一行
  4. `Attm: {联系人} {电话}`（左） + `DATE: {deliveryDate}`（右）同一行
  5. 9 列表头：NO / 品名(料号) / 规格 / 单位 / 数量 / 单价 / 金额 / 订单号码 / 备注（与纸版同列、同取值来源）
  6. 明细行：容量按 **6 行**排版；**超过 6 行自然分页，不得截断数据**
  7. 合计金额行（一行，保留）
  8. 说明行：`如上列貨品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。`（繁体「貨」）
  9. 经手人两栏：`送货单位经手人：____` / `收货单位经手人：____`（留空签名线）
  10. 底部公司名（`company.name`）
- **不打二维码**：三联版式内不出现 `PrintQrCode`（针式打不清 + 窄纸放不下）；纸版里的二维码保持现状不动
- 窄纸上的金额/数量格式沿用现有 `money()` 与右对齐口径，不新造格式化函数
- 明细行数上限只影响版式，不得为了"排版好看"丢数据（超行必须分页继续）

### 3.3 纸版(QR-026) 对齐模板

1. **地址移回标题行右侧**：把 `.qr026-company-header` 里的地址行删掉，标题行（`.qr026-title-row`）右侧增加「地址：{{ company.address }}」（与「送　货　单」同一行）
2. **说明行改回繁体**：`如上列货品…` → `如上列貨品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。`
3. **保留**合计金额行（`.qr026-total-row`）与单价/金额列 —— 不动，仅确认不改

---

## 4. 验证（Codex 自跑，结果写进报告）

- `cd jjx-web && npx vue-tsc --noEmit` → 0 错
- `cd jjx-web && npm run check:status-enums` → 通过
- 自查断言（贴命令与输出）：
  - `grep -n "layout === 'system'\|PrintCompanyHeader" jjx-web/src/views/sales/delivery/print.vue` → 无命中
  - `grep -n "triplicate" jjx-web/src/views/sales/delivery/print.vue` → 有版式选项 + 分支
  - `grep -n "貨品" jjx-web/src/views/sales/delivery/print.vue` → 命中说明行
- **不要**跑全量测试、**不要**重启服务、**不要**动数据库

---

## 5. 验收口径（用户侧，打印时看）

1. 打印页只剩两个版式：`纸版(QR-026)`、`三联纸(241×140)`；此前若选过系统版，打开自动落到纸版
2. 三联：打印对话框选 241×140（或驱动里的自定义纸型）→ 一页一联，9 列表头完整不截断，无二维码；明细超过 6 行顺延下一页
3. 纸版：地址在标题行右侧；说明行是「如上列**貨**品…」；合计金额行仍在
4. 两个版式的明细数值必须一致（同一次发货同一批数据）

---

## 6. 未纳入本任务（另立、待拍板）

- `quality_template_registry` id=26（JJX-QR-026 送货单）的 `print_mode` 仍为 `dual`（= 纸版+系统版），删掉系统版后该值语义过期 → 词表新值 + 迁移另立，不在本任务
- 浏览器打印三联的固有风险（每次要在打印对话框选纸型、关页眉页脚；要"一键连续多张/精确套打"需引入 Lodop/CLodop 类本地打印控件）= 新依赖，单独拍板
- 打印留痕（`usePrintLog`）本任务不加，保持现状
