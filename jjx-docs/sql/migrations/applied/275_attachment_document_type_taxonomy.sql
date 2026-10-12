-- 275_attachment_document_type_taxonomy.sql
-- 任务码：dev-20261012-040（ATT-DICT-01）｜公共附件资料类型字典定稿
-- 口径（用户 2026-10-12 已定）：dict_code 沿用 attachment_category(一级)/attachment_document_type(二级)；编码大写；
--   二级 ext_data={'parent':一级码,'sources':[按一级语义]}；老项 general/general_document 停用(is_active=0)，不物理删除。
-- 幂等：INSERT 走 WHERE NOT EXISTS；UPDATE 幂等；同一 SQL 可重复执行不报错。
-- 范围：仅 sys_dict / sys_dict_item 种子；不写业务数据、不改表结构。风险：low。
-- 执行通道：bash scripts/db-migrate.sh 275_attachment_document_type_taxonomy.sql --yes --task dev-20261012-040 --backup <手工全库备份.sql>
-- 编号说明：任务书原写 274，但 274 已被 dev-20261012-039(bom_item_common_material, 2026-10-12 已应用)占用，故取下一号 275。
-- !!! 关键适配：sys_dict_item 使用 utf8mb4_unicode_ci（大小写不敏感），'GENERAL'='general' 为真，唯一键 (dict_code,item_key,tenant_id,deleted) 同属不敏感；
--     故旧一级键 general 会挡住新键 GENERAL。处理：先把旧 general 改名为 general_legacy 并停用（保留、不删除）释放键位，再插入规范化 GENERAL。
--     旧二级 general_document 无冲突，直接停用。BINARY 限定确保只命中旧小写键、可重复执行。

-- ============ 执行前核对（人工，供参考） ============
-- SELECT item_key,label,is_active FROM sys_dict_item WHERE dict_code IN ('attachment_category','attachment_document_type') AND deleted=0 ORDER BY dict_code,sort_order;

-- 0) 字典头兜底（271 已建，幂等确保存在）
INSERT INTO sys_dict(dict_code,dict_name,dict_group,remark,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','公共附件一级分类','system','公共附件分类；停用保留历史可读',90,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_code='attachment_category');
INSERT INTO sys_dict(dict_code,dict_name,dict_group,remark,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','公共附件二级类型','system','ext_data.parent指向一级item_key；sources为允许来源数组',91,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_code='attachment_document_type');

-- 1) 旧一级 general 让位（utf8mb4_unicode_ci 下与 GENERAL 同键）：改名停用、保留不删除
UPDATE sys_dict_item SET item_key='general_legacy', is_active=0
 WHERE dict_code='attachment_category' AND BINARY item_key='general';

-- 2) 一级分类 5 条（attachment_category；sort_order 按用户清单顺序 1..5）
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','CUSTOMER_DOC','CUSTOMER_DOC','客户资料',1,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='CUSTOMER_DOC');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','ENGINEERING_DOC','ENGINEERING_DOC','工程技术资料',2,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='ENGINEERING_DOC');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','BUSINESS_DOC','BUSINESS_DOC','商务资料',3,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='BUSINESS_DOC');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','QUALITY_DOC','QUALITY_DOC','质量资料',4,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='QUALITY_DOC');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','GENERAL','GENERAL','通用资料',5,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='GENERAL');

