# 修复公共工序组件只读模式图标不显示

任务：dev-20261007-009；日期：2026-10-07；执行者：Codex。

## 问题与证据

工艺路线详情/审核通过 ProcessOperation 展示工序图标，但该组件 vuedraggable 的导入名 draggable 与同名布尔 prop 冲突。模板编译为 $setup.draggable（导入组件对象，始终真），只读时 localItems 又按 props.draggable=false 初始化为空，最终进入空拖拽列表而没有图标。

前置只读核查：当前路线 1 的 4 个作业均有标准工序 icon 值（面板冲孔、面板凹凸、面板、面板隔片），对应 SVG 文件存在，运行中 Vite 注册模块包含 icon-面板冲孔。因此问题在组件分支选择。

## 修复范围

只修改 src/components/ProcessOperation/index.vue：vuedraggable 导入与标签统一改名 VueDraggable，条件明确为 props.draggable。保留 props、事件、排序和同步逻辑，修复所有复用该组件的只读界面。不改数据库业务数据、不操作服务生命周期。

## 验证

- 真实 Vue SSR 回归（使用实际 ProcessOperation、IconStepBadge、SvgIcon、vuedraggable 及 Element Plus）：修复前只读输出不含图标 SVG；修复后输出两个工序的 SVG href、数字下标与作业说明，无拖拽/删除控件；编辑模式保留图标、拖拽手柄和删除控件；无图标数据仍显示占位。
- vue-tsc --noEmit、Prettier、git diff --check、文档门禁通过。
- 运行中 Vite 请求 /src/components/ProcessOperation/index.vue 返回 HTTP 200，编译后的条件已经使用 props.draggable，确认正在提供修复版本；没有重启服务。
- npm run validate 仍在既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 状态映射违规处停止，后续门禁未执行，未扩大范围修改基线。整站构建的前序既有阻断（空 MaterialCategory.vue、eventConfig 模板语法）本次未改动，未重复整站构建。
- 未进行浏览器视觉验收或真实业务审批。SSR 测试中 Element Plus 提示缺少服务端 ID/z-index provider，渲染断言通过；该测试提示不影响客户端页面。

## 验收

刷新工艺路线详情或审核，面板冲孔、面板凹凸及组合中的面板/面板隔片应显示图标；打开修改界面，拖拽和下标/作业说明编辑仍可用。提交 hash 与验证摘要登记 sys_task.remark，提交后任务置待审核。
