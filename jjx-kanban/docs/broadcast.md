# 语音播报系统

## 1. 设计原则

> **安静是默认状态，播报只在「有事」时触发。**

## 2. 触发机制

### 2.1 触发类型

| 触发类型 | 说明 | 示例 |
|---------|------|------|
| ⏰ 定时触发 | 固定时间点自动播报 | 每日 08:00 逾期播报 |
| 🚨 事件触发 | 特定数据变化时触发 | 卡片被标为 `blocked` |
| 📊 阈值触发 | 数据超过阈值时触发 | 某工序积压 > 5 张 |

### 2.2 定时触发

```typescript
// 前端方案（简单）：定时轮询
const TIMES = [
    { hour: 8, minute: 0,  message: '晨会逾期播报' },
    { hour: 14, minute: 0, message: '下午开工提醒' },
    { hour: 17, minute: 30, message: '下班前异常播报' },
]

function checkSchedule() {
    const now = new Date()
    TIMES.forEach(t => {
        if (now.getHours() === t.hour && now.getMinutes() === t.minute) {
            triggerScheduledBroadcast()
        }
    })
}
```

### 2.3 播报内容模板

| 场景 | 播报内容 |
|------|---------|
| 晨会逾期 | "请注意，当前有 3 张工单已逾期。MK12 逾期 2 天，BL06 逾期 1 天……" |
| 阻塞通知 | "警告：WO-005 在印刷工序被标记为阻塞，原因：待料。" |
| 紧急任务 | "紧急任务：MK12 返工单已创建，请安排处理。" |
| 积压提醒 | "测试工序当前有 8 张工单积压，请注意调配人手。" |
| 超时停留 | "MK12 薄膜开关在贴合工序已停留 6 小时，请注意。" |

### 2.4 优先级队列

```
队列管理：
  ┌──────────────────────────────┐
  │  Broadcast Queue             │
  ├──────────────────────────────┤
  │  P0 (urgent):  ⚠ 阻塞/逾期   │ ← 立即播放，打断当前播报
  │  P1 (warning): 📊 积压/超时  │ ← 排入队列，等当前播报完
  │  P2 (info):   📋 晨会汇总   │ ← 排入队列
  └──────────────────────────────┘
```

### 2.5 防重复机制

```typescript
// 同一内容在 5 分钟内不重复播报
const recentMessages = new Map<string, number>()  // message → timestamp

function shouldBroadcast(message: string): boolean {
    const lastTime = recentMessages.get(message)
    const now = Date.now()
    if (lastTime && now - lastTime < 300000) { // 5分钟
        return false
    }
    recentMessages.set(message, now)
    return true
}
```

---

## 3. 配置面板

用户可控制的配置项：

| 配置项 | 说明 | 默认 |
|-------|------|------|
| 开关 | 启用/禁用语播报 | 启用 |
| 音量 | 1-100 | 80 |
| 语速 | 0.5-2.0 | 1.0 |
| 定时播报 | 开关晨会/整点播报 | 启用 |
| 阻塞播报 | 开关阻塞通知 | 启用 |
| 积压阈值 | 超过此数量才播（0=不播） | 5 |
| 静音时段 | 此时间段不播报 | 22:00-07:00 |

---

## 4. 前端架构

```typescript
// broadcast.ts — 详见 utils/broadcast.ts
export class BroadcastService {
    private enabled: boolean
    private queue: BroadcastMessage[]
    private speaking: boolean
    private recentMessages: Map<string, number>
    private config: BroadcastConfig
    private scheduleTimer?: number

    init(): void
    setEnabled(on: boolean): void
    say(text: string, level?: 'info' | 'warning' | 'urgent'): void
    configure(config: Partial<BroadcastConfig>): void
    
    // 播报场景
    announceOverdueList(cards: BoardCard[]): void
    announceCardBlocked(card: BoardCard): void
    announceEmergencyTask(card: BoardCard): void
    announceProcessCongestion(columnLabel: string, count: number): void
    
    private startScheduleCheck(): void
    private processQueue(): void
}
```

---

## 5. 实施阶段

| 阶段 | 内容 | 复杂度 |
|------|------|--------|
| 1 | 基础 TTS + 手动触发测试 | ★☆☆☆☆ |
| 2 | 阻塞/创建时自动播报 | ★★☆☆☆ |
| 3 | 定时播报（晨会/整点） | ★★★☆☆ |
| 4 | 积压/超时播报 + 配置面板 | ★★★★☆ |
| 5 | 多端同步（WebSocket 推送） | ★★★★★ |
