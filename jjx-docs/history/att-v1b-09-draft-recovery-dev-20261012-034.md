> 最新状态（2026-10-12 本轮续办）：已按指定函数/行隔离实施，原只读阻塞记录保留；当前实现、验证及未验项见末尾 §7。未commit/push。

# ATT-V1B-09 报告：待绑定队列「刷新可恢复」+ 过期文案出路（dev-20261012-034）——【只读·阻塞】

> 任务码：dev-20261012-034（ATT-V1B-09，P2）｜ 日期：2026-10-12 ｜ 状态：**只读核查交付（代码改动被阻塞）**
> 关联：dev-20261012-002（公共附件总任务）｜dev-20261012-024（同改 useAttachmentRefs.ts）

---

## 1. 阻塞（命中任务卡第 6 点）

**目标文件均为他人在制改动、无法隔离：**

| 文件 | git 状态 | 说明 |
|---|---|---|
| `jjx-web/src/composables/useAttachmentRefs.ts` | **`??` 未跟踪** | V1-B WP 在制新文件（2026-10-12 建），**无基线可隔离**我的改动 |
| `jjx-web/src/views/sales/inquiry/index.vue` | **`M` 未提交** | V1-B 在制（`git diff --stat` +53/−5）|
| `jjx-web/src/views/sales/inquiry/components/InquiryDetailDialog.vue` | `M` 未提交 | 同批在制 |

→ `useAttachmentRefs.ts` 是**未跟踪**文件（对方 WIP，提交时机由人工定），在其上叠加我的改动**无法与本任务分离**，故**停在只读核查，不改任何代码**。

## 2. 只读核查（根因，均有行号证据）

**`views/sales/inquiry/index.vue`**
- `:605` `const draftId = ref('')`
- `:816` `handleAdd()`：`draftId.value = newDraftId()` —— **每次「新增」都重新生成 draftId**。
- `:826`（编辑已有单据）/`:1034`（新增保存成功后）`draftId.value = ''`。
- `:329` 传给 `AttachmentCenter :draft-id="draftId"`；`:616` `bindPendingAttachments()` 保存后绑定。

**`composables/useAttachmentRefs.ts`**
- `:110` `draftKey = sourceId ? 'id-'+sourceId : explicitDraftId`
- `:111` `storageKey = ${STORAGE_PREFIX}:${userId}:${sourceType}:${draftKey}`
- `:158 restore()`：按 `storageKey` 从 `localStorage` 恢复待绑定队列
- `:352 watch(storageKey, …)`：键切换时「新键空→先 restore；persist 成功后 dropStoredKey(旧键)」

**根因**：刷新页面后重开「新增」→ `handleAdd` **又生成了新 `draftId`** → `storageKey` 随之变化 → 旧键里持久化的待绑定队列**读不回来**（`restore()` 读的是新键）→ **待绑定附件"不可见"**。且每个新 `draftId` 对应一个 `localStorage` 键，**旧键只在新键 persist 成功时才被 drop**，`handleAdd` 反复重生成也会留下未被清理的旧键 → **键累积**。

## 3. 计划修复（**未实施**——被阻塞）

1. `views/sales/inquiry/index.vue` `handleAdd`：**`draftId` 仅在为空时生成**（`if (!draftId.value) draftId.value = newDraftId()`）→ 同一浏览会话内复用；保存成功后已 `:1034 draftId.value=''`（已有）。
2. `useAttachmentRefs.ts`：保证旧 `localStorage` 键**不无限累积**（键切换迁移/清理；结合 024 变更**串行或按行隔离**）。
3. 过期出路：**不做主动过期检测**（后端无「按 id 查 TEMP」接口）；仅在**重试绑定失败**时标「可能已过期/不存在」+ 引导**移除**。并把 `GET /refs/temp/mine` 记为**后端缺口**。
4. 不跨单据串队列（沿用 draftKey 隔离语义）。

## 4. 验收（**未验证**——因阻塞未改码）

| 验收项 | 结果 |
|---|---|
| 新建→上传 2 个→刷新→重开「新增」：待绑定 2 仍在 | **未验证**（需先落地 §3.1）|
| 保存成功后再开「新增」：队列为空 | **未验证** |
| 过期分支 | **未验证**（不做主动检测，且不写库构造）|

**过期分支人工构造步骤（供用户执行）**：
```sql
-- 把某测试附件的 expires_at 改到过去（仅测试数据，勿碰真实附件 12/13）
UPDATE sys_attachment SET expires_at = '2020-01-01 00:00:00' WHERE id = <测试附件ID>;
```
构造后在待绑定区**重试绑定**，观察是否落「可能已过期/不存在 + 可移除」。恢复：把 `expires_at` 改回（或删除该测试附件）。

## 5. 未执行声明

未改后端/前端代码、未改库、未 commit/push。工作区仅新增本报告（及 INDEX 行）。

## 6. 解除条件

- 由人工决定 V1-B 在制改动（`useAttachmentRefs.ts` 等）**提交或让行**后，本任务可继续；
- 或明确授权我在该未跟踪文件上直接改（承担与对方在制品混同的风险）。


## 7. 本轮续办：实施、证据与验收交接

### 7.1 文件隔离与范围

