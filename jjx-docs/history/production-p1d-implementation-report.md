# JJX Production P1-D API & Frontend Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P1-D1 后端 API 收口 + P1-D2 前端切换（四动作 UI/责任链 Timeline/移除固定 level）
> 状态：完成，等待人工验收

---

## 1. 实际修改/新增文件

### 后端
| 文件 | 操作 | 说明 |
|---|---|---|
| `domain/dto/DispatchAssignV1DTO.java` | 新增 | 正式 ASSIGN 入参（executionId/orderId/targetUserId/equipmentId/remark，**无 level/transferFrom**） |
| `controller/DispatchController.java` | 修改 | +`POST /assign-v1`（正式 ASSIGN，Swagger 标注 V1 专用）；`/assign` 标注 Legacy |
| `service/DispatchService.java` | 修改 | +assignV1 接口；assign 注释标 Legacy |
| `service/impl/DispatchServiceImpl.java` | 修改 | +assignV1 实现；+allowedActions 计算（buildAllowedActions） |
| `domain/vo/DispatchVO.java` | 修改 | +allowedActions 字段 |
| `domain/dto/DispatchAssignDTO.java` | 修改 | 注释标 Legacy compatibility DTO |
| `test/.../DispatchV1DtoStructureTest.java` | 新增 | DTO 结构检查（4 例） |
| `test/.../DispatchAllowedActionsTest.java` | 新增 | allowedActions 投影（6 例） |

### 前端
| 文件 | 操作 | 说明 |
|---|---|---|
| `api/production/dispatch.ts` | 修改 | +assign-v1/delegate/reassign/return/nodes/current-node API；+V1 DTO 类型；DispatchVO 类型 + allowedActions/currentAssignee |
| `views/production/dispatch/index.vue` | 重写 | V1 页面（四动作/scope/责任链 drawer/currentAssignee 列） |
| `components/OperatorChain/index.vue` | 重写 | nodes 优先渲染（legacy fallback 兼容），移除 firstOnly/level 语义 |

---

## 2. P1-D1 后端 API 收口

### A. 正式业务 API（Dispatch V1 前端使用）

| METHOD | PATH | 说明 |
|---|---|---|
| POST | `/production/dispatch/assign-v1` | 初始派工 ASSIGN（**无 level/transferFrom**） |
| POST | `/production/dispatch/{id}/delegate` | 继续派工 DELEGATE（targetUserId/remark） |
| POST | `/production/dispatch/{id}/reassign` | 改派 REASSIGN（targetUserId/reason） |
| POST | `/production/dispatch/{id}/return` | 退回上级 RETURN（仅 reason，**无 targetUserId**） |
| GET | `/production/dispatch/page` | 分页工作台（+currentAssignee/allowedActions projection；+scope=mine） |
| GET | `/production/dispatch/{id}/nodes` | 责任链历史（Node-first） |
| GET | `/production/dispatch/{id}/current-node` | 当前 ACTIVE 责任人 |
| GET | `/production/dispatch/{id}` | 详情（含 projection） |

### B. Legacy compatibility API（保留，V1 前端不调用）

`POST /assign`（DispatchAssignDTO，含 level/transferFrom，P1-C adapter）、`POST /batch-assign`、`POST /{id}/reject`（整单退回）、`PUT /order/{orderId}/team`、`GET /underlings/{uid}`、`GET /team-persons/{tid}`、`GET /my-persons`、`GET /my-depts`、`GET /can-assign`、`GET /order/{orderId}/pending`

- 代码标记：`Legacy compatibility adapter. Do not use from Dispatch V1 frontend.`（Controller/DTO/Service 三处）

### C. Internal/migration diagnostic API

`GET /{id}/compare-node-legacy`（@Operation hidden，业务 UI 不出现，前端禁止调用；P1-E 后决定移除）

---

## 3. 正式业务 API 清单

见 §2-A。关键要求落实：
- 新前端动作 DTO 绝对不含 level/transferFrom（测试断言：4 个 DTO 反射扫描字段）
- DELEGATE=targetUserId+remark；REASSIGN=targetUserId+reason；RETURN=仅 reason（测试断言无 targetUserId）
- ASSIGN=executionId/orderId/targetUserId/equipmentId/remark（V1 DTO）

## 4. Legacy API 清单

见 §2-B。未删除任何端点（旧客户端不报错）；P1-E/Final Gate 决定删除范围。

## 5. diagnostic API 清单

`GET /{id}/compare-node-legacy`（hidden；P1-E cutover 前人工使用）

## 6. allowedActions 是否实现及方案

**✅ 已实现**（DispatchVO.allowedActions: string[]）。

