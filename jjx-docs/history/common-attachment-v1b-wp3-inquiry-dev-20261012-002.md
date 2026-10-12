# 公共附件 V1-B · WP3 询价单试点实施报告

- 任务码：dev-20261012-002
- 日期：2026-10-12
- 设计依据：`jjx-docs/history/common-attachment-v1b-frontend-design-dev-20261012-002.md`（v2）§4/§7；实施报告 `common-attachment-v1b-wp1-wp2-dev-20261012-002.md`（§8/§9）
- 范围：**仅询价单**（主表单 + 详情弹窗）；未触碰销售订单/报价单/样品单/产品模块
- 状态：代码完成 + 类型检查通过；**浏览器点通待人工**（本轮不重启服务、未 commit）

## 1. 实施前核查结论

| # | 核查项 | 结论 |
|---|---|---|
| 1 | 询价单附件入口 | 主表单 `views/sales/inquiry/index.vue:323-329`（旧 `AttachmentPanel`，`v-if="form.inquiryId"`）+ `:331` 客供资料 `CustomerDocUpload`；详情弹窗 `components/InquiryDetailDialog.vue:59`（旧 `AttachmentPanel`）。共 2 处通用附件 + 1 处客供资料入口 |
| 2 | 保存接口返回 / 新 id 获取 | `submitForm()`（`index.vue:969`）：新建 `inquiryApi.add(form)` → `res.data.inquiryId`（`:993` 已按此取 `newId`，并回写 `form.inquiryId`）；编辑 `inquiryApi.edit({...form, attachmentIds})`，id 来自 `form.inquiryId` |
| 3 | 旧组件的额外职责 | ① `AttachmentPanel` 的 traceId 合并：**询价单两处都没传 `trace-id`**（已按实际代码确认），故替换不丢功能；② `AttachmentPanel` 的"只有 `biz_id/biz_type`"列表 → 与 AttachmentCenter 的 B 段等价，且新组件多出引用元数据/解绑/编辑；③ `uploadedAttachmentIds`（`index.vue:597`）**只由 `CustomerDocUpload` 的 `@success` 喂**（`:333`），用于编辑时 `attachmentIds` 字段；后端 `InquiryServiceImpl:262` 把它拼进 `OperLogDetailBuilder` 的**变更日志明细**（不是业务关联表，`sales_inquiry` 无附件列）→ **该通道保持原样**，不由新组件写入（避免改动既有审计口径） |
| 4 | Props/Events/bindPending 兼容性 | `AttachmentCenter` 的 `source-id` 绑定 `form.inquiryId || null`（响应式）→ 保存回写 id 后 `bindPending()` 立即生效；`bindPending()` 返回 `document-not-saved / all-ok / partial / all-failed`，与"保存成功后才绑定"的流程匹配；`readonly`/`locked` 本轮只用 `readonly`（详情），`locked` 保持 false（无已确认锁定规则，不自行发明） |

## 2. 修改文件清单

| 文件 | 改动 |
|---|---|
| `jjx-web/src/views/sales/inquiry/index.vue` | ① 模板：`AttachmentPanel` → `AttachmentCenter`（`source-type="inquiry"`、`:source-id="form.inquiryId \|\| null"`、`:draft-id="draftId"`、`view-perm="sales:inquiry:view"`、`edit-perm="sales:inquiry:edit"`）；② import 替换；③ 新增 `attachmentCenterRef`、`draftId`、`newDraftId()`、`bindPendingAttachments()`；④ `handleAdd()` 生成唯一 `draftId`（编辑时置空）；⑤ `submitForm()` 在客供资料 `flushPending` 之后调用 `bindPendingAttachments()`，成功后清 `draftId`；⑥ 保存/审核/其他业务逻辑**未改** |
| `jjx-web/src/views/sales/inquiry/components/InquiryDetailDialog.vue` | `AttachmentPanel` → `AttachmentCenter mode="readonly"` + `view-perm="sales:inquiry:view"`（共享组件，打样工作台来源单据查看同步受益） |
| `jjx-web/src/composables/useAttachmentRefs.ts` | 新增 `watch(storageKey)`：键切换（新建 `draft-X` → 保存后 `id-N`）时，内存队列为空则从新键恢复、非空则落盘到新键——保证"保存后绑定失败"的队列不被空键覆盖丢失，且刷新后仍能恢复 |

未改：`CustomerDocUpload`（客供资料专用入口及职责保留）、`uploadedAttachmentIds` 通道、询价单其余保存/审核逻辑、其他业务模块、数据库、旧组件文件（`AttachmentPanel` 仍被 6 处其它页面使用，未删除）。

