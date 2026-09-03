-- 49_qr005_print_go_live.sql
-- 生产指令单打印落地（任务1296）
-- 核查：production/order/print.vue 已就绪（A4+工单二维码+数量汇总）、路由/入口已有、
--       1296 本次补打印留痕（print-log 接 quality_template_print_log）
-- 模板升级：JJX-QR-005 制造指令单 category blank→data（打印中心从"规划中"变"已联动"，出现"去业务模块打印"）
UPDATE quality_template_registry SET category = 'data'
WHERE record_no = 'JJX-QR-005' AND category = 'blank';
