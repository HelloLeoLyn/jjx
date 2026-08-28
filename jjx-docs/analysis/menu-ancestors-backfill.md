# dev-20260828-045 全库重算 sys_menu.ancestors

## 背景

sys_menu 是 jjx_erp_db 里唯一带 `ancestors` 列的表
（sys_dept 的 ancestors 是 Java 实体上的 `@TableField(exist = false)`，表中无此列）。

当前 150 条 `ancestors IS NULL`，无"不以 0 开头"的脏值：

| menu_type | 数量 |
|---|---|
| F 按钮 | 135 |
| C 菜单 | 13 |
| M 目录 | 2（含 menu_id=90 工程管理） |

RouterHelper.java:91/107 构树只用 parent_id，不读 ancestors，所以当前不影响功能；
但菜单管理页展示、以及后续写迁移按 ancestors 推导层级时都会踩（dev-20260828-042 已踩过一次）。

## 实施：jjx-docs/sql/migrations/22_menu_ancestors_backfill.sql

- 幂等（重算本身天然幂等，重复执行结果一致），写法风格参照 19/20/21。
- 用 MySQL 8 递归 CTE 沿 parent_id 链自顶向下计算每行的规范 ancestors：
  - 根节点（parent_id = 0）→ `'0'`
  - 其余 → `父.ancestors + ',' + 父.menu_id`
- **全表回写**，不只补 NULL：让历史上算错的行也收敛到规范值。
- 链路断裂的行（parent_id 指向不存在的记录）必须跳过、不得写入 NULL 或半截值；
  这类行由检测 SQL 暴露（当前应为 0 条，dev-20260828-042 已清完孤儿）。
- 防环：递归 CTE 加深度上限（如 10 层）或路径去重，避免脏数据导致无限递归。
- 不要修改 parent_id、menu_type、order_num、perms 等任何其它列。

## 检测 SQL（写进迁移末尾注释）

1. 规范性（应 0 行）：
   ```sql
   SELECT menu_id, menu_name, parent_id, ancestors
   FROM sys_menu
   WHERE ancestors IS NULL OR ancestors NOT LIKE '0%';
   ```
2. 与父链一致性（应 0 行）：
   ```sql
   SELECT c.menu_id, c.ancestors, p.ancestors AS parent_ancestors
   FROM sys_menu c JOIN sys_menu p ON p.menu_id = c.parent_id
   WHERE c.parent_id <> 0
     AND c.ancestors <> CONCAT(p.ancestors, ',', p.menu_id);
   ```
3. 孤儿（应 0 行，作为前置条件复核）：
   ```sql
   SELECT m.menu_id FROM sys_menu m
   LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
   WHERE m.parent_id <> 0 AND p.menu_id IS NULL;
   ```

## 验证

1. 迁移连续执行两次，sys_menu 全表数据一致（幂等）。
2. 上面三段检测 SQL 全部返回 0 行。
3. 只有 ancestors 列发生变化：比对执行前后 `menu_id, parent_id, menu_type, order_num, perms,
   visible, status` 的快照必须完全一致。
4. 无前后端代码改动，因此无需跑 validate / test-compile；若确实改到了代码，则两者都要过。
5. 不要 git commit。不要触碰工作区已有的 4 个销售相关修改文件。
