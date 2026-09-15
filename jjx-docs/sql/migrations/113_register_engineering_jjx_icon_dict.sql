-- JJX 工序图标字典（不新增业务表）
-- 任务：dev-20260912-013
-- 图标文件由前端/附件服务保存，字典项仅保存图标编码、名称和元数据。
INSERT INTO sys_dict
    (dict_code, dict_name, dict_group, remark, sort_order, is_active, tenant_id)
SELECT
    'engineering_jjx_icon', 'JJX 工序图标', 'engineering',
    'JJX 专属工序图标；字典项 remark 保存分类、来源和预览元数据', 90, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE dict_code = 'engineering_jjx_icon' AND deleted = 0
);
