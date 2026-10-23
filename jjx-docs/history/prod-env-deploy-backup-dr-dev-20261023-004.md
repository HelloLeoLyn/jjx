# 内网生产环境部署方案：部署、备份、快速恢复与应急（设计稿，未实施）

任务码：dev-20261023-004
状态：设计稿（用户 2026-10-23 明确"先完善文档方案，补任务卡信息，实施等通知"）
说明：本文只描述方案与步骤，不含已执行的变更；正式实施需用户当次授权（涉及安装软件、开端口、启停服务）。

---

## 1. 目标与范围

1）在公司内网提供一套生产环境，供内部使用，不做外网穿透。
2）同时支持 IP+端口 与 内网域名访问，HTTPS 使用自签证书。
3）数据库独立 schema，与开发库完全隔离。
4）具备可执行的备份、快速恢复（RTO ≤ 30 分钟、RPO ≤ 5 分钟）与应急处置能力。
5）本机同时承担开发用途，必须把开发与生产的冲突点定死。

---

## 2. 已确认口径（用户 2026-10-23 决策）

| 项 | 决策 |
|---|---|
| 数据库 | 独立 schema：生产 `jjx_erp_prod`，开发 `jjx_erp_db`；账号分离，生产账号只给 DML |
| 访问方式 | IP+端口 与 内网域名（如 jjx.local）都要支持 |
| HTTPS | 自签证书，SAN 含 DNS 与 IP；需说明客户端信任自签 CA 的前提 |
| 部署形态 | A：WSL 原生（Nginx + Spring Boot jar + systemd），不用 Docker |
| 维护窗口 | 每周六 02:00–02:30 |
| 告警 | 邮件（备份失败 / 服务掉线 / 磁盘 >85%） |
| RPO | ≤ 5 分钟（每日全备 + binlog 每日归档） |
| RTO | ≤ 30 分钟；版本回滚 ≤ 5 分钟 |
| 主备分工 | 生产 = 本机（15Gi 内存 / 16 核 / 1TB 盘）；备份落点 = 8G Windows 机器（SMB 共享） |
| 备份链路 | 本机备份写 Windows 可见目录 `D:\jjx-backup`（WSL 侧 `/mnt/d/jjx-backup`），8G 机器用 robocopy 定时拉取 |

---

## 3. 现状与风险（实测，2026-10-23）

1）本机：内存 15Gi（已用约 10Gi，swap 4Gi 已用 3.2Gi）、16 核 Ryzen 7 9700X、磁盘 1007G 已用 29G。
2）MySQL：数据目录 `/var/lib/mysql`，**binlog 已开启**（`log_bin=ON`、`binlog_format=ROW`、保留 30 天）。
3）Redis：`127.0.0.1:6379`，RDB 开启、**AOF 关闭**，当前 10 个 key；`databases=16` 可用于分库隔离。
4）前端：Vite 跑在 `0.0.0.0:3000`（开发用途）；后端 8080 当前未运行；Nginx 未安装。
5）备份现状：全部在仓库 `jjx-docs/sql/backups/`，两份共 12MB，**与数据同盘**，无异地副本。
6）定时任务：代码中有 8 个 `@Scheduled`，若 dev 与 prod 同时运行会重复执行。
7）风险清单：
   - 盘坏 / WSL 重建 / 目录误删 → 数据与备份同时丢失；
   - Redis 序列键 `seq:{业务码}:{yyMMdd}` 丢失会从 001 重发，造成撞号（当前无 DB 播种逻辑）；
   - 开发侧破坏性脚本（`00_clean_test_data.sql`、`db-clean-test-data.sh`）误指生产库；
   - swap 使用率偏高，两个 JVM 并存时存在 OOM 风险。

---

## 4. 目标架构

```
局域网客户端
   │  http(80) / https(443) 、域名 jjx.local 或 IP
   ▼
Windows 主机（端口转发 / mirrored 网络）
   ▼
WSL: Nginx
   ├── /            → /opt/jjx/web（前端 dist，静态缓存 + gzip）
   └── /api         → 127.0.0.1:8081（Spring Boot jar，systemd 托管）
                        ├── MySQL 127.0.0.1:3306 / schema jjx_erp_prod
                        └── Redis 127.0.0.1:6379 / db1
备份：/opt/jjx/backup → D:\jjx-backup →（robocopy）→ 8G 机器 D:\jjx-backup
```

---

## 5. 端口与目录分工

