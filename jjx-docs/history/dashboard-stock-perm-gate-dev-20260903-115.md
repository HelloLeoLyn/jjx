# 首页仪表盘库存区块按权限收敛（dev-20260903-115）

状态：✅ 已实施（2026-09-03，用户指令"你来改，不用等codex"→ 手工实施，未走 Codex；vue-tsc 0 错误）
任务来源：sys_error_log 27 次 NotPermissionException——sales_clerk 打开首页即调
/inventory/stock/summary 与 /inventory/stock/low-stock，无 inventory:stock:view 权限 → 403。
用户裁定（2026-09-03）：销售角色不该看库存，显示与请求都按权限收敛，不放宽权限口径。

## 改动范围（只允许改这一个文件）

文件：`jjx-web/src/views/dashboard/index.vue`（全仓库仅此一处）

1. 顶部统计卡"库存项数"所在 el-col（约 :51）：加 `v-if="hasPermi('inventory:stock:view')"`
2. 顶部统计卡"低库存预警"所在 el-col（约 :77）：加 `v-if="hasPermi('inventory:stock:view')"`
3. "库存预警" el-card 所在 el-col（约 :189，含 header + alert-list + el-empty 整个卡片）：
   加同一个 v-if——无权限连空状态卡片都不显示
4. onMounted 并发请求段（约 :362-369）：`const canViewStock = hasPermi('inventory:stock:view')`，
   数组里两个库存调用换成 `canViewStock ? stockApi.summary() : Promise.resolve(null)` 与
   `canViewStock ? stockApi.getLowStock() : Promise.resolve(null)`——保留下标位置，后续
   `res.value?.data` 判空天然跳过
5. fillDefaults()（约 :407）：内部对 `stats.stockCount = 204` / 假数兜底两行加
   `hasPermi('inventory:stock:view')` 守卫——无权限角色不产生假库存数（卡片已隐藏，
   此条保证状态里也不留假数）

说明：
- hasPermi 为页面既有函数（:334）：perms 为空或含 `*` 时返回 true（admin 不受影响）
- 不改后端、不改其他卡片（物料总数/采购订单未报 403，不在范围）
- 布局：销售角色登录后四卡变两卡（物料总数+采购订单），不做补位
- 不要 prettier 重排无关行；不要 git commit；工作区有其他会话 WIP
  （jjx-web/src/layout/redirect.vue、views/inventory/outbound/print.vue、
  views/purchase/order/print.vue、jjx-docs/sql/migrations/53_*.sql 54_*.sql 等），
  一律不碰，只允许动 dashboard/index.vue 一个文件

## 验证

- `cd jjx-web && npm run validate`（check:status-enums + vue-tsc）通过
- 验收（用户执行）：sales_clerk 登录首页→库存两张卡与预警区不可见、Network 无两个
  库存请求；warehouse_keeper 登录→正常显示真实数据；sys_error_log 不再新增
  inventory:stock:view 的 NotPermissionException
