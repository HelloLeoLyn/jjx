# 重启后回归清单（037/038/039/040 + 1232/1312/1944/1914）（dev-20260921-044）

- 日期：2026-09-21
- 任务码：dev-20260921-044（第 2 档：一次重启收掉整批「代码已就位、只差运行态」的任务）
- 用途：**后端重启后照着一页点**。每条给了「动作 → 断言 → 失败判据 + 取证命令」。
- 覆盖任务：1312（发货→出库断链）、2100/039（发货流程改造）、2099/038（签收/拒收链路）、2101/040（完工入库口径）、2098/037（OrderMapper 白名单）、1232（销售主流程，母任务）、1944（质量归一阶段 3）、1914（返工复检子批次）
- 口径：验收是用户自己做；本页只负责"点哪里、看什么、什么情况算失败"。

---

## 0. 前置：重启（这一步由用户执行）

```bash
# 1) 仓库根：留回滚副本（当前运行的是 18:23 那个 jar）
cp /home/administrator/jjx/jjx-server/target/jjx-server-1.0.0.jar /tmp/jjx-server-1.0.0.prev.jar

# 2) 打包（不要用 mvn clean —— 会删掉正在运行的 target jar）
cd /home/administrator/jjx/jjx-server && mvn -o package -DskipTests

# 3) 仓库根启动
cd /home/administrator/jjx && java -jar jjx-server/target/jjx-server-1.0.0.jar
```

- 重启即全体下线，浏览器要**重新登录**（权限快照在会话里冻结）。
- 打包会把并行会话未提交的改动一起打进去（当前工作区有 23 个 M 文件）。
- 就绪判断：`curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/sessions/auth` 有响应即可。

---

## A. 发货 → 自动出库（1312 + 039 + 037）— 最高优先，链路最长

| # | 动作 | 断言（通过） | 失败判据 |
|---|---|---|---|
| A1 | 取一张「7 生产中」的订单（SO260921001 或新造），点发货，**全量**发 | ① `sales_delivery` +1（`delivery_no` = DL*）② `sales_delivery_item` 有本次明细行 ③ `inventory_outbound_order` 出现 `source_type='SALES'` 的出库单 ④ `inventory_outbound_item` 数量 = 本次发货量 ⑤ `sales_order.order_status` = 8 ⑥ `shipped_quantity` = 明细累计 | ③ 为 0 行 = 桥仍没触发；③ 有单但 ④ 无明细 = 走了兜底路径；⑤=8 但库存未减 = 出库确认没跑 |
| A2 | 同一张单先发 **1 件**，再发剩余 | 第一次：出库数量 =1、`shipped_quantity`=1、订单**仍为 7**（列表显示「已发 1/2」）；第二次：订单转 8 | 第一次就跳 8 = 039 分批发货未生效 |
| A3 | 造一张订单金额为 0 的单，点发货 | 被拒绝 + 提示补价 | 放行 = 040 的 0 元拦截未生效 |
| A4 | A1 之后查库 | `shipped_quantity / produced_quantity / prod_status` **真落库**（非 0/NULL） | 全 0 = 037 白名单仍未生效（静默丢弃的老毛病） |

取证：

```sql
-- 出库单与明细
SELECT o.outbound_id,o.outbound_no,o.source_type,o.source_no,o.outbound_type,o.create_time
FROM inventory_outbound_order o WHERE o.source_type='SALES' ORDER BY o.outbound_id DESC LIMIT 5;
SELECT * FROM inventory_outbound_item WHERE outbound_id = <上一步 outbound_id>;
-- 订单侧回写
SELECT order_id,order_no,order_status,shipped_quantity,total_quantity,produced_quantity,prod_status,
       paid_amount,unpaid_amount,payment_status FROM sales_order WHERE order_id = <订单id>;
```

日志关键行（后端 stdout）：

```bash
# 桥是否被调用（没有这行=事件没发）
grep "🚛 销售发货联动出库" <日志>
# 按发货单明细出库成功
grep "销售出库单已按发货单明细创建并扣库存" <日志>
# 走兜底（=异常信号）
grep "创建销售出库单失败\|销售出库单已创建并确认" <日志>
```

---

## B. 签收 / 拒收（038 + 039）

