# 生产入库区分「来源批次数量 / 本单数量」并展示处置单据

任务：dev-20260930-015；执行者：Codex（额度耗尽由 dahuang 接手收尾）；日期：2026-09-30。

## 背景与证据

用户核查 WO260930001-FI01 时发现页面表头与明细都显示 45，怀疑"本批总数"丢了 5 件。只读核查：

```sql
SELECT o.inbound_no, l.lot_quantity, l.pass_quantity, l.fail_quantity, o.total_quantity, i.posted_quantity
FROM inventory_inbound_order o
JOIN inventory_inbound_item i ON i.inbound_id = o.inbound_id
JOIN quality_lot l ON l.lot_id = i.lot_id
WHERE o.inbound_no = 'WO260930001-FI01';
-- WO260930001-FI01 | 50 | 45 | 5 | 45 | 45
```

- 来源检验批 QL260930003：总数 50、检验合格 45、检验不合格 5。
- 入库单 WO260930001-FI01：本单数量 45、已入库（posted）45。
- 页面原「总数量」读的是**入库单自身的 totalQuantity**（本单明细合计），并非来源检验批总数 → 是**页面数量口径没表达清楚**，不是数据错：这张单实际入库 45 件是正确的。
- 剩余 5 件已有去向：2 件让步入库（IN260930002）、3 件报废（SCR260930001）；故该批**累计入库 47 件**，FI01 本单仍是 45 件。

## 结论（口径定稿）

| 展示项 | 取值 |
|---|---|
| 来源批次数量 | 来源成品检验批的原批数量（`quality_lot.lot_quantity`） |
| 本单数量 | 入库单自身 `inventory_inbound_order.total_quantity` |
| 本单已入库 | 明细 `posted_quantity` 合计 |
| 来源批不合格 | `quality_lot.fail_quantity` |

- **只读投影**：不修改入库单数量、库存、流水与表结构；**不允许**把原入库单数量直接改成 50。
- 来源缺失或不完整（无批次关联 / 数量为空）时显示「—」，**不用本单数量兜底**，也不伪造总数。
- 详情额外列出同批相关单据（含让步入库、红冲单）与报废单及其状态，避免把待入库/已作废单据误当已完成处置。

## 改动清单

后端（只读查询，无写入）：
- `dto/vo/InboundLotSummaryVO.java`（新）：来源批 + 关联入库单/报废单的只读投影。
- `mapper/InventoryInboundItemMapper.java`：新增 `selectSourceLots`（LEFT JOIN quality_lot，保留缺批次关联行）、`selectLotInboundDocuments`（按批次汇总各入库单数量与过账量，红冲保留负数）、`selectLotScrapDocuments`（保留报废单状态）。
- `service/impl/InventoryInboundServiceImpl.java`：`populateSourceLots`（列表一次查、详情批量查关联单据；同 lotId 去重后求和；任一来源不完整则 `sourceLotQuantity`/`sourceRejectedQuantity` 留 null）。
- `dto/vo/InboundVO.java`：+`sourceLotQuantity`、+`sourceRejectedQuantity`、+`sourceLots`。

前端：
- `types/inventory/inbound.ts`：新增 `InboundLotSummaryVO`，`InboundVO` 补三个字段。
- `views/inventory/inbound/components/InboundLotTrace.vue`（新）：来源批区块（批次数量/合格/不合格 + 相关入库单表 + 报废单表）。
- `index.vue` / `detail.vue` / `InboundDetail.vue`：拆「来源批次数量 / 本单数量 / 本单已入库 / 来源批不合格」，明细列改「本单数量」，挂载追溯块。

测试：
- `test/java/com/jjx/inventory/InboundSourceQuantityTest.java`：3 例（来源批总量与本单数量分离；同批多明细去重；来源不完整不伪造总数）。

## 验证

- `mvn -o compile` 通过；`mvn -o test -Dtest=InboundSourceQuantityTest` → Tests run: 3, Failures: 0。
- `npx vue-tsc --noEmit` 通过。
- 未修改任何数据、库存、流水或表结构（纯读投影）。

## 遗留

- 按约定未做 E2E：前端改动 vite 热更即见，**后端需重启**后生效，界面验收由用户执行。
- 报废/让步入库单据仅只读展示，不在本页联动处置。
