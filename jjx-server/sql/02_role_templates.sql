-- =====================================================
-- 角色模板脚本：每模块 3 角色（all / ops / review）
-- 2026-08-05 新增
--
-- 设计：
--   {module}:all    = 模块全部权限（页面+按钮）
--   {module}:ops    = view + 业务动作（add/edit/delete/export/import/convert/confirm/submit...），不含审核
--   {module}:review = view + 审核动作（approve/review/reject/release/obsolete）
--
-- 规则：
--   - 审核类动作后缀：approve/review/reject/release/obsolete（归 review）
--   - 无审核类权限码的模块（如 production）不建 review 角色
--   - system/log 模块不建（系统管理权限敏感，只给管理员）
--   - 幂等：INSERT IGNORE（role_key 唯一）+ 绑定前先清后插，可重复执行
--   - 已存在的 role_key（sales:all / product:all / enginering:all[拼写错，历史保留]）自动跳过不覆盖
-- =====================================================

DROP PROCEDURE IF EXISTS build_role_templates;

DELIMITER $$

CREATE PROCEDURE build_role_templates()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE m VARCHAR(50);
  DECLARE rid BIGINT;
  -- 参与模板的模块（排除 system/log）
  DECLARE cur CURSOR FOR
    SELECT DISTINCT SUBSTRING_INDEX(perms, ':', 1) AS module
    FROM sys_menu
    WHERE perms IS NOT NULL AND perms <> ''
      AND SUBSTRING_INDEX(perms, ':', 1) IN ('sales','purchase','engineering','product','production','inventory');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;

  read_loop: LOOP
    FETCH cur INTO m;
    IF done THEN LEAVE read_loop; END IF;

    -- ===== 1. 全权限角色 {m}:all =====
    INSERT IGNORE INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
    VALUES (CONCAT(UPPER(m), ' 全权限'), CONCAT(m, ':all'), 20, '1', '0', '0', 'admin', NOW(), '角色模板-模块全权限(含审核)');

    SET rid = (SELECT role_id FROM sys_role WHERE role_key = CONCAT(m, ':all'));
    IF rid IS NOT NULL THEN
      DELETE FROM sys_role_menu WHERE role_id = rid;
      INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT rid, menu_id FROM sys_menu
        WHERE perms LIKE CONCAT(m, ':%') AND perms IS NOT NULL AND perms <> '';
    END IF;

    -- ===== 2. 业务操作角色 {m}:ops（不含审核） =====
    INSERT IGNORE INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
    VALUES (CONCAT(UPPER(m), ' 业务操作'), CONCAT(m, ':ops'), 21, '1', '0', '0', 'admin', NOW(), '角色模板-业务操作(不含审核)');

    SET rid = (SELECT role_id FROM sys_role WHERE role_key = CONCAT(m, ':ops'));
    IF rid IS NOT NULL THEN
      DELETE FROM sys_role_menu WHERE role_id = rid;
      INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT rid, menu_id FROM sys_menu
        WHERE perms LIKE CONCAT(m, ':%') AND perms IS NOT NULL AND perms <> ''
          AND SUBSTRING_INDEX(perms, ':', -1) NOT IN ('approve','review','reject','release','obsolete');
    END IF;

    -- ===== 3. 审核员角色 {m}:review（仅当模块存在审核类权限码） =====
    IF EXISTS (SELECT 1 FROM sys_menu
               WHERE perms LIKE CONCAT(m, ':%')
                 AND SUBSTRING_INDEX(perms, ':', -1) IN ('approve','review','reject','release','obsolete')) THEN
      INSERT IGNORE INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
      VALUES (CONCAT(UPPER(m), ' 审核员'), CONCAT(m, ':review'), 22, '1', '0', '0', 'admin', NOW(), '角色模板-审核员(页面+审核动作)');

      SET rid = (SELECT role_id FROM sys_role WHERE role_key = CONCAT(m, ':review'));
      IF rid IS NOT NULL THEN
        DELETE FROM sys_role_menu WHERE role_id = rid;
        INSERT INTO sys_role_menu (role_id, menu_id)
          SELECT rid, menu_id FROM sys_menu
          WHERE perms LIKE CONCAT(m, ':%') AND perms IS NOT NULL AND perms <> ''
            AND (SUBSTRING_INDEX(perms, ':', -1) IN ('approve','review','reject','release','obsolete')
                 OR SUBSTRING_INDEX(perms, ':', -1) = 'view');
      END IF;
    END IF;

  END LOOP;

  CLOSE cur;
END$$

DELIMITER ;

CALL build_role_templates();

DROP PROCEDURE build_role_templates;

-- ==================== 结果验证 ====================
SELECT r.role_id, r.role_key, r.role_name,
       (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.role_id = r.role_id) AS menu_count
FROM sys_role r
WHERE r.role_key IN ('sales:all','sales:ops','sales:review',
                     'purchase:all','purchase:ops','purchase:review',
                     'engineering:all','engineering:ops','engineering:review',
                     'product:all','product:ops','product:review',
                     'production:all','production:ops',
                     'inventory:all','inventory:ops','inventory:review')
ORDER BY r.role_key;
