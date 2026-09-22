-- ============================================================================
-- ⚠ 撞号改号（2026-09-22，Hermes）：原为 148_，与同号文件 148_role_menu_fix_system_orphans.sql 撞号；按「后建者改号」改为 186_，内容一字未动。
-- 148_display_name_normalize.sql
-- 任务码：dev-20260921-007
--
-- 目的：把「人员显示名」字段里历史写入的账号名（sys_user.user_name）归一为
--       显示名（sys_user.nick_name），修同一人在列表/打印件里出现两种写法的问题。
--
-- 背景（实测，2026-09-21）：
--   用户报「QT2609210002 / QT2609210001 为什么销售员不一样」：两条报价单
--   sales_person_id 都是 138，但 sales_person_name 一条是账号 zhangmuhua、一条是姓名 张慕华。
--   写入来源不同：
--     询价单自动设置销售负责人 → SecurityUtils.getUsername()（账号）  InquiryServiceImpl:198-205
--     询价转报价             → 照抄询价单的值                        InquiryServiceImpl:500
--     手动新增报价           → SecurityUtils.getRealName()（姓名）  QuotationServiceImpl:259-262
--   前端列表直接显示该字段：QuotationTableColumns.vue:66。
--   全库扫描（160 个显示类 *Name 列，逐列比对 sys_user.user_name vs nick_name）命中：
--     sales_quotation.sales_person_name  1 行（QT2609210001 = zhangmuhua）
--     sales_inquiry.sales_person_name    1 行（INQ2609210001 = zhangmuhua）
--     sys_notification.sender_name       1 行
--     sys_task.assignee_name            21 行
--   其余列当前为 0 行（业务测试数据刚清理过），下面的语句对它们是 no-op、幂等。
--
-- 范围：只处理「人员显示名」语义的列，不含 material_name / customer_name /
--       product_name / bank_name / warehouse_name 等业务名称列。
--
-- 配套改动（同任务）：
--   ① SecurityUtils.getDisplayName()（realName 为空回退 username），9 处显示类 *Name 改用它，
--      EventAspect 补 triggerRealName（通知「发送人」显示姓名；任务 create_by 保留账号语义）；
--   ② CONVENTIONS.md 新增规则：显示名只写 nick_name，账号只进 create_by/username 列。
--   → 本迁移只归历史数据，新数据由代码侧保证。
--
-- 幂等：JOIN 命中 = 该列当前存的就是账号名，写成 nick_name 后不再命中；可重复执行。
-- 说明：列与文件表可能跨 collation，比较统一 CONVERT ... COLLATE utf8mb4_unicode_ci。
-- ============================================================================

-- 数字段归一：由脚本生成（见文件头说明），每列一条幂等 UPDATE
UPDATE `sales_quotation` x JOIN sys_user u ON CONVERT(x.`sales_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`sales_person_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`sales_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_quotation` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_quotation_flow` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_inquiry` x JOIN sys_user u ON CONVERT(x.`sales_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`sales_person_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`sales_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_order` x JOIN sys_user u ON CONVERT(x.`sales_manager_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`sales_manager_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`sales_manager_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_order_review` x JOIN sys_user u ON CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`reviewer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_order_review` x JOIN sys_user u ON CONVERT(x.`next_reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`next_reviewer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`next_reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_order_review` x JOIN sys_user u ON CONVERT(x.`next_handler_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`next_handler_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`next_handler_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_customer` x JOIN sys_user u ON CONVERT(x.`sales_manager_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`sales_manager_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`sales_manager_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_delivery` x JOIN sys_user u ON CONVERT(x.`delivery_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`delivery_person_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`delivery_person_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_delivery` x JOIN sys_user u ON CONVERT(x.`receiver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`receiver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`receiver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_delivery` x JOIN sys_user u ON CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`receive_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_invoice` x JOIN sys_user u ON CONVERT(x.`issue_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`issue_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`issue_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_invoice` x JOIN sys_user u ON CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`receive_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_receipt` x JOIN sys_user u ON CONVERT(x.`confirm_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`confirm_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`confirm_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_receipt` x JOIN sys_user u ON CONVERT(x.`payer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`payer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`payer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_return` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_return` x JOIN sys_user u ON CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`receive_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`receive_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_return` x JOIN sys_user u ON CONVERT(x.`refund_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`refund_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`refund_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_sample_order` x JOIN sys_user u ON CONVERT(x.`confirm_by` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`confirm_by` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`confirm_by` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sales_sample_process` x JOIN sys_user u ON CONVERT(x.`operator` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `purchase_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `purchase_payment` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_inbound_order` x JOIN sys_user u ON CONVERT(x.`inspector_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`inspector_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`inspector_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_inbound_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_outbound_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_transaction` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_transfer_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_stocktake_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_stocktake_order` x JOIN sys_user u ON CONVERT(x.`stocktaker_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`stocktaker_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`stocktaker_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_stocktake_order` x JOIN sys_user u ON CONVERT(x.`supervisor_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`supervisor_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`supervisor_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_quarantine` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_disposition_order` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_return_order` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_rework_order` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_scrap_order` x JOIN sys_user u ON CONVERT(x.`applicant_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`applicant_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`applicant_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `inventory_iqc_scrap_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_order` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_order` x JOIN sys_user u ON CONVERT(x.`dispatch_leader_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`dispatch_leader_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`dispatch_leader_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_operation_execution` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_operation_record` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_task_event` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_trace_log` x JOIN sys_user u ON CONVERT(x.`operator` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_work_report` x JOIN sys_user u ON CONVERT(x.`reporter_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`reporter_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`reporter_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_work_report` x JOIN sys_user u ON CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`reviewer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_work_report` x JOIN sys_user u ON CONVERT(x.`pending_reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`pending_reviewer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`pending_reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_work_report` x JOIN sys_user u ON CONVERT(x.`proxy_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`proxy_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`proxy_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_work_report` x JOIN sys_user u ON CONVERT(x.`cancelled_by_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`cancelled_by_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`cancelled_by_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_quality_inspection` x JOIN sys_user u ON CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`inspector` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `production_quality_inspection` x JOIN sys_user u ON CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`reviewer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`reviewer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `quality_lot` x JOIN sys_user u ON CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`inspector` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `quality_ncr` x JOIN sys_user u ON CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`inspector` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`inspector` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `quality_ncr_action` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `quality_template_print_log` x JOIN sys_user u ON CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`operator_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`operator_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `engineering_film` x JOIN sys_user u ON CONVERT(x.`designer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`designer_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`designer_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `engineering_film` x JOIN sys_user u ON CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`approver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`approver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sys_notification` x JOIN sys_user u ON CONVERT(x.`sender_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`sender_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`sender_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sys_notification` x JOIN sys_user u ON CONVERT(x.`receiver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`receiver_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`receiver_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE `sys_task` x JOIN sys_user u ON CONVERT(x.`assignee_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET x.`assignee_name` = u.nick_name
 WHERE CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci IS NOT NULL AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci <> '' AND CONVERT(x.`assignee_name` USING utf8mb4) COLLATE utf8mb4_unicode_ci <> CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_unicode_ci;

-- ============================================================================
-- 执行后自检（都应 0 行）：
--   SELECT quotation_no, sales_person_name FROM sales_quotation;
--   SELECT inquiry_no, sales_person_name FROM sales_inquiry;
--   SELECT COUNT(*) FROM sys_task WHERE assignee_name IN (SELECT user_name FROM sys_user);
-- ============================================================================
