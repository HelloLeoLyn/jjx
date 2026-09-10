-- 80_hr_module.sql
-- 人事管理模块 P0：员工档案 / 岗位 / 导入部门映射 / 菜单权限 / 字典 / 配置项
-- 方案依据：jjx-docs/analysis/hr-module-plan-20260910.md（2026-09-10 Leo「按方案实施」）
-- 默认口径：①员工档案与账号解耦、一对一可选关联 ②部门按方案 §6 映射（新建「品质部」）
--           ③工号 JJX+4位流水（可用 sys_config hr.emp_no.* 覆盖） ④身份证 AES 加密存储 ⑤仅做 P0
-- 前置备份：jjx-docs/sql/backups/sys_menu_sys_dept_hr_*.sql

-- ============ 1. 员工档案 ============
CREATE TABLE IF NOT EXISTS hr_employee (
  emp_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '员工ID',
  emp_no            VARCHAR(32)  NOT NULL COMMENT '工号',
  name              VARCHAR(64)  NOT NULL COMMENT '姓名',
  sex               TINYINT      NULL COMMENT '性别 1男 2女',
  dept_id           BIGINT       NULL COMMENT '部门ID(sys_dept.dept_id)',
  position_id       BIGINT       NULL COMMENT '岗位ID(hr_position.position_id)',
  phone             VARCHAR(20)  NULL COMMENT '手机号',
  email             VARCHAR(100) NULL COMMENT '邮箱',
  hire_date         DATE         NULL COMMENT '进厂日期',
  leave_date        DATE         NULL COMMENT '离职日期',
  employment_status TINYINT      NOT NULL DEFAULT 2 COMMENT '在职状态 1试用 2正式 3停薪留职 9离职',
  id_card_no        VARCHAR(128) NULL COMMENT '身份证号（AES 加密存储）',
  id_card_address   VARCHAR(255) NULL COMMENT '身份证地址（敏感）',
  current_address   VARCHAR(255) NULL COMMENT '现住址（敏感）',
  education         VARCHAR(32)  NULL COMMENT '学历（字典 hr_education）',
  major             VARCHAR(64)  NULL COMMENT '专业',
  resume            TEXT         NULL COMMENT '个人履历',
  user_id           BIGINT       NULL COMMENT '关联系统账号 sys_user.user_id（可空，一对一）',
  remark            VARCHAR(255) NULL COMMENT '备注',
  del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标记 0正常 2删除',
  create_by         VARCHAR(50)  NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         VARCHAR(50)  NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  PRIMARY KEY (emp_id),
  UNIQUE KEY uk_hr_emp_no (emp_no),
  UNIQUE KEY uk_hr_emp_phone (phone),
  UNIQUE KEY uk_hr_emp_user (user_id),
  UNIQUE KEY uk_hr_emp_idcard (id_card_no),
  KEY idx_hr_emp_dept (dept_id),
  KEY idx_hr_emp_status (employment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工档案（人事主数据；账号可选关联）';

-- ============ 2. 岗位/职称 ============
CREATE TABLE IF NOT EXISTS hr_position (
  position_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  position_code VARCHAR(32)  NOT NULL COMMENT '岗位编码',
  position_name VARCHAR(64)  NOT NULL COMMENT '岗位名称',
  dept_id       BIGINT       NULL COMMENT '所属部门（可空=通用）',
  level         INT          NULL COMMENT '职级',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark        VARCHAR(255) NULL COMMENT '备注',
  del_flag      CHAR(1)      NOT NULL DEFAULT '0',
  create_by     VARCHAR(50)  NULL,
  create_time   DATETIME     NULL,
  update_by     VARCHAR(50)  NULL,
  update_time   DATETIME     NULL,
  PRIMARY KEY (position_id),
  UNIQUE KEY uk_hr_position_code (position_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位/职称';

-- ============ 3. 导入用部门映射 ============
CREATE TABLE IF NOT EXISTS hr_dept_mapping (
  mapping_id  BIGINT      NOT NULL AUTO_INCREMENT,
  source_name VARCHAR(64) NOT NULL COMMENT '导入文件中的部门文本',
  dept_id     BIGINT      NOT NULL COMMENT '映射到 sys_dept.dept_id',
  remark      VARCHAR(255) NULL,
  create_time DATETIME    NULL,
  PRIMARY KEY (mapping_id),
  UNIQUE KEY uk_hr_dept_mapping_source (source_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人事导入部门映射';

-- ============ 4. 新增部门：品质部（方案 §6） ============
INSERT INTO sys_dept (parent_id, dept_name, order_num, status, del_flag, create_by, create_time, update_by, update_time)
SELECT 1, '品质部', 6, '0', '0', 'dahuang', NOW(), 'dahuang', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = 1 AND dept_name = '品质部' AND del_flag = '0');

-- ============ 5. 部门映射种子 ============
INSERT INTO hr_dept_mapping (source_name, dept_id, remark, create_time)
SELECT s.src, d.dept_id, 'dev-20260910 人事导入映射', NOW()
FROM (
  SELECT '工程部' AS src, '研发部' AS dst
  UNION ALL SELECT '业务部', '市场部'
  UNION ALL SELECT '仓库',   '办公室'
  UNION ALL SELECT '资材部', '采购部'
  UNION ALL SELECT '品质部', '品质部'
  UNION ALL SELECT '品管部', '品质部'
  UNION ALL SELECT '部品管', '品质部'
  UNION ALL SELECT '制造部', '生产中心'
  UNION ALL SELECT '加工',   '生产中心'
  UNION ALL SELECT '刀模',   '生产中心'
  UNION ALL SELECT '冲型',   '冲型车间'
  UNION ALL SELECT '印刷',   '印刷车间'
  UNION ALL SELECT '组装',   '组装车间'
) s
JOIN sys_dept d ON d.dept_name = s.dst AND d.del_flag = '0'
WHERE NOT EXISTS (SELECT 1 FROM hr_dept_mapping m WHERE m.source_name = s.src);

-- ============ 6. 岗位种子 ============
INSERT INTO hr_position (position_code, position_name, status, remark, create_by, create_time)
SELECT s.code, s.name, 1, 'dev-20260910 初始岗位', 'dahuang', NOW()
FROM (
  SELECT 'QC' AS code, 'QC 质检员' AS name
  UNION ALL SELECT 'IPQC', 'IPQC 制程检验'
  UNION ALL SELECT 'IQC',  'IQC 来料检验'
  UNION ALL SELECT 'LEADER', '组长'
  UNION ALL SELECT 'WORKSHOP_MGR', '车间主任'
  UNION ALL SELECT 'OPERATOR', '操作工'
  UNION ALL SELECT 'DESIGNER', '设计师'
  UNION ALL SELECT 'SALES', '业务员'
  UNION ALL SELECT 'BUYER', '采购员'
  UNION ALL SELECT 'KEEPER', '仓管员'
  UNION ALL SELECT 'HR', '人事专员'
) s
WHERE NOT EXISTS (SELECT 1 FROM hr_position p WHERE p.position_code = s.code);

-- ============ 7. 字典 ============
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, create_time, deleted, tenant_id)
SELECT 'hr_education', '学历', 'hr', 'dev-20260910 人事模块', 0, 1, NOW(), 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'hr_education' AND deleted = 0);

INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, create_time, deleted, tenant_id)
SELECT 'hr_employment_status', '在职状态', 'hr', 'dev-20260910 人事模块', 0, 1, NOW(), 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'hr_employment_status' AND deleted = 0);

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, create_time, deleted, tenant_id)
SELECT s.dc, s.ik, s.iv, s.lb, s.so, 1, NOW(), 0, 1
FROM (
  SELECT 'hr_education' AS dc, '1' AS ik, '小学'   AS iv, '小学'   AS lb, 1 AS so
  UNION ALL SELECT 'hr_education','2','初中','初中',2
  UNION ALL SELECT 'hr_education','3','高中','高中',3
  UNION ALL SELECT 'hr_education','4','中专','中专',4
  UNION ALL SELECT 'hr_education','5','职高','职高',5
  UNION ALL SELECT 'hr_education','6','大专','大专',6
  UNION ALL SELECT 'hr_education','7','本科','本科',7
  UNION ALL SELECT 'hr_education','8','硕士','硕士',8
  UNION ALL SELECT 'hr_employment_status','1','试用','试用',1
  UNION ALL SELECT 'hr_employment_status','2','正式','正式',2
  UNION ALL SELECT 'hr_employment_status','3','停薪留职','停薪留职',3
  UNION ALL SELECT 'hr_employment_status','9','离职','离职',9
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_code = s.dc AND i.item_key = s.ik AND i.deleted = 0);

