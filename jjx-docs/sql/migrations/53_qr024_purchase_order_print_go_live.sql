-- 53_qr024_purchase_order_print_go_live.sql
-- 采购订单打印落地（任务1318，2026-09-03）
-- 核查：purchase/order/print.vue + 路由 + 列表按钮均已存在；本次补打印留痕
-- 模板升级：JJX-QR-024 采购订单 category blank→data（打印中心"规划中"→"已联动"）
UPDATE quality_template_registry SET category = 'data'
WHERE record_no = 'JJX-QR-024' AND category = 'blank';
