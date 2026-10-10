# 样品需求单 QR-065（挂样品单）与打样不良记录：提案与实施方案（dev-20261010-028）

日期：2026-10-10

## 一、需求（用户 2026-10-10 拍板）

功能一 样品需求单 QR-065：复用 QR-065；在样品单打印页加「样品需求单」版式；任意状态可打；机种编号=产品编码；客供资料/检验报告/订单号码/承认书份数=打印留空（客户填）；签核 3 位（业务/核准/部门主管）=会签式（同意/不同意+意见+留痕+可多轮）；签字权限走权限点+角色管理；不卡流程（纯留痕）；姓名+时间文字。

功能二 打样平台不良记录：样品单级；字段=制样类别（印刷/加工冲型）+不良原因+改善+记录人（权限）+日期；可多条。

## 二、新表提案（CONVENTIONS §15.3）

用户 2026-10-10 拍板同意新建 2 张专表。

### 2.1 sales_sample_requisition_sign（样品需求单会签记录）
独立于其它单据的会签记录；样品单级、可多轮、三位签字、纯留痕（不驱动单据状态）。

为什么不能复用现有表：
- `biz_requirement_approval`（业务需求会签）结构相近，但语义不同：业务需求是四部门、全部同意才生效、任一不同意驳回（**卡流程、驱动状态**）；本单是三位、**不卡流程、纯留痕**，且属样品单域而非需求域，非"同一对象的类型/变体"（§15.2.1）。
- JSON 落样品单表：不可按 orderId+role+round 查询、无库级约束、挂不上独立权限点。
- 操作日志（sys_ope_log/trace）：语义是动作审计，回显不出"哪位签/未签"。

§15.2 准入：①独立对象 ③一对多 ④超 JSON 承载 ⑤需独立权限点留痕 ⑥无法复用 —— 过 5 条。

### 2.2 sales_sample_defect_record（打样不良原因及改善）
样品单级多条记录，回填 QR-065「印刷制样记录 / 加工冲型制样记录」两栏。

为什么不能复用：`sales_sample_process.process_note` 是工序级随手备注，装不下"样品单级、多条、留痕、独立权限"；`quality_ncr` 是正式生产/检验的不良品处置流程（报废/返工/让步），语义不符。

§15.2 准入：①独立对象 ③一对多 ④超 JSON 承载 ⑤需独立权限点留痕 ⑥无法复用 —— 过 5 条。

### 2.3 迁移与兼容 / 回滚 / 风险
- 建表 DDL 见迁移 `259_sample_requisition_and_defect_tables.sql`；均为**新增空表**，无存量数据迁移，无字段改动，对现有业务零影响。
- 兼容：不改现有表结构与既有数据；QR-065 由"停用解绑"改为"生效并绑样品单"（见迁移 260）。
- 回滚：DROP 两张新表 + 迁移 260 反向（QR-065 回 status=2 解绑、删 4 个权限按钮与其授权）。
- 风险：低。唯一外部依赖是用户执行迁移；若不执行，前端/后端新功能将报错（表不存在），但现有功能不受影响。

## 三、权限点与菜单（甲：权限点 + 角色管理）

新增 4 个按钮权限（sys_menu，menu_type=F）：
- `sales:sample:reqsign:sales`（业务位）挂 样品单管理(229)
- `sales:sample:reqsign:approve`（核准位）挂 229
- `sales:sample:reqsign:dept`（部门主管位）挂 229
- `engineering:sample:defect:record`（不良记录人）挂 打样平台(239)

授权默认：签核 3 权限授 超级管理员(1) + SALES 全权限(10)；不良记录权限授 超级管理员(1) + ENGINEERING 全权限(16)。其余角色由管理员在「角色管理」按需勾选。

## 四、接口（SampleRequisitionController，/sales/sample-order）

- 会签：GET /{orderId}/requisition-signs ｜ PUT /{orderId}/requisition-sign?role=&approved=&comment=
- 不良：GET /{orderId}/defects ｜ POST /{orderId}/defects ｜ DELETE /defects/{id}

## 五、前端挂点

- 样品单打印页 `views/sales/sample-order/print.vue`：加版式切换（样品单 / 样品需求单），按原版 QR-065 版式出。
- 样品单管理列表 `views/sales/sample-order/index.vue`：加「需求单签核」动作 → 会签弹窗（业务/核准/部门主管三栏，按权限）。
- 打样平台工作台 `views/engineering/sample-workbench/`：加「不良原因及改善」面板（按权限可录，可多条）。

## 六、迁移与部署

- `259_sample_requisition_and_defect_tables.sql`：建 2 表。
- `260_sample_requisition_permissions_and_qr065.sql`：加 4 权限按钮 + 授权 + 恢复 QR-065（status=1、category=data、biz_type=sales_sample_order、print_component=views/sales/sample-order/print.vue、print_mode=dual、qr_enabled=1、biz_module=销售管理-样品单）。
- 迁移与后端重启由用户执行；本任务不启停服务。

## 七、不做 / 遗留

- 不做打印留痕（用户明确）。
- 签核不卡流程（纯留痕）。
- 后续若出现第 3 种单据需会签，再抽通用会签表（§15.6 同构表 ≥3 才评审）。
