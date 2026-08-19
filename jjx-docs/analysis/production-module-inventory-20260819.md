# JJX 生产管理 V1 现状盘点报告

> 版本：v1.0
> 日期：2026-08-19
> 性质：只读分析（未修改任何代码/数据库/菜单/配置，未提交 Git）
> 依据：当前真实代码（jjx-server/jjx-web）+ 数据库（jjx_erp_db）实际表结构与数据
> 说明：dispatch-redesign-v2.md 仅作为已有设计资料纳入对照，不作为实施方案

---

## 0. Executive Summary

当前生产模块具备完整的**骨架**：工单（计划/工单双态）→ 工序执行（按工艺路线自动生成）→ 派工（工序级）→ 质检（IQC/IPQC/OQC/FQC 混合）→ 追溯（空壳）→ 设备（纯档案），并有完工自动质检、自动入库、自动核算人工成本、销售订单回写等联动。

但存在以下结构性问题：

1. **派工模型最薄弱**：链数据存在 operators JSON 里，无结构化节点；assign 一个接口承担"新建/追加级别/改派"三义；硬编码 1-3 级；状态枚举与表注释不一致。
2. **报工无独立模型**：没有 WorkReport 表，"报工"= 直接更新 execution 数量字段 + 前端调 edit 接口，与派工末级无归属关系。
3. **追溯是空壳**：production_trace_log 表 0 行数据，全项目无任何写入方（只有查询），正/反追溯 API 查空表。
4. **职责重复**：production-operation 页面与 execution 页面调用同一套 API（operationExecutionApi），api/production/operation.ts 是断链死代码（后端无 /production/operation 路由）。
5. **状态机混乱**：同一含义多套值（如"执行中"在 order=6/execution=2/dispatch=3），execution_status 一个字段承担计划/执行/质量/异常/确认多维语义。
6. **质检类型不一致**：表注释与 getTypeName 只认 IQC/IPQC/OQC，但完工质检门写死查 "FQC"（getTypeName 无 FQC 映射，会原样显示）。
7. **组织架构能力已够**：sys_dept 支持 4 层树（公司→生产中心→车间→班组），leader 字段 + underlings 递归已实现，可支撑多级派工；但存在硬编码（生产中心 id=5、派工级别 1-3 上限）。

---

## 1. 生产模块整体架构

### 1.1 后端（jjx-server/src/main/java/com/jjx/production/）

| 层 | 文件 |
|---|---|
| Controller | DispatchController、EquipmentController、ProductionCostController、ProductionOperationExecutionController、ProductionOperationRecordController、ProductionOrderController、ProductionReportController、ProductionTraceController、QualityInspectionController、ToolingController |
| Service | DispatchService、EquipmentService、ProductionOperationExecutionService、ProductionOperationRecordService、ProductionOrderService、ProductionTraceService、QualityInspectionService、ToolingService（+ impl/ 8 个实现） |
| Entity | ProductionDispatch、ProductionDispatchLog、ProductionEquipment、ProductionOperationExecution、ProductionOperationRecord、ProductionOrder、ProductionQualityInspection、ProductionQualityInspectionItem、ProductionTooling、ProductionTraceLog |
| DTO | DispatchAssignDTO、DispatchQueryDTO、EquipmentQueryDTO、InspectionItemDTO、ProductionOperationExecutionCreateDTO/UpdateDTO/QueryDTO、ProductionOperationRecordCreateDTO/UpdateDTO/QueryDTO、ProductionOrderCreateDTO/UpdateDTO/QueryDTO、QualityInspectionCreateDTO/UpdateDTO/QueryDTO、ToolingDTO/ImportDTO/QueryDTO、TraceQueryDTO、ConvertPlanToWorkOrdersDTO |
| VO | DispatchVO、InspectionItemVO、OrderStatisticsVO、ProductionOperationExecutionVO、ProductionOperationRecordVO、ProductionOrderExportVO、ProductionOrderVO、QualityInspectionVO、ToolingVO、TraceVO |
| Mapper | ProductionDispatchLogMapper、ProductionDispatchMapper、ProductionEquipmentMapper、ProductionOperationExecutionMapper、ProductionOperationRecordMapper、ProductionOrderMapper、ProductionQualityInspectionItemMapper、ProductionQualityInspectionMapper、ProductionToolingMapper、ProductionTraceLogMapper |
| 枚举 | DispatchStatusEnum、ExecutionStatusEnum、OrderStatusEnum、OrderTypeEnum、RecordTypeEnum、ToolingStatusEnum、ToolingTypeEnum |
| 其他 | ProductionOrderConverter、ProductionOrderTimeoutTask |

依赖的外部模块：engineering（工艺路线/BOM）、inventory（领料出库/完工入库）、sales（销售订单回写）、system（部门/用户/权限）。

### 1.2 前端（jjx-web/src/）

