# 物料规则逐项前后缀与选择应用完成记录

任务码：dev-20261119-003
执行者：Codex；独立验收：用户；本记录为实现与自查证据，不代替运行态验收。

## 已实现

命名、型号、规格每个参与字段都有“前后缀”入口，默认留空，已配置时标记。名称/型号使用逐字段prefixes/suffixes；规格字段使用prefix/suffix，均保存在原attribute_schema JSON中，无迁移、无新表列。缺值连同前后缀跳过；规格后缀恰为该字段单位时只输出一次。重算从属性生成，不在旧名称上叠加文字。保存与预览复用后端生成规则。

规则页统一“应用到现有物料”入口，替换旧的整品类批量改名按钮。未保存草稿需先保存。弹窗为“预览并应用规则”，后端分段扫描、筛选实际差异并分页返回（每页20/50/100）；支持编码/名称搜索、逐行勾选、本页选择、清空、跨页保留，每次最多500条。默认更新名称和规格；型号必须显式勾选，先生成型号，再生成依赖它的名称/规格。不变记录不列出，资料不足或冲突行显示原因且不可选。

应用请求只含选中ID、应用范围和预览指纹，不接收客户端指定的新名称/规格。后端逐行独立事务，按品类→模板→物料锁序重新读取和计算，跨品类或指纹变化拒绝，重复应用已一致行跳过；成功同事务记录sys_oper_log和原事件，日志失败则资料更新一并回滚。没有修改库存数量、单位、属性、历史单据或历史流水。即时库存按现有查询路径读取新的物料规格。

## 验证证据

1）Java定向测试64项通过，包含旧规则/型号/规格兼容、前后缀往返、缺值、单位、重复生成不叠加、分页与跨页选中、取消/范围校验、型号显式采用、无差异跳过、旧预览拒绝、独立事务及审计失败处理。JVM768M；使用mvn -f jjx-server/pom.xml -Dtest=MaterialRuleAffixTest,MaterialRuleApplicationTest,MaterialRuleApplicationDatabaseTest,MaterialNamingPatternTest,MaterialAttributeSchemaSeparatorTest,MaterialMasterNamingTest,MaterialModelPatternTest,MaterialTemplateCompatibilityTest -DargLine=-Xmx768m test（JJX_TEST_RULES_DATABASE=1）。
2）其中1项连接真实MySQL，所有写入仅在单连接同名临时表，正式表只读取结构；验证只更新已选记录、型号默认保留、审计落库，以及审计失败时名称和规格一起回滚；连接关闭销毁临时表。没有写入正式业务资料。
3）前端定向Vitest37项通过（MaterialMasterSettingsDialog.spec.ts与materialRuleApplication.spec.ts，单worker、1G内存）；vue-tsc --noEmit退出0（3G）。本机未安装主node_modules的Vitest，复用已有.tmp/dev-034-testdeps依赖和临时配置，插件关闭dts生成，未更改依赖清单。
4）check:docs、check:status-enums、check:result-case、check:migration-collation通过；NOT NULL strict新增缺失0；collation漂移0；stock strict五项全0；lot strict全部0；IQC lineage、quality-ledger、doc-no通过。
5）npm run validate未全过：check:model-baseline发现inventory_material_spec_attr_def、inventory_material_spec_value两张既有表未登记。information_schema.tables显示二者创建时间均2026-10-24 13:07:24，早于本次任务；本任务没有DDL，也未扩大baseline。其余门禁已分别验证。首次Windows Python输出编码问题用PYTHONIOENCODING=utf-8解除；NOT NULL脚本通过本机实际Python路径执行。

## 交付与限制

源文件14个，另任务plan/completion和INDEX三项文档。提交哈希和任务状态以sys_task回写及Git记录为准。未执行存量批量应用；由用户在弹窗选择实际要更新的物料。后端需用户重启载入新接口，浏览器运行态和真实业务资料的批量修改未验证。全量validate的既有表基线缺口保留，不在本任务扩大处理。

## 界面返修（2026-11-19，用户“改”）

原版把两个输入框放在同一“前后缀”弹层里，行内直接可见的只有分隔符，未满足用户要直观区分前后缀的操作预期。本次名称/型号/规格三套规则统一改为行内直接显示“前缀 [输入框]、后缀 [输入框]、分隔符 [输入框]”，取消合并按钮及弹层；空值仍保持原有含义，布局可换行。返修只改2个源码/测试文件，另更新本任务plan/completion，不改接口、数据库结构或业务数据。

将既有前后缀测试由调用setAffix改成真实ElInput输入：无需点击弹层即能看到三套规则的两个独立输入框和分隔符；分别输入并验证保存载荷；清空型号后缀仍保留其前缀和其它块草稿。组件测试32项通过；vue-tsc --noEmit通过；check:docs和check:status-enums通过。后端、库存与表基线未重跑（返修无相关改动，原有基线缺口记录仍保留）。测试复用已有.tmp/dev-034-testdeps，未修改依赖。浏览器运行态未验证，需用户刷新页面核对。原卡重新开始与完成时间、返修提交见sys_task追记。
