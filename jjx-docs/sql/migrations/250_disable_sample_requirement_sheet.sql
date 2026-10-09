-- 250_disable_sample_requirement_sheet.sql
-- 屏蔽「样品需求单」QR-065（dev-20261009-022，2026-10-09）
-- 背景：QR-065「样品需求单」原被"借壳"到询价打印（biz_type=sales_inquiry，print_component=sales/inquiry/print.vue），名实不符；
--       业务上"样品需求单"应归样品管理，归属未审定。故先停用并解除询价绑定，待业务核查后再实现。
--       （对齐 QR-069/070 的"blank + 停用"形态）
-- 幂等：重复执行为 0 影响。
UPDATE quality_template_registry
SET status = 2,
    biz_type = NULL,
    print_component = NULL,
    print_mode = NULL,
    category = 'blank'
WHERE record_no = 'JJX-QR-065'
  AND (status <> 2 OR biz_type IS NOT NULL OR print_component IS NOT NULL OR print_mode IS NOT NULL OR category <> 'blank');
