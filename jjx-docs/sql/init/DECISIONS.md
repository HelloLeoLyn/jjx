# 数据初始化脚本 —— 决策记录（用户定，Hermes 记）

> 目标（2026-09-12 用户明确）：**业务数据迁移初始化**——把现有 `jjx_erp_db` 的**正式数据**作为新环境的初始数据，
> 不是"干净空库的产品骨架"，也**不是"业务表全进"**。
> 判定维度是**数据**不是表：同一张表里可能正式数据与测试数据混存 → **正式数据进、测试数据不进**（2026-09-12 用户澄清）。
> 记录方式：用户按模块逐条拍板，本文件累计；未定项保留在文末"待定"。

## 统一规则

- 进：**正式数据**（用户已确认其"进"的表/行=正式数据）
- 不进：测试数据/演示草稿、各类日志（oper/login/error）、`sys_task` 开发看板、
  运行时时序/版本键（`sys_number_sequence`、`ops.schema.*`）、历史备份表（`*_backup_*` / `*_bak_*`）
- 特殊：ID 原样保留（业务单据互相引用，不能重编号）；静态文件（附件/图纸/打印模板）不在 SQL 内，需单独处理
- 判定测试数据的取证方式：`create_by/create_time` 集中且与某次功能验证吻合、`from_source` 为 `history_archive` 等自动化来源、
  approve_status/业务状态为草稿、批量导入表头行混入等（见各模块"证据"列）

## 系统模块 sys_*（20 张表 · 2026-09-12 已定）

| # | 表 | 行数 | 决定 | 说明 |
|---|---|---|---|---|
| 1 | sys_menu | 281 | **进** | 菜单骨架（含按钮型 F 行） |
| 2 | sys_role | 25 | **进** | 全部角色（用户已定：不筛内置/自定义，全进） |
| 3 | sys_role_menu | 867 | **进** | 角色-菜单授权 |
| 4 | sys_dict | 69 | **进** | 字典类型 |
| 5 | sys_dict_item | 434 | **进** | 字典数据 |
| 6 | sys_config | 61 | **进** | 剔除 `ops.schema.applied`/`ops.schema.version`；密钥类（hr.idcard.key / smtp_* / sms_api_key）占位 |
| 7 | sys_user | 24 | **进** | 用户已定：真实账号一起迁（含 24 个员工账号） |
| 8 | sys_user_role | 22 | **进** | 用户-角色关联 |
| 9 | sys_number_sequence | 2 | 不进 | 运行时单号计数器（规则在 sys_config.biz_no_rule.*） |
| 10 | sys_attachment | 0 | 条件进 | 用户："跟打印模板相关的可以保留"；实测当前 **0 行**，无实际影响；打印模板素材在 `jjx-docs/print_template/`，模板登记见 quality_template_registry（归质量模块单列） |
| 11 | sys_oper_log | 3 | 不进 | 操作日志 |
| 12 | sys_login_log | 3 | 不进 | 登录日志 |
| 13 | sys_error_log | 0 | 不进 | 错误日志 |
| 14 | sys_notification | 0 | 不进 | 消息通知 |
| 15 | sys_task | 783 | 不进 | 本项目开发看板数据 |
| 16 | sys_event_config_bak_20260814 | 0 | 不进 | 历史备份表 |
| 17 | sys_dept | 16 | **进** | 用户已定：客户真实组织架构一起迁 |
| 18 | sys_event_config | 139 | **进** | 事件→通知/派任务 规则配置 |
| 19 | sys_role 内置范围 | — | **进（全量）** | 用户已定：不再区分内置/自定义，25 个角色全进 |
| 20 | sys_tag | 148 | **进（全量）** | 用户已定：两组标签（supplier_goods 55 / material_attribute 9…）全进 |

> 用户 2026-09-12 澄清：上述标"进"的均为**正式数据**。

### sys_* 汇总结论

- 进（15 张）：sys_menu、sys_role、sys_role_menu、sys_dict、sys_dict_item、sys_config、sys_user、sys_user_role、
  sys_dept、sys_event_config、sys_tag，加上条件进的 sys_attachment（0 行）、以及随 DDL 走的空表结构。
