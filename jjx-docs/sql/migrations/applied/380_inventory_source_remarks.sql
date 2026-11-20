-- dev-20261120-002：只补物料档案的迁移源行备注，不改原凭证、库存数量/成本/流水。
-- 输入由scripts/inventory-source-remarks.py --write从原Excel和正式JSON核对后生成，客户原文不入Git。
-- 必须从仓库根运行db-migrate；输入缺失/不匹配/并发修改即报错回滚。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @inventory_source_remarks = NULL;
SOURCE .tmp/inventory-source-remarks/input.sql;
DROP PROCEDURE IF EXISTS repair_inventory_source_remarks_380;
DELIMITER $$
CREATE PROCEDURE repair_inventory_source_remarks_380()
BEGIN
    DECLARE expected_changes INT DEFAULT 0;
    DECLARE changed_rows INT DEFAULT 0;
    DECLARE batch_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF @inventory_source_remarks IS NULL OR NOT JSON_VALID(@inventory_source_remarks) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少已核对的私有迁移备注输入';
    END IF;
    IF JSON_UNQUOTE(JSON_EXTRACT(@inventory_source_remarks,'$.sourceSha256')) <> '6c9b13cf39b07b1dbae14d76d60f0002c1d3cf3191bace99f4102ae4704598f2'
       OR JSON_UNQUOTE(JSON_EXTRACT(@inventory_source_remarks,'$.batchCode')) <> 'MIG20261117-6c9b13cf39b0' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='本次备注修复仅限指定原始迁移批次';
    END IF;
    SELECT inbound_id INTO batch_id FROM inventory_inbound_order
      WHERE inbound_no='MIG20261117-6c9b13cf39b0' AND source_type='INV_MIGRATION'
        AND trace_id='6c9b13cf39b07b1dbae14d76d60f0002c1d3cf3191bace99f4102ae4704598f2';
    IF batch_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原迁移凭证缺失';
    END IF;
    CREATE TEMPORARY TABLE migration_remark_sources_380 (
        source_row INT PRIMARY KEY, material_id BIGINT NOT NULL, note TEXT NOT NULL
    ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE TEMPORARY TABLE migration_remark_materials_380 (
        material_id BIGINT PRIMARY KEY, before_remark TEXT, after_remark TEXT NOT NULL
    ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    INSERT INTO migration_remark_sources_380
      SELECT source_row,material_id,note FROM JSON_TABLE(@inventory_source_remarks,'$.sources[*]'
        COLUMNS(source_row INT PATH '$.sourceRow',material_id BIGINT PATH '$.materialId',note TEXT PATH '$.note')) j;
    INSERT INTO migration_remark_materials_380
      SELECT material_id,before_remark,after_remark FROM JSON_TABLE(@inventory_source_remarks,'$.materials[*]'
        COLUMNS(material_id BIGINT PATH '$.materialId',before_remark TEXT PATH '$.before',after_remark TEXT PATH '$.after')) j;
    IF (SELECT COUNT(*) FROM migration_remark_sources_380) <> 389 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='来源备注数量不一致';
    END IF;
    IF (SELECT COUNT(*) FROM migration_remark_materials_380) <> 384 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='来源物料数量不一致';
    END IF;
    IF EXISTS(SELECT 1 FROM migration_remark_materials_380 WHERE CHAR_LENGTH(after_remark)>1000) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='备注超过档案长度，禁止截断';
    END IF;
    IF EXISTS(SELECT 1 FROM migration_remark_sources_380 s LEFT JOIN inventory_inbound_item i
           ON i.inbound_id=batch_id AND i.sort_order=s.source_row AND i.material_id=s.material_id WHERE i.item_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原始来源行归属不一致';
    END IF;
    IF EXISTS(SELECT 1 FROM migration_remark_sources_380 s LEFT JOIN migration_remark_materials_380 m
           ON m.material_id=s.material_id
           WHERE m.material_id IS NULL OR LOCATE(BINARY CONCAT('【迁移来源 MIG20261117-6c9b13cf39b0 / 源行',s.source_row,'】',CHAR(10),s.note),BINARY m.after_remark)=0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标备注未完整保留对应源行原文';
    END IF;
    IF EXISTS(SELECT 1 FROM migration_remark_materials_380 m LEFT JOIN migration_remark_sources_380 s
           ON s.material_id=m.material_id WHERE s.material_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标物料没有来源备注';
    END IF;
    START TRANSACTION;
    IF EXISTS(SELECT 1 FROM migration_remark_materials_380 p LEFT JOIN inventory_material m ON m.material_id=p.material_id
        WHERE m.material_id IS NULL OR NOT ((BINARY m.remark <=> BINARY p.before_remark) OR (BINARY m.remark <=> BINARY p.after_remark))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='物料备注已被修改，请重新只读核对生成输入';
    END IF;
    SELECT COUNT(*) INTO expected_changes FROM inventory_material m JOIN migration_remark_materials_380 p ON p.material_id=m.material_id
      WHERE NOT (BINARY m.remark <=> BINARY p.after_remark);
    UPDATE inventory_material m JOIN migration_remark_materials_380 p ON p.material_id=m.material_id
      SET m.remark=p.after_remark,m.update_by='Codex',m.update_time=NOW()
      WHERE BINARY m.remark <=> BINARY p.before_remark AND NOT (BINARY m.remark <=> BINARY p.after_remark);
    SET changed_rows=ROW_COUNT();
    IF changed_rows<>expected_changes OR EXISTS(SELECT 1 FROM migration_remark_materials_380 p
        JOIN inventory_material m ON m.material_id=p.material_id WHERE NOT(BINARY m.remark <=> BINARY p.after_remark)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='备注更新数量/回查不符，已回滚';
    END IF;
    COMMIT;
    SELECT changed_rows AS material_remarks_updated;
    DROP TEMPORARY TABLE migration_remark_sources_380;
    DROP TEMPORARY TABLE migration_remark_materials_380;
END$$
DELIMITER ;
CALL repair_inventory_source_remarks_380();
DROP PROCEDURE repair_inventory_source_remarks_380;