| # | 动作 | 断言 | 失败判据 |
|---|---|---|---|
| B1 | 发货单登记**签收** | 20/21 角色收到「发货单【DL…】客户已签收」通知；`sys_notification` 有行、`receiver_id` 非空 | 无通知 = 迁移 166 的收件人未生效（旧日志会打「未解析到动态收件人或目标角色」，现已降级为 debug，改看通知表） |
| B2 | 对**已发货**单登记**拒收**（原因必填） | ① 发货单状态 → 5 ② 自动生成入库单 `REJECT-{发货单号}` 并过账 ③ 库存回冲（`product_stock` 回到拒收前） ④ 订单回 **7 生产中**（可重新发货） ⑤ 收到 `sales.delivery.rejected` 通知/任务（收件人 20/21） | ② 缺 = `createSalesRejectInbound` 没跑；③ 缺 = 过账失败；④ 缺 = 订单未收口 |
| B3 | 打开送货单打印 | 只出现**本次发货明细**（历史无明细的老单回退订单明细，不算失败） | 打印全量订单明细 = 039 打印口径未生效 |

取证：

```sql
SELECT delivery_id,delivery_no,order_id,delivery_status,customer_receive_date,
       reject_reason,reject_time,reject_by,reject_name FROM sales_delivery ORDER BY delivery_id DESC LIMIT 5;
SELECT inbound_id,inbound_no,source_type,source_no,total_quantity,approve_status
FROM inventory_inbound_order WHERE source_no LIKE 'REJECT-%' ORDER BY inbound_id DESC LIMIT 5;
```

---

## C. 生产完工入库口径（040）

| # | 动作 | 断言 | 失败判据 |
|---|---|---|---|
| C1 | 触发/重放一次「生产工单已生成入库单」事件 | 通知标题 = 「生产工单【WO-…】已生成入库单【FINISH-…】」 | 标题出现字面量 `{bizNo}`/`{sourceNo}` 或「生产工单【】」= 迁移 167 未生效 |
| C2 | 看生产完工入库的通知正文 | 显示来源描述（生产工单 WO-… / 采购单 PR… + 供应商 / 客户拒收回库） | 正文空值或供应商为空 = `sourceDesc` 键没进 payload |
| C3 | FQC 差额入库先于工单完工 | 允许，且完工时 `createFromProduction` 幂等跳过（日志有跳过记录），不报错 | 抛错阻断 = 幂等分支没生效 |
| C4 | 查 SO260921001 | `produced_quantity`=2、`prod_status`=4、金额 0 → 440.00 | 仍为 0 = 迁移 164/168 未生效 |

---

## D. 质量归一（1944 + 1914）

| # | 动作 | 断言 | 失败判据 |
|---|---|---|---|
| D1 | IQC 全链路跑一遍：收货→提交检验→**审核通过**→部分接收→隔离→退货/返工/报废→复检 | ① 判定只发生在**审核通过**时（`quality_lot` 此时才写）② 5 张 IQC 单据（quarantine/disposition/return/rework/scrap）落库时 **lot_id 有值** ③ 隔离/退货数量与判定口径一致 | ① 提交时就判定 = expand 语义回退；② lot_id 全空 = 阶段 3 读口未切 |
| D2 | 批次谱系页：供应商返工 → 复检 | ① 返工生成**子批次**、父子谱系可追溯 ② 复检入口**只允许选返工子批次**，原批次不能冒充 ③ 整批合格接收数量必须 = 收货数量，不符可阻断 | 2 能选原批次 = 1914 校验未生效；3 放行 = RM001599 校验未生效 |

---

## E. 1232 销售主流程全链（母任务，最后跑一次总检）

客户 → 询价 → 报价 → 订单 → 评审 → 工单 → 发货 → 出库 → 收款 → 发票 → 完成，**一条链一次跑完**，重点看三处：

1. **收款回写**：新建收款单后 `sales_order.paid_amount / unpaid_amount / payment_status` 必须真变（这是 1779 的首现场景，也是 A4 的同一根因）
2. **事件链**：每一步该发的通知都在 `sys_notification`（收款改/删、退货驳回退款、订单完成、报价改单、询价发送/接受/拒绝共 10 处，任务 2027）
3. **状态机**：`order_status` 每步跃迁与枚举一致，不跳级、不回退

---

## F. 有意不在本轮验的（避免误判）

- 账期起算 / 对账清单按签收日联动 —— 客户信用期未建模（039 已标注另立项）
- 拒收回库后的质检/报废分支 —— 口径=可再售直接回库
- OQC 出货检验、CAPA —— 尚未开发（2104/2105）
- 跨表 collation 统一 —— 本次用显式 COLLATE 规避，另立项
- 历史脏数据（077 之前的老单）—— 用户自行处理

---

## G. 结果回填

- 通过 → 对应任务 `sys_task.status` 置 10（1312/2099/2100/2101/2098/1944/1914/1232）
- 不通过 → 把「哪一条 + 实际看到的 + 上面哪条取证命令的输出」贴回来，按失败判据定位是新缺口还是旧进程残留
- 本页本身（dev-20260921-044）验收同样翻 10
