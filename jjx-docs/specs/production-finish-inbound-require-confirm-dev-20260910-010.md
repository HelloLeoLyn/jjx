# 生产完工入库改为「确认入库后才进库存台账」实施规格

> 任务号：dev-20260910-010 ｜ 状态：⏳待实施 ｜ 建档：2026-09-10
> 用户已拍板方案 **B**；本文件是交 Codex 的实施图纸，Codex 沙箱连不上 MySQL，**SQL/迁移与库上验证由本机执行**

## 1. 需求

FQC 判定通过后**只生成产品入库单**（可打印、可查看），**必须人工「确认入库」后库存台账才增加**。

### 已定口径（用户 2026-09-10 拍板）

| # | 项 | 决定 |
|---|---|---|
| 1 | 建单后停留状态 | **待审批 PENDING(1)** |
| 2 | 完工入库是否要审批环节 | **不要**（生产入库是内部业务，无供应商） |
| 3 | 确认入库权限 | 新建权限点，**由权限系统配置**，一般授予仓库管理员（`inventory:ops`） |
| 4 | `produced_quantity` 回写时机 | **搬到"确认入库"之后**（现在在建单时就回写，会造成"订单显示已产出、库存没有"） |
| 5 | 成品口径（finished/completed/remaining） | **不动**（口径Y：FQC 通过即算生产完成，入账等确认） |
| 6 | 总体方案 | **B**：`approve()` 只做审批不再过账，加库存唯一收敛到 `confirm()` |

## 2. 现状（改动依据）

```
FQC pass → QualityActionServiceImpl.handleFqcPass(:131)
        → InventoryInboundServiceImpl.createFromProduction(orderId, inspectionId, passQty) (:1407)
            建单 FINISH-<工单号>-FQC-<质检单ID>，inbound_type=PRODUCTION_FINISH
            → 自己调 approve(order.getInboundId(), ...) (:1497)      ← 问题源头
        → approve() (:946) 内部 addStock() (:994) + 状态置 COMPLETED(10)
        → 库存台账立刻 +1（实测：inbound 3 在 16:02:54 建单即过账）
```

`addStock()` 有两个调用点：`confirm()`(:317「确认入库」) 和 `approve()`(:994「审批通过入库」)。**过账现在是两条路**，这就是要收敛掉的历史坑。

`confirm()` 已有正确的状态守卫，无需新增状态：
```java
purchaseConfirmable  = isPurchaseInbound(order) && status == APPROVED(2)
manualConfirmable    = !isPurchaseInbound(order) && status ∈ {DRAFT(0), PENDING(1), APPROVED(2)}
→ addStock() → (采购) createIqcQuarantine() → status = COMPLETED(10)
```
生产入库停在 PENDING(1) 正好落在 `manualConfirmable` 里，前端「确认入库」按钮条件也已覆盖。

## 3. 改动点（逐文件、逐处）

### 后端

**① `jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java`**

- `approve()`（约 946-1031，**只做审批**）
  - 删除 `addStock(order, approverId, approverName, "审批通过入库");`（约 :994）
  - 删除紧随的安全库存检查块（`alertService.checkSafeStockAlert(...)` 循环，约 :995-1006）→ 移到 `confirm()`（那边已有同类检查，二选一保留即可）
  - 状态改为已批准：`order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());`（原来是 COMPLETED(10)）
  - **保留**：行锁 `selectByIdForUpdate`、PENDING 状态守卫、质检联动（IQC 逐项复核结果处理）、审批人/备注写入
  - 注释改写：明确「审批不过账；库存过账唯一入口是 confirm()」

- `confirm()`（295-331，**唯一过账入口**）
  - 状态守卫**不动**（已覆盖生产入库停在 PENDING(1)）
  - `addStock(...)` 之后、置 COMPLETED 之前，**新增生产来源回写**：
    ```java
    if ("PRODUCTION".equals(order.getSourceType())) {
        writebackProducedQuantity(order, postedQtyThisTime);   // 见下
    }
    ```
    其中 `postedQtyThisTime` 取本次 `addStock` 实际过账的增量之和（**不要用整单 quantity**，避免重复确认累加；`addStock` 内部已按 `posted_quantity` 增量计算）
  - 置 COMPLETED(10)、安全库存检查保留

- 新增私有方法 `writebackProducedQuantity(InventoryInboundOrder order, BigDecimal postedQty)`
  - `prodOrder = productionOrderMapper.selectById(order.getSourceId())`；无 `salesOrderId` 直接返回
  - `salesOrder.producedQuantity += postedQty`，`updateById`
  - 异常只 `log.warn`，不影响主流程（与现状一致）

- `createFromProduction(workOrderId, inspectionId, inspectedPassQty)`（1407-1517）
  - 删除自动审批：把 `order.setOrderStatus(PENDING); updateById; approve(...)`（约 1494-1497）改为**只置 PENDING 并 updateById 后返回**
  - 删除 `produced_quantity` 回写块（约 1502-1515）——已搬到 `confirm()`
  - 其余（单号幂等、默认仓库、明细数量=本次 FQC 通过数、批次 `BATCH-<工单号>`、`inventoryItemService.ensure(PRODUCT, ...)`）**保持不变**

**② `jjx-server/src/main/java/com/jjx/inventory/event/InventoryEventBridge.java`**

- `onProductionCompleted`（约 26-43）现在 `create(...)` 后直接 `confirm(..., "system")` → **自动过账，与本次需求冲突**
- 处理：改为只 `create(...)`（不 confirm）；若该方法已被 FQC 路径取代且无实际触发，**直接删除该方法**（实施时先确认 `production.completed` 事件是否仍在发布，用 `log`/`grep` 确认后再删）