- 不进（5 张）：sys_number_sequence、sys_oper_log、sys_login_log、sys_error_log、sys_notification、sys_task、
  sys_event_config_bak_20260814 —— 均为日志/看板/运行时/备份表。
- 注意：`sys_tag_rel`(1829) 不在 sys_* 模块内（表名以 sys_ 开头但属业务关联），按"业务数据全进"归入对应业务模块（供应商/物料）时再确认。

## 待定（需用户定，或后续模块再定）

1. **静态文件**：`sys_attachment` 记录 + 磁盘文件（仓库根 `upload/` 31M 与 `jjx-server/upload/` 45M 两份，且存在相对路径随 cwd 漂移问题）；
   打印模板 `jjx-docs/print_template/`。是否随初始数据一起交付、以什么形式（tar 包 + 放置路径说明）？
2. **目标环境形状**：空库全量导入（含 DDL）还是仅数据导入（目标库已有结构）？决定用 `mysqldump --no-create-info` 还是全量。
3. **测试脏数据**：仓库已有 `jjx-docs/sql/00_clean_test_data.sql`；迁移前是否先清理（如 process_icon_sample 25 行、engineering_archive_import 1 行等）？
4. **ID 是否原样保留**：业务单据互相引用，建议保留原自增 ID 并重置 AUTO_INCREMENT（待用户确认）。

## 工程模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 21 | engineering_standard_process | 49 | **进（正式）** | SP-101 面板/SP-103 面板隔片… 真实标准工序库 |
| 22 | jjx_screen_master | 4448 | **进（正式），但先剔表头行** | G0001/G0002 真实客户料号；**第 1 行 `screen_no='编号' / content='网 版 内 容 记 录'` 是 Excel 表头误导入**，迁移时剔除 |
| 23 | product | 1 | 不进（测试草稿） | `JST-263MHMC / 7600-005-Rev004`，`from_source='history_archive'`，2026-09-12 15:00:53 生成，状态=1 |
| 24 | engineering_bom + engineering_bom_item | 1 / 14 | 不进（测试草稿） | `BOM-JST-263MHMC` 同一批档案识别产物，approve_status=1 |
| 25 | engineering_routing + engineering_routing_item | 1 / 25 | 不进（测试草稿） | `RT-JST-263MHMC` 同上，routing_type=history_archive |
| 26 | engineering_archive_import | 1 | 不进（测试） | FAILED 记录（昨天分析的那条），原图在 jjx-server/upload 下 |
| 27 | engineering_process_icon_sample | 25 | 不进（测试） | 全部挂 archive_id=1，2026-09-12 15:00 生成 |
| 28 | engineering_base | 0 | 无数据 | 空表；结构随 DDL |
| 29 | engineering_film | 0 | 无数据 | 空表（菲林模块刚打通，尚未建档） |
| 30 | product_category | 0 | 无数据 | 空表 |
| 31 | product_config_model / product_config_option / product_instance | 0/0/0 | 无数据 | 空表（配置模型未用） |
| 32 | engineering_bom_backup_20260809 / product_backup_20260809 | 0/0 | 不进 | 历史备份表，建议连表一起清理（迁移时按 DDL 决定是否保留结构） |
| 33 | production_tooling | 7412 | 归**生产模块**再定 | 表名含 tooling 但属工装台账，行数 7282→7412 在增，需在生产模块确认正式性 |

### 工程模块汇总结论（2026-09-12 用户拍板）

- **进**：21 `engineering_standard_process`(49)、22 `jjx_screen_master`(4448)
- **不进**：23 product(1)、24 engineering_bom(+item 1/14)、25 engineering_routing(+item 1/25)、
  26 engineering_archive_import(1)、27 engineering_process_icon_sample(25)、32 两张 `*_backup_20260809`
- 空表无数据（结构随 DDL）：28 engineering_base、29 engineering_film、30 product_category、31 product_config_*
- 33 production_tooling(7412) 归生产模块
- 附带待确认：22 迁移时是否顺手剔除 Excel 表头误导入的那 1 行（`screen_no='编号'`）——剔除属删正式表数据，需先表级 guard 备份并再确认一次