| 动作 | 条件（与 ActionService 权限一致） |
|---|---|
| ASSIGN | 无 ACTIVE 且（超管 OR 有 assign 权限） |
| DELEGATE | 有 ACTIVE 且（超管 OR 有 assign 权限 OR ACTIVE 本人） |
| REASSIGN | 有 ACTIVE 且（超管 OR 有 assign 权限 OR ACTIVE 本人） |
| RETURN | 有 ACTIVE 且 parentNodeId!=null 且（超管 OR ACTIVE 本人）——管理员代操作不可 RETURN（与后端一致） |

- 前端按钮显隐用；**真正权限仍由后端 ActionService 校验**（安全边界在后端）
- 测试：6 例覆盖（无 ACTIVE 有/无权限、本人、无关用户、root 不可 RETURN、管理员可 DELEGATE/REASSIGN 不可 RETURN）

## 7. P1-D2 页面结构

保留 JJX 后台风格（表格工作台，非 APS）：
- 顶部筛选：工单号/工序关键字/状态 + **scope 切换（全部相关 / 我的当前任务）**
- 主表列：工单号/数量/工序/设备/**当前责任人（+所属组织）**/状态/指派时间/操作
- 操作列按 `allowedActions` 动态渲染（初始派工/继续派工/改派/退回上级/开始/完成/拒绝派工/责任链）
- 详情用 Drawer（责任链 Timeline）

## 8. currentAssignee 展示

- 列表列：`row.currentAssigneeName` + `row.currentOrgName` tag（**不 parse operators**）
- 详情 Drawer「当前责任」卡片：责任人/组织/当前负责 tag/开始时间/指派人

## 9. 全部相关 / 我的当前任务

- `query.scope`：空=全部相关（我指派过或参与过的可见任务）；`mine`=我的当前任务（ACTIVE assignee=我）
- UI 文案：`全部相关` / `我的当前任务`（radio 切换）——"历史参与过"不冒充"我的待办"

## 10. Responsibility Timeline

- Drawer 责任历史用 `el-timeline`（来自 `/nodes`，后端已按 responsibility history 稳定排序，前端不自行按 level 排）
- 每节点：assigneeName/orgName/nodeStatus tag/指派时间区间（assignedAt-closedAt）/assignedByName/remark
- NodeStatus tag：ACTIVE=当前负责(绿)、DELEGATED=已下派、REASSIGNED=已改派、RETURNED=已退回、COMPLETED=已完成、CANCELLED=已取消
- **Timeline 而非组织树**（RETURN/REASSIGN 多持有实例按时间平铺）

## 11-14. 四动作 UI

| 动作 | 弹窗 | 提交 |
|---|---|---|
| 初始派工 | 工序/设备/责任人/备注（"确定第一责任人"） | `assignDispatchV1`（**无 level**） |
| 继续派工 | 当前责任人展示 + 派给（人员选择）/备注 | `delegateDispatch`（parent 由系统=当前 ACTIVE） |
| 改派 | 当前责任人 → 新责任人/原因 | `reassignDispatch`（当前 ACTIVE 是唯一 from，无 transferFrom） |
| 退回上级 | 当前责任人 + 退回原因（必填）；**无目标人选择** | `returnDispatch`（root 时按钮不显示，后端仍拒绝） |

## 15. OperatorPicker 复用

✅ 复用现有 `OperatorPicker`（部门树勾选）+ `getMyPersons` 候选；文案改为"选择责任人/派给人员"；未重做组织人员组件。DELEGATE/REASSIGN 候选范围由后端校验（前端只是 UX 便利）。

## 16. NodeStatus 映射

独立 mapper（`NODE_STATUS_LABELS`，与 DispatchStatus/ExecutionStatus 分离）：ACTIVE=当前负责/DELEGATED=已下派/REASSIGNED=已改派/RETURNED=已退回/COMPLETED=已完成/CANCELLED=已取消。

## 17. DispatchStatus 旧语义 UI 处理

- 未改数据库 enum code
- UI 文案收敛：TEAM_ASSIGNED(1) 和 ASSIGNED(2) 都显示为 **「已派工」**（不再表达层级）；P1-E/Final Gate 后评估状态清理

## 18. start/complete/reject 保留方式

- start/complete：保留现状（按钮按 dispatchStatus 2/3 显示）
- 旧 reject 改名 **「拒绝派工（整单退回）」**，与 RETURN「退回上级」**明确区分**（弹窗内红字说明差异）；RETURN=责任退回上一级，REJECT=整单回到未派工

## 19. operators 前端依赖清理

- 主页面：**0 处** parseOperators / JSON.parse(row.operators) / operator.level（grep 验证）
- OperatorChain：改为 nodes 优先（legacy fallback 仅在后端返回兼容 DTO 时展示，组件不自己判断 NODE/LEGACY）
- 其他历史页面（非生产派工主流程）未强制清理——P1-E 报告中列残留

## 20. fixed level UI 清理

