-- dev-20261110-004 工序材料回填到打样BOM；不改历史JSON/独立材料/库存。
-- remark 来源标记与 SampleOrderServiceImpl / useSampleBom 一致；project_ids 仍为标准工序ID。
INSERT INTO sales_sample_bom
(order_id, round_no, layer_name, material_name, specification, material_id, material_code,
 quantity, unit, base_qty, module_qty, split, project_ids, remark, create_by)
WITH candidates AS (
 SELECT p.order_id, p.round_no, p.process_id, p.std_process_id, m.*,
 ROW_NUMBER() OVER (PARTITION BY p.process_id,
   COALESCE(CONCAT('id:',m.material_id),CONCAT('name:',m.name,'|',COALESCE(m.spec,''))) ORDER BY m.ord) AS rn
 FROM sales_sample_process p
 JOIN JSON_TABLE(COALESCE(NULLIF(p.materials,''),'[]'), '$[*]' COLUMNS (
   ord FOR ORDINALITY, name VARCHAR(200) PATH '$.name', spec VARCHAR(500) PATH '$.spec',
   material_id BIGINT PATH '$.materialId', material_code VARCHAR(64) PATH '$.materialCode',
   qty DECIMAL(12,4) PATH '$.qty', unit VARCHAR(20) PATH '$.unit',
   base_qty DECIMAL(12,0) PATH '$.baseQty', module_qty DECIMAL(12,0) PATH '$.moduleQty',
   split VARCHAR(50) PATH '$.split', layer_name VARCHAR(50) PATH '$.layerName',
   owner_key VARCHAR(200) PATH '$.processItemKey', projects JSON PATH '$.projectIds'
 )) m
 WHERE TRIM(COALESCE(m.name,'')) <> ''
 AND (COALESCE(m.owner_key,'') = '' OR m.owner_key COLLATE utf8mb4_unicode_ci = JSON_UNQUOTE(JSON_EXTRACT(p.custom_process_params,'$.sampleUic.key')) COLLATE utf8mb4_unicode_ci)
 AND (COALESCE(m.owner_key,'') <> '' OR COALESCE(JSON_LENGTH(m.projects),0) = 0
      OR JSON_CONTAINS(m.projects,CAST(p.std_process_id AS JSON)))
), normalized AS (
 SELECT candidates.*,
 COALESCE(base_qty, IF(module_qty IS NULL,ROUND(COALESCE(qty,1)*10000),1)) AS b,
 COALESCE(module_qty, IF(base_qty IS NULL,10000,1)) AS m
 FROM candidates WHERE rn=1
)
SELECT c.order_id,c.round_no,COALESCE(c.layer_name,''),c.name,c.spec,c.material_id,c.material_code,
 ROUND(c.b/c.m,4),COALESCE(c.unit,'PCS'),c.b,c.m,c.split,
 IF(c.std_process_id IS NULL,JSON_ARRAY(),JSON_ARRAY(c.std_process_id)),
 CONCAT('sample-process-material:',c.process_id),'codex'
FROM normalized c
WHERE c.b>=1 AND c.m>=1 AND NOT EXISTS (
 SELECT 1 FROM sales_sample_bom b WHERE b.order_id=c.order_id AND b.round_no=c.round_no
 AND b.remark=CONCAT('sample-process-material:',c.process_id) COLLATE utf8mb4_unicode_ci
 AND (b.material_id=c.material_id OR (b.material_id IS NULL AND c.material_id IS NULL
      AND b.material_name=c.name COLLATE utf8mb4_unicode_ci AND COALESCE(b.specification,'')=COALESCE(c.spec,'') COLLATE utf8mb4_unicode_ci))
);
