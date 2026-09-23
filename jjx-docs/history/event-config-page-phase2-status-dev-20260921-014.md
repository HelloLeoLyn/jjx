# 事件配置页阶段2（变量注册表/保存校验/最近渲染/试渲染）现状核查（dev-20260921-014，看板 2033）

> 性质：**分析/核查稿**（2026-09-23 19:0x 大黄）。结论先行，证据在后。
> 依赖：012（dev-20260921-012 已完成=10）、013（dev-20260921-013 已完成=10）→ **前置已满足**。

---

## 0. 结论摘要

1. **①~④ 四项主体都已实现**（不是"待开始"）——落在提交 `8d0556f7`（`feat(quality): 收口质检模型并完成任务批次（任务码 dev-20260918-027）`，2026-09-18 那次大批量收尾把 012/013 阶段2 的在制品一并带入库）。
2. 但其中 **③「最近一次实际渲染」实际不工作**：`sys_notification.event_code` **104/104 全为 NULL**（事件码被写进了 `biz_type` 列），而配置页/端点按 `event_code` 查询 → **永远显示"暂无实际发送记录"**。
3. 其余三处属**口径/覆盖度缺口**（能用但达不到规格）：① 注册表只覆盖 **8/160** 个事件；② 后端保存**零校验**（只有前端提示）；④ 试渲染用**样例值**而不是"最近一次真实 payload"。

---

## 1. 逐项对照（规格 vs 现状）

| # | 规格要求 | 现状 | 代码位置 | 判定 |
|---|---|---|---|---|
| ① | 事件变量注册表：每个 eventCode 声明可用键（注解 params/手写 payload 导出 JSON，或建 `sys_event_var` 表） | 已实现，但为**手写 Java 常量表**：COMMON 5 个（bizNo/bizId/bizType/triggerUserName/triggerRealName）+ `order.delivering`、`purchase.arrived`、6 个 `quality.iqc.*` 专用键 | `event/EventVariableRegistry.java`（59 行）；端点 `GET /system/event-config/{eventCode}/metadata` → `variables` | ⚠️ **覆盖 8/160**；与 payload 无自动同步（漂移风险） |
| ② | 模板编辑处提供"可用变量"一键插入；保存时校验未知键并提示（不阻断或可强制保存） | 已实现：变量面板 + 「插入标题/插入内容」+ 未登记变量实时黄条告警 + 提交时 `ElMessageBox.confirm("模板包含未登记变量：…仍要保存吗？")` | `views/system/eventConfig/index.vue` L230-245（面板）、L466-469（`unknownVariables`）、L513（保存确认） | ✅ 前端符合规格（不阻断）；⚠️ **后端 add/edit 无任何校验**（`EventConfigController` L100/L112 直接 insert/updateById） |
| ③ | 「最近一次实际渲染」列：从 `sys_notification` 按 `event_code` 取最新标题/时间/收件人 | 端点+UI 都有：`metadata.latest` = `sys_notification` 按 `event_code` 倒序取 1 条；页面「最近发送」区块展示标题/内容/收件人/时间 | `EventConfigController.metadata()` L81-93；`index.vue` L255-262 | ❌ **恒空**：`sys_notification.event_code` 全 NULL（见 §2） |
| ④ | 「试渲染」：用最近一次事件 payload（可存 `sys_event_last_payload` 或从通知/任务反推）预览 | 已实现，但为**前端本地样例渲染**：用注册表的 `example` 值替换占位符（支持 `{key\|备选}`） | `index.vue` L470-481（`examplePayload` + `renderPreview`） | ⚠️ 可用于验键名/备选逻辑；**不能验真实取值** |

---

## 2. 关键证据（2026-09-23 19:0x）

### 2.1 ③ 为什么不工作（唯一"功能不工作"项）

| 环节 | 事实 |
|---|---|
| DTO | `NotificationCreateDTO`（notification/domain/dto）**没有 `eventCode` 字段**（只有 title/content/notificationType/bizType/bizId/sender*/receiver*/priority） |
| 发布侧 | `event/impl/LocalEventPublisher.java:237` → `dto.setBizType(eventCode);`（把事件码写进了 `biz_type`），**从未 setEventCode** |
| 落库 | `NotificationServiceImpl.createNotification()` 用 `BeanUtils.copyProperties(dto, notif)` → `notif.event_code` 保持 NULL |
| 数据 | `sys_notification` 104 行：`event_code` **NULL 104/104**；`biz_type` 形如 `inventory.inbound.confirmed`（= 事件码）；`receiver_id` 104/104 有值但 `receiver_name` **0/104** |
| 后果 | 配置页按 `event_code = ?` 查 → 匹配不到 → 页面恒显示「暂无实际发送记录」；「收件人」列即便修好也仍为 `-` |

> 注：通知本身是发的、标题/内容也是**渲染后真实文案**（如 `入库单【WO-PL260923001-01-FI06】已入库过账，库存已可用`）——**只是查不到**，不是没发。

### 2.2 覆盖度与量级