## 3. 关键实现说明

1. **draftId 生命周期**：`handleAdd()` 生成 `inq-<ts36>-<rand>`；同一张未保存询价单在弹窗内保持稳定（不随渲染/重置变化）；保存成功后清空（此后靠 `sourceId=inquiryId`）；`handleUpdate()` 显式置空（编辑走 sourceId）。`resetForm()` 会把 `form.inquiryId` 置 `undefined`，因此"新增"必定进入草稿态（`sourceId=null` → 需要 draftId → 未提供则禁用上传，符合 WP1+WP2 补缺的强制规则）。
2. **保存前只 TEMP，不提前绑定**：`sourceId=null` 时 `AttachmentCenter` 只上传不绑定（`draftReady` 由 draftId 满足）（设计要求 §三-3）。
3. **保存后绑定**：`add()` 拿到 `newId` → 回写 `form.inquiryId` → 客供资料 flush（原逻辑不变）→ `bindPendingAttachments()` → 逐条 `POST /refs`；成功条目出队、失败条目留在队列（§三-4）。
4. **失败提示与重试**：`partial` → 「询价单已保存，部分附件绑定失败（成功 N 条、失败 M 条），可在附件区重试绑定」；`all-failed` → 错误提示同款；**不阻断保存、不自动重试、不删附件**；"重试绑定/移除"按钮在附件区待绑定区（§三-5）。
5. **编辑已有询价单**：`form.inquiryId` 存在 → 上传即绑定（`autoBind` 默认 true）（§三-6）。
6. **列表展示**：A 段（refs，主键 refId，含文件名/大小/类型/分类/解绑/编辑）+ B 段（历史 `attachment/list`，只读、标「历史附件」、无解绑）；已在 A 段出现的 `attachmentId` 不再出现在 B 段，避免重复条目（§三-7）。
7. **权限/锁定**：只传已确认的 `sales:inquiry:view` / `sales:inquiry:edit`；`locked` 保持 false，不自定义状态锁定（§三-8）。
8. **客供资料入口**：`CustomerDocUpload` + `@success` + `flushPending` 全链路未动（§三-9）。

## 4. 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 前端类型检查 | `npx vue-tsc --noEmit` | **通过（exit 0）** |
| 状态枚举门禁（本轮文件） | `npm run check:status-enums` | 本轮 3 个文件**0 新增违规**；整体 exit=1 仍是那 7 条存量（`usePrintFieldSuggest.ts` 2 条、`OrderBatchActions.vue` 5 条），与本轮无关 |
| 旧组件未被误删 | `grep -rn AttachmentPanel src/` | 询价单目录已无引用；`AttachmentPanel` 仍被 6 处（BizFlowDetail、AttachmentUploader、sample-order、OrderDetailDrawer、OrderForm、quality-archive）使用 |
| 共享详情组件调用方 | `grep -rn InquiryDetailDialog src/` | 2 处（询价单页、工程打样工作台）；都以 `:inquiry-id` 传值，`mode="readonly"` 不需要 draftId |
| 服务重启 | — | **不需要**（纯前端改动；后端 refs 元数据能力已在上一轮重启后生效，vite dev server 热更新） |

**自查事故记录（已修复）**：编辑脚本时一次 patch 因模糊匹配落到了 `handleUpdate`（把标题改成"新增询价单"并误加 draftId）。发现后立即核对上下文并修复（现 `handleUpdate` 为 `draftId=''`、标题 `'修改询价单'`），`vue-tsc` 通过；此处留痕以便复核。

## 5. 未验证项

1. **浏览器点通**（本环境无浏览器自动化）：新建上传 TEMP、保存后绑定、列表元数据显示、解绑刷新、失败重试、历史附件只读——全部待人工按 §6 步骤验收。
2. 绑定失败的**真实失败注入**未做（需构造后端拒绝，如权限被回收或附件过期）；队列保留逻辑为静态验证。
3. 多标签页/并发绑定未测。
4. 待绑定队列的"7 天后 TEMP 过期"提示仍按设计 §5-5 留待后续（点重试会报"附件不存在或已删除"）。

## 6. 人工验收步骤（按任务四）

前置：前端 dev server 已在跑（`:3000`），无需重启；后端保持当前版本。

