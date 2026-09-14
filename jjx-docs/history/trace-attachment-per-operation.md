# 流水附件按操作精确归属（方案二）

目标：流水每条记录点开，只显示该次操作自己的附件（来源=日志 detail 里的 attachments），
不再按单据级别查全量附件往行里合并。历史数据不考虑，只保证后续行为正确。

## 只改一个文件：jjx-web/src/components/TraceTimeline/index.vue

### 1. 删除整单附件查询与合并（loadRowContent 尾部，约 414-430 行）

删掉这整段（含 try/catch）：
```ts
// 独立附件（detail 未引用的，如询价图纸）：按 bizType+bizId 拉全单附件，与行内附件去重合并
try {
  let all = attachmentCache.get(key)
  if (!all) {
    const res: any = await attachmentApi.list(attachmentBizType, Number(row.bizId))
    ...
  }
  const merged = new Map<number, TraceAttachment>()
  ...
  row.attachments = [...merged.values()]
} catch { ... }
```
`row.attachments` 只保留后端 VO 从 detail 解析出来的值，前端不再改写它。

### 2. 撤掉 bizType 映射，恢复原始缓存 key

当前（本次新加，要去掉）：
```ts
const attachmentBizType = row.bizType === 'sample' ? 'sample_order' : row.bizType
const key = `${attachmentBizType || ''}:${row.bizId || ''}`
if (!attachmentBizType || !row.bizId) return
```
恢复为改动前的形式（供审核记录缓存使用）：
```ts
const key = `${row.bizType || ''}:${row.bizId || ''}`
if (!row.bizType || !row.bizId) return
```
不再需要任何 sample→sample_order 的兼容映射。

### 3. 审核记录那条路保持原样

`row.isReview` 分支（/api/trace/reviews + reviewCache + matchReview）逻辑、请求参数、
缓存键行为必须与本次改动之前完全一致，一行都不要动。

### 4. 清理死代码

删除因第 1 步而不再使用的：`attachmentApi` import、`attachmentCache`、以及任何只服务于
该段逻辑的类型/变量。不要留未使用的 import 或变量（会被 vue-tsc / lint 抓）。

### 5. 变更内容与附件展示不动

`detailAttachments` / `imageAttachments` / `fileAttachments` 等展示层计算属性、
`changes` 展示区块，一律不改。

## 不要碰

- `jjx-web/src/views/sales/sample-order/index.vue`（用户正在重构）
- `jjx-web/src/views/engineering/sample-workbench/workbench.vue`
- 后端任何文件、registry.ts、以及 048 已完成的 attachmentIds 改动（那是本方案的数据来源，保留）
- `scripts/status-magic-baseline.json`

## 验证

- `cd jjx-web && npm run validate` 必须通过（状态门禁 + vue-tsc）
- 不要 git commit
