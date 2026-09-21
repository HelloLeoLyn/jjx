-- ============================================================================
-- 168_sales_order_price_backfill.sql
-- 任务码：dev-20260921-040（SO260921001 数据收口：金额口径）
--
-- 背景：SP260921001 转量产生成 SO260921001（order_id=2）时，样品单本身无单价（unit_price=0），
--   预填把 0 价带进订单 → 订单/明细金额、税额、含税金额、应收全为 0，
--   后续出库/回款/开票全链路金额为 0（财务口径断链）。
--   来源报价单 QT2609210001 同产品（JST268MEOO）有价：2 × 220.00 = 440.00。
--
-- 本迁移只做**存量收口**（幂等）：
--   ① 明细补价：明细价为空/0，且来源报价单（order.quotation_id）存在同产品（product_id 或 product_code）
--      的有效单价 → 按报价单价补齐，金额=数量×单价
--   ② 订单表头按明细重算：total_amount=Σ明细金额；tax_amount 按订单 tax_rate 计算；
--      total_amount_with_tax=小计+税额；final_amount=含税-折扣（仅对"表头金额仍为 0 且明细有金额"的订单生效，
--      不覆盖人工维护过的金额）
--
-- 代码侧配套（同任务）：
--   · 前端转产量预填按来源报价单自动带出单价（OrderForm.autoFillPriceFromQuotation，可改）
--   · 后端发货校验：订单金额为 0 拒绝发货（OrderStatusServiceImpl.shipOrder）
--
-- 幂等：可重复执行。
-- ============================================================================

-- ① 明细补价（报价单同产品优先，product_id 命中优先于 product_code）
-- ⚠️ 表间 collation 不一致（sales_order_product.product_code=utf8mb4_unicode_ci，
--    sales_quotation_item.product_code=utf8mb4_0900_ai_ci），直接比会报 ERROR 1267，故显式 COLLATE。
UPDATE sales_order_product sop
  JOIN sales_order so ON so.order_id = sop.order_id
  JOIN sales_quotation_item qi
    ON qi.quotation_id = so.quotation_id
   AND ((qi.product_id IS NOT NULL AND qi.product_id = sop.product_id)
        OR (qi.product_code IS NOT NULL
            AND qi.product_code COLLATE utf8mb4_unicode_ci = sop.product_code))
   SET sop.unit_price = qi.unit_price,
       sop.amount = ROUND(COALESCE(sop.quantity, 0) * qi.unit_price, 2)
 WHERE so.deleted = 0
   AND COALESCE(so.quotation_id, 0) > 0
   AND (sop.unit_price IS NULL OR sop.unit_price = 0)
   AND qi.unit_price > 0;

-- ② 订单表头重算（仅补 0 金额订单）
UPDATE sales_order so
  JOIN (
        SELECT order_id, SUM(COALESCE(amount, 0)) AS item_amount
          FROM sales_order_product
         GROUP BY order_id
       ) x ON x.order_id = so.order_id
   SET so.total_amount = x.item_amount,
       so.tax_amount = ROUND(x.item_amount * COALESCE(so.tax_rate, 0) / 100, 2),
       so.total_amount_with_tax = x.item_amount + ROUND(x.item_amount * COALESCE(so.tax_rate, 0) / 100, 2),
       so.final_amount = x.item_amount + ROUND(x.item_amount * COALESCE(so.tax_rate, 0) / 100, 2)
                         - COALESCE(so.discount_amount, 0)
 WHERE so.deleted = 0
   AND COALESCE(so.total_amount, 0) = 0
   AND x.item_amount > 0;
