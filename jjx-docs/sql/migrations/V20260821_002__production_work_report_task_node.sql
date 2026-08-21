-- =====================================================
-- V20260821_002：production_work_report 接入 Task Tree（task_node_id）
-- 定位：新报工必须绑定 TaskNode（Task Tree 统一模型，替代旧 DispatchNode 责任锚点）
-- 决策：
--  - 新增 task_node_id（NULL 允许：历史报工无 taskNodeId；P2 起新报工由 Service 强制绑定并写入）
--  - dispatch_id / dispatch_node_id 旧列本轮保留不删除（历史数据可追溯），仅放宽 NOT NULL：
--    新报工只写 task_node_id，不再向旧列写入；旧列删除放数据库清理阶段统一处理
--  - 旧列关联索引 idx_dispatch_node 保留（历史查询仍可用）
-- =====================================================

ALTER TABLE `production_work_report`
  ADD COLUMN `task_node_id` BIGINT NULL COMMENT '任务树节点ID(Task Tree 报工锚点；P2 起新报工必须绑定)'
  AFTER `execution_id`;

ALTER TABLE `production_work_report`
  MODIFY COLUMN `dispatch_node_id` BIGINT NULL COMMENT '旧派工责任节点ID(历史保留，新报工不再写入；数据库清理阶段删除)';

ALTER TABLE `production_work_report`
  ADD KEY `idx_task_node` (`task_node_id`);