-- ============ 8. 菜单与权限 ============
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '人事管理', 0, 270, '/hrs', 'layout/index.vue', NULL, '1', '0', 'M', '0', '0', 'hr:view',
    'UserFilled', '0', 'HrManagement', '1', NULL, 270, 'dahuang', NOW(), 'dahuang', NOW(), 'dev-20260910 人事模块'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = '/hrs');
SET @hr_dir := (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = '/hrs' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '员工档案', @hr_dir, 1, 'employee', 'views/hr/employee/index.vue', NULL, '1', '0', 'C', '0', '0',
    'hr:employee:view', 'User', CONCAT('0,', @hr_dir), 'HrEmployee', '1', NULL, 1,
    'dahuang', NOW(), 'dahuang', NOW(), 'dev-20260910'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @hr_dir AND path = 'employee');
SET @hr_emp_menu := (SELECT menu_id FROM sys_menu WHERE parent_id = @hr_dir AND path = 'employee' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '岗位维护', @hr_dir, 2, 'position', 'views/hr/position/index.vue', NULL, '1', '0', 'C', '0', '0',
    'hr:position:view', 'Postcard', CONCAT('0,', @hr_dir), 'HrPosition', '1', NULL, 2,
    'dahuang', NOW(), 'dahuang', NOW(), 'dev-20260910'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @hr_dir AND path = 'position');
