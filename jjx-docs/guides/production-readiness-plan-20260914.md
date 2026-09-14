# 生产前置核查计划（上线前必须过的清单）

状态：⏳进行中（除第 2 项 DDL 基座外已逐项核查完毕；未完结项见文末「核查进度表」）
任务：dev-20260914-008（任务 1757）／本轮推进 dev-20260914-013（任务 1769）
被取代 / 取代：无
什么情况看这篇：准备上生产、或判断"现在能不能上"时，从这一页开始
最后复核：2026-09-14

> 分层口径：🔴 **硬前置**＝不做就上不了（或上了会出事）／🟡 **要拍板**＝口径要先定／🟢 上线当天或之后可做。
> 用法：每查完一项，把命令 + 输出填进文末「核查进度表」；没证据不算查过。

## 一、数据库

| # | 层 | 项 | 现状（2026-09-14 实测） | 结论 / 待办 |
|---|---|---|---|---|
| 1 | 🔴 | 版本记账断链 | ~~applied 缺 102~106~~ | ✅ **已补齐**：`db-migrate.sh --record 102..106` 连跑 5 次，现 applied = `66…109` 连号，version=109；5 份 `sys_config` guard 备份在 `jjx-backups/`（同一分钟被同名覆盖，只留最后一份——脚本时间粒度是分钟，属小瑕疵） |
| 2 | 🔴 | DDL 基座缺失 | 迁移目录里只有 `107~109`；**含 CREATE TABLE 的 = 0**；全库 111 张表的建表语句不在仓库 | ⏸ **用户 2026-09-14 决定暂不生成**（空环境演练会用到，见第 24 项） |
| 3 | 🟡 | 迁移 101~106 归属 | 工作区已删、**未提交**；内容仍在 git 历史 `7643353f` | ⏸ **用户确认是有意删除** → 保持工作区状态、我不提交这个删除（它在 pre-commit 保护范围内，属要用户点头的动作） |
| 4 | 🟡 | 迁移 107~109 未提交 | ~~`??` 未跟踪~~ | ✅ **已提交** |
| 5 | 🔴 | 生产库的连接与断言 | 开发口径散布在：`scripts/db-clean-test-data.sh:28,69`（库名 + 断言"必须是 jjx_erp_db"）、`scripts/agent-preflight.sh:70`（jjx_ro/jjx_erp_db 探测）、`scripts/clean-archive-ocr-data.sh:12`、`application-dev.yml` | 生产替换清单：① **不要在生产机跑 `db-clean-test-data.sh`**（它的库名断言就是 dev 专用）② `agent-preflight` 的库名/账号探测要参数化 ③ 用 `application-prod.yml`（第 13 项）替掉 dev |

## 二、数据初始化

| # | 层 | 项 | 现状 | 结论 / 待办 |
|---|---|---|---|---|
| 6 | 🔴 | 子集快照是否最新 | ⚠ **已过期**：`--verify` → `inventory_item` 快照 1536 → 现库 1537（+1） | **重出快照**（登记任务 → `db-export-init-subset.sh --task dev-YYYYMMDD-NNN`），并在上线前最后再校一次 |
| 7 | 🟡 | DECISIONS 5 个待定项 | ①静态文件交付 ②目标库形状 ③ID 是否原样 ④测试脏数据是否先清 ⑤`file_id` 是否置空 | ✅ **口径已定**（写入 `sql/init/DECISIONS.md`）：① tar 包 + 放置说明随初始化数据交付 ② 目标库＝仅数据（结构由 DDL 负责；DDL 暂缓期间用"现库结构基线"过渡）③ ID 原样保留（业务互引）④ 不清测试数据（生产用子集快照，本身不含测试数据）⑤ `file_id` 置空 |
| 8 | 🟡 | 已知数据缺口 | **比原记录更严重**：物料 1601 vs 库存身份 1535 → **66 条物料无库存身份**；`item_code` 与物料编码 **1535 条全部不一致**（还是旧 `MTR…` 格式）；`quality_template_registry` 19 条 `file_id` 悬挂（`sys_attachment` 只有 1 行）；`inventory_item` 有 2 条非物料行（含 1 条测试 PRODUCT） | 迁移后对齐三步：① 补 66 条库存身份 ② 刷新 1535 条 `item_code` 为物料现编码 ③ 置空 19 条悬挂 `file_id`；测试 PRODUCT 行不进初始化 |
| 9 | 🔴 | 测试数据不得进生产 | dev 库有 `sys_task` 看板 800+ 条、测试单据、OCR 测试档案与样本 | ✅ **口径**：生产初始化＝**DDL + 子集快照**；`db-clean-test-data.sh` **只对 dev 库名生效，禁止对生产库使用** |

