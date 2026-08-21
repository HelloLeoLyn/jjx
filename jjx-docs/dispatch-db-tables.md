# 派工管理 数据库表结构文档

> 生成时间：2026-08-21　|　数据库：`jjx_erp_db`（MySQL）　|　模块：生产 - 派工管理（Dispatch V1 / Node-first 责任链）
>
> 覆盖范围：派工管理页面及相关操作涉及的 13 张表，按 核心表 / 关联业务表 / 辅助基础表 / 权限表 分层。

## 表清单

| 表名 | 分层 | 说明 |
|---|---|---|
| `production_dispatch` | 核心表 | 工序派工单 |
| `production_dispatch_node` | 核心表 | 派工责任链节点(责任持有实例) |
| `production_dispatch_log` | 核心表 | 派工操作流水 |
| `production_operation_execution` | 关联业务表 | 生产工序执行表 |
| `production_execution_assignment` | 关联业务表 | 工序作业分配(谁做多少) |
| `production_work_report` | 关联业务表 | 生产报工(一次不可覆盖的生产数量/工时事实) |
| `production_order` | 关联业务表 | 生产订单表（合并计划和工单） |
| `sys_user` | 辅助基础表 | 用户表 |
| `sys_dept` | 辅助基础表 | 部门表 |
| `engineering_standard_process` | 辅助基础表 | 产品标准工序表 |
| `production_equipment` | 辅助基础表 | 设备管理 |
| `sys_menu` | 权限表 | 菜单表 |
| `sys_role_menu` | 权限表 | 角色和菜单关联表 |

## production_dispatch  `核心表`

**表注释**：工序派工单

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `dispatch_id` | bigint | NO | PRI | NULL | auto_increment | 派工单ID |
| `order_id` | bigint | NO | MUL | NULL |  | 生产订单ID |
| `order_no` | varchar(50) | YES |  | NULL |  | 工单编号(冗余) |
| `execution_id` | bigint | NO | UNI | NULL |  | 工序执行记录ID(production_operation_execution) |
| `process_name` | varchar(200) | YES |  | NULL |  | 工序名称(冗余) |
| `process_order` | int | YES |  | NULL |  | 工序顺序(冗余) |
| `team_id` | bigint | YES | MUL | NULL |  | 责任班组(部门ID) |
| `team_name` | varchar(100) | YES |  | NULL |  | 责任班组名称 |
| `equipment_id` | bigint | YES |  | NULL |  | 设备ID(空=不限) |
| `equipment_name` | varchar(200) | YES |  | NULL |  | 设备名称 |
| `operators` | varchar(500) | YES |  | NULL |  | 执行人(JSON数组 [{userId,userName}]) |
| `assigned_by` | bigint | YES |  | NULL |  | 派工主管(用户ID) |
| `assigned_by_name` | varchar(64) | YES |  | NULL |  | 派工主管姓名 |
| `assign_time` | datetime | YES |  | NULL |  | 最近指派时间 |
| `status` | tinyint | NO | MUL | 0 |  | 状态：0待派工 1已派工 2执行中 3已完成 4已退回（静态枚举） |
| `reject_reason` | varchar(500) | YES |  | NULL |  | 退回原因(退回时必填) |
| `re_dispatch_count` | int | NO |  | 0 |  | 改派次数 |
| `remark` | varchar(500) | YES |  | NULL |  | 备注 |
| `del_flag` | char(1) | NO |  | 0 |  | 删除标志 0正常 1删除 |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建人 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新人 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `dispatch_id` |
| `uk_execution` | 是 | `execution_id` |
| `idx_order` | 否 | `order_id` |
| `idx_team_status` | 否 | `team_id` , `status` |
| `idx_status` | 否 | `status` |

---

## production_dispatch_node  `核心表`

