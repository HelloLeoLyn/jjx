# JJX Production P2-B WorkReport Foundation Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P2-B WorkReport 数据库与后端基础设施（不接入 Execution 写流程、不实现动作）
> 状态：完成，等待人工验收

---

## 1. 新增/修改文件

**新增 10 个（零修改）：**

| 文件 | 说明 |
|---|---|
| `jjx-server/sql/migrations/V20260819_002__production_work_report.sql` | 建表 migration（已执行） |
| `domain/entity/ProductionWorkReport.java` | 报工事实实体 |
| `enums/WorkReportStatusEnum.java` | SUBMITTED/CANCELLED 枚举 |
| `mapper/ProductionWorkReportMapper.java` | BaseMapper |
| `service/WorkReportReadService.java` | 只读服务接口 |
| `service/impl/WorkReportReadServiceImpl.java` | 只读服务实现 |
| `domain/vo/WorkReportVO.java` | 报工 VO（Execution 历史展示） |
| `controller/WorkReportController.java` | 只读 Controller（2 个 GET） |
| `test/.../WorkReportStatusEnumTest.java` | 枚举测试 |
| `test/.../ProductionWorkReportMapperTest.java` | Mapper/Entity 约束测试 |

**修改：零**（未触碰 Execution/Dispatch/Node/Quality/Trace 任何现有文件——git 确认）。

## 2. Migration 文件

`V20260819_002__production_work_report.sql`（检查无版本冲突，遵循现有 V 命名规范，手动执行）。**已执行**（P2-B 允许的唯一 DDL），EXIT=0。

## 3. 最终 production_work_report DDL（实测 SHOW CREATE TABLE）

