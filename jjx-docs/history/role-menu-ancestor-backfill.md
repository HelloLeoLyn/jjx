# dev-20260828-040 修复角色授权缺失祖先菜单导致子树丢失

## 问题

RouterHelper.java:91/107 在「用户已授权菜单列表」内按 parentId 匹配父子构树：
父菜单没被授权 → 其下所有子菜单在菜单树里被整片丢弃，用户表现为「有权限但菜单是空的」。

实库当前 4 组缺失（检测 SQL 见下）：

| role_id | role_name | 缺失父菜单 | 父菜单类型 |
|---|---|---|---|
| 15 | PRODUCT 审核员 | 65 编辑产品 | C |
| 16 | ENGINEERING 全权限 | 6 产品管理 | M |
| 17 | ENGINEERING 业务操作 | 6 产品管理 | M |
| 18 | ENGINEERING 审核员 | 6 产品管理 | M |

检测 SQL（修复后必须返回 0 行）：

```sql
SELECT rm.role_id, c.parent_id
FROM sys_role_menu rm
JOIN sys_menu c ON c.menu_id = rm.menu_id
JOIN sys_menu p ON p.menu_id = c.parent_id
WHERE c.parent_id <> 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm2
                  WHERE rm2.role_id = rm.role_id AND rm2.menu_id = c.parent_id);
```

## 一、存量修复：jjx-docs/sql/migrations/20_role_menu_ancestor_backfill.sql

- 幂等（参照 19_system_menu_restructure.sql 与 18_ 的写法，用 INSERT IGNORE）。
- 递归补齐：一次补父不够，父的父可能也缺。用 MySQL 8 递归 CTE 或多轮 INSERT IGNORE
  直到收敛，覆盖任意深度。
- 只补 sys_menu 里真实存在的父菜单；忽略 parent_id 指向不存在记录的孤儿
  （menu_id 110/111/112 的 parent_id=20 是已知孤儿，属 dev-20260828-042，本次不处理）。
- 迁移末尾加一段注释说明检测 SQL，便于回归。
- 执行后在开发库 jjx_erp_db 上跑检测 SQL，必须 0 行。

## 二、根因修复：SysRoleServiceImpl.insertAuthMenus（第 396-426 行）

当前实现直接按前端传来的 menuIds 全删全插，前端漏勾父目录就会产生同类脏数据。
这是 sys_role_menu 的唯一写入口（SysRoleController.java:222 /authMenu/selectAll）。

改法：插入前把 menuIds 扩展为「自身 + 全部祖先链」，去重后再插。

- 祖先链必须走 parent_id 链向上递归，**不要用 sys_menu.ancestors 列**：
  该列存在 NULL（如 menu_id 90 工程管理）和历史不一致值，不可信。
- 一次性 `select menu_id, parent_id from sys_menu` 加载成 Map 后在内存里走链，
  不要在循环里查库；对 parentId=0 / null / 不存在的父 ID 停止向上。
- 防御环形引用（走链时记录已访问 ID，重复即跳出）。
- 保持原有行为不变：仍是先删该角色全部授权再插；返回值语义不变；不改接口签名。

## 三、测试

在 jjx-server 现有测试风格下（java.lang.reflect.Proxy 手写 mapper mock，
参考 TraceServiceImplTest 的 `mapper(Class, methodName, result, calls)` 模式）
为 insertAuthMenus 加单元测试，至少覆盖：

1. 只传子菜单 ID 时，实际插入包含其父与祖父 ID（三级：孙 → 父 → 祖）。
2. 已包含父 ID 时不重复插入（去重）。
3. parent_id 指向不存在的菜单时不抛异常，只插自身。

## 四、验证

1. `cd jjx-server && mvn -o clean test -Dtest=<新测试类> -DfailIfNoTests=false` 通过。
2. `cd jjx-server && mvn -o test-compile` 通过。
3. 迁移 SQL 连续执行两次，sys_role_menu 数据一致（幂等）。
4. 检测 SQL 返回 0 行。
5. 不要 git commit；不要触碰工作区已有的 4 个销售相关无关修改文件
   （OrderController.java、OrderServiceImpl.java、api/sales/order.ts、OrderForm.vue）。

## 五、不做

- 孤儿菜单 110/111/112 的 parent_id=20 清理（dev-20260828-042）。
- 任何权限注解 / F 权限补齐（dev-20260828-041，用户自理）。
- 前端角色授权树的勾选联动改造（后端兜底已足够，避免扩大范围）。
