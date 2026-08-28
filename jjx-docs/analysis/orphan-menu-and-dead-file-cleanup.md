# dev-20260828-042 + 043 数据清理与死文件删除

两项独立小任务，一次实施。

## 一、dev-20260828-042 清理孤儿按钮菜单 110/111/112

现状（实库已核）：

| menu_id | menu_name | parent_id | perms | menu_type |
|---|---|---|---|---|
| 110 | 新增材料 | 20（**不存在**） | inventory:material:add | F |
| 111 | 编辑材料 | 20（**不存在**） | inventory:material:edit | F |
| 112 | 删除材料 | 20（**不存在**） | inventory:material:delete | F |

sys_menu 里没有 menu_id=20 的记录，这三条 F 权限挂在空父节点上，
在菜单管理页的树里显示不出来，管理员无法维护。

正确归属：物料列表(240)，component views/inventory/material/index.vue，
parent_id=19（材料管理），即目标 ancestors = `0,18,19,240`。

授权现状已核：110/111/112 与 240 都授权给了角色 1/22/23，
所以改挂后不会产生「有子菜单授权但缺父授权」的新缺口（迁移末尾仍需用检测 SQL 复核）。

### 迁移：jjx-docs/sql/migrations/21_orphan_material_button_menus.sql

- 幂等（可重复执行），写法参照 19/20 两个迁移。
- `UPDATE sys_menu SET parent_id = 240, ancestors = '0,18,19,240'
   WHERE menu_id IN (110, 111, 112)`，order_num 保持现值（1/2/3）。
- 但**不要硬编码 240**：用 `route_name='MaterialList'` 或
  `component='views/inventory/material/index.vue' AND menu_type='C'` 定位父菜单 id，
  取不到就不执行更新（避免在别的库上把菜单挂错）。
- 末尾附两段复核 SQL 注释：
  1. 孤儿检测（应 0 行）：
     ```sql
     SELECT m.menu_id, m.menu_name, m.parent_id
     FROM sys_menu m LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
     WHERE m.parent_id <> 0 AND p.menu_id IS NULL;
     ```
  2. 复用 20 号迁移里的「缺失祖先授权」检测（应 0 行）。

## 二、dev-20260828-043 删除死文件 jjx-web/src/mock/router.json

已核实：全 `jjx-web/src` 无任何 import/引用，vite.config.ts 与 package.json
都没有 mock 插件，内容还是过期菜单（缺事件配置/单据模板/系统配置/文件管理，
日志顺序与库不一致），留着只会误导开发。

- 直接删除 `jjx-web/src/mock/router.json`。
- 删除前再自查一次确实无引用（含 `router.json` 字符串、`@/mock`、`src/mock` 三种写法）。
- **不要动** `jjx-web/src/mock/productTestData.ts`：它同样未被引用，但不在本次授权范围，
  只在最终报告里提一句即可。

## 三、验证

1. `cd jjx-web && npm run validate` 通过（状态魔法值新增 0 处，不得扩充 baseline）。
2. `cd jjx-server && mvn -o test-compile` 通过（本次无后端改动，仅确认未被牵连）。
3. 迁移 21 若沙箱内连不上 MySQL 就只写文件，并明确告知需在沙箱外执行两次 + 跑两段检测 SQL。
4. 不要 git commit。不要触碰工作区已有的 4 个销售相关修改文件
   （OrderController.java、OrderServiceImpl.java、api/sales/order.ts、OrderForm.vue）。