**表注释**：派工责任链节点(责任持有实例)

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `node_id` | bigint | NO | PRI | NULL | auto_increment | 节点ID |
| `dispatch_id` | bigint | NO | MUL | NULL |  | 派工单ID(production_dispatch.dispatch_id) |
| `parent_node_id` | bigint | YES | MUL | NULL |  | 上级节点ID(第1级=NULL，表示源头主管直派；责任来源节点) |
| `assignee_type` | varchar(20) | NO |  | USER |  | 责任主体类型：USER(P1第一版仅支持) |
| `assignee_id` | bigint | NO | MUL | NULL |  | 责任主体ID(用户ID) |
| `assignee_name` | varchar(64) | NO |  | NULL |  | 责任主体姓名快照(改昵称不影响历史) |
| `org_id` | bigint | YES |  | NULL |  | 责任主体当时所属组织ID快照 |
| `org_name` | varchar(100) | YES |  | NULL |  | 责任主体当时所属组织名称快照 |
| `org_path` | varchar(500) | YES |  | NULL |  | 责任主体当时所属组织祖先路径快照(如"1/5/6/7") |
| `node_status` | varchar(20) | NO |  | ACTIVE |  | 节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED |
| `assigned_by` | bigint | YES |  | NULL |  | 本次责任由谁指派(用户ID) |
| `assigned_by_name` | varchar(64) | YES |  | NULL |  | 指派人姓名快照 |
| `assigned_at` | datetime | YES |  | NULL |  | 本次责任正式生效时间 |
| `closed_at` | datetime | YES |  | NULL |  | 本次责任周期结束时间(流转走/完成/取消) |
| `remark` | varchar(500) | YES |  | NULL |  | 备注/退回原因/迁移说明(LEGACY_BACKFILL) |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建人 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新人 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |
| `active_guard` | tinyint | YES |  | NULL | STORED GENERATED | 唯一ACTIVE守卫列(ACTIVE→1，其他→NULL；DB生成，Java不写) |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `node_id` |
| `uk_dispatch_active` | 是 | `dispatch_id` , `active_guard` |
| `idx_dispatch` | 否 | `dispatch_id` |
| `idx_assignee_status` | 否 | `assignee_id` , `node_status` |
| `idx_parent` | 否 | `parent_node_id` |

---

## production_dispatch_log  `核心表`

**表注释**：派工操作流水

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `log_id` | bigint | NO | PRI | NULL | auto_increment | 流水ID |
| `dispatch_id` | bigint | NO | MUL | NULL |  | 派工单ID |
| `order_id` | bigint | YES | MUL | NULL |  | 工单ID(冗余) |
| `action` | varchar(20) | NO |  | NULL |  | 操作：ASSIGN指派/REASSIGN改派/REJECT退回/START开始/COMPLETE完成 |
| `content` | varchar(1000) | YES |  | NULL |  | 变更内容（如：由生产一组改派给生产二组，设备由3#印刷机改为5#印刷机） |
| `operator_id` | bigint | YES |  | NULL |  | 操作人ID |
| `operator_name` | varchar(64) | YES |  | NULL |  | 操作人姓名 |
| `create_time` | datetime | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 操作时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `log_id` |
| `idx_dispatch` | 否 | `dispatch_id` |
| `idx_order` | 否 | `order_id` |

---

## production_operation_execution  `关联业务表`

**表注释**：生产工序执行表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `execution_id` | bigint | NO | PRI | NULL | auto_increment | 执行ID |
| `order_id` | bigint | NO | MUL | NULL |  | 生产订单ID |
| `process_id` | bigint | YES | MUL | NULL |  | 标准工序ID（印刷等自定义工序为空） |
| `custom_process_params` | varchar(500) | YES |  | NULL |  | 计划工艺参数JSON（从工艺路线带入） |
| `process_name` | varchar(200) | YES |  | NULL |  | 工序名称冗余（印刷等自定义工序） |
| `major_category` | varchar(20) | NO |  | ASSEMBLY |  | 大类：ASSEMBLY冲型组装/PRINT印刷 |
| `process_order` | int | NO |  | NULL |  | 工序顺序 |
| `planned_start_time` | datetime | YES | MUL | NULL |  | 计划开始时间 |
| `planned_end_time` | datetime | YES |  | NULL |  | 计划结束时间 |
| `actual_start_time` | datetime | YES | MUL | NULL |  | 实际开始时间 |
| `actual_end_time` | datetime | YES |  | NULL |  | 实际结束时间 |
| `actual_labor_hours` | decimal(10,2) | YES |  | 0.00 |  | 实际人工工时 |
| `actual_machine_hours` | decimal(10,2) | YES |  | 0.00 |  | 实际机器工时 |
| `equipment_id` | bigint | YES | MUL | NULL |  | 使用设备ID |
| `equipment_code` | varchar(50) | YES |  | NULL |  | 设备编号 |
| `equipment_name` | varchar(200) | YES |  | NULL |  | 设备名称 |
| `operator_id` | bigint | YES | MUL | NULL |  | 操作员ID |
| `operator_name` | varchar(100) | YES |  | NULL |  | 操作员姓名 |
| `input_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 投入数量 |
| `output_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 产出数量 |
| `qualified_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 合格数量 |
| `defective_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 不良数量 |
| `defective_reason` | varchar(500) | YES |  | NULL |  | 不良原因 |
| `actual_process_params` | json | YES |  | NULL |  | 实际工艺参数（JSON格式） |
| `quality_check_result` | json | YES |  | NULL |  | 质量检查结果（JSON格式） |
| `execution_status` | tinyint | YES | MUL | 0 |  | 执行状态: 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `execution_id` |
| `idx_order_id` | 否 | `order_id` |
| `idx_process_id` | 否 | `process_id` |
| `idx_execution_status` | 否 | `execution_status` |
| `idx_operator_id` | 否 | `operator_id` |
| `idx_equipment_id` | 否 | `equipment_id` |
| `idx_planned_time` | 否 | `planned_start_time` , `planned_end_time` |
| `idx_actual_time` | 否 | `actual_start_time` , `actual_end_time` |
| `idx_execution_order_status` | 否 | `order_id` , `execution_status` , `process_order` |
| `idx_execution_operator_time` | 否 | `operator_id` , `actual_start_time` , `actual_end_time` |
| `idx_execution_equipment_time` | 否 | `equipment_id` , `actual_start_time` , `actual_end_time` |

