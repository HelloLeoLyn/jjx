# dev-20260828-049 库存预警事件链路修复（事件名/注册/静默失败）

用户已确认：角色配置、事件标题内容、启用开关、event_type 均由用户自己在配置界面处理，
本任务只修**代码问题**。已实测确认的 3 处：

1. InventoryAlertServiceImpl.java:607 代码 fire 的 `stock.max` 与配置表存在的 `stock.over`
   不一致，LocalEventPublisher 按 event_code 精确查询（selectOne），查不到就 return。
2. InventoryAlertServiceImpl.java:632/:656 fire 的 `stock.expiry`、`stock.obsolete`
   在配置表中完全不存在，同样在 LocalEventPublisher:45-47 被静默吞掉。
3. LocalEventPublisher.java:45-47 event == null 时直接 return，不打任何日志，
   导致事件名不匹配/未注册时排查毫无痕迹（代码里各处 fire 的 try/catch + log.warn 只在
   抛异常时触发，覆盖不到这个分支）。

## 修改范围

### 1. InventoryAlertServiceImpl.java
- :607 `eventPublisher.fire("stock.max", ...)` → `eventPublisher.fire("stock.over", ...)`
- :632 `stock.expiry`、:656 `stock.obsolete` **保留代码触发**（检查逻辑本身正确），
  依赖第 2 步把配置注册上；**不要在代码里删这两个 fire**。
- 其余 fire（stock.low x2、stock.shortage x2）不动。

### 2. LocalEventPublisher.java:45-47
```java
if (event == null) {
    log.warn("事件未配置或已停用，跳过: {}", eventCode);
    return;
}
```
只加这一行日志，**不改任何其它行为**（跳过语义保持原样）。

### 3. 迁移 jjx-docs/sql/migrations/24_stock_event_config_fix.sql
幂等注册两条缺失事件（INSERT ... SELECT ... WHERE NOT EXISTS，参照既有迁移写法）：
- `stock.expiry`：库存过期预警 / inventory / notification / 标题与内容参照 stock.over
  的风格（"物料库存即将过期"类），priority=urgent，kanban_module=emergency，
  is_enabled=0（默认不启用，由用户在界面开启），target_role 留 `[]`（用户自理）。
- `stock.obsolete`：库存呆滞预警 / 同上结构，is_enabled=0，target_role `[]`。
- **不要动** stock.low / stock.over / stock.shortage 的既有行（角色、启用、文案由用户处理）。
- 迁移末尾注释写明：本次只补注册，启用与 target_role 由用户在 事件配置 界面维护。

## 明确不做

- 不改 stock.low / stock.over / stock.shortage 配置行的任何字段。
- 不改预警检查算法、不加新触发点、不动出库联动（OutboundServiceImpl:380 已确认存在）。
- 不处理数据层（inventory_stock 0 条、物料 safe_stock 全 0 属业务数据，用户自理）。

## 验证

1. `cd jjx-server && mvn -o clean test-compile` 通过。
2. 迁移 SQL 连续执行两次幂等（沙箱连不上 MySQL 就只写文件并告知，由用户在外部执行）。
3. 执行后 `SELECT event_code FROM sys_event_config WHERE event_code IN ('stock.expiry','stock.obsolete');`
   应返回 2 行。
4. 不要 git commit。不要碰工作区用户正在改的文件（sample-order/index.vue、
   SampleConvertCheckDialog.vue、workbench.vue）。
