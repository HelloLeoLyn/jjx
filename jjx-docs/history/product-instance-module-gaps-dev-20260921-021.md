# 产品实例模块残留缺陷核查（dev-20260921-021，看板任务 2060）

> 性质：**分析/核查稿**（2026-09-23 18:5x 大黄）。结论先行，证据在后。
> 关联：dev-20260921-013（产品域事件信封）、dev-20260921-023（编译修复）。

---

## 0. 结论摘要

1. 任务标题所述的**「三方法成功路径也 return false」已经修复**（不是本次做的）——`startProduction / completeProduction / deliverInstance` 现在都是 `boolean updated = ...updateById(instance) > 0; if (updated) publish...; return updated;`，事件发布已改为服务内手写 payload，不再受 `EventAspect` 的「返回 false 不发事件」守卫影响。
2. 但同一模块存在**更严重的残留缺陷**（本次核查新发现）：
   - **P1：新建产品实例 100% 失败** —— `checkInstanceCodeUnique()` 恒返回 `false`（实现自项目初始提交起就被注释掉），`createInstance()` 里 `if (!checkInstanceCodeUnique(...)) throw DB_DUPLICATE_KEY` → 每次必抛。**实测：`POST /product/instance` 返回 `{"code":6004,"msg":"数据已存在"}`**。
   - **P1：前端 3 个调用的端点在后端不存在** → 批量建实例、状态变更、生命周期页全部 404。
   - P2：3 个查询方法恒 `return null`；1 个更新方法落库语句被注释（幽灵事件）。
3. 该模块页面**在菜单里是可见的**（menu_id=11 `/product/instance`），所以这不是"死模块"，是**用户能点到但用不了**的功能。

---

## 1. 已修复部分（先澄清，避免重复劳动）

`jjx-server/src/main/java/com/jjx/product/service/impl/ProductInstanceServiceImpl.java`：

| 方法 | 现状（2026-09-23 复核） |
|---|---|
| `startProduction` (L164) | `boolean updated = productInstanceMapper.updateById(instance) > 0; if (updated) publishInstanceEvent("product.instance.production_started", ...); return updated;` ✅ |
| `completeProduction` (L186) | 同上形态 + `product.instance.production_completed` ✅ |
| `deliverInstance` (L208) | 同上形态 + `product.instance.delivered` ✅ |

事件不再依赖控制器返回值：服务内直接 `publishInstanceEvent()`（`EventPublishSupport.fireAfterCommit`），因此 `EventAspect`（`system/aspect/EventAspect.java:43`：`if (result instanceof Boolean && !(Boolean) result) return result;`）不再拦这三条事件。

> 结论：**2060 的原始待办已消失**，该任务应重新定义为「产品实例模块收口」。

---

## 2. 实测证据（2026-09-23 18:5x）

| 项 | 结果 |
|---|---|
| 后端进程 | 18:30:24 启动（`java -jar .../jjx-server-1.0.0.jar`） |
| `GET /product/instance/list?pageNum=1&pageSize=5` | `200 {"total":0,"records":[]}` ✅（分页/查询链路本身通） |
| `POST /product/instance`（最小体：instanceCode/productId/orderId/customerId/quantity） | **`{"code":6004,"msg":"数据已存在","success":false}`** ← 硬证据 |
| `product_instance` 表 | **0 行**（落库失败，与上面互证） |

---

## 3. 缺陷清单（按影响排序）

