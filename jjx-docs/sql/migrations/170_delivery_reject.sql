-- ============================================================================
-- 170_delivery_reject.sql
-- 任务码：dev-20260921-039（发货流程级改造：拒收回流）
--
-- 背景：发货状态 5「已拒收」全链路无写入点（枚举里有、没人写），拒收后：
--   · 订单 shipped_quantity 不减、订单还停在已发货；
--   · 出库已扣的库存不会回来（实物退回来了，账上还挂在客户端）；
--   · 没有任何记录拒收原因/时间/经办人，也没通知销售跟进。
--
-- 本次落地（自动为主、少人工填写）：
--   1) sales_delivery 补拒收登记字段（原因/时间/经办人）
--   2) 登记事件 sales.delivery.rejected（通知+派任务给销售跟进：重发/退货），收件人 销售业务操作(20)+销售审核员(21)
--   3) 库存回冲走「拒收回库」入库单（inbound_type/source_type = SALES_RETURN，自动审批+过账回成品库存）
--
-- 幂等：字段用 IF NOT EXISTS 逻辑（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）；
--       事件用 INSERT ... ON DUPLICATE KEY UPDATE（event_code 唯一键）。
-- ============================================================================

SET @db := DATABASE();

-- 1) 拒收字段
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'sales_delivery' AND column_name = 'reject_reason') = 0,
  'ALTER TABLE sales_delivery ADD COLUMN reject_reason varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''拒收原因''',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'sales_delivery' AND column_name = 'reject_time') = 0,
  'ALTER TABLE sales_delivery ADD COLUMN reject_time datetime DEFAULT NULL COMMENT ''拒收登记时间''',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'sales_delivery' AND column_name = 'reject_by') = 0,
  'ALTER TABLE sales_delivery ADD COLUMN reject_by bigint DEFAULT NULL COMMENT ''拒收登记人ID''',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'sales_delivery' AND column_name = 'reject_name') = 0,
  'ALTER TABLE sales_delivery ADD COLUMN reject_name varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''拒收登记人''',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 拒收事件登记（事件必须注册，否则通知/任务静默丢失）
INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled, target_role, title, content)
VALUES
  ('sales.delivery.rejected', '销售发货被拒收', 'sales', 'both', 'biz', 'high', 1, '[20,21]',
   '发货单【{bizNo}】被客户拒收，请跟进重发/退货',
   '① 发货单【{bizNo}】（客户 {customerName}）已登记拒收，原因：{rejectReason}。② 拒收数量已自动回冲成品库存（拒收回库单 REJECT-{bizNo}，无需仓库手工入库）。③ 订单已回到「生产中」状态，可重新发货（销售订单页 → 发货）。')
ON DUPLICATE KEY UPDATE
  event_name = VALUES(event_name), biz_module = VALUES(biz_module), event_type = VALUES(event_type),
  kanban_module = VALUES(kanban_module), priority = VALUES(priority), is_enabled = VALUES(is_enabled),
  target_role = VALUES(target_role), title = VALUES(title), content = VALUES(content);