| # | 操作 | 预期 | 取证 |
|---|---|---|---|
| 1 | 询价单页 → 新增 → 附件区选 **2 个文件** | 出现「待绑定 2」；此时**不得**产生任何引用行 | `SELECT COUNT(*) FROM sys_attachment_ref WHERE source_type='inquiry' AND source_id=<待确认>`（新单据此时无 id，可用 `SELECT id,upload_state FROM sys_attachment ORDER BY id DESC LIMIT 2;` 确认 `upload_state='TEMP'`） |
| 2 | 填写必填项 → 确定（保存） | 提示"新增成功"；待绑定区清空 | `SELECT id,attachment_id,source_type,source_id,deleted FROM sys_attachment_ref ORDER BY id DESC LIMIT 2;` → 2 行新引用指向该 inquiryId；`SELECT id,upload_state,expires_at FROM sys_attachment WHERE id IN (<两个附件id>);` → `BOUND`、`expires_at IS NULL` |
| 3 | 关闭弹窗 → 重新打开该询价单（列表行"修改"或详情） | 附件区 A 段显示 2 条，**文件名/大小/分类**正常，预览/下载可用 | 页面直接观察；下载应返回真实字节 |
| 4 | 点某条附件的"解除引用" → 确认 | 二次确认文案含"物理文件保留"；列表刷新后该条消失 | `SELECT id,deleted,deleted_at FROM sys_attachment_ref WHERE id=<refId>;` → `deleted=1` |
| 5 | 制造一次绑定失败（可选：临时移除 `sales:inquiry:edit` 后保存） | 提示「询价单已保存，部分附件绑定失败…」；待绑定区保留失败条目并可"重试绑定" | 失败条目仍在页面；`localStorage` 键 `attach-pending:v2:<userId>:inquiry:<draftId 或 id-N>` 有记录 |
| 6 | 用历史附件单据（如 `INQ261012001`）打开 | B 段显示「历史附件」只读条目（无解绑按钮），且**不重复**出现在 A 段 | 页面观察；DB：该附件 `upload_state` 仍为 `LEGACY`（未被前端改写） |
7 | 客供资料入口回归 | "上传客供资料"按钮与弹窗行为与改造前一致 | 与改造前对照 |

## 7. 约束遵守与遗留

- 未改数据库结构/未迁移；未改其他业务模块；未处理 `dev-20261012-011`；未清理历史/测试数据；未 commit；未重启服务（也不需要）。
- 遗留：§5 的四项未验证；`check:status-enums` 存量红（与本轮无关）；测试附件（14~35）与 5 个孤儿 `.bin` 仍待清理。

## 8. 队列时序核查（只读，2026-10-12）

### 8.1 实际调用链（带行号）

绑定成功路径（新建）：

```
handleAdd()                        index.vue:805  → draftId = newDraftId() (:808) → resetForm()（inquiryId=undefined）
上传文件                            AttachmentCenter.onFiles → useAttachmentRefs.uploadTemp (:228)
                                   → POST /refs/temp → pending.push(:244) → persist()(:136 → 键 …:draft-X)

submitForm()                       index.vue:~1010
 ├ inquiryApi.add(form) → res.data.inquiryId
 ├ form.inquiryId = newId                      ← 触发父组件重渲染（Vue 微任务队列）
 ├ await customerDocUploadRef.flushPending(newId)   ← 第一个 await：让出微任务
 │    （Vue 的 flushJobs 微任务在赋值时就已入队(FIFO 在前) → 本行恢复前父组件已重渲染，
 │      子组件 props.sourceId 已更新 → composable 的 sourceId computed = newId）
 ├ await bindPendingAttachments()   index.vue:616 → refs.bindPending() (:284)
 │    ├ sourceId.value 已为新 id → 继续（否则返回 document-not-saved）
 │    ├ 逐条 bindPendingOne → POST /refs → 成功 removePending(:175) / 失败 state='failed'+error
 │    ├ persist()（此时 storageKey 已切到 …:id-N）
 │    └ await load() 刷新引用列表
 ├ ElMessage.success('新增成功')                ← 仅 partial/all-failed 时前面多一条 warning/error
 └ draftId.value = ''  → dialogVisible=false → getList()
    （此时 sourceId 已存在，storageKey 仍是 id-N → 清 draftId 不会改变键）
```

键切换时的 watch（`useAttachmentRefs.ts:339`）：

```
watch(storageKey, key => { if (!key) return; if (!pending.value.length) restore(); else persist() })
```

- **关闭弹窗不清队列**：`handleClose()`（`index.vue:1041`）只重置客供资料与 `uploadedAttachmentIds`，**不碰 draftId、不碰 AttachmentCenter 队列**；`el-dialog` 也**没有 `destroy-on-close`**（`index.vue:165-170`）→ 组件实例与内存队列跨"关闭/重开"存活。