---

## production_execution_assignment  `关联业务表`

**表注释**：工序作业分配(谁做多少)

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `assignment_id` | bigint | NO | PRI | NULL | auto_increment | 作业分配ID |
| `execution_id` | bigint | NO | MUL | NULL |  | 工序执行ID |
| `order_id` | bigint | NO | MUL | NULL |  | 生产工单ID(冗余:工单维度查询/过滤) |
| `dispatch_id` | bigint | YES |  | NULL |  | 派工容器ID(1:1 execution) |
| `dispatch_node_id` | bigint | YES | MUL | NULL |  | 分配时责任节点ID(谁授权的这份作业) |
| `assignee_id` | bigint | NO | MUL | NULL |  | 执行人ID |
| `assignee_name` | varchar(64) | NO |  | NULL |  | 执行人姓名(快照) |
| `assigned_quantity` | decimal(18,4) | NO |  | NULL |  | 分配作业数量(创建后不可直接修改) |
| `released_quantity` | decimal(18,4) | NO |  | 0.0000 |  | 释放剩余数量(默认0) |
| `assignment_status` | varchar(20) | NO |  | ACTIVE |  | 状态: ACTIVE/COMPLETED/CANCELLED |
| `assigned_by` | bigint | YES |  | NULL |  | 分配人ID |
| `assigned_by_name` | varchar(64) | YES |  | NULL |  | 分配人姓名(快照) |
| `assigned_at` | datetime | YES |  | NULL |  | 分配时间(业务时间) |
| `cancelled_by` | bigint | YES |  | NULL |  | 取消/释放操作人ID |
| `cancelled_at` | datetime | YES |  | NULL |  | 取消/释放时间 |
| `cancel_reason` | varchar(500) | YES |  | NULL |  | 取消/释放原因 |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建人 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新人 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `assignment_id` |
| `idx_execution` | 否 | `execution_id` |
| `idx_order` | 否 | `order_id` |
| `idx_assignee_status` | 否 | `assignee_id` , `assignment_status` |
| `idx_dispatch_node` | 否 | `dispatch_node_id` |

---

## production_work_report  `关联业务表`

