-- 执行人：Codex；原因：清理历史档案 OCR E2E 测试脏数据后重新验证；任务：dev-20260912-013
-- 仅删除只读审计确认无任何生产/销售/库存引用的测试档案 1、2 及其草稿产物。
START TRANSACTION;

DELETE FROM engineering_process_icon_sample
WHERE archive_id IN (1, 2);

DELETE FROM sys_attachment
WHERE biz_type = 'engineering_archive'
  AND biz_id IN (1, 2);

DELETE FROM engineering_routing_item
WHERE routing_id = 2;

DELETE FROM engineering_bom_item
WHERE bom_id = 2;

DELETE FROM engineering_archive_import
WHERE archive_id IN (1, 2)
  AND file_hash IN (
    '971a5fd9ed6c2bb96c81a3e5b4c3957be36c37524055e9c2c8aaf509ce6c8397',
    'b166a4a2e38d41917607830fb96d5da0476a17c396149ead83e70554e8cc5197'
  );

DELETE FROM engineering_routing
WHERE routing_id = 2
  AND routing_code = 'RT-JST-263MHMC'
  AND approve_status = 1;

DELETE FROM engineering_bom
WHERE bom_id = 2
  AND bom_code = 'BOM-JST-263MHMC'
  AND approve_status = 1;

DELETE FROM product
WHERE product_id = 2
  AND product_code = 'JST-263MHMC'
  AND product_status = 1
  AND from_source = 'history_archive';

COMMIT;
