# 任务 1111 分析：报价转样品单，样品单数量没根据报价单默认

> 任务：dev-1787656259238（kanban_module=dev）
> 优先级：normal | 状态：未开始(0) | 负责人：未分配 | 截止：2026-08-25（已过期）
> 分析日期：2026-08-26
> 分析人：大黄 | 状态：仅分析，未改动代码

---

## 一、问题描述

报价单转为样品单时，"打样数量（sampleQty）"没有根据报价单的数量默认，而是固定填了 10。

## 二、现象确认（数据库实测）

| 单据 | 字段 | 值 |
|---|---|---|
| 报价单 QT2608250001（quotation_type=2 样品报价） | 明细 quantity | **1** |
| 由它转出的样品单 SP2608250001 | sample_qty | **10**（错误，硬编码默认值） |
| 同一张样品单 | total_quantity | **1**（明细求和，正确） |

结论：用户操作"转为样品单"时，预览器弹窗的"打样数量"输入框默认值为 10，未按报价单明细数量（1）默认，用户未修改即提交，导致样品单 header 打样数量错误。

## 三、根因分析

### 1. 前端主因：硬编码默认值

`jjx-web/src/components/OperationPreviewDialog/registry.ts` 第 175 行：

```js
{
  key: 'quotation.toSample',
  bizType: 'quotation',
  name: '转为样品单',
  fromStatus: [0, 1, 2, 3, 4, 6],
  fields: [{ key: 'sampleQty', label: '打样数量', type: 'number', required: true, defaultValue: 10 }],
  api: ({ bizId, values }) => sampleOrderApi.createFromQuotation(bizId, { sampleQty: Number(values.sampleQty) }),
}
```

- `defaultValue: 10` 是写死的，与报价单数据无关。
- 报价单实体 `SalesQuotation` 本身**没有数量字段**，数量只存在明细表 `sales_quotation_item.quantity`；
- 报价单列表接口（GET /sales/quotation/list）返回的就是实体，**不带数量聚合**，前端 row 上拿不到默认值来源。
- `OperationPreviewDialog/index.vue` 打开时只用 `field.defaultValue` 初始化表单值，不支持动态取数。

### 2. 后端无兜底

`jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java` `createFromQuotation()`：

```java
order.setSampleQty(sampleQty);   // 直接用前端传参，不传就是 null
```

- 前端不传（或传 null）时 header 打样数量为 null，不会从报价单明细回退取值。
- 明细复制（copyQuotationItemsToOrder）本身是正确继承报价单明细 quantity 的；
- total_quantity 由 updateTotalQuantityByItems 按明细求和，也是对的（DEV-806 口径）。

## 四、修复建议

### 方案 A（推荐，前端为主）

参考代码库已有先例：`jjx-web/src/views/sales/sample-order/index.vue` 的 `openPreview()` 对 `sample.markReady` 已实现"动态默认值"：

```js
// 动态默认值：实际打样数量默认取单据数量
if (opKey === 'sample.markReady' && row.sampleQty) {
  op = { ...op, fields: (op.fields || []).map((f) =>
    f.key === 'sampleQty' ? { ...f, defaultValue: row.sampleQty } : f) }
}
```

对报价单页 `jjx-web/src/views/sales/quotation/index.vue` 的 `openPreview()` 增加：

1. 当 `opKey === 'quotation.toSample'` 时，异步调用 `quotationApi.getItems(quotationId)`（接口已存在：GET /sales/quotation/{quotationId}/items）；
2. 按明细 quantity **求和**作为 sampleQty 默认值（与 DEV-806 total_quantity 聚合口径一致）；
3. 无明细/请求失败时兜底 1（或保留 10）；
4. 同时去掉 registry.ts 中该字段写死的 `defaultValue: 10`，避免误导。

### 方案 B（后端防御，建议一并做）

`SampleOrderServiceImpl.createFromQuotation()` 中 sampleQty 为 null 时，按报价单明细 quantity 求和默认：

```java
if (sampleQty == null) {
    sampleQty = quotationItemMapper.selectList(...)  // 按 quotationId 查明细
        .stream().mapToInt(it -> it.getQuantity() == null ? 0 : it.getQuantity()).sum();
}
order.setSampleQty(sampleQty);
```

保证 API 直接调用方不传参也能得到合理值。

### 待确认

- 默认值口径：**明细求和**（建议，与 DEV-806 一致） vs 第一条明细数量。现有数据为单明细，两者等价；多明细场景需定口径。

## 五、影响面

- 仅影响"报价转样品单"创建时的默认值，不影响已创建的样品单数据（SP2608250001 的 sample_qty=10 是已存在脏数据，如需修正另行处理）。
- `sample.markReady`（实际打样数量，registry 第 241 行）同样有 defaultValue: 10，但前端已用 row.sampleQty 动态覆盖，仅作兜底，可不改。
