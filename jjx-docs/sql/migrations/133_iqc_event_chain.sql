-- ============================================================================
-- 133_iqc_event_chain.sql
-- 任务码：dev-20260918-004
--
-- 目的：补齐「采购收货 → 来料待检验 → 检验通过 → 确认入库」这条链的通知/待办配置。
--
-- 背景：业务代码已经在发这些事件（InventoryInboundServiceImpl：
--       quality.iqc.submitted / quality.iqc.approved / quality.iqc.quarantine.created），
--       但 sys_event_config 里没有任何 quality.iqc.* 配置行 —— LocalEventPublisher
--       查不到配置就直接跳过（log.warn "事件未配置或已停用，跳过"），
--       所以质量人员和仓管收不到任何通知或待办，只能自己进 IQC 页看。
--
-- 范围：只增/改配置数据（sys_event_config）；不改表结构、不改业务代码。
-- 口径：event_type='both' = 发通知 + 建 office 待办任务；'notification' = 只发通知。
--       target_role 数组的第一个元素 = 待办任务的归属角色（LocalEventPublisher.parseSingleRole）。
--
-- 角色现状（2026-09-18 实测，全库 26 个用户）：
--       22 INVENTORY 全权限 2 人 ／ 23 INVENTORY 业务操作 0 人
--       25 PURCHASE 全权限 1 人 ／ 26 PURCHASE 业务操作 0 人
--       33 QUALITY 来料检验员 0 人 ／ 34 QUALITY 品质主管 1 人
--       所以：待办任务的角色放在「当前有人」的岗位上（34 / 22）；
--       语义岗位 33（来料检验员）一并写入 target_role，待其授权后自动开始收通知。
--
-- 幂等：新增用 WHERE NOT EXISTS；追加角色用 JSON_CONTAINS 判重；可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 来料待检验（收货生成入库单 → 请录入检验结果）
--    通知 + 待办；派给品质主管（34，有人）与来料检验员（33，待授权）；
--    检验通过（quality.iqc.approved）时自动办结该待办并置已读。
-- ---------------------------------------------------------------------------
INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
     target_role, title, content, close_source_events, exclude_trigger)
SELECT 'quality.iqc.submitted', '来料待检验', 'quality', 'both', 'office', 'normal', 1,
       JSON_ARRAY(34, 33),
       '来料待检验：入库单【{inboundNo}】',
       '采购单 {sourceNo} 已收货并生成入库单【{inboundNo}】，请到「库存管理 → IQC 来料检验」录入检验结果并提交。',
       'quality.iqc.approved', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'quality.iqc.submitted');

-- ---------------------------------------------------------------------------
-- 2) 来料检验通过 → 请确认入库
--    通知 + 待办；派给库存全权限（22，有人，实际执行确认入库）；品质主管（34）同收通知；
--    确认入库（inventory.inbound.confirmed）时自动办结该待办并置已读（见第 5 段）。
-- ---------------------------------------------------------------------------
INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
     target_role, title, content, close_source_events, exclude_trigger)
SELECT 'quality.iqc.approved', '来料检验通过-待确认入库', 'quality', 'both', 'office', 'normal', 1,
       JSON_ARRAY(22, 34),
       '来料检验已通过：【{inboundNo}】请确认入库',
       '入库单【{inboundNo}】来料检验已全部通过，请到「库存管理 → 入库管理」执行确认入库完成过账。',
       NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'quality.iqc.approved');

-- ---------------------------------------------------------------------------
-- 3) 来料检验不合格 → 已生成隔离台账，待处置
--    只发通知（不建待办：处置动作在 IQC 页按行执行）；发给品质主管。
-- ---------------------------------------------------------------------------
INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
     target_role, title, content, close_source_events, exclude_trigger)
SELECT 'quality.iqc.quarantine.created', '来料检验不合格-隔离待处置', 'quality', 'notification', 'office', 'high', 1,
       JSON_ARRAY(34),
       '来料检验不合格：入库单【{inboundNo}】已隔离 {quarantineCount} 项',
       '入库单【{inboundNo}】存在来料检验不合格品，已生成隔离台账，请到「库存管理 → IQC 来料检验」执行隔离处置（退货 / 返工 / 让步接收 / 报废）。',
       NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'quality.iqc.quarantine.created');

-- ---------------------------------------------------------------------------
-- 4) 采购到货 / 采购生成入库单 → 知会来料检验员（33）
--    这两条事件已存在，仅把 33 追加进 target_role（不改变原有收件人与待办归属角色）。
-- ---------------------------------------------------------------------------
UPDATE sys_event_config
   SET target_role = JSON_ARRAY_APPEND(target_role, '$', 33)
 WHERE event_code = 'purchase.received'
   AND NOT JSON_CONTAINS(COALESCE(target_role, JSON_ARRAY()), '33');

UPDATE sys_event_config
   SET target_role = JSON_ARRAY_APPEND(target_role, '$', 33)
 WHERE event_code = 'inventory.inbound.created_from_purchase'
   AND NOT JSON_CONTAINS(COALESCE(target_role, JSON_ARRAY()), '33');

-- ---------------------------------------------------------------------------
-- 5) 确认入库时自动办结「请确认入库」待办 + 置已读
--    close_source_events 写在「触发办结的那个事件」的配置上：
--    inventory.inbound.confirmed 的 payload.bizId = inboundId，与第 2 段任务的 biz_id 一致。
-- ---------------------------------------------------------------------------
UPDATE sys_event_config
   SET close_source_events = CONCAT_WS(',', NULLIF(close_source_events, ''), 'quality.iqc.approved')
 WHERE event_code = 'inventory.inbound.confirmed'
   AND (close_source_events IS NULL OR close_source_events NOT LIKE '%quality.iqc.approved%');

-- ============================================================================
-- 执行后自检（应与注释一致）：
--   SELECT id, event_code, event_type, target_role, title, close_source_events
--     FROM sys_event_config
--    WHERE event_code IN ('quality.iqc.submitted','quality.iqc.approved',
--                         'quality.iqc.quarantine.created','purchase.received',
--                         'inventory.inbound.created_from_purchase','inventory.inbound.confirmed');
-- ============================================================================
