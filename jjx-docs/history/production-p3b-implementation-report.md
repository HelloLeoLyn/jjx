# JJX Production P3-B Quality Data Model & Foundation Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 基线：HEAD = 8dc8970（P3-A 已拍板）
> 状态：完成，等待人工验收

---

## 1. 实际修改文件

**数据库（1 新增）**
- `jjx-server/sql/migrations/V20260819_003__quality_inspection_execution_link.sql`（已手动执行，无 Flyway）

**后端模型（5 修改）**
- `ProductionQualityInspection.java`（Entity：+executionId/workReportId，数量 Integer→BigDecimal）
- `QualityInspectionCreateDTO.java`（+executionId/workReportId）
- `QualityInspectionUpdateDTO.java`（数量 Integer→BigDecimal）
- `QualityInspectionQueryDTO.java`（+executionId/workReportId 查询过滤）
- `QualityInspectionVO.java`（+executionId/workReportId，数量 BigDecimal）

**后端 Service（2 修改）**
- `QualityInspectionService.java`（接口 +7 个 P3-B 读取方法）
- `QualityInspectionServiceImpl.java`（create 写新字段、page 过滤、toVO 映射、getStatistics/exportPdf 数量 BigDecimal、读取方法实现、checkWorkReportLink 实现、update 标记 LEGACY）

**测试（1 新增）**
- `QualityInspectionP3BTest.java`（10 个测试：Entity 类型/数量 BigDecimal/create 写入/读取能力/关联一致性）

## 2. Migration 做了什么

`V20260819_003__quality_inspection_execution_link.sql`：
1. `production_quality_inspection` 增加 `execution_id BIGINT NULL`（关联工序执行）
2. 增加 `work_report_id BIGINT NULL`（关联报工）
3. `total_qty / pass_qty / fail_qty`：INT → `DECIMAL(18,4)`
4. 新增索引 `idx_execution_id`、`idx_work_report_id`
5. 不加物理 FK（与 P1/P2 migration 一致）

## 3. Migration 前后 Quality 数据量

**前后均为 0 行**（`production_quality_inspection` = 0、`production_quality_inspection_item` = 0）。
→ 无历史兼容负担，未伪造任何 execution/workReport 假关联。

## 4. 最终表结构关键变化

```
production_quality_inspection
├─ order_id        BIGINT NULL   （保留，订单级）
├─ execution_id    BIGINT NULL   ← 新增（IPQC/FQC；IQC/OQC 可空）
├─ work_report_id  BIGINT NULL   ← 新增（IPQC 可空/推荐；FQC=NULL；IQC/OQC=NULL）
├─ total_qty       DECIMAL(18,4) ← INT 改
├─ pass_qty        DECIMAL(18,4) ← INT 改（= 质量认可合格数量）
├─ fail_qty        DECIMAL(18,4) ← INT 改（= 质量判定不合格数量）
└─ 索引：uk_inspection_no / idx_order_id / idx_type / idx_result
        / idx_execution_id（新）/ idx_work_report_id（新）
```

## 5. executionId / workReportId 如何落地

- **Entity**：`Long executionId` / `Long workReportId`，MyBatis-Plus 自动映射；
- **CreateDTO**：客户端可传（可空），Service `create()` 写入；
- **VO**：查询返回；**query 支持按 executionId / workReportId 过滤**；
- **领域约定（P3-A 拍板）**：
  - FQC：orderId + executionId 有值，workReportId = NULL（最终工序级质量判定）
  - IPQC：orderId + executionId 有值，workReportId 可空（工序级或针对某次报工）
  - IQC/OQC：不强制关联，保持兼容
- **不冗余 dispatchNodeId**（责任追溯走 WorkReport → DispatchNode）

## 6. 数量类型如何统一

