# JJX Production P0 Domain Cleanup 分析与实施计划

> 版本：v1.0
> 日期：2026-08-19
> 性质：只读影响分析 + 最小实施方案设计（本轮未修改任何代码/数据库/菜单/配置，未提交 Git）
> 前置依据：《JJX 生产管理 V1 现状盘点报告》（jjx-docs/analysis/production-module-inventory-20260819.md）
> 范围：P0 Production Domain Cleanup；P1-P4 仅作边界判断，不实施

---

## 1. Executive Summary

P0 的目标不是重构生产模块，而是**清除会污染 P1-P4 设计地基的问题**。经过对真实代码/表结构的逐项核对，结论如下：

- **必须现在修（P0-A）的只有 3 项**：质检类型定义不一致（FQC 无定义但被完工逻辑写死使用）、完工数量口径两套并存（updateOrderStatus 强填数量 vs 052 口径）、execution 更新映射 `remark→defectiveReason` 错位（真实 bug）。
- **建议顺手修（P0-B）的 3 项**：生产中心 ID=5 硬编码提取、execution 缺 createBy/updateBy 审计字段、OperationRecord 无写入路径的定位声明。
- **其余问题全部归类到 P1-P4/TECH-DEBT**，尤其：派工三级硬编码**全部留给 P1 一次替换**（P0 解除会产生半成品，违背"避免在 P0 做一半 Dispatch V1"）；报工覆盖式更新留给 P2（P0 只锁定口径）；追溯空壳留给 P4；operation.ts 死代码列为 TECH-DEBT（不干扰后续设计）。

**一句话**：P0 保持小而明确，只做"定义统一 + 口径锁定 + 审计补漏"，不做任何模型改造。

---

## 2. P0 的目标和边界

**目标**：
1. 统一质检类型定义（为 P3 稳定基础）
2. 锁定报工数量语义与汇总口径（避免 P2 双重累计）
3. 确认状态机现状，找出阻碍 P1-P4 的点（但不重设计状态机）
4. 补审计字段缺口（让 P1-P4 能可靠知道"谁、何时、做了什么"）
5. 输出清晰的 P0/P1/P2/P3/P4/TECH-DEBT 分类

**边界（禁止）**：
- 不实施 production_dispatch_node（P1）
- 不新增 WorkReport（P2）
- 不扩展 QMS（P3）
- 不实施 Trace 写入（P4）
- 不改造 sys_dept 组织模型
- 不删除 production_operation_record 表
- 不为"架构漂亮"扩大范围

---

## 3. 状态定义分析

### 3.1 现状盘点（真实枚举）

| 模块 | 枚举/字段 | 值 |
|---|---|---|
| 生产订单 | OrderStatusEnum.order_status | 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期 |
| 订单审批 | approval_status（独立字段） | PENDING/APPROVED/REJECTED/CANCELLED |
| 工序执行 | ExecutionStatusEnum.execution_status | 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认 |
| 派工 | DispatchStatusEnum.status | 0待派工/1已派班组/2已派工/3执行中/4已完成/5已退回 |
| 操作记录 | RecordTypeEnum.record_type | START/PAUSE/RESUME/COMPLETE/QUALITY/ISSUE/PARAMETER/STATUS/OPERATION/DATA/EQUIPMENT/MATERIAL/TIME/ATTACHMENT/REMARK/SYSTEM（16 种） |
| 质检 | quality_inspection.result | pending/pass/fail（无独立状态字段） |
| 设备 | equipment.status | 0待机/1运行中/2维护中/3故障中 |
| 工装 | ToolingStatusEnum.status | 0在库/1使用中/2清洗保养中/3维修中/4报废 |

### 3.2 问题清单

