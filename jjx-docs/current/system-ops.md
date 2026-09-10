# 系统管理 + 运维 + 环境 · 现行真相

> 状态：✅已实施 | 本文是"现在是什么样"，同时也是**运维入口索引**
> 最后复核：2026-09-10

## 一句话

系统管理管权限与配置，运维监控管日志与文件；协作规范、部署手册、内网环境都在这篇里指路。

## 入口（菜单 1 → /system）

| 分组 | menu_id | 子项 |
|---|---|---|
| 组织权限 | 297 | 用户管理(2) / 角色管理(3) / 部门管理(5) / 菜单管理(4) |
| 基础配置 | 298 | 字典管理(61) / 系统参数(250) / 事件配置(238) / 汇率管理(300) |
| 运维监控 | 299 | 操作日志(56) / 登录日志(57) / 异常日志(58) / 文件管理(251) |

文件管理下已有按钮权限：`手动备份(system:file:backup)`、`预警检查(system:file:check)`。

## 规范 / 手册（动手前先看）

- `AGENTS.md`（根，速查）+ `jjx-docs/standards/CONVENTIONS.md`（**唯一真源**：备份/迁移/文档/提交/§9 讨论与执行边界）
- `jjx-docs/reference/internal-https-setup-guide-20260904.md` —— 内网 HTTPS 全量方案（证书怎么签、怎么装到手机、排障）
- `jjx-docs/reference/ops-runbook-20260910.md` —— 排查手册（30 秒看全局、链路分段、常见故障分型）
- `jjx-docs/README.md` —— 文档库入口（我要干嘛 → 看哪篇）

## 内网环境（单机）

```
手机/PDA ── https://192.168.1.176:443 ──► docker 容器 jjx-nginx（80→301、443 TLS）
                                            └─► host.docker.internal:3000  vite dev（前端）
                                                  └─ /api ─► 127.0.0.1:8080  后端 java -jar
                                                        └─► MySQL 127.0.0.1:3306 / Redis 127.0.0.1:6379
```

- 后端一律跑**打包 jar**（`java -jar target/jjx-server-1.0.0.jar`）：单类加载器，避开 IDE/devtools 双类加载器导致 MyBatis type 解析失败的坑。
- 证书：`~/nginx-certs/`（CA `ca.crt` + `server.crt`，CN/SAN = 192.168.1.176，有效期到 2036-08-29）。**SAN 只含 192.168.1.176 / localhost / 127.0.0.1** —— 用 172.18.0.1 访问会报证书不匹配；换网络需重签。
- nginx 容器曾两次因"单文件 bind mount 解析失败"起不来（Exited 127，`docker start` 可恢复）；重启策略现为 unless-stopped。

## 定时任务（应用内，@Scheduled）

| 任务 | 时间 | 干什么 |
|---|---|---|
| LogCleanTask | 每日 02:00 操作 / 03:00 登录 / 04:00 错误 | 按 `LogCleanProperties` 保留天数分批清理 + 归档（错误日志只清已处理的）；12:00 打日志统计 |
| FileBackupService | 每日 23:30 增量 / 周日 03:00 全量 / 每小时 :05 预警 / 03:30 清回收站 | 附件备份与容量预警，阈值走 `sys_config` |
| InventoryAlertTask / SalesExpireTask / MaterialReserveTimeoutTask / ProductionOrderTimeoutTask | 各自周期 | 库存预警、销售过期、备料超时、工单超期 |

## 配置与告警

- 配置在 `sys_config`（页面上是"系统参数"250）。命名约定：`file.alert.*`（附件容量阈值）、`biz_no_rule.*`（单号规则）、`production_*`（角色名单）、`ops.*`（运维，暂未建）。
- 站内通知：`NotificationService`（FileBackupService 在用，receiverId 硬编码 1L）；另有通用催办 `POST /common/notify-task`，默认收件角色取 `sys_config.notify_task_default_roles`（dev-20260909-003）。

## 已知缺口（还没做，别以为有）

- **后端没有 actuator**：无 `/actuator/health`、无 metrics。要做监控/探针得先加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`。
- **迁移版本没有版本表**：只能人工比对 `jjx-docs/sql/migrations/` 最大序号与库中实际状态（2026-09-10 就因此漏跑 75/77/78）。
- 运维方案（自检+探针+告警）讨论稿在 `analysis/ops-monitoring-plan-20260910.md`，**未落地**。

## 历史（"当年为什么这样"才看）

`analysis/system-menu-restructure.md`、`role-menu-ancestor-backfill.md`、`menu-ancestors-backfill.md`、`module-redesign.md`、`db-audit-report.md`、`project-health-report.md`
