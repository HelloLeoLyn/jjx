-- 印刷齐套缺口通知事件（dev-20261126-002）
INSERT INTO sys_event_config(event_code,event_name,biz_module,event_type,kanban_module,priority,is_enabled,target_role,title,content,jump_path,create_time,update_time)
SELECT 'printing.kit.shortage','印刷齐套缺口','production','notification','biz','high',1,'[28, 29, 32]',
       '工单【{bizNo}】印刷缺版 {gapCount} 项',
       '工单【{bizNo}】印刷齐套复查仍缺 {gapCount} 项（网版未制版或已洗版），请安排制版或改用同版本备用版。',
       '/production/printing-kit?orderId={orderId}', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code='printing.kit.shortage');

SELECT event_code,target_role,title FROM sys_event_config WHERE event_code='printing.kit.shortage';