| # | 现状 | 问题 | 是否 P0 必须修 | 最小修复方案 |
|---|---|---|---|---|
| S1 | "执行中"：order=6 / execution=2 / dispatch=3 / equipment=1；"已完成"：order=8 / execution=4 / dispatch=4 | 同一含义多状态值，跨模块对照困难 | 否 | P1 派工状态机重定义时统一；P0 不动作 |
| S2 | execution_status 一个字段承载：计划(0/1)+执行(2/3)+结果(4/5/6)+时效(7)+质量异常(8)+确认(9) | 一个 status 承担"执行状态+质量状态+异常状态"多维度 | **是（影响 P2/P3）** | P0 只加注释/常量映射锁定"execution_status 仅表示执行生命周期"；质量结果走 quality_check_result，异常走独立标记。不做结构拆分 |
| S3 | DispatchStatusEnum 六态 vs production_dispatch 表注释五态（0待派工 1已派工 2执行中 3已完成 4已退回） | 表注释与枚举不一致 | 否 | P1 重定义派工状态时同步 DDL 注释；P0 不动作 |
| S4 | validateStatusTransition 用 magic number 比较（cs==0/ns==1…），见 ProductionOrderServiceImpl.java | 状态跳转校验硬编码数字，且校验表不完整（3已驳回→? / 10已关闭→? 未定义） | 否 | P1 统一状态机时改；P0 记录为 TECH-DEBT（当前行为可用，不阻碍 P1-P4） |
| S5 | Service 直接比较 magic number：`plan.getOrderStatus() != 2`（ProductionOrderServiceImpl:1098）、`prodOrder.getOrderStatus() == 8`（QualityInspectionServiceImpl:116）、`status == 2/4`（DispatchServiceImpl:732-750） | magic number 散落，改枚举值易漏 | 否 | 不阻碍后续；P0 不动（改动面大收益小），记录 TECH-DEBT |
| S6 | 质检 result 用字符串 "pending"/"pass"/"fail"（QualityInspectionServiceImpl:80/91/111/129/205-207） | 字符串硬编码，无常量/枚举 | **是** | P0 与质检类型一起引入 QualityInspectionResultEnum（见 P0-01） |
| S7 | updateOrderStatus 置 COMPLETED 时强填 completed_quantity=planned_quantity（ProductionOrderServiceImpl） | 与 052 口径（finished_quantity=最后工序合格数）两套完工数量语义 | **是** | P0 锁定口径：completed_quantity=工序合格汇总（展示）、finished_quantity=成品完工（完工/入库/订单回写唯一口径）；updateOrderStatus 的强填逻辑标注废弃（见 P0-02） |

### 3.3 结论
P0 不需要重设计状态机。真正阻碍后续的是 S2（多维混用）和 S7（完工口径两套），用"常量锁定 + 注释声明"解决，不做结构改动。

---

## 4. Quality 类型分析

### 4.1 逐项确认（真实位置）

| 层 | 现状 | 证据 |
|---|---|---|
| 数据库 | production_quality_inspection.inspection_type 注释：IQC-来料检, IPQC-过程检, OQC-成品检；**无 FQC 定义** | 表 DDL（2026-08-19 导出） |
| Java 常量/枚举 | **无质检类型枚举类**；getTypeName() 仅映射 IQC/IPQC/OQC | QualityInspectionServiceImpl.java:197-202 |
| Controller/Service | **完工逻辑写死字符串 "FQC"**：自动创建质检单 setInspectionType("FQC")（ProductionOrderServiceImpl:416）、完工门查询 inspectionType="FQC"（:930） | ProductionOrderServiceImpl.java:416/930 |
| 前端 option | quality/index.vue 无类型选项表（新建检验是 TODO 占位）；tag 颜色 map 只有"过程检验/成品检验" | quality/index.vue:233-234 |
| TypeScript | QualityVO.inspectionType: string（自由字符串，无枚举） | api/production/quality.ts:25 |
| SQL | 无质检类型初始化数据 | sql 目录无相关 INSERT |
| 测试 | 无质检测试 | jjx-server/src/test 无 production 包 |
| 默认值 | create() 不设默认 inspection_type（由调用方传）；完工自动创建传 "FQC" | QualityInspectionServiceImpl.java:71-98 |

### 4.2 不一致确认
**确认存在"代码写 FQC，但系统枚举/页面没有 FQC"**：
- getTypeName("FQC") 返回原样 "FQC"（无中文名）
- 前端 tag 颜色 map 无 FQC → 完工质检单显示裸 "FQC"
- 数据库注释无 FQC
- 但完工门**依赖** FQC 类型的 pass 记录才能完工 → FQC 实际是"存在但未定义"的隐式类型

### 4.3 P0 最小兼容方案
**P0-01：引入 QualityInspectionTypeEnum（IQC/IPQC/OQC/FQC）+ QualityInspectionResultEnum（pending/pass/fail）**
- 纯新增枚举类，不改表结构、不改存储值（历史数据 0 行，无需迁移）
- getTypeName() 改为查枚举 label（FQC→"完工检验"）
- ProductionOrderServiceImpl 的 "FQC" 字符串改为枚举引用
- 前端 tag map 补 FQC；TypeScript 补联合类型（可选，低成本）
- **不扩展 QMS**（不建检验标准字典、不做检验任务流转——那些是 P3）

---

## 5. Dispatch 三级硬编码分析

### 5.1 全量定位（所有相关文件/调用链）

