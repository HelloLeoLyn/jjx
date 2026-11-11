# 多层BOM制造需求 confirm：持久确认与并发分配设计提案

任务：dev-20261111-006（总任务 dev-20261111-002 子任务 D，第二步后半）。
日期：2026-11-11，Asia/Shanghai。工作目录：/home/administrator/jjx。
状态：**仅设计提案**。未执行 DDL、未建表、未写迁移、未改既有表、未暴露 confirm 成功响应、未启动服务。所有表/列均为草案，需用户批准后才进入实施卡。

## 结论摘要

1）需要 2 张新表：请求头 `bom_requirement_request`（客户端幂等键 + 请求体指纹）与分配行 `bom_requirement_allocation`（供给来源唯一占用 + 审计）。除这两张表外全部复用现有只读表。
2）不复用 `order_material_reserve`、`sales_order_stock_reserve`：两者 `order_id` NOT NULL，语义是销售订单预留，制造需求没有销售订单，塞假单会污染订单回写、预留报表和领料额度。
3）不复用 `biz_requirement`：该表是变更/需求登记单（requirement_type/change_type/review 流程），不是制造需求确认。
4）confirm **不写** `inventory_transaction` / `inventory_stock` / `inventory_stock_item`：它表达“需求占用了哪批候选供给”，不是库存变动；库存口径不变式仍只由 `InventoryStockMutationService.applyDelta` 维护。副作用是：confirm 不锁定现货，现货仍可能被领料抢走（见“风险”）。
5）卷类（卷/ROLL/COIL）逐卷占用留给 013；confirm 与 013 的边界见第 6 节。

## 现状事实（只读核对，2026-11-11）

1）当前没有 confirm 实现：`ManufacturingRequirementController` 只有 preview 一个 POST；`RequirementConfirmRequest` 只是契约占位；summary 返回 `confirmSupported=false`。
2）供给来源三类，且口径已在本卡第二步收紧：`inventory_stock`/`inventory_stock_item`（现货）、`purchase_order_item`（已批准采购的未收数量）、`production_order`（已批准工单的 planned − finished）。
3）`purchase_order`、`production_order` 都没有仓库列 → 指定仓库时采购/生产供给在 preview 中只展示不抵扣；confirm 必须沿用同一处理，不能凭空给供给归属仓库。
4）`order_material_reserve(id, order_id NOT NULL, order_no NOT NULL, material_id, reserve_quantity, status, ...)`；`sales_order_stock_reserve(id, order_id NOT NULL, ..., reserve_quantity, status, reserve_type)`。两者都没有“请求”维度、没有“供给来源”维度。
5）`biz_requirement` 字段为 requirement_no/requirement_type/title/change_type/reviewer/execute_*，属于变更单流程。
6）`inventory_transaction` 只增不改；`inventory_stock.available_quantity` 是生成列（total − reserved）。
7）真实库数据现状：purchase_order 0 行、purchase_order_item 0 行；production_order 2 行（approval_status=0、order_status=1），无 MATERIAL 产出 BOM 绑定；inventory_warehouse 2 行 status 均为 '1'。即 confirm 上线初期没有真实供给可占用，需要业务先补采购单/工单审批。

## 设计

### 1）请求级幂等

草案表 `bom_requirement_request`：

| 列 | 类型 | 说明 |
|---|---|---|
| request_id | BIGINT PK AUTO | 服务端主键 |
| request_no | VARCHAR(32) NOT NULL UNIQUE | 服务端单号，供业务/报表引用 |
| client_request_id | VARCHAR(64) NOT NULL UNIQUE | 客户端幂等键（UUID），重放唯一依据 |
| request_hash | CHAR(64) NOT NULL | 规范化请求体的 SHA-256 |
| product_id / bom_id | BIGINT NOT NULL | 需求对象 |
| quantity | DECIMAL(18,4) NOT NULL | 需求数量 |
| demand_date | DATE NOT NULL | 需求日 |
| warehouse_id | BIGINT NULL | 指定仓库时现货按仓，供给不抵扣（沿用 preview） |
| status | TINYINT NOT NULL | 1 已确认 / 2 已释放 / 3 已回滚 |
| supply_fingerprint | CHAR(64) NULL | 确认时供给快照指纹，供失效比对 |
| create_by / create_time / update_by / update_time / remark | 审计 | 与既有表风格一致 |

