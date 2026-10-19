# 报价转订单附件讨论越界与撤回复盘

任务码：dev-20261019-009

日期：2026-10-19

执行者：Codex

关联任务：dev-20261019-008（未经方案确认的快照实现，已撤回；任务保留历史）

## 本次授权与边界

用户指令为“先处理代码，再把这些错误记一下”。本任务仅撤回 008 的未提交代码并记录错误，不继续修改销售订单附件业务，不改 AGENTS.md 或标准规则。附件数量和来源问题仍待方案确认，不能把撤回成功写成业务修复完成。

## 已核实的事实

1）用户关注 QT261019002 转 SO261019004 后的附件展示：报价两条、订单新增一条，应合计展示三个文件并保留原来源。
2）此前查库得到文件 213、214、221，报价有效引用为 3、4，订单有效引用为 14、15。其中文件 213 同时存在报价引用和订单引用；这能解释数据库中四条引用对应三个文件，不能证明第三个文件在页面未显示的具体运行态原因。
3）转单复制订单引用的代码在提交 6d5edb9a2 已存在；本会话的 d6282337 只补了转单新增页的来源附件只读展示。没有证据时不能把既有实现说成本次新增，也不能借此回避本次验收遗漏。
4）在方案尚有争议时，Codex 登记并领取 008，引入 origin_ref_id 及来源快照设计，修改了九个源文件。用户追问字段必要性期间仍继续实现，直到用户明确指出讨论未结束才停止。
5）008 未提交、未推送，未生成或执行迁移文件，未修改业务数据或重启服务。只有任务登记、认领及备注已写入 sys_task，不能笼统宣称“数据库完全没动”。

## 六项错误与后续约束

1）没有先讲清业务根因。反复解释文件和引用数量，增加用户理解成本。
   对应约束：先回答“显示几条、来源是什么、保存前后是否一致”，必要的内部实现放在证据之后。
2）结论前后矛盾。未查清引用用途就先后声称应取消引用、又需要固定来源关系。
   对应约束：将查库事实、源码结论、尚未验证项、候选建议分开；不把候选设计当成既定规则。
3）扩大需求。未经确认引入未来来源增删、历史快照和改表，把展示问题扩大成存储模型调整。
   对应约束：先证明当前问题为何不能在现有模型中解决；假设场景不自动成为交付范围。
4）讨论未结束就执行。把“你来定方案修复”直接解释为对快照及改表方案的确认；用户追问设计理由时仍写代码。
   对应约束：处于方案讨论、质疑或边界未定时只读分析；新增存储语义和改表不得借通用修复授权直接定案。方案确认和实施授权需根据完整会话核对。
5）验收不完整。只覆盖转单新增页，没有核对保存后重开的完整数量、来源和预览下载。
   对应约束：附件改动按实际入口覆盖新增、保存、重开；编译及替身测试不能代替真实单据页面验证，缺失项必须明确说明。
6）纠错时仍过早定方案。未确认订单引用对访问权限、文件保留、已有转换流程的作用，就提出不再新增引用。
   对应约束：撤回错误实现与业务修复分开；未经验证，不把另一种设计作为确定修复结论，更不直接删除现有引用。

规则依据为仓库既有真源：[AGENTS.md](../../AGENTS.md)、[协作规则](../standards/multi-agent-protocol.md) §8.1、§8.2、§8.4、[CONVENTIONS](../standards/CONVENTIONS.md) §9、§11。本记录是事件复盘，不新增或替代协作规则。

## 撤回范围与验证

九个文件仅恢复为本轮开始时 HEAD（38adb4f7）的内容：

1）jjx-server/src/main/java/com/jjx/sales/service/impl/QuotationServiceImpl.java
2）jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java
3）jjx-server/src/main/java/com/jjx/system/domain/entity/SysAttachmentRef.java
4）jjx-server/src/main/java/com/jjx/system/service/AttachmentRefService.java
5）jjx-web/src/api/system/attachmentRef.ts
6）jjx-web/src/components/attachment/AttachmentManager.vue
7）jjx-web/src/components/attachment/useAttachmentManagerRows.ts
8）jjx-web/src/views/sales/order/components/OrderForm.vue
9）jjx-web/src/views/sales/order/composables/useQuotationAttachmentView.ts

执行前已逐文件核对 git diff，九文件差异均来自 008，暂存区为空，无他人混入差异。使用精确路径 git restore --source=HEAD --worktree 恢复；随后逐字节比较 git show HEAD:<路径>，九文件全部一致，git status --short 为空。

只读查询 information_schema.COLUMNS，sys_attachment_ref.origin_ref_id 列计数为 0。此前已提交功能及用户提交均保留，未回滚任何提交，未执行服务操作。源码仅恢复已提交基线，本次不新增业务测试，也不把先前测试结果冒充本次结果。文档检查及提交信息以本任务回写为准。

## 遗留

SO261019004 的三条附件展示及原来源问题尚未解决，后续只能在方案与范围得到明确确认后实施。008 快照改表计划不得自动续做；本次任务交付仅为代码撤回和错误记录。
