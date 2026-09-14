# 接手与本地起环境（新手第一步）

状态：✅已实施
任务：dev-20260914-011
被取代 / 取代：无
什么情况看这篇：第一次接手这个系统（人或 agent），要把它在本地跑起来、并且知道从哪下手
最后复核：2026-09-14

## 0. 你接手的是什么（30 秒）

一套 ERP：**销售 / 采购 / 库存 / 生产 / 质量 / 工程 / 打印** 七个域。
- 后端：Spring Boot（`jjx-server/`），端口 8080，jar 名 `jjx-server-1.0.0.jar`
- 前端：Vue 3 + Vite（`jjx-web/`），开发模式默认 3000
- 数据库：MySQL `jjx_erp_db`（本机 `127.0.0.1:3306`）
- 文档库：`jjx-docs/README.md`（入口＝目录 + 指导书）
- 规矩：仓库根 `AGENTS.md` + `jjx-docs/standards/CONVENTIONS.md`（唯一真源）

## 1. 先读三篇（顺序别跳）

1. `AGENTS.md`（仓库根）—— 动手前必须知道的：**讨论≠执行**、状态枚举、改库先备份、提交信息格式
2. `jjx-docs/standards/CONVENTIONS.md` —— 唯一真源：备份/迁移/文档/提交 + **§11 多 agent 并行协作四条硬规则**
3. `jjx-docs/guides/scripts-commands-20260914.md` —— 要跑的任何脚本怎么跑、**危险等级🟢🟡🔴**、前置条件

（要写文档再加 `guides/docs-operation-guide-20260914.md`）

## 2. 第一条命令：开工自检

```bash
bash scripts/install-hooks.sh          # 每个 clone 只做一次（装 git 闸门）
bash scripts/agent-preflight.sh        # 之后每次开工先跑
```

自检 5 项：git 闸门 / 库迁移版本 / 只读账号 / 文档门禁 / 工作区状态。

**输出口径**（2026-09-14 已修正并明确，任务 dev-20260914-012）：
- **✘＝阻塞项（挡开工）／⚠＝提醒项（不挡开工）**；结尾直接写"自检通过"或"未通过"，退出码同这个口径（0=无阻塞项，1=有阻塞项）
- 常见 ⚠ 两类：① 工作区有未提交改动（只是提醒：提交时只 add 自己动过的路径）② `jjx_ro` 不可用（只影响 commit-msg 的任务码真实性校验）

## 3. 把系统跑起来

### 3.1 后端

```bash
cd jjx-server
mvn -o package -DskipTests                                  # 离线打包（本仓库支持 -o）
cp target/jjx-server-1.0.0.jar /tmp/jjx-server-1.0.0.jar.prev   # 换 jar 前留回滚副本
cd ..                                                       # ★ 必须在仓库根启动
java -jar jjx-server/target/jjx-server-1.0.0.jar
```

- profile 默认 `dev`（`application-dev.yml`：MySQL `localhost:3306/jjx_erp_db`、Redis `localhost:6379`）
- **★ 启动目录必须＝仓库根**：上传根是绝对路径 `${JJX_UPLOAD_ROOT:/home/administrator/jjx/upload}`；历史上从 `jjx-server/` 启动过，多出一份 `jjx-server/upload/`（见 §6）
- 改完代码不重新打包重启 → 表现是"改了没生效"（其实是旧 jar）

### 3.2 前端

```bash
cd jjx-web
pnpm install          # 首次
pnpm dev              # 开发模式：Vite 直接服务磁盘最新文件，改完刷新即可
```

提交前自查：`cd jjx-web && npm run validate`（状态枚举 + 文档门禁 + vue-tsc）

### 3.3 数据库

- 连接：`127.0.0.1:3306 / jjx_erp_db`；**写操作用有权限账号，只读排查一律用 `jjx_ro`**
- 迁移唯一通道：`bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN`（内部先全库备份 → 执行 → 记版本；**别用 `mysql < 文件` 绕过**）
- ⚠️ **暂时无法从零建库**：仓库里没有完整 DDL 基座（迁移文件里只有 `103` 含 5 张建表；git 里那份全库 dump 是 09-12 的旧货、106 张表且缺新表）→ 现状只能"用现有库"或"用备份恢复"。该项＝生产前置第 2 项，已被定为"暂不生成"
- 初始化数据：`jjx-docs/sql/init/`（19 表子集快照 + 清单 `init-subset-tables.txt` + 决策 `DECISIONS.md`）
- 看快照是否最新：`bash scripts/db-export-init-subset.sh --verify`

## 4. 账号从哪拿

`jjx-docs/accounts/accounts.json` —— 9 个系统账号（含明文密码，**勿外传**）。上生产时**不要**把这份文件带过去（生产前置第 11 项）。

## 5. 怎么算"跑通了"（最小验证）

1. 前端能打开、能登录
2. 左侧菜单**按角色**显示（不是全空、也不是全有）
3. 走一遍一单主流程（销售 → 生产 → 入库），或打开一张已有单据看详情 + 流水
4. 打印能出一张（如领料单 / 确认书）

完整冒烟清单＝生产前置第 26 项，待补。

## 6. 新手最常踩的 8 个坑（都是今年真踩过的）

| # | 坑 | 症状 | 怎么办 |
|---|---|---|---|
| 1 | 上传目录有两份 | 附件/图纸"找不到文件" | 活的是**仓库根 `upload/`**；从 `jjx-server/` 启动会产生第二份（旧那份里还有老业务文件，别乱删） |
| 2 | 旧 jar | "代码改了没生效" | 重新 `mvn -o package -DskipTests` + 重启（重启前留 jar 副本） |
| 3 | 绕过迁移通道 | 没备份、没版本记录 | 只用 `scripts/db-migrate.sh`；直接 `mysql < 文件` 等于裸奔 |
| 4 | 权限 403 | 按钮点了报"无此权限" | 看 `reference/event-permission-matrix.md`；**跨模块调接口会 403**；改完权限要重新登录 |
| 5 | 权限清单为空 | 菜单/按钮全显示或全不显示 | 前端有 fail-open 兜底（待修，生产前置第 17 项）→ 先重新登录再看 |
| 6 | 状态值硬写 | 显示/筛选不对 | 状态一律用 `jjx-web/src/enums/` 的具名枚举（门禁 `check:status-enums` 管） |
| 7 | 误清库 | 测试数据/看板被清 | `scripts/db-clean-test-data.sh` 默认只体检，真清要在**终端手输库名**；它只认 dev 库名，别对生产用 |
| 8 | 提交被闸门拦 | `✘ 拦截：禁止删除/移动迁移…` | 那是 `jjx-docs/sql/`、`jjx-docs/standards/` 的删除/移动保护（CONVENTIONS §5）；确认后果才 `git commit --no-verify` |

## 7. 接手后第一周建议顺序

1. 读三篇（§1）→ 跑自检（§2）→ 本地起环境（§3）→ 跑通（§5）
2. 挑一个模块读 `modules/<模块>.md`（现有：库存 / 质量 / 生产 / 系统运维；销售 / 采购 / 工程 / 打印待写）
3. 想知道来龙去脉 → `history/INDEX.md`（131 篇，按文件名/主题搜）
4. 想知道整体目标与进度 → `guides/master-plan-20260914.md`
5. 要准备上生产 → `guides/production-readiness-plan-20260914.md`（26 项核查表）
6. 要动数据库前 → 命令手册（`guides/scripts-commands-20260914.md`）+ CONVENTIONS §2（先备份）
