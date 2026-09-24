-- ============================================================================
-- 220_iqc_quarantine_inspection_id_nullable.sql
-- 任务码：dev-20260924-022
--
-- 目的：修「IQC 不合格品隔离台账」插入报错
--       Cause: java.sql.SQLException: Field 'inspection_id' doesn't have a default value
--       （Mapper = com.jjx.inventory.mapper.InventoryIqcQuarantineMapper.insert）
--
-- 根因（三层叠加）：
--   ① 本表 inspection_id 为 bigint NOT NULL 且无默认值，库 sql_mode 含 STRICT_TRANS_TABLES；
--   ② IQC 归一（dev-20260918-026 / -027，提交 8d0556f7）后代码写死
--      quarantine.setInspectionId(null)，检验批关联改由 lot_id 承载
--      （InventoryInboundServiceImpl.createIqcQuarantine，第 435 行）；
--   ③ MyBatis-Plus 默认 insertStrategy=NOT_NULL，null 字段被剔出 INSERT 列清单
--      → 生成的 INSERT 缺 inspection_id 列 → 严格模式下直接报错、事务整单回滚。
--   影响面：仅明细 fail_quantity > 0 时建隔离行（全合格单不会走到该 SQL）；
--   触发点 updateInboundReviewStatus / confirmInbound 均在事务内，故表现为整单失败。
--
-- 本迁移（方案 A，2026-09-24 用户拍板）：
--   1) inspection_id 改为可空（保留列，不再写入；归一后关联看 lot_id）；
--   2) 删除已失效的旧唯一键 uk_iqc_quarantine_item_inspection
--      （inspection_id 恒 NULL 时 NULL 互不相等，该约束实际不再生效）；
--   3) 换成归一后的业务键 uk_iqc_quarantine_item_lot(inbound_item_id, lot_id)，
--      恢复 DB 层幂等守卫（此前只剩 Java 侧 selectCount 兜底）。
--
-- 破坏性语句原因：DROP INDEX 是本次约束替换的必要动作，仅删索引、不动数据/列；
--                 旧索引对新口径已无约束力（见 2)。
-- 前置实测（2026-09-24 15:3x，只读）：本表 0 行，无历史数据 → 改列与新唯一键均无数据风险。
-- 回滚：
--   ALTER TABLE inventory_iqc_quarantine DROP INDEX uk_iqc_quarantine_item_lot;
--   ALTER TABLE inventory_iqc_quarantine ADD UNIQUE KEY uk_iqc_quarantine_item_inspection (inbound_item_id, inspection_id);
--   ALTER TABLE inventory_iqc_quarantine MODIFY COLUMN inspection_id BIGINT NOT NULL;
--   （回滚第 3 步前需先清掉 inspection_id 为 NULL 的行，否则会失败）
-- ============================================================================

SET @db := DATABASE();

-- 0) 前置体检：把改前状态打到执行日志里（只读，便于事后核对）
SELECT
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_quarantine' AND column_name='inspection_id') AS inspection_id_nullable_before,
  (SELECT COUNT(*) FROM inventory_iqc_quarantine) AS rows_before,
  (SELECT COUNT(*) FROM (
      SELECT inbound_item_id, lot_id FROM inventory_iqc_quarantine
      GROUP BY inbound_item_id, lot_id HAVING COUNT(*) > 1) d) AS dup_item_lot_before;

-- 1) inspection_id 归一后可空（幂等：仅当当前仍为 NOT NULL 时才改）
SET @s := (SELECT IF(COUNT(*)=1,
    'ALTER TABLE inventory_iqc_quarantine MODIFY COLUMN inspection_id BIGINT NULL DEFAULT NULL COMMENT ''历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_quarantine'
    AND column_name='inspection_id' AND is_nullable='NO');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 执行备注（2026-09-24 15:4x，dev-20260924-022）：首次执行时本步骤判定失效而漏执行——
--   `information_schema.statistics` 一个索引会按列数返回多行，用 COUNT(*)=1 守卫时两列索引
--   得到 2 → 判定为"不存在"→ 跳过 DROP。已改为 COUNT(*)>0 并加 seq_in_index=1 限定（每索引
--   恰一行），随后幂等补跑，最终结构已核对：旧键已删、新键在位。
-- 2) 删旧唯一键（幂等：仅当存在时才删）——旧键对归一后口径已失效
SET @s := (SELECT IF(COUNT(*)>0,
    'ALTER TABLE inventory_iqc_quarantine DROP INDEX uk_iqc_quarantine_item_inspection',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema=@db AND table_name='inventory_iqc_quarantine'
    AND index_name='uk_iqc_quarantine_item_inspection' AND seq_in_index=1);
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 换新唯一键 (inbound_item_id, lot_id)（幂等：仅当不存在时才加）
--    若执行时有重复 (inbound_item_id, lot_id)（迁移前实测 0 行），ADD UNIQUE 会直接
--    报 Duplicate entry 并中止迁移——属预期拦截，先人工查重再重跑。
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_quarantine ADD UNIQUE KEY uk_iqc_quarantine_item_lot (inbound_item_id, lot_id)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema=@db AND table_name='inventory_iqc_quarantine'
    AND index_name='uk_iqc_quarantine_item_lot' AND seq_in_index=1);
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
