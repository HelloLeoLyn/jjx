# 成品检验·二期：处置结论冻结 / 随批作废 / 换代重算（dev-20260923-022）

> 任务：dev-20260923-022（P2，看板 2236；含并入的 dev-20260922-029、现象卡 dev-20260923-018）
> 上游：一期 dev-20260923-021（判定可合格上界闸门，已实现）
> 性质：**设计稿 + 本期实现记录**（撤销流留待下一步）

---

## 1. 为什么做（三条证据）

| # | 现象 | 证据（2026-09-23 实测） |
|---|---|---|
| 1 | 复检换代把**已判报废**量洗回良品 | 021 已挡（判定上界闸门）；根因链见 `-020` |
| 2 | **失效批上的不良单没人收口**（`dev-20260922-029`） | 批被复检取代后，其 NCR 仍挂 `PENDING/DISPOSING`；此时点一次**让步接收**就能把"已经不存在的货"加进良品库存（库存虚增） |
| 3 | 换代/红冲后**工单完工数不重算**（`dev-20260922-018` / `-018`） | 工单 `WO-PL260923001-01` 显示完工 **200**，但有效合格累计只剩 **100**（008 已作废、009 未判定）。完工口径唯一入口 `syncFinishInbound()` 只在**判定**时被调用，换代不触发 |

---

## 2. 业内口径

1. **处置结论=一次性使用决策（UD）**：做出后冻结；要改只能"撤销/重置"，且需**权限 + 原因 + 审计**（SAP QM）；
2. **批被后继版本取代 → 该批一切未完成动作随之失效**（未完成处置不得再生效）；
3. **数量守恒**：任何影响"有效批集合/有效合格"的变化，都必须**同步重算下游口径**（工单完工、入库净额）；
4. 违规动作要**挡在入口**（fail-closed），而不是事后对账发现。

---

## 3. 本期范围（已实现）

| # | 能力 | 落点 | 行为 |
|---|---|---|---|
| ① | **失效批禁止处置** | `QualityNcrServiceImpl.disposeInternal()` | 处置前校验：NCR 状态为 `VOID` → 拒绝；`isLatestVersion(ncr.lotId)` 为假（已有复检新版本）→ 拒绝，提示"请对最新版本处理，或先撤销原处置留痕后重开" |
| ② | **随批作废（VOID）** | 新增 `QualityNcrService.voidOpenDispositionsBySupersededLot(lotId, reason)`，由 `QualityFinishServiceImpl.reinspectLot()` 在换代时调用 | 把被取代批上 `PENDING/DISPOSING` 的 NCR 置 `VOID`，其 `PENDING/PROCESSING` 的处置单置 `VOID`，并在 remark 追加「【随批失效】复检换代：QL…：该批已有复检新版本，不良单随之作废(VOID)，禁止再处置」 |
| ③ | **换代后重算工单有效合格** | `QualityFinishServiceImpl.reinspectLot()` 末尾 | 换代改变"有效批集合"→ 调 `syncFinishInbound(orderId, "复检换代重算：…")` 重算 `completed/finished/remaining`（幂等：= 有效批合格累计） |

**降级策略**：②③ 均包在 try/catch 内（打 WARN 不阻断复检）——复检是主业务，收口/重算失败不应让检验员做不了事；但会留日志便于排查。

---

## 4. 非本期（下一步）

| # | 能力 | 为什么单独做 |
|---|---|---|
| ④ | **撤销已 DONE 的处置（撤销流）** | 需要**新权限点**（如 `quality:ncr:revoke`，走迁移 + 角色授权），且按类型处理反向影响：`SCRAP` 无库存影响（可安全撤销）；`CONCESSION` 已转良品库存 → 需**反向库存调整**；`REWORK` 已建返工执行/复检批 → 需连带处理。建议按 SCRAP → CONCESSION → REWORK 顺序分批 |
| ⑤ | 数量守恒巡检门禁 | 属 `-023`（三期） |
| ⑥ | 处置结论的完整冻结模型（含 UD 重开语义） | 与 ④ 一起，避免半套 |

---

## 5. 提示文案

- 失效批处置被拒：**「该批已失效（已有复检新版本），不能继续处置；请对最新版本处理，或先撤销原处置留痕后重开」**
- 已作废不良单被处置：**「该不良单已随批作废（VOID），不能处置」**

---