## 三、敏感信息 / 脱敏

| # | 层 | 项 | 现状 | 结论 / 待办 |
|---|---|---|---|---|
| 10 | 🟡 | 身份证密文与密钥 | `sys_config.hr.idcard.key` 存在；员工表存 AES 密文 | ✅ **口径已出**：见 `design/sensitive-data-masking-dev-20260914-002.md`（身份证密文与 `hr.idcard.key` 原样带、注入式交付；smtp/sms 类占位或环境变量注入） |
| 11 | 🟡 | 其他凭据 | `jjx-docs/accounts/accounts.json` **+ `index.html` 都被 git 跟踪**（明文口令，已在历史里）；`sys_config` 有 `sms_api_key`/`smtp_host`/`smtp_port`/`company_email` | 口径：① **生产交付包排除** `jjx-docs/accounts/`（不打包、不带过去）② 这些口令**已在 git 历史**，生产用新口令并**轮换**（旧口令视为泄露）③ `sys_config` 的 sms/smtp 值生产改占位 |
| 12 | 🔴 | 数据库账号 | 现有 `root@localhost`、`jjx_ro@127.0.0.1`/`@localhost`（均有口令） | 生产账号规划：`jjx_app`（应用，业务表读写）/`jjx_mig`（迁移，DDL 权限）/`jjx_ro`（只读排查），全部强口令；`root` 仅本机运维用 |

## 四、环境 / 部署

| # | 层 | 项 | 现状 | 结论 / 待办 |
|---|---|---|---|---|
| 13 | 🔴 | 生产配置 profile | 只有 `application.yml`（active: dev）+ `application-dev.yml` | ✅ **已建模板** `jjx-server/src/main/resources/application-prod.yml`（全部走环境变量、无明文）；启动用 `--spring.profiles.active=prod`，上线前填真实值 |
| 14 | 🔴 | 上传目录 | 活的＝仓库根 `upload/`（9-07 后新写 291 个文件；实例 cwd＝仓库根）；`jjx-server/upload/` 是 9-06 前残留（9-07 后写入 0），但**两份各有对方没有的文件** | ✅ 口径：生产 `JJX_UPLOAD_ROOT` 显式设为生产绝对路径；**上线前做一次"合并搬迁"**（把旧份独有目录 `production_order`/`purchase_order`/`quotation_flow`/`sales_order` + `quality_template` 9-04/9-05 文件并进活目录），再归档旧份 |
| 15 | 🟡 | 外部依赖 | MySQL、Redis 是 **systemd 服务**（active running）；OCR 是 `.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8866`（手工起）；后端是 `java -jar`（手工起，cwd=仓库根） | 生产：把**后端与 OCR 也做成 systemd 单元**（含开机自启、自动重启、日志落文件）；MySQL/Redis 已有 unit 沿用 |
| 16 | 🟡 | 网络与证书 | 监听：3306/6379/8866 都只听 `127.0.0.1`（好）；`8080` 监听 `*`、`3000` 监听 `0.0.0.0`（前端 dev server 对外）；80/443 有监听；证书在 `jjx-docs/assets/certs/`（`JJX-CA.crt` + 手机安装指引） | 生产口径：对外只开 80/443 走 nginx 反代 → 后端 8080 收成仅本机；**不要暴露前端 dev server(3000)**；手机端扫码需 HTTPS（证书见 `guides/internal-https-setup-guide-20260904.md`） |

## 五、权限 / 安全