规则：

A) 同一 `client_request_id` + 同一 `request_hash` → 返回首次结果（幂等重放），不新增分配行。
B) 同一 `client_request_id` + 不同 `request_hash` → 拒绝（409 REQUEST_ID_CONFLICT），不覆盖原记录，原记录保持可审计。
C) `client_request_id` 为空 → 直接拒绝，不生成隐式键（隐式键无法保证重放语义）。

### 2）分配行唯一键

草案表 `bom_requirement_allocation`：

| 列 | 类型 | 说明 |
|---|---|---|
| allocation_id | BIGINT PK AUTO | |
| request_id | BIGINT NOT NULL | 关联请求头 |
| line_no | INT NOT NULL | 请求内行号，稳定顺序 |
| material_id | BIGINT NOT NULL | 物料 |
| supply_type | VARCHAR(16) NOT NULL | STOCK / PURCHASE / PRODUCTION |
| supply_ref_id | BIGINT NULL | 采购= purchase_order_item.item_id；生产= production_order.order_id；现货=NULL |
| warehouse_id | BIGINT NULL | 仅 STOCK 行 |
| quantity | DECIMAL(18,4) NOT NULL | > 0，已折算到库存单位 |
| unit | VARCHAR(20) NOT NULL | 库存单位 |
| unit_factor_exact | VARCHAR(64) NULL | 折算依据留存（如 5/2），便于复核 |
| active_flag | TINYINT NULL | 1=占用；释放时置 NULL |
| release_reason / release_by / release_time | | 释放审计，释放不删行 |
| create_time | DATETIME | |

唯一键：

- `UNIQUE(request_id, line_no)`：同一请求内行号不可重复。
- `UNIQUE(supply_type, supply_ref_id, active_flag)`：**同一供给来源同时只能被一个请求占用**；释放时 `active_flag=NULL`（MySQL 唯一索引允许多 NULL），历史行保留。
- 普通索引 `(material_id, active_flag)`、`(request_id)`。

### 3）同键不同请求体拒绝

规范化规则（写入前统一执行，避免同义请求被判为不同）：

A) 数值 `stripTrailingZeros().toPlainString()`；B) 日期 ISO-8601；C) 字段按名字排序后拼接；D) 不含客户端可随意变化的字段（时间戳、备注）。

对规范化结果取 SHA-256 存 `request_hash`。比较在事务内、对 request 行加锁读之后进行，避免并发重放时两次都判为空。

### 4）事务与锁顺序

1）事务边界只包 confirm 自身（REQUIRED），不加到 preview。
2）先按 `client_request_id` 唯一键插入 request 行：由唯一索引裁决并发重放，失败方回读已存在行并按 1A/1B 处理。
3）再锁定供给来源：固定顺序 `STOCK → PURCHASE → PRODUCTION`，同类型内按 `supply_ref_id` / `material_id` 升序 `SELECT ... FOR UPDATE`。
4）最后插入 allocation 行；`UNIQUE(supply_type, supply_ref_id, active_flag)` 冲突 → 整体回滚并返回 409 SUPPLY_CONTENDED + 被占用来源清单。
5）永不按“请求内行序”或“用户点击顺序”加锁；这样与库存领料/入库路径（按 material_id 升序）不交叉成环。
6）现货行只读校验可用量，不加写锁（confirm 不改余额），因此现货存在被领料抢走的窗口（见风险）。

### 5）失效供给重算与回滚

A) confirm 时用与 preview 相同的 Provider 重新读取供给，计算 fingerprint；与请求若携带的 preview fingerprint 不一致 → 409 SUPPLY_CHANGED，返回差异（数量/来源），不静默按旧数量确认。
B) request 行与 allocation 行同事务写入；任一步失败整体回滚，不留半套占用。
C) 释放只把 `active_flag` 置 NULL 并写 release_*，不删行、不动库存。
D) 供给来源失效（采购单被驳回/取消、工单关闭）：查询期按来源订单状态过滤 active 行；需要持久失效时由独立任务生成释放记录，不静默改数量。是否需要该任务涉及调度授权，列入待批准项。

