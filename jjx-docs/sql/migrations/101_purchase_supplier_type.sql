-- ============================================================================
-- 101_purchase_supplier_type.sql
-- 供应商类型字典重构：参考物料类型 inventory_material_type（dev-20260912-003）
--   背景：供应商类型原为 M(原材料供应商)/E(设备供应商)/O(其他供应商)；实际业务按供货品类
--         分（原材料/辅助材料/油墨/成品/设备/其他）。供应商类型=主要供货类别单选，明细走标签。
--   改动：字典 purchase_supplier_type 值改为 R/A/I/F/E/O 六值（原 M/E/O 废弃）。
--   幂等：先删该字典项再插入，可重复执行；不触碰业务表数据（purchase_supplier 当前 0 行）。
--   无破坏性语句针对业务表；DELETE 仅限本字典项（配置数据）。
--   作者/任务：大黄(OpenClaw) / dev-20260912-003
-- ============================================================================

SET @db := DATABASE();

DELETE FROM sys_dict_item WHERE dict_code = 'purchase_supplier_type';

INSERT INTO sys_dict_item
    (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
VALUES
    ('purchase_supplier_type', 'MATERIAL',   'R', '原材料',   '{"enum":"MATERIAL","origin":"SupplierTypeEnum.java"}',  0, 1,
     JSON_OBJECT('enum', 'MATERIAL',   'origin', 'SupplierTypeEnum.java'), 0, 1),
    ('purchase_supplier_type', 'AUXILIARY',  'A', '辅助材料', '{"enum":"AUXILIARY","origin":"SupplierTypeEnum.java"}', 1, 1,
     JSON_OBJECT('enum', 'AUXILIARY',  'origin', 'SupplierTypeEnum.java'), 0, 1),
    ('purchase_supplier_type', 'INK',        'I', '油墨',     '{"enum":"INK","origin":"SupplierTypeEnum.java"}',       2, 1,
     JSON_OBJECT('enum', 'INK',        'origin', 'SupplierTypeEnum.java'), 0, 1),
    ('purchase_supplier_type', 'FINISHED',   'F', '成品',     '{"enum":"FINISHED","origin":"SupplierTypeEnum.java"}',  3, 1,
     JSON_OBJECT('enum', 'FINISHED',   'origin', 'SupplierTypeEnum.java'), 0, 1),
    ('purchase_supplier_type', 'EQUIPMENT',  'E', '设备',     '{"enum":"EQUIPMENT","origin":"SupplierTypeEnum.java"}', 4, 1,
     JSON_OBJECT('enum', 'EQUIPMENT',  'origin', 'SupplierTypeEnum.java'), 0, 1),
    ('purchase_supplier_type', 'OTHER',      'O', '其他',     '{"enum":"OTHER","origin":"SupplierTypeEnum.java"}',     5, 1,
     JSON_OBJECT('enum', 'OTHER',      'origin', 'SupplierTypeEnum.java'), 0, 1);
