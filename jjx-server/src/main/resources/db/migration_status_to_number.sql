-- ============================================================
-- 状态字段统一 number 迁移脚本
-- 日期: 2026-07-31
-- 规则: 字符串状态 → 数字 (从0开始, 保持与后端枚举一致)
-- 跳过: kanban_task.status / portal_inquiry.inquiry_status (死表)
-- ============================================================

-- ---------- ① 销售模块 ----------
-- sales_inquiry.inquiry_status: draft=0, pending=1, sent=2, converted=3, accepted=4, rejected=5
UPDATE sales_inquiry SET inquiry_status = '0' WHERE inquiry_status = 'draft';
UPDATE sales_inquiry SET inquiry_status = '1' WHERE inquiry_status = 'pending';
UPDATE sales_inquiry SET inquiry_status = '2' WHERE inquiry_status = 'sent';
UPDATE sales_inquiry SET inquiry_status = '3' WHERE inquiry_status = 'converted';
UPDATE sales_inquiry SET inquiry_status = '4' WHERE inquiry_status = 'accepted';
UPDATE sales_inquiry SET inquiry_status = '5' WHERE inquiry_status = 'rejected';
ALTER TABLE sales_inquiry MODIFY COLUMN inquiry_status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0草稿/1待处理/2已发送/3已转报价/4已确认/5已拒绝';

-- sales_quotation.quotation_status: draft=0, sent=1, accepted=2, rejected=3, expired=4, pending_review=5, approved=6
UPDATE sales_quotation SET quotation_status = '0' WHERE quotation_status = 'draft';
UPDATE sales_quotation SET quotation_status = '1' WHERE quotation_status = 'sent';
UPDATE sales_quotation SET quotation_status = '2' WHERE quotation_status = 'accepted';
UPDATE sales_quotation SET quotation_status = '3' WHERE quotation_status = 'rejected';
UPDATE sales_quotation SET quotation_status = '4' WHERE quotation_status = 'expired';
UPDATE sales_quotation SET quotation_status = '5' WHERE quotation_status = 'pending_review';
UPDATE sales_quotation SET quotation_status = '6' WHERE quotation_status = 'approved';
ALTER TABLE sales_quotation MODIFY COLUMN quotation_status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0草稿/1已发送/2已确认/3已拒绝/4已过期/5待审核/6已审核';

-- ---------- ② 库存模块 ----------
-- 统一单据状态编码: draft=0, pending=1, approved=2, rejected=3, processing=4, confirmed=5,
--                   out_confirm=6, in_confirm=7, closed=8, cancelled=9, completed=10, processed=11, in_progress=12

-- inventory_inbound_order
UPDATE inventory_inbound_order SET approve_status = '1' WHERE approve_status = 'pending';
UPDATE inventory_inbound_order SET approve_status = '2' WHERE approve_status = 'approved';
UPDATE inventory_inbound_order SET approve_status = '3' WHERE approve_status = 'rejected';
UPDATE inventory_inbound_order SET order_status = '0' WHERE order_status = 'draft';
UPDATE inventory_inbound_order SET order_status = '1' WHERE order_status = 'pending';
UPDATE inventory_inbound_order SET order_status = '2' WHERE order_status = 'approved';
UPDATE inventory_inbound_order SET order_status = '3' WHERE order_status = 'rejected';
UPDATE inventory_inbound_order SET order_status = '4' WHERE order_status = 'processing';
UPDATE inventory_inbound_order SET order_status = '5' WHERE order_status = 'confirmed';
UPDATE inventory_inbound_order SET order_status = '6' WHERE order_status = 'out_confirm';
UPDATE inventory_inbound_order SET order_status = '7' WHERE order_status = 'in_confirm';
UPDATE inventory_inbound_order SET order_status = '8' WHERE order_status = 'closed';
UPDATE inventory_inbound_order SET order_status = '9' WHERE order_status = 'cancelled';
ALTER TABLE inventory_inbound_order MODIFY COLUMN approve_status TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态: 1待审批/2已批准/3已驳回';
ALTER TABLE inventory_inbound_order MODIFY COLUMN order_status TINYINT NOT NULL DEFAULT 0 COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/4处理中/5已确认/6已出库/7已入库/8已关闭/9已取消';