## 采购模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 34 | purchase_supplier | 55 | **进（正式）** | 55 行全部 `create_by=admin`、`create_time=2026-09-12 12:18:18`（当天批量导入），企业名为真实供应商（深圳市艾高科电子材料/无锡市爱赛雅印刷器材…），status=1、del_flag=0 全有效 |
| 35 | sys_tag_rel（biz_type='purchase_supplier'） | 63 | **进（正式）** | 供应商与供货品类标签的挂靠关系，随 34 一起迁 |
| 36 | purchase_order / purchase_order_item | 0 / 0 | 无数据 | 空表；结构随 DDL |
| 37 | purchase_payment | 0 | 无数据 | 空表 |
| 38 | purchase_document | 0 | 无数据 | 空表（采购收货/发票/对账若走此表，同样无数据） |
| 39 | purchase_material_inquiry | 0 | 无数据 | 空表（物料询价） |
| 40 | sales_inquiry | 0 | 归属**销售模块** | 询价属销售侧，此处仅说明归属，不在采购模块定 |

> 观察（非决策）：采购菜单里的 采购计划/收货/发票/付款/报表 未找到同名独立表（全库无 `purchase_plan`/`purchase_receipt`/`purchase_invoice`），
> 可能统一走 `purchase_document`、或页面为未落地实现——需要时可单独核一次。

### 采购模块汇总结论（2026-09-12 用户拍板）

- **进**：34 `purchase_supplier`(55，用户确认为正式名录)、35 `sys_tag_rel` 中 `biz_type='purchase_supplier'`(63)
- 无数据（结构随 DDL）：36 purchase_order / purchase_order_item、37 purchase_payment、38 purchase_document、39 purchase_material_inquiry
- 40 sales_inquiry 归销售模块

## 库存模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 41 | inventory_material | 1601 | **进（正式）** | 全部 admin 于 2026-09-12 批量导入、status=1；编码两族 RM(821 原材料)+AU(780 辅料)；真实物料名（0.125中砂PC(尚昇)…） |
| 42 | inventory_item | 1536 | **进，但需处理 3 处** | 统一库存身份：MATERIAL 1535 + PRODUCT 1。① **66 条物料没有库存身份**（1601-1535）② `item_code` 是旧编码 `MTR2026…`，与物料现编码 `RM/AU…` 不一致（source_id 关联完好、无悬挂）③ PRODUCT 那条指向 `product_id=1`（历史档案测试草稿） |
| 43 | inventory_warehouse | 2 | **进（正式）** | WH01 成品仓（销售发货默认出库仓）、WH02 原料仓，2026-08-18 建 |
| 44 | inventory_material_category | 1 | **进（正式）** | INK 油墨印刷（2026-09-02 建，方案 sample-print-color-ink-master-data） |
| 45 | inventory_item 中 item_type='PRODUCT'(1) | 1 | **不进** | 指向测试产品 product_id=1 |
| 46 | 其余库存业务表 | 0 | 无数据 | inventory_stock / stock_item / transaction / inbound(+item) / outbound(+item) / stocktake(+item) / transfer(+item) / iqc_* / alert_log / storage_location 实测全 0（该模块业务尚未跑起来） |
| 47 | sys_tag_rel（biz_type='inventory_material'） | 1766 | **待确认** | 2026-09-12 15:24:20~15:24:33 由同一账号在 13 秒内批量生成，≈每个物料 1 个标签；若这批打标是正式品类归属则随 41 进，若是测试打标则不进 |

> 迁移注意：42 的 `item_code` 与 41 的编码族已不一致（历史遗留），迁移时建议**保留现状按源数据搬**，
> 并在迁移后单独做一次"物料↔库存身份对齐"（补 66 条 + 刷新 item_code），或直接由 41 重建 42。

### 库存模块汇总结论（2026-09-12 用户拍板：47 进，其余按建议）

- **进**：41 `inventory_material`(1601)、42 `inventory_item`(1536，按现状搬 + 迁后对齐：补 66 条、刷新 item_code)、
  43 `inventory_warehouse`(2)、47 `sys_tag_rel` 物料部分(1766，用户确认为正式打标)