| 用途 | 生产 | 开发（保持现状） |
|---|---|---|
| 对外入口 | Nginx 80 / 443 | Vite 3000（不对外转发） |
| 后端 | 8081 | 8080 |
| 数据库 | schema `jjx_erp_prod` | schema `jjx_erp_db` |
| Redis | db1 | db0 |
| 上传目录 | `/opt/jjx/upload` | 仓库内 `upload/` |
| 日志 | `/opt/jjx/logs`（按天轮转） | 控制台 |
| 定时任务 | 启用 | 关闭（profile 控制） |

Windows 端口转发只转发 80/443，**不转发 3000/8080**。

目录规划：
- `/opt/jjx/server`（jar，保留最近 3 版）
- `/opt/jjx/web`（dist，保留最近 3 版）
- `/opt/jjx/upload`
- `/opt/jjx/logs`
- `/opt/jjx/backup`
- `/opt/jjx/scripts`（发布 / 备份 / 恢复 / 巡检）
- `/opt/jjx/nginx`（站点配置与自签证书）

---

## 6. 部署步骤（分阶段，实施时按序执行）

### 阶段 1：基础环境
1）Windows 侧：固定 IP（路由器 MAC 绑定）；端口转发 80/443 到 WSL；防火墙入站放行；任务计划开机启动 WSL。
2）WSL 侧：`/etc/wsl.conf` 开 systemd；`.wslconfig` 限 memory/swap；安装 Nginx。
3）资源：dev JVM `-Xmx1g`、prod JVM `-Xmx1536m~2g`；MySQL `innodb_buffer_pool_size=512M`。

### 阶段 2：数据库
1）创建 `jjx_erp_prod` 与专用账号（仅 DML，禁 DROP/TRUNCATE）。
2）用 `scripts/db-migrate.sh` 对生产库逐条执行迁移；记账写入**生产库自己的** `sys_config.ops.schema.applied`（与开发库账本独立）。
3）初始化必要基础数据（字典、角色权限、admin 账号等）。

### 阶段 3：后端
1）`mvn package` 出 jar → `/opt/jjx/server/jjx-server-<日期>.jar`。
2）`application-prod.yml`：数据源指向 `jjx_erp_prod`、`file.upload.path=/opt/jjx/upload`、日志目录、关闭 SQL 打印与 swagger、Redis db1。
3）systemd unit：`Restart=always`、`SPRING_PROFILES_ACTIVE=prod`、`JAVA_OPTS=-Xmx1536m`、`StandardOutput=journal`。

### 阶段 4：前端与 HTTPS
1）`npm run build` → `/opt/jjx/web/<日期>/`；保留 3 版，切换用软链。
2）Nginx：静态托管 + `/api` 反代 8081 + gzip + 静态缓存 + `try_files` history 路由 + `client_max_body_size` 与上传上限一致。
3）自签证书：`openssl req -x509 -nodes -days 3650 -subj "/CN=jjx.local" -addext "subjectAltName=DNS:jjx.local,IP:<主机IP>"`；私钥 600；Nginx 443；80 可选跳转。

### 阶段 5：备份与告警（见第 7 节）
### 阶段 6：恢复脚本与演练（见第 8 节）
### 阶段 7：应急卡与巡检（见第 9 节）

---

## 7. 备份方案

### 7.1 备份对象（四类，缺一不可）
1）MySQL 全库（`jjx_erp_prod`，含人事档案；私有存储，必要时 gpg 加密）。
2）binlog（支撑时间点恢复）。
3）Redis（AOF + 每日 RDB）。
4）上传文件目录 `/opt/jjx/upload`。
5）配置与产物：`application-prod.yml`、Nginx 配置、systemd unit、jar、dist。

### 7.2 落点与链路
1）本机暂存：`/opt/jjx/backup`。
2）Windows 可见目录：`D:\jjx-backup`（WSL 侧 `/mnt/d/jjx-backup`）。
3）异地：8G 机器 `D:\jjx-backup`，robocopy 每日拉取（不用 `/MIR`，用 `/E` 配合保留策略，避免误删镜像）。

### 7.3 频率与保留
- 每日 03:00（避开周六 02:00–02:30 维护窗口）全备 + binlog 归档 + 上传增量。
- 保留：本地 7 日；`D:\jjx-backup` 7 日 + 4 周 + 12 月；8G 机器同策略。
- 维护窗口（周六 02:00–02:30）：一致性全备/大版本升级，停写后再备文件。

### 7.4 脚本（待实施时编写）
- `backup-daily.sh`：dump → binlog 归档 → redis BGSAVE → rsync upload → tar 配置 → 校验（大小/表数/尾行）→ 失败邮件告警。
- `backup-retention.sh`：按 7 日 / 4 周 / 12 月清理。
- `sync-to-backup-host.ps1` 或 Windows 任务计划的 robocopy 命令行。

