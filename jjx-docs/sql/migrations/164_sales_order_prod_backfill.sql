-- ============================================================================
-- 164_sales_order_prod_backfill.sql
-- 任务码：dev-20260921-037（OrderMapper.updateById 字段白名单丢字段 —— 配套数据收口）
--
-- 背景：OrderMapper.updateById 的覆写 SQL 只覆盖 remark/order_status/total_quantity + 样品扩展字段，
--   produced_quantity / prod_status / shipped_quantity 等字段走 updateById 时被静默丢弃：
--   · 完工入库回写（InventoryInboundServiceImpl.writebackProducedQuantity）日志打印「累计=2」但库里仍 0；
--   · 销售出库回写 shipped_quantity（InventoryOutboundServiceImpl）同样丢；
--   实例：SO260921001（order_id=2）工单 WO-PL2609210001-01 完工 2、完工入库已过账、库存 PRODUCT 可用 2，
--   但订单 produced_quantity=0、prod_status=1（无生产）。
-- 代码侧已修（白名单补全 sales_order 全量列，见同任务 OrderMapper.java）。
--
-- 本迁移只做**存量收口**（幂等）：
--   ① produced_quantity = 该订单下 WORK_ORDER 的 completed_quantity 合计
--   ② prod_status：0 完工 → 1 无生产；0<完工<计划 → 2 部分生产中；完工≥计划 → 4 生产完成
--   （3 全部生产中 需要"在制"信息，本次不推断、保持原值）
--
-- 备份：~/jjx-backups/sales_order_prod_backfill_20260921-*.sql（表级 guard）
-- ============================================================================

UPDATE sales_order so
  JOIN (
        SELECT sales_order_id,
               SUM(COALESCE(completed_quantity, 0)) AS done_qty,
               SUM(COALESCE(planned_quantity, 0))   AS plan_qty
          FROM production_order
         WHERE sales_order_id IS NOT NULL
           AND order_type = 'WORK_ORDER'
         GROUP BY sales_order_id
       ) x ON x.sales_order_id = so.order_id
   SET so.produced_quantity = x.done_qty,
       so.prod_status = CASE
                          WHEN x.done_qty <= 0 THEN 1
                          WHEN x.plan_qty > 0 AND x.done_qty >= x.plan_qty THEN 4
                          ELSE 2
                        END
 WHERE so.deleted = 0;
-- 幂等：直接按工单公式重算，可重复执行。
