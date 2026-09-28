# 质量模块任务卡 dev-20260928-010~016 导出与评审结论

> 日期：2026-09-28 ｜ 类型：任务卡导出 + 评审结论（只读分析，未改代码/数据）
> 导出人：dahuang（OpenClaw）｜ 评审：Codex（2026-09-28 15:32）
> 来源：sys_task（jjx_erp_db）｜ 起因：2026-09-28 质量/采购 E2E 真实联调发现

## 一、任务卡全文（010~016）

### dev-20260928-010 ｜ 优先级 P1 ｜ 状态 0

**标题**：【账目·一致性】四套「已处置」账分叉：报废不同步 NCR + 多处持久化 + 跨表巡检脚本

【来源】2026-09-28 E2E 联调 + history/quality-module-unification-analysis-20260928.md §4.1 核查（用户交办：把问题统一登记）。\n【实测】IN260928002-1：隔离台账剩 5、NCR 待处置 6、quality_lot.disposed_quantity=2，而实际已处置 3（报废1+退货1+返工1）→ 差 1 件。\n【根因】handleQuarantine 对 SCRAP 跳过 NCR 同步（if (!scrap)），本意等报废单审批时补记（approveIqcScrap 才调 syncIqcDisposition）；但报废单审批前端无入口（见 011）→ 缺口永久存在。同一「已处置」量在 4 处各自持久化：quality_lot.disposed_quantity、quality_ncr.disposed_quantity、inventory_iqc_quarantine.remaining_quantity、inventory_iqc_disposition_order 累计，靠写路径双写同步。\n【要求】① 给出唯一权威口径（建议：处置动作表为事实来源，其余为派生/对账，不新增汇总表）；② 报废也先登记处置事实、审批只改状态（消除 if (!scrap) 缺口）；③ 增加只读跨表一致性巡检（脚本或 SQL）：fail_quantity 与 NCR 不良总量、disposed 与有效处置动作、复检子批数量与返工量、库存流水派生结存与库存表，逐项对账。\n【白名单】jjx-server/src/main/java/com/jjx/quality/service/impl/QualityNcrServiceImpl.java；jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（handleQuarantine/approveIqcScrap）；新增巡检脚本放 scripts/。\n【验收】巡检脚本对当前数据能报出该分叉；修复后隔离/NCR/lot 三账一致；不新增汇总表；编译通过。

### dev-20260928-011 ｜ 优先级 P1 ｜ 状态 0

**标题**：【缺陷·阻塞】IQC 报废单缺前端审批入口，报废流程断头（隔离状态到不了已报废）

【来源】2026-09-28 E2E 联调实测。\n【现象】报废处置生成 IQS260928001（status=PENDING_APPROVAL）后，前端没有任何页面能审批：api/inventory/iqc.ts 已定义 listScrapOrders/approveScrap，但全站 0 处调用（grep 验证）→ 报废单永久悬空、隔离状态到不了「已报废」、NCR 也补不上记录（见 010）。\n【后端现状】接口齐备：GET /inventory/inbound/{inboundId}/iqc-scrap-orders；POST /inventory/inbound/iqc-scrap-orders/{scrapId}/approve（权限 inventory:inbound:approve 或 quality:ncr:dispose）；approveIqcScrap 通过时会同步 NCR(SCRAP) 并把隔离状态置 SCRAPPED，驳回会回滚隔离剩余并回到待处置。\n【要求】补一个前端审批入口：建议「来料不合格处置」页新增「报废单」Tab（或在「已处置」Tab 行内对 action=SCRAP 且 status=PENDING_APPROVAL 的行加「审批」按钮），支持 通过/驳回 + 意见。\n【白名单】jjx-web/src/views/inventory/iqc-quarantine/index.vue；components/（如新增弹窗）；api/inventory/iqc.ts。\n【验收】能审批通过/驳回；通过后 隔离→SCRAPPED、处置单→COMPLETED、NCR 补记 SCRAP 动作；驳回后 数量回滚并回到待处置；npm run validate 通过。

### dev-20260928-012 ｜ 优先级 P1 ｜ 状态 0

**标题**：【缺陷】复检合格的返工品没有入库出口（合格 1 件进不了库存）