- 主页面 grep：`level`/`第2级`/`第3级`/`追加`/`transferFrom` **0 残留**（grep 验证）
- 转派弹窗（transferFrom + 级别）、链完整性开关、"＋N级"标签全部移除
- OperatorChain 移除 firstOnly/"＋N级"旧语义

## 21. 新 API 调用情况

前端正式动作全部调用 V1 API：assign-v1/delegate/reassign/return；读取用 page（projection）+ nodes + current-node；scope=mine 传 page 参数。旧 assign/batch-assign 仅批量弹窗保留（batch-assign 是 legacy 快捷入口，P1-E 评估）。

## 22. legacy adapter 残留（逐项）

| 位置 | 状态 |
|---|---|
| `POST /assign`（DispatchAssignDTO） | 保留，标注 Legacy；P1-D 前端不用 |
| `POST /batch-assign` | 保留（批量弹窗在用，内部走 adapter） |
| `appendLevel/mergeChain/levelOfUser`（DispatchServiceImpl） | 保留（adapter 依赖），标注 legacy；P1-E 评估删除 |
| `page?viewType` | 未引入（scope=mine 已够） |

## 23. 前端/后端测试

**后端新增 10 例**：DispatchV1DtoStructureTest（4：ASSIGN/DELEGATE/REASSIGN/RETURN DTO 结构）+ DispatchAllowedActionsTest（6：权限投影）。
**全量 production 包 61/61 通过**（含 P0/P1-A/B/C 全部既有测试）。

## 24. compile/type-check/build

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0 |
| `mvn test`（production 61 例） | ✅ BUILD SUCCESS |
| `vue-tsc --noEmit` | ✅ 0 errors（P1-D 3 个文件专项检查无类型错误） |
| `vite build` | ⚠️ 失败——**历史遗留文件问题**（MaterialCategory.vue 空文件、standard-process/index.vue、OrderDetailDialog.vue，均非本次改动，git 确认）；与 P0/P1-A/B/C 相同的既有状态，vue-tsc 为本项目实际可用静态验证 |

## 25. browser 验证

**未执行**：browser attach-only（用户 Chrome 未运行），按评审要求不修改浏览器环境。改用：vue-tsc + grep 验证 + 后端测试 + 前端代码审查替代（§23/§19/§20）。

## 26-30. 关键状态回答

- **schema 是否变化**：❌ **否**（0 migration；git 确认仅 P1-A 的 V20260819_001）
- **是否执行 backfillAll**：❌ **否**（node 表 0 条业务数据）
- **是否删除 operators**：❌ **否**（保留列；仅由 Node projection 同步）
- **新 UI 是否仍依赖 level**：❌ **否**（grep 0 残留）
- **新 UI 是否仍解析 operators 判断当前责任人**：❌ **否**（当前责任人全部来自 currentAssigneeName projection；主页面 0 处 parseOperators）

## 31. 是否满足进入 P1-E

**✅ 满足。** 验收标准全绿：
- 正式四动作 API 清晰（assign-v1/delegate/reassign/return）✅
- 新前端不再通过旧 assign 推断动作（全 V1 API）✅
- 当前责任人来自 Node projection ✅
- 责任历史来自 /nodes API ✅
- 全部相关与我的当前任务分离（scope）✅
- ASSIGN/DELEGATE/REASSIGN 不含 level/transferFrom（测试断言）✅
- RETURN 不允许选择目标人（测试断言）✅
- 旧固定 1/2/3 级 UI 消失（grep）✅
- OperatorChain 不再以 operators JSON 为主（nodes 优先）✅
- NodeStatus 展示正确（独立 mapper）✅
- 旧 REJECT 与 RETURN 语义区分（拒绝派工 vs 退回上级）✅
- legacy-only 数据仍能查看（fallback 保留）✅
- 新写动作能 on-write adoption（P1-C）✅
- 正式 backfill 未执行 ✅；operators 未删除 ✅；无 schema change ✅
- P2/P3/P4 未越界 ✅
- 后端测试 61/61 ✅；前端 vue-tsc 0 errors ✅
- 未提交 Git ✅

## 32. 风险/遗留

| 项 | 等级 | 说明 |
|---|---|---|
| vite build 历史失败 | 低 | 3 个历史文件问题（非 P1-D），vue-tsc 通过；建议 P1-E 后单独清理 |
| batch-assign 仍走 legacy adapter | 低 | 批量快捷入口保留；P1-E 评估是否 Node 化 |
| OperatorChain 组件已无引用 | 低 | dispatch 页面改用 currentAssignee 列 + drawer；组件保留供其他模块/复用 |
| allowedActions 与后端权限重复 | 低 | 设计如此（前端 UX，后端安全边界）；后端 ActionService 是唯一权威 |
| TEAM_ASSIGNED/ASSIGNED 文案合并 | 低 | 仅 UI 文案（都显示"已派工"），DB enum 未动 |

---

*报告完。P1-D 完成，停止等待人工验收。*
