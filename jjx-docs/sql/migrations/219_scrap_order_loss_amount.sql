-- risk: low
-- dev-20260924-010（报废线五期）：成品报废单落「损失金额」（材料损失 / 工时损失 / 合计 + 口径快照）。
-- Tables: quality_scrap_order
-- 口径（业务逻辑）：
--   · 材料损失 = 报废数量 × 单位材料标准成本；单位材料标准成本 = Σ(BOM 单耗 ×(1+损耗率) × 材料单价)
--   · 工时损失 = 报废数量 × 单位工时标准成本；单位工时标准成本 = Σ(工序标准工时 × 该工序标准工价)
--   · 材料单价取值链（逐级回退、**来源记入 loss_basis**）：①批次入库单价 → ②库存批次成本 → ③最近采购价 → ④物料主数据(成本价/标准价) → ⑤无价（计 0 并在 basis 列「无价物料 N 项」）
--   · 工价/工时缺失 → 工时损失计 0，并在 basis 列「工时缺失 N 项」（不静默、不瞎估）
--   · 金额是**单据快照**（单据不可改）；报废被受控撤销时回冲（单据 VOID + 台账金额冲减）
-- 幂等：列存在性判定的动态 ALTER。

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_scrap_order ADD COLUMN loss_material decimal(14,2) NULL COMMENT ''材料损失金额（dev-20260924-010）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_scrap_order' AND column_name = 'loss_material');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_scrap_order ADD COLUMN loss_labor decimal(14,2) NULL COMMENT ''工时损失金额（dev-20260924-010）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_scrap_order' AND column_name = 'loss_labor');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_scrap_order ADD COLUMN loss_total decimal(14,2) NULL COMMENT ''损失合计 = 材料 + 工时（dev-20260924-010）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_scrap_order' AND column_name = 'loss_total');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_scrap_order ADD COLUMN loss_basis varchar(500) NULL COMMENT ''损失口径快照：单价来源/工价/缺失项（dev-20260924-010）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_scrap_order' AND column_name = 'loss_basis');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