| 位置 | 硬编码形式 | 文件 |
|---|---|---|
| Service 最大层级校验 | `if (lv < 1 || lv > 3) throw new BusinessException("执行人级别仅支持 1-3 级")` | DispatchServiceImpl.java:325（appendLevel 方法） |
| DTO 注释 | level 字段注释"多级执行人链 1/2/3" | DispatchAssignDTO.java:34 |
| 数据存储 | operators JSON `[{userId,userName,level}]`，level 1-3 | production_dispatch.operators |
| 链合并 | mergeChain 按 levelMap TreeMap 排序（1/2/3） | DispatchServiceImpl.java:632-660 |
| 级别查询 | levelOfUser()/describeLevel() 按 level 字段匹配 | DispatchServiceImpl.java:383-398/… |
| 当前责任人 | 前端 parseOperators 排序后取链上各级；OperatorChain 组件展示全部级（无 3 级限制逻辑，但数据上限 3） | dispatch/index.vue、components/OperatorChain/index.vue |
| UI 转派级别 | `Math.min(transferFromLevel.value + 1, 3)` — **前端封顶 3 级** | dispatch/index.vue:637 |
| UI 文案 | "执行人级别仅支持 1-3 级" 提示、转派文案"第 N 级" | dispatch/index.vue:263 |
| 权限/资格 | 无基于 level 的权限判断（资格基于"是否在链上" LIKE 匹配 JSON） | DispatchServiceImpl:564-580 |

### 5.2 判断：哪些 P0 解除、哪些留 P1

| 硬编码点 | P0 处理 | 理由 |
|---|---|---|
| lv<1\|\|lv>3 校验（后端） | **留 P1** | 解除 = 允许 4 级链，但 JSON 无 parent 指针、无 ACTIVE 节点模型，会产生"能存 4 级但无法正确流转"的半成品 |
| 前端 Math.min(…,3) | **留 P1** | 同上；P1 换 DispatchNode 后一并替换 |
| operators JSON 结构 | **留 P1** | P0 不实施 production_dispatch_node，数据结构不动 |
| DTO 注释/文案 | 留 P1 | 纯注释，P1 重写 |
| 权限 LIKE 匹配 JSON | **留 P1**（记录为 P1 必须项） | isDispatched() 的 LIKE 查询在 P1 换节点表后改为 exists 查询；P0 改它无意义 |

**结论**：**所有派工三级硬编码全部留给 P1 一次替换**。P0 不碰 dispatch 逻辑。理由：三级限制是"当前模型（JSON 链）的自洽约束"，P0 单独解除会破坏自洽性且无法获得 P1 的收益。

---

## 6. 组织/生产中心 ID 硬编码分析

### 6.1 全量检查结果

| 模块 | 硬编码 | 文件 |
|---|---|---|
| dispatch | `sysDeptMapper.selectById(5L)` 注释"生产中心（id=5，与部门数据一致）" | DispatchServiceImpl.java:557（isProductionManager 方法） |
| production order | 无 | — |
| execution | 无 | — |
| quality | 无 | — |
| trace | 无 | — |
| equipment | 无 | — |
| 角色 | DispatchServiceImpl 无角色 ID 硬编码（权限走 perms 注解） | — |
| 用户 | 无固定用户 ID | — |

**全项目仅 1 处：DispatchServiceImpl.java:557 的 `selectById(5L)`。**

### 6.2 ID=5 实际代表什么
- sys_dept 表中 dept_id=5 = "生产中心"，leader=prod_mgr
- 来源：`jjx-docs/sql/production-org-data.sql`（2026-08-13 生成的演示组织数据，dept_id 5/6/7/8/9/10/11/12/13/14 为生产 3 层）
- 用途：isProductionManager() 判断"当前用户是否生产中心负责人"（初始派工权人之一），配合 canAssign()/checkDispatchRight()
- **与初始化 SQL 强耦合**：selectById(5L) 依赖 production-org-data.sql 的插入顺序/ID

### 6.3 更合理来源（最小方案，不引入配置中心）
- 方案 A（推荐）：改为**按 leader 查询**——`sys_dept` 查 `leader = 当前用户` 且为生产中心的部门；或更简单：查当前用户是否任一部门的 leader 且该部门路径在"生产中心"下。改动小、不依赖 ID。
- 方案 B：Spring 配置项 `jjx.production.center-dept-id=5`，改配置不改代码。简单但仍是配置。
- 方案 C：常量提取 `PRODUCTION_CENTER_DEPT_ID = 5L` + 注释。最保守，只是消除"裸数字"。

**P0 推荐**：方案 A 的轻量版——isProductionManager 改为"当前用户是 sys_dept 中某部门的 leader，且该部门 parent 链上存在名为'生产中心'的部门"。若不放心名称判断，退而求其次用方案 C（常量+注释），把"组织语义"问题记录为 TECH-DEBT（P1 派工模型重做时会重新定义初始派工权来源）。

---

## 7. Execution 与 OperationRecord 职责分析

