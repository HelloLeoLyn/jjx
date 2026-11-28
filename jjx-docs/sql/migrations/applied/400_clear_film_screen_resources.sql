-- dev-20261128-002：用户要求清空全部菲林、网版及7294个网框，重新录入真实资料。
-- 破坏性数据清理专用文件；不创建/删除任何持久业务表，不关闭外键，不重置自增号。
-- 仅由 scripts/db-migrate.sh 执行；手工备份：jjx_erp_db_backup_20261128-1052.sql。
-- 保留产品、路线、打样、工单、规范、共享附件文件及非本域维护记录。
-- 临时检查表仅属于当前连接，CHECK失败即使mysql退出，未提交的删除也会回滚。
CREATE TEMPORARY TABLE film_screen_clear_guard_400 (
    check_name VARCHAR(100) NOT NULL,
    ok TINYINT NOT NULL CHECK (ok = 1)
) ENGINE=InnoDB;

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

-- 串行化读取保护扫描到的记录与插入间隙；数据/引用变化时停止，不扩大删除范围。
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_artwork_6', COUNT(*) = 6 FROM print_artwork;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_versions_5', COUNT(*) = 5 FROM print_artwork_version;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_copies_2', COUNT(*) = 2 FROM print_film_copy;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_requests_0', COUNT(*) = 0 FROM print_film_request;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_plates_1', COUNT(*) = 1 FROM engineering_screen_plate;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_stretches_1', COUNT(*) = 1 FROM print_frame_stretch;
INSERT INTO film_screen_clear_guard_400
SELECT 'expected_frames_7294', COUNT(*) = 7294 FROM engineering_screen_frame;
INSERT INTO film_screen_clear_guard_400
SELECT 'legacy_films_empty', COUNT(*) = 0 FROM engineering_film;
INSERT INTO film_screen_clear_guard_400
SELECT 'no_routing_refs', COUNT(*) = 0 FROM engineering_routing_item
WHERE artwork_version_id IS NOT NULL OR default_plate_id IS NOT NULL;
INSERT INTO film_screen_clear_guard_400
SELECT 'no_sample_refs', COUNT(*) = 0 FROM sales_sample_process
WHERE artwork_version_id IS NOT NULL OR default_plate_id IS NOT NULL OR actual_plate_id IS NOT NULL;
INSERT INTO film_screen_clear_guard_400
SELECT 'no_production_usage', COUNT(*) = 0 FROM print_use_segment;
INSERT INTO film_screen_clear_guard_400
SELECT 'no_kit_shortages', COUNT(*) = 0 FROM print_kit_shortage;
INSERT INTO film_screen_clear_guard_400
SELECT 'no_spec_refs', COUNT(*) = 0 FROM product_work_spec_item
WHERE snapshot_json REGEXP '(artworkVersionId|defaultPlateId|actualPlateId|DEMO-PW-|DEMO-PL-|DEMO-F0)'
   OR resource_type IN ('FILM','ARTWORK','ARTWORK_VERSION','SCREEN_PLATE');
INSERT INTO film_screen_clear_guard_400
SELECT 'no_attachment_refs', COUNT(*) = 0 FROM sys_attachment_ref
WHERE source_type LIKE 'print%' OR source_type LIKE '%film%' OR source_type LIKE '%screen%';

SET @film_clear_products_400 = (SELECT COUNT(*) FROM product);
SET @film_clear_routes_400 = (SELECT COUNT(*) FROM engineering_routing);
SET @film_clear_route_items_400 = (SELECT COUNT(*) FROM engineering_routing_item);
SET @film_clear_samples_400 = (SELECT COUNT(*) FROM sales_sample_order);
SET @film_clear_sample_items_400 = (SELECT COUNT(*) FROM sales_sample_process);
SET @film_clear_orders_400 = (SELECT COUNT(*) FROM production_order);
SET @film_clear_files_400 = (SELECT COUNT(*) FROM sys_attachment);
SET @film_clear_specs_400 = (SELECT COUNT(*) FROM product_work_spec_item);

-- 用户明确授权的全量清理；子记录先删，所有删除均在本事务内。
DELETE FROM engineering_screen_plate;
DELETE FROM print_frame_stretch;
DELETE FROM engineering_screen_frame;
DELETE FROM print_film_copy;
DELETE FROM print_film_request;
-- 解除本域主档与版本之间的循环外键，外键约束始终开启。
UPDATE print_artwork SET default_version_id = NULL WHERE default_version_id IS NOT NULL;
DELETE FROM print_artwork_version;
DELETE FROM print_artwork;
DELETE FROM engineering_film;
DELETE FROM engineering_resource_product_rel
WHERE resource_type IN ('ARTWORK','ARTWORK_VERSION','FILM_COPY','FILM_REQUEST','SCREEN_FRAME','SCREEN_PLATE');
-- 清除本域命令重放结果，避免旧请求返回已删除资源的成功快照；保留其它域审计。
DELETE FROM engineering_resource_maintenance
WHERE resource_type IN ('ARTWORK','ARTWORK_VERSION','FILM_COPY','FILM_REQUEST','SCREEN_FRAME','SCREEN_PLATE');

INSERT INTO film_screen_clear_guard_400
SELECT 'all_target_rows_empty',
    (SELECT COUNT(*) FROM print_artwork) +
    (SELECT COUNT(*) FROM print_artwork_version) +
    (SELECT COUNT(*) FROM print_film_copy) +
    (SELECT COUNT(*) FROM print_film_request) +
    (SELECT COUNT(*) FROM engineering_screen_plate) +
    (SELECT COUNT(*) FROM print_frame_stretch) +
    (SELECT COUNT(*) FROM engineering_screen_frame) +
    (SELECT COUNT(*) FROM engineering_film) = 0;
INSERT INTO film_screen_clear_guard_400
SELECT 'protected_business_counts_unchanged',
    @film_clear_products_400 = (SELECT COUNT(*) FROM product) AND
    @film_clear_routes_400 = (SELECT COUNT(*) FROM engineering_routing) AND
    @film_clear_route_items_400 = (SELECT COUNT(*) FROM engineering_routing_item) AND
    @film_clear_samples_400 = (SELECT COUNT(*) FROM sales_sample_order) AND
    @film_clear_sample_items_400 = (SELECT COUNT(*) FROM sales_sample_process) AND
    @film_clear_orders_400 = (SELECT COUNT(*) FROM production_order) AND
    @film_clear_files_400 = (SELECT COUNT(*) FROM sys_attachment) AND
    @film_clear_specs_400 = (SELECT COUNT(*) FROM product_work_spec_item);

COMMIT;
SELECT 'dev-20261128-002: film/screen cleanup committed' AS result;
DROP TEMPORARY TABLE film_screen_clear_guard_400;