-- inventory_outbound_order
UPDATE inventory_outbound_order SET approve_status = '1' WHERE approve_status = 'pending';
UPDATE inventory_outbound_order SET approve_status = '2' WHERE approve_status = 'approved';
UPDATE inventory_outbound_order SET approve_status = '3' WHERE approve_status = 'rejected';
UPDATE inventory_outbound_order SET order_status = '0' WHERE order_status = 'draft';
UPDATE inventory_outbound_order SET order_status = '1' WHERE order_status = 'pending';
UPDATE inventory_outbound_order SET order_status = '2' WHERE order_status = 'approved';
UPDATE inventory_outbound_order SET order_status = '3' WHERE order_status = 'rejected';
UPDATE inventory_outbound_order SET order_status = '4' WHERE order_status = 'processing';
UPDATE inventory_outbound_order SET order_status = '5' WHERE order_status = 'confirmed';
UPDATE inventory_outbound_order SET order_status = '6' WHERE order_status = 'out_confirm';
UPDATE inventory_outbound_order SET order_status = '7' WHERE order_status = 'in_confirm';
UPDATE inventory_outbound_order SET order_status = '8' WHERE order_status = 'closed';
UPDATE inventory_outbound_order SET order_status = '9' WHERE order_status = 'cancelled';
ALTER TABLE inventory_outbound_order MODIFY COLUMN approve_status TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态: 1待审批/2已批准/3已驳回';
ALTER TABLE inventory_outbound_order MODIFY COLUMN order_status TINYINT NOT NULL DEFAULT 0 COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/4处理中/5已确认/6已出库/7已入库/8已关闭/9已取消';

-- inventory_stocktake_order
UPDATE inventory_stocktake_order SET approve_status = '1' WHERE approve_status = 'pending';
UPDATE inventory_stocktake_order SET approve_status = '2' WHERE approve_status = 'approved';
UPDATE inventory_stocktake_order SET approve_status = '3' WHERE approve_status = 'rejected';
UPDATE inventory_stocktake_order SET order_status = '0' WHERE order_status = 'draft';
UPDATE inventory_stocktake_order SET order_status = '4' WHERE order_status = 'processing';
UPDATE inventory_stocktake_order SET order_status = '5' WHERE order_status = 'confirmed';
UPDATE inventory_stocktake_order SET order_status = '11' WHERE order_status = 'processed';
UPDATE inventory_stocktake_order SET order_status = '8' WHERE order_status = 'closed';
ALTER TABLE inventory_stocktake_order MODIFY COLUMN approve_status TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态: 1待审批/2已批准/3已驳回';
ALTER TABLE inventory_stocktake_order MODIFY COLUMN order_status TINYINT NOT NULL DEFAULT 0 COMMENT '单据状态: 0草稿/4处理中/5已确认/11已处理/8已关闭';

-- inventory_stocktake_item.adjust_status: pending=0, processed=1
UPDATE inventory_stocktake_item SET adjust_status = '0' WHERE adjust_status = 'pending';
UPDATE inventory_stocktake_item SET adjust_status = '1' WHERE adjust_status = 'processed';
ALTER TABLE inventory_stocktake_item MODIFY COLUMN adjust_status TINYINT NOT NULL DEFAULT 0 COMMENT '调整状态: 0待处理/1已处理';

-- inventory_transfer_order
UPDATE inventory_transfer_order SET approve_status = '1' WHERE approve_status = 'pending';
UPDATE inventory_transfer_order SET approve_status = '2' WHERE approve_status = 'approved';
UPDATE inventory_transfer_order SET approve_status = '3' WHERE approve_status = 'rejected';
UPDATE inventory_transfer_order SET order_status = '0' WHERE order_status = 'draft';
UPDATE inventory_transfer_order SET order_status = '1' WHERE order_status = 'pending';
UPDATE inventory_transfer_order SET order_status = '2' WHERE order_status = 'approved';
UPDATE inventory_transfer_order SET order_status = '3' WHERE order_status = 'rejected';
UPDATE inventory_transfer_order SET order_status = '6' WHERE order_status = 'out_confirm';
UPDATE inventory_transfer_order SET order_status = '10' WHERE order_status = 'completed';
UPDATE inventory_transfer_order SET order_status = '12' WHERE order_status = 'in_progress';
UPDATE inventory_transfer_order SET order_status = '9' WHERE order_status = 'cancelled';
ALTER TABLE inventory_transfer_order MODIFY COLUMN approve_status TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态: 1待审批/2已批准/3已驳回';
ALTER TABLE inventory_transfer_order MODIFY COLUMN order_status TINYINT NOT NULL DEFAULT 0 COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/6已出库/10已完成/12调拨中/9已取消';