| 指标 | 数值 |
|---|---|
| `sys_event_config` 事件总数 / 启用 | **160 / 155** |
| 注册表登记专用键的事件 | **8**（order.delivering、purchase.arrived、quality.iqc.item.approved/rejected、quality.iqc.reinspection.created、quality.iqc.approved、quality.iqc.quarantine.created、quality.iqc.submitted）→ 其余 ~152 个事件在配置页都会提示"该事件暂未登记专用变量" |
| 通知条数 / 最近一条 | 104 / 2026-09-23 17:22:40 |

---

## 3. 建议方案（分档，含工作量）

| 档 | 内容 | 工作量 | 说明 |
|---|---|---|---|
| **A（建议做）** | 修 ③ 的数据链：`NotificationCreateDTO` 加 `eventCode`；`LocalEventPublisher.createNotification()` 里 `dto.setEventCode(eventCode)`（`biz_type` 现值保留，不动语义）；顺带 `setReceiverName(...)`（从 payload 或 sys_user 补）；可选回填历史 104 条（`UPDATE sys_notification SET event_code = biz_type WHERE event_code IS NULL AND biz_type LIKE '%.%'`，先 guard 备份 + 迁移） | 0.3h + 迁移 | 不做的话 ③ 只是个摆设 |
| **B（建议做）** | ④ 改"真实 payload 试渲染"：新增 `sys_event_last_payload`（event_code → payload JSON，`fire()` 时 upsert），`metadata` 返回 `lastPayload`，前端优先用真实 payload、缺失时回落样例值 | 1h | 让"试渲染"能验真实取值（如真实客户名/数量） |
| **C（可选）** | ② 后端保存校验：add/edit 时用 `EventVariableRegistry` 校验模板占位符，返回 warning 列表（**不阻断**），与前端提示呼应 | 0.5h | 防 API 直调绕过前端提示 |
| **D（可选）** | ① 注册表覆盖度：由"手写常量表"改为**自动生成**（fire 时把 payload 的键登记进 `sys_event_var`，或从各事件的 payload 构造处导出） | 2h+ | 根治漂移与 95% 误报；也可先只补高频事件 |

**验收方式提醒**：③ 修完能否显示，还取决于该事件**是否真的发过通知**——`target_role` 指向空置角色（如角色 16）的事件通知会静默丢失（既有系统性问题，归「事件系统治理」立项），届时页面仍会显示"暂无实际发送记录"，属于正常。

---

## 4. 待拍板

1. 是否做 **A**（我建议做，否则 ③ 等于没做）；
2. **B/C/D** 哪几项纳入本期；
3. 若做 A 的历史回填：是否需要（回填后老通知也能在配置页看到最近一次）。

> 分析结论：任务 2033 应从「待开始」改为「**部分实现 + 3 项增强待定**」，不建议当作全新任务开工。

---

## 5. 实施记录（2026-09-23 19:3x，用户拍板「A+B+C 先做，D 登记任务」）

| 档 | 落地 |
|---|---|
| **A** | ③ 数据链修好：`NotificationCreateDTO` 增 `eventCode`；`LocalEventPublisher.createNotification()` 补 `dto.setEventCode(eventCode)`（`biz_type` 现值保留，语义不动）+ 补收件人姓名（新增 `resolveUserName()`：`sys_user` 昵称优先、回退账号名）；**迁移 210** 回填历史 `sys_notification.event_code`（**121/121**，仅当 `biz_type` 命中已配置事件码，保守回填） |
| **B** | ④ 试渲染改真实数据：新建表 **`sys_event_last_payload`**（`event_code` PK / `payload` JSON / `biz_id` / `update_time`）+ 实体 `SysEventLastPayload` + Mapper（`ON DUPLICATE KEY UPDATE` upsert）；`LocalEventPublisher.fire()` 开头 `saveLastPayload()`（try/catch，不阻塞主流程）；`metadata` 端点返回 `lastPayload` + `lastPayloadTime` + `payloadSource`(`lastEvent`/`sample`)；前端优先用真实 payload、缺失回落样例值，并在「试渲染」区显示**数据来源标签** |
| **C** | ② 后端校验：`EventTemplateRenderer` 新增 `placeholderExpressions()`；`EventConfigController` 的 add/edit 调 `validateTemplateVariables()`（按 `EventVariableRegistry` 校验标题/内容的占位符候选键）→ 未登记键进 `data.warnings` + `log.warn`，**不阻断保存**；前端保存成功后若有 warnings 则 `ElMessage.warning` 提示 |

**验证**：`mvn -o compile`（JDK21，删 class 强制重编 + `strings` 核验 `saveLastPayload`/`setEventCode`/`resolveUserName`/`validateTemplateVariables`/`lastPayload`/`payloadSource`/`placeholderExpressions` 均在 class 内）；`vue-tsc --noEmit` 0 错；`npm run validate` 全绿。**未打包未重启**（需重启生效）。

**备份**：表级 guard `~/jjx-backups/sys_notification_eventcode_backfill_20260923-1929.sql`（md5 `b0b803beeeab36a2f9d22d8bd9f5f82f`）+ db-migrate 全库 before-210；`sys_task` 登记 guard `sys_task_register_20260923-1932_before-014-d.sql`（md5 `8f334f9dad80f2bb3431e6977be31812`）。

**D**：已按指示登记为 **dev-20260923-037**（task_id 2261，待开始 P3）——注册表自动化（`sys_event_var` 采集 + 人工描述覆盖）。