| # | 层 | 项 | 现状 | 结论 / 待办 |
|---|---|---|---|---|
| 17 | 🔴 | 权限 fail-open | `jjx-web/src/permission.ts:51` `setPermissions(['*'])` 兜底；`store/modules/user.ts:120` 再兜底 `dashboard:view`；判定在 `directives/index.ts:45 hasPermi()` | ✅ **规格已出**：`design/permission-fail-closed-dev-20260914-014.md`（fail-closed + 登录后实时重算 + 回归清单）；代码落地另开任务 |
| 18 | 🟡 | 跨模块 403 | 角色按模块一刀切授权 → 页面调其他模块的接口就 403（如采购单页取物料下拉） | 口径：给**只读查询**开口子（新增 `xxx:query` 或像 `/system/tag/facets` 那样"登录即可"的辅助接口），**不是**把菜单挪到别的模块下 |
| 19 | 🟡 | 审计留痕 | 清理脚本会清空 `sys_oper_log`；`db-migrate.sh` 里的 `REMARK` 变量**定义了但没落盘**（只有备份文件 + `sys_config` 版本记录） | 建议：迁移与清理的摘要统一追加到 `$JJX_BACKUP_DIR/*-log.txt`（清理脚本已这么做，迁移脚本待补） |

## 六、工程闸门 / 发布纪律

| # | 层 | 项 | 现状 | 结论 / 待办 |
|---|---|---|---|---|
| 20 | 🔴 | 门禁是否全绿 | ✅ **全绿**：`check:docs` 通过；`check:status-enums` 通过（存量 138 处、新增 0）；`vue-tsc --noEmit` 退出码 0；`mvn -o test-compile` BUILD SUCCESS | 发布前再跑一遍（并行会话还在改代码，绿是当时的） |
| 21 | 🔴 | 工作区收敛 | 未提交 ~60 项（39 M / 9 D / 12 ??），主体是并行会话的 Java/Vue + 用户删的 101~106 + 3 个 assets | 见附录 D「归属清单」——**按归属分派**，我不替别人提交 |
| 22 | 🟡 | 钩子 | `core.hooksPath=scripts/hooks` ✓（本 clone） | 生产机/其他 clone 跑 `bash scripts/install-hooks.sh` |
| 23 | 🟡 | 分支与发布 | 分支 `dev`；tag 1 个；remote `git@github.com:HelloLeoLyn/jjx.git` | 口径：上线打 tag（如 `v0.1.0-prod`），生产**从 tag 检出**（不用 dev 头）；发布人=用户，回滚见附录 A |

## 七、上线演练与回滚

| # | 层 | 项 | 结论 / 待办 |
|---|---|---|---|
| 24 | 🔴 | 空环境演练 | **受第 2 项阻塞**（没有 DDL 基座就建不出库）。前置条件：要么解封第 2 项生成 DDL 基线，要么用"现库结构导出的基线 + 子集快照"；演练步骤：干净机 → 建库（DDL）→ 导数据（快照）→ 起服务（prod profile）→ 冒烟（附录 B） |
| 25 | 🔴 | 回滚方案 | ✅ 已出：见附录 A |
| 26 | 🔴 | 冒烟清单 | ✅ 已出：见附录 B |

---

## 附录 A：回滚方案（第 25 项）

1. **上线前必做**：全库备份到生产外部存储（`mysqldump` 全库 + 记 **md5 / 表数 / 字节数**），文件名 `jjx_erp_db_backup_<YYYYMMDD-HHmm>_before-golive.sql`；备份不在机上算完成 —— 要能在**另一台机器**上还原验证一次。
2. **代码回滚**：生产从 tag 检出（第 23 项）→ 回滚 = 切回上一个 tag + 重启后端/前端；jar 换前留副本（`cp target/*.jar /tmp/*.prev`）。
3. **数据回滚**：
   - 迁移是按号递增、**没有 down 脚本** → 回滚=用第 1 步的全库备份整库还原（所以那一步是命根子）；
   - 单条数据补丁类（如置空 `file_id`）→ 回滚脚本要单独写，并在执行前导出受影响表（`$JJX_BACKUP_DIR/<表>_<topic>_YYYYMMDD-HHmm.sql`）。