### 前端

**③ `jjx-web/src/views/inventory/inbound/index.vue`**（约 131-154）
- 两处「确认入库」按钮 `v-hasPermi` 由 `['inventory:inbound:edit']` 改为 `['inventory:inbound:confirm']`
- 状态条件保持 `∈ {DRAFT(0), PENDING(1), APPROVED(2)}`（生产单停在 1 会正常显示）

**④ `jjx-web/src/views/inventory/inbound/detail.vue`**
- 按钮 `v-if="inboundData.status === 2"`（约 :28）放宽为 `[0,1,2].includes(inboundData.status)`（与列表页一致；否则生产单停在 1 时详情页没有按钮）
- `v-hasPermi` 由 `['inventory:inbound:approve']`（约 :31）改为 `['inventory:inbound:confirm']`
- `handleConfirm`（约 354-372）里硬编码的 `currentUser = {id:'1', name:'当前用户'}` 是模拟数据 → 改为从用户 store 取真实用户（或后端从 Sa-Token 取，`confirm` 接口已有 operatorId/operatorName 参数）

### 权限与迁移

**⑤ `jjx-docs/sql/migrations/81_add_inbound_confirm_perm.sql`**（序号接当前最大；⚠ 79 已被 `79_cleanup_legacy_inventory_stock.sql`、80 已被 `80_hr_module.sql` 占用，实施前用 `bash scripts/db-migrate.sh --status` 复核再取号）

- 新增按钮权限（挂在入库作业 28 下，**icon/visible 按规范**，参考现有 129/130 的写法）：
  `menu_name='确认入库'`, `parent_id=28`, `menu_type='F'`, `perms='inventory:inbound:confirm'`, `order_num` 顺延
- 授权：`role_key IN ('inventory:ops','inventory:all')`（admin 有 `*:*:*`），沿用本仓库惯用的「父目录授权反向补齐」写法
- **幂等**：`WHERE NOT EXISTS`；重复执行不新增
- 执行方式：`bash scripts/db-migrate.sh 81_add_inbound_confirm_perm.sql --yes --task dev-20260910-010`（脚本自动先备份 + 记 `ops.schema.version`=81）；**执行前先跑 `--status` 确认没有更新的迁移插入序号**

## 4. 验收清单

| # | 操作 | 期望 |
|---|---|---|
| 1 | 执行迁移 81 | `SELECT COUNT(*) FROM sys_menu WHERE perms='inventory:inbound:confirm'` = 1；该菜单角色授权 ≥ 2 个角色 |
| 2 | 生产质检页 FQC 判定通过（pass>0） | 新增 1 张入库单：`inbound_no=FINISH-<工单号>-FQC-<质检单ID>`、`order_status=1`、`posted_quantity=0`、`sales_order.produced_quantity` **不变** |
| 3 | 同一步骤后查库存 | `inventory_stock` 合计、`inventory_stock_item` 该批次、`inventory_transaction` **全部不变**（这是本次核心验收） |
| 4 | 入库作业页点「确认入库」 | `order_status=10`、`posted_quantity=数量`、批次入库、台账 +数量、流水 1 条、`produced_quantity` +数量 |
| 5 | 再点一次「确认入库」 | 返回失败/提示，库存**不再增加**（状态守卫） |
| 6 | 分批：两次 FQC pass（如 60 + 40） | 两张待确认单（`...-FQC-<id1>` / `-FQC-<id2>`），各自确认各自入账 |
| 7 | 采购入库回归（IQC 全链路） | 提交检验 → 品质主管复核 → 状态=2 → 「确认入库」→ 过账 + 不合格进隔离台账，**行为与改动前一致** |
| 8 | 前端校验 | `cd jjx-web && npm run validate` 通过（含状态枚举门禁与文档门禁） |
| 9 | 后端编译 | `cd jjx-server && mvn -o clean compile` 通过 |

## 5. 【明确不做】

- 不改成品口径算法（`finished_quantity / completed_quantity / remaining_quantity`，口径Y）
- 不改 FQC 判定、返工/报废闭环、`remaining_fail_qty` 递减逻辑
- 不改 `addStock()` 的批次/汇总/流水实现
- 不改出库侧 `confirm()` 语义、不动调拨/盘点/退货的审批语义（**本次只动入库侧 approve 的过账**）
- 不新增业务表；不动 `product_stock`（已删除）

## 6. 风险与回归面

- **`approve()` 不再过账会影响所有入库类型**（采购/生产/退货/调拨/盘盈）→ 验收第 7 条必须真跑采购 IQC 全链路
- 安全库存预警时点后移到「确认入库」→ 报表/预警比完工时间晚，属预期
- 生产单停在「待审批(1)」但已无审批动作 → 若列表页对生产来源仍显示「审核入库」按钮，会误导；建议实施时对 `sourceType=PRODUCTION` 隐藏审批按钮（**待确认**，可留到下一轮）
- `writebackProducedQuantity` 必须用**本次过账增量**，否则重复确认会重复累加

## 7. 交接说明

- 实施顺序：迁移 81（本机执行）→ 后端改动 → 前端改动 → 按第 4 节验收
- 提交规范：`feat(inventory): 生产完工入库改为确认入库后入台账（任务码 dev-20260910-010）`，只提交本任务相关文件
- 本文件按规范登记：`jjx-docs/specs/`（README 维护标准 2：specs 内第一行写任务号）
