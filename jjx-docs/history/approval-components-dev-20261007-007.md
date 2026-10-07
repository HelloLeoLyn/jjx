# BOM 与工艺路线展示组件及公共审核意见表单

任务：dev-20261007-007；日期：2026-10-07；执行者：Codex。

## 背景与范围

用户批准将 BOM 审核的基本信息、明细拆成模块内展示组件，并将审核意见抽成公共组件；route 使用相同分层设计。开工工作区干净，进行中任务未声明冲突文件。只改前端组件和本记录，不改业务接口、数据库结构或服务生命周期。

## 实施

- BOM：BomBasicInfo 接收 EngineeringBom；BomDetailTable 接收 EngineeringBomItem[]，保留原审核表格的列、数量格式和来源类型展示。BomApproveDialog 保留数据加载、关闭/提交确认、权限、API 和 success/close 事件。统一基本信息初始化与重置，清空旧审核批注，状态默认值使用 BomStatusEnum.DRAFT。
- route：RouteBasicInfo 接收 EngineeringRoutingVO；RouteProcessTable 接收 EngineeringRoutingItemVO[]，包含工序分组、连续类别分块、工时汇总、工艺参数及 ProcessOperation 展示。分组从 props 派生，数据/字典变化后自动重算。RouteDetailView 保留 loadDetail/resetDetail 及 extra 插槽，已有详情和审核入口继续复用。
- 公共组件：src/components/Approval/ApprovalOpinionForm.vue；v-model 为 ApprovalOpinion { result, remark }，result 使用 src/enums/common/ApprovalEnum.ts。输入通过 update:modelValue 返回新对象，不直接修改 props。提供 validate()/clearValidate()；允许通过/驳回、意见必填、最小/最大长度、通过快捷意见与禁用状态由调用方配置。
- BOM 保留通过/驳回意见必填、至少 4 字、最多 500 字和“审核通过”快捷意见；对外 success 中 approveResult 仍映射为 ProductActions。
- route 保留通过意见可选、驳回必填、通过/驳回分别授权及原 approve/reject 事件；权限由业务组件判断并传入，公共组件在校验时拒绝无权限结果。
- components.d.ts 包含自动生成的公共组件声明。

## 验证

通过：vue-tsc --noEmit；本次 8 个 Vue 组件的 compileScript/compileTemplate；改动文件 Prettier 检查；git diff --check；针对实际组件 setup 的只读行为验证（BOM 必填/4字/500字、route 通过可空/驳回必填、无权限动作拒绝、v-model 不修改 props、树形/平铺分组及数据刷新）。

完整门禁存在任务前已在 HEAD 中的阻断，未扩大范围订正：

- npm run check:status-enums、npm run validate：src/views/engineering/product-spec/index.vue:431 的 OUTBOUND_STATUS 页内状态映射不在基线，validate 在此停止，后续全量检查未执行。未扩大状态基线。
- npx vite build：src/views/inventory/material/components/MaterialCategory.vue 为 0 字节；src/views/system/eventConfig/index.vue:239 模板表达式语法错误。以两个审核弹窗为入口的构建仍经项目公共依赖触达上述页面，同样阻断。
- 定向 ESLint：现有配置依赖 @vue/eslint-config-typescript 缺失。

未进行浏览器端真实审批或 E2E（避免改动业务数据）；未启动、停止或重启服务。完整构建及全量门禁仍需在上述已有问题另行处理后复验。

## 手工验收

1. BOM 打开审核：基本信息、明细及旧批注正常；空意见/不足4字不能提交，快捷意见可填写，通过/驳回继续调用对应接口。
2. 关闭后打开另一 BOM：新审核意见为空，基本信息旧批注不残留，旧校验提示清除。
3. route 详情及审核：基本信息、工序分组/排序/参数/图标/工时与此前一致。
4. route 仅通过或仅驳回权限：不可选无权限动作；通过允许空意见，驳回拒绝空或纯空白意见。

提交 hash 与上述验证摘要记录在 sys_task.remark；任务提交后置待审核。
