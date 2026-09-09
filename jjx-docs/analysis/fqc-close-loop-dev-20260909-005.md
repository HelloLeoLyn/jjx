# FQC 闭环方案 v3：零新表（dev-20260909-005）

状态：📋 v3 待 Leo 确认（v2 被否：加表过多，98 表已多；改为类型/列级扩展复用现有模型）

## v3 原则（2026-09-09 18:38 Leo 批评后重设计）

- 0 新表：分批检验/不良台账/返工轮回全部用现有表 + 类型字段/少量加列表达
- 复用弹药（已核实存在）：
  - QualityDispositionEnum：INTERNAL_SORT(内部返工)/SCRAP/ CONCESSION/ HOLD 等（FQC 默认 INTERNAL_SORT）
  - production_quality_inspection：inspection_type(FQC/IQC/IPQC/OQC) + source_type/source_id + disposition + result/pass_qty/fail_qty + previous_inspection_id（复检链）+ review_status
  - production_operation_execution + production_task 树 + WorkReport + 完工自动 createFqc：完整生产生命周期，返工直接复用
- 加列（共 3 列，克制）：
  1. production_quality_inspection.remaining_fail_qty：不良待处置余量（判定 fail 时=fail_qty；处置递减到 0 = 台账清）
  2. production_operation_execution.execution_type：NORMAL / REWORK（默认 NORMAL）
  3. production_operation_execution.source_inspection_id：返工执行的来源 FQC 单（复检链锚点）
- 不加列方案替代：成品累计 = finished（口径Y 累计写 Σpass）；未检/不良余额 = 详情页实时聚合查询

## 流程（对比 v2 不变，载体变了）

FQC 判定（全检、分批=多张 FQC 单；挂 execution）
  ├─ pass → finished 累计 + pass（可超计划）
  └─ fail → remaining_fail_qty=fail_qty（本单即台账，disposition 默认 INTERNAL_SORT）
       处置动作（对同一张 FQC 单可多次，余量递减）：
       ├─ 报废 SCRAP：remaining 清零 → 单关闭（审计 @Log）
       └─ 返工 INTERNAL_SORT：生成 REWORK 执行（process 默认最后工序，数量≤remaining）
              → 任务树分派 → 返工报工 → 完工 → 自动 createFqc（挂 REWORK 执行，previous_inspection_id=来源单）
              → 复检判定：pass → finished 累计、来源单 remaining 递减（清账）
                          fail → 再轮回（remaining 不退，继续处置/转报废；轮次=previous_inspection_id 链长）
补产（可选）：报废致欠产 → 新建 REWORK/补产执行补足（数量=缺口），并入同一轮回机制

## 完工防漏门（completeOrder 校验）

1. 状态=进行中；2. 全部有效 execution（NORMAL+REWORK）COMPLETED；
3. 无 PENDING FQC（无在检）；4. 全部 fail 单 remaining_fail_qty=0（不良处置清）；
5. ΣFQC pass（含复检）≥ 计划（只能多不能少）；——缺项列出原因

## 待 Leo 确认（2 点）

A. 处置粒度：一张 FQC 单的 fail 量，处置动作可多次分批（返工 X → 再返工 Y → 剩 Z 转报废）→ remaining 递减；
   还是一张单只允许一个处置结论（全返工 or 全报废，分批靠多开 FQC 单）？
B. 报废/返工造成的欠产（成品<计划）时：完工门直接拦（提示补产），还是自动创建补产执行？(建议：完工门拦 + 提供一键补产入口，不自动)
