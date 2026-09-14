# 打印中心体验：data 类跳转 + 三态标识（dev-20260901-1240）

- 任务来源：sys_task dev-20260901-1240（P2）
- 依据：jjx-docs/analysis/print-system-analysis-20260831.md（P2：打印中心 data 类无跳转；P0 建议三态标识）

## 根因

1. `jjx-web/src/views/production/quality-print/index.vue:37`：data 类模板只显示"请到对应业务模块打印"提示，无跳转，用户得自己找路。
2. 41 份 biz_type 占位与 6 份 data 实现脱节：blank+有 biz_type 的模板看起来"能联动"实际只能打空白表，用户误判。

## 改动（只改 1 个前端文件：jjx-web/src/views/production/quality-print/index.vue）

1. **data 类跳转**：新增 `BIZ_TYPE_ROUTE` 映射表（biz_type → 业务模块路由），操作列 data 类由静态 tag 改为"去业务模块打印"按钮（router.push 到映射路由）。映射：
   - quality_inspection → /production/quality
   - operation_execution → /production/execution
   - inventory_inbound → /inventory/inbound
   - inventory_outbound → /inventory/outbound
   - production_order → /production/order
   - purchase_order → /purchase/order
   - sales_delivery → /sales/delivery
   - sales_order_review → /sales/order
   - sales_inquiry → /sales/inquiry
   - product → /product/list
   - production_equipment → /production/equipment
   - purchase_supplier → /purchase/supplier
   - 未映射的 biz_type（sales_contract 已删表、engineering_change 未实现）→ 保持原提示 tag

2. **三态标识**：列表加"联动状态"列：
   - 已联动：category === 'data'（success tag）
   - 规划中：category === 'blank' 且 bizType 非空（warning tag）
   - 空白表：category === 'blank' 且 bizType 为空（info tag）
   用 QualityTemplateCategory（'data'/'blank'）与 row.bizType 判断，不新增枚举。

## 风险

- 工作区有用户 WIP，只改本 spec 列出的 1 个文件
- 不要 git commit
- 无 migration、无后端改动
- AGENTS.md：状态标识用枚举/常量判断，不写裸字符串（category 判断用现有 QualityTemplateCategory 常量）
- vue-tsc 报错若来自用户 WIP 文件过滤掉

## 验证

- cd jjx-web && npx vue-tsc --noEmit（自己文件零错误）
- npm run check:status-enums
- 报告剩余问题