| 类别 | 内容 |
|---|---|
| 页面 views/production/ | order/（index + 11 个组件 + 3 composables + utils）、dispatch/index.vue、execution/index.vue、quality/（index/report/print）、trace/index.vue、equipment/index.vue、production-operation/index.vue、report/index.vue、cost/index.vue、tooling/（index + ToolingFormDialog） |
| API api/production/ | order.ts、dispatch.ts、operationExecution.ts、operation.ts、quality.ts、trace.ts、equipment.ts、cost.ts、report.ts、tooling.ts |
| 类型 types/production/ | order.ts、operationExecution.ts、operation.ts |
| 枚举 enums/production/ | WorkOrderEnum.ts（订单/执行/记录类型枚举）、ToolingEnum.ts |
| 公共组件 | components/OperatorChain/index.vue、components/OperatorPicker/index.vue、components/TraceTimeline/index.vue |

### 1.3 数据库表（10 张生产相关）

production_order、production_operation_execution、production_operation_record、production_dispatch、production_dispatch_log、production_quality_inspection、production_quality_inspection_item、production_trace_log、production_equipment、production_tooling

### 1.4 菜单/权限（sys_menu）

生产管理（menu_id=43, path=/production）一级菜单 6 个可见 + 4 个隐藏（2026-08-19 收敛后）：
可见：生产订单(45)、派工管理(261)、工序执行(48)、质检管理(264)、生产追溯(52)、设备管理(49)
隐藏：操作记录(51)、生产报表(77)、成本核算(76)、工装模具档案(253)（visible='1'，路由/权限保留）

### 1.5 数据量（2026-08-19 实测）

production_order=4、production_operation_execution=9、production_operation_record=0、production_dispatch=3、production_quality_inspection=0、production_trace_log=0、production_equipment=0、engineering_routing=1、engineering_routing_item=3、engineering_bom=1

---

## 2. 生产订单现状

**实体/表**：ProductionOrder / production_order（ProductionOrder.java）

**核心字段**：
- 主键 order_id；工单号 order_no；trace_id（链路追踪 UUID，DEV-568）
- 类型 order_type：PLAN 生产计划 / WORK_ORDER 生产工单（实体注释写 WORK_ORDER，OrderTypeEnum 另有 ORDER/TRIAL/REWORK/SAMPLE/REPAIR/SPARE/URGENT 7 种，枚举与实体注释不一致）
- 产品 product_id/product_code/product_name/product_spec/product_unit
- 数量：planned_quantity（计划）、completed_quantity（工序合格汇总，仅进度展示）、finished_quantity（成品完工=最后一道工序合格数，052 口径）、remaining_quantity
- 时间：plan_start_date/plan_end_date（LocalDate）、actual_start_time/actual_end_time
- 状态：order_status（0-11 共 12 态）+ approval_status（PENDING/APPROVED/REJECTED/CANCELLED 独立审批字段）
- 组织/负责人：department_id/name（生产部门）、dispatch_team_id/name（负责班组=部门ID）、dispatch_leader_id/name（工单负责人）
- 其他：priority、material_status（领料状态 0/1/2）、rework_flag（返工标记）、completed_by、quality_inspection_id、inbound_pending_flag/reason、material_cost/labor_cost/total_cost、sales_order_id/no、parent_order_id、routing_id/code、bom_id/code

**与外部对象关系**：
| 对象 | 关系 | 依据 |
|---|---|---|
| 工序 | 1—N：production_operation_execution.order_id；工单创建/转单时按工艺路线批量生成 | ProductionOrderServiceImpl.generateOperationExecutions() |
| 工艺路线 | routing_id → engineering_routing | ProductionOrder 字段 |
| BOM | bom_id → engineering_bom | ProductionOrder 字段 |
| 派工 | 1—N：production_dispatch.order_id（工单级冗余）；另 production_dispatch 有 order_id+execution_id | DispatchServiceImpl |
| 人员 | dispatch_leader_id（负责人）、completed_by（完工留痕）、create_by | ProductionOrder 字段 |
| 班组 | dispatch_team_id=sys_dept.dept_id | ProductionOrder 字段 |
| 设备 | **无工单级设备字段**（设备在工序执行/派工层） | — |
| 报工 | 无独立报工表；completed/finished_quantity 由工序执行完成时汇总回写 | updateOrderCompletedQuantity() |
| 质检 | quality_inspection_id（完工自动建 FQC 质检单并回填）；质检 FAIL 置 rework_flag=1 | completeOrder()、QualityInspectionServiceImpl.update() |
| 追溯 | trace_id（UUID 字符串，非批次）；production_trace_log.order_id 弱关联 | — |
| 批次 | **无批次字段**（batch_no 只在 trace_log 上有，且无写入方） | — |

---

## 3. 工序模型现状

**结论：模板 + 实例双层模型已存在，且为"模板 → 实例"单向复制。**

- **模板层**：
  - engineering_standard_process（标准工序，ProductStandardProcess，process_id）
  - engineering_routing（工艺路线，含 routing_id/product_id/version/approveStatus/processCount）
  - engineering_routing_item（路线工序项：routing_id + process_id + process_order + customLaborHours/customMachineHours/standardWage/customProcessParams/processCategory/majorCategory/indexNumber/precondition 前置条件/isOptional 可选项/groupId+groupOrder 工序组）
