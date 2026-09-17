-- ============================================================================
-- 123: 质量报告挂接检验批模型 ——质量重构批3 / dev-20260917-012
-- 口径：QR-037 进料检验报告 / QR-039 成品检验报告 统一由"检验批"取数（AQL/AC/RE + 逐件实测 + CR/MA/MI），
--       打印组件指向新的 /quality/print/lot-report 页；台账同时标记 category=data、print_mode=dual。
-- 幂等：直接按 record_no 覆盖（值固定，可重复执行）。
-- ============================================================================
USE `jjx_erp_db`;

UPDATE quality_template_registry
SET print_component = 'views/quality/print/LotReportPage.vue',
    print_mode = 'dual',
    category = 'data',
    remark = CONCAT(COALESCE(remark, ''), ' | 2026-09-17 dev-20260917-012：改由检验批取数（AQL/AC/RE + 逐件实测）'),
    update_time = NOW()
WHERE record_no IN ('JJX-QR-037', 'JJX-QR-039');

SELECT id, record_no, record_name, category, print_component, print_mode
FROM quality_template_registry WHERE record_no IN ('JJX-QR-037', 'JJX-QR-039');
