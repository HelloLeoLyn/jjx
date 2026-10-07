# 工艺路线详情与审核对齐修改界面双层 tabs

任务：dev-20261007-008；日期：2026-10-07；执行者：Codex。

## 背景与依据

用户批准详情/审核工序区域参考修改界面：第一层冲型组装/印刷，第二层面板/上线/下线/未分类，显示对应工序图标和名称，印刷参数分列，页签计数并默认选择有数据页签。修改界面 RouteItemIconEditor 按 majorCategory 分离 PRINT 和其他工序，按 processCategory 筛选结构；详情此前混合展示为连续结构分块。工作区基线为空，未发现进行中任务占用此次文件。

## 改动

- RouteProcessTable：采用同编辑界面的两层 el-tabs。冲型组装数量按组合/独立工序行计数，印刷按行计数；子结构页签显示对应行数，空页签显示明确空状态。
- 冲型组装：复用 ProcessOperation 的只读图标展示，保留图标顺序、数字下标、作业说明；补充每个作业的名称和组合子序号。保留工时、备注以及父子结构/旧 groupId 分组兼容。无 groupId 的旧独立行以稳定数组索引分组，序号与排序支持回退到 processOrder。
- 印刷：独立列展示名称、色号、油墨编号、网框编号、人工工时、机器工时、备注；有专属图标使用原图标，无图标使用 Printer。参数缺失或 JSON 非法显示横线，显式零工时保持为零。
- 每次替换路线 items 时重置默认页签；冲型组装有数据则优先选它，仅印刷有数据则选印刷；两个子结构分别选择第一个有数据页签。空数据回到默认页签。
- routeProcessTabs.ts：由 ProcessCategoryEnum 派生结构页签，共用结构归类函数；OTHER、空值及未知旧类别均归入未分类，避免旧数据不可见。RouteItemIconEditor 只替换页签定义和筛选函数，输入/拖拽/保存逻辑保持原接口。
- 既有 RouteDetailView 在 tabs 外展示基本信息，extra 插槽中的审核意见继续可见；详情和审核同时使用新布局。

## 验证与限制

通过：vue-tsc --noEmit；两个改动 Vue 组件的 compileScript/compileTemplate；对真实组件 setup 和共享分类函数的行为检查，涵盖大类分离、结构归类、页签数量、仅印刷/混合/空路线的默认页签及数据替换重置、印刷参数和非法 JSON、零工时、图标顺序/下标/作业说明、父子结构和旧 groupId 分组；Prettier、git diff --check、文档门禁。

全量 npm run check:status-enums 和 npm run validate 仍被 HEAD 中既有 product-spec/index.vue:431 OUTBOUND_STATUS 状态映射阻断，未增加基线、未执行 validate 后续门禁。npx vite build 仍被既有 MaterialCategory.vue 空文件和 system/eventConfig/index.vue:239 模板语法错误阻断。上述均未扩大范围修复。

未进行浏览器视觉验证或真实业务审批；未操作服务生命周期、业务接口及业务数据。提交 hash 和验证摘要记录在 sys_task.remark。

## 手工验收

1. 分别在路线详情和审核打开混合路线：冲型组装/印刷大类及各子结构数量与编辑界面对应。
2. 查看组合及单独工序：图标、名称、下标、说明、顺序和工时正确，无编辑入口。
3. 切换到印刷：参数分列，备注可见，无参数显示横线。
4. 仅印刷或仅下线数据的路线默认选中有数据页签；关闭并换路线后默认页签重算。
5. 未分类及 OTHER 历史数据可查看；空页签有空状态；切换 tabs 不影响审核意见。
