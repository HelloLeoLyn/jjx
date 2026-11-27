-- risk: low
-- 菲林出片申请与实物分离（dev-20261127-002，原型 b61afa0f）
-- Owner已批准新增独立申请表 + 现有实物表可空关联；不生成/更新/删除任何业务数据。
-- 前置：Owner已批准；本轮另明确授权Codex执行新备份（排除 hr_employee）；走 db-migrate.sh。
-- 旧 REQUESTED/SENT 实物占位数据不得自动转成申请，必须单独核对。
DROP PROCEDURE IF EXISTS jjx_film_request_399_preflight;
DELIMITER $$
CREATE PROCEDURE jjx_film_request_399_preflight()
BEGIN
    IF EXISTS(SELECT 1 FROM print_film_copy WHERE status IN ('REQUESTED','SENT')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='发现旧出片占位记录，须核对后再执行迁移399';
    END IF;
END$$
DELIMITER ;
CALL jjx_film_request_399_preflight();
DROP PROCEDURE jjx_film_request_399_preflight;

CREATE TABLE IF NOT EXISTS print_film_request (
    request_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '出片申请ID',
    request_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出片申请编号',
    version_id BIGINT NOT NULL COMMENT '所属菲林版本',
    requested_quantity INT NOT NULL COMMENT '原申请张数，不随回片改写',
    material VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '片基材质',
    thickness_mm DECIMAL(10,3) NOT NULL COMMENT '厚度mm',
    size VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '尺寸',
    due_date DATE NOT NULL COMMENT '期望回片日期',
    remark VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请说明',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '并发版本号',
    create_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_print_film_request_no (request_no),
    UNIQUE KEY uk_print_film_request_version (request_id,version_id),
    KEY idx_print_film_request_version (version_id,request_id),
    CONSTRAINT fk_print_film_request_version FOREIGN KEY (version_id) REFERENCES print_artwork_version(version_id),
    CONSTRAINT chk_print_film_request_qty CHECK (requested_quantity BETWEEN 1 AND 500),
    CONSTRAINT chk_print_film_request_thickness CHECK (thickness_mm > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菲林出片申请（回片数量与进度由关联实物派生）';

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='print_film_copy' AND COLUMN_NAME='film_request_id'),
    'SELECT ''实物申请关联列已存在''', 'ALTER TABLE print_film_copy ADD COLUMN film_request_id BIGINT NULL COMMENT ''来源出片申请，历史实物可为空''');
PREPARE film_ddl FROM @ddl;
EXECUTE film_ddl;
DEALLOCATE PREPARE film_ddl;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='print_film_copy' AND CONSTRAINT_NAME='fk_print_film_copy_request'),
    'SELECT ''实物申请关联约束已存在''', 'ALTER TABLE print_film_copy ADD CONSTRAINT fk_print_film_copy_request FOREIGN KEY (film_request_id,version_id) REFERENCES print_film_request(request_id,version_id)');
PREPARE film_ddl FROM @ddl;
EXECUTE film_ddl;
DEALLOCATE PREPARE film_ddl;
