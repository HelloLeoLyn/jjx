# 物料归类闭环与导入1A：阶段二后端实施及308应用记录

任务码：dev-20261024-004（阶段二后端，派工 hermes，执行 codex）
范围：迁移脚本、后端代码、对应测试、临时库演练及308正式应用。下方原准备记录保留为历史证据，最新应用与验证结果见末节；阶段三前端未做，任务保持进行中。
依据：AGENTS.md、multi-agent-protocol.md；material-master-target-model-dev-20261024-003.md 第7、8节。

## 背景与现状证据

1）执行前 git status --short 为空；sys_task 为 status=1、assignee_name=dahuang，remark 原长度364。使用 task-update.py note 按原状态和归属精确追加接手说明，影响1行并写后回查；没有重新认领。
2）正式库 ops.schema.version=307；inventory_material 为48列、1607条，其中1041条有分类、566条未归类。原始信息仍优先读取 original_name/original_specification，其次读取 remark 的明确来源标记；不从生成规格反推原始事实。
3）本轮随后观察到其它会话对备份目录及前端作业台文件的改动，这些文件未由本轮修改、暂存或还原。本轮始终未执行服务启停、git add、commit、push或正式库业务迁移。

## 准备的迁移与服务行为

1）308_material_classify_closure.sql：新增 class_source/class_confirmed 及组合索引，新增关键词规则表，显式 utf8mb4_unicode_ci。规则 keyword+category_code 联合唯一；match_field/ enabled 有CHECK约束。文件头写明前置计数、正式应用入口和回滚方法；不自行修改迁移账本。
2）归类三态使用后端枚举，分页、列表、搜索与导出共用查询条件：unclassified 为 category_id IS NULL；rule_unconfirmed 为 RULE 且未确认；confirmed 为已确认。
3）人工批量归类限制1至200个不同正整数ID。锁定分类/模板和物料后，校验属性单轨数据、材质允许范围及模板必填值，再写 category_id/template_id/MANUAL/已确认。缺失物料和校验失败返回逐条明细；意外数据库错误回滚整个事务。确认接口同样校验模板，保留原归类来源。
4）规则按 priority 降序、id升序依次匹配，NAME/SPEC/ANY 均为忽略大小写的字面包含，不解释正则；停用规则不参与。默认只预览；只有 confirm=true 写库。只处理未归类物料，写入前重新核对来源并使用 category_id IS NULL 条件，避免覆盖并发人工归类。
5）规则CRUD沿用 inventory:material:edit。人工归类后可通过同一规则库新增关键词→品类规则，无第二份规则配置或属性值表。

## 接口与入口覆盖

| 方法 | 路径 | 请求与行为 |
|---|---|---|
| GET | /inventory/material/page、/list、/search | query classStatus=unclassified/rule_unconfirmed/confirmed；返回classSource/classConfirmed |
| POST | /inventory/material/classify/batch | JSON {"materialIds":[1,2],"categoryId":18}；返回successCount/failCount/details |
| GET/POST/PUT/DELETE | /inventory/material/classify/rules | 新增/修改JSON含keyword、matchField、categoryCode、priority、enabled；修改含id，DELETE查询参数id |
| POST | /inventory/material/classify/apply-rules | confirm缺省false，返回dryRun/changes/failures；confirm=true才写库 |
| POST | /inventory/material/classify/{id}/confirm | 单条人工确认；未归类或模板校验失败不写确认标记 |
| GET | /inventory/material/master/import-template | 下载统一1A模板；categoryId可省略，文件按品类列逐行处理 |
| POST | /inventory/material/importTemplate | 列表原入口下载相同的统一模板 |
| POST | /inventory/material/master/import | multipart file；preview缺省true；autoClassify缺省false；保留可选categoryId兼容旧文件 |
| POST | /inventory/material/import | 列表原入口复用同一导入服务；preview缺省false以保留原入口执行约定；autoClassify缺省false；响应保留旧successCount/skipCount/failCount/failDetails结构 |

