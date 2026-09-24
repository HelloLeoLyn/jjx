# 事件变量注册表自动化 · 现状核查与推荐实现（dev-20260923-037，看板 2261）

> 性质：**分析/核查稿**（2026-09-24 09:5x 大黄）｜只分析、未改码
> 来源：dev-20260921-014（事件配置页阶段2 / 看板 2033）的 D 项；A/B/C 已于 2026-09-23 落地

---

## 0. 结论摘要

1. **真实误报面是 24/160（15%），不是"95%"**——区分两层：
   - **噪音层（152 处）**：除已登记的 8 个 eventCode 外，其余事件在配置页显示「该事件暂未登记专用变量，可使用通用变量」的**空态提示**（无害，但看着像"没做"）。
   - **真误报层（24 处）**：这些事件的**模板真的用了未登记的键**（如 `sample.ready` 用 `{productName}{productCode}`、`production.work-report.submitted` 用 `{reportId}{qualifiedQuantity}`）→ 保存时会弹「模板包含未登记变量：…，保存后可能渲染为空」的二次确认，**而且措辞是错的**（这些键实际能渲染出值，只是注册表没登记）。
2. **今天多了一个"白捡"的杠杆**：`sys_event_last_payload`（2026-09-23 的 A/B/C 落地，今晨 09:32 重启后已生效）**已经在每次事件触发时落 payload JSON** → 自动采集的数据源**已经就位，不需要再建新表、不需要新写入逻辑**。
3. **推荐最小实现（≈1h，无新表）**：`metadata` 端点把 `variables` 从"只读手写常量表"改为 **`COMMON ∪ 人工登记 ∪ 最近一次 payload 的键`**（采集键带 `source=collected` + `last_seen_at`），并单独提示「模板里用到、但从未采集到」的键；**未知键校验仍只认"人工 + 采集"**（否则自证循环、校验形同虚设）。
4. **如实告知取舍**：`sys_event_last_payload` 只存"最近一次" → 键集合会随最近一次 payload **漂移**（某次 payload 少个键就看不见）。要"收敛漂移 + 真 `last_seen_at` 语义"需要**累积表** `sys_event_var`（方案①原设计，另 +1h）→ 建议作**二期可选**。

---

## 1. 现状量化（2026-09-24 实测）

| 指标 | 数值 | 来源 |
|---|---|---|
| 注册表已登记 | **COMMON 5 键**（bizNo/bizId/bizType/triggerUserName/triggerRealName）+ **2 个内联 eventCode**（order.delivering、purchase.arrived）+ **6 个 quality.iqc.\*** 共享 9 键 → 覆盖 **8/160** 个事件 | `EventVariableRegistry.java` |
| 事件配置 | **160** 条（启用 **155**） | `sys_event_config` |
| 模板里出现过的键（去重） | **32** 个 | 160 条 title/content 的正则提取 |
| **模板含未登记键的事件** | **24** 个（15%）→ 真误报面 | 同上，与注册表求差 |
| payload 构造点 | `EventPublishSupport.payload(...)` **41** 处；`fire(...)` **60** 处 | grep |
| 基础 payload 保证带 | `bizType` + `bizId` + `bizNo`（bizNo 空时回落 bizId 字面值） | `EventPublishSupport.payload()` |
| 手写 payload 高频键（抽样） | customerName 15 / message 12 / code 10 / sourceNo 5 / orderId 5 / count 4 / warehouseId 3 … | grep |
| **`sys_event_last_payload`** | 表已建、**0 行**（今晨重启后还没事件触发）；`metadata` 已返回 `payloadSource` 字段 → 新代码已在运行 | DB + 实调 `/system/event-config/order.delivering/metadata` |

**24 处真误报样例**：`sample.ready → productCode, productName`；`purchase.received → supplierName`；`inventory.inbound.confirmed → operatorName, sourceDesc`；`stock.shortage → noBomCount, shortageCount`；`production.work-report.submitted → defectiveQuantity, qualifiedQuantity, reportId, taskId` …

---

## 2. 方案对比（结合今天的新事实）

| 方案 | 做法 | 工作量 | 评价 |
|---|---|---|---|
| **①′（本次推荐，最省）** | `metadata` 端点解析 `sys_event_last_payload.payload` → 键集合 ∪ COMMON ∪ 人工表；采集键 `source=collected`、`last_seen_at` 取该行 `update_time` | **≈1h** | **不需要新表、不需要新的写入/采集逻辑**（payload 已在落库）；覆盖面 = "曾触发过的事件"；缺点是漂移（见 §0.4） |
| ①（原推荐：累积表） | 建 `sys_event_var`，`fire()` 里 upsert 本次 payload 的键（不覆盖人工描述） | ≈2h | 能**收敛漂移** + 真 `last_seen_at`；但写入路径要动 `LocalEventPublisher`（与 ①′ 不冲突，可二期叠加） |
| ② 构建期导出 | 从 41 处 payload 构造点导出静态 JSON | ≈2h | 静态、无运行期依赖；但手写/动态 payload 多，覆盖不全，且要维护导出脚本 |
| ③ 注解扫描 | `@EventVar` 声明 + 启动期扫描 | ≈3h | 侵入式；手写 payload 场景为主，注解覆盖不全，收益最低 |

**推荐路线**：先 ①′（1h，把覆盖度从 8/160 提到"所有触发过的事件"、24 处真误报清零）→ 需要"漂移收敛/描述/示例值"时再上 ①（累积表）。

---

## 3. 实施时必须守住的三个细节

1. **模板占位符不能并入校验白名单**。如果把"配置自身的 `{}` 占位符"也算已登记，`unknownVariables` 会永远为空 → 校验失效（自证循环）。模板键只能用于**展示层**提示（"该键被模板使用，但尚未采集到值"）。
2. **采集键的描述回落**：先给「（自动采集，暂无中文描述）」，只对高频键（bizNo/orderNo/customerName/sourceNo/reportId…）补人工描述，别硬编码 30+ 键。
3. **示例值直接复用真实 payload**：采集键的 `example` 可从最近 payload 取真值 → 顺带把 2033 的 ④（试渲染）从"样例值"升级为"真实值"，两件事用同一份数据。

---

## 4. 验收建议

- 任意**触发过**的事件在配置页「可用变量」都能列全（不再显示空态）；
- 现有 **24 处**「模板含未登记变量」告警 → **归零**（键被采集到）或转为新提示「模板使用但从未采集到」；
- 键名漂移：新键第一时间出现；消失的键（二期累积表）带 `last_seen_at` 陈旧标记；
- 门禁：`npm run validate` 全绿；`metadata` 合并逻辑加**纯函数单测**（键集合合并 / 去重 / 排序稳定）。

---

## 5. 一句话结论

> 2261 现在有了"白捡的一半"：`sys_event_last_payload` 已经在落每事件的 payload，**不用再建表就能把注册表变成"自动采集"**。建议先做 ①′（约 1h）——覆盖度 8/160 → "所有触发过的事件"、24 处真误报清零；累积表 `sys_event_var`（漂移收敛 + last_seen_at）留作二期。
