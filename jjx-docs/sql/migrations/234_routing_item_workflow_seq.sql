-- risk: low
-- task: dev-20260929-027
-- 工艺路线明细的工序序号改按「组（workflow）」重排：每组（面板 / 上线 / 下线）各自从 1 开始。
-- 背景（用户 2026-09-29 拍板方案 B，见 sys_task dev-20260929-027）：
--   历史档案生成草稿的 process_order 原语义就是「每组从 1」（:720 `int order = 1` 在 workflow 循环内），
--   但唯一键 uk_routing_process_order=(routing_id, process_order) 假设「全路线序号唯一」，
--   档案含两个及以上 workflow 时第二组第 1 道必然撞键 → `Duplicate entry '1-1'`
--   （先前的"全局连续编号"只是绕开撞键，把用户要的组内序号改掉了）。
-- 措施：加列 workflow_seq（组序号，只对产出工序的组递增，1..N）
--       + 唯一键换成 (routing_id, workflow_seq, process_order)（新名 uk_routing_workflow_process）。
--   子件行（parent_id 非空）process_order 仍为 NULL，NULL 在唯一键里不参与比较 → 不受影响。
-- 存量数据：全部落 workflow_seq=1（组内序号=原 process_order），语义与撞键行为都不变。
ALTER TABLE engineering_routing_item
    ADD COLUMN workflow_seq INT NOT NULL DEFAULT 1
        COMMENT '组（workflow）序号：同组内 process_order 从 1 递增；按路线内出现顺序 1..N'
        AFTER process_category;

ALTER TABLE engineering_routing_item
    DROP INDEX uk_routing_process_order,
    ADD UNIQUE KEY uk_routing_workflow_process (routing_id, workflow_seq, process_order);
