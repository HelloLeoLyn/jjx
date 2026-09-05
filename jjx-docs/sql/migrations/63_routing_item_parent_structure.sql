-- ============================================================
-- 63_组合工序父子结构.sql（dev-20260905-016，任务1471）
-- 2026-09-05 组合工序模型重构（逻辑2 父子行）
-- 背景：组合工序（1 道工序含多作业项）无实体化表达，各层靠 process_order/group 猜测，
--       转标准拉平、转工单按行生成 execution 导致工序数错乱（4 道变 5 条）。
-- 方案：engineering_routing_item 加 parent_id：
--       父行 = 工序（process_order 1..N，名称/大类/工时/参数），
--       子行 = 组合作业项（parent_id 挂父行，process_order 置空，group_* 退役）。
-- 存量平铺数据 parent_id 全 NULL，读取兼容（execution 生成/回显自动回退按行）。
-- ============================================================

ALTER TABLE engineering_routing_item
    ADD COLUMN parent_id BIGINT NULL AFTER group_order,
    ADD KEY idx_routing_item_parent (parent_id);

-- 子行无工序序号（父行 process_order 仍 NOT NULL 语义由代码保证 1..N 连续）
ALTER TABLE engineering_routing_item
    MODIFY COLUMN process_order INT NULL;