**表注释**：生产报工(一次不可覆盖的生产数量/工时事实)

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `report_id` | bigint | NO | PRI | NULL | auto_increment | 报工ID |
| `order_id` | bigint | NO |  | NULL |  | 生产订单ID(冗余引用，便于追溯查询) |
| `order_no` | varchar(50) | YES |  | NULL |  | 工单编号(冗余) |
| `execution_id` | bigint | NO | MUL | NULL |  | 工序执行记录ID(生产事实主体) |
| `dispatch_id` | bigint | YES |  | NULL |  | 派工单ID(冗余；需与 node.dispatchId 一致) |
| `dispatch_node_id` | bigint | NO | MUL | NULL |  | 报工时责任节点ID(责任锚点) |
| `assignment_id` | bigint | YES | MUL | NULL |  | 关联作业分配ID(新链路必填,历史NULL) |
| `reporter_id` | bigint | NO | MUL | NULL |  | 报工提交人ID(P2-C 默认须=ACTIVE assignee，库不强制) |
| `reporter_name` | varchar(64) | NO |  | NULL |  | 报工提交人姓名快照 |
| `equipment_id` | bigint | YES |  | NULL |  | 本次实际使用设备ID(可空=人工工序无设备) |
| `equipment_name` | varchar(200) | YES |  | NULL |  | 本次实际使用设备名称(快照) |
| `qualified_quantity` | decimal(18,4) | NO |  | 0.0000 |  | 本次合格数量 |
| `defective_quantity` | decimal(18,4) | NO |  | 0.0000 |  | 本次不良数量 |
| `labor_hours` | decimal(10,2) | NO |  | 0.00 |  | 本次人工工时 |
| `machine_hours` | decimal(10,2) | NO |  | 0.00 |  | 本次机器工时 |
| `work_start_time` | datetime | YES |  | NULL |  | 本次生产开始时间(可空) |
| `work_end_time` | datetime | YES |  | NULL |  | 本次生产结束时间(可空；P2-C 校验 end>=start) |
| `report_time` | datetime | NO | MUL | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 报工正式提交时间(Service 显式设置) |
| `defect_reason` | varchar(500) | YES |  | NULL |  | 不良原因(P2 V1 单字段，P3 再做缺陷明细) |
| `remark` | varchar(500) | YES |  | NULL |  | 备注(提交后不可变) |
| `report_status` | varchar(20) | NO |  | SUBMITTED |  | 状态：SUBMITTED已提交/CANCELLED已撤销 |
| `cancelled_by` | bigint | YES |  | NULL |  | 撤销人ID |
| `cancelled_by_name` | varchar(64) | YES |  | NULL |  | 撤销人姓名 |
| `cancelled_at` | datetime | YES |  | NULL |  | 撤销时间 |
| `cancel_reason` | varchar(500) | YES |  | NULL |  | 撤销原因(P2-C 必填) |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建人 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新人 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `report_id` |
| `idx_execution` | 否 | `execution_id` |
| `idx_execution_status` | 否 | `execution_id` , `report_status` |
| `idx_dispatch_node` | 否 | `dispatch_node_id` |
| `idx_reporter_status` | 否 | `reporter_id` , `report_status` |
| `idx_report_time` | 否 | `report_time` |
| `idx_assignment` | 否 | `assignment_id` |

---

## production_order  `关联业务表`