-- inventory_transfer_item.status: pending=0, completed=1
UPDATE inventory_transfer_item SET status = '0' WHERE status = 'pending';
UPDATE inventory_transfer_item SET status = '1' WHERE status = 'completed';
ALTER TABLE inventory_transfer_item MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '明细状态: 0待调拨/1已完成';

-- inventory_alert_log.status: new=0, read=1, processed=2
UPDATE inventory_alert_log SET status = '0' WHERE status = 'new';
UPDATE inventory_alert_log SET status = '1' WHERE status = 'read';
UPDATE inventory_alert_log SET status = '2' WHERE status = 'processed';
ALTER TABLE inventory_alert_log MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态: 0新增/1已读/2已处理';

-- ---------- ③ 采购模块 ----------
-- purchase_order.payment_status: 复用 PaymentStatusEnum pending=0, partially_paid=1, completed=2
UPDATE purchase_order SET payment_status = '0' WHERE payment_status = 'pending';
UPDATE purchase_order SET payment_status = '1' WHERE payment_status = 'partially_paid';
UPDATE purchase_order SET payment_status = '2' WHERE payment_status = 'completed';
ALTER TABLE purchase_order MODIFY COLUMN payment_status TINYINT NOT NULL DEFAULT 0 COMMENT '付款状态: 0待付款/1部分付款/2已付款';

-- purchase_order.receipt_status: 复用 ReceiptStatusEnum pending=0, partially_received=1, completed=2
UPDATE purchase_order SET receipt_status = '0' WHERE receipt_status = 'pending';
UPDATE purchase_order SET receipt_status = '1' WHERE receipt_status = 'partially_received';
UPDATE purchase_order SET receipt_status = '2' WHERE receipt_status = 'completed';
ALTER TABLE purchase_order MODIFY COLUMN receipt_status TINYINT NOT NULL DEFAULT 0 COMMENT '收货状态: 0待收货/1部分收货/2已收货';

-- purchase_payment.payment_status: 同上
UPDATE purchase_payment SET payment_status = '0' WHERE payment_status = 'pending';
UPDATE purchase_payment SET payment_status = '1' WHERE payment_status = 'partially_paid';
UPDATE purchase_payment SET payment_status = '2' WHERE payment_status = 'completed';
ALTER TABLE purchase_payment MODIFY COLUMN payment_status TINYINT NOT NULL DEFAULT 0 COMMENT '付款状态: 0待付款/1部分付款/2已付款';

-- purchase_document.document_status: pending=0, verified=1, archived=2
UPDATE purchase_document SET document_status = '0' WHERE document_status = 'pending';
UPDATE purchase_document SET document_status = '1' WHERE document_status = 'verified';
UPDATE purchase_document SET document_status = '2' WHERE document_status = 'archived';
ALTER TABLE purchase_document MODIFY COLUMN document_status TINYINT NOT NULL DEFAULT 0 COMMENT '单据状态: 0待处理/1已核验/2已归档';

-- purchase_material_inquiry.inquiry_status: active=0, inactive=1, expired=2, cancelled=3, completed=4
UPDATE purchase_material_inquiry SET inquiry_status = '0' WHERE inquiry_status = 'active';
UPDATE purchase_material_inquiry SET inquiry_status = '1' WHERE inquiry_status = 'inactive';
UPDATE purchase_material_inquiry SET inquiry_status = '2' WHERE inquiry_status = 'expired';
UPDATE purchase_material_inquiry SET inquiry_status = '3' WHERE inquiry_status = 'cancelled';
UPDATE purchase_material_inquiry SET inquiry_status = '4' WHERE inquiry_status = 'completed';
ALTER TABLE purchase_material_inquiry MODIFY COLUMN inquiry_status TINYINT NOT NULL DEFAULT 0 COMMENT '询价状态: 0有效/1无效/2已过期/3已取消/4已完成';