- **实例层**：production_operation_execution（工单下的工序实例）
- **工单如何产生工序**：计划转工单（convertPlanToWorkOrders → generateOperationExecutions）或直接创建工单后，按 routing_id 查 engineering_routing_item，按 process_order 升序逐条生成 execution 实例。**首道工序直接置为 EXECUTING（激活），其余 PENDING**（049 定稿）。
- **顺序**：process_order（int，路线内序号）
- **前后置依赖**：模板层有 precondition/preconditionDisplay（前置条件文本）与 isOptional（可选项）字段，但**代码中未见依赖校验/自动跳过逻辑**（ExecutionStatusEnum 有 SKIPPED 但无自动跳过实现）
- **并行工序**：不支持。工序按 process_order 线性生成、线性展示（execution 页表格 + dispatch 列表按 process_order 排序）
- **计划数量**：execution 无计划数量字段；计划数量在工单层 planned_quantity；execution 有 input_quantity（投入）
- **已完成数量**：execution.qualified_quantity（合格数）+ output_quantity（产出）
- **工序状态**：execution_status（0 待执行/1 准备中/2 执行中/3 已暂停/4 已完成/5 已跳过/6 已取消/7 已超期/8 异常中/9 待确认）
- **如何进入 execution**：generateOperationExecutions 批量插入；另有手建接口 POST /production/operation-execution（createExecution）

---

## 4. 派工管理现状（重点）

**实体/表**：ProductionDispatch / production_dispatch（uk_execution 唯一键=一工序一单）；ProductionDispatchLog / production_dispatch_log（ASSIGN/REASSIGN/REJECT/START/COMPLETE 流水）

**当前模型**：**工序 → 班组/设备/执行人（多级链）**。核心是"工序执行记录（execution_id）→ 一张派工单"，执行人链存 operators JSON：`[{userId,userName,level}]`，level 1-3。

**关键字段**：dispatch_id、order_id/order_no、execution_id（唯一）、process_name/process_order、team_id/team_name、equipment_id/equipment_name、operators(JSON)、assigned_by/assigned_by_name、assign_time、status、reject_reason、re_dispatch_count、remark

**能力矩阵**：
| 能力 | 现状 | 依据 |
|---|---|---|
| 一单多次派工 | ❌ 不支持（uk_execution 一工序一单） | 表唯一键 |
| 数量拆分/部分派工 | ❌ 不存在（整道工序派工，无数量维度） | 表结构无数量字段 |
| 剩余待派数量 | ❌ 不存在 | — |
| 多人员 | ✅ operators JSON 数组 | DispatchAssignDTO.operatorIds |
| 多设备 | ❌ 单设备（equipment_id） | 表结构 |
| 多级组织 | ⚠️ 部分：level 1-3 硬编码上限，链存在 JSON | appendLevel() 校验 lv<1||lv>3 |
| 二次派工 | ✅ re_dispatch_count 计数 | appendLevel() |
| 转派 | ✅ transferFrom（链上执行人转给手下，级别+1） | DispatchAssignDTO.transferFrom |
| 撤回 | ❌ 不存在 | — |
| 取消 | ❌ 不存在（只有退回 reject） | — |
| 接单 | ❌ 不存在（无接单动作，只有开始 start） | — |
| 开工 | ✅ start（仅 ASSIGNED/REJECTED 可开始） | DispatchServiceImpl.start() |
| 完工 | ✅ complete | DispatchServiceImpl.complete() |
| 派工历史 | ✅ production_dispatch_log（含操作人/时间/内容） | addLog() |
| parentId/parentDispatchId/parentAllocationId 父子结构 | ❌ 不存在（只有 level 序号，无 parent 指针） | 表结构 |
| 操作人/操作时间 | ✅ assigned_by/assign_time + log.operator_id/operator_name/create_time | 表结构 |

**状态**：DispatchStatusEnum：0 待派工/1 已派班组/2 已派工/3 执行中/4 已完成/5 已退回。**表注释仍是旧的 0-4 五态（0待派工 1已派工 2执行中 3已完成 4已退回）**，与枚举不一致（2026-08-13 加了"已派班组"但表注释未同步）。

**与 execution 联动**：start/complete 时 syncByExecution 双向同步状态（2=执行中/4=已完成）。

**2026-08-19 已实现"逐级下放"雏形**：派工资格=超管/生产负责人/被派工过；执行人候选=自己+手下（underlings 递归部门树）；班组不再独立选择（自动=第1级执行人所属部门）。但链条仍是 JSON、仍 3 级封顶、assign 接口仍是多义。

---

## 5. 《dispatch-redesign-v2.md》与当前实现对照

（dispatch-redesign-v2.md：2026-08-19 重新设计稿，作为已有设计资料）