**表注释**：生产订单表（合并计划和工单）

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `order_id` | bigint | NO | PRI | NULL | auto_increment | 订单ID |
| `trace_id` | varchar(64) | YES |  | NULL |  | 链路追踪ID |
| `order_no` | varchar(50) | NO | UNI | NULL |  | 订单编号 |
| `order_type` | varchar(20) | NO | MUL | NULL |  | 订单类型：PLAN生产计划/WORK_ORDER生产工单 |
| `parent_order_id` | bigint | YES | MUL | NULL |  | 父订单ID（计划生成工单时使用） |
| `sales_order_id` | bigint | YES | MUL | NULL |  | 销售订单ID |
| `sales_order_no` | varchar(50) | YES |  | NULL |  | 销售订单编号 |
| `product_id` | bigint | NO | MUL | NULL |  | 产品ID |
| `product_code` | varchar(50) | NO |  | NULL |  | 产品编码 |
| `product_name` | varchar(200) | NO |  | NULL |  | 产品名称 |
| `product_spec` | varchar(500) | YES |  | NULL |  | 产品规格 |
| `product_unit` | varchar(20) | YES |  | PCS |  | 产品单位 |
| `bom_id` | bigint | YES |  | NULL |  | 创建时使用的BOM ID |
| `bom_code` | varchar(50) | YES |  | NULL |  | 创建时使用的BOM编码 |
| `routing_id` | bigint | YES |  | NULL |  | 使用的工艺路线ID |
| `routing_code` | varchar(50) | YES |  | NULL |  | 工艺路线编码 |
| `planned_quantity` | decimal(18,4) | NO |  | NULL |  | 计划数量 |
| `completed_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 已完成数量 |
| `finished_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 成品完工数量（最后一道工序合格数，052口径） |
| `remaining_quantity` | decimal(18,4) | YES |  | 0.0000 |  | 剩余数量 |
| `plan_start_date` | date | NO | MUL | NULL |  | 计划开始日期 |
| `plan_end_date` | date | NO |  | NULL |  | 计划结束日期 |
| `actual_start_time` | datetime | YES |  | NULL |  | 实际开始时间 |
| `actual_end_time` | datetime | YES |  | NULL |  | 实际结束时间 |
| `completed_by` | varchar(50) | YES |  | NULL |  | 完工操作人(053留痕) |
| `quality_inspection_id` | bigint | YES |  | NULL |  | 关联完工质检单ID(053留痕) |
| `inbound_pending_flag` | tinyint | YES |  | 0 |  | 入库待处理标记：0正常 1入库失败待重试(056) |
| `inbound_pending_reason` | varchar(500) | YES |  | NULL |  | 入库失败原因(056) |
| `order_status` | tinyint | YES | MUL | 0 |  | 订单状态: 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期 |
| `approval_status` | tinyint | YES |  | 0 |  | 审批状态: 0草稿/1待审批/2已批准/3已驳回/4已取消 |
| `approver_id` | bigint | YES |  | NULL |  | 审批人ID |
| `approver_name` | varchar(100) | YES |  | NULL |  | 审批人姓名 |
| `approval_time` | datetime | YES |  | NULL |  | 审批时间 |
| `approval_remark` | varchar(500) | YES |  | NULL |  | 审批备注 |
| `priority` | varchar(20) | NO | MUL | NULL |  | 优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急 |
| `dispatch_team_id` | bigint | YES |  | NULL |  | 负责班组(部门ID) |
| `dispatch_team_name` | varchar(100) | YES |  | NULL |  | 负责班组名称 |
| `dispatch_leader_id` | bigint | YES |  | NULL |  | 工单负责人(用户ID) |
| `dispatch_leader_name` | varchar(64) | YES |  | NULL |  | 工单负责人姓名 |
| `department_id` | bigint | YES | MUL | NULL |  | 生产部门ID |
| `department_name` | varchar(100) | YES |  | NULL |  | 生产部门名称 |
| `material_cost` | decimal(18,4) | YES |  | 0.0000 |  | 材料成本 |
| `labor_cost` | decimal(18,4) | YES |  | 0.0000 |  | 人工成本 |
| `total_cost` | decimal(18,4) | YES |  | 0.0000 |  | 总成本 |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建者 |
| `create_time` | datetime | YES | MUL | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新者 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |
| `remark` | varchar(500) | YES |  | NULL |  | 备注 |
| `material_status` | tinyint | YES |  | 0 |  | 领料状态:0未领料/1待发料/2已领料 |
| `rework_flag` | tinyint | YES |  | 0 |  | 返工标记：0正常 1质检FAIL待返工(053) |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `order_id` |
| `uk_order_no` | 是 | `order_no` |
| `idx_order_type` | 否 | `order_type` |
| `idx_order_status` | 否 | `order_status` |
| `idx_product_id` | 否 | `product_id` |
| `idx_plan_date` | 否 | `plan_start_date` , `plan_end_date` |
| `idx_priority` | 否 | `priority` |
| `idx_create_time` | 否 | `create_time` |
| `idx_parent_order` | 否 | `parent_order_id` |
| `idx_sales_order` | 否 | `sales_order_id` |
| `idx_production_order_type_status` | 否 | `order_type` , `order_status` , `plan_start_date` |
| `idx_production_order_priority_date` | 否 | `priority` , `plan_start_date` , `plan_end_date` |
| `idx_production_order_department` | 否 | `department_id` , `order_status` , `create_time` |

---

## sys_user  `辅助基础表`

