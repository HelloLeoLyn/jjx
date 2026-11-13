# 采购计划升级实施记录

任务：dev-20261113-005；执行者 Codex；独立复核交接 OpenClaw；最终复核结论以 sys_task.description 的独立记录为准。
授权：用户“你来实施升级”；承接 dev-20261113-002 的采购数量/计价数量方案。本文为当时实施记录，不替代运行态验收。

## 背景与范围

原采购计划添加物料只有物料和数量；建议量用 Math.round 并至少补1；确认直接创建0价订单，未承接新采购单位/尺寸。原始需求、实际计划采购量混在一个 quantity。原打印页重新加载预警，无法打印当前手工计划。

本卡8个业务源：新增 PurchasePlanEntryController、PurchasePlanEntryService、PurchasePlanContextMapper、planEntry.ts；修改 plan/index.vue、plan/print.vue、OrderFormDialog.vue、PurchaseItemTable.vue。另6个测试文件及本报告/INDEX。无迁移，不增加独立计划存储/审批，不改变库存写入或订单审批收货状态机，不改物料档案/供应商关系。用户及其他会话的 backups、迁移归档、BOM在制文件不纳入提交。

## 最终行为与边界

1）添加/编辑窗口：物料编码/品名/规格、基本单位只读、计划数量和单位、条件尺寸、折合基本数量、库存/可用/在途、可选需求日期与采购说明。档案已确定宽/片长只读；每卷长度沿已有采购预估口径。手工计划没有关联需求时显示“—”，不制造需求。重复物料要求编辑现有行，不跨单位直接相加。
2）原始需求保留原小数，计划采购量独立；例：本地RM000350规格1220×100000mm/㎡，构造需求300㎡，计划3卷/每卷100m，折合366㎡；需求仍300。AUX000106的195×50000mm/㎡按卷换算；RM000105的450×225mm/PCS按片采购保持PCS，不强制㎡库存。这些数量为隔离测试输入，不是真实订单记录。
3）计划数量通过 /purchase/plan/quantity-preview 委托现有 PurchaseOrderServiceImpl.previewPricing，仍由 PurchaseQuantityPricing 唯一换算；该入口强制档案基本单位、0价0税和基本单位计价，只返回数量试算，无保存。不同规格仍不同料号；缺尺寸、非整数卷/片、不支持单位由原规则拒绝。前端不复制数量/金额公式。
4）/purchase/plan/material/{id} 返回当前档案、结构尺寸、支持单位、库存和在途。余额读 inventory_stock.total_quantity/total_reserved；在途对单一物料聚合 quantity-received_quantity，使用 ApproveStatusEnum 草稿/待审批/已批准，ReceiptStatusEnum 未收/部分收货，不计拒绝/取消/齐收，异常不返回伪零。仅本计划信息使用该查询，未改其他页面旧在途算法。
5）选择供应商后打开现有订单窗口，预填计划单位/数量/尺寸/备注/日期；读取供应商默认报价单位，不能用默认采购单位把366㎡改成366卷。计划来源模式锁住供应商和物料增删，数量/单位/价格/税率仍可核对；需要换供应商/物料时取消返回计划。多需求日期以最早日期预填订单交期，各行需求日期写入采购说明供核对。
6）订单核对取消、默认读取失败、保存失败均保留计划；只有真正保存成功才回传订单号/实际基本数量。保存期间禁止关闭，避免成功后丢失来源关联。订单号读取失败时阻止计划来源保存。来源需求/日期以行采购说明快照保存，不增加一套计划数据库。
7）仅已保存且数量覆盖原计划量和来源需求的行移除并处理预警；数量不足保留原行与已关联订单提示，不自动猜剩余计划量，须人工核对避免重复采购。保留原按物料关联预警能力，但仅传本次覆盖物料。预警关联失败显示单独重试按钮，重试不会创建第二张订单；未成功关联期间禁止再次下单。
8）换料、改数量、关闭弹窗和卸载使用请求版本校验，不回填迟到结果。现有供应商品名和物料详情保留。计划仍为前端当前会话草稿，刷新不保留，页面明确提示；未扩展持久化需求。
9）Excel带两组单位数量、需求、余额、日期、备注。打印从当前计划生成sessionStorage快照并复制到新窗口，不重新查询建议替换当前计划；无效快照明确报错，打印继续沿用留痕接口。直接访问无snapshot的旧打印地址保留采购建议入口。

## 权限与部署

新增 /purchase/plan 的3个只读接口允许 purchase:plan:view/add/confirm 任一；不授予采购单创建或供应商编辑权限。生成订单仍走原 purchase:order:add、默认读取需订单add/edit，预警关联仍受原权限约束；本卡不新增授权。正式环境仅计划查看权限的账号不能因此创建订单。
没有DDL和业务库写入，只有任务元数据；不执行服务启停。新后端接口需要部署新构建后生效。依赖原364字段，本地库已在之前由另一操作应用；本卡不执行364或归档他人迁移。

## 验证

作者定向后端6类48项（9+3+20+7+4+5）通过，包括H2真实SQL、采购保存回读和收货回归；前端6文件40项通过；npm run validate退出0，包含库存五查全0、状态枚举、文档、基线与vue-tsc。
定向命令（重型任务共用 /tmp/jjx-heavy-check.lock，MAVEN_OPTS768m/测试heap512m，MemoryMax2G、Swap0、timeout240）：
`mvn -o -q -Dtest=PurchasePlanEntryTest,PurchasePlanContextTest,PurchaseEntryQuantityTest,PurchaseOrderDualUnitTest,PurchaseEntryPersistenceTest,PurchaseReceiptDualUnitTest -DargLine=-Xmx512m test`
前端使用 MemoryMax1G、Swap0、heap768m、timeout180：
`vitest run src/views/purchase/plan/__tests__ src/views/purchase/order/__tests__ --maxWorkers=1 --no-file-parallelism`
validate使用 MemoryMax3G、Swap0、heap2560m、timeout240。
原始日志 .tmp/purchase-005/backend.log、frontend-final.log、validate.log；退出码由外层保存输出。最初编译遇到其他会话BOM在制源码/测试错误，未改他人文件；待对方修正后同命令通过。本卡测试初稿构造器/Mockito匹配器问题已修正。前端测试mock缺少request/router和物料ID上下文的初次失败已修正，未为适配测试放宽业务规则。

浏览器正式Vue组件联动通过：原始需求300㎡保留、3卷=366㎡、取消核对不下单、单价85按㎡计价未税31110、保存仅一次订单POST、预警失败后单独重试两次关联POST、真实弹出窗口收到当前计划快照、100片基本数量保持100PCS，页面异常0。截图位于 .tmp/purchase-005/plan-roll-dialog.png、plan-table.png、plan-order-confirm.png、plan-piece-dialog.png；browser-result.json 保存请求证据。浏览器发现ElementPlus InputNumber仅在挂载时设置aria-disabled，加载中挂载会残留禁用标记；计划字段和订单明细改为加载完成后挂载，已用实际输入/保存证明可操作。测试脚本最初路由误拦src/api模块、选择器命中下拉列表及动画等待问题均已修正，不代表生产逻辑缺陷。浏览器所有 /api 均拦截，数据为本地档案只读快照加真实Java服务生成的3168组试算响应；仅测试数据构造余额/预警/订单保存响应，不代表真实后端登录/鉴权/保存/收货入库闭环。

## 遗留

真实账号权限、真实采购保存→审批→收货入库闭环未验证；后端部署/启停须用户明确操作。计划持久化、计划审批、部分采购自动拆分及加工多产出不在本卡范围。