【来源】2026-09-28 E2E 联调实测。\n【实测】IN260928002 返工 1 件 → 复检子批 RW-IQW260928001 复检合格 1 件（quality_lot lot5：pass_quantity=1、stored_quantity=0），但没有任何库存动作：无 inventory_transaction、入库明细 accepted_quantity=492 不含该件 → 这 1 件复检合格品进不了可用库存（货在账上没有位置）。\n【根因】复检批是独立 lot，判定合格后没有触发任何入库/放行动作；原入库单的 accepted 已在首次提交时固定为 492。\n【要求】明确复检合格品的入库路径并落地，且与 dev-20260928-009 口径一致（入库必须经「确认入库」）：二选一并说明取舍——① 并入原入库单、以「差额过账」在确认入库时补入；② 生成一张独立的待确认入库单（source_type=IQC_REWORK 或类似）。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（复检/判定/确认入库链路）；quality 域判定放行链路；前端如需入口限 IQC/处置页。\n【验收】复检合格 1 件后可在库存侧看到待确认入库动作，确认后库存 +1 且流水完整（含批次/来源/前后数量）；原批 492 不重复计入；编译通过。

### dev-20260928-013 ｜ 优先级 P2 ｜ 状态 0

**标题**：【历史与审计】质量域缺版本历史 + 复检替代/追加无字段 + 处置无操作日志 + update_time 不刷新

【来源】history/quality-module-unification-analysis-20260928.md §4.2/§4.3/§4.4 + 2026-09-28 E2E 实测。\n【问题】① 检验项保存为「删除+重插」（QualityLotServiceImpl.saveItems delete+insert），判定为覆盖式快照（applyJudgement 直接覆盖 inspected/pass/fail/result/status）→ 无判定与检验项版本历史；② 复检「替代 vs 追加」无数据字段（只有 parent_lot_id/version），靠服务代码推断；③ 处置动作 handleQuarantine 无 @Log → sys_oper_log 实测 0 条；④ 单据/检验批/隔离台账 update_time 不刷新（隔离实际 11:58 变更、update_time 停 11:40:48；入库单状态已 2/10、update_time 停 11:31:30），疑似 entity 带着旧 update_time 回写覆盖了库的 ON UPDATE CURRENT_TIMESTAMP。\n【要求】① 先冻结历史策略口径，旧数据缺失接受标记「历史不可完全恢复」，不补造；② 复检关系补 relationshipMode(REPLACE/INCREMENT)、scopeQuantity、effectiveQuantity（或由统一读模型输出）；③ 处置动作补 @Log 操作日志；④ 修 update_time 回写问题（保存时置空或显式 NOW()）。\n【白名单】jjx-server/src/main/java/com/jjx/quality/service/impl/QualityLotServiceImpl.java；jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java；jjx-server/src/main/java/com/jjx/inventory/controller/InventoryInboundController.java；相关 entity/mapper。\n【验收】处置动作在 sys_oper_log 可查；单据/台账 update_time 随变更刷新；复检替代/追加可辨识；编译通过。\n【与 dev-20260928-009 的关系（15:26 修订，避免与已修卡重叠）】③「处置动作补 @Log」已由 009 完成（commit 7fae973e，handleQuarantine 已加 @Log），本卡不再重复；另承担 009 的两项遗留：a) 清理 009 遗留的 addReleasedQuarantineStock 死代码；b) 「退货/返工/报废是否补库存流水」的口径（与 010 联动）。

### dev-20260928-014 ｜ 优先级 P2 ｜ 状态 0

**标题**：【单据字段语义】复检提交覆盖单据检验结果 + inspection_result 三义混用 + approve_status 冗余 + 明细无 update_time

