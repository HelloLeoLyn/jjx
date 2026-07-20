# 大屏模式设计

## 1. 概述

大屏模式（Big Screen Mode / Kiosk Mode）是为车间电视/投影场景设计的全屏展示方案。车间管理人员和工人可远距离查看生产状态，无需操作交互。

## 2. 场景分析

| 场景 | 位置 | 显示内容 | 更新频率 |
|------|------|---------|---------|
| 车间入口 | 门厅/走廊 55" 电视 | 整体生产概览 + 异常警示 | 60s |
| 产线看板 | 产线工位 32" 屏 | 本产线工单工序状态 | 30s |
| 班组长室 | 办公室 24" 显示器 | 全组任务 + 报工统计 | 实时 |
| 管理区 | 大会议室 86" 电视 | OEE + 质量统计 + 异常汇总 | 5min |

## 3. 界面布局

### 3.1 标准大屏布局

```
┌─────────────────────────────────────────────────────────────────┐
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  标题栏：JJX 生产看板 · 2026-07-18 14:35 · 周一           │   │
│  │  [ 正常 ● 注意 ● 紧急 ● ]                                │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ 总工单     │  │ 进行中   │  │ 今日完工  │  │ 逾期     │        │
│  │   45      │  │   32     │  │    5     │  │   3      │        │
│  │   total   │  │   ing    │  │  today   │  │ overdue  │        │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │
│                                                                 │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌──────┐  │
│  │ 印刷   │ │ 冲切   │ │ 贴合   │ │ SMT   │ │ 装配   │ │ 测试 │  │
│  │  5张   │ │  8张   │ │  4张   │ │  3张  │ │  6张  │ │  2张 │  │
│  └───────┘ └───────┘ └───────┘ └───────┘ └───────┘ └──────┘  │
│  ┌───────┐ ┌─────────────────────────────────────────────────┐ │
│  │ 包装   │ │  ⚠ 异常工单：                                   │ │
│  │  4张   │ │  - MK12 逾期 3 天，阻塞在贴合                    │ │
│  │        │ │  - BL06 待料，阻塞在 SMT                         │ │
│  └───────┘ │  - BH08 品质异常，退回印刷                        │ │
│            └─────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  底部状态栏：数据更新于 14:32 · 系统正常 · 数据源: ERP   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 布局要点

- **留白充足**：大屏间距需比普通屏幕大 50%
- **字体巨大**：基础字号 ≥ 24px，标题 ≥ 40px，数字 ≥ 56px
- **高对比度**：深色背景 + 亮色文字，远距离可辨
- **自动轮播**：多视图切换时平滑过渡

---

## 4. 大屏接口设计

### 4.1 数据接口

```
GET /api/v1/board/big-screen?templateCode=production
```

响应格式（精简版，不含富文本、备注等）：

```json
{
    "code": 0,
    "data": {
        "summary": {
            "totalOrders": 45,
            "inProgress": 32,
            "completedToday": 5,
            "overdue": 3,
            "blocked": 4,
            "avgProcessTime": "2.5h"
        },
        "statusLevel": "warning",       // normal / warning / danger
        "columns": [
            {
                "label": "印刷",
                "count": 5,
                "cards": [
                    {
                        "id": "WO-001",
                        "title": "MK12",
                        "priority": "urgent"
                    }
                ]
            }
        ],
        "alerts": [
            { "level": "danger", "text": "MK12 逾期3天，阻塞在贴合" }
        ],
        "lastSyncTime": "2026-07-18 14:32:00"
    }
}
```

### 4.2 轮播控制

| 字段 | 类型 | 说明 |
|------|------|------|
| `carouselViews` | string[] | 轮播视图列表 |
| `carouselInterval` | int | 切换间隔（秒） |

---

## 5. 前端实现

### 5.1 组件结构

```
KanbanBigScreen.vue          ← 全屏容器
├── BigScreenHeader.vue      ← 标题 + 时间 + 状态灯
├── BigScreenSummary.vue     ← 4 个关键指标卡片
├── BigScreenBoard.vue       ← 看板列（大字版）
│   └── BigScreenColumn.vue  ← 列（无拖拽，只展示）
├── BigScreenAlert.vue       ← 异常工单列表
└── BigScreenFooter.vue      ← 底部状态栏
```

### 5.2 关键技术点

**全屏 API：**
```typescript
async function enterFullscreen() {
    const el = document.documentElement
    if (el.requestFullscreen) {
        await el.requestFullscreen()
    }
}
```

**自动轮播：**
```typescript
// 多视图间定期切换
let carouselTimer: number

function startCarousel() {
    carouselTimer = window.setInterval(() => {
        const views = ['process', 'deadline', 'priority']
        currentViewIndex = (currentViewIndex + 1) % views.length
        loadView(views[currentViewIndex])
    }, 30000) // 30s 切换一次
}
```

**自动刷新：**
```typescript
// 定期拉取最新数据，保持大屏实时
const refreshTimer = setInterval(() => {
    fetchBigScreenData()
}, 60000) // 60s 刷新一次
```

### 5.3 CSS 适配

```scss
// 大屏基础字号放大
.big-screen {
    --fs-sm: 18px;
    --fs-md: 24px;
    --fs-lg: 40px;
    --fs-xl: 56px;
    --fs-number: 72px;

    background: #1a1a2e;  // 深色背景
    color: #e0e0e0;

    // 状态氛围色
    &.status-normal { --accent: #67c23a; }
    &.status-warning { --accent: #e6a23c; }
    &.status-danger { --accent: #f56c6c; }
}
```

### 5.4 路由

```typescript
// 独立路由
const routes = [
    {
        path: '/big-screen',
        name: 'big-screen',
        component: () => import('@/views/KanbanBigScreen.vue'),
    },
]
```

---

## 6. 实施建议

| 阶段 | 内容 | 价值 |
|------|------|------|
| 1 | 大屏 HTML 框架 + 全屏 API + 自动刷新 | 先跑起来，投到电视上 |
| 2 | 四个摘要卡片（总/进行中/完工/逾期） | 管理层最关心的数据 |
| 3 | 看板列大字版 + 异常工单列表 | 工人可看清各工序积压 |
| 4 | 轮播多视图 + 氛围色 | 提升视觉效果 |
| 5 | 底部状态栏 + 数据更新时间 | 增强可信度 |