### 7.1 现状
- **execution 页面**（views/production/execution/index.vue）：调 operationExecutionApi（list/start/pause/quality-check/complete/cancel/edit），展示工序执行状态与数量
- **production-operation 页面**（views/production/production-operation/index.vue）：**同样 import operationExecutionApi**（:387），调 list/start/complete/remove —— 两个页面同一数据源
- **api/production/operation.ts**：请求 `/production/operation/*`，**后端无此路由**（后端只有 /production/operation-record 与 /production/operation-execution），且**前端无任何页面引用此文件** = 断链死代码
- **production_operation_record 表**：有完整结构（record_type/quantity/parameters/quality_data/issue_description 等 16 种类型），但：
  - 前端无任何 api 封装调用 `/production/operation-record`（api/production/ 下无 record.ts）
  - 后端 ProductionOperationRecordController 存在（create/update/delete/list/page/execution/order/process/import/export），但**无业务代码调用 createRecord**
  - 表数据 0 行
- **start/pause/complete 不写 record**：ProductionOperationExecutionServiceImpl 中无任何 createRecord/recordMapper 调用（grep 确认）

### 7.2 目标定位 vs 现实
- 目标：Execution = 工序执行状态与汇总；OperationRecord = 执行过程事件/Timeline
- 现实：**离目标还差"写入方"**。record 表结构完全符合 Timeline 定位，但没有任何代码写它；start/pause/complete 等事件没有落 record
- **结论**：结构对了、写入缺失。这不是职责重复，而是"能力未接通"

### 7.3 P0 最小方案
- 不删除 production_operation_record 表
- **P0-B（推荐做）**：在 ExecutionService 的 start/pause/complete/qualityCheck 中补 record 写入（每事件插一条 Timeline），让 Timeline 成为真实数据；同时 production-operation 页面已隐藏菜单，保留但不再维护
- 若 P0 想更小：只删除断链死代码 api/production/operation.ts（无引用、无路由、零风险），record 写入留 P2（WorkReport 引入时一起设计事件来源）
- 判断：**record 写入不是 P1-P4 的前置阻塞**（P2 报工模型会重新定义事件流），所以归 P0-B；死代码删除归 TECH-DEBT 或顺手做

---

## 8. 数量口径分析

### 8.1 字段逐项确认

| 字段（execution） | 当前语义 | 实际代码行为 | 推荐正式语义 | P0 调整 |
|---|---|---|---|---|
| input_quantity | 投入数量 | 生成时默认 0；前端记录弹窗可填 | 本次投料数量（每次报工的投入） | 否（P2 定义） |
| output_quantity | 产出数量 | **DTO actualCompletedQuantity → output_quantity**（updateEntityFromUpdateDTO:862）；completeExecution 默认 output=input | 本次产出数量（覆盖式） | 否 |
| qualified_quantity | 合格数量 | DTO actualQualifiedQuantity → qualified_quantity；complete 默认=output | 本次合格数量（覆盖式） | 否 |
| defective_quantity | 不良数量 | DTO actualDefectiveQuantity → defective_quantity；complete 默认 0 | 本次不良数量（覆盖式） | 否 |
| actual_labor_hours | 实际人工工时 | 前端记录弹窗可填；完工成本核算读取 | 本次人工工时 | 否 |
| actual_machine_hours | 实际机器工时 | 同上 | 本次机器工时 | 否 |
| **remark（DTO）** | — | **updateEntityFromUpdateDTO:909 把 DTO.remark → execution.defective_reason（不良原因）** | remark 应为备注 | **是（P0-A，错位 bug）** |

### 8.2 关键口径确认
- **是否累计值**：否。前端 submitRecord 每次 `edit` 全量覆盖（execution/index.vue:590-610），是"本次值覆盖"模式
- **多次修改是否覆盖**：是，直接 updateById 覆盖，无增量、无历史
- **完工判断依赖**：finished_quantity（=最后一道工序 qualified_quantity，052 口径），canCompleteOrder:904 要求 finishedQuantity>0
- **订单完成数量计算**：updateOrderCompletedQuantity()：completed_quantity=Σ 所有已完成工序 qualified_quantity（汇总）；finished_quantity=最后一道工序合格数
- **质检依赖**：完工门只查 FQC 记录存在且 pass（不读 execution 数量）；质检单自身 total/pass/fail 独立
- **双重累计风险**：P2 引入 WorkReport 后，如果 WorkReport 也写 execution 数量（本次值），且完工汇总仍 Σ execution.qualified_quantity，则**不会双重累计**（因为是覆盖不是累加）；但若 WorkReport 改为增量累加而 execution 仍覆盖，会混乱。**P0 必须锁定的结论：execution 数量字段 = 最新一次报工快照（覆盖式）；P2 的 WorkReport 表负责历史明细与累计，execution 保持快照语义**

### 8.3 P0 处理
- **P0-A：修复 remark→defective_reason 错位映射**（updateEntityFromUpdateDTO:909）。最小修：DTO 增加 defectiveReason 字段，remark 不再映射到 defective_reason
- **P0-B：在实体/DTO 注释中锁定数量语义**（"快照语义，覆盖式，历史明细归 WorkReport/P2"），防止 P2 开发误解
- 不做字段改名、不做增量逻辑（P2）

