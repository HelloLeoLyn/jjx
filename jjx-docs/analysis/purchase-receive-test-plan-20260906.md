# 采购模块测试方案：覆盖 2026-09-06 收货 400 Bug 回归

> 日期：2026-09-06
> 关联修复：commit b62a308（OrderReceiveDialog 去掉 inspectionResult/inspectionRemark 提交）
> 适用范围：采购收货链路 + 1505 检验迁移链路防回归

---

## 一、背景与目标

### Bug 回顾
- 现象：采购收货弹窗提交报 400，错误含 `Unrecognized field "inspectionResult" ... not marked as ignorable`
- 根因链：任务 1505 后端把收货 DTO 检验字段删掉（收货去检验，检验移到入库确认）→ 前端 OrderReceiveDialog 未同步仍提交旧字段 → WebConfig 自建 ObjectMapper（`new ObjectMapper()`，Jackson 原生默认开启 `FAIL_ON_UNKNOWN_PROPERTIES`）拒未知字段 → 400
- 修复：前端删除「检验结果」列、「检验备注」输入及提交映射（30 行）

### 测试目标
1. 回归验证收货主链路修复生效（弹窗无检验控件 + 提交成功 + 数据落库）
2. 锁定「前端提交字段 = 后端 DTO 字段」契约，防同类漂移复发
3. 验证 1505 迁移后的正向链路（收货 → 入库确认内嵌来料检验）不受收货去检验误伤
4. 发现即留证（截图/接口返回/DB 前后值）

---

## 二、被测对象与环境

| 项 | 值 |
|---|---|
| 后端 | `POST /purchase/order/{orderId}/receive`，DTO 仅 `items[{itemId, receivedQuantity}]` |
| 前端 | 采购管理 → 采购订单 → 收货弹窗 `OrderReceiveDialog.vue` |
| 被测单 | `order_id=1`（PO-1788614769255，罗杰斯泡棉，已审核 approval_status=3），明细 item_id=1/2 各订 2000 已收 100，剩 1900 |
| 服务 | 后端 :8080、前端 :3000（已起） |
| 账号 | admin / 123456（`POST /sessions/auth` 取 token，请求头 `token:`） |
| 测试基建 | `/mnt/d/openclaw-workspace/tests/`（playwright.config.js + 现有 spec），TC 格式按 `docs/test/测试标准.md` |

---

## 三、测试分层与用例

### A. 接口层（purchase-receive-api 脚本，curl/python）

| 编号 | 用例 | 断言 |
|---|---|---|
| A1 | 合法最小载荷：单明细收 10 | `code:200`，无业务错误 |
| A2 | 旧字段载荷：payload 带 `inspectionResult/inspectionRemark` | 被拒（400 或业务错误码）——锁定当前严格契约 |
| A3 | 空 `items` | 校验失败（`收货明细不能为空`） |
| A4 | `receivedQuantity <= 0` | 校验失败（`收货数量必须大于0`） |
| A5 | 超剩余量收货（如收 99999） | 探明后端行为（部分收/拦截）→ 按现状断言 |
| A6 | 不存在的 orderId / itemId | 404 或明确业务错误，非 500 |

> A2 属「契约锁定」：若未来有人把字段加回前端，此用例立即红。

### B. E2E UI 层（purchase-receive.spec.js，headless + 截图）

| 编号 | 用例 | 断言/留证 |
|---|---|---|
| B1 | 登录 → 采购订单列表 → 打开订单 1 收货弹窗 | ✅ 弹窗内**不存在**「检验结果」「检验备注」控件；截图 |
| B2 | 明细 1 填本次收货 10 → 提交 | ✅ 成功提示、弹窗关闭；截图 |
| B3 | 数据验证 | ✅ DB `purchase_order_item.received_quantity` item_id=1 从 100 → 110；列表/详情刷新可见 |
| B4 | 票据上传组件仍可用（冒烟） | ✅ 上传区存在、可打开（不强制传文件） |

### C. 跨模块正向链路（1505 不回归，E2E 或半自动）

| 编号 | 用例 | 断言 |
|---|---|---|
| C1 | 收货后进入入库确认（或从入库单入口）→ 检验员逐行录入 + 判定提交 | InboundInspectionDialog 检验能力仍在，提交成功 |
| C2 | 主管复核过账（时间允许） | 过账成功、库存物料数量增加 |

### D. 防漂移守卫（低成本，建议纳入）

| 编号 | 项 | 说明 |
|---|---|---|
| D1 | 提交字段 vs DTO 字段对照脚本 | 静态解析 OrderReceiveDialog submit keys vs `PurchaseOrderReceiveDTO$ReceiveItemDTO` 字段，不一致即红；可挂 `npm run validate` 或独立 node 脚本 |
| D2 | 死代码清理（后续） | `ReceiveDialog.vue` 未被任何页面引用且仍含旧字段提交，建议删除或同步（不阻塞本方案） |
| D3 | 类型收口（可选） | `api/purchase/order.ts` 的 `batchReceiveOrderItems` 参数仍声明可选 `inspectionResult/inspectionRemark`，建议移除防误导 |

---

## 四、数据与清理

- 被测单复用 order_id=1，测试收货量取小值（10），跑完记录前后差值断言，**不依赖全量数据造数**
- 清理：用例结束后可回滚 `received_quantity` 至基线（100）或登记为测试消耗，避免订单被逐步收满影响后续演示
- 截图目录：`tests/screenshots/purchase-receive-*`

---

## 五、产出物

1. 接口测试脚本 + E2E spec：`/mnt/d/openclaw-workspace/tests/`
2. 执行截图（MEDIA 发送）
3. 测试结果报告 md（UTF-8 BOM），含每用例通过/失败、DB 前后值、截图引用

---

## 六、执行前需确认的点

1. A2 的实际返回结构（400 原始体 or Result 包装）与 A5 超收规则——执行时先探后断言
2. 被测单是否同意直接复用 order_id=1（会真实 +10 收货量，跑后可回滚）
3. 本次执行范围：A+B 必做；C1 尽量；C2 视数据；D1-D3 作为后续建议不阻塞
