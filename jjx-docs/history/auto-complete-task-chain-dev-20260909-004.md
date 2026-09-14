# 任务完成链简化：中间节点自动完成 + 工序完工收口根任务（dev-20260909-004）

状态：✅ 已实施（待用户重启后端生效）

## 背景（2026-09-09 用户报告）

工单 WO-PL2609090001-01 工序 P02：工人 assembly_op1 报工通过（T003 COMPLETED）后，
assembly_mgr（车间主任，T002 负责人）点"完工"报错：
"该工序暂不能完工：父级任务需负责人在电脑端任务管理中确认（负责人：生产中心主任）"。

## 根因

完成确认链要求每级人工点"完成"（自下而上）：工人任务=报工达标自动完成；
管理派工节点（T002/T001）子任务完成后仍要负责人**手动**再点一次"完成"；
工序完工（completeExecution）还要求根任务（T001）必须 COMPLETED。
多级派工链 → 每级一次重复人工点击，且报错文案只提示根任务，误导中间层负责人。

## 拍板口径（2026-09-09 17:22-17:26 用户裁定）

1. **中间节点自动完成**：管理节点（子任务全完成 + 达标 + 无待审 + 无剩余）自动 COMPLETED，
   沿父链逐级向上传导，**到根任务为止**（根不自动）。
2. **根任务收口 = 工序"完工"按钮**：点完工时跑完整校验（同 complete 前置 5 项），
   通过则根任务 markCompleted（留痕）+ 工序完工一步到位。
3. **完工权限**：仅该工序**根任务负责人**（一级负责人，production:all 下具体某人）或
   **超级管理员**（role_key=admin）可完工；中间层（assembly_mgr 等）界面不再出现完工按钮。
4. **历史数据补偿**：一次性把"子任务全完成但父节点仍 ACTIVE"的中间节点批量补齐为 COMPLETED。

## 代码改动

见代码 diff（本日改动均 mvn compile 通过，未打包未重启）：
- ProductionTaskService/Impl：提取 completionBlockers 完成前置；新增 autoCompleteAncestors（审批后向上传导）、
  getRootAssigneeId、completeRootForExecution（收口根任务）；assertExecutionCompletable 改为
  "根可完成即放行（收集全部 blockers）"，删掉误导性"父级任务需负责人确认"恒加文案。
- WorkReportActionServiceImpl.completeTaskWhenQualified：任务被自动完成时触发向上传导（失败仅告警不阻断审批）。
- ProductionOperationExecutionServiceImpl.completeExecution：入口加完工权限校验（根负责人/超管）；
  校验通过后先收口根任务再置工序 COMPLETED。
- MyProductionExecutionVO + pageMyProductionExecutions：新增 canComplete（服务端算：EXECUTING
  && 当前用户=根任务负责人 || admin）。
- 移动端 mobile/order.vue：完工按钮 v-if 改用服务端 canComplete；types 同步。

## 数据补偿（已执行）

- guard 备份：sql/backups/production_task_completechain_20260909-1730.sql
- 补偿条件：非根(parent_task_id NOT NULL) + ACTIVE + 无未完成直接子 + 自身无 PENDING 报工
  + task_quantity <= 自身 APPROVED 量 + Σ已 COMPLETED 直接子任务量；循环执行至 0 行（支持多层链）。

## 验证记录（2026-09-09 17:4x）

- 后端 mvn compile 通过；前端 vue-tsc --noEmit 通过（未打包未重启，待用户重启生效）。
- 数据补偿已执行：命中 2 行（P02-T002 task7 / P04-T002 task9，子任务全完成但父仍 ACTIVE）→ COMPLETED；
  根任务 T001/T004 保持 ACTIVE（等完工按钮收口）；P05-T002（task10，无子任务未派工）保持 ACTIVE 属正常。
- 补偿脚本：sql/migrations/72_auto_complete_mid_tasks.sql（临时表两步法，规避 MySQL 1093 同表子查询限制；
  UPDATE ... JOIN 临时表 形式；幂等，可重复执行）。

## 重启后预期行为

- assembly_op1 报工 → assembly_mgr 审批 → T003 完成 → T002 自动完成（不再手动点）
- 主任（prod_manager）在工序界面点「完工」→ 校验通过 → 根任务 COMPLETED + 工序完工，一步到位
- 非一级负责人（assembly_mgr 等）界面不再显示完工按钮（服务端 canComplete=false，接口层亦有权限拦截）

## ➕ 追加 A+B（2026-09-09 18:00-18:2x，Leo 拍板）：任务级「完成」按钮收口

问题：派工管理列表（dispatch/index.vue，第一层任务）操作栏的「完成」调任务级 complete API（老人工确认链），
与「完工=工序执行收口」双入口且绕过新权限模型；工序执行页（production-operation）「完成」按钮 v-if===1 状态错位（EXECUTING=2 反而不显示）。

- A（去掉任务级完成入口）：后端 allowedActions 投影不再放行 COMPLETE（complete API 保留兑底）；
  前端 dispatch/index.vue 删「完成」按钮 + useDispatchList 删 handleComplete/completeTask/ElMessageBox/orderProcessLabel。
- B（工序执行页收敛）：后端 ProductionOperationExecutionVO + canComplete（enrichExecutionVOs 统一填充：
  EXECUTING && (当前用户=该工序根任务 assignee || admin)，覆盖 list/page/详情）；
  前端 production-operation/index.vue「完成」v-if 改 scope.row.canComplete（同时修复 ===1 错位，EXECUTING 行才显示）；
  types OperationExecutionVO 复用既有 canComplete 声明。
- 验证：mvn compile + vue-tsc --noEmit 通过。未打包未重启，待用户重启生效。

## ➕➋ 口径Y 修复：工单成品数量口径统一（2026-09-09 18:17-18:25，Leo 拍板口径Y）

现象：WO-PL2609090001-01（order 2）工卡显示 计划100/完成500/剩余-400。
根因：updateOrderCompletedQuantity（052口径，工序完工时触发）把 Σ 各已完成工序合格数（5×100）写 completedQuantity，
remaining=计划-Σ 无下限（-400）；finishedQuantity 又被 FQC PASS 覆盖（50），三口径混写互相打架。
- 拍板口径Y：成品完成 = FQC 检验通过数 passQty。
- 改动：
  1. QualityActionServiceImpl.handleFqcPass：成品三件套统一写点——completed/finished=passQty，remaining=max(0,planned-passQty)。
  2. ProductionOperationExecutionServiceImpl：删除 updateOrderCompletedQuantity 方法及工序完工调用（该口径历史毒源）。
  3. ProductionOrderServiceImpl：注释口径更新（质检门④/取消入库 052 → FQC PASS passQty）。
- 数据校正（已执行，guard 备份 sql/backups/production_order_fqccomplete_20260909-1818.sql）：
  order 2 → completed=50, finished=50, remaining=50（对齐 FQC inspection 4 pass 50）。
  全库排查：无其它 remaining<0/completed>finished 工单。
- mvn compile 通过，未打包未重启。
- 行为变化提示：工序完工不再实时写工单已完成；FQC 判定 PASS 后才写入成品数（工单完成口径=FQC）。
