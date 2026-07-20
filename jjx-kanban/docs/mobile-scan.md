# 移动端与扫码方案

## 1. 移动端适配

### 1.1 场景分析

| 设备 | 使用者 | 使用场景 | 屏幕尺寸 |
|------|--------|---------|---------|
| PDA (手持终端) | 产线工人 | 扫码 → 确认工序完成 → 报工 | 4-5" |
| 平板 (iPad/Android) | 班组长 | 巡检时查看进度、处理异常 | 10-12" |
| 手机 (iPhone/Android) | 管理人员 | 远程查看车间状态 | 6-7" |

### 1.2 适配策略

三个设备共用一套代码库，通过响应式布局适配：

```scss
// 断点设计
$breakpoints: (
    'phone': 480px,    // 手机纵向
    'pda': 640px,      // PDA / 小屏手机
    'tablet': 1024px,  // 平板
    'desktop': 1280px, // 桌面
);

// 移动端默认隐藏部分信息，通过点击展开
.kanban-card {
    @media (max-width: 640px) {
        .card-field:nth-child(n+3) {
            display: none;  // 只显示标题 + 编号
        }
        .card-footer {
            flex-direction: column;
            gap: 4px;
        }
    }
}
```

### 1.3 触摸优化

| 优化项 | 实现方式 |
|--------|---------|
| 拖拽改为点击 | 移动端取消拖拽，改用「选择目标列」弹窗 |
| 操作按钮放大 | 触摸目标 ≥ 44×44px |
| 横向滚动提示 | 看板底部显示「← 滑动查看更多 →」 |
| 手势支持 | 左滑/右滑切换视图 |

---

## 2. 二维码扫码方案

### 2.1 应用场景

```
工单打印时生成二维码 ──── 贴上工单流转卡
         │
         ▼
工人/质检员用 PDA 扫描
         │
         ▼
系统自动识别工单号，跳转到对应卡片详情
         │
         ▼
工人确认工序完成，提交报工
```

### 2.2 二维码内容格式

```json
// 推荐格式：URL 参数（兼容性最好）
https://kanban.jjx.com/mobile/scan?wo=WO-202607-001

// 备用格式：纯工单号（适合离线扫码器）
WO-202607-001
```

### 2.3 生成方案

在工单打印/条码打印环节，使用 ZPL 指令或 API 生成二维码：

```typescript
// 前端生成（配合打印）
import QRCode from 'qrcode'

async function generateQR(workOrderId: string): Promise<string> {
    const url = `https://kanban.jjx.com/mobile/scan?wo=${workOrderId}`
    return await QRCode.toDataURL(url, { width: 200, margin: 2 })
}
```

### 2.4 扫码跳转逻辑

```typescript
// 扫码页面路由
// /mobile/scan?wo=WO-202607-001

import { useRouter, useRoute } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(async () => {
    const woId = route.query.wo as string
    if (woId) {
        // 1. 查找工单
        const res = await fetchCardDetail(woId)
        // 2. 从详情获取当前工序
        // 3. 跳转到看板页面并自动定位到该卡片
        router.push({
            path: '/kanban',
            query: { highlight: woId },
        })
    }
})
```

### 2.5 扫码设备支持

| 设备类型 | 输入方式 | 实现方式 |
|---------|---------|---------|
| PDA 内置扫码器 | 硬件触发 | 扫码后模拟键盘输入（默认支持） |
| USB 扫码枪 | 串口模拟 | 扫码后自动输入 URL + 回车 |
| 手机摄像头 | 软件扫码 | 使用 `html5-qrcode` 库 |
| 专用扫码终端 | 网络请求 | 设备直发 HTTP 请求到服务端 |

### 2.6 扫码流程

```
工人用 PDA 扫描工单二维码
        │
        ▼
PDA 浏览器打开 https://kanban.jjx.com/mobile/scan?wo=WO-001
        │
        ▼
移动端看板加载 → 高亮定位到 WO-001 卡片
        │
        ▼
显示工序详情 + 操作按钮：
  ┌────────────────────┐
  │  WO-001            │
  │  薄膜开关-MK12     │
  │  当前工序：冲切     │
  │                    │
  │  [✅ 确认完成]     │
  │  [⚠ 标记异常]     │
  │  [📋 报工]         │
  └────────────────────┘
        │
        ▼
工人点击「确认完成」→ 工序状态更新
```

---

## 3. 移动端路由设计

```typescript
// 移动端独立路由
const mobileRoutes = [
    {
        path: '/mobile',
        component: () => import('@/views/mobile/MobileLayout.vue'),
        children: [
            {
                path: 'scan',
                name: 'mobile-scan',
                component: () => import('@/views/mobile/ScanPage.vue'),
            },
            {
                path: 'card/:id',
                name: 'mobile-card-detail',
                component: () => import('@/views/mobile/CardDetail.vue'),
            },
            {
                path: 'report/:id',
                name: 'mobile-report',
                component: () => import('@/views/mobile/ReportForm.vue'),
            },
        ],
    },
]
```

---

## 4. 实施建议

| 阶段 | 内容 | 前置条件 |
|------|------|---------|
| 1 | 移动端基础布局（PDA 优先） | 后端 API 就绪 |
| 2 | 扫码跳转 + 卡片详情页 | 纸质工单打码规范确定 |
| 3 | 确认完成 / 报工表单 | 报工数据模型就绪 |
| 4 | 手机 / 平板适配 | 大屏模式跑通后 |
