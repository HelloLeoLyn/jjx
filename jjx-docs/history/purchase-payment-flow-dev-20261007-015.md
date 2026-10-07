# 采购付款入口与申请金额口径统一

任务：dev-20261007-015；执行者：Codex；日期：2026-10-07。

## 背景与证据

用户确认保留订单快捷申请与采购付款管理两个入口，修复状态、金额口径与命名，不扩展完整应付及核销。

采购订单审批实际使用 common/ApproveStatusEnum：APPROVED=3。原付款候选 SQL 却筛 approval_status=4，导致 PO261007002/003 虽已批准且未付款，也不在菜单新增申请下拉中。原弹窗以订单金额减已付金额作为申请上限，后台还扣除待审批和已批准未支付的申请，界面与提交校验不一致。

付款服务原来将已完成付款的金额合计传入 updatePaymentInfo，而该 SQL 做 paid_amount + 合计，新增、审批、删除或确认后的重复汇总可能重复累计。

## 实施

- 订单列表顶部与行操作“付款”改为“申请付款”，申请弹窗和菜单新增/编辑名称明确区分申请与实际支付。保留付款审批、确认付款流程。
- 待付款订单查询参数来自已有 ApproveStatusEnum.APPROVED 和 PurchasePaymentStatusEnum 的待付、部分付款枚举；创建和编辑申请的后台准入均要求订单已批准。
- 新增只读 GET /purchase/payment/order/{orderId}/summary。允许付款查看、新增或编辑权限访问，返回订单金额、已付、申请中、可申请和订单名称/币种。
- 同一计算方法用于额度展示和提交校验：已付为已完成付款单合计；申请中包含未完成且未驳回的申请；可申请为订单金额减两者，最低为零。编辑排除本单，并验证排除的付款单属于该订单且待审批、未支付。未确定订单金额不能申请。
- 两个入口共用 PaymentAmountSummary.vue；加载期间禁用金额输入和提交；切换订单、编辑、关闭弹窗均保护旧响应。编辑保留原金额和订单名称，不以默认额度覆盖原申请。
- 新增 setPaymentSummary 写绝对汇总值，付款服务使用该方法，重复汇总不累加；旧订单域增量接口保留其既有语义，本轮未扩展清理该接口。
- 无业务数据修改、无表结构变更、无服务生命周期操作。既有 BomDetailTable.vue 在制修改不纳入。

## 验证

- 前端 vue-tsc --noEmit 通过。
- 使用实际 Vue 响应式与编译后的共享组件、模拟 API：加载、切换订单旧响应保护、编辑传排除 ID、关闭后响应保护、无订单禁用；共享组件及两页、弹窗共四个 Vue 模板编译通过。
- mvn -o -DskipTests compile 完整编译 968 个源码成功；修改的后端源码另经 Java 21 + Lombok 在隔离目录编译通过。
- 新增 PurchasePaymentFlowTest：申请额度统计/驳回释放、编辑本单排除及非法排除、非批准订单/零金额/超额拦截与精确额度边界、重复重算绝对值写入、候选 SQL 枚举参数，共 5 项通过。
- 普通 Maven 测试首次受 Mockito 动态附加限制影响，预加载代理后又遇到 target/test-classes 中 IDE 编译产物的 NoClassDefFoundError: PurchaseOrder。改用隔离编译的实际源码与 JUnit Platform 1.11.4 Launcher、预加载 Mockito 代理，5 项测试均成功；未修改构建或 IDE 配置。
- check:docs、git diff --check 通过。
- check:status-enums / validate 仍被既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 阻断；未修改状态基线。

## 生效与遗留

后端新增接口和规则需要重新启动后端进程；遵守用户规则，未自动重启。前端刷新后生效；未进行浏览器与已登录接口联调。本次没有扩展应付、预付款分类、银行核销或驳回重提。未批量订正既有订单已付金额，未来付款单操作会重新汇总该订单金额。
