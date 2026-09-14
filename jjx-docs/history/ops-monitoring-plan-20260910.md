# 运维监控方案（应用内自检 + 宿主探针 + 告警 + 处置）

> 建档：2026-09-10 | 适用：JJX ERP 开发/内部环境（WSL2 + Docker Desktop + MySQL/Redis 本机 + vite:3000 + 后端:8080 + docker nginx:80/443）
> 任务码：待定（建议 dev-20260910-002，落地时登记 sys_task）

## 1. 结论先行

**不要把「运维监控」做成第二个监控系统，把它从"日志查看器"升级为"环境体检台"。**

三条判断：

1. **真正的痛点不在应用内，而在环境层。** 2026-09-10 一天内实际发生三起故障：① docker nginx 未启动 → 手机 https 全断；② 迁移只执行到 74，77/78 没跑 → 界面缺「质量管理」整块；③ 8080 跑的是旧代码 → 新功能不生效。这三件事在应用里**完全不可见**，用户是"用不了才发现"。日志（操作/登录/异常）解决不了这一类问题。
2. **应用内外要分层，不要让 Java 去 docker exec。** 后端只负责它能看到的事实（DB、Redis、表结构版本、定时任务、备份、磁盘）；宿主机层面的（容器状态、80/443、nginx、vite、宿主端口）由一个极轻的探针脚本定时写入一张状态表，页面只做只读展示。这样职责清晰、后端无特权。
3. **告警和处置直接复用现成范式，不新造通道。** 项目里 `FileBackupService`（DEV-736）已经把「@Scheduled 定时检查 + sys_config 阈值 + NotificationService 站内通知 + 保留策略」这套跑通了，运维监控顺着它写即可；催办通道还有 dev-20260909-003 的 `POST /common/notify-task`（默认收件角色走 sys_config `notify_task_default_roles`）可复用。

## 2. 现状盘点

### 2.1 已有的运维监控资产（可复用，别重复造）

菜单：`系统管理(1) → 运维监控(299)`，现有子项 `操作日志(56)` `登录日志(57)` `异常日志(58)` `文件管理(251)`；文件管理下已有按钮权限 `手动备份(system:file:backup)` `预警检查(system:file:check)`。

后端已有件（实际文件）：

| 能力 | 位置 | 可复用点 |
|---|---|---|
| 定时任务基础设施 | `framework/config/ScheduleConfig.java` + 5 个 `*Task.java` | 直接加检查类 @Scheduled 即可 |
| 日志保留+归档 | `system/service/LogCleanTask.java` + `LogArchiveService` + `system/config/LogCleanProperties` | 保留天数、分批、归档已成型；体检只需读它的执行结果 |
| 文件备份+容量预警 | `system/service/FileBackupService.java` | **最佳范本**：日备/周备/每小时预警/回收站清理，阈值走 sys_config，站内通知，保留 daily 14 天 / weekly 8 个 |
| 站内通知 | `NotificationService`（FileBackupService 在用，receiverId 硬编码 1L） | 告警通道 |
| 通用催办 | `POST /common/notify-task`（dev-20260909-003） | 备用告警通道，支持按 role_key 找收件人 |
| 配置中心 | `sys_config`（key/value + remark，注释带任务码） | 所有阈值/收件人/开关的落点 |
| 日志表 | `sys_oper_log`(21 列，含 action/trace_id/cost_time) `sys_login_log` `sys_error_log` | 体检数据源 |

### 2.2 今天暴露的缺口（本方案要覆盖的目标）

| 事故 | 现象 | 应用内可见性 | 本方案对应检查项 |
|---|---|---|---|
| nginx 容器 Exited(127) | 80/443 无监听，手机 https 全断 | 零 | 宿主探针：容器状态 / 端口 / https 200 |
| 迁移未执行（库停在 74） | 界面缺「质量管理」，后端接口与库不匹配 | 零 | 应用内：schema 版本 vs 迁移目录最大序号 |
| 8080 跑旧代码 | 新功能不生效，排查耗时 | 零 | 应用内：进程/构建时间 vs 最近提交时间 |
| 备份被删无人认领 | 5 个 backup 处于"已删除未提交" | 零 | 应用内：备份目录清单 + 最近成功备份时间 |
| icon='#' 打崩侧边栏 | 前端 InvalidCharacterError | 零 | 应用内：菜单数据合法性检查 |

## 3. 方案设计

### 3.1 分层职责

