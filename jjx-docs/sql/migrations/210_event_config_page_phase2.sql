-- 210_event_config_page_phase2.sql
-- 任务：dev-20260921-014（事件配置页阶段2 收口，看板 2033）
-- 背景：阶段2 的 ①~④ 主体已在 8d0556f7 落地，但「最近一次实际渲染」实际不工作：
--       事件码历史上被写进了 sys_notification.biz_type，event_code 列一直是 NULL，
--       而配置页 metadata 端点按 event_code 查询 → 恒空。
-- 内容：
--   ① 新建 sys_event_last_payload —— 存每个事件最近一次真实 payload，供配置页「试渲染」按真实数据预览
--      （写入方：LocalEventPublisher.fire() upsert；读取方：GET /system/event-config/{eventCode}/metadata）
--   ② 回填 sys_notification.event_code（仅当 biz_type 确实是已配置的事件码，保守回填）
-- 幂等：CREATE IF NOT EXISTS + 条件 UPDATE。
CREATE TABLE IF NOT EXISTS `sys_event_last_payload` (
  `event_code` varchar(100) NOT NULL COMMENT '事件码',
  `payload` json DEFAULT NULL COMMENT '最近一次事件 payload（JSON）',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务对象 ID',
  `update_time` datetime DEFAULT NULL COMMENT '最近一次触发时间',
  PRIMARY KEY (`event_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件最近一次 payload（事件配置页试渲染用）';

UPDATE sys_notification n
   SET n.event_code = n.biz_type
 WHERE n.event_code IS NULL
   AND EXISTS (SELECT 1 FROM sys_event_config c WHERE c.event_code = n.biz_type);