### 8.2 逐项风险判断

| # | 核查点 | 结论 | 依据 |
|---|---|---|---|
| 1 | 保存 → 取得 inquiryId → sourceId 更新 → bindPending 的执行顺序 | **正确**（无时序缺陷） | `form.inquiryId = newId` 后紧接着 `await flushPending(...)`；Vue 的渲染 flush 是**先入队的微任务**，微任务 FIFO → 恢复执行时子组件 props 已同步。且 `bindPending()` 内部有 `if (!sourceId.value) return 'document-not-saved'` 兜底（不绑但也**不丢队列**） |
| 2 | `watch(storageKey)` 是否异步丢队列/覆盖 | **不丢不覆盖** | 三条分支都安全：① 非空 → `persist()` 只写不读；② 空 → `restore()` 读持久化副本（正是"刷新后重开单据能找回失败条目"的路径）；③ 键为空 → 直接 return。`restore()` 仅在内存队列为空时执行，不可能覆盖在场条目 |
| 3 | 旧 `draftId` 键是否清理 / 是否残留误恢复 | **残留存在，但不会误恢复**；有 1 处 UI 可达性缺口 | 旧键（`…:draft-X`）在切换后**不会被删除**（`persist()` 只写新键）→ localStorage 会累积不可达的旧键（垃圾，非功能问题；新草稿 id 随机，永不撞键）。**缺口**：若在"保存前"刷新页面，内存队列清空 + 新 `handleAdd()` 生成**另一个** draftId → 上一草稿的待绑定条目在界面上**不可达**（服务端 TEMP 仍在，7 天内可被重新选择同文件找回同一 id）。另：取消后重开新增会沿用当前队列（`persist()` 到新键）→ 条目仍可见可移除，**不是孤儿** |
| 4 | 保存成功但绑定失败 → 关闭/重开/刷新 能否恢复 | **能**（三条路径） | 关闭重开：内存队列存活（无 destroy-on-close）✓；页面刷新后在**同一张单据**重开：`sourceId→id-N` 触发 `watch(storageKey)` → `restore()` 读回 `…:id-N` 的失败条目 ✓；persist 时机覆盖失败态（`bindPending` 末尾 `persist()`）✓。边界：换成**别的**单据打开时不会显示（条目按单据隔离，属预期） |
| 5 | 重复绑定 / 错误出队 / 误报整单保存失败 | **无重复绑定、无错误出队**；误报为**理论风险**（已兜底） | ① 成功条目立即 `removePending`，失败条目保留；`bindPending` 过滤掉 `binding` 态防连点；② 服务端 `POST /refs` 同来源同附件同产品幂等（V1-A 验收实测返回原 refId）+ DB 唯一键 `uk_attachment_ref_active` → 重复也生不出第二条引用；③ `bindPendingAttachments()` 自身不抛（逐条 try/catch、`load()` 用 `allSettled`、`persist()` 吞异常）→ 不会把"绑定失败"变成"整单保存失败"；即便真抛，调用点仍在 `form.inquiryId` 已赋值之后 → 再次提交会走**编辑**分支，**不会**产生重复询价单 |

### 8.3 最小修复建议（本轮未实施，均 1 行级）

| # | 建议 | 位置 | 理由 |
|---|---|---|---|
| R1 | `await nextTick()` 后再 `bindPending()`（或把 `form.inquiryId` 的赋值与绑定调用都放进 `nextTick`） | `index.vue:616` 内 | 让"props 已同步"变成**确定性**而非依赖微任务顺序；避免极端情况下（Vue 调度被暂停等）误出"询价单尚未保存"的文案 |
| R2 | `bindPendingAttachments()` 内部整段 `try/catch`（异常只 toast，不向上冒） | `index.vue:616-633` | 兜住"绑定异常 → 保存流程被 catch 当作提交失败"的理论路径 |
| R3 | 新建草稿 id 只在为空时生成：`if (!draftId.value) draftId.value = newDraftId()` | `index.vue:805` 内 | 修 §8.2-③ 的**保存前刷新**UI 可达性缺口（同一草稿键复用，刷新后 `restore()` 能读回）；保存成功后已清空，不影响下一张单据 |

### 8.4 结论