```sql
CREATE TABLE `production_work_report` (
  `report_id`          bigint NOT NULL AUTO_INCREMENT COMMENT '报工ID',
  `order_id`           bigint NOT NULL COMMENT '生产订单ID(冗余引用，便于追溯查询)',
  `order_no`           varchar(50) DEFAULT NULL COMMENT '工单编号(冗余)',
  `execution_id`       bigint NOT NULL COMMENT '工序执行记录ID(生产事实主体)',
  `dispatch_id`        bigint DEFAULT NULL COMMENT '派工单ID(冗余；需与 node.dispatchId 一致)',
  `dispatch_node_id`   bigint NOT NULL COMMENT '报工时责任节点ID(责任锚点)',
  `reporter_id`        bigint NOT NULL COMMENT '报工提交人ID(P2-C 默认须=ACTIVE assignee，库不强制)',
  `reporter_name`      varchar(64) NOT NULL COMMENT '报工提交人姓名快照',
  `equipment_id`       bigint DEFAULT NULL COMMENT '本次实际使用设备ID(可空=人工工序无设备)',
  `equipment_name`     varchar(200) DEFAULT NULL COMMENT '本次实际使用设备名称(快照)',
  `qualified_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '本次合格数量',
  `defective_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '本次不良数量',
  `labor_hours`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '本次人工工时',
  `machine_hours`      decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '本次机器工时',
  `work_start_time`    datetime DEFAULT NULL COMMENT '本次生产开始时间(可空)',
  `work_end_time`      datetime DEFAULT NULL COMMENT '本次生产结束时间(可空；P2-C 校验 end>=start)',
  `report_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报工正式提交时间(Service 显式设置)',
  `defect_reason`      varchar(500) DEFAULT NULL COMMENT '不良原因(P2 V1 单字段，P3 再做缺陷明细)',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注(提交后不可变)',
  `report_status`      varchar(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED已提交/CANCELLED已撤销',
  `cancelled_by`       bigint DEFAULT NULL COMMENT '撤销人ID',
  `cancelled_by_name`  varchar(64) DEFAULT NULL COMMENT '撤销人姓名',
  `cancelled_at`       datetime DEFAULT NULL COMMENT '撤销时间',
  `cancel_reason`      varchar(500) DEFAULT NULL COMMENT '撤销原因(P2-C 必填)',
  `create_by`          varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time`        datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_execution_status` (`execution_id`,`report_status`),
  KEY `idx_dispatch_node` (`dispatch_node_id`),
  KEY `idx_reporter_status` (`reporter_id`,`report_status`),
  KEY `idx_report_time` (`report_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产报工(一次不可覆盖的生产数量/工时事实)'
```

## 4. 字段及语义

| 字段 | 语义 |
|---|---|
| report_id | 主键（即报工标识，无 report_no） |
| order_id / order_no | 生产订单冗余引用（追溯/查询） |
| execution_id | 生产事实主体（必填） |
| dispatch_id | 冗余派工单 ID（须=node.dispatchId） |
| dispatch_node_id | 报工时责任节点（责任锚点，必填） |
| reporter_id / reporter_name | 实际提交报工用户（P2-C 默认=ACTIVE assignee，库不强制） |
| equipment_id / equipment_name | 本次实际使用设备（可空=人工工序；允许与默认设备不同） |
| qualified / defective_quantity | 本次合格/不良（DECIMAL(18,4) DEFAULT 0） |
| labor / machine_hours | 本次人工/机器工时（DECIMAL(10,2) DEFAULT 0，与 execution 一致） |
| work_start / work_end_time | 本次生产时间区间（可空；P2-C 校验 end>=start） |
| report_time | 报工正式提交时间（DEFAULT CURRENT_TIMESTAMP，Service 显式设置） |
| defect_reason | 不良原因单字段（P3 再细化） |
| remark | 备注（提交后不可变） |
| report_status | SUBMITTED/CANCELLED（DEFAULT SUBMITTED） |
| cancelled_by / by_name / at / cancel_reason | 撤销审计（一次建齐，P2-C 用） |

## 5. 为什么无 report_no

用户现阶段不使用报工编号（无追溯/打印/现场沟通编号需求），reportId 足够；不为 ERP 单据编号统一引入生成器（P2-A 评审 §十六拍板）。

## 6. 为什么无 input/output 字段

- execution.inputQuantity 是计划/输入基数，非实际投入事实
- 本次实际产出 = qualified + defective（不引入第三口径）
- outputQuantity 将在 P2-C 作为 execution projection 计算（SUM(qualified+defective)）——避免三数量口径漂移（P2-A §十七拍板）

## 7. 为什么 dispatchId 冗余

查询便利 / 历史稳定 / Trace 追溯 / 报表统计五角度均值得（P2-A §5.2 拍板）；虽可通过 node→dispatch 推导，但冗余一列成本极低且独立可查。

## 8. 为什么不冗余 org

责任组织历史通过 `dispatch_node_id → ProductionDispatchNode.org snapshot` 获取；重复保存无意义（P2-A §十四拍板）。

## 9. WorkReportStatusEnum

| code | label |
|---|---|
| SUBMITTED | 已提交 |
| CANCELLED | 已撤销 |

fromCode/labelOf 未知值原样返回（兼容）；**无 DRAFT/APPROVED/REJECTED**（测试断言 values().length==2）。

## 10. Entity

`ProductionWorkReport.java`：完整映射表字段；领域注释明确"已提交事实不可修改、更正=CANCEL+新增、禁止物理删除、未来写动作仅 SUBMIT/CANCEL"。**不创建通用 updateWorkReport/saveOrUpdate**。

## 11. Mapper

`ProductionWorkReportMapper extends BaseMapper`：按 executionId / executionId+reportStatus / dispatchNodeId / reporterId / reportId 均可用 Wrapper 实现（无需 XML）。

## 12. Read Service

`WorkReportReadService`：
- `getById` / `listByExecutionId` / `listSubmittedByExecutionId` / `listByDispatchNodeId`
- 排序：reportTime DESC → createTime DESC → reportId DESC（Execution 报工历史 Drawer 默认）
- 不实现 submit/cancel/update（P2-C）

## 13. VO

`WorkReportVO`：reportId/executionId/dispatchId/dispatchNodeId/reporter/equipment/数量/工时/时间区间/defectReason/remark/reportStatus(+label)/cancel 审计；不含无意义内部字段。

## 14. Controller 是否建立及理由

**方案 B（只读 Controller）**：`GET /production/work-report/{id}` + `GET /production/work-report/execution/{executionId}`，权限 `production:operation-execution:view`（复用现有）。理由：P2-C 动作 API 就绪前，前端/调试可读报工历史；不暴露 POST/PUT/DELETE（WorkReport 未来写动作只有 SUBMIT/CANCEL）。

## 15. 索引

```
idx_execution (execution_id)
idx_execution_status (execution_id, report_status)  ← 汇总 SUM 常用，比单 idx_execution 更有价值
idx_dispatch_node (dispatch_node_id)
idx_reporter_status (reporter_id, report_status)
idx_report_time (report_time)
```

未堆重复索引；idx_execution_status 覆盖了单列场景（索引左前缀）。

## 16. 外键策略

**无物理 FK**（遵循项目现状，信息_schema 验证 fk_count=0）；execution_id/dispatch_id/dispatch_node_id 逻辑关联由 Service 校验。

## 17. Cancel 字段

一次建齐：cancelled_by / cancelled_by_name / cancelled_at / cancel_reason。P2-C 撤销时 cancel_reason 必填；CANCELLED 报工原事实字段保留、禁止物理删除。

## 18. 权限点是否建立

**未建立**。`production:work-report:add/cancel` 权限点数据属于业务 DML，按 P2-A 评审：**放 P2-C 与 Action API 一起实施**（P2-B 不为空 Foundation 提前授权）。P2-B 只读 API 复用现有 `production:operation-execution:view`。

## 19. Migration 是否执行

**✅ 已执行**（V20260819_002，唯一 DDL：CREATE TABLE + 索引；无 ALTER 任何现有表）。

## 20. production_work_report 实际记录数

**0 条**（实测 COUNT=0；验证数据全部事务回滚）。

## 21. execution/dispatch/node 数据是否变化

**❌ 完全没变化**（实测：execution 9 条、dispatch 3 条、dispatch_node 4 条，与 P2-A 复核时点一致）。

## 22. 是否做历史 WorkReport backfill

**❌ 否**。未从现有 execution 数量反向伪造报工（当前系统无逐次报工历史，无法真实恢复）；历史 execution 数据保留为历史 projection；WorkReport 从 P2 正式上线后积累真实事实。

## 23. 测试及结果

| 测试 | 覆盖 | 结果 |
|---|---|---|
| WorkReportStatusEnumTest（3 例） | 映射/fromCode/labelOf/未知兼容/仅两态 | ✅ 3/3 |
| ProductionWorkReportMapperTest（4 例） | insert 核心字段/execution+status 查询/dispatchNode 查询/无 reportNo-input-output-org 断言 | ✅ 4/4 |
| **全量 production 包** | 66 例 | ✅ 66/66 BUILD SUCCESS |

**真实 MySQL 验证（事务回滚，0 残留）**：插入（含全部字段）/按 executionId/executionId+status/dispatchNodeId/reporterId 查询/时间排序 reportTime DESC/回滚——全部 PASS。

## 24. compile 结果

`mvn compile` ✅ EXIT=0（0 ERROR）。前端未改（无需 vue-tsc/build）。

## 25. 是否发生 P2-C 越界实现

**❌ 否**。未实现 submit/cancel/update 动作；未改 Execution 数量逻辑/updateExecution/completeExecution/order finished-completed/Dispatch ActionService/DispatchNode 状态/Quality/Trace/production-operation/前端。git 确认 P2-B 仅 10 个新增文件，零修改。

## 26. 是否满足进入 P2-C

**✅ 满足。** 对照验收标准（§33 全绿）：
- production_work_report 表建立 ✅
- 0 正式业务记录 ✅
- 无历史假报工 backfill ✅
- WorkReportStatus 仅 SUBMITTED/CANCELLED ✅（测试断言）
- Entity/Mapper/Read Service 正常 ✅
- 不暴露通用 update/delete CRUD ✅（只读 Controller + Read Service）
- 数量字段 DECIMAL(18,4) ✅（Schema 实测）
- 无 report_no / 无 input/output / 无 org 快照 ✅（测试断言 + 信息_schema）
- dispatchId + dispatchNodeId 存在 ✅
- cancel 审计字段完整 ✅
- 无物理 FK ✅（信息_schema fk_count=0）
- Execution 旧数量逻辑完全没动 ✅
- P1 Dispatch 完全没动 ✅
- 测试通过 ✅ 66/66；compile ✅
- 未提交 Git ✅

## 27. 风险/遗留

| 项 | 等级 | 说明 |
|---|---|---|
| 无阻塞 | — | 未发现阻塞 P2-C 的问题 |
| idx_execution 与 idx_execution_status 部分重叠 | 低 | 组合索引左前缀覆盖单列场景，保留两者为查询计划冗余；P2-C 可 EXPLAIN 复核是否精简 |
| 只读 Controller 权限用 operation-execution:view | 低 | 后续新增 work-report:add/cancel 权限点（P2-C），只读权限届时评估 |

---

*报告完。P2-B 完成，停止等待人工验收。*