| 文档提出 | 当前是否已实现 | 与现状是否一致 | 判定 |
|---|---|---|---|
| 四动作语义互斥：指派=建链、下派=加一级、改派=同级换人、退回=回退一级 | ❌ 未实现：assign 一个接口承担新建/追加级别/改派；前端"改派/转派"都调 assign | 不一致（现实现混用） | 新增设计 |
| 派工链节点表 production_dispatch_node（每级：user/parent_node_id/status/assigned_by/assigned_at） | ❌ 未实现：链存 operators JSON，无 parent 指针 | 不一致 | 新增设计 |
| 链深度不封顶 | ❌ 未实现：appendLevel 硬编码 1-3 | 不一致 | 新增设计 |
| 链不变量：一条链仅一个 ACTIVE 节点=末级=当前待办人 | ❌ 未实现：无 ACTIVE 概念，状态在整单上 | 不一致 | 新增设计 |
| 状态五态（砍掉"已派班组"） | ⚠️ 枚举 6 态含"已派班组(1)"，且表注释与枚举不一致 | 部分一致（文档想砍的正是现存的） | 设计修正 |
| 列表按工单聚合 + 当前待办人列 | ❌ 未实现：现列表为工序行视图（execution LEFT JOIN dispatch），链列只显示第1级+级数 | 不一致 | 新增设计 |
| 退回语义：末级退回→上级重新 ACTIVE；第1级退回→整单待派工 | ⚠️ 现 reject 直接置 5 已退回（整单回待派工语义），无逐级回退 | 部分一致 | 新增设计 |
| 下派对象=自己的手下（组织树约束） | ✅ 已实现：underlings()/myPersons() 按 leader 递归部门树 | 一致 | 可复用 |
| 班组=执行人所属部门自动推导 | ✅ 已实现（2026-08-19） | 一致 | 可复用 |
| 派工资格=当前 ACTIVE 节点的人 | ⚠️ 现为"超管/生产负责人/被派工过（LIKE 匹配 JSON）" | 不一致 | 设计修正 |
| 接口拆分 assign/transfer/reassign | ❌ 未实现：只有 assign/batch-assign | 不一致 | 新增设计 |
| 链展示组件（A＞B＞C★）与时间线详情 | ⚠️ OperatorChain 组件已存在（compact 显示+弹窗时间线雏形），但数据源是 JSON 非节点表 | 部分一致 | 改造复用 |

**与整体生产模型可能冲突的点**：
1. 文档假设"报工挂链末级"——当前报工直接改 execution 数量，与派工链无归属关系；若要挂末级需报工模型改造（见 §7）。
2. 文档假设"工单级责任字段（dispatch_team/leader）砍掉"——当前 production_order 上这些字段仍在且被 OrderTableActions/updateOrderTeam 使用。
3. 文档的"链=组织树下行路径"依赖 sys_dept.leader 字段语义（leader 存 userName），当前已有但数据是演示数据。
4. 文档未覆盖 execution 联动的状态同步（syncByExecution），V1 设计需保留。

---

## 6. 组织架构能力

**实体/表**：SysDept / sys_dept；SysUser / sys_user；SysUserRole、SysRole

**SysDept 字段**：dept_id、parent_id、ancestors（路径）、dept_name、order_num、leader（部门负责人 userName）、status、del_flag
**SysUser 字段**：user_id、dept_id、user_name、nick_name、user_type、status、del_flag

**真实数据（4 层树，14 部门）**：
JJX公司(1) → 生产中心(5, prod_manager) → 印刷车间(6, print_mgr)/冲型车间(9, punch_mgr)/组装车间(12, assembly_mgr) → 印刷一组(7)/印刷二组(8)/冲型一组(10)/冲型二组(11)/组装一组(13)/组装二组(14)；另有研发部/市场部/办公室/采购部。

**能力判定**：
- 任意层级：✅ parent_id + ancestors 支持；已实际用到 4 层
- 组织树 API：✅ SysDeptController（treeselect 等）+ 派工侧 my-depts/underlings/team-persons/my-persons
- 支撑"生产调度→工厂/车间→班组→人员"多级派工：✅ 结构上可行（leader 字段 + underlings 递归已实现）
- **写死问题**：
  1. isProductionManager 硬编码 `sysDeptMapper.selectById(5L)`（生产中心 id=5）
  2. 派工链硬编码 level 1-3（业务层级写死，非组织层级）
  3. underlings 依赖 `sys_dept.leader = 当前用户 userName` 语义（leader 存用户名而非 userId，脆弱）

---

## 7. 工序执行与报工现状（execution）

**实体/表**：ProductionOperationExecution / production_operation_execution