已记录本轮开始前两个目标文件及报告/INDEX副本。useAttachmentRefs保留033内容指纹requestKey实现及其他在制内容；只改persist空键处理、restore错误恢复、bindPendingOne失败提示与watch(storageKey)迁移条件。询价页只加用户隔离sessionStorage草稿辅助函数、handleAdd恢复调用和保存成功清理调用；专用客供入口、原新增/编辑API、失败隔离和详情组件不改。没有提交整个在制文件。

旧报告§1～§6属于前一轮只读阻塞历史，本节为当前续办结果；保留原文不覆盖。INDEX只更新本任务已有索引描述，不新增重复条目。

### 7.2 草稿刷新恢复

草稿身份存sessionStorage：`attach-draft:v1:<userId>:inquiry`；队列继续使用已有localStorage：`attach-pending:v2:<userId>:<sourceType>:<draftId或id-N>`。handleAdd读取同一浏览标签页/用户的草稿标识，只在为空时生成newDraftId，关闭/取消后重开复用。成功提交并完成原附件处理后clearDraftId删除会话标识，下一次新增生成新草稿。

编辑已有单据只用id-N，临时清空界面draftId不删除其他未保存草稿的会话标识；切回新增可恢复原草稿。用户变更使用不同会话键，不能用另一用户草稿。sessionStorage不可用会记录明确警告，此时内存内可复用，但刷新恢复不能保证；无跨浏览器/跨机器恢复承诺。

persist不再写空数组，空队列直接删除对应键。watch只允许“上一键=本用户本来源当前显式draftId、新键=保存后的单据”迁移队列，目标键成功写入后才删旧键。其他单据/用户/草稿切换先清内存并读取目标键，禁止带入旧单据队列。没有扫描或删除不明归属的历史非空草稿；本次消除正常新建/保存过程中重复新建空键和旧迁移键累积，不冒称已清理此前遗留的所有键。

### 7.3 被动失败提示与后端缺口

不主动查询TEMP有效期。重试绑定得到“附件不存在/已删除/已过期”时，持久化entry.error并提示“可能已过期或不存在，可移除后重新上传”，保留条目及现有移除按钮；restore恢复error，刷新仍可看见指引。

后端AttachmentAccessService对过期TEMP也返回“无权查看附件或历史来源尚未确认”；对此提示保留原文，追加“可能已过期或不存在，也可能缺少读取权限，确认后可移除并重新上传”，不武断认定过期。编辑权限错误、网络错误等保持原义，不标成过期。

后端缺口：没有按id读取TEMP状态的接口，也没有`GET /refs/temp/mine`。本任务只记录该缺口，不新增接口；若未来实现，需要按登录用户隔离、分页、返回生命周期与过期时间，并另行授权。当前无主动过期检测能力。

### 7.4 局部真实命令输出

临时Node VM转译并运行实际useAttachmentRefs源码；TypeScript AST提取询价页实际草稿函数，使用共享session/localStorage模拟刷新重建。后端API为模拟，不是实际浏览器或业务库证据。实际输出：

```text
同会话刷新重建页面：草稿ID相同，待绑定2条恢复
保存迁移：旧草稿键删除；绑定后空键删除；清会话标识后新增队列为空
切换已有单据不迁移队列；切回恢复；不同用户同单据不串队列
模拟不存在失败：提示移除重传且刷新保留；权限失败保持原义
新键持久化失败：不删除原草稿唯一副本
```

另外覆盖实际后端“无权查看附件或历史来源尚未确认”异常，断言提示保留权限可能性及移除引导。模拟验证不替代真实过期验收。

`npx vue-tsc --noEmit`退出码0；`npm run check:status-enums`退出码0：状态魔法值门禁通过（存量基线122处，新增0处）。报告保持UTF-8 BOM；`npm run check:docs`退出码0：文档门禁通过（history 239篇 / modules 4篇 / task-docs 1篇）。

### 7.5 真实浏览器与过期验收（未验证）

另一Hermes：新增询价上传两文件→刷新→重开新增，观察待绑定2；保存成功后再开新增，应为空。另检查从未保存草稿切到已有单据再切回，不串队列；失败条目在其已保存单据中可恢复。记录Local Storage旧草稿键清理与Session Storage草稿标识。

真实过期分支**未验证**，本轮没有改库。用户可自行在专用测试附件上构造（只允许自己的TEMP且无有效引用，先只读确认id、owner及状态），记录原expires_at后执行：

```sql
-- 由用户执行，替换已核实的测试ID与自己的用户ID；agent本轮未执行。
UPDATE sys_attachment
SET expires_at=DATE_SUB(NOW(),INTERVAL 1 DAY)
WHERE id=<测试附件ID> AND upload_owner='<自己的用户ID>'
  AND upload_state='TEMP' AND deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_attachment_ref r
                  WHERE r.attachment_id=sys_attachment.id AND r.deleted=0);
```

在已保存测试单据的待绑定区点重试，当前后端预计返回读取拒绝；页面应显示可能过期/不存在或缺少读取权限及移除出路。记录真实响应与截图，不预先记PASS。测试后由用户按原expires_at恢复或按测试处置决定；agent不主动清库/改期/重启服务。

### 7.6 回写与遗留

原remark长度446，本轮不覆盖或截断旧备注；详细结果追加description，remark仅追加短更正指针，status保持/置2供另一Hermes审核。不commit/push。遗留：真实浏览器刷新和过期验收；sessionStorage不可用时刷新恢复限制；旧非空孤立草稿不自动扫删；后端TEMP查询接口缺口。