【来源】2026-09-28 E2E 实测 + 代码核查。\n【问题】① 复检提交会把入库单级 inspection_result 覆盖成 PASS：submitApprove 末尾用「本次提交的行」重算 allPass，而复检提交只含复检那 1 件（全合格）→ 实测 IN260928002 明明 8 件不良却显示 PASS；② inspection_result 三义混用：NULL=待检、有值=待审或已审，语义靠猜；③ approve_status 与 order_status 并行，approve_status 永远停在 1（从不推进）→ 冗余误导；④ inventory_inbound_item 无 update_time 字段，明细变更无法追溯；⑤ 页面与日志仍写「提交入库审批/待审批」，而采购入库实际不走单据级审批（approve() 会抛「采购入库请使用单项 IQC 审核」）。\n【要求】① 复检不得覆盖单据级检验结果（保留原批结论或按全单重算）；② 明确 inspection_result 取值语义（枚举；待检用独立值）；③ 清理/停用 approve_status 或明确语义；④ 明细补 update_time（迁移）；⑤ 文案与实流程对齐。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java；inventory/domain/InventoryInboundOrder.java、InventoryInboundItem.java；迁移 SQL（如加列）；相关前端文案。\n【验收】复检后单据结果不被误改；字段语义有枚举/文档；文案一致；编译通过。

### dev-20260928-015 ｜ 优先级 P2 ｜ 状态 0

**标题**：【权限口径】IQC 隔离处置权限前后端不一致（接口 OR 语义 vs 前端仅 quality:ncr:dispose）

【来源】2026-09-28 E2E 核查（用户问：让步接收谁操作、为什么不用仓库确认）。\n【现状】后端 POST /inventory/inbound/iqc-quarantine/{quarantineId}/action 的 @SaCheckPermission 为 {"inventory:inbound:edit","quality:ncr:dispose"} 且 mode=OR；前端 iqc-quarantine 页与来料检验页的 canDispose 只认 quality:ncr:dispose。→ 持有 inventory:inbound:edit 的 INVENTORY 角色（22 全权限 / 23 业务操作）走接口仍可执行处置，与 dev-20260921-028 定的口径 B（隔离处置只给品质主管一侧）冲突。\n【要求】二选一（需用户拍板）：① 接口收紧为仅 quality:ncr:dispose（与前端一致、与口径 B 一致）；② 明确放开并同步前端 + 更新口径记录。建议 ①。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/controller/InventoryInboundController.java。\n【验收】接口与前端口径一致；越权调用被拒；编译通过。

### dev-20260928-016 ｜ 优先级 P2 ｜ 状态 0

**标题**：【前端·口径与术语】来料检验/不合格处置页展示一致性（列名/采购单号/检验批号/数量口径/命名）

【来源】2026-09-28 用户实操反馈 + quality-module-unification-analysis-20260928.md §4.5/§4.7。\n【用户已定】来料检验列表：列「入库单号」改名为「来料批次」；新增「采购单号」列；「检验批号(QLxxx)」不进列表、放明细按材料行显示。\n【其余待统一】① 数量字段（收货数量/合格/不良/剩余可处置/复检）并列且无口径标注，返工复检场景易误会；② 「谱系」按钮与抽屉标题「批次溯源」一功能两名；③ 同批数据在 IQC / 检验批工作台 / NCR / 报废 / 打印页字段名与口径不一致（多套 DTO 与前端本地计算）；④ 检验批工作台菜单只挂 FQC/OQC，来料检验批无独立入口。\n【要求】按用户口径改列名与新增列；数量字段统一带口径前缀（如 整批收货/整批不良/本批复检/已处置/剩余可处置/累计已入库）；来源展示业务单号而非内部 ID；命名统一。\n【白名单】jjx-web/src/views/inventory/iqc/（detail.vue、components/）；jjx-web/src/views/inventory/iqc-quarantine/index.vue；jjx-web/src/views/inventory/inbound/ 对应组件；后端如需补 sourceNo/lotNo：IqcPendingVO + InventoryInboundOrderMapper 的 selectIqcPendingPage。\n【验收】列表显示「来料批次 + 采购单号」，明细显示检验批号；数量带口径；「谱系/批次溯源」命名统一；npm run validate 与 npx vue-tsc --noEmit 通过。

## 二、评审结论（Codex，摘要）

方向基本正确，覆盖了当前质量模块的主要断点，但**不适合直接并行开工**。最大问题不是遗漏功能，而是「事实、审批状态、当前汇总、库存结果」没有完全分层，卡与卡之间存在口径冲突。三类风险：