**表注释**：用户表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `user_id` | bigint | NO | PRI | NULL | auto_increment | 用户ID |
| `user_name` | varchar(50) | NO | UNI | NULL |  | 用户名 |
| `nick_name` | varchar(50) | YES |  |  |  | 昵称 |
| `user_type` | varchar(2) | YES |  |  |  | 用户类型 |
| `email` | varchar(100) | YES |  |  |  | 邮箱 |
| `phone` | varchar(20) | YES |  |  |  | 手机号 |
| `sex` | char(1) | YES |  | 0 |  | 性别（0男 1女 2未知） |
| `avatar` | text | YES |  | NULL |  | 头像 |
| `password` | varchar(100) | NO |  | NULL |  | 密码 |
| `salt` | varchar(20) | YES |  |  |  | 盐值 |
| `status` | tinyint | YES |  | 0 |  | 状态（0正常 1停用） |
| `del_flag` | char(1) | YES |  | 0 |  | 删除标志（0正常 2删除） |
| `login_ip` | varchar(50) | YES |  |  |  | 最后登录IP |
| `login_date` | datetime | YES |  | NULL |  | 最后登录时间 |
| `create_by` | varchar(50) | NO |  |  |  | 创建者 |
| `create_time` | datetime | YES |  | NULL |  | 创建时间 |
| `update_by` | varchar(50) | NO |  |  |  | 更新者 |
| `update_time` | datetime | YES |  | NULL |  | 更新时间 |
| `remark` | varchar(500) | YES |  |  |  | 备注 |
| `dept_id` | bigint | YES |  | NULL |  | 部门Id |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `user_id` |
| `idx_user_name` | 是 | `user_name` |

---

## sys_dept  `辅助基础表`

**表注释**：部门表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `dept_id` | bigint | NO | PRI | NULL | auto_increment | 部门ID |
| `parent_id` | bigint | YES | MUL | 0 |  | 父部门ID |
| `dept_name` | varchar(30) | NO |  | NULL |  | 部门名称 |
| `order_num` | int | YES |  | 0 |  | 显示顺序 |
| `leader` | varchar(20) | YES |  | NULL |  | 负责人 |
| `phone` | varchar(11) | YES |  | NULL |  | 联系电话 |
| `email` | varchar(50) | YES |  | NULL |  | 邮箱 |
| `status` | char(1) | YES |  | 0 |  | 状态（0正常 1停用） |
| `del_flag` | char(1) | YES |  | 0 |  | 删除标志 |
| `create_by` | bigint | NO |  | 1 |  | 更新者 |
| `create_time` | datetime | YES |  | NULL |  | 创建时间 |
| `update_by` | bigint | NO |  | 1 |  | 更新者 |
| `update_time` | datetime | YES |  | NULL |  | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `dept_id` |
| `idx_parent_id` | 否 | `parent_id` |

---

## engineering_standard_process  `辅助基础表`

