# 成品检验「判定可合格上界」闸门 —— 一期设计方案（dev-20260923-021）

> 任务：dev-20260923-021（P1，看板 2235）｜缺陷来源：dev-20260923-020（task 2234）
> 依赖口径：ISO 9001:2015 §8.7（不合格输出控制）｜任务基线：022「判定即出单」（Hermes）
> 性质：**一期设计稿（本文）+ 一期实现**（上界校验 / 复检默认不良量 / 可操作提示）

---

## 1. 问题（实测证据）

成品 JST270MEOO / 工单 WO-PL260923001-01 真实链条：

| 时刻 | 检验批（版本） | 判定 | 出单 |
|---|---|---|---|
| 11:43 | QL260923006 (v1) | 100 合格 | FI02 100 → 作废(9) |
| 11:49 | QL260923007 (v2，复检自 v1) | **98 合格 / 2 不良**（MA=2，外观颜色） | FI03 98 |
| **12:08** | — | **NCR260923001 处置：SCRAP 2 件，DONE** | — |
| 15:45 | QL260923008 (v3，复检自 v2) | **100 全合格** ❗ | FI04 100 → 16:12 红冲 |
| 16:42 | QL260923009 (v4，复检自 v3) | **100 全合格** ❗ | FI05 100 → 库存 +100 |

**结果**：报废单说 2 件报废，库存却按 100 件良品入账 → **库存虚高 2 件**（成品汇总 200，实际可用 198）。

**根因**：`applyJudgement()` 只校验「检验数量 ≤ 批批量」「合格+不良=检验数量」，**不校验"链上已处置的不可回收量"**；复检换代又采用「被取代批不计账」口径 → 已判报废的量在新版本里被重新判成合格。

---

## 2. 业内口径（本方案的依据）

| 原则 | 出处 | 本系统落点 |
|---|---|---|
| 不合格品处置动作固定（返工/让步/报废…），且**需授权批准、必须留记录** | ISO 9001:2015 §8.7.1 / §8.7.2 | 已有 `quality_ncr_action`（REWORK/CONCESSION/SCRAP + DONE） |
| 处置结论 = 一次性「使用决策」，做出后**冻结**；要改必须**撤销（reset）**并留痕 | SAP QM（Usage Decision） | **二期**（-022：冻结 + 撤销流） |
| **数量守恒**：已报废的数量不会因为"换版本/重判"回到良品；回收的唯一合法路径是**返工 + 复检合格** | 数量守恒（GMP/QSR/IATF 同源要求） | **本文一期**（上界校验） |

**一期只做"护栏"，不动冻结模型** —— 目标是：**让"把报废洗回良品"这个动作在系统里不可能通过**。

---

## 3. 口径定义（一期定稿）

**四个数量**（工单/批次通用，写入 `-024` 的对账栏）：
`计划量 Planned`｜`投入(报工) Reported`｜`良品 Good`｜`不良 Defect（→ 报废 Scrap / 返工 Rework / 让步 Concession）`

**可合格上界（本文核心公式）**：

```
可合格上界 upperBound
  = 批批量 lotQuantity
  − Σ(本批链上「已报废且未回收」量)          ← SCRAP 且 DONE，且未被返工复检回收
  − Σ(本批链上「让步接收但客户未确认」量)    ← CONCESSION 且 DONE 且 customer_confirmed ≠ 1
```

**"已回收"的判定**：REWORK 处置（DONE）视为**可回收**——其回收体现为**返工后新建的复检批**的合格量，因此 REWORK 量**不从上界扣减**；同时要求 `reworkExecutionId` 非空（有真实返工执行）。

**"批链"的判定**：沿 `parent_lot_id` 向上回溯到根批（限 50 跳，防脏数据成环）→ 链上全部 lotId 集合。

---

## 4. 一期范围 / 非范围

**做（本文）**
1. `applyJudgement()` 增加校验：`passQuantity > upperBound` → 拒绝，并给**可操作提示**；
2. 新增只读接口 `GET /quality/lot/{lotId}/judgement-guard` → 供前端**预填**与展示提示；
3. 前端判定弹窗：**默认按"上界 = 合格、批量 − 上界 = 不良"预填**（即"复检默认不良量"），并在上界 < 批量时显示警示条；
4. 纯函数抽取 `JudgementBoundCalculator` + 单元测试（无 DB 依赖）。

**不做（后续）**
- 二期（`-022`）：处置结论冻结 + 显式撤销流 + 失效批随批 VOID + 处置入口守卫 + 与 `-018` 合流重算工单有效合格；
- 三期（`-023`）：数量守恒巡检门禁（投入 = 良品 + 报废 + 返工在制 + 在制）。

---

## 5. 落点

| # | 文件 | 改动 |
|---|---|---|
| 1 | `quality/service/support/JudgementBoundCalculator.java` | **新增**：纯函数 `upperBound(lotQty, scrapDone, concessionUnconfirmed)`、`suggestedFail(...)`、`normalize(...)` |
| 2 | `quality/dto/vo/JudgementGuardVO.java` | **新增**：`lotId/lotNo/lotQuantity/scrappedQuantity/concessionPendingQuantity/upperBound/suggestedPass/suggestedFail/message/blocking` |
| 3 | `quality/mapper/QualityNcrActionMapper.java` | **新增** `sumDoneActionsByLotIds(lotIds)`：按 `action_type` 汇总 DONE 处置量（含 `customer_confirmed` 的已确认量） |
| 4 | `quality/service/QualityLotService(+Impl)` | **新增** `evaluateJudgementGuard(lotId)`；`applyJudgement()` 内加上界校验（行锁内，校验在写库前） |
| 5 | `quality/controller/QualityLotController.java` | **新增** `GET /{lotId}/judgement-guard`（`quality:lot:view`） |
| 6 | `jjx-web/api/quality/lot.ts` | **新增** `judgementGuard(lotId)` |
| 7 | `jjx-web/views/quality/lot/components/LotWorkbench.vue` | `openJudge()` 拉上界 → 预填 合格/不良 + 警示条；接口失败**不阻塞**（退回原行为） |

