# BOM 规格属性生成与自动跟随修复记录

任务码：dev-20261024-023。执行者：Codex。日期：2026-10-24。

1）授权与任务记录

用户在分析后明确要求实施修复；本机只读账号 jjx_ro 查询返回 ERROR 1045，用户随后明确允许先改代码、稍后补任务领取。未以 root 进行业务只读查询，未修改 sys_task，未进行数据库迁移或历史数据订正。工作区起始 git status --short 为空；原卡原文依据 20261024-1939 备份快照，原实现提交 ca4f084f。本记录是执行者自查，不是独立验收结论。

2）修复结果

A) BomItemEditor 选料通过现有物料详情接口取得 specificationGeneration；刷新及 BOM 保存前重新核对属性生成结果。物料已存规格为空但属性可生成时仍能带入。生成失败保留内容并提示重试；未关联快速模式保持手填。
B) EngineeringBomServiceImpl 查询通过 MaterialMasterService.specificationPreview 生成当前对照值；保存时将实际落库行号、物料编号、BOM 规格、当时生成规格追加到现有 sys_oper_log.detail。日志与 BOM 保存共用事务，不新增数据库列。
C) 重新打开时，规格仍等于上次保存的自动值即可跟随新属性；手填、人工清空保留，支持恢复自动；编辑与审核页面显示人工改写及属性变化提示。没有对照记录的历史非空值无法证明曾为自动值，因此保留原文；历史查询本身不写数据。
D) 选料请求使用版本和物料编号排除过期结果，生成过程中的手工输入不被覆盖；BOM 保存等待生成完成，并拦截等待期间重复提交。既有规格旧值→新值留痕继续使用 itemChanges，操作人与时间沿用操作日志。

3）自查命令与结果

A) jjx-server：mvn -o -q -DskipTests compile，退出 0。随后 mvn -o -q -Dtest=BomSpecificationServiceTest,BomItemReviewFlowTest,BomGroupPersistenceTest,BomCommonMaterialAndSplitTest,BomItemBatchReviewTest,BomMaterialBatchLinkTest -DargLine=-Xmx512m test，退出 0，35/35。H2 用例验证真实行号、保存重读以及日志失败时 BOM 与日志一起回滚；不能据此声称真实 MySQL 运行态已验证。
B) jjx-web：node --test src/components/__tests__/BomItemEditorGroups.test.cjs src/views/product/bom/components/__tests__/BomFormDialog.test.cjs，退出 0，19/19；包含空已存规格、属性更新、手填/清空、恢复、乱序选料、失败重试及保存等待。
C) Vitest：bomSpecMode.spec.ts 与 bomPrintLayout.spec.ts，退出 0，15/15。使用已有隔离依赖及 Windows Job Object，测试进程树 1 GiB、单 worker。
D) 常规 vue-tsc --noEmit 报既有 productFilePreview.ts 的 TS6307 文件收录错误；vue-tsc --noEmit --composite false --incremental false 退出 0，未更改工程配置或相关文件。check:status-enums、check:docs、git diff --check 通过。
E) 表数基线检查无法读取目标数据库，未通过运行态核验；没有新增表或迁移。服务未启动、停止或重启，未覆盖运行 jar。

4）待办与验证边界

A) 数据库连接恢复后，补原卡领取/完成回写、开始/完成时间及实际提交哈希；本次未宣称任务状态已变更。
B) 用户重启后端、刷新前端后，实测选料→手改→保存→重开→物料属性修改→自动跟随/手改提示；真实库与浏览器尚未验证。
C) 若历史规格与当前属性不同且没有旧对照证据，继续保留历史值；需要自动跟随的行可通过“恢复系统生成”并保存建立对照。
