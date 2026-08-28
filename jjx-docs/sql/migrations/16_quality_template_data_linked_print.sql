-- dev-20260827-028 质量记录数据联动打印第一批
UPDATE quality_template_registry
SET category = 'data',
    biz_type = CASE
        WHEN record_no IN ('JJX-QR-039', 'JJX-QR-073') THEN 'quality_inspection'
        WHEN record_no = 'JJX-QR-037' THEN 'inventory_inbound'
        WHEN record_no IN ('JJX-QR-043', 'JJX-QR-082', 'JJX-QR-083') THEN 'operation_execution'
    END,
    update_time = CURRENT_TIMESTAMP
WHERE record_no IN (
    'JJX-QR-039', 'JJX-QR-037', 'JJX-QR-043',
    'JJX-QR-082', 'JJX-QR-083', 'JJX-QR-073'
);