SET @hr_pos_menu := (SELECT menu_id FROM sys_menu WHERE parent_id = @hr_dir AND path = 'position' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, visible, status, perms, create_by, create_time, update_by, update_time, remark)
SELECT s.name, s.pid, 'F', '0', '0', s.perm, 'dahuang', NOW(), 'dahuang', NOW(), 'dev-20260910'
FROM (
  SELECT '员工档案-新增' AS name, @hr_emp_menu AS pid, 'hr:employee:add' AS perm
  UNION ALL SELECT '员工档案-修改', @hr_emp_menu, 'hr:employee:edit'
  UNION ALL SELECT '员工档案-删除', @hr_emp_menu, 'hr:employee:delete'
  UNION ALL SELECT '员工档案-导入', @hr_emp_menu, 'hr:employee:import'
  UNION ALL SELECT '员工档案-导出', @hr_emp_menu, 'hr:employee:export'
  UNION ALL SELECT '员工档案-敏感字段（身份证/住址）', @hr_emp_menu, 'hr:employee:sensitive'
  UNION ALL SELECT '岗位维护-编辑', @hr_pos_menu, 'hr:position:edit'
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = s.perm);

-- ============ 9. 角色：超管授权 + 新增「HR 人事管理员」 ============
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE (m.menu_id = @hr_dir OR m.perms LIKE 'hr:%')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
SELECT 'HR 人事管理员', 'hr:all', 60, '1', '0', '0', 'dahuang', NOW(), 'dev-20260910 人事模块；授权后可见员工档案全量'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'hr:all');
SET @hr_role := (SELECT role_id FROM sys_role WHERE role_key = 'hr:all' LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @hr_role, m.menu_id FROM sys_menu m
WHERE (m.menu_id = @hr_dir OR m.perms LIKE 'hr:%')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @hr_role AND rm.menu_id = m.menu_id);

-- ============ 10. 配置项 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time)
SELECT 'hr.emp_no.prefix', 'JJX', '人事-工号前缀', 'hr', 'dev-20260910', 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'hr.emp_no.prefix');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time)
SELECT 'hr.emp_no.digits', '4', '人事-工号流水位数', 'hr', 'dev-20260910', 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'hr.emp_no.digits');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time)
SELECT 'hr.idcard.key', 'jjx-hr-idcard-key-2026-change-me', '人事-身份证加密密钥', 'hr',
       'dev-20260910；生产环境请更换为强随机密钥（16/24/32 字节）', 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'hr.idcard.key');
