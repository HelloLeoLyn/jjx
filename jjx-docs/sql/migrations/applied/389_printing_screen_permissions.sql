-- P04 放行、洗版权限，仅登记动作，不授予角色（dev-20261125-010）。
INSERT INTO sys_menu(menu_name,parent_id,order_num,menu_type,perms,ancestors,create_time)
SELECT action.label,parent.menu_id,action.seq,'F',action.perm,
       CONCAT_WS(',',NULLIF(parent.ancestors,''),parent.menu_id),NOW()
FROM sys_menu parent
JOIN (SELECT '网版检验放行' label, 'engineering:screen-resource:release' perm, 1 seq
      UNION ALL SELECT '网版洗版','engineering:screen-resource:wash',2) action
WHERE parent.perms='engineering:screen-resource:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=action.perm);