**能力矩阵**：
| 能力 | 现状 | 依据 |
|---|---|---|
| 接单 | ❌ 无接单动作 | — |
| 开工 | ✅ start（状态校验后置 EXECUTING，联动派工） | startExecution() |
| 暂停 | ✅ pause | pauseExecution() |
| 恢复 | ⚠️ 枚举/记录类型有 RESUME，但**无恢复 API**（只有 start/pause/quality-check/complete/cancel） | ExecutionController |
| 报工 | ⚠️ 无独立报工表：前端"记录"弹窗调 operationExecutionApi.edit() 更新 actualCompletedQuantity/actualQualifiedQuantity/actualDefectiveQuantity/actualLaborHours/actualMachineHours | execution/index.vue submitRecord |
| 部分报工 | ⚠️ 可多次 edit 修改数量（无增量约束，直接覆盖） | updateExecution() |
| 完工 | ✅ complete（数量冻结：工单已完工禁止再报工） | completeExecution() |
| 良品数量 | ✅ qualified_quantity | 表字段 |
| 不良数量 | ✅ defective_quantity + defective_reason | 表字段 |
| 实际工时 | ✅ actual_labor_hours / actual_machine_hours | 表字段 |
| 人员 | ✅ operator_id/operator_name | 表字段 |
| 班组 | ❌ 无字段 | — |
| 设备 | ✅ equipment_id/code/name | 表字段 |
| 班次 | ❌ 无字段 | — |
| 异常 | ⚠️ 无独立异常表/字段；execution_status=8 异常中 + quality_check_result JSON + record(ISSUE) | 表字段 |
| 操作历史 | ✅ production_operation_record（START/PAUSE/RESUME/COMPLETE/QUALITY/ISSUE/PARAM/STATUS 等 16 种） | RecordTypeEnum |

**计划/实际数据分离**：**不分离**。execution 同一张表同时放 planned_start/end_time 与 actual_start/end_time、计划参数(customProcessParams)与实际参数(actualProcessParams)。模板参数来自 engineering_routing_item，生成时复制进 execution。

**WorkReport/ProductionReport 等价模型**：
- WorkReport：❌ 不存在独立报工表
- ProductionReport：⚠️ 只是报表统计接口（/production/report/output|efficiency|quality），非报工数据表
- ExecutionRecord/OperationRecord：✅ production_operation_record 是执行事件记录（时间线），非数量报工

**关键结论**：报工 = 改 execution 数量字段 + 前端"记录"弹窗；报工无独立表、无增量校验、无派工级归属。

---

## 8. production-operation 定位

