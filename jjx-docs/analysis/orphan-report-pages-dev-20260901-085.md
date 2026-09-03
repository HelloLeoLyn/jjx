# 孤儿报表页处理：purchase/report 挂菜单 + inventory/report 删除（任务 1270 / dev-20260901-085）

2026-09-03 分析定案。用户裁定：采购报表挂菜单；库存报表删除(mock 假数据，假功能不上线)；
views/production/report 孤儿 **不在本任务处理**（已另登记 task 1313 / dev-20260903-107）。

## 事实与结论（证据）

| 页面 | 行数 | 内容 | 结论 |
|---|---|---|---|
| views/purchase/report/index.vue | 267 | 调真实接口 /purchase/order/statistics（PurchaseOrderController:215）+ /purchase/supplier/statistics（PurchaseSupplierController:185→PurchaseSupplierServiceImpl:310），实测 200 真实结构 | 挂菜单 |
| views/inventory/report/index.vue | 658 | reportData 硬编码假数（totalMaterials:156/totalCost:1250000/…）、mockStockSummaryData 数组、无任何真实 api 调用 | 删除 |
| views/production/report/index.vue + print.vue | — | 无 C 菜单、无引用 | 不处理，见 1313 |

参照系：销售报表 menu 215（C，parent 13，path 'report'，component views/sales/report/index.vue，
perms sales:report:view，visible 0，icon TrendCharts）同型已挂。

## 改动清单

### 后端/菜单（迁移文件 43_purchase_report_menu.sql，我在沙箱外执行）
1. sys_menu 新增 C 菜单「采购报表」：parent_id=36（采购管理 M），order_num=6，
   path='report'，component='views/purchase/report/index.vue'，menu_type='C'，
   perms='purchase:report:view'，route_name='PurchaseReport'，ancestors='0,36'，
   visible='0'，status='0'，is_frame=1，is_cache=0，icon='TrendCharts'。
   （NOT EXISTS 按 route_name='PurchaseReport' 幂等）
2. sys_role_menu 授权：从兄弟菜单 采购订单（route_name='PurchaseOrder'）复制角色授权
   （INSERT IGNORE ... SELECT 动态匹配，不硬编码 menu_id）。
3. 删除孤儿 F 按钮 127「库存报表」（parent_id=26 库存列表下，perms inventory:report:view）：
   先删 sys_role_menu（4 条），再删 sys_menu 行（按 perms+parent_id+menu_type 动态匹配）。

### 前端（交 codex）
- 删除 jjx-web/src/views/inventory/report/index.vue（全站无引用，先 grep 复核再删）。
- 不做其他任何改动。

## 明确不做
- views/production/report 孤儿（task 1313 另行处理）。
- 不补采购报表页本身的权限点/按钮（页面只有统计 GET，接口各自带
  @SaCheckPermission purchase:order:view / purchase:supplier:view，均已授权）。
- 不处理 42 迁移遗留（sales:dashboard F 已注册，与本任务无关）。

## 验证
1. 我（沙箱外）：执行 43_purchase_report_menu.sql → scripts/check-menu-integrity.sh 三项 0 行
   → SELECT 回查新 C 行与授权行数 > 0、127 行与 sys_role_menu 关联 = 0。
2. codex：删除文件后 grep -rn "inventory/report" jjx-web/src 确认无残留引用（预期 0）。
3. 用户侧：重新登录前端 → 采购管理下出现「采购报表」菜单 → 页面统计卡片出真实数字（当前 dev
   采购数据为空时显示 0，属正常）；库存列表下不再有多余报表权限概念（F 不渲染菜单，无 UI 可见变化）。