---

## 9. API 与重复能力分析

### 9.1 生产主链死 API 扫描

| API/文件 | 状态 | 分类 |
|---|---|---|
| api/production/operation.ts（/production/operation/*） | 后端无路由、前端无引用 → **断链死代码** | **B 后续处理**（或顺手删，零风险） |
| ProductionOperationRecordController（/production/operation-record/*） | 后端存在、前端无 api 封装、无业务调用者 | B 后续处理（P2 接 Timeline 时激活） |
| ProductionReportController（/production/report/output\|efficiency\|quality） | 后端存在、前端 api/production/report.ts 存在且页面引用（report/index.vue 菜单已隐藏） | C 保留无妨（报表能力保留） |
| ProductionCostController（/production/cost/list\|summary） | 后端存在、前端 cost/index.vue 引用（菜单已隐藏） | C 保留无妨 |
| ToolingController（/production/tooling/*） | 后端存在、前端引用（菜单已隐藏） | C 保留无妨 |
| DispatchController.updateOrderTeam（PUT /production/dispatch/order/{orderId}/team） | 前端 OrderTableActions 未直接调用（grep 未见）；但 updateOrderTeam 服务被 OrderTableActions 的"派工"跳转间接使用？——未确认前端调用方 | C 保留无妨（工单级责任字段仍在用） |
| execution 页面 handleRecord 弹窗（"生产记录"） | 弹窗提交调 operationExecutionApi.edit（不是 record API） | **A 关注**：该弹窗是当前唯一"报工"入口，语义上应指向报工而非 Timeline；P0 不改行为，P2 重定义 |

### 9.2 重复入口判断
- production-operation 页面 ≡ execution 页面（同一 operationExecutionApi）→ 菜单已隐藏，**B 后续处理**：等 P2/P4 决定其去留（任务背景已定："定位为工序执行详情和 Timeline"）
- 不做大规模删除

---

## 10. 组织基础模型影响

### 10.1 现状确认
- sys_dept：parent_id + ancestors + leader + order_num + status（4 层：公司→生产中心→车间→班组）
- **leader 存 userName（不是 userId）**：production-org-data.sql 中 leader='prod_mgr'/'print_mgr' 等；DispatchServiceImpl.underlings() 用 `sys_dept.leader = ?` 传当前用户 userName 递归查手下
- 车间/班组识别：**无 dept type 字段**；通过名称（印刷车间/印刷一组）和层级推断；代码中**未发现**通过 dept_name 判断"车间/班组"的逻辑（唯一例外：isProductionManager 通过 selectById(5L) 定位生产中心）
- user ↔ dept：sys_user.dept_id

### 10.2 是否阻碍 P1 DispatchNode
- P1 需要"下派对象 = 当前节点的下级（组织树约束）"——underlings() 已实现且可用（leader=userName 语义）
- **潜在阻碍点**：leader 用 userName 而非 userId，如果 P1 按 userId 建立 DispatchNode 关联，需要转换；但 underlings() 本身工作正常，可复用
- **结论**：**不阻碍 P1，记录为 TECH-DEBT**（leader 语义统一为 userId 是长期改进，P0 不动 sys_dept）

---

## 11. Audit/Timeline 基础能力

### 11.1 核心实体审计字段盘点

| 实体/表 | createBy | createTime | updateBy | updateTime | 业务操作人/时间 |
|---|---|---|---|---|---|
| production_order | ✅ | ✅ | ✅ | ✅ | completed_by + actual_end_time；approver_id/approval_time |
| production_operation_execution | ❌ | ✅ | ❌ | ✅ | operator_id/name（报工人） |
| production_dispatch | ✅ | ✅ | ✅ | ✅ | assigned_by/assign_time；缺 completed_by/completed_at |
| production_operation_record | ❌（operator_id/name） | ✅(record_time) | ❌ | ❌ | operator_id/name + record_time |
| production_quality_inspection | ✅ | ✅ | ✅ | ✅ | inspector/inspect_time |
| production_trace_log | ✅ | ✅ | ❌ | ❌ | operator/operate_time |
| production_equipment | ✅ | ✅ | ✅ | ✅ | — |

### 11.2 分类

| 分类 | 项 |
|---|---|
| **P0 必须补** | execution 缺 createBy/updateBy（P1 派工/P2 报工都要追溯"谁改的工序"）——补字段+审计填充 |
| P1 补 | dispatch 缺 completed_by/completed_at（P1 重做派工状态机时加） |
| P2 补 | record 的审计字段（P2 定义 Timeline 写入规范时定） |
| 无需补 | order/quality/equipment/trace 已具备 |

---

## 12. 测试现状

### 12.1 现状盘点
- 测试目录：jjx-server/src/test/java/com/jjx/ 下仅：sales（SalesFlowTest、QuotationInvariantTest）、inventory（InventoryInbound/Outbound/TransferInvariantTest、InventoryTransactionContractTest）、common/utils/pdf（PdfDocBuilderTest）
- **production 模块 0 测试**（order/dispatch/execution/quality/trace/equipment 均无单测/集成测试）
- 前端无 spec/test 文件（无 .spec.ts/.test.ts）

### 12.2 P0 最少回归测试
P0 修改点都很小，最少补：
1. QualityInspectionTypeEnum/ResultEnum 的 label 映射测试（含 FQC→完工检验）
2. updateEntityFromUpdateDTO 的映射测试（remark 不再写 defective_reason；defectiveReason 正确映射）
3. isProductionManager 改为按 leader/组织查询后的行为测试（若采用方案 A）
- 不建大型测试框架，只补上述 2-3 个单元测试即可覆盖 P0 改动

---

## 13. 分类总表（P0/P1/P2/P3/P4/TECH-DEBT）

| 编号 | 问题 | 分类 | 说明 |
|---|---|---|---|
| C1 | 质检类型 FQC 无定义但完工逻辑写死使用 | **P0-A** | 引入枚举统一，见 P0-01 |
| C2 | 质检 result 字符串硬编码 pending/pass/fail | **P0-A** | 与 C1 一起，见 P0-01 |
| C3 | 完工数量口径两套（updateOrderStatus 强填 vs 052 口径） | **P0-A** | 锁定口径，见 P0-02 |
| C4 | execution 更新映射 remark→defective_reason 错位 | **P0-A** | 修复映射，见 P0-03 |
| C5 | 生产中心 ID=5 硬编码（selectById(5L)） | **P0-B** | 提取/按 leader 查询，见 P0-04 |
| C6 | execution 缺 createBy/updateBy 审计 | **P0-B** | 补字段，见 P0-05 |
| C7 | OperationRecord 无写入方（Timeline 空转） | **P0-B** | 补 start/pause/complete 事件写入，见 P0-06 |
| C8 | 派工三级硬编码（后端 lv>3 + 前端 Math.min+JSON 链） | **P1** | 全部留给 P1 DispatchNode 一次替换，P0 不碰 |
| C9 | 派工资格 LIKE 匹配 JSON（isDispatched） | **P1** | 换节点表后改 exists 查询 |
| C10 | dispatch 状态表注释与枚举不一致 | **P1** | 状态机重定义时同步 |
| C11 | 派工无 completed_by/completed_at | **P1** | 状态机重做时加 |
| C12 | 报工覆盖式无历史 | **P2** | WorkReport V1 解决；P0 只锁定快照语义 |
| C13 | execution 数量字段快照语义注释锁定 | **P0-B（配合 P2）** | 防双重累计 |
| C14 | 质检检验标准字典/检验任务流转 | **P3** | QMS 范围 |
| C15 | 追溯无写入方（trace_log 0 行） | **P4** | Trace V1 |
| C16 | api/production/operation.ts 断链死代码 | TECH-DEBT（可顺手删） | 无引用无路由，删了零风险 |
| C17 | validateStatusTransition magic number + 校验表不完整 | TECH-DEBT | 当前行为可用 |
| C18 | Service magic number（status==2/8 等） | TECH-DEBT | 改动面大收益小 |
| C19 | sys_dept.leader 存 userName 非 userId | TECH-DEBT | 不阻碍 P1 |
| C20 | execution_status 多维语义（计划/执行/质量/异常/确认） | TECH-DEBT（P2/P3 配合） | P0 只注释锁定，不拆结构 |
| C21 | production-operation 页面与 execution 重复 | TECH-DEBT（P2/P4 处理） | 菜单已隐藏 |
| C22 | report/cost/tooling 隐藏入口保留 | C 保留无妨 | — |

---

## 14. 推荐 P0 最小实施包

### P0-01 质检类型/结果枚举统一
- **问题**：FQC 无定义但完工逻辑写死使用；result 字符串散落硬编码
- **影响**：P3 Quality Integration 的基础；当前 FQC 质检单显示裸代码
- **修改范围**：新增枚举 + 2 个 Service 方法改造 + 前端 tag map
- **涉及文件**：
  - 新增 `jjx-server/.../production/enums/QualityInspectionTypeEnum.java`（IQC/IPQC/OQC/FQC + label）
  - 新增 `.../production/enums/QualityInspectionResultEnum.java`（PENDING/PASS/FAIL）
  - 改 `QualityInspectionServiceImpl.java`（getTypeName/getResultName 查枚举）
  - 改 `ProductionOrderServiceImpl.java:416/930`（"FQC" → 枚举引用）
  - 改 `jjx-web/src/views/production/quality/index.vue`（tag map 补 FQC）
  - 改 `jjx-web/src/api/production/quality.ts`（inspectionType 联合类型，可选）
- **数据库变更**：无（历史数据 0 行，值不变）
- **兼容方式**：纯新增枚举+读逻辑改造，存储值不变；getTypeName 对未知值仍原样返回（兜底）
- **测试**：枚举 label 映射单测；完工自动建单类型=FQC 的断言
- **风险**：低

### P0-02 完工数量口径锁定
- **问题**：updateOrderStatus(COMPLETED) 强填 completed_quantity=planned_quantity，与 052 口径（finished_quantity）冲突
- **影响**：完工判断/入库/订单回写唯一口径必须明确，否则 P2 双重累计
- **修改范围**：注释/常量声明 + 判定 updateOrderStatus 强填逻辑的处置
- **涉及文件**：
  - 改 `ProductionOrderServiceImpl.java`（updateOrderStatus 中强填逻辑加 @Deprecated 注释或移除；文档声明 completed_quantity=汇总展示口径、finished_quantity=完工唯一口径）
  - 改 `ProductionOrder.java`（字段注释补充口径声明）
- **数据库变更**：无
- **兼容方式**：行为若移除强填，历史已完成订单字段不变（只在下次状态变更时生效）；建议 P0 仅注释锁定+移除强填（该路径由 completeOrder 正规流程替代）
- **测试**：completeOrder 路径不回归（现有流程不受影响）
- **风险**：中低（需确认 updateOrderStatus 直接置 COMPLETED 的调用方是否还依赖强填——前端 OrderStatusDialog 可手动改状态；若依赖，则 P0 只加注释不移除，标注 TECH-DEBT）

### P0-03 execution 更新映射修复（remark→defective_reason 错位）
- **问题**：updateEntityFromUpdateDTO 把 DTO.remark 写进 execution.defective_reason
- **影响**：报工填写的"备注"被当成"不良原因"，数据语义错误，P2 前必须修正
- **修改范围**：DTO 加 defectiveReason 字段 + 映射修正
- **涉及文件**：
  - 改 `ProductionOperationExecutionUpdateDTO.java`（新增 defectiveReason）
  - 改 `ProductionOperationExecutionServiceImpl.java:852-915`（remark 不再映射 defective_reason；defectiveReason 单独映射；remark 落 execution 备注——execution 实体无 remark 字段，则 remark 仅弃用或落 operation_record）
  - 前端 `execution/index.vue` submitRecord 改传 defectiveReason（可选）
- **数据库变更**：无（字段已存在）
- **兼容方式**：旧数据 defective_reason 可能是误填的备注——历史数据 0 行（execution 9 行但 record 0 行），影响极小
- **测试**：updateEntityFromUpdateDTO 映射单测
- **风险**：低

### P0-04 生产中心 ID=5 硬编码解除
- **问题**：DispatchServiceImpl:557 selectById(5L)
- **影响**：P1 派工初始权定义依赖；演示数据重建后 ID 可能变
- **修改范围**：isProductionManager 改造
- **涉及文件**：改 `DispatchServiceImpl.java:557`
- **数据库变更**：无
- **兼容方式**：方案 A（按 leader 递归查生产中心下部门）行为等价；或方案 C（常量+注释）零行为变化
- **测试**：isProductionManager 单测（若方案 A）
- **风险**：低

### P0-05 execution 审计字段补齐
- **问题**：execution 无 createBy/updateBy
- **影响**：P1/P2 追溯"谁改了工序"不可靠
- **修改范围**：实体+表加字段（DDL migration）+ MyBatis 审计填充
- **涉及文件**：
  - 改 `ProductionOperationExecution.java`
  - 改 `production_operation_execution` 表（ALTER 加 create_by/update_by）
  - 改 `ProductionOperationExecutionServiceImpl`（update 时填 updateBy）
- **数据库变更**：**是——ALTER TABLE 加 2 列**（唯一 P0 数据库变更）
- **兼容方式**：新列可空，历史数据 NULL 可接受；migration 脚本可回滚（DROP COLUMN）
- **测试**：插入/更新后字段断言
- **风险**：低

### P0-06 OperationRecord Timeline 写入接通
- **问题**：record 表 0 行、start/pause/complete 不写事件
- **影响**：Timeline 定位（P2/P4 依赖）需要真实事件源；不阻塞 P1
- **修改范围**：ExecutionService 事件写入
- **涉及文件**：改 `ProductionOperationExecutionServiceImpl.java`（start/pause/complete/qualityCheck 各插一条 record）
- **数据库变更**：无（表已存在）
- **兼容方式**：新增写入，不影响现有逻辑
- **测试**：start→record 断言
- **风险**：低（若 P0 想更小，此项可降级为"仅定位声明"，写入留 P2）

### 可选 P0-07 删除断链死代码
- api/production/operation.ts（无引用、无路由）
- **分类**：TECH-DEBT，可顺手删（前端一个文件）
- 若保守，保留无妨

---

## 15. 文件影响清单（P0 完整）

**后端（约 7 个文件 + 2 个新增）**：
1. 新增 `production/enums/QualityInspectionTypeEnum.java`
2. 新增 `production/enums/QualityInspectionResultEnum.java`
3. 改 `production/service/impl/QualityInspectionServiceImpl.java`
4. 改 `production/service/impl/ProductionOrderServiceImpl.java`
5. 改 `production/service/impl/ProductionOperationExecutionServiceImpl.java`
6. 改 `production/domain/dto/ProductionOperationExecutionUpdateDTO.java`
7. 改 `production/domain/entity/ProductionOperationExecution.java`
8. 改 `production/service/impl/DispatchServiceImpl.java`（仅 P0-04 一处）

**前端（约 3 个文件）**：
1. 改 `views/production/quality/index.vue`（tag map）
2. 改 `api/production/quality.ts`（联合类型，可选）
3. 改 `views/production/execution/index.vue`（defectiveReason 传参，可选）
4. 删 `api/production/operation.ts`（可选）

**数据库（1 个 migration）**：
- ALTER TABLE production_operation_execution ADD COLUMN create_by/update_by（可空）

**文档（2 个）**：
- 本报告 + 盘点报告（已存在）

---

## 16. 数据库影响兼容与回滚方案

| 项 | 结论 |
|---|---|
| 是否需要数据库 migration | **是，且仅 1 个**：execution 加 create_by/update_by 两列（可空） |
| 是否涉及历史数据 | 质检 0 行、record 0 行、dispatch 3 行、execution 9 行（均演示数据）——无真实历史数据包袱 |
| 是否有破坏性变更 | 无。所有改动向后兼容（枚举新增、映射修复、字段可空新增） |
| 是否需要前后端同时改 | P0-01（tag map）和 P0-03（defectiveReason）建议前后端同步；其余纯后端 |
| 是否可以向后兼容 | 是；getTypeName 未知值原样返回兜底，旧前端不传 defectiveReason 时 defective_reason 不再被误写 |
| 是否影响现有派工数据 | 否（P0 不碰 dispatch 数据/逻辑，仅 P0-04 改权限判断函数内部实现，行为等价） |
| 是否影响现有生产订单 | 否（P0-02 若移除强填逻辑，仅影响"手动改状态=已完成"路径；该路径应走 completeOrder） |
| 是否影响已完成工序 | 否（数量/状态字段不变） |
| **回滚方案** | 枚举/Service 改动走 git revert；migration 执行 `ALTER TABLE production_operation_execution DROP COLUMN create_by, DROP COLUMN update_by` 即可回滚（可空列，无数据依赖） |

---

## 17. 验收标准

1. 质检类型枚举包含 IQC/IPQC/OQC/FQC，完工自动创建与完工门查询均引用枚举（grep 无裸 "FQC" 字符串）
2. getTypeName("FQC") 返回"完工检验"；未知类型仍原样返回
3. updateEntityFromUpdateDTO：remark 不再写入 defective_reason；defectiveReason 正确映射
4. execution 表新增 create_by/update_by 且插入/更新自动填充
5. DispatchServiceImpl 无 `selectById(5L)` 裸硬编码（常量或按 leader 查询）
6. （若 P0-06）start/pause/complete 后 production_operation_record 有对应事件行
7. 后端 `mvn compile` 通过；新增单测通过
8. 六个生产菜单入口可正常访问（不受影响回归确认）

---

## 18. 实施顺序

1. **P0-01** 质检枚举（独立，先行）
2. **P0-03** 映射修复（独立，小）
3. **P0-04** ID 硬编码（独立，小）
4. **P0-02** 完工口径（需先确认 updateOrderStatus 手动置完成路径的使用方，再定移除 or 注释）
5. **P0-05** 审计字段（唯一 DDL，最后做，避免与其他改动冲突）
6. **P0-06** Timeline 写入（可选，若 P0 控制规模可移到 P2）
7. 全量 `mvn compile` + 单测 + 菜单回归

---

## 19. 特别说明（"代码比预期合理"的地方）

- **validateStatusTransition** 虽然用 magic number，但状态流转表本身定义完整（草稿→待审→…→关闭），行为可用——因此归 TECH-DEBT 而非 P0
- **完工联动链**（质检门→自动 FQC→自动入库→成本核算→SO 回写）设计合理，P0 完全不动
- **production_operation_record 表结构**（16 种记录类型 + JSON 扩展字段）本身就是合格的 Timeline 模型，缺的只是写入方
- **sys_dept 组织树**（parent_id+ancestors+leader）足以支撑 P1 多级派工，无需 P0 改造

---

*报告完。本轮只读，未修改任何代码/数据库/菜单/配置，未提交 Git。等待人工评审。*
