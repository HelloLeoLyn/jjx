# 库存采购到 IQC 实测报告（2026-09-07）

## 范围

本轮验证采购收货已生成的入库单进入 IQC 后的审核、库存过账、不合格品隔离及重复确认保护。被测单据为 `PO202609070002`（`inbound_id=2`）。

执行前全库备份为 `jjx-docs/sql/backups/jjx_erp_db_backup_20260907-2240_before-purchase-iqc-test.sql`，MD5 `12C8E5FD3BB2D013C1FBB4AA1D0B449A`，包含 98 个 `CREATE TABLE`。

## 结果

| 检查项 | 结果 | 证据 |
|---|---|---|
| 三项 IQC 单项审核 | 通过 | 明细 4、5、6 均返回 `code=200,data=true`；质量记录 1、2、3 均为 `APPROVED` |
| 合格数量过账 | 通过 | `posted_quantity` 分别为 1195、1200、1200，库存合计 3595 |
| 部分允收与隔离 | 通过 | 明细 4 为 `FAIL/PARTIAL_ACCEPT`，1200 中允收 1195、隔离 5，隔离状态 `PENDING` |
| 库存流水 | 通过 | 3 条 `PURCHASE` 流水合计 3595，另有数量 5 的 `INBOUND_IQC` 隔离流水 |
| 单据完成 | 通过 | 入库单 `order_status=10`，总体检验结论 `OTHER` |
| 重复确认保护 | 通过 | 第二次确认返回 `data=false`，流水仍为 4 条，库存未重复增加 |

## 校验补充

- `vue-tsc --noEmit` 通过。
- `npm run check:status-enums` 未通过：发现 `OrderTableActions.vue` 中 3 条生产订单状态魔法值；与本轮 IQC 实测无直接关联，未扩大 baseline。
- 工作区已有 `jjx-web/src/components.d.ts` 与 `jjx-web/src/views/inventory/iqc/index.vue` 并行改动，本轮未覆盖或回退。

## 结论

“逐项审核 → 允收数量入可用库存 → 拒收数量进入隔离台账 → 防重复过账”链路实测通过。下一阶段可继续验证隔离品退货、返工复检和报废审批分支。
