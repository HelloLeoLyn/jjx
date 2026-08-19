# JJX Production P0 Domain Cleanup Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：用户批准的 P0 四项（P0-01~P0-04），0 数据库 schema 变更，不提交 Git
> 状态：代码修改 + 测试 + 验证完成，等待人工验收

---

## 1. 实际修改文件

### 后端（新增 2，修改 4）

| 文件 | 操作 |
|---|---|
| `jjx-server/.../production/enums/QualityInspectionTypeEnum.java` | 新增 |
| `jjx-server/.../production/enums/QualityInspectionResultEnum.java` | 新增 |
| `jjx-server/.../production/service/impl/QualityInspectionServiceImpl.java` | 修改 |
| `jjx-server/.../production/service/impl/ProductionOrderServiceImpl.java` | 修改 |
| `jjx-server/.../production/service/impl/ProductionOperationExecutionServiceImpl.java` | 修改 |
| `jjx-server/.../production/service/impl/DispatchServiceImpl.java` | 修改 |
| `jjx-server/.../production/domain/dto/ProductionOperationExecutionUpdateDTO.java` | 修改 |

### 前端（修改 3）

| 文件 | 操作 |
|---|---|
| `jjx-web/src/views/production/quality/index.vue` | 修改（tag map 补齐类型） |
| `jjx-web/src/views/production/execution/index.vue` | 修改（submitRecord 传 defectiveReason） |
| `jjx-web/src/types/production/operationExecution.ts` | 修改（OperationExecutionUpdateDTO 加 defectiveReason） |

### 测试（新增 3）

| 文件 | 覆盖 |
|---|---|
| `jjx-server/src/test/java/com/jjx/production/QualityInspectionEnumsTest.java` | 枚举映射 |
| `jjx-server/src/test/java/com/jjx/production/ExecutionDtoMappingTest.java` | DTO 映射 |
| `jjx-server/src/test/java/com/jjx/production/DispatchPermissionTest.java` | 初始派工权限 |

---

## 2. P0-01 实施结果（Quality Type / Result Enum）✅

**正式定义**：IQC=来料检验 / IPQC=过程检验 / FQC=完工检验 / OQC=出货检验；PENDING=pending / PASS=pass / FAIL=fail。

**修改内容**：
- 新增两个枚举类，提供 `getCode()/getLabel()/fromCode()/labelOf()`
- **未知历史值兼容**：`fromCode` 返回 null，`labelOf` 原样返回（不抛异常，展示层可回显）
- `QualityInspectionServiceImpl`：getTypeName/getResultName 改走枚举；create()/update()/getStatistics() 的 `"pending"/"pass"/"fail"` 裸字符串全部替换为枚举引用
- `ProductionOrderServiceImpl`：完工自动创建质检单 `setInspectionType("FQC")` → `QualityInspectionTypeEnum.FQC.getCode()`；完工门查询 `.eq("FQC")+.eq("pass")` → 枚举引用
- 前端 quality 页面 tag map 补齐：来料检验/过程检验/完工检验/出货检验

**验证**：grep 确认生产主链无裸 `"FQC"` 字符串（仅枚举定义处存在）；TypeScript 未做大改（保持 string，符合"允许增加联合类型但不强求"）。

**未扩展**：无 QMS（无检验标准字典、无检验任务流、无返工流程）——符合 P0 边界。

---

## 3. P0-02 数量调用方调查结果（Production Quantity Semantics）✅

### 调用方全景（实施前已全量搜索）

| 调用点 | 用途 | 口径 |
|---|---|---|
| `ProductionOperationExecutionServiceImpl.updateOrderCompletedQuantity` | 写 completed=Σ合格、finished=最后工序合格数 | 双口径写入方 |
| `ProductionOrderServiceImpl.completeOrder` 事件 quantity | 完工事件 | completed（展示） |
| `ProductionOrderServiceImpl.cancelOrder` 部分完工入库 | 入库 | finished（正确） |
| `ProductionOrderServiceImpl.canCompleteOrder` 完工门 | 完工判断 | finished>0（正确） |
| `InventoryInboundServiceImpl:745` 完工入库 | 入库数量 | **finished 优先**，回退 completed/planned |
| `ProductionCostController` / `ProductionReportController` / 导出 | 展示/统计 | completed |
| **`ProductionOrderServiceImpl.updateOrderStatus(COMPLETED)`** | 手动改状态 | **原强填 completed=planned（问题点）** |