-- 3) 二级资料类型 24 条（attachment_document_type；ext_data.parent + sources 按一级语义；sort_order 1..24）
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CUSTOMER_DRAWING','CUSTOMER_DRAWING','客户图纸',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),1,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CUSTOMER_DRAWING');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CUSTOMER_APPROVAL','CUSTOMER_APPROVAL','客户承认书',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),2,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CUSTOMER_APPROVAL');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','PRODUCT_SPECIFICATION','PRODUCT_SPECIFICATION','产品规格书',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),3,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PRODUCT_SPECIFICATION');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','PRODUCT_IMAGE','PRODUCT_IMAGE','产品照片',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),4,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PRODUCT_IMAGE');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CUSTOMER_REQUIREMENT','CUSTOMER_REQUIREMENT','客户技术要求',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),5,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CUSTOMER_REQUIREMENT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CUSTOMER_SAMPLE','CUSTOMER_SAMPLE','客户样品资料',
JSON_OBJECT('parent','CUSTOMER_DOC','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample')),6,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CUSTOMER_SAMPLE');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','ENGINEERING_DRAWING','ENGINEERING_DRAWING','工程图',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),7,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='ENGINEERING_DRAWING');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','BOM','BOM','BOM',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),8,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='BOM');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','PROCESS_ROUTE','PROCESS_ROUTE','工艺路线',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),9,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PROCESS_ROUTE');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','WORK_INSTRUCTION','WORK_INSTRUCTION','作业指导书',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),10,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='WORK_INSTRUCTION');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','DIE_DRAWING','DIE_DRAWING','刀模图',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),11,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DIE_DRAWING');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','DIE_CUTTING_PARAMETER','DIE_CUTTING_PARAMETER','冲型参数',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),12,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DIE_CUTTING_PARAMETER');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','PRINTING_PROCESS','PRINTING_PROCESS','印刷工艺',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),13,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PRINTING_PROCESS');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','INK_MIXING_RECORD','INK_MIXING_RECORD','油墨调和记录',
JSON_OBJECT('parent','ENGINEERING_DOC','sources',JSON_ARRAY('product','production_order')),14,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='INK_MIXING_RECORD');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','SALES_INQUIRY_ATTACHMENT','SALES_INQUIRY_ATTACHMENT','询价附件',
JSON_OBJECT('parent','BUSINESS_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','purchase_order')),15,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='SALES_INQUIRY_ATTACHMENT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','SALES_QUOTATION_ATTACHMENT','SALES_QUOTATION_ATTACHMENT','报价附件',
JSON_OBJECT('parent','BUSINESS_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','purchase_order')),16,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='SALES_QUOTATION_ATTACHMENT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CONTRACT','CONTRACT','合同',
JSON_OBJECT('parent','BUSINESS_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','purchase_order')),17,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CONTRACT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','ORDER_CONFIRMATION','ORDER_CONFIRMATION','订单确认',
JSON_OBJECT('parent','BUSINESS_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','purchase_order')),18,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='ORDER_CONFIRMATION');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','INSPECTION_REPORT','INSPECTION_REPORT','检验报告',
JSON_OBJECT('parent','QUALITY_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','sample','production_order','material')),19,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='INSPECTION_REPORT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','TEST_RECORD','TEST_RECORD','检测记录',
JSON_OBJECT('parent','QUALITY_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','sample','production_order','material')),20,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='TEST_RECORD');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','DEFECT_PHOTO','DEFECT_PHOTO','不良照片',
JSON_OBJECT('parent','QUALITY_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','sample','production_order','material')),21,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DEFECT_PHOTO');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','ABNORMAL_REPORT','ABNORMAL_REPORT','异常报告',
JSON_OBJECT('parent','QUALITY_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','sample','production_order','material')),22,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='ABNORMAL_REPORT');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','CERTIFICATE_OF_CONFORMITY','CERTIFICATE_OF_CONFORMITY','合格证明',
JSON_OBJECT('parent','QUALITY_DOC','sources',JSON_ARRAY('inquiry','quotation','sales_order','sample','production_order','material')),23,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CERTIFICATE_OF_CONFORMITY');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','OTHER','OTHER','其他',
JSON_OBJECT('parent','GENERAL','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')),24,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='OTHER');

-- 4) 旧二级 general_document 停用（保留历史可读，不删除）
UPDATE sys_dict_item SET is_active=0
 WHERE dict_code='attachment_document_type' AND BINARY item_key='general_document';

-- ============ 执行后核对（只读） ============
-- 期望：attachment_category active=5（CUSTOMER_DOC/ENGINEERING_DOC/BUSINESS_DOC/QUALITY_DOC/GENERAL）；attachment_document_type active=24；
--       旧项 general_legacy / general_document 均 is_active=0
SELECT dict_code, SUM(is_active) AS active_cnt, COUNT(*) AS total FROM sys_dict_item
WHERE dict_code IN ('attachment_category','attachment_document_type') AND deleted=0 GROUP BY dict_code;
SELECT dict_code,item_key,label,is_active FROM sys_dict_item WHERE dict_code IN ('attachment_category','attachment_document_type') AND deleted=0 ORDER BY dict_code,sort_order,item_id;
