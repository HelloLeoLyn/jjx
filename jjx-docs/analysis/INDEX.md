# analysis/ 索引（历史快照层 · 122 篇 md）

> ⚠️ 本页是**历史快照清单**（实施记录 / 方案 / 分析 / 核查），按日期倒序，**不保证反映当前实现**。
> 想知道"某模块现在是什么样" → 看 `jjx-docs/current/<模块>.md`；入口导航见 `jjx-docs/README.md`。
> 2026-09-10 校对：表头原写"90 篇，2026-09-03 生成"，实际 122 篇、登记 122 条（2026-09-10 补登记 20 篇）。

生成：python3 遍历 analysis/*.md 取文件名日期 + 首个 # 标题。增删文件后重新生成。

| 日期 | 文件 | 标题 |
|---|---|---|
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
