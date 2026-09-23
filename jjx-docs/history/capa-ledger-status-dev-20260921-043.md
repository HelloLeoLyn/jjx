# CAPA 最小台账 现状核查（dev-20260921-043，看板 2105）

> 性质：**分析/核查稿**（2026-09-23 20:1x 大黄）｜只分析、未改代码
> 任务原文（09-21 登记）：「质量域 15 张表无 CAPA/8D 表……建议新表 `quality_capa` + 页面 + 四态 + 结案门禁」
> 用户 09-23 已把本任务「立项」转待开始；本次核查发现：**功能已实现，但门禁只覆盖了一条路径**。

---

## 0. 结论摘要

1. **CAPA 最小台账已实现**（不是"待开发"）：表 `quality_capa`、号段规则 `biz_no_rule.quality_capa`（`CAPA+yyMMdd+3`）、菜单 **391「CAPA台账」`/quality/capa`**（授权角色 1/28/34）、后端 `QualityCapaController/Service/Mapper`、前端 `views/quality/capa` + `enums/quality/CapaEnum.ts` **全部在位**，四态流转与必填校验都做了。
2. **结案门禁有洞（P2）**：口径要求「有未关闭 CAPA 的不良单不得结案」，但**只有「处置完成」一条路径查了 CAPA**（`QualityNcrServiceImpl:340-341`）；另外两条把 NCR 置 `CLOSED` 的路径**不查**——
   - `syncFromLot()`（复检更正同步台账，`:128`）：`disposed ≥ defect` → 直接 `CLOSED`；
   - 返工完成路径（`:662`）：同判据 → 直接 `CLOSED`。
   → 结果：**有未关闭 CAPA 的不良单仍可能被结案**（走这两条路径时）。
3. 另有 3 条体验/文档缺口（P3），见 §3。
4. 数据 `quality_capa` **0 行** → 该功能从未被实际使用过、无回归样本。

---

## 1. 已实现部分（逐条对照任务口径）

| 口径要求 | 现状 | 位置 |
|---|---|---|
| 新表 `quality_capa` | ✅ `capa_id/capa_no/ncr_id/root_cause_category/root_cause/action_plan/owner_id/owner_name/due_date/verification_result/status/closed_time` + 审计 + `del_flag` | `quality/domain/entity/QualityCapa.java` |
| 号（可读、走号段） | ✅ `biz_no_rule.quality_capa` = `CAPA+yyMMdd+3`，创建时经 `RedisSequenceService.generateBusinessNumberByType` | `QualityCapaService.create()` |
| 页面挂「质量管理」下 | ✅ 菜单 391 C 型 `/quality/capa` → `views/quality/capa/index.vue`，`quality:capa:view`，授权角色 **1/28/34** | `sys_menu` / `sys_role_menu` |
| 状态四态 | ✅ `PENDING_ANALYSIS 待分析 → ACTION_IN_PROGRESS 措施执行中 → PENDING_VERIFICATION 待验证 → CLOSED 已关闭` | `CapaEnum.ts` + `QualityCapaService.advance()` |
| 必填校验 | ✅ 待分析→执行中 必须填「根因 + 措施计划」；待验证→关闭 必须填「验证结论」 | `advance()` |
| 来源 = 不良单 | ✅ `ncr_id` 必填且校验不良单存在；**已结案的不良单挂 CAPA 会自动回退 `DISPOSING`**（避免"结案后补 CAPA"矛盾） | `create()` |
| 结案门禁（口径要求） | ⚠️ **部分**：处置完成路径 ✅；复检同步 / 返工完成两条路径 ❌（见 §0.2） | `:340-341` vs `:128` / `:662` |
| 反查未关闭 CAPA | ✅ `countOpen(ncrId, excludeId)` | `QualityCapaService` |

**接口**：`GET /quality/capa/page`（`quality:capa:view`）、`POST /quality/capa`（`quality:ncr:dispose`）、`PUT /quality/capa/{id}/advance`（`quality:ncr:dispose`）。

---

## 2. 结案门禁缺口（P2，建议修）

判据本应是：**`disposed ≥ defect` 且 `countOpen(ncr) == 0` → CLOSED；否则 DISPOSING**。

| 路径 | 代码 | 是否查 CAPA |
|---|---|---|
| 处置完成（`completeAction`） | `ncr.setStatus(... canClose ? "CLOSED" : "DISPOSING")`，`canClose = disposed≥defect && capaService.countOpen(ncrId,null)==0` | ✅ 有 |
| 复检更正同步台账（`syncFromLot`） | `open.setStatus(disposed.compareTo(defect) >= 0 ? "CLOSED" : …)` | ❌ **无** |
| 返工完成（`completeReworkAction` 路径） | `ncr.setStatus(disposed≥defect ? "CLOSED" : "DISPOSING")` | ❌ **无** |
| CAPA 关闭时 | `ncr.pendingQuantity()==0 && countOpen(ncrId,id)==0` → CLOSED | ✅ 有 |

**建议（最小改动）**：把判据抽成一个方法 `canCloseNcr(QualityNcr ncr, Long excludeCapaId)`（= 处置量够 + 无未关闭 CAPA），**三处共用**，避免以后再漏一条路径。工作量约 0.3h + 回归（造一条"有未关闭 CAPA 的 NCR"，分别走三条路径验证只有第三条能关）。

---

## 3. 体验/文档缺口（P3，可选）

1. **新建 CAPA 要手输「不良单ID」**（`el-input-number`），没有从**不良台账页**「建 CAPA」带过来的入口 → 实际操作很别扭；且 NCR 页**看不到**该不良单的 CAPA 进度（双向不可见）。
2. **页面极简**：只有「推进」；无详情/编辑/删除，无逾期高亮（`due_date` 已存但没用起来），无"我的 CAPA"筛选。
3. **按钮无权限指令**：页面上「新建/推进」没有 `v-hasPermi`，而后端要 `quality:ncr:dispose` → 只有 `quality:capa:view` 的角色会出现"看得见按钮、点了 403"的**权限死路**（本仓已有先例：任务 2061 治理过同类问题）。建议前端补 `v-hasPermi('quality:ncr:dispose')`，或在菜单里补两个 F 型按钮权限点。
4. **文档漂移**：`jjx-docs/modules/quality.md` 的菜单表**未列** CAPA（菜单 391）与 `quality:capa:view`；`check-doc-no.sh` 的 `required` 列表**已含** `quality_capa` ✅。
5. 口径里"来源可以是 lot_id"未做（只支持 ncr_id）——最小台账可不做，记录备查。

---

## 4. 一句话结论

> CAPA **不用重新开发**（表/号段/菜单/四态/页面都在）；要做的是**把结案门禁补齐**（两条路径不查未关闭 CAPA → 有洞），外加 3 条体验/文档小项。建议：**门禁缺口单独立小任务（P2，0.3h）**，体验项按需。
