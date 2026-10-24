# BOM 规格属性生成与自动跟随修复记录

任务码：dev-20261024-023。执行者：Codex。日期：2026-10-24；2026-10-25 按用户最终口径返修。

本节记录本次最终实现；下方“2026-10-24 首次修复记录”保留历史，后端组合默认规格的旧实现已由本次替代。

1）最新规则与实现

A) BomItemEditor 列名为“规格”。现有用户布局、宽度和控件调整保留，提交仅包含本次新增修改。
B) 默认值通过前端唯一函数 generateBomSpecification 组合：尺寸非空直接返回原文，不加标题或单位；尺寸为空，按属性模板的 in_spec、spec_order、visible_when 生成值，跳过空项。中文相邻直接连写；相邻任一项含英文或数字，使用一个空格。例如“280gf 带点镀镍”。布尔支持模板自定义带点/无点文字；型号、品牌、备注不被额外拼入，独立厚度属性不参与。
C) 选料、保存前刷新、恢复自动、手改比较与审核提示共享前端组合规则。后端返回 size、attributes、schema 原始来源，不再决定 BOM 默认规格的显示组合。前端本次自动值通过 DTO 临时字段写入既有对照日志；新行日志记录真实行号，日志失败时 BOM 保存一起回滚。
D) 保留人工改写、人工清空；自动值跟随来源变化，人工值保留并提示。没有历史对照证据的非空旧值保留原文；未知来源或空属性不以空串覆盖手填值。

2）依赖任务与交付

A) dev-20261024-028，提交 d0d274c0：补 EngineeringBomVO.bomName，实际转换/H2 保存、改名、重读与空名称校验通过，已 push、待审核。
B) dev-20261024-029，提交 7b54b4db：统一 size 与专用尺寸组件，宽*长*厚、圆形直径*厚，缺项保留位置。旧厚度仅在单条编辑时安全合并；未知原文保留，原有厚度列暂作兼容，未执行 DDL 或批量历史订正。后端隔离37例、前端14例、类型和枚举检查通过，已 push、待审核。
C) dev-20261024-030，提交 bf3af7d9：前端属性配置支持常用/低频、规格参与/排序、显示条件和布尔文字；已使用模板允许增加无默认值可选属性，破坏性改变仍拒绝。常用预设先进入编辑草稿，由用户检查并保存后启用，不自动改现有模板或物料。后端40例、前端11例及类型/枚举通过，已 push、待审核。

3）验证与限制

A) 原023定向验证：BOM 编辑器/表单20例，规格组合/手改判定/既有打印20例，后端名称返回/规格来源/对照日志/BOM事务27例，均通过；包含尺寸不重复单位、中文/英文数字分隔、重开跟随、手改保护和过期异步响应。
B) 后端使用 .tmp 独立 Maven 输出与隔离 H2；不覆盖运行 jar，不操作服务。前端使用 Windows Job Object，测试进程树限1GiB、单worker；类型检查采用 vue-tsc --noEmit --composite false --incremental false。
C) 运行接口/浏览器及真实业务库未验证；只读账号 jjx_ro 仍 ERROR 1045，数据库基线检查不可用，不能写“全部门禁全绿”。用户本轮批准的 root 连接例外仅用于 sys_task 查询、领取和回写，未扩展为业务库排查权限。
D) 需要用户重启后端并刷新前端后实测；执行者自查不代替用户独立验收。库存 Excel 期初迁移仅登记为 dev-20261025-001，按用户要求暂缓，不在本轮实施。

2026-10-24 首次修复记录：

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
