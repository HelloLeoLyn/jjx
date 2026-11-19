# 库位分层维护与细分移库交付

任务码：dev-20261119-004。授权：用户“先做004”。执行者：Codex；独立验收：用户。本报告为执行者自查，状态待审核，不代替独立验收。

交付行为

1）仓库与库位页面改为仓库选择与位置树。名称、层数、子位置均由用户维护；不写死区、架、层、格。保留原110条库位ID/名称/编码，不自动清洗、改名、去重或归类。临时存放分组和普通位置使用相同规则，158“在印刷，贴膜房间”保留。
2）仅启用的末级位置接收正向库存变动；上级按单位分别显示收、发、结存、预留。库位维护、批次库存的路径计算共用后端规则；批次列表显示完整位置路径，仓库/库位筛选加载真实选项。未分配位置的历史账本口径保留。
3）新增编码可留空，按仓库编码加流水号自动生成，支持修改且全局唯一。改名称/上级不重编编码，禁止循环、跨仓挂父级。禁用/删除依据真实批次、预留和历史引用；有历史的空位置可停用，不能物理删除。
4）容量由用户选择是否启用；关闭时不强制容量或尺寸，不伪造默认值。启用时需正容量和记账单位，入账核验本位置及祖先的容量；不自动换算、不混加不同单位。已用容量由批次派生，不再直接维护第二份库存。
5）有货末级位置使用“细分并分配库存”：最多50个新位置、500批，库存分页显示，逐批明确分配并确认；分配合计须等于原批次，预留批次拒绝，实物卷整卷移动。新位置、移库单、批次结存、成对流水与操作日志同事务；失败全部回滚。保留批号、原入库明细、检验批、生产/失效日期、卷参数、计量快照和成本。
6）细分凭证复用调拨单据，类型location，单号LS加UUID，记录实际操作者、批准/收发时间和明细状态。需同时有库位新增/编辑及移库新增/编辑/审核权限；通用调拨的提交、审批、驳回、收发、取消、改状态均禁止重新操作此类已即时过账凭证。不同单位的单据数量合计、未知成本金额为空。原普通调拨流程本次仅加细分凭证保护，不重做其工作流。
7）导入保留用户名称与重复行，编码留空由后端生成，尺寸未知留空，关闭容量管理；有错误则整批回滚。修复0正常/1停用的混用、默认仓库硬编码、分页参数别名、空容量渲染和虚假容量统计。

数据与迁移

1）378_location_hierarchy.sql：原库位表增加parent_id、capacity_enabled、capacity_unit及父级索引，原记录默认不启用容量管理，无数据回填。
2）379_transfer_item_inventory_identity.sql：调拨明细material_id允许为空，成品以inventory_item_id追溯，不伪造材料ID，无现有行订正。
3）两份迁移均经scripts/db-migrate.sh执行、记账回查并同名归档至migrations/applied/；没有新增业务表。使用用户已有手工全库备份jjx_erp_db_backup_20261119-1853_before-commit-20261119.sql，脚本已校验不含hr_employee；未自动生成或清理备份。
4）迁移工具补Windows兼容：原生Python路径转为Git Bash路径；Windows文件仍fsync后原子替换恢复记录，跳过不支持的目录fsync。已用真实378/379迁移验证执行、记账和归档；未运行依赖POSIX fcntl的完整隔离迁移脚本套件，同步命令指南。

验证及边界

1）后端：在jjx-server执行 `mvn -q -Dtest=LocationHierarchyMysqlTest,LocationSubdivisionTransferGuardTest,InventoryStockMutationServiceTest,InventoryStockMutationBoundaryTest -DargLine=-Xmx768m -Dsurefire.timeout=120 test`，环境JJX_TEST_MYSQL=1、MAVEN_OPTS=-Xmx768m。18项通过，其中12项为真实MySQL会话临时表事务：原名/编码、循环/跨仓、末级入账、分单位收发存、祖先容量、历史保护、分配守恒/预留/整卷、日志失败全回滚、批量状态原子性、成品身份与批次完整路径；另覆盖7个通用调拨入口保护及既有库存边界。正式库位置名称/ID/父级、批次数/数量、流水数的测试前后指纹一致。
2）临时表测试边界：MySQL不允许一个SQL重复引用同一临时表，库存汇总mapper使用单次扫描等价聚合替身；位置、批次、流水、调拨主明细、日志及回滚使用真实mapper SQL。该测试不能单独证明生产汇总SQL，既有统一变动入口调用校验与正式库stock strict对账另行验证。
3）前端：真实Element Plus组件5项通过（空容量表格与收发存、仓库ID兼容/空配置新增、11批分页保留分配、预留/读取失败拦截及改方案重新确认、导入口语原名与重复行）。命令：`node .tmp/dev-034-testdeps/node_modules/vitest/vitest.mjs run --config .tmp/location-004-vitest.config.mts`，JJX_TEST_FULL=1，JJX_TEST_INCLUDE=src/views/inventory/warehouse/__tests__/locationHierarchy.spec.ts，1worker/1G。测试依赖与配置复用本机临时目录，不写项目依赖。
4）`vue-tsc --noEmit`、`vite build`通过（Node内存3G；构建22.39秒，仅既有大包/组件同名提示）。未启动/重启服务，未验证浏览器连接新后端的完整运行态；后端由用户重启后再验收。
5）`check:status-enums`通过，新增魔法值0；`check:result-case`、`check:docs`、`check:collation:strict`、`check:migration-collation`、`check:notnull`、`check:lot:strict`、`check:iqc-lineage:strict`、`check:quality-ledger:strict`、`check:doc-no`通过。Windows的notnull检查使用实际Python可执行文件调用同一脚本，非空列实体缺失新增0。
6）`check:stock:strict`真实连接正式库，汇总≠明细、有明细无汇总、有汇总无明细、批次≠流水、有流水无批次五项全部0；原业务库存未订正。
7）`npm run validate`在表基线门禁停止：既有inventory_material_spec_attr_def、inventory_material_spec_value未备案，与003交付时相同。本任务未创建两表、未删除表或扩展基线；不能宣称validate全绿。其它后续门禁已逐项执行。推送若被该既有缺口拦截，按仓库允许的单次跳过规则记录实际结果，不修改钩子或基线。

交付文件及后续

位置实体/DTO/枚举、LocationHierarchyMapper/Policy/Service、库存变动入口、批次位置展示、原库位API与服务、细分凭证保护、前端维护/分配/导入/筛选、对应定向测试及迁移工具；另含迁移378/379、本任务计划/报告/索引及命令指南。仅本任务文件纳入提交。提交哈希、推送结果和回写时间记录在sys_task，避免报告产生自引用哈希。

遗留：既有两表基线缺口；后端需用户重启，浏览器连接新后端的完整运行态与独立验收未完成。普通调拨历史工作流不在本任务重做范围。