| # | 位置 | 现状 | 影响 | 级别 |
|---|---|---|---|---|
| 1 | `ProductInstanceServiceImpl.checkInstanceCodeUnique()`（private static） | 实现被注释（`selectByInstanceCode` 等 Mapper 自定义方法已不存在，Mapper 现在是裸 `BaseMapper`），恒 `return false` | `createInstance()` 里 `!checkInstanceCodeUnique → throw` → **新建、批量新建全部失败（6004）** | **P1** |
| 2 | 前端 `api/product/index.ts` vs 后端 Controller | 前端调 `POST /product/instance/batch/{orderId}`、`PUT /product/instance/status/{instanceId}`、`GET /product/instance/lifecycle/{instanceId}`；后端只有 `POST /product/instance/batch`（列表体）、`startProduction/completeProduction/deliver/{id}`，**无 status/lifecycle 端点** | 批量建实例、改状态、生命周期视图 **全部 404** | **P1** |
| 3 | `getInstancesByOrderId / getInstancesByProductId / getInstancesByCustomerId` | 三方法体被注释，恒 `return null`；却由 `/byOrder/{id}`、`/byProduct/{id}`、`/byCustomer/{id}` 暴露 | 三个端点恒返回 `data:null`（前端取 `.length`/遍历即崩） | P2 |
| 4 | `updateInstanceStatus(instanceId, status)` | `// return productInstanceMapper.update(instance) > 0;` 被注释 → **状态不落库**，却 `publishInstanceEvent("product.instance.status_updated")` + `return true` | 假成功 + 幽灵事件；当前无端点暴露（前端期待 `/status/{id}`，见 #2） → 目前是死方法 | P2 |
| 5 | 状态列双轨 | 表有 `instance_status`（实体/上述方法在用）与 `lifecycle_status`（表新列，另有 `order_item_id/config_snapshot/bom_snapshot/design_task_id/work_order_id/bom_id/route_id/film_ids/order_date/delivery_date/actual_delivery_date` 等实体未映射的列） | 同一条数据两个状态口径，报表/门禁/前端各读各的 | P2（需拍板） |
| 6 | 6 条 `product.instance.*` 事件配置 | 均 `is_enabled=1` 但 **`target_role=[16]`** | 角色 **16（ENGINEERING 全权限）长期空置** → 即使事件发出，通知/任务也静默丢失（既有系统性问题，见「事件系统治理」立项） | 归口另案 |

---

## 4. 修复方案建议（约 2 小时，可与 022/023 解耦）

| 步骤 | 动作 | 工作量 |
|---|---|---|
| 1 | `checkInstanceCodeUnique` 用 `BaseMapper` + `LambdaQueryWrapper`（`eq(instanceCode)`，编辑场景排除自身）重写；顺带给 `ProductInstance` 补基础校验注解（当前**一个 @NotNull/@NotBlank 都没有**，`@Validated` 形同虚设） | 0.5h |
| 2 | **端点对齐二选一**：<br>(a) 前端改调后端现有端点（推荐：`/batch` 传列表体；用 `startProduction/completeProduction/deliver` 代替 `/status/{id}`）；<br>(b) 后端补 `PUT /status/{id}`、`GET /lifecycle/{id}` | 0.5h |
| 3 | 三个查询方法用 `LambdaQueryWrapper` 实现（按 orderId/productId/customerId） | 0.3h |
| 4 | `updateInstanceStatus` 恢复 `updateById`；同时决定是否暴露端点（与步骤 2 一并定） | 0.2h |
| 5 | 口径拍板：`instance_status` vs `lifecycle_status` 保留哪个（建议：以 `lifecycle_status` 为准，`instance_status` 只读兼容后废弃） | 决策 |
| 6 | 验证：编译 + **停服重新 package 起服**后实机走「新建 → 开始生产 → 完成 → 交付」全链，并核对事件是否落到通知/任务侧 | 0.5h |

## 5. 风险 / 注意

- 该模块 0 行数据、0 使用痕迹 → 修之前建议先确认**业务是否还要这个功能**（"产品实例"作为订单行实例 + 快照模型，与现在的检验批/物料批次模型是否有重叠，需要口径确认；若不要，则应走「菜单下架 + 代码归档」而不是修）。
- 步骤 2 选 (a) 会动前端 `views/product/instance/index.vue` + `api/product/index.ts`；选 (b) 只动后端。**建议 (a)**，因为后端现有端点语义更清晰（start/complete/deliver 三态而不是一个 status 数字）。
- 与 `EventAspect` 守卫无关（事件已在服务内发出），但**收件人角色 16 空置**会让"事件发了没人收到"，验证时要看 `sys_event_log`/通知表而不是只看代码。