4. **回滚触发线**：出现"单据写不进去 / 库存数量对不上 / 打印与附件打不开"这三类中的任一类且 30 分钟内定位不到 → 直接回滚，不硬扛。
5. **回滚后动作**：把回滚原因、时间、涉及提交与备份 md5 记进 `sys_task`（任务备注）+ `$JJX_BACKUP_DIR/` 日志。

## 附录 B：冒烟清单（第 26 项）

| # | 冒烟项 | 怎么算过 |
|---|---|---|
| 1 | 登录 | 账号密码登录成功，进首页不报错 |
| 2 | 菜单与权限 | 用**两个不同角色**账号登录，左侧菜单与按钮**不同**，且都不是"全有/全无" |
| 3 | 主流程一单到底 | 销售订单确认 → 生产工单 → 工序执行 → 报工 → 完工 → 成品入库，状态与数量前后对得上 |
| 4 | 库存 | 入库确认后库存数量、批次、流水都出现；隔离/处置台账能打开 |
| 5 | 打印 | 领料单、销售确认书各打一张，版式与内容正确 |
| 6 | 附件与图纸 | 上传一个附件、打开一个图纸/档案原图，都能显示（验证上传根路径正确） |
| 7 | 手机端 | 手机扫码打开 `/m/**`，能查任务、报工（需 HTTPS） |
| 8 | 通知/待办 | 触发一条事件（如提交审核），对应账号能在"我的待办/通知"看到 |
| 9 | 流水 | 任一单据的"流水"能打开并显示变更内容与附件 |

## 附录 C：生产口径速查（第 5/9/11/12 项）

| 项 | 生产口径 |
|---|---|
| 库名与账号 | 应用 `jjx_app` / 迁移 `jjx_mig` / 只读 `jjx_ro`，强口令；`root` 仅本机运维 |
| 连接方式 | `application-prod.yml`（环境变量注入），启动 `--spring.profiles.active=prod` |
| 初始化数据 | **DDL + 子集快照**（19 表）；禁止把 `db-clean-test-data.sh` 用在生产库 |
| 上传根 | `JJX_UPLOAD_ROOT` 显式设生产绝对路径；启动目录仍必须＝仓库根（或该变量已设） |
| 凭据 | `jjx-docs/accounts/` 不进生产交付包；`sys_config` 的 sms/smtp 值改占位；生产口令全部轮换 |
| 审计 | 迁移/清理摘要落 `$JJX_BACKUP_DIR/*-log.txt`（库内 `sys_oper_log` 会被清理脚本清空） |

## 附录 D：工作区未提交归属清单（第 21 项）

| 归属 | 内容 | 处理 |
|---|---|---|
| 并行会话（工艺资源 / 档案 OCR / 销售样品） | Java + Vue ~35 个文件（`EngineeringRoutingItem`、`ProductionStandardProcess`、`EngineeringArchiveImport*`、`archiveImport.ts`、`sample-workbench/*`、`components.d.ts` 等） | **由各自会话提交**（我不替他们提交，避免混入未完成的活） |
| 用户 | 删除 `jjx-docs/sql/migrations/101~106`、删除 `jjx-docs/assets/JST-001POOO/*.dwg|.cdr`（3 个）、`AGENTS.md` §9 服务生命周期那条 | 由用户决定提交或保留 |
| 我（Hermes） | 已全部提交，工作区无遗留 | — |

## 核查进度表（每查一项填一行，没证据不算查过）

