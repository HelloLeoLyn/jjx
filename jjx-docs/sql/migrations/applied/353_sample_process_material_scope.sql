-- dev-20261110-005：订正352回填范围；仅删除非当前轮次/非工程打样中的工序同步行。
-- 工程打样中 = SampleOrderStatusEnum.ENGINEERING(3)。独立材料及历史工序JSON不变。
-- DELETE原因：352未限定轮次/状态，可能改写归档轮次；限定remark前缀并保留当前有效镜像。
DELETE b FROM sales_sample_bom b
WHERE b.remark LIKE 'sample-process-material:%'
AND NOT EXISTS (
 SELECT 1 FROM sales_sample_order s
 WHERE s.order_id=b.order_id AND s.sample_round=b.round_no AND s.sample_status=3
);
SELECT ROW_COUNT() AS removed_out_of_scope_process_material_rows;