```
┌ 前端：运维监控 → 环境体检（红/黄/绿表 + 每项修复命令 + 一键复制）
│        运维监控 → 告警记录（谁在什么时候因为什么被通知）
├ 后端：OpsHealthService（跑检查项，产出统一结构，落快照，触发告警）
│        HealthCheck 接口 —— 每个检查项一个 @Component 实现（插件式，加项零改老代码）
├ 状态表：sys_ops_health_snapshot（每次执行的检查项结果，用于趋势与"上次绿是什么时候"）
│        sys_ops_probe（宿主探针写入的外部事实，后端只读）
└ 宿主：探针脚本（cron 每 5 分钟）→ 检查 docker/端口/https/磁盘 → 写 sys_ops_probe
```

### 3.2 统一检查项结构（关键设计）

每个检查项产出固定字段，前端不需要为任何一项写特例：

| 字段 | 说明 |
|---|---|
| code | 唯一标识，如 `db.connection` / `schema.version` / `host.nginx` |
| scope | `app` \| `host` \| `data` |
| status | `GREEN` \| `WARN` \| `RED` \| `UNKNOWN` |
| detail | 人话结论，如"库 schema=74，目录最大=78，缺 75/77/78" |
| fix_hint | 可复制的修复命令/动作，如 `bash jjx-docs/scripts/health.sh --fix nginx` |
| checked_at | 检测时间 |

首批检查项（P0）：

- `app.db` 数据库连通 + 慢查询基础统计
- `app.redis` 连通
- `app.schema` **迁移版本**：`sys_config.ops.schema_version` vs `jjx-docs/sql/migrations/` 最大序号，不一致列缺失清单
- `app.scheduled` 定时任务最近执行时间（LogCleanTask / FileBackupService / InventoryAlertTask …），超期未跑报黄
- `app.backup` 最近一次成功备份时间 + 备份目录体积/文件数（超阈值红）
- `data.attachment` 附件目录占用百分比（复用 FileBackupService.stats()）
- `app.logs` 三张日志表行数 + 保留天数配置是否生效
- `app.menu_data` 菜单数据合法性（icon 必须是合法 Element Plus 图标名或 `src/icons/{svg,jjx}/` 下文件；visible=0 的 M/C 菜单必须 icon 合法）
- `host.nginx` 容器 jjx-nginx 是否 Up（探针写入）
- `host.ports` 80/443/3000/8080 监听（探针写入）
- `host.https` `https://192.168.1.176` 返回 200 + 证书 SAN 是否覆盖当前 IP（IP 变了要报红，文档 §5.1 已踩过）
- `host.disk` 根分区占用（探针写入）

### 3.3 复用清单（明确不新造）

- 定时调度：沿用 `@Scheduled` + `ScheduleConfig`，不引入 Quartz/Prometheus/Grafana
- 阈值与开关：全部 `sys_config`，命名沿用现成风格 `ops.*`（对照 `file.alert.*`）：`ops.health.interval_minutes`、`ops.schema.version`、`ops.backup.stale_hours`、`ops.disk.percent`、`ops.alert.roles`、`ops.alert.dedupe_minutes`
- 告警通道：优先 `NotificationService`（站内），需要按角色分发时用 `POST /common/notify-task`
- 备份：复用现有 `mysqldump` 命令与 `jjx-docs/sql/backups/` 约定，不另立目录
- 权限模型：沿用 `sys_menu.perms` + `sys_role_menu`，父目录授权按规范反向补齐

### 3.4 菜单与权限（落地细节，前面踩过坑）

新增（迁移 `79_ops_health_menu.sql`，接 78 后）：

- `运维监控(299) → 环境体检`，path `ops-health`，perms `ops:health:view`
- 按钮：`ops:health:alert`（告警配置）、`ops:health:action`（处置动作）
- **icon 必须用合法值**（今天 icon='#' 打崩侧边栏；用 `Monitor`/`FirstAidKit`），并纳入 `app.menu_data` 检查项，防止回潮
- 迁移本身幂等（`WHERE NOT EXISTS`），执行前先备份（规范 §2）

## 4. 分阶段落地

### P0 · 环境体检（只读，零风险，建议先做）

1. 迁移 `79_ops_health_menu.sql`：菜单 + 权限 + `sys_config` 初始阈值（含 `ops.schema.version` 写入当前值 78）
2. 后端 `system/ops/`：`HealthCheck` 接口 + `OpsHealthService` + 上述 app/data 检查项实现（每个一个类，`@Component` 自动收集）
3. 表 `sys_ops_health_snapshot`（检查项结果快照）；`sys_ops_probe`（宿主探针表，见 P1）
4. 接口：`GET /ops/health`（当前全量）、`GET /ops/health/history?code=`（趋势）
5. 前端 `views/system/ops-health/index.vue`：红黄绿表格 + 分组 + 每行 fix_hint 一键复制 + 手动"重新体检"
6. 探针脚本 `jjx-docs/scripts/ops-probe.sh`（只写 `sys_ops_probe`，见 P1 设计）