### 最终确认语义（已加注释到代码）

- **planned_quantity** = 订单计划生产数量
- **finished_quantity** = 成品完成数量（最后有效工序/完工检验合格数，052 口径）= **完工/入库/订单完成判断的唯一核心口径**
- **completed_quantity** = 工序合格汇总（历史兼容展示字段，**不得作为成品完成判断依据**）

### 修复

- **移除 `updateOrderStatus(COMPLETED)` 强填**：`order.setCompletedQuantity(order.getPlannedQuantity()); order.setRemainingQuantity(ZERO);` 两行删除，保留 actualEndTime + 状态修改兼容（手动改状态仍可置已完成，但不再制造错误数量）。原因：该接口被 UI（OrderStatusDialog）使用，但后端只收 orderId/orderStatus/remark 三参数，前端填的 completedQuantity 根本传不进来，强填纯属"制造错误生产数量"。
- 未删数据库字段、未迁移历史数据、未动 WorkReport（P2 范围）——符合约束。
- `completed_quantity` 与 `finished_quantity` 未强行等同。

---

## 4. P0-03 映射修复结果（Execution DTO Mapping Bug）✅

**问题**：`updateEntityFromUpdateDTO` 原代码 `if (updateDTO.getRemark() != null) execution.setDefectiveReason(updateDTO.getRemark())`——前端"不良原因"借道 remark 传入，后端误写入 defective_reason。

**修复**：
- `ProductionOperationExecutionUpdateDTO` 新增 `defectiveReason` 字段
- `updateEntityFromUpdateDTO`：`defectiveReason → execution.setDefectiveReason()`；**remark 不再映射 defective_reason**
- execution 实体无 remark 字段 → **remark 不持久化**（报告说明：未为保存 remark 新增表字段，符合"不要为了保存 remark 新增表字段"要求）
- 前端 execution 页 submitRecord：`remark: recordForm.defectiveReason` → `defectiveReason: recordForm.defectiveReason`（用户填写项是"不良原因"，语义归位）
- 前端类型 `OperationExecutionUpdateDTO` 加 `defectiveReason?: string`

**验证**：测试 `ExecutionDtoMappingTest` 反射调 private static 方法确认：defectiveReason 正确写入；仅 remark 时 defective_reason 保持 null。

---

## 5. P0-04 初始派工权限实现方式 ✅

**要求**：消除 `selectById(5L)`；不用 deptName/ancestors 名称硬编码；优先复用现有权限点。

**决策**：**复用现有权限点 `production:dispatch:assign`（派工指派）**，未新增权限点。理由：
- 用户指引"如果已有合适权限，直接复用；如果没有，允许增加明确权限点（如 production:dispatch:initial）"
- 现有权限体系已有 `production:dispatch:list / assign / start`；`assign` 语义="派工指派"，可表达"允许执行生产初始派工"
- 零数据库变更（不新增 sys_menu/sys_role_menu 数据），最符合"0 数据库 migration"

**修改（DispatchServiceImpl）**：
- `page()` 数据权限：`!isProductionManager(username)` → `!hasPermission("production:dispatch:assign")`（超管/有派工权看全量）
- `canAssign(userId)`：超管 → true；`hasPermission("production:dispatch:assign")` → true；`isDispatched(userId)`（被派工过可继续派工，保留）→ true
- `checkDispatchRight(operatorId)`：同上三段式
- **删除 `isProductionManager` 方法**（不再有 selectById(5L)），删除后 sysDeptMapper 仍被 myDeptTree 使用，import 保留

**验证**：grep 确认 `selectById(5L)` 零残留；测试 `DispatchPermissionTest` 覆盖：有 assign 权限可初始派工、超管可派、无权限且未派工不可派。

**注**：用户提到的 P1 模型（初始派工=权限决定第一责任节点；继续派工=ACTIVE 节点决定）未实施——属于 P1，符合"不提前实施"。

---

## 6. 新增/修改测试

| 测试 | 断言 | 结果 |
|---|---|---|
| QualityInspectionEnumsTest（4 例） | IQC/IPQC/FQC/OQC 映射；pending/pass/fail 映射；未知值兼容 | ✅ 4/4 |
| ExecutionDtoMappingTest（2 例） | defectiveReason→defective_reason；remark 不再写入 | ✅ 2/2 |
| DispatchPermissionTest（3 例） | assign 权限可派、超管可派、无权限不可派 | ✅ 3/3 |
| **合计** | | **9/9 通过** |

