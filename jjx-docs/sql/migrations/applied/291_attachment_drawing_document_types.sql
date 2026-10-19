-- 291_attachment_drawing_document_types.sql
-- 任务：dev-20261019-017
-- 目的：工程图集收敛到「一级工程技术资料 + 二级资料类型」前，补齐 4 个二级类型（组装图/包装图/模具/刀模参数图）。
-- 说明：仅新增字典项，不改表结构；NOT EXISTS 幂等，可重复执行。
-- risk: low

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'ASSEMBLY_DRAWING', 'ASSEMBLY_DRAWING', '组装图',
       '工程图集二级类型（dev-20261019-017）', 33, 1,
       JSON_OBJECT('parent', 'ENGINEERING_DOC', 'sources', JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')), 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='ASSEMBLY_DRAWING' AND deleted=0);

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'PACKAGING_DRAWING', 'PACKAGING_DRAWING', '包装图',
       '工程图集二级类型（dev-20261019-017）', 34, 1,
       JSON_OBJECT('parent', 'ENGINEERING_DOC', 'sources', JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')), 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PACKAGING_DRAWING' AND deleted=0);

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'MOLD_DRAWING', 'MOLD_DRAWING', '模具',
       '工程图集二级类型（dev-20261019-017）', 35, 1,
       JSON_OBJECT('parent', 'ENGINEERING_DOC', 'sources', JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')), 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='MOLD_DRAWING' AND deleted=0);

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'DIE_PARAMETER_DRAWING', 'DIE_PARAMETER_DRAWING', '刀模参数图',
       '工程图集二级类型（dev-20261019-017）', 36, 1,
       JSON_OBJECT('parent', 'ENGINEERING_DOC', 'sources', JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')), 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DIE_PARAMETER_DRAWING' AND deleted=0);