**前端页面**：views/production/production-operation/index.vue（已隐藏菜单）
**真实 API 调用**：`import { operationExecutionApi } from '@/api/production/operationExecution'`，调用 list/start/complete/remove——**与 execution 页面同一套 API**。
**api/production/operation.ts**：请求 /production/operation/*（list/detail/create/update/delete/start/complete/cancel/stats/options/export/batch-status），**后端无 /production/operation 路由**（只有 /production/operation-record 与 /production/operation-execution），且**前端无任何页面引用该文件** → **死代码/断链**。

**结论**：
- 它保存/展示的就是**工序执行（execution）数据**（列：操作编号/工单/产品/工序/操作员/设备/计划数量/已完成/操作状态/开始/结束时间）
- 与 execution：**职责重复**（两个页面同一数据源）
- 与 trace：无直接关系（trace 是独立日志表，0 数据）
- 与 dispatch：无直接关系
- 建议：V1 中并入 execution（工序执行详情 + Timeline），删除死代码 operation.ts；页面定位与菜单收敛决定一致（"工序执行详情和生产追溯中的操作记录/Timeline"）

---

## 9. 质检现状（quality）

**实体/表**：ProductionQualityInspection / production_quality_inspection；ProductionQualityInspectionItem / production_quality_inspection_item

**字段**：inspection_no、inspection_type、order_id、material_id、product_id、inspector、inspect_time、result（pending/pass/fail）、total_qty/pass_qty/fail_qty、defect_desc（文本）、remark + item 明细（check_item/standard/actual_value/result/remark）

**类型**：表注释=IQC 来料检/IPQC 过程检/OQC 成品检；getTypeName() 映射 IQC/IPQC/OQC；但**完工质检门写死 inspection_type="FQC"**（ProductionOrderServiceImpl 416/930 行），getTypeName 无 FQC 映射 → FQC 单会原样显示"FQC"。
**状态**：无独立状态字段，result 即状态（pending 待检/pass 合格/fail 不合格）
**检验任务**：❌ 无任务概念（直接建单）
**返工/报废/让步**：返工 ⚠️ 有联动（FAIL→工单 rework_flag=1，通过→清除）；报废/让步 ❌ 不存在
**与生产订单**：order_id 关联；完工自动建 FQC 单并回填 production_order.quality_inspection_id
**与工序**：❌ 无工序/执行关联字段（无法定位是哪道工序的检验）
**与报工**：间接——完工门要求 finished_quantity>0（最后工序合格数）
**与批次**：❌ 无批次字段
**当前模式**：**混合**（IQC 来料/IPQC 过程/OQC+FQC 成品四类并存），但数据 0 行、前端"新建检验"是 TODO（console.log 占位）、"检验标准"按钮也是 TODO
**API**：page/get/create/update/delete/statistics/export-pdf/export-excel

---

## 10. 生产追溯现状（trace）

**实体/表**：ProductionTraceLog / production_trace_log
**字段**：trace_type（MATERIAL 原料/ORDER 工单/PRODUCT 产品）、trace_code、batch_no、order_id、product_id、material_id、operation（inbound/outbound/start/complete/inspect）、operator、operate_time、detail(JSON)
**查询维度**：追溯编码、追溯类型、批次号、工单ID（page + forward 正追溯 + backward 反追溯）

**关键发现**：
- **全项目没有任何写入 production_trace_log 的代码**（grep 全源码只有 select/查询，无 insert）
- **表 0 行数据**

**主链串联判定**：
| 连接点 | 状态 |
|---|---|
| 工单→工序 | ✅ 打通（order_id → execution） |
| 工序→派工 | ✅ 打通（execution_id ↔ dispatch） |
| 派工→执行 | ✅ 打通（dispatch start/complete ↔ syncByExecution） |
| 执行→报工 | ⚠️ 部分（无报工表，数量在 execution 上） |
| 报工→质检 | ⚠️ 部分（完工自动建 FQC 单，但无执行级关联） |
| 质检→完成 | ✅ 打通（FQC 门 + 完工自动入库） |
| **追溯整链** | ❌ **未打通**（trace_log 无写入方，0 数据；且 trace_code 需要物料编码/工单号/产品编码，无批次/SN 数据源） |

**结论**：追溯是"壳"——表结构、API、页面都有，但没有任何业务代码写数据，实际追溯不了任何东西。

---

## 11. 设备管理现状（equipment）

**实体/表**：ProductionEquipment / production_equipment
**字段**：equipment_id、equipment_no、equipment_name、equipment_type、model、department（**文本，非 dept_id 外键**）、location、status（0 待机/1 运行中/2 维护中/3 故障中）、utilization、last_maintenance、next_maintenance、remark
**与工序**：execution.equipment_id/code/name 冗余引用
**与派工**：dispatch.equipment_id/name 冗余引用
**与执行**：同 execution
**点检/保养/维修/故障/OEE/备件**：仅 last/next_maintenance 两个维护时间字段（前端有"维护计划"按钮）；点检/维修记录/故障记录/OEE/备件 ❌ 全部不存在
**定位**：**生产资源档案**（基础 CRUD + 两个维护时间字段），未发展为设备管理系统。数据 0 行。

---

## 12. 数据库关系（简化 ER，基于真实外键/代码关联）

```
engineering_standard_process（标准工序模板）
      ↑ process_id
engineering_routing_item（工艺路线工序项）──routing_id──▶ engineering_routing（工艺路线）
      │ process_id                                                    ↑ routing_id
      │                                                               │
      ▼                                                               │
production_order（工单/计划）──bom_id──▶ engineering_bom（BOM）
  │ order_id
  ├──▶ production_operation_execution（工序实例）  ←process_id─ standard_process
  │       │ execution_id
  │       ├──▶ production_operation_record（操作时间线）
  │       └──▶ production_dispatch（派工单，uk_execution 1:1）
  │               └──▶ production_dispatch_log（派工流水）
  ├──▶ production_quality_inspection（质检单，order_id）
  │       └──▶ production_quality_inspection_item（检验明细）
  ├──▶ production_trace_log（追溯日志，order_id/product_id/material_id 弱关联，无写入）
  ├──▶ production_equipment（设备，execution/dispatch 冗余设备名，无外键）
  └──▶ production_tooling（工装，无业务关联）

sys_dept（部门树，parent_id 自关联）──dept_id──▶ sys_user（用户）
  ▲ team_id（dispatch）/ department_id（order）/ dept_id（user）
```

**关键表与关联字段**：
- production_order.order_id ↔ production_operation_execution.order_id
- production_operation_execution.execution_id ↔ production_dispatch.execution_id（唯一）
- production_operation_execution.execution_id ↔ production_operation_record.execution_id
- production_order.order_id ↔ production_quality_inspection.order_id
- production_order.order_id ↔ production_trace_log.order_id（弱）
- production_dispatch.team_id ↔ sys_dept.dept_id；dispatch.operators(JSON) ↔ sys_user.user_id
- production_order.dispatch_leader_id ↔ sys_user.user_id；department_id/dispatch_team_id ↔ sys_dept.dept_id
- engineering_routing.routing_id ↔ engineering_routing_item.routing_id；item.process_id ↔ engineering_standard_process.process_id

---

## 13. 状态机

### 13.1 各模块真实枚举值

| 模块 | 状态值 |
|---|---|
| 生产订单 order_status | 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期（OrderStatusEnum） |
| 审批 approval_status | PENDING/APPROVED/REJECTED/CANCELLED（独立字段） |
| 工序执行 execution_status | 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认（ExecutionStatusEnum） |
| 派工 dispatch.status | 0待派工/1已派班组/2已派工/3执行中/4已完成/5已退回（DispatchStatusEnum；表注释仍为旧 0-4） |
| 报工 | 无独立状态（复用 execution_status） |
| 质检 result | pending/pass/fail（无独立状态字段） |
| 设备 status | 0待机/1运行中/2维护中/3故障中 |
| 工装 status | 0在库/1使用中/2清洗保养中/3维修中/4报废 |

### 13.2 问题清单

1. **同一含义多套值**："执行中"：order=6、execution=2、dispatch=3、equipment=1；"已完成"：order=8、execution=4、dispatch=4
2. **execution_status 一个字段承担多维度**：计划（0待执行/1准备中）+ 执行（2/3）+ 结果（4/5/6）+ 时效（7超期）+ 质量异常（8异常中）+ 确认（9待确认）——超期是计算态（isOverdue()）却混入枚举
3. **派工表注释与枚举不一致**：表注释 0-4 五态 vs 枚举 0-5 六态（"已派班组"未同步注释）
4. **质检类型不一致**：表注释/映射 IQC/IPQC/OQC vs 完工门写死 FQC
5. **不可逆/异常流转**：派工已完成禁止改派（appendLevel 抛异常）；order 完工后数量冻结（053）；order 状态流转靠 validateStatusTransition + canStart/canComplete 多处分散校验
6. **order 状态与业务事实脱节**：updateOrderStatus 直接置 COMPLETED 时 completed_quantity=planned_quantity（强行填满），与 052 口径（finishedQuantity=最后工序合格数）存在两套完工数量语义（completed_quantity 汇总 vs finished_quantity 成品）

---

## 14. API 主链（按业务链）

```
【生产订单】
POST   /production/order                              → 创建工单/计划
POST   /production/order/convert-plan-to-work-orders  → 计划转工单（同时生成工序）
GET    /production/order/page | /schedule/gantt       → 列表/排程甘特
PUT    /production/order/{orderId}/start              → 开工（自动领料出库、回写SO）
PUT    /production/order/{orderId}/pause|cancel|close → 暂停/取消/关闭
PUT    /production/order/{orderId}/complete           → 完工（质检门→自动FQC单→自动入库→成本核算）

【派工】
GET    /production/dispatch/page                      → 派工工作台（工序行视图）
GET    /production/dispatch/order/{orderId}/pending   → 工单待派工序
GET    /production/dispatch/underlings/{userId}       → 手下（转派候选）
GET    /production/dispatch/my-persons | my-depts     → 自己+手下/可管辖部门树
POST   /production/dispatch/assign                    → 指派/改派/追加级别（多义）
POST   /production/dispatch/batch-assign              → 整单批量派工
POST   /production/dispatch/{id}/reject               → 退回
POST   /production/dispatch/{id}/start|complete       → 开工/完工（联动execution）
PUT    /production/dispatch/order/{orderId}/team      → 工单级班组/负责人

【工序执行/报工】
GET    /production/operation-execution/page|order/{orderId}
PUT    /production/operation-execution/{id}/start|pause|quality-check|complete|cancel
PUT    /production/operation-execution                → 更新（=报工入口，改数量/工时）
GET/POST /production/operation-record/**             → 执行时间线记录

【质检】
GET/POST/PUT/DELETE /production/quality/**           → 检验单 CRUD（含明细）
GET    /production/quality/statistics|export-pdf|export-excel

【追溯】
GET    /production/trace/page|forward/{code}|backward/{code}

【设备】
GET/POST/PUT/DELETE /production/equipment/**          → 档案 CRUD
```

---

## 15. 页面现状（基于前端代码；浏览器 attach-only 不可用，未实测 UI）

| 页面 | 查询条件 | 表格列 | 按钮/弹窗 | 跳转 |
|---|---|---|---|---|
| 生产订单 order/index.vue | 订单编号/产品名称/订单状态/销售订单/产品编码/审批状态/计划类型/计划时间 | 数量/计划开始/计划结束/优先级/操作 | 视图切换(计划/工单/全部/甘特)、统计卡片、批量操作、OrderFormDialog(类型/产品/数量/优先级/时间/计划类型/审批人/操作员/设备/备注)、状态/删除/领料预览/生产工作卡 | →/production/dispatch?orderNo=（派工）、/inventory/outbound（领料） |
| 派工管理 dispatch/index.vue | 工单编号/工序关键字/状态 | 工单号/数量/工序/责任班组/设备/执行人链(第一级+级数)/派工主管/状态/指派时间/改派次数 | 批量派工、指派/改派弹窗、转派弹窗(由链上执行人转给手下)、退回弹窗(原因必填)、流水时间线、执行人树形选择 | — |
| 工序执行 execution/index.vue | 工单编号/工序名称/执行状态 | 工单号/工序/工艺参数/顺序/投入/产出/合格/状态/操作员/开始/完成时间 | 开始/暂停/完成/详情/记录(投入/产出/合格/不良/工时弹窗=报工) | — |
| 质检 quality/index.vue | 检验单号/类型/结果等 | 检验类型/合格率/检验时间/结果 | 新建检验(TODO)/检验标准(TODO)/质量报告/详情 | →/production/quality/report |
| 生产追溯 trace/index.vue | 追溯编码/追溯类型/批次号 | 编码/类型/操作/批次号/工单ID/操作人/时间/详情 | 正追溯→/反追溯← | — |
| 设备管理 equipment/index.vue | 编号/名称/状态 | 编号/名称/类型/部门/位置/状态/利用率/上次维护/下次维护 | 新增/编辑弹窗、详情、维护计划 | — |

---

## 16. 能力分类

### A. 可直接复用
1. 组织树 sys_dept（4 层：公司/生产中心/车间/班组，parent_id+ancestors+leader）
2. 派工侧"自己+手下"递归（underlings/myPersons/myDepts，leader 语义）
3. 工单双态模型（PLAN/WORK_ORDER + 计划转工单 + parent_order_id）
4. 按工艺路线自动生成工序实例（generateOperationExecutions，含时间均分、首道激活）
5. 完工联动链（完工质检门→自动 FQC 单→自动成品入库→人工成本核算→SO 回写）
6. 工序执行数量字段（投入/产出/合格/不良/工时）与操作时间线（operation_record 16 种类型）
7. 质检单+明细结构（check_item/standard/actual_value）
8. 设备档案 CRUD
9. 派工流水（dispatch_log）、退回原因必填、re_dispatch_count
10. 追溯表结构与正/反追溯 API（差写入方）

### B. 可在现有模型上改造
1. 派工：operators JSON → 链节点表（或至少结构化 level+parent）；assign 拆分为 assign/transfer/reassign 语义；去掉 3 级硬编码；状态注释同步
2. 报工：在 execution 上加增量报工（报工记录表或 operation_record 扩展），解决"覆盖式报工"
3. 质检：类型字典统一（IQC/IPQC/OQC/FQC 一表一处定义）；补检验标准字典（前端 TODO）
4. 追溯：给 trace_log 补写入方（工单/工序/质检/出入库事件）
5. production-operation：并入 execution 页面（Timeline 视角），删死代码 operation.ts
6. 订单状态机：统一完工数量口径（completed_quantity vs finished_quantity），去除 updateOrderStatus 强填数量

### C. 职责重复/建议合并
1. production-operation 页面 ≡ execution 页面（同一 operationExecutionApi）
2. api/production/operation.ts 断链死代码（后端无路由、前端无引用）
3. api/production/report.ts + ProductionReportController（output/efficiency/quality）与 order/execution 统计卡片重复
4. dispatch 的"已派班组(1)"状态与班组概念（2026-08-19 已砍班组独立选择）名不副实
5. 工单级责任字段（dispatch_team/leader on production_order）与工序级派工责任并存

### D. 完全缺失/未来新增
1. 独立报工模型（WorkReport）与报工增量/校验
2. 批次/SN 追溯数据源与写入（batch_no 无主）
3. 派工数量拆分/部分派工/剩余待派数量
4. 派工接单/撤回/取消、父子派工结构（parentDispatchId）
5. 工序前后置依赖校验、并行工序、自动跳过（isOptional/precondition 有字段无逻辑）
6. 班组/班次字段（execution 无）
7. 质检任务流转、缺陷字典、返工单、报废、让步接收
8. 设备点检/保养计划/维修/故障记录/OEE/备件
9. 工序级质检关联（质检单 ↔ execution_id）
10. 恢复执行 API（RESUME 有枚举无接口）

---

## 17. 后续进行生产管理 V1 设计前必须解决的关键问题

1. **派工模型定调**：工序→人（现模型）还是任务链→人（dispatch-redesign-v2 建议）？链要不要独立节点表？派工与报工如何挂接（报工挂派工末级？）——这是 V1 最大的决策点。
2. **报工模型**：继续"改 execution"还是引入报工记录（增量、多频次、按人/班次）？决定工时/良品/不良数据的可信度与成本核算质量。
3. **追溯谁来写**：追溯链的每个节点（开/完工、质检、出入库）由哪个业务事件写入 trace_log？无写入方则追溯功能是摆设。
4. **工序模型是否够用**：是否需要前后置依赖/并行/跳过执行逻辑（模板已有字段，逻辑缺失）？计划与实际是否要分离？
5. **质检体系定级**：IQC/IPQC/OQC/FQC 四类怎么定标准、检验任务从哪来（完工自动建单已有）、要不要返工/报废/让步闭环？
6. **状态机统一**：各模块状态枚举谁来定义、口径怎么对齐（尤其"执行中/已完成"跨模块值不同）、超期这类计算态要不要落库？
7. **组织与责任硬编码**：生产中心 id=5 写死、派工 1-3 级上限、leader 存用户名——V1 是否一并解耦？
8. **职责清理**：production-operation 并入 execution？死代码 operation.ts 删除？工单级责任字段与工序级派工责任的边界？
9. **dispatch-redesign-v2 的去留**：作为 V1 派工设计的输入（链节点表/动作语义/链展示）还是另起方案——建议 V1 派工部分以该文档为讨论基础，但需与报工/质检/追溯联动设计，不要孤立落地。

---

*报告完。本轮未修改任何代码/数据库/菜单/配置，未提交 Git。*
