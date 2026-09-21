-- ============================================================================
-- 138_unify_event_templates.sql
-- 任务码：dev-20260918-009
--        （原拟用 dev-20260918-008，该码已被并发 codex 会话占用，改号登记为 009；
--          本迁移内容与执行时间不变，仅任务码更正。）
--        （编号原为 136，与并发会话的 136_iqc_rework_child_batch_lineage.sql 撞号，
--          按公约「后建者改号」改为 138，并同步 sys_config.ops.schema.applied；内容未变。）
--
-- 目的：给「采购收货 → 来料待检 → 检验通过 → 确认入库」这条链上的 6 条事件
--       套用统一的文案模板（通知与任务共用同一份 title/content）。
--
-- 通用模板（本次定稿，后续新增事件照此写，要改就在 sys_event_config 里改）：
--   标题 = {业务对象}【{业务单号}】{状态变化}[，要做的动作]
--   正文 = 三段，顺序固定：
--          ① 发生了什么（单据 + 数量/供应商等关键信息）
--          ② 为什么找你（不做会影响什么）
--          ③ 你要做什么 + 从哪进（页面路径；有「去处理」按钮可直达）
--   变量只能用事件 payload 里真实存在的键，否则模板里的 {x} 会原样显示。
--
-- ⚠ 已知限制（本次未解决，需改代码才能补）：
--   purchase.received / inventory.inbound.created_from_purchase / inventory.inbound.confirmed
--   这三个事件由 @Event 注解发布，payload 只有内部 id（orderId / purchaseOrderId / inboundId），
--   没有业务单号，所以标题只能用「内部编号」；要显示 PO… 单号必须把单号塞进事件 payload。
--   （quality.iqc.* 三个事件是手写 payload，含 inboundNo / sourceNo / supplierName，已带单号。）
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

-- 1) 采购到货（通知）—— 收件人 [26, 23, 33]
UPDATE sys_event_config
   SET title = '采购到货：请安排来料检验与入库',
       content = '① 采购单（内部编号 {bizId}）已完成收货，物料已到厂。② 未完成来料检验就不能入库过账，会卡住后续生产领料。③ 请到「库存管理 → 入库管理」找到该单并提交来料检验；点本通知「去处理」可直达。'
 WHERE event_code = 'purchase.received';

-- 2) 采购生成入库单（通知 + 待办）—— 收件人 [23, 33]
UPDATE sys_event_config
   SET title = '采购已生成入库单：请收货并提交来料检验',
       content = '① 采购单（内部编号 {bizId}）已生成入库单（内部编号 {returnValue}），到货已登记。② 入库单必须先完成来料检验才能过账，未过账的物料不可领用。③ 请到「库存管理 → 入库管理」核对该单并提交检验；点「去处理」直达。'
 WHERE event_code = 'inventory.inbound.created_from_purchase';

-- 3) 来料待检验（通知 + 待办）—— 收件人 [34, 33]
UPDATE sys_event_config
   SET title = '入库单【{inboundNo}】待来料检验',
       content = '① 采购单 {sourceNo} 已收货并生成入库单【{inboundNo}】（供应商 {supplierName}）。② 未录入检验结果就不能入库过账，会卡住生产领料。③ 请到「库存管理 → IQC 来料检验」录入检验结果并提交；点「去处理」直达该入库单。'
 WHERE event_code = 'quality.iqc.submitted';

-- 4) 来料检验通过-待确认入库（通知 + 待办）—— 收件人 [22, 34]
UPDATE sys_event_config
   SET title = '入库单【{inboundNo}】来料检验已通过，请确认入库',
       content = '① 入库单【{inboundNo}】（采购单 {sourceNo}，供应商 {supplierName}）来料检验已全部通过。② 尚未入库过账，物料还不可领用。③ 请到「库存管理 → 入库管理」执行确认入库完成过账；点「去处理」直达。'
 WHERE event_code = 'quality.iqc.approved';

-- 5) 来料检验不合格-隔离待处置（只通知）—— 收件人 [34]
UPDATE sys_event_config
   SET title = '入库单【{inboundNo}】来料检验有不合格，已隔离 {quarantineCount} 项',
       content = '① 入库单【{inboundNo}】（采购单 {sourceNo}）存在来料检验不合格品，已生成隔离台账 {quarantineCount} 项。② 隔离品不可领用、不可付款，需先处置。③ 请到「库存管理 → IQC 来料检验」按行执行隔离处置（退货 / 返工 / 让步接收 / 报废）。'
 WHERE event_code = 'quality.iqc.quarantine.created';

-- 6) 入库单确认入库（通知 + 待办）—— 收件人 [23, 28, 29, 30]
--    注意：该事件 payload 无业务单号，只有内部 id；操作人是 payload 里的 operatorName。
UPDATE sys_event_config
   SET title = '入库单已过账，库存已可用（入库单内部编号 {bizId}）',
       content = '① 入库单（内部编号 {bizId}）已完成入库过账，操作人 {operatorName}。② 物料已可领用，生产可据此安排领料/开工；仓管无需再处理。③ 生产请到「生产管理 → 生产订单」安排领料；库存请到「库存管理 → 入库管理」查看；点「去处理」直达。'
 WHERE event_code = 'inventory.inbound.confirmed';

-- ============================================================================
-- 执行后自检（应为 6 条新模板）：
--   SELECT event_code, title, content FROM sys_event_config
--    WHERE event_code IN ('purchase.received','inventory.inbound.created_from_purchase',
--          'quality.iqc.submitted','quality.iqc.approved','quality.iqc.quarantine.created',
--          'inventory.inbound.confirmed') ORDER BY event_code;
-- ============================================================================
