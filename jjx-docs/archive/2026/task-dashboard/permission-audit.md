# 权限普查报告

> 生成时间: 2026-07-23 19:30
> 范围: 非 Sales 模块（Sales 已对齐）

## 统计

| 项目 | 数量 |
|------|------|
| 后端注解总数 | 116 |
| 数据库已有 | 79 |
| 缺失数量 | 100+ |
| 严重程度 | ⚠️ 低（admin 有 `*:*:*` 通配符）|

## 缺失最多的模块

### product
```
product:bom:add/approve/delete/edit/reject
product:delete, product:index:edit, product:product:edit/obsolete
product:status:approve/reject/release/submit
```

### inventory
```
inventory:alert:edit
inventory:category:add/edit/list/query/remove
inventory:inbound:add/approve
inventory:material:add/delete/edit
inventory:outbound:add/approve/edit
inventory:stock:import
inventory:stocktake:add/approve/edit
inventory:storage-location:add/delete/edit/view
inventory:transaction:view
inventory:transfer:add/approve/edit
inventory:warehouse:add/delete/edit
```

### production
```
production:operation-execution:add/delete/edit/export/import/view
production:operation-record:add/delete/edit/export/import/view
production:order:add/delete/edit/export
```

### purchase
```
purchase:invoice:add/delete/edit/export/import/view
purchase:order:add/approve/edit/export
purchase:payment:add/approve/delete/edit/export/import/view
purchase:receipt:add/delete/edit/export/import/view
purchase:supplier:add/delete/edit/export/import
```

### system
```
system:dept:add/delete/edit
system:dict:add/delete/edit/list/query
system:menu:add/delete/edit
system:role:add/delete/edit
system:user:delete/edit/resetPwd
```

## 建议

优先级较低，因为 admin 有 `*:*:*`。等 Sales 模块权限规范化稳定后，按同样标准刷一遍（新增 missing 权限到 sys_menu，不做注解改动）。
