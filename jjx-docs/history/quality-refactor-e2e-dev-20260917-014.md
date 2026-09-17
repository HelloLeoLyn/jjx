# 质量管理重构 E2E 验收（检验批模型 · 分批 · 不良台账 · 差额入库）

- 任务：dev-20260917-014（批4），关联 001~013
- 口径（2026-09-17 用户拍板）：一笔报工 = 一个检验批；末道工序由工程标记；报工审批通过即可检；
  完工不分批；成品全检可分批；处置只有 返工/让步接收/报废；让步接收按批次级；复检=更正 + 差额入库；
  模块方案 B（来料、成品两条完全独立）；报废口径 A（只入合格数，无库存扣减）；不良必须关联 工单+工序+检验批。

## 一、前置

1. 重启后端（本次后端改动全部生效：005/006/007/008/012）。
2. 刷新前端（菜单：质量管理 → 来料检验（检验批）/ 成品检验（检验批）/ 不良台账 / 抽样方案）。
3. 数据已按 013（迁移 124）重置：该批次成品库存归零、工单成品归零、旧 FQC/IQC 单据清空、
   3 张历史完工入库单作废、抽样方案含 4 条示例（AQL 1.0 / 检验水平 II）。
   备份：`jjx-backups/jjx_erp_db_backup_20260917-1153_before-124.sql`（md5 aa5d91c43535c1c22221b41ebe64b8e2）。

## 二、链 1：来料检验（按原方案，不改）

> 2026-09-17 定案：来料是"抽检判定整批"，不套检验批模型 —— 检验批入口已隐藏（迁移 127），
> 来料仍走原页面/原流程/原隔离处置台账。本节仅确认"回退干净 + 防错校验仍在"。

1. 旧页面（质量管理 → 来料检验）做一次检验提交：流程与之前完全一致（录入 → 提交 → 审核 → 隔离处置）。
2. 期望：采购收货单状态推进入库流程；允收入库数量上限 = 良品数量（不良不得计入接收，dev-20260916-009 校验）。
3. 期望：质量管理菜单下**不再有**「来料检验（检验批）」；`SELECT COUNT(*) FROM quality_lot WHERE lot_type='IQC'` = 0。
4. QR-037 进料检验报告仍走旧打印页（`views/production/quality-print/iqc-report.vue`）。

## 三、链 2：成品检验（报工审批建批 · 分批 · 复检不增量）

1. 新工单 → 工序派工 → 报工 → **报工审批通过**。
2. 期望：若该工序为末道（工程标记；未标记时按"顺序最大"兜底并打日志），自动生成 FQC 检验批，
   数量 = 报工合格 + 不良。
3. 分批：多笔报工 → 多个检验批；列表"已检/待检"逐批推进。
4. 判定：在成品检验（检验批）页录入并判定（合格+不良=检验数量）；合格部分按**差额**写入完工入库单。
5. 复检：点【复检】→ 生成同批新版本；再次判定后**入库总量不变（只调差额）**，
   并产生 ADJUST 流水（带 lotId，remark 可追）。
6. 期望上限守卫：`应入 ≤ 工单计划数量`；超限报错（不会出现复检无限入库）。

## 四、链 3：不良处置（返工 / 让步接收 / 报废）

1. 返工：生成返工工序，完工再检合格 → 正常成品入库（闭环）。
2. 让步接收：必须先勾选"客户已确认" → 不良数量转良品库存并打特采标记（ADJUST 流水带 lotId/ncrId）。
3. 报废：仅登记台账（口径 A：不良品从未进入良品库存，无扣减）；台账结案。
4. 期望：台账 不良数量 = 各处置数量之和；处置完成后 NCR 状态 = 已结。

## 五、账实恒等式校验（可复跑）

```sql
-- 1) 检验批：批量 = 已检 + 待检；已检 = 合格 + 不良
SELECT lot_id, lot_no, lot_type, lot_quantity, inspected_quantity,
       (lot_quantity - inspected_quantity) AS remaining,
       pass_quantity, fail_quantity,
       (pass_quantity + fail_quantity)     AS inspected_check
FROM quality_lot ORDER BY lot_id;

-- 2) 不良台账：不良 = 已处置 + 待处置（不得为负）
SELECT ncr_id, ncr_no, defect_quantity, disposed_quantity,
       (defect_quantity - disposed_quantity) AS pending
FROM quality_ncr ORDER BY ncr_id;

-- 3) 入库上限：工单成品入库累计 ≤ 工单计划数量；复检后应保持不变
SELECT o.order_no, o.planned_quantity, o.finished_quantity,
       COALESCE(SUM(i.quantity), 0) AS inbound_total
FROM production_order o
LEFT JOIN inventory_inbound_order ord
       ON ord.source_type = 'PRODUCTION' AND ord.source_id = o.order_id AND ord.order_status <> 9
LEFT JOIN inventory_inbound_item i ON i.inbound_id = ord.inbound_id
GROUP BY o.order_id ORDER BY o.order_id;

-- 4) 库存与流水差额一致（同批次：初始入库 + 调整 = 当前库存）
SELECT t.batch_no, SUM(t.quantity) AS ledger_total,
       (SELECT quantity FROM inventory_stock_item s WHERE s.batch_no = t.batch_no LIMIT 1) AS stock_quantity
FROM inventory_transaction t
WHERE t.batch_no LIKE 'BATCH-%' GROUP BY t.batch_no;

-- 5) 库存变动可追检验批/不良台账（新流程产生的调整必须有来源）
SELECT transaction_id, transaction_type, quantity, lot_id, ncr_id, remark
FROM inventory_transaction WHERE lot_id IS NOT NULL OR ncr_id IS NOT NULL ORDER BY transaction_id;
```

## 六、遗留（本批未做，另立任务）

1. 旧入口退役：库存→来料检验（330）、生产→生产质检（264）、不合格品处置（333）与新页面的切换/隐藏（011 后半段）。
2. 旧完工质检路径 `createFqcForExecution` 下线（007/008 收口后）。
3. 来料侧"分批检验"的界面入口（拆批/合并）与抽检数量手改。
4. SN / 产品实例表（逐件二维码追溯）仅保留扩展位，未建表（用户明确后续再做）。
5. 报表维度（合格率/不良率按产品、工单、供应商、客户）与质量成本（报废金额）未做。
