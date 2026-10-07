# BOM 审核明细补充项目列

任务：dev-20261007-010；日期：2026-10-07；执行者：Codex。

## 背景与实施

用户要求 BOM 展示同修改界面增加“项目”。修改界面 BomItemEditor 的项目列位于物料名称后，宽160，绑定 processId，展示 processName。EngineeringBomItem 已包含 processName，现有 BOM 详情接口直接带出明细实体，无需接口或数据库调整。

BomDetailTable 在物料名称后增加宽160的只读“项目”列，读取 row.processName，缺失时显示横线，长名称支持溢出提示。此次只调整审核弹窗使用的明细展示组件，不扩展其他字段，不操作服务生命周期。

## 验证

vue-tsc --noEmit、BOM明细组件脚本与模板编译、Prettier、git diff --check、文档门禁通过。npm run validate 在既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 状态映射问题处停止，后续门禁未执行；未增加基线。未进行浏览器视觉验收或业务数据写入。

## 验收

打开 BOM 审核，确认物料名称后展示项目名称；未关联项目的明细显示“-”；长名称可查看完整提示。任务提交后置待审核，提交 hash 与验证记录写入 sys_task.remark。
