-- 工艺路线明细将作业说明与业务描述分离（dev-20260912-013）
ALTER TABLE engineering_routing_item
    ADD COLUMN work_instruction varchar(500) NULL AFTER description;

-- 历史转移逻辑写入的来源标记不是作业说明，精确清理，不触碰其他描述。
UPDATE engineering_routing_item
SET description = NULL
WHERE description LIKE '打样传承:%';
