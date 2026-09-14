# EventBus 事件驱动方案

## 一、整体架构

```
业务方法（@Event 注解 / eventBus.fire()）
  │
  ▼
EventBus 拦截器
  │
  ├── 事务提交后执行（避免事务未提交事件先发）
  │
  ├── LogHandler → 写 sys_oper_log（按日志优化方案）
  ├── TaskHandler → 写 sys_task（按任务优化方案）  
  └── NotifHandler → 写 sys_notification（按通知优化方案）
```

## 二、注解定义

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    String value();           // 事件编码，如 "product.approved"
    String bizId() default ""; // 支持 SpEL，如 "#dto.productId"
    String bizType() default ""; // 支持 SpEL
}
```

## 三、业务代码使用

### 方式一：注解（90% 场景）

```java
@Event(value = "product.approved", bizId = "#dto.productId")
public boolean approveProduct(ProductUpdateDTO dto) {
    // 只关心业务逻辑，事件由 AOP 自动处理
    return updateStatus(dto);
}
```

### 方式二：手动调用（10% 需要传参的场景）

```java
@Transactional
public boolean approveProduct(ProductUpdateDTO dto) {
    Product product = productMapper.selectById(dto.getProductId());
    // ... 校验 ...
    boolean updated = updateStatus(dto);
    
    if (updated) {
        // 事务提交后才真正触发事件
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    eventBus.fire("product.approved", Map.of(
                        "productId", product.getProductId(),
                        "productCode", product.getProductCode(),
                        "productName", product.getProductName(),
                        "operator", SecurityUtils.getUsername()
                    ));
                }
            }
        );
    }
    return updated;
}
```

## 四、EventBus 核心实现

```java
@Component
public class EventBus {
    private final List<EventHandler> handlers;

    public EventBus(
        LogHandler logHandler,
        TaskHandler taskHandler,
        NotifHandler notifHandler
    ) {
        this.handlers = Arrays.asList(logHandler, taskHandler, notifHandler);
    }

    public void fire(String eventCode, Map<String, Object> data) {
        for (EventHandler handler : handlers) {
            handler.handle(eventCode, data);
        }
    }
}

public interface EventHandler {
    void handle(String eventCode, Map<String, Object> data);
}
```

## 五、AOP 切面（注解驱动）

```java
@Aspect
@Component
public class EventAspect {

    @Autowired private EventBus eventBus;

    @Around("@annotation(event)")
    public Object around(ProceedingJoinPoint pjp, Event event) throws Throwable {
        Object result = pjp.proceed();

        // 只在成功时触发事件
        if (result instanceof Boolean && !(Boolean) result) {
            return result;
        }
        if (result instanceof Result && !((Result<?>) result).isSuccess()) {
            return result;
        }

        // 构建事件参数
        Map<String, Object> data = new HashMap<>();
        data.put("eventCode", event.value());
        // 支持 SpEL 解析 bizId
        if (StringUtils.isNotBlank(event.bizId())) {
            data.put("bizId", parseSpel(event.bizId(), pjp));
        }
        // 从方法参数和方法返回值提取数据
        data.put("params", pjp.getArgs());
        data.put("result", result);
        data.put("operator", SecurityUtils.getUsername());

        // 事务提交后触发
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        eventBus.fire(event.value(), data);
                    }
                }
            );
        } else {
            eventBus.fire(event.value(), data);
        }

        return result;
    }
}
```

## 六、三个 Handler 实现

### LogHandler

```java
@Component
public class LogHandler implements EventHandler {
    
    @Autowired private SysOperLogMapper logMapper;

    @Override
    public void handle(String eventCode, Map<String, Object> data) {
        // 查配置：哪些事件需要记日志
        // 写到 sys_oper_log（已加 biz_type/biz_id/trace_id 的版本）
        SysOperLog log = new SysOperLog();
        log.setBizType(data.get("eventCode").toString());
        log.setBizId(String.valueOf(data.getOrDefault("bizId", "")));
        log.setOperator(String.valueOf(data.getOrDefault("operator", "")));
        log.setDetail(toJson(data));
        logMapper.insert(log);
    }
}
```

### TaskHandler

```java
@Component
public class TaskHandler implements EventHandler {
    