---

## 6. 提示文案（可直接用）

**后端拒绝时**：
> 本批可判合格上限 **98**（批批量 100 − 已报废 2）。已报废数量不可回填良品；如需放行这 2 件，请先登记**返工处置（REWORK）**并走返工复检，或按**让步接收**处理（需客户确认）。

**前端警示条（上界 < 批量时）**：
> ⚠ 本批链上有 **2** 件已报废（不可回收），可判合格上限 **98**；已按上限预填，请核对后再提交。

---

## 7. 边界与例外

1. **REWORK 通道**：有 `reworkExecutionId` 的 REWORK DONE → 不扣减上界（回收走新复检批）；
2. **让步接收**：`customer_confirmed = 1` 的 CONCESSION → 不扣减；未确认的 → 扣减（客户没同意就不能算良品）；
3. **无 NCR 的正常批**：上界 = 批批量 → 行为与今天**完全一致**（不影响存量流程）；
4. **脏数据容忍**：链回溯限 50 跳；NCR/处置查询失败 → **降级为"不校验"并打 WARN**（宁可不挡，不做错杀）；
5. **不动数据、不动表结构**（上界实时按 NCR 汇总，避免第二真源 —— 与库存 `-017` 同口径）。

---

## 8. 验收用例（重启后手工 + 单测）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 复刻本案：v2 判 98/2不良 → 报废 DONE → 对 v3 判定填 **100** | **被拒绝**，提示"可判合格上限 98…" |
| 2 | 同上，填 **98/2** | 通过；不良进台账；入库 98 |
| 3 | 登记 REWORK DONE（带返工执行）后新建复检批，填 100 | **通过**（返工回收通道） |
| 4 | 正常批（无 NCR 处置）：填 100/0 | 通过，行为不变 |
| 5 | 让步接收未客户确认 → 上界扣减；客户确认后 → 不扣减 | 两态切换正确 |
| 6 | 单测：`JudgementBoundCalculatorTest` | 通过（含 0/负数/超额/边界） |

---

## 9. 风险与协作

- **误挡风险**：会挡掉"整批全好"的填法 —— 这是**故意的**（业内口径如此）；提示里给明出路（REWORK / 让步）；
- **与 022 的分工**：022 的"差额入库"逻辑不变；一期只在**判定**入口加上界护栏，属最小侵入；
- **与 020/018/029**：020 = 本缺陷；018/029 = 二期合流；本设计不碰它们的落点；
- **回归**：`npm run validate`（含 `check:lot:strict`、`check:stock:strict`）必须全绿；判定/复检既有用例（并发复检、CLOSED 重开）不受影响。


---

## 10. 一期实施记录（2026-09-23 17:2x，同日落地）

**已实现（本稿的一期范围）**
| # | 文件 | 改动 |
|---|---|---|
| 1 | `quality/service/support/JudgementBoundCalculator.java` | 新增纯函数：`upperBound / suggestedFail / needWarning / safe` |
| 2 | `quality/dto/vo/JudgementGuardVO.java` | 新增护栏只读视图 |
| 3 | `quality/mapper/QualityNcrActionMapper.java` | 新增 `sumDoneActionsByLotIds`（按 action_type 汇总 DONE 处置量 + 已确认量） |
| 4 | `quality/service/QualityLotService(+Impl)` | 新增 `evaluateJudgementGuard(lotId)`；`applyJudgement()` 增加"合格 ≤ 可判合格上界"校验（行锁内、写库前），异常降级为不校验并打 WARN |
| 5 | `quality/controller/QualityLotController.java` | 新增 `GET /quality/lot/{lotId}/judgement-guard`（`quality:lot:view`） |
| 6 | `jjx-web/api/quality/lot.ts` | 新增 `judgementGuard(lotId)` + `JudgementGuardVO` 类型 |
| 7 | `jjx-web/views/quality/lot/components/LotWorkbench.vue` | 判定弹窗：打开即取上界 → 有不可回收量时**按上限预填（合格=上界、不良=差额）** + 黄色警示条 + 标签显示"上限"；提交前客户端再拦一道；接口异常静默降级 |

**验证**
- `mvn -o -q test -Dtest=JudgementBoundCalculatorTest` → **Tests run: 8, Failures: 0, Errors: 0** ✅；
- 强制删 class 重编后核验：`QualityLotServiceImpl.upperBound` / `JudgementBoundCalculator.upperBound` / `QualityNcrActionMapper.sumDoneActionsByLotIds` 均在 class 内 ✅（防"maven 跳编/IDE 写旧 class"）；
- `npx vue-tsc --noEmit` **0 错**；`npm run validate` **全绿**（status-enums / docs / collation:strict / stock:strict / lot:strict / vue-tsc）；
- **未打包、未重启** → 需重启后端后按第 8 节用例做实机验证（尤其用例 1：复检填 100 应被拒）。

**遗留（属二期 `-022`）**：处置结论冻结 + 显式撤销流、失效批随批 VOID、处置入口守卫、与 `-018` 合流重算工单有效合格。
