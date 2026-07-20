# 权限与角色模型

## 1. RBAC 模型

采用基于角色的访问控制（Role-Based Access Control），将权限赋予角色，角色赋予用户。

```
用户 ──► 角色 ──► 权限
                    │
                    ├── 资源 (Resource)  如：卡片、视图、模板
                    └── 操作 (Action)    如：查看、创建、移动、删除
```

---

## 2. 角色定义

| 角色 | 角色标识 | 人员 | 职责 |
|------|---------|------|------|
| 操作工 | `operator` | 产线工人 | 查看负责工单、拖拽更新工序、标记完成、报工 |
| 班组长 | `team_lead` | 班长/组长 | 查看全组、插单、改优先级、标阻塞、调整负责人 |
| 质检员 | `inspector` | 品质人员 | 查看全部、标记不良/退返、查看不良统计 |
| 计划员 | `planner` | PMC/计划 | 建工单、调整工序模板、查看整体进度、排程 |
| 管理员 | `admin` | IT/系统管理 | 全部权限、系统配置、用户管理 |
| 访客 | `guest` | 访客/参观 | 只读查看生产工单（大屏模式） |

---

## 3. 权限矩阵

### 3.1 卡片操作

| 权限标识 | 说明 | admin | planner | team_lead | inspector | operator | guest |
|---------|------|-------|---------|-----------|-----------|----------|-------|
| `card:view` | 查看卡片 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `card:view_all` | 查看全部卡片 | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `card:create` | 新建卡片 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `card:move` | 拖拽移动 | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| `card:move_all` | 移动任意卡片 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `card:edit` | 编辑字段 | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `card:delete` | 删除卡片 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `card:report` | 报工记录 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |

### 3.2 模板/视图操作

| 权限标识 | 说明 | admin | planner | team_lead | inspector | operator | guest |
|---------|------|-------|---------|-----------|-----------|----------|-------|
| `template:view` | 查看模板 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `template:edit` | 编辑工序模板 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `view:create` | 自定义视图 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### 3.3 系统管理

| 权限标识 | 说明 | admin | planner | team_lead | inspector | operator | guest |
|---------|------|-------|---------|-----------|-----------|----------|-------|
| `user:manage` | 用户管理 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `log:view` | 操作日志 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `report:view` | 查看报表 | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `broadcast:config` | 播报配置 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## 4. 数据权限

### 4.1 行级权限

不同角色看到的数据范围不同：

| 角色 | 看板数据范围 |
|------|------------|
| 操作工 | 仅自己的工单 (`assignee = 当前用户`) |
| 班组长 | 本组工单（通过部门/分组关联） |
| 质检员 | 全部工序，不限工单 |
| 计划员 | 全部工单 |
| 管理员 | 全部数据 |

### 4.2 列级权限

特定角色不能看到某些列（视图定义时配置可见角色）：

```
视图配置示例：
{
    "id": "finance_column",
    "label": "成本信息",
    "visibleRoles": ["admin", "planner"]  // 仅管理员和计划员可见
}
```

---

## 5. 权限校验流程

```
用户请求
  │
  ▼
JWT Token 解析 → 获取 userId + role
  │
  ▼
权限拦截器 (PermissionInterceptor)
  ├── 1. 获取请求的资源 + 操作
  ├── 2. 查询角色对应的权限列表
  ├── 3. 匹配是否拥有该权限
  │     ├── 有 → 放行
  │     └── 无 → 返回 403
  │
  ▼
数据权限过滤
  ├── operator 角色：SQL 自动追加 AND assignee = :userId
  └── 其他角色：正常查询
```

---

## 6. 权限相关数据库表

### sys_user — 用户表（见 data-model.md）

### sys_role_permission — 角色权限表（见 data-model.md）

### 权限校验 SQL 示例

```sql
-- 查询用户是否拥有某权限
SELECT 1 FROM sys_user u
JOIN sys_role_permission rp ON u.role = rp.role
WHERE u.id = ? AND rp.permission = 'card:move'
LIMIT 1
```

---

## 7. 前端权限控制

```typescript
// 权限 Hook
export function usePermission() {
    const user = useUserStore()
    
    const hasPermission = (perm: string): boolean => {
        return user.permissions.includes(perm)
    }

    const canMoveCard = (card: BoardCard): boolean => {
        if (hasPermission('card:move_all')) return true
        if (hasPermission('card:move') && card.assignee === user.displayName) return true
        return false
    }

    return { hasPermission, canMoveCard }
}
```

### 组件级控制

```vue
<!-- 仅管理员可见 -->
<el-button
    v-if="hasPermission('template:edit')"
    @click="editTemplate"
>
    编辑模板
</el-button>

<!-- 操作工只能拖自己的卡 -->
<div v-if="canMoveCard(card)">
    <!-- 可拖拽的卡片包装 -->
</div>
```