- 数据库：`DECIMAL(18,4)`（实测 123.4567 精度保留）
- Java：`BigDecimal`（Entity/DTO/VO 全链路）
- Service 内统计/导出：`getStatistics()` 改为 BigDecimal 求和；`exportPdf()` 合格率计算改为 BigDecimal（`>`/`*double` 不适用于 BigDecimal，已改用 `compareTo`/`doubleValue()`）
- grep 确认：无 `Integer totalQty/passQty/failQty`、无 `mapToInt` 残留

## 7. Read Model 提供了什么

`QualityInspectionService` 新增（供 P3-C FQC/IPQC 联动）：

| 方法 | 用途 |
|---|---|
| `listByOrderId(orderId)` | 按订单查质检列表 |
| `listByExecutionId(executionId)` | 按工序执行查 |
| `listByWorkReportId(workReportId)` | 按报工查 |
| `listFqcHistory(executionId)` | 某 execution 的 FQC 历史（倒序） |
| `hasPendingFqc(executionId)` | 是否存在 PENDING FQC |
| `hasPassFqc(executionId)` | 是否存在 PASS FQC |
| `checkWorkReportLink(workReportId, executionId, orderId)` | 关联一致性校验：workReport.executionId == quality.executionId 且 workReport.orderId == quality.orderId；任一空/缺失 → false |

**放在现有 QualityInspectionService**（不新建 ReadService，避免为分层而分层，符合 P3-B 指令）。

## 8. 是否修改 Order / Execution / WorkReport / Dispatch

**全部未修改。**
- ProductionOrderServiceImpl / ProductionOperationExecutionServiceImpl / WorkReportActionServiceImpl / DispatchActionServiceImpl：0 改动
- WorkReport 数量语义（生产自报）、Execution qualified（WorkReport projection）：保持 P2 不变

## 9. 是否发生 P3-C 越界

**否。** 本轮未做：
- ❌ 自动创建 FQC/IPQC
- ❌ 修改 Execution/Order complete
- ❌ 修改 finished_quantity
- ❌ PASS/FAIL 判定动作、复检、Quality gate、FAIL 后恢复生产
- ❌ 前端 Quality 业务流程改动
- ❌ P4 Trace / QMS / AQL / SPC / 返工 / 报废审批

仅将现有 `update()` 标记为 **LEGACY（P3-C 待收口）**，未强删，保持现有 Quality 页面兼容。

## 10. 测试与 compile 结果

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0 |
| production 全量测试 | ✅ **95/95**（85 基线 + 10 新 P3-B） |
| 真实 DB 冒烟（事务回滚） | ✅ 新字段可写（IPQC 带 execution/workReport）、FQC 形态（execution 有值/workReport=NULL）、DECIMAL(18,4) 精度、回滚后 0 残留 |
| grep | ✅ 无 Integer/mapToInt 数量错配残留 |

## 11. 是否满足进入 P3-C

**✅ 满足。** Quality 现在能准确回答"检的是哪一道工序（execution_id）、哪一次报工（work_report_id）"，读取能力齐备，数量体系与报工一致（DECIMAL(18,4)/BigDecimal），且未触碰生产状态（P3-C 的 gate/判定/复检/自动建单可在干净基础上实施）。

## 12. 真正需要人工决定的问题

1. **本轮未实施 P3-C 的"已判定质检不可变"**：现有 `PUT /production/quality` 仍可任意覆盖 result/数量（LEGACY 标记）。P3-C 是否直接加状态守卫（pass/fail 后禁止直接改，改走复检新单）？——需 P3-C 拍板。
2. **inspection_no 生成**：当前 create 用 `QCI+yyyyMMddHHmmss` 时间戳，FQC 自动建单（P3-C）若同秒多单可能冲突——P3-C 是否改生成规则（加随机后缀）？
3. **数量 DECIMAL 化后前端**：QualityVO 前端 `totalQty/passQty/failQty` 仍为 number（BigDecimal JSON 序列化为数字，兼容），P3-D 前端如需小数展示再调。
4. **无历史假关联**：quality 0 行，未做任何 execution/workReport 回填（符合指令）。

---

*报告完。等待人工验收后进入 P3-C（Quality Actions & Production Gate）。*