-- purchase_order_item.inquiry_status: pending=0, inquired=1, comparing=2, selected=3
UPDATE purchase_order_item SET inquiry_status = '0' WHERE inquiry_status = 'pending';
UPDATE purchase_order_item SET inquiry_status = '1' WHERE inquiry_status = 'inquired';
UPDATE purchase_order_item SET inquiry_status = '2' WHERE inquiry_status = 'comparing';
UPDATE purchase_order_item SET inquiry_status = '3' WHERE inquiry_status = 'selected';
ALTER TABLE purchase_order_item MODIFY COLUMN inquiry_status TINYINT NOT NULL DEFAULT 0 COMMENT '询价状态: 0待询价/1已询价/2比价中/3已选中';

-- ---------- ④ 生产模块 ----------
-- production_order.order_status: 复用生产 OrderStatusEnum(0-11), 实体已 Integer, 仅改库
ALTER TABLE production_order MODIFY COLUMN order_status TINYINT DEFAULT 0 COMMENT '订单状态: 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期';

-- production_order.approval_status: draft=0, pending=1, approved=2, rejected=3, cancelled=4
UPDATE production_order SET approval_status = '0' WHERE approval_status = 'draft';
UPDATE production_order SET approval_status = '1' WHERE approval_status = 'pending';
UPDATE production_order SET approval_status = '2' WHERE approval_status = 'approved';
UPDATE production_order SET approval_status = '3' WHERE approval_status = 'rejected';
UPDATE production_order SET approval_status = '4' WHERE approval_status = 'cancelled';
ALTER TABLE production_order MODIFY COLUMN approval_status TINYINT DEFAULT 0 COMMENT '审批状态: 0草稿/1待审批/2已批准/3已驳回/4已取消';

-- production_operation_execution.execution_status: 按 ExecutionStatusEnum 顺序
-- PENDING=0, PREPARING=1, EXECUTING=2, PAUSED=3, COMPLETED=4, SKIPPED=5, CANCELLED=6, OVERDUE=7, ABNORMAL=8, PENDING_CONFIRMATION=9
UPDATE production_operation_execution SET execution_status = '0' WHERE execution_status = 'PENDING';
UPDATE production_operation_execution SET execution_status = '1' WHERE execution_status = 'PREPARING';
UPDATE production_operation_execution SET execution_status = '2' WHERE execution_status = 'EXECUTING';
UPDATE production_operation_execution SET execution_status = '3' WHERE execution_status = 'PAUSED';
UPDATE production_operation_execution SET execution_status = '4' WHERE execution_status = 'COMPLETED';
UPDATE production_operation_execution SET execution_status = '5' WHERE execution_status = 'SKIPPED';
UPDATE production_operation_execution SET execution_status = '6' WHERE execution_status = 'CANCELLED';
UPDATE production_operation_execution SET execution_status = '7' WHERE execution_status = 'OVERDUE';
UPDATE production_operation_execution SET execution_status = '8' WHERE execution_status = 'ABNORMAL';
UPDATE production_operation_execution SET execution_status = '9' WHERE execution_status = 'PENDING_CONFIRMATION';
ALTER TABLE production_operation_execution MODIFY COLUMN execution_status TINYINT DEFAULT 0 COMMENT '执行状态: 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认';

-- production_equipment.status: idle=0, running=1, maintenance=2, fault=3
UPDATE production_equipment SET status = '0' WHERE status = 'idle';
UPDATE production_equipment SET status = '1' WHERE status = 'running';
UPDATE production_equipment SET status = '2' WHERE status = 'maintenance';
UPDATE production_equipment SET status = '3' WHERE status = 'fault';
ALTER TABLE production_equipment MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '设备状态: 0待机/1运行中/2维护中/3故障中';

-- ---------- ⑤ 产品/系统模块 ----------
-- product_config_model.status: 复用 ConfigModelStatus active=1, inactive=0
UPDATE product_config_model SET status = '1' WHERE status = 'active';
UPDATE product_config_model SET status = '0' WHERE status = 'inactive';
ALTER TABLE product_config_model MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1激活/0未激活';

