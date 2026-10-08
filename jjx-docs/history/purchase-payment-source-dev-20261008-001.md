# 采购付款列表来源订单追溯

任务：dev-20261008-001；执行者：Codex；日期：2026-10-08。

## 背景

用户确认在付款列表增加采购订单号、供应商、订单号查询及点击详情。原列表展示订单 ID，无法直接辨认来源单据。

## 实施

- 订单 ID 列替换为采购订单号，置于付款单号后；新增供应商列，保留付款单号标识本次付款。
- 增加采购订单号模糊查询，查询时回第一页，重置时清空；后端先按参数化 LIKE 查询来源订单 ID，再过滤付款查询，保持分页与总数口径。无匹配订单时返回空结果，不能退回所有付款。
- 付款列表分页后一次批量查询当前页的不同来源订单，补入 orderNo 和 supplierName，不逐行请求；来源订单缺失时仍保留付款行。
- PurchasePayment 新增两个 @TableField(exist=false) 展示字段，DTO 增加 orderNo 查询字段，不改表结构、不保存冗余单号或供应商名称。
- 订单号可点击复用 OrderDetailDialog。持有 purchase:order:view 才显示链接并允许打开；无该权限仍显示订单号文本，详情接口保留已有权限校验。
- 不改付款审批、确认、金额汇总或状态流程，不修改业务数据，不操作运行服务。

## 验证

- vue-tsc --noEmit 通过。
- 实际 Vue 组件脚本/模板及模拟 API 验证：来源列、供应商列、订单详情权限、订单号查询参数、查询回第一页、重置清空订单号，模板编译通过。
- 新增 PurchasePaymentSourceTest 两项：批量补来源且保留缺失来源付款与分页总数；按订单号匹配 ID，字符串参数 LIKE 绑定，无匹配时空结果。
- 修改的后端源码及关联接口经 Java 21 + Lombok 隔离编译；预加载 Mockito 代理，JUnit Platform Launcher 运行新增两项、原金额五项和身份四项，共 11 项全部通过。首次扩展回归遇到本机 target 中接口编译产物干扰，补入实际接口/控制器源码后通过；未改构建或 IDE 配置。
- mvn -o -DskipTests compile 返回 BUILD SUCCESS / Nothing to compile；后端源码验证以隔离编译及测试为依据。
- check:docs、git diff --check 通过。
- check:status-enums / validate 仍被已有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 阻断；未改状态基线。

## 生效与遗留

后端需重新编译并重启，前端刷新；没有自行重启。未做已登录浏览器联调。本次范围为付款列表及来源订单详情入口，付款详情抽屉原有字段未扩展。