- **不进**：44 `inventory_material_category`(1，已核查实质废弃 → 随 dev-20260912-010 一起清理)、
  45 `inventory_item` 中 `item_type='PRODUCT'`(1，指向测试产品)
- 无数据（结构随 DDL）：46 其余库存业务表（stock/stock_item/transaction/inbound/outbound/stocktake/transfer/iqc_*/alert_log/storage_location 全 0）

## 销售模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 48 | sales_customer | 11 | **待用户逐行定** | 10 条为 2026-08-13 建：江苏盛泰科技有限公司（疑似真实）、AD科技有限公司 / 德国汉高贸易有限公司 / 东京电子株式会社 / 上海浦东进出口有限公司 / 深圳创新科技股份有限公司 / 伦敦国际咨询有限公司 / 巴黎时尚贸易公司 / 金泰贸易有限公司（疑似示例造数）；第 11 条 `V1验收测试客户-0826` **名字自带"验收测试"** |
| 49 | sales_sample_order | 1 | **不进（测试）** | `JST001MEOL`、`product_id=1`、sample_status=4、sample_qty=5、当前工序"下线冲型"、2026-09-11 22:54 建 / 09-12 11:04 更新 —— 打样流程（dev-20260912-001 独立表改造）验证单 |
| 50 | 其余销售表 | 0 | 无数据 | sales_inquiry / sales_quotation(+item/flow) / sales_order(+product/review/stock_reserve) / sales_delivery / sales_receipt / sales_invoice / sales_return(+item) / sales_sample_transfer|round|process|bom 实测全 0 |

> 说明：48 只有"逐行筛选"才有意义（1 条明确测试 + 10 条疑似示例）；若客户档案暂无可信的正式数据，建议整体不进，等客户主数据建档后再导入。

### 销售模块汇总结论（2026-09-12 用户拍板）

- **不进**：48 `sales_customer`(11，整体不进)、49 `sales_sample_order`(1，测试)
- 无数据（结构随 DDL）：50 其余销售表

## 生产模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 51 | 生产模块其余表 | 0 | 无数据 | production_order / production_task / production_task_event / production_operation_execution / production_operation_record / production_work_report / production_quality_inspection(+item) / production_equipment / production_trace_log 实测全 0 |
| 52 | production_tooling | 7282 | **建议不进（待用户确认）** | ①与 22 `jjx_screen_master` **同源**：4500 条编号（G/F/B/H/C/A####）两表都有；②它是 2026-08-12 的早期导入，网版主数据是 2026-09-01 重导并挂「网版管理」菜单（engineering:screen:*），工装台账页 `views/production/tooling` **无菜单入口**（ToolingController `/production/tooling` 代码在、未启用）；③其**独有 2782 条是空壳记录**：`remark` 为空、仅 `tooling_no=location=G0010` 之类，无网版内容 |

> ⚠ 反向风险提示（需你判断）：如果业务口径是"每张网版都要留痕、7282 才是完整台账"，那 52 就应连表带数据一起迁
> （并补一个工装菜单，属另一个任务）；若以「网版管理」为准，则只迁 22 的正式部分即可。
> 另：22 `jjx_screen_master` 的表头垃圾行**实为 2 行**（`screen_no='编号'`、`screen_no='新编号'`，content 都是"网 版 内 容 记 录"），迁移时 2 行都要剔，先前记的 1 行需更正。

### 生产模块汇总结论（2026-09-12 用户拍板）

- **不进**：52 `production_tooling`(7282)
- 无数据（结构随 DDL）：51 生产模块其余表
- 后续动作：已登记 **dev-20260912-011**「核查 production_tooling（工装台账）使用情况」——确认该表/工装功能是否仍被使用、
  与 `jjx_screen_master` 的关系、是否需要补菜单或下线，结论出来后再决定这张表要不要单独处理

