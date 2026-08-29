# 扫码B 移动端H5操作页 — 实施方案（页面结构 + 接口清单）

> 日期：2026-08-30 ｜ 依据：scan-pda-new-model-20260830.md（新模型适配稿）+ 后端接口实测
> 状态：已确认（2026-08-30 02:47 Leo 拍板），实施中

---

## 一、目标

车间工人用 **手机（PDA）/ 扫码枪** 完成最小闭环：

```
扫码/输单号 → 定位工单 → 看我的任务 → 开始工序 → 报工（数量/不良/工时）→ 结果确认
```

纯前端新增，后端接口**全部复用现有**，零新增接口（除非权限点不满足，见 §6）。

## 二、页面结构（4 个页面，新增 /m/ 移动路由）

| 路由 | 页面 | 职责 |
|---|---|---|
| `/m/scan` | 扫码入口页 | 扫码枪 / 手动输入工单号；未登录先跳登录 |
| `/m/login` | 登录页 | 复用现有账号体系（POST /sessions/auth），token 存 localStorage |
| `/m/order` | 工单任务页 | 按 orderNo 定位 → 展示该工单**我的工序任务**（execution 聚合 + 数量进度） |
| `/m/report` | 报工页 | 报工表单（合格/不良/工时/不良原因/备注）→ 提交 → 成功反馈；我的报工记录 |

页面关系（交互流程）：

```
/m/scan ──扫码/输入──▶ /m/order?orderNo=WPOxxx
                        │
                        ├─ 我的工序列表（execution + task 聚合）
                        │    每项显示：工序名 / 状态 / 已报 / 剩余
                        │
                        ├─ 点「开始」→ PUT execution/start（如未开始）
                        ├─ 点「报工」→ /m/report?executionId=&taskId=
                        │      提交 → POST work-report → 成功 → 返回列表刷新
                        └─ 点「明细」→ 该 task 完成明细（可选，二期）
```

移动端布局要求：大按钮、大字号（车间戴手套/强光），单列列表，底部提交栏。

## 三、接口清单（全部复用现有后端）

### 登录
| 接口 | 用途 | 权限 |
|---|---|---|
| `POST /sessions/auth` | 账号密码登录，取 `data.token` | 无（公开） |

> 后续所有请求带 header：`token: <token>`（与 PC 端一致）。

### 扫码定位（orderNo → orderId → executions）
| 接口 | 用途 | 权限 |
|---|---|---|
| `GET /production/order/code/{orderCode}` | 按工单号查工单，拿 `orderId` | 待确认（见 §6） |
| `GET /production/operation-execution/order/{orderId}` | 该工单全部工序执行列表 | `production:operation-execution:view` |

> 备选：若 `code/` 接口权限不满足，可改用 `GET /production/order/page` + orderNo 过滤（PC 列表页同款）。

### 我的任务（报工入口）
| 接口 | 用途 | 权限 |
|---|---|---|
| `GET /production/tasks/mine?executionId=` | 我的任务列表（assignee_id=当前登录人），返回 TaskTreeRowVO：taskId/taskNo/orderNo/processName/数量/状态/allowedActions | `production:work-report:add` |

> `allowedActions` 直接决定按钮显隐（开始/报工/退回…），前端不硬编码。

### 工序执行动作
| 接口 | 用途 | 权限 |
|---|---|---|
| `PUT /production/operation-execution/{id}/start` | 开始工序 | `production:operation-execution:edit` |
| `PUT /production/operation-execution/{id}/pause` | 暂停 | 同上 |
| `PUT /production/operation-execution/{id}/complete` | 工序完工 | 同上 |

### 报工
| 接口 | 用途 | 权限 |
|---|---|---|
| `POST /production/work-report` | 提交报工（WorkReportSubmitDTO） | `production:work-report:add` |
| `GET /production/work-report/execution/{executionId}` | 该工序报工历史（含 CANCELLED/REJECTED 标记） | `production:operation-execution:view` |
| `GET /production/work-report/mine` | 我的报工记录（分页） | `production:work-report:view` |
| `POST /production/work-report/{id}/cancel` | 撤销 PENDING 报工 | `production:work-report:cancel` |
| `GET /production/tasks/{taskId}/completion-details` | 完成明细（已批准报工合计） | `production:task:view` |

### 报工提交参数（WorkReportSubmitDTO，实测）
```json
{
  "executionId": 123,          // 必填，工序执行ID
  "taskId": 456,               // 必填，生产任务ID（mine 接口返回）
  "qualifiedQuantity": 100,    // 本次合格数量
  "defectiveQuantity": 2,      // 本次不良数量（>0 时不良原因必填）
  "laborHours": 1.5,           // 人工工时（可空=0）
  "machineHours": 1.5,         // 机器工时（可空=0）
  "defectReason": "油墨偏移",   // 不良原因
  "remark": "第一版丝印"
}
```
后端自动生成：orderId/orderNo/reporterName/equipmentName/reportStatus/reportTime；数量上限 = Task.remaining 自动校验。

## 四、前端实现要点

1. **路由**：现有 Vue3 工程新增 `/m/` 移动路由组，独立布局（无侧边栏/顶栏，全屏移动视图），组件放 `views/mobile/`。
2. **扫码**：复用 `useScanner.ts`（扫码枪=键盘缓冲）零成本；手机摄像头扫码需要 HTTPS（`getUserMedia`），**一期先支持扫码枪 + 手动输入**，摄像头扫码留到内网 HTTPS 部署后（方案稿步骤4）。
3. **登录态**：token 存 localStorage，`/m/` 路由前置守卫：无 token → 跳 `/m/login`；401 → 清 token 回登录页。
4. **错误处理**：后端返回 `msg` 直接 toast（如"超过剩余数量"），不包一层。
5. **权限按钮**：以 `allowedActions` + `v-hasPermi` 双重控制，工人看不到无权按钮。

## 五、实施步骤（拆分，逐步验收）

| 步 | 内容 | 验收 |
|---|---|---|
| 1 | `/m/` 移动布局 + 登录页 + 路由守卫 | 手机浏览器可登录 |
| 2 | `/m/scan` 扫码入口（扫码枪/手输）→ `/m/order` 定位展示我的任务 | 扫纸质工单能定位到我的工序 |
| 3 | 报工页：表单 + 提交 + 成功反馈 + 列表刷新 | 报工后 PC 端能看到 PENDING 记录 |
| 4 | 开始/暂停/完工按钮 + 我的报工记录 + 撤销 | 完整闭环 |
| 5 | 设备码校验（扫码C）、内网 HTTPS 摄像头扫码 | 后续再排 |

## 六、决策落定（2026-08-30 Leo 拍板）

1. ✅ **工人角色权限够**：现有角色已具备所需权限，无需新增（view + work-report:add + operation-execution:edit）
2. ✅ **每人一个系统账号**：工人直接账号登录，**扫码D 授权码方案砍掉不做**
3. ✅ **开始进一期，完工不进**：一期 = 登录 → 扫码定位 → 开始 → 报工 → 我的报工记录；
   完工确认（task/complete，需 task:assign）留在 PC 管理端，符合"报工≠完工"职责划分；暂停二期

## 七、与既有方案稿的关系

- 本文件是 scan-pda-new-model-20260830.md 的**扫码B 具体执行细化**（页面结构+接口实测清单），不冲突；
- 扫码C（设备码校验）/ 扫码D（授权码）按既有方案稿 §5 步骤 2~5 后续排，本期不做。
