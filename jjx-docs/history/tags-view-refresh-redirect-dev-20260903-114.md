# 标签页右键刷新路径翻倍修复（dev-20260903-114）

状态：⏳待实施（sys_task 1277，dev-1788341029953）

## 现象

ERP 顶栏标签页（TagsView）右键 → 刷新，URL 从正常页面路径变成 /redirect 前缀翻倍路径并卡死。
例：/kanban/index 刷新一次 → http://localhost:3000/redirect/kanban/kanban/index。

## 根因（已实测确认）

标签"刷新"是内部假跳转（不是浏览器 F5）：

1. TagsView.vue:75 `refreshSelectedTag` 先跳 `/redirect` + tag.path
   （如 `/redirect/kanban/index`），目的：把页面整体卸载，再跳回来重新挂载以重新拉数据。
2. 路由 `/redirect/:path(.*)`（router/index.ts:337-343）接住，redirect.vue:11 跳回时，
   `params.path` 不含开头 `/`（`(.*)` 捕获剥掉了前导斜杠，拿到的是 `kanban/index`）。
3. redirect.vue 现在写 `router.replace({ path: String(path), query })` —— path 无前导 `/`，
   vue-router 4 将不带 `/` 的 path 按相对路径解析，在当前 `/redirect/kanban/index`
   的目录基础上再拼一次 → `/redirect/kanban/kanban/index`（前缀翻倍）。
4. 翻倍后的地址仍匹配同一条 `/redirect/:path(.*)` 路由，组件实例被复用、
   setup 顶层代码不重新执行 → 不再跳转，用户卡死在错误 URL。

vue-router 4.6.4 实测复现（仓库 node_modules）：
- 当前 `/redirect/kanban/index`，`params.path = "kanban/index"`
- `replace({ path: 'kanban/index' })` → `/redirect/kanban/kanban/index`（与用户观测完全一致）
- `replace({ path: '/' + path })` → `/kanban/index`（正确）

## 修复（唯一改动）

文件：jjx-web/src/layout/redirect.vue，第 11 行，跳回时补回前导 `/`：

```ts
// 改前
router.replace({ path: String(path), query })
// 改后
router.replace({ path: '/' + String(path), query })
```

- 该文件是 CRLF 行尾 —— 只改这一行内容，不要整文件重写/格式化，保持 CRLF。
- 不要动 TagsView.vue 与其它任何文件；`/redirect` 机制本身（先卸载再跳回）是设计意图，保留。
- 不要 git commit；工作区其它脏文件（inventory/outbound/print.vue、purchase/order/print.vue、
  migrations/53、54 等）与本任务无关，禁止触碰。
- 不涉及后端与数据库。

## 明确不做

- 不做标签页 query 快照增强（tag.query 首次访问快照的既有语义保持现状，不是本任务范围）。
- 不改 /redirect 白名单、路由守卫、keep-alive 缓存逻辑。

## 验证

1. 改动后用 `npx vue-tsc --noEmit` 确认无新增类型错误（仓库可能有其它 WIP 文件的
   既有报错，与本改动无关的不要修、在报告里说明即可）。
2. 前端跑起来后人工验收（交给用户）：
   - 任选页面标签右键 → 刷新：URL 应回到原路径不变，页面重新挂载加载数据；
   - 带 query 的页面（如列表带筛选参数进入的详情/打印页）刷新后参数不丢；
   - 连续刷新两次不再出现 /redirect 前缀翻倍。
