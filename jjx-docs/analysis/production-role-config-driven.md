# dev-20260828-046 生产身份改为配置驱动（role_key 名单 + 兜底不中断）

## 目标（用户明确要求）

代码里只保留「逻辑身份」概念，**谁属于这个身份由 sys_config 配置决定**。
新建角色时运维只要把它的 role_key 加进配置就能立刻接上，不改代码、不重启、不中断。

sys_config 里本来就有 `production_config.production_admin`（值 'PRODUCTION 全权限'），
但全仓代码零引用、且存的是会被改名的角色名 —— 本任务就是把它真正接上并改成 role_key。

## 一、两个逻辑身份与配置项

sys_config，config_group = `production_config`：

| config_key | 含义 | 值格式 | 迁移后初值 |
|---|---|---|---|
| production_admin | 生产管理者（首次分配、撤回/退回身份门） | role_key 逗号分隔 | `production:all` |
| production_global_scope | 全局生产数据范围（报工查询/审批范围、任务全局视角） | role_key 逗号分隔 | `production:all` |

- **必须存 role_key，不存角色名**：role_key 在 sys_role 上有唯一索引、语义稳定；
  角色名随时会被改，一改配置就静默失效。
- 迁移把现有 production_admin 的值从 `PRODUCTION 全权限` 改成 `production:all`，
  并补 `production_global_scope`；两项的 config_name / remark 写清格式说明
  （例：多个用英文逗号分隔，填 sys_role.role_key，如 production:all,production:dispatch_mgr）。

## 二、解析器（新增 Spring Bean，带缓存与兜底）

新增 `ProductionRoleResolver`（放 com.jjx.production.service + impl），对外两个方法：

- `boolean isProductionAdmin()`：当前登录人的 role_key 列表 ∩ 配置名单 ≠ 空
- `boolean isGlobalProductionScope()`：同上，读 production_global_scope

实现要点：

1. 角色来源用现有 `SecurityUtils.hasRole` 的同一数据源（StpUtil.getRoleList()）。
2. 配置读取走现有 `SysConfigService.listActiveMapByGroup("production_config")`，
   **不要新写 SQL**。
3. 缓存：内存缓存 + 30 秒 TTL；并监听配置更新事件即时失效
   —— `SysConfigService.updateValue()` 里发布一个 Spring `ApplicationEvent`
   （如 SysConfigChangedEvent，带 configKey/group），解析器 `@EventListener` 收到就清缓存。
   事件发布不得影响原有更新逻辑（失败只记日志）。
4. **超级管理员永远通过**：`hasPermission("*:*:*")` 或 role_key = `admin` 直接返回 true，
   不受配置影响。
5. **三道防中断保险**（这是本任务的核心验收点）：
   - 兜底默认：配置项缺失 / 空串 / 全是空白 / 解析后名单里的 role_key 在 sys_role 中
     全都不存在 → 回落到内置默认 `production:all`（常量集中定义，只此一处）。
   - 失配忽略：名单里个别 role_key 对应角色已删除 → 只忽略该项，其余照常生效，不抛异常。
   - 任何异常（配置读取失败、SaToken 取角色失败）→ 记 warn 日志并回落内置默认，
     绝不允许因为配置问题抛错阻断派工。

## 三、改造点（共 8 处，逐一替换）

| 文件:行 | 现状 | 改为 |
|---|---|---|
| ProductionTaskServiceImpl.java:88 | 常量 ROLE_PRODUCTION_MANAGER = "production:all" | 删除该常量（内置默认移入解析器） |
| ProductionTaskServiceImpl.java:536 | hasRole(ROLE_PRODUCTION_MANAGER) 控候选树 | resolver.isProductionAdmin() |
| ProductionTaskServiceImpl.java:581 | 首次分配身份门 | 同上 |
| ProductionTaskServiceImpl.java:945 | 撤回/退回身份门 | 同上 |
| ProductionTaskServiceImpl.java:950 | 执行人或生产管理者 | 同上 |
| ProductionTaskServiceImpl.java:1151 | canAssign 投影 | 同上 |
| WorkReportController.java:68 | hasRole("production:all") | resolver.isGlobalProductionScope() |
| WorkReportActionServiceImpl.java:434 | hasRole("production:all") | resolver.isGlobalProductionScope() |

`SecurityUtils.isGlobalProductionScope()`（SecurityUtils.java:86-88）是静态方法、拿不到 Bean：
把它标记 `@Deprecated` 并让它委托到解析器（通过 ApplicationContext 静态持有者），
或者更干净——改造它的 3 个调用方改用注入的解析器：
ProductionTaskController.java:80、ProductionOperationExecutionController.java:92、
ProductionTaskServiceImpl.java:134。**优先选后者（改调用方，删静态方法）**，避免静态持有者。

错误文案同步改，让运维看得懂去哪配，例如：
「无首次分配权限：当前角色不在 系统管理→基础配置→系统参数→生产配置 的 production_admin 名单中」。

## 四、删角色时的保护

`SysRoleServiceImpl.deleteRoleById()`（第 207 行）当前只检查「是否有用户在用」。
增加一道：若该角色的 role_key 仍出现在 production_config 的任一名单里 → 返回失败/抛
BusinessException，提示「该角色仍被生产配置引用，请先在系统参数里移除」。
注意不要破坏现有返回语义（现在是 return false 表示不可删，保持一致的表达方式）。

## 五、测试（按仓库现有 Proxy mock 风格，参考 TraceServiceImplTest / SysRoleServiceImplTest）

至少覆盖：

1. 配置含某 role_key，用户持有该角色 → isProductionAdmin() = true。
2. 用户角色不在名单 → false。
3. 配置为空串 / 配置项不存在 → 回落内置默认 production:all（持有 production:all 的用户仍 true）。
4. 配置里全是不存在的 role_key → 回落内置默认，不抛异常。
5. 超级管理员（*:*:* 或 admin）在任何配置下都 true。
6. 多个 role_key 逗号分隔（含多余空格）能正确解析。
7. deleteRoleById 对被 production_config 引用的角色返回不可删。

## 六、验证

1. `cd jjx-server && mvn -o clean test -Dtest=<新测试类> -DfailIfNoTests=false` 通过
   （注意：不加 clean 会报 mojo 错误，是本仓已知问题）。
2. `cd jjx-server && mvn -o test-compile` 通过。
3. 若改到前端（本任务预期不需要）则 `cd jjx-web && npm run validate` 也要过。
4. 迁移 `jjx-docs/sql/migrations/23_production_role_config.sql` 幂等，连续执行两次结果一致。
5. 不要 git commit；不要触碰工作区已有的 4 个销售相关修改文件。

## 七、不做

- 不新增权限串、不动 sys_menu（这是用户明确否掉的方案 A 路线）。
- 不动 sys_role.data_scope（那套 UI 存了不生效，属另一个存量问题）。
- 不动 sys_event_config.target_role（同类问题，另开任务）。