## 质量模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 53 | quality_template_registry | 100 | **进（正式/配置目录）** | 质量与文控记录模板登记表：JJX-QR-001 文件修订申请表…制造指令单等；2026-08-28 批量建立；owner_dept 覆盖 生产部20/品管部19/文控中心19/业务部15/资材部14/行政部8/工程部5；status 生效20/停用80；category blank90+data10；print_mode dual4+system3。**缺它则打印中心与质量记录归档页无目录**。⚠ 19 条 `file_id` 非空但 `sys_attachment` 实测 0 行 → 悬挂引用，迁移时需决定置空或补附件记录 |
| 54 | quality_template_print_log | 0 | 无数据 | 模板打印日志 |
| 55 | production_quality_inspection(+item) | 0 / 0 | 无数据 | 生产质检 |
| 56 | inventory_iqc_quarantine / disposition_order / return_order / rework_order / scrap_order | 0 | 无数据 | 来料检验(IQC)系列 |

### 质量模块汇总结论（2026-09-12 用户拍板）

- **进**：53 `quality_template_registry`(100)
- 无数据（结构随 DDL）：54 quality_template_print_log、55 production_quality_inspection(+item)、56 IQC 系列
- 53 的 19 条 `file_id` 悬挂（`sys_attachment` 实测 0 行）处理建议：**迁移时置空 file_id**；
  实测模板文件在磁盘上是有的（`upload/quality_template/` 23 个 + `jjx-docs/print_template/` 34 个），
  随"静态文件包"一起交付，迁移后可按文件名重新挂接（或保持不挂，仅保留目录）

## 人事模块（2026-09-12 清单：Hermes 建议 + 证据，待用户拍板）

| # | 表 | 行数 | Hermes 建议 | 证据 |
|---|---|---|---|---|
| 57 | hr_employee | 54 | **进（正式）** | 54 名员工全部 `employment_status=2`（正式）、无离职、无删除记录；2026-09-10 批量导入；字段含工号/姓名/部门/岗位/学历/入职离职/简历。⚠ **敏感**：52 条 `id_card_no`（AES 加密）、50 条含地址（`id_card_address`/`current_address`）；24 条已关联系统账号 `user_id` |
| 58 | hr_dept_mapping | 13 | **进（正式，可重建）** | 导入时的"部门文本 → sys_dept.dept_id"映射（source_name/dept_id/remark），保留导入可追溯性 |
| 59 | hr_position / hr_education 字典 | 12 / 8 | 已随 sys_dict_item 进 | 岗位、学历字典，在 sys_* 第 4/5 条内 |
| 60 | sys_dept | 16 | 已在 sys_* 记录（进） | 见第 17 条 |

> ⚠ **密钥迁移强提示**：`sys_config.hr.idcard.key`（当前值 `jjx-hr-idcard-key-2026-change-me`）是身份证号的 AES 密钥。
> 迁移时必须**保持与原环境一致**，否则 54 条员工里的 52 条身份证号在新环境**解不开**；若目标环境要换密钥，
> 需在新旧环境都可用的情况下先解密再加密（另起任务）。
> 另：仓库 `.gitignore` 屏蔽含员工身份证号的导入源文件，迁移脚本里保留的是 AES 密文，不要混入明文。

### 人事模块汇总结论（2026-09-12 用户拍板：57 进，58/59 先核查，60 进）

**58/59 核查结论（2026-09-12 实查 + 用户口径修正）**

- 58 `hr_dept_mapping`(13)：后端 `HrEmployeeServiceImpl` 第 539 行导入时执行
  `SELECT m.source_name, m.dept_id FROM hr_dept_mapping m`（Excel 部门文本 → sys_dept.dept_id），实体/Mapper 均在；
  数据 13 条、备注 `dev-20260910-011 新组织架构重建（83）`。前端无直接引用（映射在后端做）。
  **用户口径（2026-09-12）：别名字段只是导入初期（刚开账）的过渡方案，以后导入严格按现有真实部门（sys_dept.dept_name）匹配
  → hr_dept_mapping 不进初始数据，且可移除（表 + 别名映射代码）**。
  影响面：现有 54 名员工的 `dept_id` 已落库，移除映射表**不影响已导入数据**；仅"再用旧写法（品质部/部品管/加工/组装…）导入"才会匹配不到，
  这正是新口径要收紧的地方。移除后导入链路保留：`deptNameIndex()` 精确匹配部门名 + 去"车间/部门/部"后缀的容错（`resolveDept` 第 685 行）。
