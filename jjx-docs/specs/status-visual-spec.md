# 🎨 单据状态可视化规范

> 版本: v1.0 | 最后更新: 2026-08-01
> 适用范围: 所有列表/详情/看板的状态展示
> 目的: 防止状态展示漂移、用户看不到流转变化

---

## 一、核心原则

1. **状态一律用枚举**：列表/详情状态展示走 enums/ 目录（createEnum），禁止硬编码映射
2. **统一色板**：同语义同色，全系统一致
3. **关键节点列表可见**：状态变化必须在列表行直接可见（不点详情不知道 = 失败）
4. **看板与列表一致**：看板卡片状态色 = 枚举定义色

---

## 二、统一色板

| 状态语义 | 颜色 | Element 类型 | 示例 |
|---|---|---|---|
| 草稿/待处理 | 灰 | info | 订单草稿、工单草稿 |
| 进行中 | 蓝 | primary | 打样中、生产中、审核中 |
| 待办提醒 | 橙 | warning | 待送样、待审核 |
| 成功/完成 | 绿 | success | 已确认、已完成、已批准 |
| 拒绝/取消/作废 | 红 | danger | 已驳回、已取消、已作废 |
| 终态展示 | 绿/灰 | success/info | 已转量产、已关闭 |

## 三、枚举定义规范（前端）

```ts
// enums/sales/SampleEnum.ts 示例
export const SampleOrderStatusEnum = createEnum([
  { value: 1, label: '创建', color: 'info' },
  { value: 2, label: '待审核', color: 'warning' },
  { value: 3, label: '工程打样中', color: 'primary' },
  { value: 4, label: '待送样', color: 'warning' },
  { value: 5, label: '已送样', color: 'primary' },
  { value: 6, label: '已确认', color: 'success' },
  { value: 7, label: '已转量产', color: 'success' },
  { value: 8, label: '已关闭', color: 'info' },
  { value: 9, label: '客户退回', color: 'danger' },
  { value: 10, label: '已作废', color: 'danger' },
])
```

## 四、列表关键节点必显列

| 单据 | 必显列 | 状态 |
|---|---|---|
| 样品单 | 当前工序、接单人 | ✅ (DEV-450/451) |
| 报价单 | 状态标签 | ✅ |
| 生产工单 | 状态、当前工序 | ✅ |
| 采购单 | 审批状态、收货状态 | ✅ |
| 入库/出库单 | 单据状态 | ✅ |

## 五、看板卡片

- 卡片状态色 = 枚举色
- 卡片显示：标题 + 优先级 + 截止日 + 状态标签
- 状态变化实时刷新（websocket/轮询）

## 六、已知问题跟踪

- [ ] TraceTimeline 对未知状态显示"未知"（不崩）✅
- [ ] 库存单据前端枚举待统一（入库/出库/调拨/盘点状态）
- [ ] BOM/工艺路线 approve_status 前端枚举待建
- [ ] 生产工单 OrderStatusEnum 前端色板需核对
