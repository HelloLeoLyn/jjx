# history/ 索引（历史快照层 · 158 篇 md；总数以 `node scripts/check-docs.mjs` 输出为准，2026-09-29 校正）

> ⚠️ 本页是**历史快照清单**（实施记录 / 方案 / 分析 / 核查），按日期倒序，**不保证反映当前实现**。
> 想知道"某模块现在是什么样" → 看 `jjx-docs/modules/<模块>.md`；入口导航见 `jjx-docs/README.md`。
> 2026-09-14 目录改名：`analysis/` → `history/`（dev-20260914-004），文件与登记关系未变。
> 2026-09-10 校对：表头原写"90 篇，2026-09-03 生成"，实际 122 篇、登记 122 条（2026-09-10 补登记 20 篇）。

生成：python3 遍历 history/*.md 取文件名日期 + 首个 # 标题。增删文件后重新生成。

@@
| 日期 | 文件 | 标题 |
|---|---|---|
| 2026-10-11 | product-work-spec-version-trace-design-20261011.md | JJX ERP 产品作业规范版本与历史追溯设计方案 |
| 2026-10-11 (dev-20261011-004) | product-work-spec-p1-implementation-dev-20261011-004.md | 产品作业规范版本追溯 P1 实施拆解（含 §15 新表提案） |
| 2026-10-11 (dev-20261011-003) | product-work-spec-p0-audit-dev-20261011-003.md | 产品作业规范版本追溯 P0：现状核查与最小闭环评估 |
| 2026-10-10 (dev-20261010-032) | product-spec-layout-and-customer-docs-dev-20261010-032.md | 产品作业规范抽屉布局、组件化与客供资料（来源/类型）讨论结论 |
| 2026-10-10 (dev-20261010-028) | sample-requisition-qr065-and-defect-record-dev-20261010-028.md | 样品需求单 QR-065（挂样品单）与打样不良记录：提案与实施方案 |
| 2026-10-10 (dev-20261010-026) | color-check-sheet-handoff-dev-20261010-026.md | 规范分色检查表工程自查复查与预览打印实施交接方案 |
| 2026-10-10 (dev-20261010-024) | print-spec-panel-dev-20261010-024.md | 印刷规范只读工序、整组备注留痕与指导图预览打印 |
| 2026-10-10 (dev-20261010-020) | product-work-spec-changes-dev-20261010-020.md | 作业规范变更引用、打印选择与实际溢出附页 |
| 2026-10-10 (dev-20261010-018) | product-work-spec-layout-dev-20261010-018.md | 产品作业规范材料口径与纸张网格排版修复 |
| 2026-10-10 (dev-20261010-010) | product-work-spec-sheet-dev-20261010-010.md | 产品作业规范参考纸张布局与工程维护实施 |
| 2026-10-10 (dev-20261010-009) | routing-save-guard-dev-20261010-009.md | 工艺路线新增修改：保存操作与未保存离开提醒方案 |
| 2026-10-10 (dev-20261010-006) | routing-change-log-dev-20261010-006.md | 根治工艺路线修改流水误报与明细差异漏记 |
| 2026-10-09 (dev-20261009-056) | work-order-material-calculation-dev-20261009-056.md | 工单用料计算与历史方案复用需求方案 |
| 2026-10-09 (dev-20261009-044) | bom-project-name-dev-20261009-044.md | BOM项目导入归位并支持未匹配名称原样保存 |
| 2026-10-09 (dev-20261009-041) | bom-import-quantity-source-dev-20261009-041.md | 修复BOM导入实发数量间接覆盖应用料 |
| 2026-10-09 (dev-20261009-039) | bom-applied-quantity-dev-20261009-039.md | BOM应用料改为系统计算并移除Excel录入 |
| 2026-10-09 (dev-20261009-029) | engineering-drawing-workspace-dev-20261009-029.md | 工程图纸操作优化与图纸版本、打印联动 |
| 2026-10-09 (dev-20261009-024) | engineering-drawing-categories-dev-20261009-024.md | 工程图纸名称与上传图种分类统一 |
| 2026-10-09 (dev-20261009-010) | product-spec-preview-dev-20261009-010.md | 产品电子文档集前端预览与打印版式 |
| 2026-10-09 (dev-20261009-009) | sales-order-shortage-permission-dev-20261009-009.md | 销售订单齐套检查统一专用权限 |
| 2026-10-09 (dev-20261008-030) | delivery-address-selection-dev-20261008-030.md | 发货建单接入客户地址簿与公司地址 |
| 2026-10-09 (dev-20261008-013) | production-bom-version-dev-20261008-013.md | 工单继承计划 BOM，统一领料与打印版本取数，保留历史未绑定兼容 |
| 2026-10-08 (dev-029) | customer-shipping-address-book-dev-20261008-029.md | 客户收货地址簿：多收货地址 + 下单/发货默认链（含公司地址候选）（阶段一：DB+后端） |
| 2026-10-08 (dev-024) | merged-delivery-real-data-dev-20261008-024.md | 发货管理接入真实数据 |
| 2026-10-08 (dev-011) | sales-delivery-oqc-flow-dev-20261008-011.md | 销售发货 OQC 放行前置及签收流程修复 |
| 2026-10-08 (dev-009) | qr026-excel-template-dev-20261008-009.md | QR-026 Excel 改为原模板填充 |
| 2026-10-08 (dev-008) | mockito-maker-conflict-dev-20261008-008.md | 统一采购付款测试 Mock 模式，修复全量测试冲突 |
| 2026-10-08 (dev-006) | delivery-excel-export-dev-20261008-006.md | 送货单增加可编辑 Excel 导出 |
| 2026-10-08 (dev-005) | qr026-print-margins-dev-20261008-005.md | QR-026 纸版左右留白统一15mm |
| 2026-10-08 (dev-004) | qr026-print-layout-dev-20261008-004.md | QR-026 纸版送货单位置与字体优化 |
| 2026-10-08 (dev-002) | sales-order-integrity-dev-20261008-002.md | 销售订单金额组成、编辑保护及完工状态同步 |
| 2026-10-08 (dev-001) | purchase-payment-source-dev-20261008-001.md | 采购付款列表来源订单追溯 |
| 2026-10-07 (dev-016) | purchase-payment-approver-dev-20261007-016.md | 采购付款审批人由登录身份获取 |
| 2026-10-07 (dev-015) | purchase-payment-flow-dev-20261007-015.md | 采购付款入口与申请金额口径统一 |
| 2026-10-07 (dev-014) | purchase-order-echo-dev-20261007-014.md | 采购订单供应商与订单类型回显修复 |
| 2026-10-07 (dev-013) | order-amount-alignment-dev-20261007-013.md | 订单金额汇总统一左对齐及采购付款、退货退款同类问题排查 |
| 2026-10-07 (dev-012) | bom-edit-status-dev-20261007-012.md | BOM 修改入口状态限制与驳回编辑规则对齐：行按钮、顶部按钮、入口保护 |
| 2026-10-07 (dev-011) | bom-detail-reuse-dev-20261007-011.md | BOM 详情接入公共明细组件：项目列共用并保留模数、基数 |
| 2026-10-07 (dev-010) | bom-project-column-dev-20261007-010.md | BOM 审核明细补充与修改界面一致的项目列 |
| 2026-10-07 (dev-009) | process-operation-icon-dev-20261007-009.md | 修复公共工序组件只读模式图标不显示：拖拽组件与属性同名导致分支错误 |
| 2026-10-07 (dev-008) | route-process-tabs-dev-20261007-008.md | 工艺路线详情与审核对齐修改界面双层 tabs：分类共用、工序图标及印刷参数分列 |
| 2026-10-07 (dev-007) | approval-components-dev-20261007-007.md | BOM 与工艺路线展示组件及公共审核意见表单：模块内展示复用、跨模块审核表单及验证阻断记录 |
| 2026-09-30 (dev-017) | shortage-alert-atp-phased-plan-dev-20260930-017.md | 齐套判定与缺料预警：占用口径解耦 + 分阶段对齐业内 ATP（实施计划）：002「不预警材料采购」根因复核（共享池/无占位/现货覆盖短路）、v4 定稿 4 处修正（2.3/2.4 两个 gap、在途过滤按各单交期、预占停写、验收数字按实供）、P1~P4 分阶段实施与回退（dev-20260930-017） |
| 2026-09-30 (dev-015) | inbound-source-quantity-dev-20260930-015.md | 生产入库区分「来源批次数量 / 本单数量」并展示处置单据：页面口径没表达清楚（来源批 50 / 本单 45 / 不合格 5，非数据错）；改为只读投影分列展示并列出同批让步入库与报废单据（dev-20260930-015） |
| 2026-09-30 (dev-013) | sample-single-product-dev-20260930-013.md | 样品单一单一成品：新增编辑单产品表单、报价带入边界、后端约束与快照同步 |
| 2026-09-29 (dev-005) | inventory-inspection-stock-carrier-dev-20260929-005.md | 库存「待检」载体三选一：收货→待检库存对齐业内（①库存状态格/②独立待检仓/③隔离表升格）；结论先定③、①为目标态、②不推荐；本轮不做代码实施（dev-20260929-005） |
| 2026-09-29 (dev-003) | iqc-disposition-truth-rootfix-dev-20260929-003.md | IQC 处置真源收敛核查与根治方案（IN260929005 实测 6 条）：关批判据改为「待处置=0 且在途=0」、删 lot/iqc_batch 第二真源计数、隔离状态删列改派生（删 4 个终态枚举）、报废 remark 按送审原因分支、让步件并入原批次口径写死；含工作台单页化（上列表下明细）与 024 §2.2/§7 决策1 的待确认变更（dev-20260929-003，承接 dev-20260928-010） |
| 2026-09-28 | quality-model-governance-merged-20260928.md | 质量模块治理合并版：问题补遗 8 条 + 建表设计规范（默认拒绝 / 准入≥4 条判据 / 表数基线 124 只许缩小，P0 最高优先）+ 待拍板合并 15 条 + 页面三层链路设计 |
| 2026-09-28 | quality-issues-report-20260928.md | 质量/采购模块 E2E 联调问题报告：A~F 六类问题清单（含账目分叉/报废断头/复检入库/字段语义/前端口径/模型膨胀 124 表）、9 条待拍板、CONVENTIONS §15「模型收口与表数增长控制」提案草稿 |
| 2026-09-28 | quality-iqc-scrap-approval-policy-20260928.md | IQC 与 NCR 报废审批阈值口径：IQC 每单审批，非 IQC 按 5 件阈值分流（dev-20260928-030） |
| 2026-09-28 | quality-issues-disposition-proposal-20260928.md | 质量模块问题总处置建议：统一 IQC 处置表、数量真源、迁移路径、历史/权限/读模型和全量验收方案（待用户审核） |
| 2026-09-24 (dev-009) | scope-convergence-waste-line-dev-20260924-009.md | 报废线口径收敛：老卡去重与重定基（真源=dev-20260924-004 系列）——未完成卡 49 张扫描（真相关 15/无关 6）；3 张改写 + 9 张追加收敛块 + 13 张留痕；剪掉的段落全部有归属，无范围黑洞（dev-20260924-009） |
| 2026-09-28 | quality-module-unification-analysis-20260928.md | 质量模块统一口径与历史追溯分析建议：现状证据、数量/谱系问题、无新增表优先的分阶段治理方案 |
| 2026-09-28 | quality-taskcards-review-20260928.md | 质量模块任务卡 dev-20260928-010~016 导出与评审结论：三类风险（重复记账/第二真源/先UI后模型）、逐卡可执行性结论与 7 条待拍板项 |
| 2026-09-24 (dev-037) | event-var-registry-automation-review-dev-20260923-037.md | 事件变量注册表自动化核查（看板 2261）：真误报面 24/160（非 95%）；`sys_event_last_payload` 已在落 payload → 推荐①′（metadata 合并采集键，约 1h，无新表），累积表留二期（dev-20260923-037） |
| 2026-09-30 (dev-012) | test-data-retained-review-dev-20260930-012.md | 逐项复核清理脚本保留项：退役两张无数据无依赖的遗留表，保留仍被代码引用或尚未核对旧数据的表 |
| 2026-09-23 (dev-043) | capa-ledger-status-dev-20260921-043.md | CAPA 最小台账现状核查（看板 2105）：表/号段/菜单391/四态/页面均已实现；结案门禁只覆盖 1/3 条路径（syncFromLot、返工完成两条不查未关闭 CAPA）（dev-20260921-043） |
| 2026-09-23 (dev-029) | doc-no-batch5-prereview-dev-20260923-029.md | 单号·第 5 批执行前复核（看板 2247）：稿子+迁移可用；补 1 条 P1（采购「已收量」前缀查询→source_id，否则拆号后静默重复入库）+ 2 处明确（T 位告警是新接线 / FI 定长已修）（dev-20260923-029） |
| 2026-09-23 (dev-032) | fi-seq-fixed-width-review-dev-20260923-032.md | 完工入库单 FI 序号「2 位定长」隐患复核（看板 2250）：推理链成立 + 三处加重（持续建不出单/上层不可见/两道门禁都漏）+ 同类模式扫描（dev-20260923-032） |
| 2026-09-23 (dev-014) | event-config-page-phase2-status-dev-20260921-014.md | 事件配置页阶段2现状核查（看板 2033）：①~④ 均已实现于 8d0556f7，但 ③「最近发送」恒空（sys_notification.event_code 全 NULL）、注册表仅覆盖 8/160（dev-20260921-014） |
| 2026-09-23 (dev-021) | product-instance-module-gaps-dev-20260921-021.md | 产品实例模块残留缺陷核查（原任务 2060）：新建实例恒 6004、前端 3 端点不存在、3 查询恒 null、状态更新不落库（dev-20260921-021） |
| 2026-09-21 (dev-049) | delivery-print-triplicate-dev-20260921-049.md | 送货单打印 spec：删系统版 + 新增三联纸(241×140 针式)版式 + 纸版对齐模板（dev-20260921-049） |
| 2026-09-21 (dev-044) | restart-regression-checklist-dev-20260921-044.md | 重启后回归清单（037/038/039/040 + 1232/1312/1944/1914）：一次重启收掉整批运行态验证 |
| 2026-09-18 (dev-016) | quality-legacy-fqc-endpoint-inventory-dev-20260918-016.md | 旧质检 /production/quality 端点·页面·菜单 使用方盘点（dev-20260918-016） |
| 2026-09-18 (dev-008) | quality-rework-child-lot-dev-20260918-008.md | 质量管理：供应商返工复检子批次与批次谱系方案（dev-20260918-008） |
| 2026-09-14 (dev-013) | engineering-archive-local-segmentation-dev-20260912-013.md | 历史档案本地结构切割与工序识别实施报告（dev-20260912-013） |
| 2026-09-12 (dev-004) | tag-query-helper-dev-20260912-004.md | 标签查询辅助组件（多选/分组/计数/与或）+ 供应商管理落地（dev-20260912-004） |
| 2026-09-12 (dev-002) | engineering-archive-operability-dev-20260912-002.md | 历史档案录入可运行性修复报告 |
| 2026-09-12 (dev-001) | sample-order-split-dev-20260912-001.md | 样品订单独立表改造实施记录 |
| 2026-09-11 (dev-004) | engineering-archive-import-dev-20260911-004.md | 工程管理历史档案本地识别录入实施报告 |
| 2026-09-11 (dev-003) | film-module-linkage-dev-20260911-003.md | 菲林模块打通链路：档案可用 + 打样联动 + 网版联动（dev-20260911-003） |
| 2026-09-09 (dev-004) | production-execution-workbench-dev-20260909-004.md | 工序执行页工作台化+组件化（任务 1666，Step1 重构/Step2 主从壳） |
| 2026-09-09 (dev-004) | production-execution-all-tree-dev-20260909-004.md | 工序执行「全部工序」树化试点 + 责任汇总移除（任务 1666，dev-20260909-004） |
| 2026-09-09 (dev-002) | login-user-info-structure-dev-20260909-002.md | 登录用户信息结构理顺 + isLeader（A+B+isLeader 数据层） |
| 2026-09-08 (dev-022) | mobile-worker-complete-role-key-fix-dev-20260908-022.md | 完工按钮角色校验失效修复（任务 1633 / dev-20260908-022） |
| 2026-09-07 | purchase-to-iqc-test-report-20260907.md | 库存采购到 IQC 实测报告（2026-09-07） |
| 2026-09-03 (dev-108) | sales-return-order-items-dev-20260903-108.md | 退货来源订单明细加载断链修复方案（任务 1315 / dev-20260903-108） |
| 2026-09-03 | sales-return-product-dimension-refactor-20260903.md | 销售退货单产品化改造方案（1235 设计修正，承接 1315） |
| 2026-09-03 | quality-template-file-mapping-20260903.md | 质量记录模板文件 ↔ 台账对照表（print_template ↔ quality_template_registry） |
| 2026-09-03 | print-backlog-reanalysis-20260903.md | 打印任务全量重分析（2026-09-03，五维判定） |
| 2026-09-02 (dev-103) | print-ink-master-dev-20260902-103-104.md | 1304+1305 实施：打样印刷工序色号字典下拉 + 油墨物料体系化（dev-20260902-103/104） |
| 2026-09-02 (dev-096) | delivery-print-chain-fix-dev-20260902-096.md | 送货单打印数据链修复（任务 1297 / dev-20260902-096） |
| 2026-09-02 | system-gap-analysis-20260902.md |  |
| 2026-09-02 | summary-gap-analysis-20260902.md |  |
| 2026-09-02 | sample-print-color-ink-master-data-20260902.md | 打样印刷工序：色号/油墨录入体系化方案 |
| 2026-09-02 | sales-gap-analysis-20260902.md |  |
| 2026-09-02 | quality-gap-analysis-20260902.md |  |
| 2026-09-02 | purchase-gap-analysis-20260902.md |  |
| 2026-09-02 | production-gap-analysis-20260902.md |  |
| 2026-09-02 | product-gap-analysis-20260902.md |  |
| 2026-09-02 | inventory-gap-analysis-20260902.md |  |
| 2026-09-02 | ink-material-candidates-20260902.md | 油墨物料初筛候选（dev-20260902-104） |
| 2026-09-02 | engineering-gap-analysis-20260902.md |  |
| 2026-09-02 | biz-requirement-design-20260902.md |  |
| 2026-09-01 (dev-124) | print-center-ux-dev-20260901-1240.md | 打印中心体验：data 类跳转 + 三态标识（dev-20260901-1240） |
| 2026-09-01 (dev-124) | kanban-task-log-dev-20260901-1249.md | 看板任务状态/负责人变更加操作日志（dev-20260901-1249） |
| 2026-09-01 (dev-123) | print-log-biz-dev-20260901-1237.md | 打印日志加业务单据维度：biz_type+biz_id（dev-20260901-1237） |
| 2026-09-01 (dev-123) | order-review-print-dev-20260901-1238.md | 订单评审模板联动打印：QR-047 合同评审记录表 + QR-053 合同更改评审单（dev-20260901-1238） |
| 2026-09-01 (dev-123) | inquiry-print-dev-20260901-1239.md | 询价-样品需求单联动打印：QR-065（dev-20260901-1239） |
| 2026-09-01 (dev-086) | product-biz-attachments-dev-20260901-086.md | 产品档案聚合查看业务流转附件（dev-20260901-086） |
| 2026-09-01 (dev-085) | orphan-report-pages-dev-20260901-085.md | 孤儿报表页处理：purchase/report 挂菜单 + inventory/report 删除（任务 1270 / dev-20260901-085） |
| 2026-09-01 (dev-077) | sales-receipt-update-delete-dev-20260901-077.md | 1262 实施：销售收款单补 update/delete 端点 + 订单付款状态回写联动（dev-20260901-077） |
| 2026-09-01 (dev-076) | sales-invoice-update-dev-20260901-076.md | 1261 实施：销售发票补 update 端点（dev-20260901-076） |
| 2026-09-01 (dev-061) | purchase-invoice-page-dev-20260901-061.md | 采购发票管理前端页面（dev-20260901-061） |
| 2026-09-01 (dev-052) | sales-receipt-writeback-dev-20260901-052.md | 收款回写订单付款状态（dev-20260901-052） |
| 2026-09-01 (dev-051) | sales-delivery-write-side-dev-20260901-051.md | 发货单写入侧补齐：单据+签收+打印（dev-20260901-051） |
| 2026-09-01 | production-mobile-pda-feasibility-20260901.md |  |
| 2026-08-31 | sales-module-closure-analysis-20260831.md |  |
| 2026-08-31 | print-system-analysis-20260831.md |  |
| 2026-08-30 | enum-redundancy-audit-20260830.md |  |
| 2026-08-30 | enum-field-naming-audit-20260830.md |  |
| 2026-08-19 | production-module-inventory-20260819.md |  |
| 2026-08-01 | e2e-check-report-20260801.md | JJX ERP 跨模块链路 E2E 核对报告（DEV-459） |
| ---- | trace-timeline-design.md | TraceTimeline 流水组件 · 设计方案（统一事件流 v3：主表查询 + 前端解析 + 按需加载） |
| ---- | trace-attachment-per-operation.md | 流水附件按操作精确归属（方案二） |
| ---- | task-1286-attachment-chain-audit.md | 1286 横向核查：带证据操作的附件归属链路断点清单（dev-20260902-090） |
| ---- | task-1284-operation-preview-evidence-fix.md | 1284 修复：操作弹窗证据上传时序导致附件不进流水（dev-20260902-089） |
| ---- | system-menu-restructure.md | 系统管理模块菜单重构（dev-20260828-039） |
| ---- | stock-alert-event-chain-fix.md | dev-20260828-049 库存预警事件链路修复（事件名/注册/静默失败） |
| ---- | sample-order-change-log.md | dev-20260828-047 样品单编辑接入变更记录（流水显示"没有修改内容"修复） |
| ---- | role-menu-ancestor-backfill.md | dev-20260828-040 修复角色授权缺失祖先菜单导致子树丢失 |
| ---- | quotation-status-enum-cleanup.md | 报价单重构状态魔法值替换（20 处） |
| ---- | project-health-report.md | JJX ERP 项目全面体检报告 |
| ---- | production-wp-a-assignment-design.md |  |
| ---- | production-v1-release-fix-report.md |  |
| ---- | production-v1-fix-pack-report.md |  |
| ---- | production-v1-final-review.md |  |
| ---- | production-v1-acceptance-audit.md |  |
| ---- | production-role-config-driven.md | dev-20260828-046 生产身份改为配置驱动（role_key 名单 + 兜底不中断） |
| ---- | production-p4b-trace-read-model-api-report.md |  |
| ---- | production-p4a-trace-domain-design.md |  |
| ---- | production-p4-final-frontend-acceptance-report.md |  |
| ---- | production-p3c-implementation-report.md |  |
| ---- | production-p3b-implementation-report.md |  |
| ---- | production-p3a-quality-integration-design.md |  |
| ---- | production-p3-git-final-commit-report.md |  |
| ---- | production-p3-final-acceptance-report.md |  |
| ---- | production-p2c-implementation-report.md |  |
| ---- | production-p2b-implementation-report.md |  |
| ---- | production-p2a-workreport-domain-design.md |  |
| ---- | production-p2-git-commit-report.md |  |
| ---- | production-p2-final-acceptance-report.md |  |
| ---- | production-p1d-implementation-report.md |  |
| ---- | production-p1c-implementation-report.md |  |
| ---- | production-p1b-implementation-report.md |  |
| ---- | production-p1a-implementation-report.md |  |
| ---- | production-p1-work-package-plan.md |  |
| ---- | production-p1-final-acceptance-report.md |  |
| ---- | production-p0-p1-git-commit-report.md |  |
| ---- | production-p0-implementation-report.md |  |
| ---- | production-p0-domain-cleanup-plan.md |  |
| ---- | product-trace-design.md | 产品主线追溯（工程/产品模块接入流水）设计方案 v2 |
| ---- | orphan-menu-and-dead-file-cleanup.md | dev-20260828-042 + 043 数据清理与死文件删除 |
| ---- | operation-evidence-attachment-closeout.md | dev-20260828-048 操作弹窗凭证附件收口（送样看不到附件 + 同类 11 处） |
| ---- | module-redesign.md | ERP 模块职责与菜单分配方案 |
| ---- | menu-ancestors-backfill.md | dev-20260828-045 全库重算 sys_menu.ancestors |
| ---- | kanban-task-split-status-info-dev.md | 看板任务接口拆分：状态流转 / 内容更新（修复 @Log bizStatus 空值 500） |
| ---- | kanban-optimization-analysis.md | 任务看板/任务列表 全量查询优化分析 |
| ---- | file-upload-analysis.md | 文件管理分析（附件上传现状 + ERP 查看体验方案） |
| ---- | file-management-plan.md | 文件管理方案（附件存储 · 备份预警 · 链路显示） |
| ---- | eventbus-design.md | EventBus 事件驱动方案 |
| ---- | event-driven-plan.md | 事件驱动联动方案 |
| ---- | decision-report-production-2026-08-28.md | 生产报工剩余决策项（一页纸，2026-08-28） |
| ---- | db-audit-report.md | 数据库表结构审计报告 |
| ---- | approval-matrix.md | 生产报工审批矩阵（2026-08-28 定稿） |
| ---- | analysis-report.md | 项目分析报告 |
| dev-20260908-001 | purchase-iqc-six-fixes-dev-20260908-001.md | 采购至 IQC 六项问题修复方案与验证记录（覆盖 dev-20260908-001～006） |
| dev-20260909-004 | auto-complete-task-chain-dev-20260909-004.md | 任务完成链简化：中间节点自动完成+工序完工收口根任务+完工权限（2026-09-09） |
| dev-20260909-005 | fqc-close-loop-dev-20260909-005.md | FQC闭环方案：分批检验+不合格台账+完工防漏门（2026-09-09，待拍板） |
| dev-20260909-006 | unified-inventory-item-dev-20260909-006.md | 方案A：材料/产品档案分立、库存身份与引擎统一（任务1673） |
| dev-20260909-011 | fqc-rework-close-loop-dev-20260909-011.md | FQC分批检验、不良余量与独立返工执行闭环实施记录 |
| dev-20260909-010 | inventory-menu-reorg-dev-20260909-010.md | 采购、质量、库存与生产职责重组及独立质量管理菜单实施记录 |
| 2026-09-10 | ops-monitoring-plan-20260910.md | 运维监控方案：应用内自检 + 宿主探针 + 告警 + 处置（把「运维监控」升级为环境体检台） |
| 2026-09-07 | engineering-routing-change-log-analysis-20260907.md | 工程管理工艺路线流水缺口核查与改造方案 |
| 2026-09-06 | e2e-shortage-to-production-test-plan-20260906.md | E2E 全链路测试计划：生产缺料 → 采购 → 收货 → 检验 → 入库 → 生产领料 → 生产 |
| 2026-09-06 | purchase-receive-test-plan-20260906.md | 采购模块测试方案：覆盖 2026-09-06 收货 400 Bug 回归 |
| 2026-09-04 | delivery-print-dual-layout-dev-20260904-017.md | 送货单打印双版式：系统版 + QR-026 纸版复刻（dev-20260904-017，任务1418） |
| 2026-09-04 | log-actions-draft-20260904.md | LogActions 常量草案（阶段2 铺码清单，dev-20260904-007） |
| 2026-09-04 | log-actions-inventory-20260904.md | 全站 @Log 摸底原始清单（阶段2 铺码用，机械抽取） |
| 2026-09-04 | mobile-business-review-20260904.md | 移动端业务复查 · 第0步静态核查报告（任务 1420） |
| 2026-09-04 | mobile-test-checklist-20260904.md | 移动端（手机/PDA/扫码枪）从头测试清单（任务 1420） |
| 2026-09-04 | print-common-layer-dev-20260904-019.md | 打印公共层：组件+composable 抽取（dev-20260904-019，任务 1417 后续） |
| 2026-09-04 | production-order-print-dual-layout-dev-20260904-016.md | 生产工单打印（列表批量+列勾选，内容对齐 QR-005）双版式（dev-20260904-016，任务1417） |
| 2026-09-04 | purchase-order-print-layout-select-dev-20260904-015.md | 采购订单打印页：版式二选一（系统版 / QR-024 纸版复刻）样板（dev-20260904-015） |
| 2026-09-04 | quotation-edit-attachment-trace-dev-20260904-008.md | 报价修改流水挂附件：明细行产品文件库上传归属该次修改操作行（dev-20260904-008） |
| 2026-09-04 | task-1232-demo-data-plan-20260904.md | 任务 1232：销售主流程演示数据跑通（P0，前置验证） |
| 2026-09-04 | trace-action-dev-20260904-007.md | 流水操作动作化：@Log 加 action 中文动作文案（阶段1 框架，dev-20260904-007） |
| 2026-09-03 | dashboard-stock-perm-gate-dev-20260903-115.md | 首页仪表盘库存区块按权限收敛（dev-20260903-115） |
| 2026-09-03 | inquiry-edit-trace-fixes-dev-20260903-116.md | 询价修改流水修复：单价精度误报 + 修改上传附件归属操作行（dev-20260903-116） |
| 2026-09-03 | tags-view-refresh-redirect-dev-20260903-114.md | 标签页右键刷新路径翻倍修复（dev-20260903-114） |
| 2026-09-03 | test-plan-trial-run-20260903.md | JJX ERP 试运行前系统级测试计划 |
| 2026-09-02 | bizflow-dialog-dead-tabs-cleanup-dev-20260902-088.md | 清理询价转报价弹窗无效 Tab（任务 1278 / dev-20260902-088） |
| 2026-09-02 | change-ledger-dev-20260902-101.md | 变更记录台账（QR-071 电子台账，任务 1302 / dev-20260902-101） |
| dev-20260910-005 | order-level-completion-dev-20260910-005.md | 工单级统一收口（PC+移动端）：一级负责人一次完成整张工单全部工序，逐工序前置聚合校验、最后一道自动生成 FQC |
| 2026-09-10 | hr-module-plan-20260910.md | 人事管理模块方案（员工档案与账号解耦、可选关联；数据模型/分期/权限/导入与部门映射/待拍板项） |
| 2026-09-17 | quality-refactor-e2e-dev-20260917-014.md | 质量管理重构 E2E 验收：检验批模型（分批/余量）、不良台账（返工/让步接收/报废）、复检=更正+差额入库（dev-20260917-014） |
| dev-20260921-047 | die-data-import-dev-20260921-047.md | 刀模老台账导入：28 张表 / 19,373 行 → 去重 12,134 条入 engineering_die（状态映射、跨表合并、库位规范化修正） |
| dev-20260921-050 | frame-data-import-dev-20260921-050.md | 网版老台账导入：6 张表 7,291 个网框 → engineering_screen_frame + 4,503 个 ACTIVE 版面（空框 2,788 EMPTY） |
| dev-20260924-023 | notnull-write-path-audit-dev-20260924-023.md | NOT NULL 写入路径同类问题审计（`Field 'xxx' doesn't have a default value`）：全库 360 个候选列扫描——真雷仅 IQC 单 inspection_id 一类 5 处（迁移 220/221 已修）；另留 1 处错映射必炸接口与 2 处门禁口径冲突待拍板 |
| dev-20260926-001 | supplement-production-flow-dev-20260926-001.md | 补产主流程闭环与追溯修复：物料出库确认后按原路线生成补产任务，补报/补产分流并修正工单对账 |
| dev-20260926-002 | supplement-report-guardrails-dev-20260926-002.md | 补产报工按任务剩余额度、NCR已完成报废量及已确认发料来源校验，移除旧末道入口 |
| dev-20260926-005 | fqc-rework-child-integrity-dev-20260926-005.md | FQC返工子批纳入严格巡检口径：可判上限、工单有效合格累计和入库检查统一按批次谱系计算 |
| dev-20260926-007 | scrap-replacement-balance-dev-20260926-007.md | 报废补产候选按已完成报废量扣减有效替补申请量，两个入口统一显示剩余可补额 |
| dev-20260926-006 | work-order-reconciliation-dev-20260926-006.md | 工单数量对账区分各工序计划投入/审批产出、末道产出、补产、FQC良品和待检批次，不再将报工差额标为实物在制 |
| dev-20260926-008 | work-order-overproduction-boundary-dev-20260926-008.md | 核查普通工单计划200产出210的报工/FQC/入库限制，提出工单级授权上限与超计划库存口径供确认 |
| dev-20260926-009 | operation-overrun-reporting-dev-20260926-009.md | 正常工序超计划实绩评估：统一工单授权上限、逐工序展示、排除串行重复累计且不伪造实物WIP |

| dev-20261008-017 | orphan-product-inventory-cleanup-dev-20261008-017.md | 清理两条手动删除产品后的孤立库存身份：受控迁移245、八类引用保护、库存五项核验全零 |

| dev-20261008-019 | clean-test-data-products-dev-20261008-019.md | 测试数据清理新增显式产品选项：范围预览、保留引用阻断、关联顺序及残留核验，10项模拟测试 |

| dev-20261008-020 | merged-delivery-mock-dev-20261008-020.md | 多订单合并发货前端mock样稿：选单、数量校验、六行送货单预览及演示详情，业务后端待后续实现 |

| dev-20261008-021 | delivery-workbench-preview-dev-20261008-021.md | 发货管理上下分区前端样稿：主页面选明细、部分数量、下区演示发货记录及订单待发占用联动 |

| dev-20261009-002 | product-code-generator-layout-dev-20261009-002.md | 公共产品编码组件三行两列：底部序号与只读实时产品编码并排；验证由用户执行 |

| dev-20261009-007 | quotation-shipping-fee-dev-20261009-007.md | 报价单补运费字段（对齐销售订单口径）：sales_quotation 加 shipping_fee；total=未税小计+税额+运费、final=total-折扣；转订单透传运费；表单/详情/打印/Excel 增列 |

| dev-20261009-006 | product-price-permissions-dev-20261009-006.md | 产品模块独立价格维护：查看/修改权限、普通表单移除价格、并发保护与调价日志；验证由用户执行 |

| dev-20261009-008 | quotation-order-taxrate-fix-dev-20261009-008.md | 报价税率注解归位（@DecimalMax(100) 从税额字段挪回税率）+ 报价转订单税率去÷100 直接透传；修正「新增报价报税率不能大于100」与「转单税额差100倍」 |