**测试基础设施说明**：Mockito 在 JDK 25 下无法 mock JdbcTemplate（inline mock 不兼容），现有项目测试均未 mock JdbcTemplate。为遵守"成本异常高则用最小可验证方式"：
- ExecutionDtoMappingTest：改为反射调用 private static `updateEntityFromUpdateDTO`（精确覆盖 P0-03 改动点）
- DispatchPermissionTest：通过反射构造注入 null 依赖，`isDispatched` 内部 try/catch 对 null 安全返回 false，恰好覆盖"未派工过"场景；权限判断用 `mockStatic(SecurityUtils)`

---

## 7. compile / test / build 结果

| 项 | 结果 |
|---|---|
| 后端 `mvn compile` | ✅ EXIT=0 |
| 新增测试 `mvn test -Dtest=...` | ✅ 9/9 通过，BUILD SUCCESS |
| 前端 `npx vue-tsc --noEmit` | ✅ TSC_EXIT=0（0 errors） |
| 全量后端测试 | 未跑（现有 sales/inventory 测试与本轮改动无交集，且成本高；用户要求"与本次修改相关的测试"已覆盖） |

---

## 8. 是否发生数据库变更（必须明确回答）

**❌ 未发生任何数据库变更。**
- 无 DDL（无 ALTER TABLE / CREATE TABLE / 无 migration 文件）
- 无 DML（未插入/更新/删除任何数据行，包括 sys_menu/sys_role_menu——P0-04 复用现有权限点 `production:dispatch:assign`，未新增权限点）
- 数据库 schema 与数据均保持原样

---

## 9. 是否影响现有数据

| 对象 | 影响 |
|---|---|
| 现有派工数据（production_dispatch 3 行） | ❌ 无影响（未动表结构/数据/状态语义） |
| 现有生产订单（4 行） | ⚠️ 极小行为变化：`updateOrderStatus` 手动置"已完成"时不再强填 completed=planned（以前是错误数量，现在保持真实值）；正规完工流程 `completeOrder` 不受影响 |
| 已完成的工序 | ❌ 无影响（数量/状态字段未动） |
| 质检数据（0 行） | ❌ 无影响（存储值未变，仅展示层 label 走枚举） |
| 权限 | ❌ 无影响（复用现有 assign 权限点；原来能派工的人现在仍能派工，且语义更清晰） |

---

## 10. 发现但没有处理的问题（TECH-DEBT）

1. **`updateOrderStatus` 仍接收不到前端 completedQuantity**：Controller 签名只有 orderId/orderStatus/remark，前端 OrderStatusDialog 传的 completedQuantity 被忽略——P0 已移除强填避免制造错误数量，但"手动置完成时传数量"能力本身缺失，留待 P2 统一处理。
2. **execution_status 多维语义**（计划/执行/质量/异常/确认混用一字段）：P0 只加注释锁定，未拆结构（P2/P3 配合）。
3. **magic number 残留**（`orderStatus != 2`、`orderStatus == 8` 等 Service 直接比较）：未动（改动面大收益小）。
4. **质检类型 TS 仍是 string**：未加联合类型（用户允许不强求，避免大改类型系统）。
5. **isDispatched 的 LIKE JSON 查询**：P1 换节点表后改 exists 查询（记录为 P1 必须项）。

---

## 11. 是否满足进入 P1 Dispatch V1 的条件

**✅ 满足**（P0 范围内的前置条件已达成）：
- 质检类型/结果已统一枚举定义（P3 稳定基础）✅
- 完工数量口径已锁定（finished_quantity 唯一核心口径，P2 不会双重累计）✅
- Execution DTO 映射 bug 已修复（remark/defectiveReason 分离，P2 报工数据可信）✅
- 初始派工权不依赖 deptId=5（权限驱动，P1 可直接演进为"初始派工=权限决定第一责任节点"）✅
- 0 数据库变更，向后兼容，现有派工/订单/工序数据无破坏 ✅

**P1 仍需自行解决的**（非 P0 阻塞项）：三级硬编码解除、DispatchNode 节点表、operators JSON 迁移、ASSIGN/DELEGATE/REASSIGN/RETURN 动作拆分——这些按计划属于 P1 工作包。

---

*报告完。未提交 Git，等待人工验收。*
