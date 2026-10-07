# 采购订单供应商与订单类型回显修复

任务：dev-20261007-014；执行者：Codex；日期：2026-10-07。

## 现状证据

只读查询 purchase_order：PO261007001 的 supplier_id=55、supplier_name=新晟精密工業有限公司、order_type=0。供应商 55 的 status=1、del_flag=0；StatusEnum.NORMAL=1，未被活跃列表状态条件排除。类型下拉使用 normal/urgent 等字符串，直接赋入历史 0 无法匹配。供应商选择器原来没有统一 ID 类型或补加载机制；未获得浏览器失败响应，不能断言供应商异常仅由类型不一致造成。

## 实施

- 采购枚举模块集中兼容历史订单类型 0/1 到 normal/urgent；其余类型保持原值，不改数据库。
- 修改弹窗统一供应商 ID 并传入订单名称兜底。
- SupplierSelector 内部选项和选中值以字符串匹配，发出的供应商对象和 ID 保持接口类型。列表缺少当前供应商时按 ID 补加载；补充项仅展示当前值，不开放为新选择。失败仍可用订单快照名称显示。
- 补加载不发送 change，防止重置明细；旧详情响应不会覆盖新订单；用户选择和清空只发送一次事件。

## 验证

- vue-tsc --noEmit 通过。
- 实际 Vue 响应式对象、编译后的组件脚本和模拟 API 验证：历史 0/1、现行类型、数字/字符串 ID、补加载、名称兜底、补充项禁用、旧响应保护、补加载无 change、清空；两个 Vue 模板编译通过。未做浏览器联调。
- check:docs、git diff --check 通过。
- check:status-enums / validate 被既有 engineering/product-spec/index.vue:431 的 OUTBOUND_STATUS 阻断；未增加违规或修改基线。

## 遗留

刷新后确认 PO261007001 实际回显；未改变服务状态。其他会话 BomDetailTable.vue 在制修改未纳入。