    @Autowired private SysEventKanbanMapper kanbanMapper;
    @Autowired private SysTaskMapper taskMapper;

    @Override
    public void handle(String eventCode, Map<String, Object> data) {
        // 查 sys_event_kanban 配置
        List<SysEventKanban> configs = kanbanMapper.findByEventCode(eventCode);
        for (SysEventKanban cfg : configs) {
            if (!cfg.getIsEnabled()) continue;
            
            SysTask task = new SysTask();
            task.setTaskCode(generateCode());
            task.setTaskType(cfg.getKanbanType());
            task.setTitle(replaceTemplate(cfg.getCardTitleTemplate(), data));
            task.setStatus("pending");
            task.setPriority("normal");
            task.setAssignRole(cfg.getAssignRoleId());
            task.setSourceEvent(eventCode);
            task.setSourceId(toLong(data.get("bizId")));
            taskMapper.insert(task);
        }
    }
}
```

### NotifHandler

```java
@Component
public class NotifHandler implements EventHandler {
    
    @Autowired private SysEventNotifMapper notifMapper;
    @Autowired private NotificationMapper notificationMapper;

    @Override
    public void handle(String eventCode, Map<String, Object> data) {
        // 查 sys_event_notification 配置
        List<SysEventNotification> configs = notifMapper.findByEventCode(eventCode);
        for (SysEventNotification cfg : configs) {
            // 按角色通知或按指定用户通知
            List<Long> userIds = resolveTargets(cfg);
            for (Long userId : userIds) {
                Notification notif = new Notification();
                notif.setTitle(replaceTemplate(cfg.getTitlePattern(), data));
                notif.setContent(replaceTemplate(cfg.getContentPattern(), data));
                notif.setNotificationType("SYSTEM");
                notif.setReceiverId(userId);
                notif.setEventCode(eventCode);
                notificationMapper.insert(notif);
            }
        }
    }
}
```

## 七、配置数据初始化

```sql
-- 1. 事件定义
INSERT INTO sys_event_config (event_code, event_name, event_module) VALUES
('product.approved', '产品审批通过', 'product'),
('bom.submitted', 'BOM提交审核', 'engineering'),
('bom.approved', 'BOM审核通过', 'engineering'),
('routing.submitted', '路线提交审核', 'engineering'),
('routing.approved', '路线审核通过', 'engineering'),
('order.submitted', '订单提交审核', 'sales'),
('order.approved', '订单审核通过', 'sales'),
('order.confirmed', '订单客户确认', 'sales'),
('order.start_production', '订单提交生产', 'sales');

-- 2. 看板任务映射
INSERT INTO sys_event_kanban (event_id, kanban_type, card_title_template, assign_role_id, is_enabled)
SELECT e.event_id, 'engineering', '产品【${productName}】需设计BOM和路线', r.role_id, 1
FROM sys_event_config e, sys_role r
WHERE e.event_code = 'product.approved' AND r.role_key = 'engineer';

-- 3. 通知映射
INSERT INTO sys_event_notification (event_id, notify_target, target_value, template_code)
SELECT e.event_id, 'ROLE', 'engineer', 'product.approved.template'
FROM sys_event_config e WHERE e.event_code = 'product.approved';

-- 4. 通知模板
INSERT INTO sys_notification_template (template_code, title_pattern, content_pattern) VALUES
('product.approved.template', '产品【${productName}】已审批通过',
 '产品【${productName}】已审批通过，请尽快完成BOM和工艺路线设计。');
```

## 八、实施步骤

| 步骤 | 内容 | 预估工时 |
|------|------|---------|
| 1 | 定义 @Event 注解 + AOP 切面 | 1天 |
| 2 | 实现 EventBus + 三个 Handler（Log/Task/Notif） | 1天 |
| 3 | 按优化方案改造 sys_oper_log 表结构 | 0.5天 |
| 4 | 新建统一 sys_task 表 | 0.5天 |
| 5 | 初始化事件配置数据（sys_event_config + 映射） | 0.5天 |
| 6 | 业务代码逐步加 @Event 注解替换手动调用 | 2天 |
| 7 | 前段看板适配 sys_task | 1天 |
| 8 | 旧表废弃 | 0.5天 |