### 7.5 校验与告警
- 每次备份后校验：dump 文件大小 > 阈值、包含 CREATE TABLE 语句、表数量与基线一致。
- 失败或校验不通过 → 邮件（SMTP 待提供）。
- 每日巡检：备份文件是否更新、邮件是否送达、磁盘剩余。

---

## 8. 快速恢复

### 8.1 恢复脚本
- `restore-db.sh`：全备恢复到**临时库** → 校验表数与关键行数 → 可选按 binlog 重放到指定时间点。
- `restore-files.sh`：rsync 回上传目录 → 校验文件数与抽样可访问。
- `restore-redis.sh`：放回 dump/AOF → 校验 `DBSIZE` 与 `seq:` 键。

### 8.2 恢复流程（误操作/数据损坏）
1）停止写入（切维护页或停后端）。
2）恢复全备到临时库。
3）按 binlog 重放到误操作前一刻（`mysqlbinlog --start-datetime/--stop-position`）。
4）核对关键表行数与抽样单据；确认后回灌生产库。
5）记录：误操作时间、binlog 位置、恢复耗时。

### 8.3 演练
- 每季度一次"故意删一行 → 恢复到删除前"。
- 目标：RTO ≤ 30 分钟；结果写入本文档附录。

---

## 9. 应急处置卡

| 场景 | 处置 |
|---|---|
| 全站不可用 | 看 `systemctl status` / `journalctl -n 50`；能回滚先回滚上一版 jar/dist（≤5 分钟） |
| 数据写坏/误删 | 立刻切维护页停写 → 恢复临时库验证 → 定点回补 |
| 单号撞号 | 查当天最大单号后回填 `SET seq:<业务码>:<yyMMdd> <最大号>`；长期方案见 10.3 |
| 磁盘满 | 清旧日志/旧备份，检查 upload 增长 |
| 附件上传失败 | 查磁盘、目录权限、Nginx `client_max_body_size` |
| 数据库锁/慢查询 | `SHOW PROCESSLIST` 定位长事务，必要时 kill 并记录 |

沟通模板：影响范围 / 当前状态 / 正在做什么 / 预计恢复时间 / 下次更新时间。

---

## 10. 开发与生产共存（同机）冲突清单

1）端口：生产 80/443 + 8081；开发 3000/8080；只转发 80/443。
2）数据库：不同 schema + 不同账号；迁移账本按库独立。
3）Redis：dev db0 / prod db1（`seq:` 键同库会抢号）。
4）定时任务：prod 启用、dev 关闭（8 个 `@Scheduled`）。
5）文件与日志目录分离。
6）破坏性脚本加库名白名单（只允许 `jjx_erp_db`），禁止指向 `jjx_erp_prod`。
7）前端配置：dev 指向 8080；prod dist 用相对 `/api` 反代 8081。
8）资源：两 JVM 并存需限内存；先处理 swap 偏高。
9）备份只覆盖生产（库 + upload + binlog），开发库不入每日备份。

---

## 11. 实施前置条件（缺一不可）

1）8G 机器的共享信息：IP、共享名、专用账号与密码、可用磁盘空间。
2）主机 Windows 侧 `D:\jjx-backup` 共享信息（供 8G 机器拉取）。
3）SMTP 账号（发件服务器、端口、账号、密码、收件人）。
4）Windows 主机固定 IP 或内网域名（证书 SAN 与端口转发依赖）。
5）用户当次明确授权：安装 Nginx、开通/变更端口、启停服务。

---

## 12. 验收清单（实施后逐项确认）

1）内网另一台机器可用 `https://jjx.local`（或 IP）登录。
2）完成一条业务闭环：开单 → 审核 → 打印 → 附件上传/下载。
3）Windows 重启后服务自动恢复（Nginx + 后端）。
4）备份连续 3 天成功且邮件可收到；`D:\jjx-backup` 与 8G 机器上均有当日文件。
5）恢复演练一次通过并记录耗时（≤30 分钟）。
6）版本回滚一次成功（≤5 分钟）。
7）dev 与 prod 同时开启互不影响；prod 定时任务只跑一份。
8）清库脚本对生产库执行被拒绝。

---

## 13. 未决与后续

1）Redis 序列号无 DB 播种：建议单独开卡（键缺失时按库内当天最大单号回填，或改数据库序列表）。
2）`approve-check` 的 N+1 查询优化（与部署无关，另一张卡）。
3）NAS 是否上线：若后续有 NAS，可把异地备份从 8G 机器迁到 NAS，并把 8G 机器降级为第二副本。
4）仓库内 `jjx-docs/sql/backups/` 现存两份备份，按规则应只留最新一份，待用户确认清理。