| # | 项 | 层 | 状态 | 证据（命令 + 输出 / 提交号） | 日期 |
|---|---|---|---|---|---|
| 1 | 版本记账补 102~106 | 🔴 | ✅ 已补 | `db-migrate.sh --record 102..106 --yes` → applied = `66…109`，version=109；guard 备份 md5 逐个记录在 `jjx-backups/` | 2026-09-14 |
| 2 | DDL 基座 | 🔴 | ⏸ 用户定暂缓 | 结论见上（唯一可行＝从现库生成 DDL 基线） | 2026-09-14 |
| 3 | 101~106 归属 | 🟡 | ⏸ 用户有意删除（保持现状） | 工作区 ` D` ×6；内容在 git 历史 `7643353f` | 2026-09-14 |
| 4 | 提交 107~109 | 🟡 | ✅ 已提交 | `chore(sql): 提交已应用的迁移 107~109（dev-20260914-013）` | 2026-09-14 |
| 5 | 生产库连接/断言 | 🔴 | ✅ 已核出替换清单 | 见上表（4 个脚本 + dev.yml） | 2026-09-14 |
| 6 | 快照重校 | 🔴 | ⚠ 已过期待重出 | `--verify` → `inventory_item` 1536→1537（+1） | 2026-09-14 |
| 7 | DECISIONS 5 项 | 🟡 | ✅ 口径已定 | 已写入 `sql/init/DECISIONS.md`（5 项） | 2026-09-14 |
| 8 | 数据缺口对齐 | 🟡 | ✅ 已核出精确清单 | 66 条无身份 / 1535 条 item_code 全不符 / 19 条悬挂 / 2 条非物料行 | 2026-09-14 |
| 9 | 生产初始化路径 | 🔴 | ✅ 口径已定 | DDL + 子集快照；禁用清理脚本 | 2026-09-14 |
| 10 | 脱敏口径 | 🟡 | ✅ 规格已出 | `design/sensitive-data-masking-dev-20260914-002.md` | 2026-09-14 |
| 11 | 凭据清理 | 🟡 | ✅ 已核 + 口径 | `accounts/` 2 个文件均被 git 跟踪；口径见附录 C | 2026-09-14 |
| 12 | 生产库账号 | 🔴 | ✅ 已核 + 规划 | 现有 root/jjx_ro；规划见附录 C | 2026-09-14 |
| 13 | application-prod.yml | 🔴 | ✅ 已建模板 | `jjx-server/src/main/resources/application-prod.yml`（环境变量注入） | 2026-09-14 |
| 14 | 上传目录 | 🔴 | ✅ 已查清 + 口径 | 活＝仓库根；旧份需合并搬迁 | 2026-09-14 |
| 15 | 外部依赖 | 🟡 | ✅ 已核 + 口径 | MySQL/Redis 是 systemd；后端/OCR 手工起 → 生产要 unit | 2026-09-14 |
| 16 | 网络与证书 | 🟡 | ✅ 已核 + 口径 | 8080/3000 对外暴露需收回；证书在 `assets/certs/` | 2026-09-14 |
| 17 | 权限 fail-closed | 🔴 | ✅ 规格已出（代码待写） | `design/permission-fail-closed-dev-20260914-014.md` | 2026-09-14 |
| 18 | 跨模块 403 | 🟡 | ✅ 口径已定 | 只读查询开口子（`xxx:query` / 登录即可） | 2026-09-14 |
| 19 | 审计留痕 | 🟡 | ✅ 已核 + 建议 | `db-migrate.sh` 的 `REMARK` 未落盘（只有备份 + 版本记录） | 2026-09-14 |
| 20 | 门禁全绿 | 🔴 | ✅ 全绿 | check:docs ✓ / status-enums ✓（新增 0）/ vue-tsc 退出码 0 / mvn BUILD SUCCESS | 2026-09-14 |
| 21 | 工作区收敛 | 🔴 | 🟨 归属清单已出 | 见附录 D（按归属分派） | 2026-09-14 |
| 22 | 钩子安装 | 🟡 | ✅ 本 clone 已核 | `core.hooksPath=scripts/hooks` | 2026-09-14 |
| 23 | 分支与发布 | 🟡 | ✅ 口径已定 | dev + 1 tag + remote `HelloLeoLyn/jjx`；上线打 tag、从 tag 检出 | 2026-09-14 |
| 24 | 空环境演练 | 🔴 | ⛔ 受第 2 项阻塞 | 前置条件与步骤见上表 | 2026-09-14 |
| 25 | 回滚方案 | 🔴 | ✅ 已出 | 附录 A | 2026-09-14 |
| 26 | 冒烟清单 | 🔴 | ✅ 已出 | 附录 B（9 项） | 2026-09-14 |
