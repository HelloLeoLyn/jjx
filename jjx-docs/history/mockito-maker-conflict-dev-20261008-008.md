# 统一采购付款测试 Mock 模式，修复全量测试冲突

任务：dev-20261008-008；日期：2026-10-08；执行：Codex。

## 问题与证据

用户提供全量 Maven 测试失败日志：266项、34失败、0错误、3跳过。读取 Surefire XML 后确认34项均在 Mockito `MockUtil.getMockHandlerOrNull:160` 触发内部断言，涉及采购付款、生产完工、设备绑定、工序候选人、报工及追溯等测试。此处断言要求返回 MockHandler 的 MockMaker 与其设置一致，尚未进入各项业务断言。

`PurchasePaymentFlowTest` 两处显式指定 `withSettings().mockMaker("mock-maker-subclass")`，其他测试使用默认模式。用户批准按建议统一。

## 修改范围

仅移除上述两处显式模式，改为默认 `mock(PurchaseOrderMapper.class)` 和 `mock(PurchasePaymentMapper.class)`。不调整业务代码、测试断言、Mockito版本或构建设置，不关闭断言，不跳过失败测试。

## 验证

- 初次沙箱内 `mvn -o test`：Mockito附加测试JVM受环境限制，出现插件初始化错误；没有以该次结果判断业务回归。
- 在允许附加测试JVM的环境重跑同一 `mvn -o test`：BUILD SUCCESS，266项、0失败、0错误、3跳过，耗时10.690秒。此前34项失败均消失；这验证了混用Mock创建模式是本次失败的触发原因。
- 原有3项跳过保留，未扩大跳过范围。
- `npm run check:docs`：通过。
- `git diff --check`：通过。

## 遗留

仅测试代码调整，无运行服务重启或数据库迁移要求。仓库已有工作簿模板修改及产品测试删除不属于本任务，未纳入提交。