1）统一模板的第1行为使用说明、第2行为表头，包含品类、原名称、原规格、通用业务字段和全部启用模板属性列的并集；附品类编码/路径/必填属性/材质范围说明表。
2）每行先确认显式品类存在、启用且为可选末级，再按该模板检查属性；缺少字段或值报告“第N行：缺【列名】列或值”，材质越界报告列名和值。错误逐行收集，不以50条为限，不跳过错误继续写入；任一预检错误则整表不写入。未知的非空属性列、无表头数据、重复列、公式与重复物料也会报错。
3）显式填写品类且经模板校验通过的导入记 IMPORT/已确认；autoClassify 命中规则记 RULE/未确认；没有分类且未命中规则记 NULL/未确认。这保证三态覆盖导入结果。已人工确认的物料不会被导入覆盖，事务写入前再次核验。
4）旧“名称/物料名称+规格”以及“材料+规格”两列文件仍可导入，没有品类时进入未归类队列；原单品类元数据模板也兼容并校验版本。未归类新物料采用中性MAT编号（仍使用共享物料序号），不补默认分类、不重编历史码。未归类保存不要求模板必填属性；有分类的导入预检和人工确认均按模板校验。
5）标准单价、采购周期、安全/最高库存、再订货点、保质期、预警天数、批次控制沿用通用参数校验，不导入或修改库存余额/流水；可选参数未填时更新保留原值。

## 验证步骤与真实退出码

以下命令分别保存被测命令退出码后才截取日志，非管道末尾命令的退出码。Maven命令在jjx-server目录运行。

```bash
mvn -q -DskipTests compile
# exit 0

JJX_TEST_308=1 JJX_TEST_MYSQL=1 \
JJX_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/jjx_dryrun_308?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
mvn -q -Dtest=MaterialClassifyTest,Material308DryRunTest,MaterialImport1AControllerTest,MaterialMasterDataTest,MaterialMasterSchemaTest,MaterialMasterMysqlTest,MaterialCategoryOptionsPermissionTest test
# exit 0；59项，failures=0、errors=0、skipped=0

cd ../jjx-web
npm run check:docs
# exit 0

cd ..
node scripts/check-migration-collation.mjs
# exit 0；1个待办迁移文件
```

| 测试类 | 项数 | 验证范围 |
|---|---:|---|
| MaterialClassifyTest | 6 | NAME/SPEC/ANY、停用、字面匹配、200/201边界、逐项失败、默认预览、并发人工分类保护、权限与参数缺省 |
| Material308DryRunTest | 12 | 真实MySQL的308回填和三态、规则CRUD/唯一键/优先级、预览无写、落库与逐条确认、200条批量、意外DB错误事务回滚、统一模板、逐行缺列/越界、旧模板、自动归类/未命中、确认保护 |
| MaterialImport1AControllerTest | 3 | MockMvc验证旧导入入口接统一服务、错误行号/列名结构、统一模板下载、apply-rules默认预览 |
| MaterialMasterDataTest | 25 | 原物料主数据行为回归，新增列表/搜索/分页共用三态过滤 |
| MaterialMasterSchemaTest | 5 | 模板属性校验、来源读取等回归 |
| MaterialMasterMysqlTest | 7 | 真实MySQL会话临时表：主数据保存、导入整表预检及第二行失败回滚等回归 |
| MaterialCategoryOptionsPermissionTest | 1 | 分类选项的登录权限边界回归 |

测试不启动服务。Material308DryRunTest的所有测试写入均在外层事务中回滚；MaterialMasterMysqlTest使用会话临时表。历史MaterialMasterDatabaseTest依赖304版本正式库，本轮未启用；仅适配新构造参数以保持测试编译。

首次命令在仓库根目录运行Maven因无pom返回1，已改为jjx-server目录；新增测试首次有Wrapper参数访问类型编译错误，已修正。沙箱内Mockito自附加失败，获准在本机执行相关测试；308测试夹具补齐create_by等非空审计字段，并在JDBC修改模板后清理MyBatis会话缓存。以上失败均未写正式物料数据，最终结果如上。

## 308临时库演练证据

1）先核实jjx_dryrun_308不存在，随后创建该库；仅克隆物料、分类、模板、字典、字典项与sys_config共6张所需表，不生成备份、不复制人事表。
2）用实际308脚本在该库执行，退出码0。复制时按information_schema排除生成列，只写基础列，让生成列由数据库计算。

```text
target_schema: jjx_dryrun_308
schema_version: 307（克隆账本不更新，308仅执行DDL/DML演练）
迁移前: total=1607 categorized=1041 unclassified=566
迁移后: class_source=NULL class_confirmed=0 count=566
迁移后: class_source=RULE class_confirmed=0 count=1041
演练库inventory_material列数: 50
规则表TABLE_COLLATION: utf8mb4_unicode_ci
正式库inventory_material列数: 48
正式库ops.schema.version: 307
DRYRUN_EXIT=0
```

## 准备阶段的待确认点与应用前置（历史记录）

