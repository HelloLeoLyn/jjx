# BOM 详情接入公共明细组件

任务：dev-20261007-011；日期：2026-10-07；执行者：Codex。

## 背景与实施

用户在 BOM 详情看不到上一任务增加的项目列。排查确认 BomDetail 仍内嵌独立 el-table，只有 BomApproveDialog 引用 BomDetailTable。用户明确授权详情接入。

- BomDetail 删除独立明细表，改为引用 BomDetailTable，继续传入现有 bomDetailList，详情和审核共用项目、物料信息等列。
- BomDetailTable 增加 extra-columns 插槽，详情在原数量/损耗率之后插入模数、基数，保留详情特有信息。
- 公共组件允许配置 height 与 stripe，默认仍为300高、无条纹，审核无需调整；详情传 height=auto、stripe，沿用此前自动高度与条纹。
- 基本信息、接口、弹窗控制、数据加载与业务规则没有调整，不操作服务生命周期及业务数据。

## 验证

vue-tsc --noEmit、详情/公共明细/审核三个 Vue 组件脚本与模板编译、公共明细 Prettier 检查、git diff --check、文档门禁通过。BomDetail 仅做接入替换，保留原文件 CRLF 格式。

npm run validate 仍被任务前既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 状态映射问题阻断，后续门禁未执行；未扩大状态基线，未重复前序已阻断的整站构建。未做浏览器视觉验收或业务数据写入。

## 验收

刷新后打开 BOM 详情，物料名称后显示项目，空项目显示横线，模数和基数仍可查看；审核窗口继续显示项目，保持默认表格高度。提交 hash 和验证摘要记录在 sys_task.remark，提交后置待审核。