1. **会重复记账**：010 与 011 对「报废何时生效」定义不一致（010 说发起即登记事实、011 说审批通过才补记 NCR）——若各按字面实现，会出现"双记一次"的新 bug。
2. **会继续增加第二真源**：010/013 对「动作表 / 汇总字段 / 复检字段」的边界不够清楚（如把 quality_ncr_action 简单当全部处置的唯一权威，忽略 IQC 域另有 inventory_iqc_disposition_order 与 inventory_iqc_scrap_order）。
3. **会先做 UI、后返工模型**：012（未定方案）、014（inspection_result 语义未定）、016（范围与"全模块统一"冲突）的前置业务语义尚未冻结。

逐卡结论：

| 任务 | 结论 | 主要问题 |
|---|---|---|
| 010 | 暂不可直接开工 | 权威表与审批生效时点未完全定义；巡检口径过粗（不能只用"动作数量之和"，需按「有效状态+动作类型+是否完成」）；fail_quantity 与 NCR 总量、复检子批与返工量的对账都需先定义边界；脚本验收过轻 |
| 011 | 依赖 010、015 | 通过/驳回可能重复记账；驳回回滚边界未定义（已生效的汇总/库存须走反向动作，不得直接覆盖）；缺防重复审批（并发/重试）；按钮权限与 015 冲突 |
| 012 | 需先选方案 | 卡内"二选一"未定，不可验收；建议方案②（生成独立待确认入库单，source_type=IQC_REWORK）；缺幂等、部分合格、复检不合格、取消/红冲、来源关联等验收；与 014 有依赖 |
| 013 | 建议拆卡 | 混了历史策略+字段+日志+死代码+库存口径，范围过大；effectiveQuantity 不应再持久化（避免第四套账）；历史策略需产出「可重算/已丢失/不可恢复」三类清单；@Log 覆盖面不足 |
| 014 | 需先定语义 | inspection_result 不能一个字段承担三义，短期 PENDING/PASS/FAIL/PARTIAL/REINSPECTION，长期拆 originalInspectionResult / currentDispositionStatus / reinspectionStatus；approve_status 先查消费者再停写、勿直接删；加 update_time 属结构变更，需补迁移编号/备份/回滚/写入路径 |
| 015 | 明确阻塞 | 必须用户拍板；且不应只改一个注解，应形成**权限矩阵**（发起处置 / 报废审批 / 让步确认入库 / 复检判定 / 退货确认 × 库存角色 / 品质角色）；建议沿口径 B：IQC 处置与报废审批归品质，确认入库归仓库 |
| 016 | 可拆分执行 | UI 改动可做，但"全模块统一"需统一读模型；建议缩范围（只做 IQC 列表/明细/隔离页/入库组件 + 文案与数量前缀），NCR/报废/打印/检验批工作台另立；注意 IN 号改叫「来料批次」有术语风险（真批次是 batch_no），建议加副标题说明 |

## 三、待拍板清单（开工前必须先定）

1. 报废「发起时是否登记申请事实」，及「哪个状态才计入有效已处置」。
2. 退货 / 返工 / 报废 是否产生库存流水（009 的遗留口径）。
3. 复检：整批替代(REPLACE) 与 局部追加(INCREMENT) 是否都支持、如何并存。
4. 复检合格品：采用「独立待确认入库单」（推荐）还是并回原单差额过账。
5. 单据级 inspection_result 的最终语义（短期兼容值 / 长期拆分三字段）。
6. IQC 处置权限：只给品质，还是放开库存侧（接口 OR 语义）。
7. 历史数据：接受「标记为不可完全恢复」，还是要求补录（禁止用当前汇总回填伪历史）。

## 四、建议执行顺序（按依赖，不按编号）

- **阶段一 · 先拍板规则**：上表 7 条。
- **阶段二 · 模型与对账**（先不动大量 UI）：010（处置事实/有效状态/巡检）、013-A（复检关系与历史策略）、014（单据结果语义）、015（权限矩阵）。
- **阶段三 · 流程闭环**：011（报废审批入口）、012（复检合格独立入库）、013-B（更新时间与日志）、013-C（死代码清理）。
- **阶段四 · 展示**：016（列名/数量口径前缀/原批↔复检批关系/批次溯源命名）。

## 五、备注

- 本文件由 sys_task 导出（2026-09-28 15:35），卡内容以库为准；本文的评审段为 Codex 意见摘要，非最终结论。
- 相关：`quality-module-unification-analysis-20260928.md`（质量模块统一口径与历史追溯分析建议）。