**表注释**：产品标准工序表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `process_id` | bigint | NO | PRI | NULL | auto_increment | 工序ID |
| `process_code` | varchar(50) | NO | UNI | NULL |  | 工序编码 |
| `process_name` | varchar(100) | NO |  | NULL |  | 工序名称 |
| `process_type` | varchar(20) | NO | MUL | NULL |  | 工序类型：PRINTING印刷/CUTTING模切/LAMINATING贴合/TESTING测试/PACKAGING包装 |
| `process_category` | varchar(20) | YES |  | NULL |  | 工序类别：PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量 |
| `standard_labor_hours` | decimal(10,2) | YES |  | 0.00 |  | 标准人工工时(小时) |
| `standard_machine_hours` | decimal(10,2) | YES |  | 0.00 |  | 标准机器工时(小时) |
| `process_param_template` | json | YES |  | NULL |  | 工艺参数模板（JSON格式） |
| `skill_requirement` | varchar(500) | YES |  | NULL |  | 技能要求 |
| `equipment_type` | varchar(100) | YES |  | NULL |  | 设备类型 |
| `quality_standard` | varchar(500) | YES |  | NULL |  | 质量标准 |
| `description` | varchar(500) | YES |  | NULL |  | 工序说明 |
| `is_enabled` | tinyint(1) | YES | MUL | 1 |  | 是否启用：0否 1是 |
| `display_order` | int | YES | MUL | 0 |  | 显示顺序 |
| `icon` | varchar(64) | YES |  |  |  | 图标 |
| `has_index` | tinyint(1) | NO |  | 0 |  | 是否带下标：0-不带,1-带 |
| `create_by` | varchar(64) | YES |  | NULL |  | 创建者 |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| `update_by` | varchar(64) | YES |  | NULL |  | 更新者 |
| `update_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `process_id` |
| `uk_process_code` | 是 | `process_code` |
| `idx_process_type` | 否 | `process_type` |
| `idx_is_enabled` | 否 | `is_enabled` |
| `idx_display_order` | 否 | `display_order` |
| `idx_standard_process_type_category` | 否 | `process_type` , `process_category` , `is_enabled` |

---

## production_equipment  `辅助基础表`

**表注释**：设备管理

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `equipment_id` | bigint | NO | PRI | NULL | auto_increment | 设备ID |
| `equipment_no` | varchar(50) | NO | UNI | NULL |  | 设备编号 |
| `equipment_name` | varchar(200) | NO |  | NULL |  | 设备名称 |
| `equipment_type` | varchar(50) | YES |  | NULL |  | 设备类型 |
| `model` | varchar(100) | YES |  | NULL |  | 型号规格 |
| `department` | varchar(100) | YES |  | NULL |  | 所属部门 |
| `location` | varchar(200) | YES |  | NULL |  | 安装位置 |
| `status` | tinyint | NO |  | 0 |  | 设备状态: 0待机/1运行中/2维护中/3故障中 |
| `utilization` | decimal(5,2) | YES |  | 0.00 |  | 利用率(%) |
| `last_maintenance` | datetime | YES |  | NULL |  | 上次维护时间 |
| `next_maintenance` | datetime | YES |  | NULL |  | 下次维护时间 |
| `remark` | varchar(500) | YES |  | NULL |  | 备注 |
| `del_flag` | char(1) | YES |  | 0 |  |  |
| `create_by` | varchar(64) | YES |  | NULL |  |  |
| `create_time` | datetime | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |  |
| `update_by` | varchar(64) | YES |  | NULL |  |  |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `equipment_id` |
| `uk_equipment_no` | 是 | `equipment_no` |

---

## sys_menu  `权限表`

**表注释**：菜单表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `menu_id` | bigint | NO | PRI | NULL | auto_increment | 菜单ID |
| `menu_name` | varchar(50) | NO |  | NULL |  | 菜单名称 |
| `parent_id` | bigint | YES |  | 0 |  | 父菜单ID |
| `order_num` | int | YES |  | 0 |  | 显示顺序 |
| `path` | varchar(200) | YES |  |  |  | 路由地址 |
| `component` | varchar(255) | YES |  | NULL |  | 组件路径 |
| `query` | varchar(255) | YES |  | NULL |  | 路由参数 |
| `is_frame` | char(1) | YES |  | 1 |  | 是否为外链（0是 1否） |
| `is_cache` | char(1) | YES |  | 0 |  | 是否缓存（0缓存 1不缓存） |
| `menu_type` | char(1) | YES |  |  |  | 菜单类型（M目录 C菜单 F按钮） |
| `visible` | char(1) | YES |  | 0 |  | 显示状态（0显示 1隐藏） |
| `status` | char(1) | YES |  | 0 |  | 状态（0正常 1停用） |
| `perms` | varchar(100) | YES |  | NULL |  | 权限标识 |
| `icon` | varchar(100) | YES |  | # |  | 菜单图标 |
| `ancestors` | varchar(200) | YES |  | NULL |  | 祖级列表 |
| `route_name` | varchar(100) | YES |  | NULL |  | 路由名称 |
| `requires_auth` | char(1) | YES |  | 1 |  | 是否需要认证（1是 0否） |
| `redirect` | varchar(255) | YES |  | NULL |  | 重定向路径 |
| `sort` | int | YES |  | 0 |  | 排序值 |
| `create_by` | varchar(50) | NO |  | admin |  | 创建者 |
| `create_time` | datetime | YES |  | NULL |  | 创建时间 |
| `update_by` | varchar(50) | NO |  | admin |  | 更新者 |
| `update_time` | timestamp | YES |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 更新时间 |
| `remark` | varchar(500) | YES |  |  |  | 备注 |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `menu_id` |

---

## sys_role_menu  `权限表`

**表注释**：角色和菜单关联表

### 字段

| 字段 | 类型 | 可空 | 键 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|---|
| `role_id` | bigint | NO | PRI | NULL |  | 角色ID |
| `menu_id` | bigint | NO | PRI | NULL |  | 菜单ID |

### 索引

| 索引名 | 唯一 | 字段 |
|---|---|---|
| `PRIMARY` | 是 | `role_id` , `menu_id` |
| `idx_sys_role_menu_role` | 否 | `role_id` |

---