-- product_instance.lifecycle_status: 复用 LifecycleStatus(1-9) DESIGN=1
UPDATE product_instance SET lifecycle_status = '1' WHERE lifecycle_status = 'DESIGN';
UPDATE product_instance SET lifecycle_status = '2' WHERE lifecycle_status = 'CUSTOMER_CONFIRM';
UPDATE product_instance SET lifecycle_status = '3' WHERE lifecycle_status = 'MATERIAL_PREPARING';
UPDATE product_instance SET lifecycle_status = '4' WHERE lifecycle_status = 'PRODUCTION';
UPDATE product_instance SET lifecycle_status = '5' WHERE lifecycle_status = 'QC';
UPDATE product_instance SET lifecycle_status = '6' WHERE lifecycle_status = 'SHIPPED';
UPDATE product_instance SET lifecycle_status = '7' WHERE lifecycle_status = 'COMPLETED';
UPDATE product_instance SET lifecycle_status = '8' WHERE lifecycle_status = 'HOLD';
UPDATE product_instance SET lifecycle_status = '9' WHERE lifecycle_status = 'REWORK';
ALTER TABLE product_instance MODIFY COLUMN lifecycle_status TINYINT NOT NULL DEFAULT 1 COMMENT '生命周期: 1设计/2客户确认/3备料/4生产/5质检/6发货/7完成/8暂停/9返工';

-- product_instance.instance_status: 完整状态机编码 draft=0,created=1,planned=2,in_production=3,paused=4,completed=5,shipped=6,stored=7,in_stock=8,delivered=9,installed=10,in_service=11,maintenance=12,decommissioned=13,returned=14,refurbished=15,scrapped=16,cancelled=17
UPDATE product_instance SET instance_status = '0' WHERE instance_status = 'draft';
UPDATE product_instance SET instance_status = '1' WHERE instance_status = 'created';
UPDATE product_instance SET instance_status = '2' WHERE instance_status = 'planned';
UPDATE product_instance SET instance_status = '3' WHERE instance_status = 'in_production';
UPDATE product_instance SET instance_status = '4' WHERE instance_status = 'paused';
UPDATE product_instance SET instance_status = '5' WHERE instance_status = 'completed';
UPDATE product_instance SET instance_status = '6' WHERE instance_status = 'shipped';
UPDATE product_instance SET instance_status = '7' WHERE instance_status = 'stored';
UPDATE product_instance SET instance_status = '8' WHERE instance_status = 'in_stock';
UPDATE product_instance SET instance_status = '9' WHERE instance_status = 'delivered';
UPDATE product_instance SET instance_status = '10' WHERE instance_status = 'installed';
UPDATE product_instance SET instance_status = '11' WHERE instance_status = 'in_service';
UPDATE product_instance SET instance_status = '12' WHERE instance_status = 'maintenance';
UPDATE product_instance SET instance_status = '13' WHERE instance_status = 'decommissioned';
UPDATE product_instance SET instance_status = '14' WHERE instance_status = 'returned';
UPDATE product_instance SET instance_status = '15' WHERE instance_status = 'refurbished';
UPDATE product_instance SET instance_status = '16' WHERE instance_status = 'scrapped';
UPDATE product_instance SET instance_status = '17' WHERE instance_status = 'cancelled';
ALTER TABLE product_instance MODIFY COLUMN instance_status TINYINT DEFAULT 1 COMMENT '实例状态: 0草稿/1已创建/2已计划/3生产中/4已暂停/5已完成/6已发货/7已入库/8在库/9已交付/10已安装/11使用中/12维护中/13已退役/14已退回/15翻新/16已报废/17已取消';

-- sys_notification.status: PENDING=0, SENT=1
UPDATE sys_notification SET status = '0' WHERE status = 'PENDING';
UPDATE sys_notification SET status = '1' WHERE status = 'SENT';
ALTER TABLE sys_notification MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待发送/1已发送';

-- sys_task.status: pending=0
UPDATE sys_task SET status = '0' WHERE status = 'pending';
ALTER TABLE sys_task MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待处理';
