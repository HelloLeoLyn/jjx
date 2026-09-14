# 看板任务状态/负责人变更加操作日志（dev-20260901-1249）

- 任务来源：sys_task dev-20260901-1249（P2）
- 依据：erp-audit 基线报告第 2 大缺口：kanban task 状态/负责人变更无 @Log，高频变更不可追溯

## 根因

BoardTaskController.updateTask（PATCH /kanban/board/{module}/tasks/{taskId}）是纯 updateById，无 @Log——任务拖拽改状态/换负责人在操作日志里无痕。

## 改动（1 个文件 + 验证）

`jjx-server/src/main/java/com/jjx/kanban/controller/BoardTaskController.java`：

在 updateTask 方法上加 @Log：
```java
@Log(module = "看板任务", businessType = BusinessType.UPDATE,
        bizType = "'kanban_task'", bizId = "#taskId", detail = "#updates")
```

- import com.jjx.system.annotation.BusinessType / com.jjx.system.annotation.Log
- detail="#updates" 把本次变更的字段 Map 写入操作日志 detail（SpEL 求值，Map 自动 JSON 序列化——与 OperLogAspect 的 detail 处理一致；若发现 detail 无法直接序列化 Map，退回 detail="#taskId" 并报告）
- 不改业务逻辑、不改其他端点

## 风险

- 工作区有用户 WIP（测试文件 D/M、jjx-docs/analysis 下 20260831 两个文档、Log.java 可能有用户未提交改动），只改本 spec 列出的文件
- 不要 git commit
- 无 migration（不加表不加列）
- 若 @Log 的 detail 表达式对 Map 参数求值报错（编译期无法发现，运行期才炸），在报告里说明并改用简单表达式

## 验证

- mvn -o clean test-compile
- 不跑全量测试
- 报告剩余问题