- **无需修改代码**：未发现会导致"队列丢失、重复绑定、错误出队、误报整单保存失败"的真实缺陷（§8.2 五项均通过或已兜底）。
- 建议按 R1/R2/R3 做 3 处**加固**（可选，收益分别是确定性、异常兜底、刷新可达性）；其中 R3 对应一个真实但影响有限的可用性缺口（保存前刷新后，界面看不到上一草稿的待绑定条目，服务端 TEMP 仍在）。
- 复验前提：本节为**静态时序分析 + 代码走查**，未做浏览器运行时验证（§5-1）；人工点通时请特别观察 §8-1 的 `document-not-saved` 文案是否出现（出现即为 R1 需要落地的信号）。

## 9. 队列加固（本轮实施，2026-10-12）

### 9.1 修改文件（仅 2 个）

| 文件 | 改动 |
|---|---|
| `jjx-web/src/views/sales/inquiry/index.vue` | `bindPendingAttachments()`：① 入口 `await nextTick()`（第 618 行，等 `form.inquiryId` 回写引起的渲染完成后再读 sourceId）；② 整段 `try/catch` 异常隔离（异常只 `console.error` + `ElMessage.error('附件处理失败：…')`，不再向上冒泡） |
| `jjx-web/src/composables/useAttachmentRefs.ts` | ① `persist()` 改为返回布尔（写入是否成功）；② 新增 `dropStoredKey(key)`；③ `watch(storageKey)` 改为 `if (!pending.value.length) restore(); if (persist()) dropStoredKey(previousKey)` |

未改：`CustomerDocUpload`（保存与审计逻辑原样）、后端、数据库、其他业务页面；**未实施 R3**（草稿 id 仍按次生成，保存前刷新的可达性缺口按指令不扩展）。

### 9.2 队列迁移（draft-X → id-N）行为对照

| 场景 | 加固前 | 加固后 |
|---|---|---|
| 草稿有 2 条待绑定/失败条目，保存成功触发键切换 | 条目 `persist()` 到 `…:id-N`；旧键 `…:draft-X` **永久残留**（不可达垃圾） | 条目写入 `…:id-N` **成功**后，`dropStoredKey('…:draft-X')` **删除旧键** |
| 新键写入失败（localStorage 被禁用/配额满） | 静默失败，无从判断 | `persist()` 返回 `false` → **不删除旧键**（唯一副本保留，可人工恢复） |
| 内存队列为空时键切换（刷新后打开单据） | `restore()` 读新键 | 先 `restore()` 读新键，再把结果落回新键（写成功才清旧键） |
| 键为空（既无 sourceId 也无 draftId） | 直接 return | 同前，不动任何持久化数据 |
| `previousKey === key`（无实际迁移） | 不触发 | `dropStoredKey` 判等后 no-op |

### 9.3 异常处理结果

- `bindPendingAttachments()` 内的任何异常（`nextTick`/`bindPending`/`ElMessage`）都被隔离：**只有附件相关提示**，不会再进入 `submitForm` 的 `catch` 分支。
- 因此不会出现"询价单已保存却提示提交失败、弹窗不关"的误导态；成功提示、清 `draftId`、关弹窗、刷新列表照常执行。
- **不重复创建询价单**有两道防线：① 不冒泡 → 流程正常走完，用户无需再点；② 若用户仍手动再点"确定"，`form.inquiryId` 已在保存时回写 → 走**编辑**分支而非新增。
- `document-not-saved` 文案保留为兜底信号（若人工点通时出现，说明 `nextTick` 仍未覆盖到某条路径）。

### 9.4 验证结果与未验证项

| 项 | 结果 |
|---|---|
| TypeScript 最小检查 | `npx vue-tsc --noEmit` → **exit 0** |
| 改动范围 | 仅上述 2 个文件；`git status` 确认 `CustomerDocUpload.vue` 未被修改 |
| 服务重启 | 不需要（纯前端，vite 热更新） |
| **未验证** | ① 浏览器实测（含异常注入、localStorage 写失败注入、迁移后旧键是否真的被删除——可用 DevTools → Application → Local Storage 观察 `attach-pending:v2:*` 键数量）；② 多标签页并发；③ R3 缺口（保存前刷新后上一草稿条目不可见）按指令保留 |

人工验收补充步骤（在 §6 基础上加两条）：

1. 保存成功后打开 DevTools → Application → Local Storage，确认 **不存在** `attach-pending:v2:{uid}:inquiry:inq-*`（草稿键已被清理），只剩 `…:inquiry:id-N`。
2. 制造一次绑定失败后保存 → 提示必须是「询价单已保存，部分附件绑定失败…」或「附件处理失败：…」，**不得**出现"提交失败"；随后刷新页面 → 重开该询价单 → 失败条目仍在附件区待绑定列表。