## 6. 验收用例（重启后手工）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 某批判定 98/2 不良 → 对 **v2 早期版本**直接调处置接口 | **被拒**（提示对最新版本处理） |
| 2 | 对最新判定批复检换代 | 旧批的 PENDING/DISPOSING 不良单变 **VOID**，remark 带「随批失效」；其未完成处置单也 VOID |
| 3 | 换代后查工单完工数 | `completed_quantity` = **有效批合格累计**（不再停留在旧值） |
| 4 | 正常批处置 | 不受影响（`isLatestVersion` 为真） |
| 5 | 换代时 NCR/重算失败（模拟） | 复检仍成功，日志有 WARN，不出现"检验员被卡住" |

---

## 7. 风险

- ① 可能挡掉"对旧批补处置"的习惯用法 —— 这是**有意的**（旧批应作废，处置对最新版做）；提示里给了出路；
- ③ 的重算会**改写工单完工数**（如 200 → 100）→ 属预期修正；若工单已下游使用（已入库/已发货）需人工确认，日志留痕；
- 新增的 `VOID` 状态：前端展示需能显示（若前端 NCR 列表状态映射缺 `VOID`，会显示原文/异常）→ **待办：前端状态映射补 `VOID`**（下一步一并做）。

---

## 8. 本期实施记录（2026-09-23 17:3x）

| 文件 | 改动 |
|---|---|
| `quality/service/QualityNcrService.java` | 新增 `voidOpenDispositionsBySupersededLot(lotId, reason)` |
| `quality/service/impl/QualityNcrServiceImpl.java` | 实现随批 VOID（NCR + 未完成处置单，remark 留痕）+ `disposeInternal` 增加**失效批/已作废禁处置**守卫 + 备注追加工具 |
| `quality/service/impl/QualityFinishServiceImpl.java` | `reinspectLot()`：换代后 ① 随批作废不良单 ② 重算工单有效合格（均 try/catch 降级 + WARN） |

验证：`mvn -o -q compile` ✅（并删目标 class 强制重编核验新代码在 class 内）；`npm run validate` 全绿（无前端改动）。
**未打包、未重启** → 按第 6 节用例实机验证。


---

## 9. 撤销流实施记录（2026-09-23 17:4x，同日续做）

**已实现（第 4 节里原标"非本期"的 ④，本期只开 SCRAP 这一档）**
| # | 位置 | 改动 |
|---|---|---|
| 1 | 迁移 **206**（幂等，已执行） | 新权限点 **`quality:ncr:revoke`**（菜单 396「不良处置撤销」，挂 378 产品不良台账，icon 显式 NULL）+ 授权角色 1/28/34 |
| 2 | `quality/service/QualityNcrService(+Impl)` | 新增 `revokeAction(actionId, reason, operatorName)`：**受控动作** —— 原因必填；仅允许撤销 `DONE`；本期仅 `SCRAP`（无库存影响）；副作用 = 处置单→`VOID` + 台账已处置量回落/状态回退 + 检验批已处置量回落（**判定上界随之上抬**）；全链 remark 留痕（原因/操作人/时间） |
| 3 | `quality/controller/QualityNcrController` | 新增 `POST /quality/ncr/action/{actionId}/revoke?reason=`（`quality:ncr:revoke`） |
| 4 | `jjx-web/api/quality/lot.ts` | 新增 `revokeAction(actionId, reason)` |
| 5 | `jjx-web/views/quality/ncr/index.vue` | 状态映射补 **VOID**（NCR：已作废（随批/撤销）；处置单：已作废）；处置明细表对「SCRAP + DONE」显示**撤销**按钮（`quality:ncr:revoke`）+ 撤销弹窗（必填原因 + 二次确认）+ 撤销后刷新 |

**残留（下一步）**：CONCESSION（已转良品库存）/ REWORK（已建返工执行与复检批）的撤销需**反向库存调整 / 返工冲销**，按 SCRAP → CONCESSION → REWORK 顺序分批做；撤销后若批已 CLOSED，需先走「重开」再复检判定（弹窗提示已写）。

**验证**：`mvn -o -q compile` ✅（删 class 强制重编核验 `revokeAction` 在 class 内）；`vue-tsc --noEmit` **0 错**；`npm run validate` **全绿**（status-enums 130 基线无新增 / docs / collation / stock / lot）。**未打包未重启**。