**验收**：故意停掉 jjx-nginx → 页面 `host.nginx` 变红并给出 `docker start jjx-nginx`；启动后变绿。故意把 `ops.schema.version` 改成 76 → `app.schema` 变红并列出缺 77/78。

### P1 · 主动告警（复用现成通道）

1. 宿主 cron 每 5 分钟跑 `ops-probe.sh` → 写 `sys_ops_probe`（容器/端口/https/磁盘）
2. 后端 `@Scheduled` 每 N 分钟（`ops.health.interval_minutes`）汇总体检，**红项**按 `ops.alert.roles` 发通知；**去重落表**（`sys_ops_health_alert`），不做内存去重——`FileBackupService.lastDailyAlertDate` 那种内存变量在重启后会重复发，这里不重蹈
3. 恢复通知：同一 code 由红转绿时发一条"已恢复"，避免只报坏消息
4. 前端加「告警记录」子页（谁、何时、因何、是否已确认）

**验收**：停 nginx → 5~10 分钟内收到站内通知且只收一条；恢复 → 收到"已恢复"；重启后端不重复发。

### P2 · 安全处置（可一键，但绝不自动）

只做"幂等、可回滚、无业务影响"的动作，且必须二次确认：

- `docker start jjx-nginx`（仅当探针报 Exited，且 80/443 空闲）
- "生成迁移前备份"：调用规范 §2 的 mysqldump 命令，产出 `jjx-docs/sql/backups/jjx_erp_db_backup_YYYYMMDD-HHmm_<tag>.sql` 并回显 md5
- "清理旧备份"：按保留策略列出待删清单，人工勾选后删除（不自动删）

**明确不做**：不自动执行迁移、不自动 git 操作（reset/clean/push）、不自动改共享目录文件。

## 5. 需要先对齐的三件事（落地前必须定）

1. **探针怎么拿到写库权限**：脚本直连 MySQL 需要账号（现在只有 root/123456，规范红线区）。两个选项：① 建只对 `sys_ops_probe` 有 INSERT 权限的专账号；② 后端暴露 `POST /ops/probe`（内网 + 令牌），脚本只发 HTTP。**我建议 ②**：不新增 DB 账号、走应用层校验，也顺便让探针结果进操作日志。← 需要你拍板
2. **"已应用迁移序号"记在哪**：建议 `sys_config.ops.schema_version`，由每次执行迁移时最后一行 UPDATE（或执行人/脚本登记）。这决定 `app.schema` 检查项靠不靠谱。← 需要你拍板
3. **验收口径**：P0 是"能看见"，P1 是"能通知"，P2 是"能一键"。如果你只想先要"能看见"，P0 单独交付也有完整价值。

## 6. 风险与取舍

- 探针写库引入新的写路径：走 HTTP 方案则风险极低；直连方案需专账号 + 只写单表
- 检查项会随环境演进腐化：所以用插件式接口 + `fix_hint` 强制写清"怎么修"，腐化时一眼看出
- 通知风暴：靠"落表去重 + 恢复通知"控制，阈值与间隔全走 sys_config，可调不重启
- 与既有日志/备份功能的关系：本方案**只读**它们的产物（保留策略、备份成功时间），不改 `LogCleanTask`/`FileBackupService` 的行为
- 手机端 https 是业务依赖（扫码要安全上下文），所以 `host.https` 建议定为 RED 级而非 WARN

## 7. 文件清单（预计改动）

| 类型 | 路径 |
|---|---|
| 迁移 | `jjx-docs/sql/migrations/79_ops_health_menu.sql` |
| 后端 | `jjx-server/src/main/java/com/jjx/system/ops/`（HealthCheck、OpsHealthService、各检查项）、`system/controller/OpsHealthController.java` |
| 实体/Mapper | `system/domain/entity/SysOpsHealthSnapshot.java`、`SysOpsProbe.java` + 对应 Mapper |
| 前端 | `jjx-web/src/views/system/ops-health/index.vue`、`jjx-web/src/api/system/opsHealth.ts` |
| 脚本 | `jjx-docs/scripts/ops-probe.sh`（宿主 cron 调用） |
| 配置 | `sys_config`：`ops.*` 一组键 |
| 文档 | 本文件 + `analysis/INDEX.md` 登记 |

---

**落地建议**：先做 P0（只读体检）单独交付并验收，再决定 P1/P2 的节奏。实施方式按仓库惯例：写 spec → 交 Codex（沙箱无 DB，SQL 由本机执行）→ 本机执行迁移与验证 → 只提交本次相关文件。
