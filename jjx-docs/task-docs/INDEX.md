# task-docs/ 索引（任务四件套）

> 门禁 R5：本目录每个 `.md`（`README.md` / `INDEX.md` 除外）都必须登记在下面表格里。
> 命名：`dev-YYYYMMDD-NNN-<类型>.md`（analysis / plan / completion / acceptance）。

| 任务码 | 文件 | 类型 | 标题 |
|---|---|---|---|
| dev-20261013-047 | dev-20261013-047-completion.md | completion | 未保存草稿的 TEMP 附件清理（口径 A：到期无引用附件标记删除进回收站，物理删除仍禁用；文件管理页新增清理入口） |
| dev-20261014-021 | dev-20261014-021-plan.md | plan | 任务执行器方案：固定流程脚本化（inspect/start/check/commit/record/push 复用现有脚本，只回摘要与失败项），业务方案/事务边界/审查仍由 agent 判断 |
| dev-20261013-046 | dev-20261013-046-plan.md | plan | 产品详情「业务流转附件」展示口径：按字段过滤=只显"算本产品"的行（product_id命中 或 product_id空且product_code=本产品编码）；整单级/他产品行不显示 |
| dev-20261014-019 | dev-20261014-019-plan.md | plan | 后端：附件/产品上传支持图纸元数据（图种/编号/名称/版本/原稿-打印件），解锁 016 第3项工程图纸迁公共组件 |
| dev-20261014-031 | dev-20261014-031-plan.md | plan | 来源体系治理：扩展点收敛 + 两套权限定判据 + task 权限 + 来源清单同源 |
| dev-20261014-030 | dev-20261014-030-plan.md | plan | 046 回炉：业务流转附件按字段过滤（删三层判定与整单级兜底） |
| dev-20261014-029 | dev-20261014-029-plan.md | plan | 实现 019 方案B：公共上传/建引用支持图纸元数据（图种/编号/名称/版本/原稿-打印件），解锁 016 第 3 项 |
| dev-20261014-028 | dev-20261014-028-plan.md | plan | 附件双写收口剩余项：报价/样品同类点改传引用 ID + 授权证据老字段兜底退役（承接 3132 分析） |
| dev-20261014-027 | dev-20261014-027-plan.md | plan | 工序自定义图标上传接入公共附件（先定「业务附件 vs 资产」，再谈实现） |
| dev-20261014-026 | dev-20261014-026-plan.md | plan | 质量模板上传接入公共附件（新增来源 quality_template；先定 file_id 与新引用的口径） |
| dev-20261014-025 | dev-20261014-025-plan.md | plan | 质量档案上传接入公共附件（新增来源 quality_archive；上传/查看/已回传判定三处一起改） |
| dev-20261014-024 | dev-20261014-024-plan.md | plan | 操作确认弹窗证据附件接入公共附件（先盘调用方来源，再定范围） |
| dev-20261014-023 | dev-20261014-023-plan.md | plan | 销售送货回签件（两入口）接入公共附件（新增来源 sales_delivery，两入口一处实现） |
| dev-20261014-022 | dev-20261014-022-plan.md | plan | 采购发票文件接入公共附件（新增来源 purchase_invoice + 临时上传/校验落库/按来源查）——照收货票据 018 同款，交接包 |
| dev-20261014-020 | dev-20261014-020-plan.md | plan | 客供/产品文件库口径定稿（并集 + 分层标注：本产品 / 来源整单）并落地 016 第 1/2 项 |
| dev-20261014-017 | dev-20261014-017-plan.md | plan | 采购订单详情凭证改走公共附件组件（订单凭证 + 付款凭证读 + 删除改解绑）——修「付款单传的凭证在订单详情看不到」 |
| dev-20261014-016 | dev-20261014-016-plan.md | plan | 产品维度上传改走公共附件组件（客供稿 / 产品文件库 / 工程图纸 / 菲林）——交接包，供他人执行 |
| dev-20261014-011 | dev-20261014-011-plan.md | feature | 样品单「新建产品」入口：打样产品选择器接产品编码器现场生成编码、随单自动建档；抽公共编码弹窗组件（QuotationCodeGenDialog 改薄封装） |
| dev-20261014-010 | dev-20261014-010-plan.md | plan | 迁移文件缺失盘点 + 门禁指向修复（账本 208 号、仓内仅 256~277；check-collation.sh 指向已删的 193） |
| dev-20261014-009 | dev-20261014-009-analysis.md | analysis | 附件中心组件治理分析：AttachmentRefList（506 行/4 kind）与 AttachmentCenter（4 模式）与 AttachmentDocList 两张表怎么收（只出分析） |
| dev-20261014-008 | dev-20261014-008-plan.md | plan | 老通道面板残局收尾：AttachmentPanel 两处调用方换 AttachmentDocList + 删 TraceAttachmentPanel |
| dev-20261014-006 | dev-20261014-006-plan.md | plan | 附件表 collation 订正（sys_attachment_ref/order_shortage_ledger → utf8mb4_unicode_ci，迁移 278）+ 防复发三层规则与门禁 |
| dev-20261014-003 | dev-20261014-003-plan.md | plan | 只读文档公共组件 AttachmentDocList：参数即查询条件、只读可下载（默认10列从字段清单生成；含 refs 按来源单号解析） |
| dev-20261014-001 | dev-20261014-001-plan.md | plan | 附件批量改名 P 段按产品编码解析回填（后端 list() 只读回填 productId，修「INQ-1-P0-…」；存量不动） |
| dev-20261013-043 | dev-20261013-043-plan.md | plan | S5「明细行上传附件」方案（已定案：方案2 = 引用表加内部列 product_code；报价/询价明细行「上传附件」按钮，不建档、不多余数据行） |
| dev-20261013-042 | dev-20261013-042-plan.md | plan | M5 附件存储统一到 refs（跨 agent 交接版）：老通道退役 + 新增 purchase_payment 来源 + 老路径退役 + 多产品选产品（P1—P6 已拍板；合并 028/027） |
| dev-20261013-040 | dev-20261013-040-plan.md | plan | M3 附件前端组件收尾：字段清单消费四段 + 只读列表抽取 + 预览/格式化去重 + 待绑定显示上传人（合并 016/035/026/025） |
| dev-20261012-041 | dev-20261012-041-acceptance.md | acceptance | ATT-DICT-02 收口验收（Redis 缓存清理 + 只读复核三层一致） |
| dev-20261013-010 | dev-20261013-010-completion.md | completion | 发布快照 P2：完整内容原子固化与引用资源保留（含 DOCSET_JSON 契约） |
| dev-20261013-011 | dev-20261013-011-completion.md | completion | 发布快照 P3：预览打印统一读取与完整性拦截 |

| dev-20261013-029 | dev-20261013-029-completion.md | completion | 作业规范发行信息口径对齐（页面发行单位/日期与快照契约） |

| dev-20261013-031 | dev-20261013-031-completion.md | completion | 作业规范发行信息：发行单位/发行日期录入入口 |
