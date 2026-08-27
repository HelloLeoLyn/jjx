# 路由标签页卡顿/刷新问题分析报告

> 分析日期：2026-07-22
> 优先级：P2（体验优化）
> 状态：待处理

---

## 问题描述

点击新路由标签（未访问过的页面）时：
1. **卡顿** — 明显延迟后才能进入页面
2. **页面刷新** — 当前页面像被替换刷新
3. **旧标签关闭** — 当前标签在过程中被关闭
4. 已点击过的路由标签一切正常

---

## 根因分析

### 核心问题：路由初始化时机错误

当前代码将**动态路由初始化**放在了路由守卫（`permission.ts`）中按需执行，而不是在应用启动/登录时一次性完成。

**错误流程：**

```
用户点击菜单
  → router.beforeEach（permission.ts）
  → checkIsInitialized() 返回 false
  → initPermissionSystem()
     ├─ userStore.getUserInfo()  ← API 调用
     ├─ permissionStore.generateRoutes()
     │   └─ menuApi.getRouters() ← API 调用
     ├─ router.addRoute() 逐个注册
  → next({ ...to, replace: true })  ← 页面替换
```

**成熟做法（RuoYi / vben-admin）：**

```
应用启动/登录成功
  → 一次性获取路由 + 权限 + 用户信息
  → 批量注册动态路由
  → 进入首页

后续每次路由跳转
  → 守卫只做 token / 权限校验
  → 不调任何 API，不操作路由表
```

---

### 问题清单（按严重程度）

#### P0 — 必修

| # | 问题 | 文件 | 具体位置 |
|---|------|------|---------|
| 1 | **`next({...to, replace: true})` 替换当前页面** | `src/permission.ts` | L120 |
| 2 | **路由初始化在守卫里按需执行（含 API 调用）** | `src/permission.ts` | L24-90 |
| 3 | **permissionStore.reset() 清空 loaded 状态导致重复初始化** | `src/store/modules/permission.ts` | L76-78 |

#### P1 — 建议修

| # | 问题 | 文件 | 具体位置 |
|---|------|------|---------|
| 4 | **dashboard/index.vue 没有 component name** | `src/views/dashboard/index.vue` | 整个文件 |
| 5 | `AppMain.vue` 的 `<transition mode="out-in">` 制造空白期 | `src/layout/components/AppMain.vue` | L5 |
| 6 | **两个 `router.onError` 重复注册，chunk 失败即整页 reload** | `src/router/index.ts` + `src/permission.ts` | 各一个 |

#### P2 — 建议优化

| # | 问题 | 说明 |
|---|------|------|
| 7 | `import.meta.glob` 懒加载首次 chunk 请求慢 | Vite 默认行为 |
| 8 | `vite.config.ts` 中 WSL2 polling 影响 HMR 但不应影响运行时 | 开发环境问题 |

---

## 🔗 关键文件索引

| 文件 | 作用 | 状态 |
|------|------|------|
| `src/permission.ts` | 路由守卫 + 权限初始化 | ⚠️ 主要问题所在 |
| `src/router/index.ts` | 路由配置 + 进度条 | ✅ 干净 |
| `src/store/modules/permission.ts` | 权限状态管理（当前使用版） | ⚠️ 重置逻辑有问题 |
| `src/store/modules/user.ts` | 用户状态管理（当前使用版） | ⚠️ 登出时重置权限 |
| `src/layout/components/AppMain.vue` | 路由出口 + keep-alive | ⚠️ transition 模式 + key |
| `src/layout/components/TagsView.vue` | 标签页组件 | ✅ 问题不大 |
| `src/store/modules/tagsView.ts` | 标签页状态 | ✅ 逻辑正常 |
| `src/utils/routeHelper.ts` | 后端路由→Vue Router 转换 | ✅ 逻辑正常 |
| `src/views/dashboard/index.vue` | Dashboard 组件 | ⚠️ 缺 component name |
| `src/store/modules/permissionBack.ts` | 旧版权限 store（RuoYi 原始版） | ℹ️ 可参考 |
| `src/store/modules/userback.ts` | 旧版用户 store（RuoYi 原始版） | ℹ️ 可参考 |
| `src/router/index.back` | 旧版路由（RuoYi 原始版） | ℹ️ 可参考 |

---

## 成熟框架参考

| 维度 | RuoYi 原版 | vben-admin | **当前代码** |
|------|-----------|-----------|-------------|
| 路由初始化时机 | 登录后 + 守卫兜底（有缓存） | App.vue setup 里一次性 | 守卫里按需（无缓存） |
| 守卫是否调 API | 否（已缓存） | 否（已就绪） | **是，每次都调** |
| `replace: true` | 仅兜底时用 | 不用 | **必用** |
| keep-alive 匹配 | route meta 控制 `noCache` | 组件名声明 | **Dashboard 无名，可能不匹配** |
| 组件 name 声明 | 全有 | 全有 | Dashboard 缺 |

---

## 修复原则

1. **参考 RuoYi 的路由初始化流程架构**，但用 TypeScript + Composition API 重写
2. **路由守卫只做校验，不做初始化** — 这是与成熟框架对齐的关键
3. **修复 `replace: true`**，改为正常 push
4. **所有视图组件补充 component name**，确保 keep-alive 正确缓存
5. **合并重复的 `router.onError`** 到一处
6. **去掉或优化 `<transition mode="out-in">`**，减少空白感知

---

## 后续步骤

1. 设计重构方案（参考 RuoYi 路由流程 + vben 的 cache 策略）
2. 代码修改
3. 测试验证（首次访问、切换标签、刷新页面、登出再登录）

> 等待排期处理。
