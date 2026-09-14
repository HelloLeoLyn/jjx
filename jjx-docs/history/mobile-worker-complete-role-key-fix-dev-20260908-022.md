# 完工按钮角色校验失效修复（任务 1633 / dev-20260908-022）

## 背景
1513（完工按钮角色错位）E2E 实测失败（2026-09-08 19:24，大黄登记）：punch_op1（PRODUCTION 操作工）手机端任务详情仍显示「✓ 完工」按钮，完工对操作工未隐藏。

## 根因（已核实）
- 前端 `jjx-web/src/views/mobile/order.vue:97`：`isProductionWorker = computed(() => userStore.getRoles.includes('production:worker'))`，按钮 v-if 为 `!isProductionWorker && canComplete(ex)`（order.vue:64）。
- 后端 `AuthService.java:117-118`：`getRoles()` → `roleService.selectRoleNameByUsrId(userId)` 返回**角色名**（如 "PRODUCTION 操作工"）。
- 前端拿角色名去比 role_key `production:worker` → 恒 false → 完工对操作工不隐藏。
- DB 佐证：punch_op1 角色 role_id=32，role_key=`production:worker`，role_name=`PRODUCTION 操作工`。
- 影响圈：roles 全前端仅 2 处消费（order.vue:97 isProductionWorker、directives/index.ts:54 hasRole 指令），均按 role_key 语义比对，**无任何展示用途**；后端 selectRoleNameByUsrId 仅 AuthService 一处使用。

## 改动清单（最小修法，方案①推荐）
1. `jjx-server/src/main/java/com/jjx/system/service/ISysRoleService.java`：新增 `List<String> selectRoleKeyByUsrId(Long userId);`（紧邻现有 selectRoleNameByUsrId，接口约 211 行处）。
2. `jjx-server/src/main/java/com/jjx/system/service/impl/SysRoleServiceImpl.java`：实现该方法，**照抄** 474-481 行 selectRoleNameByUsrId 的 LambdaQueryWrapper 写法，仅把 `SysRole::getRoleName` 换成 `SysRole::getRoleKey`。
3. `jjx-server/src/main/java/com/jjx/system/service/AuthService.java:117-118`：`getRoles()` 改为 `return roleService.selectRoleKeyByUsrId(userId);`。

前端零改动（数据源语义一改，两个消费点自动正确）。不改 LoginVO/userInfo 结构，仅 roles 列表元素从角色名变为角色 key。

## 验证
- Codex 侧：`cd jjx-server && mvn -o clean compile`（离线编译，全量不需要）。
- 交付后由 Hermes 复核 diff（确认只动上述 3 个文件、无越界）+ 自跑编译门禁。
- 用户运行时验证清单（后端重启后）：
  1. punch_op1 登录手机端 /m/order 任务详情：**无**「✓ 完工」按钮，报工/开始/暂停等正常；
  2. prod_manager（production:all）或派工主管：完工按钮仍可见可点。

## 明确不做
- 不改 SaPermissionConfig / PermissionServiceImpl（服务端 sa-token 角色校验已用 role_key，正常工作）。
- 不改前端任何文件、不动数据库、无迁移。
- 不 `git commit`（提交由 Hermes 在复核后执行）。
- 不碰工作区无关脏文件（InventoryOutbound*/materialPick.ts/PickPreviewDialog.vue/views/production/order/index.vue = 任务 1625 WIP）。