### 6）与 013 逐卷预留的边界

A) 卷类物料（卷/ROLL/COIL）在 confirm 中不做逐卷占用：preview 已禁止用档案长宽自动换算，有 `unit_factor`+依据时也只按数量折算，不带卷号/批次。
B) 逐卷（roll/lot）实物占用由 013 的表与接口负责；confirm 不写 013 的表，需要时调用 013 提供的“申请占用/释放”服务契约。
C) 若要记录跨模块关联，建议在 allocation 增加 `roll_reservation_id BIGINT NULL`（列，不是新表），或由 013 侧存 confirm request_id；两者取其一，依赖 013 最终表形态。
D) 原则：confirm 决定“是否占用这批候选供给”，013 决定“用哪一卷”。

### 7）迁移与回退

A) 两张新表 additive，不改既有表结构；迁移编号按 `jjx-docs/sql/migrations` 规则取“目录最大号与 `ops.schema.applied` 最大号的较大者 +1”。当前目录最大为 356，但 356 正被 013 移动归档，实施前必须重新核对账本，不能照抄本提案里的编号。
B) 回退：先关 confirm（权限收回或配置开关），表保留只读；确需删表须用户批准并先导出。不触碰 inventory_*、purchase_*、production_order 结构。
C) preview 与 confirm 解耦：confirm 关闭时 `confirmSupported=false`，VO 不变。

### 8）需要新表/新列 vs 可复用现有表

| 需求 | 结论 |
|---|---|
| 请求级幂等（同键不同体拒绝） | **新表** bom_requirement_request（必需） |
| 分配行唯一键与释放审计 | **新表** bom_requirement_allocation（必需） |
| 与 013 的卷级关联（可选） | **新列** roll_reservation_id 或 013 侧反向存 request_id（二选一） |
| 现货可用量 | 复用 inventory_stock / inventory_stock_item（只读） |
| 采购未收数量 | 复用 purchase_order_item（只读，item_id 作 supply_ref_id） |
| 工单未入库产出 | 复用 production_order（只读） |
| 库存变动 / 流水 | **不复用、不写**（confirm 不是库存变动） |
| 销售订单预留 | **不复用** order_material_reserve / sales_order_stock_reserve（order_id NOT NULL，语义冲突） |
| 变更需求单 | **不复用** biz_requirement（ECN 语义） |
| 权限 | 建议新增 production:requirement:confirm（待批）；preview 权限不变 |
| 菜单 | 本提案不新增菜单，confirm 由需求试算页按钮触发（前端属后续卡） |

### 9）未决项（需用户批准）

1）是否批准两张新表及上述唯一键方案，特别是“释放时 `active_flag=NULL`”的写法（替代方案是新增 `released` 行并改唯一键为三列包含状态）。
2）confirm 是否允许部分确认：供给不足时按“已满足部分确认 + 缺口待办”落单，还是整单拒绝。
3）供给失效后的自动释放是否需要定时任务（涉及进程/调度授权，本卡不启停）。
4）卷类占用枚举（`supply_type=ROLL`）与 013 接口形态由谁定义。
5）是否新增 `production:requirement:confirm` 权限点与角色授权。

## 风险

1）现货不锁写：confirm 只保证“同一采购行/工单不被两个 confirm 同时占用”，不保证现货不被领料或入库红冲抢走。彻底解决需库存预留（属库存写入路径，本卡明确禁止）。
2）唯一键含 NULL 列：同一来源多次“占用—释放”会产生多条 NULL 行，报表统计必须按 `active_flag=1` 取当前占用，否则会重复计数。
3）fingerprint 机制依赖 preview 与 confirm 的同源读取；若两次读取间隔内有并发确认，只能靠来源唯一键兜住，不能靠 fingerprint 保证一致。
4）真实库当前无采购/工单供给数据，confirm 上线后首选验证点是“全缺口 + 现货占用”路径。

## 本提案的边界声明

未执行任何 DDL、未创建表、未改迁移目录、未改 `sys_task`、未启动服务、未暴露 confirm 成功响应、未新增前端入口。`jjx-docs/history/INDEX.md` 未登记（INDEX 当前由 013 持有，登记前需 OpenClaw-lead 确认）。