- 59 `hr_position`(12) / `hr_education`(8) 是**字典**不是表：前端 `views/hr/employee/index.vue:477-478` 用 `useDict('hr_position')`/`useDict('hr_education')`，
  后端 `HrEmployeeServiceImpl:581` 导入时查 `hr_education`；且数据对得上（岗位取值 作业员26/印刷师傅7/主管6… 全命中字典；学历存 1~8 字典值）→ **随 sys_dict_item 进，无清理需求**
- 60 `sys_dept`(16)：见系统模块第 17 条（进）
- 结论：57 进、59 随字典进、60 进；**58 不进且可移除**（待用户决定是否登记清理任务）

## 文档/业务/其他模块（2026-09-12 清单：全部无数据）

| # | 表 | 行数 | 结论 | 说明 |
|---|---|---|---|---|
| 61 | portal_language_config / portal_page_content / portal_product_display | 0/0/0 | 无数据 | 官网/门户配置 |
| 62 | biz_requirement / biz_requirement_approval | 0/0 | 无数据 | 业务需求登记与审批 |
| 63 | order_material_reserve | 0 | 无数据 | 订单物料预留 |
| 64 | sales_order_stock_reserve | 0 | 无数据 | 已在销售模块 50 条内记录 |
| 65 | sys_attachment | 0 | 已在系统模块第 10 条记录（0 行，条件进） | 附件记录 |

## 汇总（截至 2026-09-12，全部模块已定）

全库 **106 张表**，其中有数据的 **37 张**已逐条拍板，其余 69 张为空表（只随 DDL 建结构）。

**进（正式数据）**：
sys_menu、sys_role、sys_role_menu、sys_dict、sys_dict_item、sys_config（剔除 ops.schema.*）、sys_user、sys_user_role、
sys_dept、sys_event_config、sys_tag、engineering_standard_process、jjx_screen_master（剔 2 行表头）、purchase_supplier、
sys_tag_rel（`purchase_supplier` 63 + `inventory_material` 1766）、inventory_material、inventory_item（按现状 + 迁后对齐）、
inventory_warehouse、quality_template_registry（file_id 置空）、hr_employee（52 条身份证密文，密钥原样带）

**不进**：
sys_number_sequence、sys_oper_log、sys_login_log、sys_error_log、sys_notification、sys_task、sys_event_config_bak_20260814、
product、engineering_bom(+item)、engineering_routing(+item)、engineering_archive_import、engineering_process_icon_sample、
engineering_bom_backup_20260809、product_backup_20260809、inventory_material_category（另立 dev-20260912-010 清理）、
inventory_item 的 PRODUCT 行、sales_customer、sales_sample_order、production_tooling（另立 dev-20260912-011 核查）、
hr_dept_mapping（用户口径：导入过渡方案，以后严格按真实部门，可移除）

**空表（结构随 DDL）**：其余 69 张。

## 模块进度

- [x] 系统模块 sys_*（2026-09-12 用户逐条拍板）
- [x] 工程模块（2026-09-12 用户拍板：21/22 进，其余不进）
- [x] 采购模块（2026-09-12 用户拍板：34/35 进，55 家供应商确认为正式）
- [x] 库存模块（2026-09-12 用户拍板：47 进，其余按建议）
- [x] 销售模块（2026-09-12 用户拍板：48/49 均不进）
- [x] 生产模块（2026-09-12 用户拍板：52 不进 + 登记 dev-20260912-011 核查）
- [x] 质量模块（2026-09-12 用户拍板：53 进）
- [x] 人事模块（2026-09-12 用户拍板：57 进 + 58/59 核查（在用，进）+ 60 进）
- [x] 文档/业务/其他模块（2026-09-12 清单：全部无数据）

**下一步（待用户决定）**：①静态文件（附件/图纸/打印模板）如何交付；②目标环境是空库全量还是仅数据；
③ID 是否原样保留；④测试脏数据是否迁移前先清；⑤20 修正项：jjx_screen_master 剔 2 行表头、quality_template_registry 的 file_id 置空。
