# 前端 v-hasPermi 审计报告

> 生成时间: 2026-07-23 19:35

## 前端使用 vs 数据库匹配

| 前端权限 | 数据库 | 问题 |
|---------|--------|------|
| `inventory:warehouse:remove` | ❌ | 后端用 `delete`，前端写 `remove` |
| `system:dept:add/edit/remove` | ❌ | 完全缺失 |
| `system:dict:add/delete/edit/query` | ❌ | 完全缺失 |
| `system:log:exception/login/operation` | ❌ | 数据库为 `log:exception:view` 等，命名不同 |
| `system:menu:add/edit/remove` | ❌ | 完全缺失 |
| `system:role:edit/remove` | ❌ | 完全缺失 |
| `system:user:add` | ✅ | |
| `system:user:edit/remove/reset` | ❌ | `reset` 后端是 `resetPwd`，命名不统一 |

## 结论

前端用了 20 个 v-hasPermi 检查，但匹配的只有 1 个。同上，admin 有 `*:*:*` 暂时没报错。
统一权限命名后再同步修复。