1）本轮仅准备，不构成正式应用或发布授权；正式库迁移、部署、服务重启和浏览器运行态联调均未执行。下一步仍需用户手工备份及另行授权应用308。
2）新增规则表已经用户本卡明确授权，但scripts/model-baseline.json不在本轮白名单；正式应用前须配套登记该表的批准记录并同步表数基线。本轮未修改规范或扩大白名单。
3）本轮status保持1、assignee_name保持dahuang；本轮没有提交哈希。实施代码15个、测试7个；另有308迁移、本文及history索引登记。独立验收尚未进行。

## 2026-10-24 正式应用308与阶段二收口

用户已独立复核准备结果，并明确授权使用手工备份应用308、仅在 scripts/model-baseline.json 的 approvedNewTables 登记规则表、执行指定回归及提交推送。本次不修改前端在制文件，不暂存备份目录，任务仍保持 status=1，前端阶段三未做；未启停或重启服务。

### 迁移入口与账本证据

```bash
bash scripts/db-migrate.sh jjx-docs/sql/migrations/308_material_classify_closure.sql --yes --task dev-20261024-004 --backup jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1501.sql
# RC=0
# 手工备份：6397951B / 133张表；脚本校验排除hr_employee
# 备份SHA256：ae928ee3bd6b90bb699ff91a9685bd8acfb88967b668718dc9330d8ba43a4eab
# 已归档：applied/308_material_classify_closure.sql
```

应用前正式库为307、inventory_material为48列，新列与规则表均不存在；应用后为308、50列。class_source 为可空 varchar(10)、utf8mb4_unicode_ci；class_confirmed 为非空 tinyint(1)、默认0。规则表为空，含keyword/category_code唯一键、NAME/SPEC/ANY与enabled约束，表及字符串列均为utf8mb4_unicode_ci。

账本 ops.schema.applied 含308，ops.schema.version=308；ops.schema.file.308 指向同名脚本。应用后归档文件SHA256与应用前相同：1b1581896056a5b44148cada0c426091a77a8cb17160b78f7f6b0ab1288d2bd7。待办目录原文件已不存在；迁移脚本未改动内容，仅由入口同名归档。

```sql
SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material';
-- 50
SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'
   AND COLUMN_NAME IN ('class_source','class_confirmed');
-- 2
SELECT COUNT(*) FROM inventory_material_classify_rule;
-- 0
SELECT config_value FROM sys_config WHERE config_key='ops.schema.version';
-- 308
SELECT FIND_IN_SET('308',config_value)>0 FROM sys_config WHERE config_key='ops.schema.applied';
-- 1
SELECT COUNT(*) FROM inventory_material
 WHERE category_id IS NOT NULL AND class_source='RULE' AND class_confirmed=0;
-- 1041
SELECT COUNT(*) FROM inventory_material
 WHERE category_id IS NULL AND class_source IS NULL AND class_confirmed=0;
-- 566
```

### 三态筛选SQL与真实条数

查询条件逐一对应后端 MaterialClassStatus，未加入额外筛选。

```sql
SELECT COUNT(*) AS unclassified FROM inventory_material WHERE category_id IS NULL;
-- 566
SELECT COUNT(*) AS rule_unconfirmed FROM inventory_material
 WHERE class_source='RULE' AND class_confirmed=0;
-- 1041
SELECT COUNT(*) AS confirmed FROM inventory_material WHERE class_confirmed=1;
-- 0
```

总数仍为1607，未执行额外数据订正。

### 本次门禁与验证

```bash
# 仓库根目录
bash scripts/check-model-baseline.sh
# RC=0；当前137张表、基线136张表；未批准新表0；仅增加本卡批准例外，未改其他基线或放宽检查
bash scripts/check-collation.sh --strict
# RC=0；基表列漂移0、基表默认漂移0
node scripts/check-migration-collation.mjs
# 应用前RC=0；1个待办迁移文件

# jjx-server目录
mvn -q -DskipTests compile
# RC=0
```

初次只读查询受沙箱TCP socket限制，按授权改为本机执行；首次任务查询误用了不存在的task_name字段，修正查询字段后RC=0。两次均未执行迁移或写业务数据。正式迁移及应用后校验均一次通过，无失败恢复、重跑或自动修数据。

```bash
# jjx-server目录；离线执行，测试JVM上限512m
mvn -o -q test -Dtest=MaterialMasterDataTest -DargLine=-Xmx512m
# RC=0；Surefire XML：tests=25、failures=0、errors=0、skipped=0
```

本次只运行上述指定回归；前期59项与308临时库演练的证据仍以准备阶段记录为准，未声称本次重新执行。HTTP与浏览器运行态联调未做，后端需后续明确授权重启后才会运行新实现。

```bash
# jjx-web目录
npm run check:docs
# RC=0；history 266篇、modules 4篇、task-docs 54篇；未改文档基线
```
