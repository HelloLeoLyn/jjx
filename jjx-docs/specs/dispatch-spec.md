# JJX ERP 生产派工模块方案

> 版本：v1.0（讨论稿）
> 编写：2026-08-12
> 状态：需求已讨论，待评审后开发

---

## 一、背景与定位

当前生产链路：生产工单（计划）→ 工序执行（记录），**中间没有派工环节**——谁来做、用哪台设备、责任怎么追，目前无系统化管理。

新建**生产派工模块**：以工序为粒度派工到班组/设备/执行人，建立"工单负责人 → 工序班组长 → 执行人"三级责任链，全程操作留流水。

## 二、需求决策（2026-08-12 已确认）

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 班组来源 | ✅ 用现有部门树（sys_dept）当班组，班组=末级部门 |
| 2 | 派工粒度 | ✅ 工序级派工单为主体 + 工单级批量派工快捷入口，班组/设备/执行人可逐级指定 |
| 3 | 派工主体 | ✅ 各级主管派工指定（工单级=车间主任/计划员，工序级=班组长） |
| 4 | 状态机 | ✅ 待派工→已派工→执行中→已完成，支持退回改派 |
| 5 | 审批 | ✅ 不审批，但状态展示要清晰（tag 色 + 退回原因必填） |
| 6 | 操作留痕 | ✅ 所有派工操作记流水（指派/改派/退回/开始/完成，含操作人/时间/变更内容） |

## 三、派工模型（三级责任链）

```
【工单级】生产工单（production_order 加字段）
   负责班组 + 工单负责人（默认=班组 leader，可改）
      ↓ 车间主任/计划员指派
【工序级】工序派工单（production_dispatch，一道工序一张）
   责任班组 + 设备（可空=不限）+ 执行人（可多个）
      ↓ 班组长/主管指派
【执行级】工序执行记录（production_operation_execution，现有表）
   执行时自动带出派工的班组/设备/执行人
```

- **工序来源**：工单创建时已按工艺路线生成工序执行记录（`production_operation_execution`），派工单挂 `execution_id`，天然联动执行状态
- **按工单批量派工**：选班组/设备/执行人 → 整单所有未派工工序一键批量生成派工单（可再逐道微调）

## 四、表结构设计

### 4.1 production_order 增加字段（工单级责任）

```sql
ALTER TABLE production_order
  ADD COLUMN dispatch_team_id    BIGINT       NULL COMMENT '负责班组(部门ID)' AFTER priority,
  ADD COLUMN dispatch_team_name  VARCHAR(100) NULL COMMENT '负责班组名称' AFTER dispatch_team_id,
  ADD COLUMN dispatch_leader_id  BIGINT       NULL COMMENT '工单负责人(用户ID，默认=班组leader)' AFTER dispatch_team_name,
  ADD COLUMN dispatch_leader_name VARCHAR(64) NULL COMMENT '工单负责人姓名' AFTER dispatch_leader_id;
```

### 4.2 production_dispatch（工序派工单）

```sql
CREATE TABLE production_dispatch (
  dispatch_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '派工单ID',
  order_id           BIGINT       NOT NULL COMMENT '生产订单ID',
  order_no           VARCHAR(50)  NULL COMMENT '工单编号(冗余)',
  execution_id       BIGINT       NOT NULL COMMENT '工序执行记录ID(production_operation_execution)',
  process_name       VARCHAR(200) NULL COMMENT '工序名称(冗余)',
  process_order      INT          NULL COMMENT '工序顺序(冗余)',

  -- 派工对象：班组 + 设备 + 执行人（可组合，均可空=未指定）
  team_id            BIGINT       NULL COMMENT '责任班组(部门ID)',
  team_name          VARCHAR(100) NULL COMMENT '责任班组名称',
  equipment_id       BIGINT       NULL COMMENT '设备ID(空=不限)',
  equipment_name     VARCHAR(200) NULL COMMENT '设备名称',
  operators          VARCHAR(500) NULL COMMENT '执行人(JSON数组 [{userId,userName}])',

  -- 派工主体与状态
  assigned_by        BIGINT       NULL COMMENT '派工主管(用户ID)',
  assigned_by_name   VARCHAR(64)  NULL COMMENT '派工主管姓名',
  assign_time        DATETIME     NULL COMMENT '最近指派时间',
  status             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待派工 1已派工 2执行中 3已完成 4已退回（静态枚举）',
  reject_reason      VARCHAR(500) NULL COMMENT '退回原因(退回时必填)',
  re_dispatch_count  INT          NOT NULL DEFAULT 0 COMMENT '改派次数',
  remark             VARCHAR(500) NULL COMMENT '备注',

  -- 审计（与全项目一致）
  del_flag           CHAR(1)      NOT NULL DEFAULT '0',
  create_by          VARCHAR(64)  NULL,
  create_time        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by          VARCHAR(64)  NULL,
  update_time        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (dispatch_id),
  UNIQUE KEY uk_execution (execution_id),
  KEY idx_order (order_id),
  KEY idx_team_status (team_id, status),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序派工单';
```

### 4.3 production_dispatch_log（派工流水）

```sql
CREATE TABLE production_dispatch_log (
  log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  dispatch_id   BIGINT       NOT NULL COMMENT '派工单ID',
  order_id      BIGINT       NULL COMMENT '工单ID(冗余)',
  action        VARCHAR(20)  NOT NULL COMMENT '操作：ASSIGN指派/REASSIGN改派/REJECT退回/START开始/COMPLETE完成',
  content       VARCHAR(1000) NULL COMMENT '变更内容（如：由生产一组改派给生产二组，设备由3#印刷机改为5#印刷机）',
  operator_id   BIGINT       NULL COMMENT '操作人ID',
  operator_name VARCHAR(64)  NULL COMMENT '操作人姓名',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

  PRIMARY KEY (log_id),
  KEY idx_dispatch (dispatch_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工操作流水';
```

## 五、状态机

```
0 待派工 ──主管指派──▶ 1 已派工 ──执行人开始──▶ 2 执行中 ──完成──▶ 3 已完成
    ▲                    │                         │
    └──────主管退回(原因必填)◀───────┘              │
         改派次数+1，流水记录                         └── 任意非终态可退回
```

| 状态 | 含义 | tag 色 |
|---|---|---|
| 0 待派工 | 未指派或已退回待改派 | 灰 |
| 1 已派工 | 已指派班组/设备/人，等待开工 | 蓝 |
| 2 执行中 | 工序已开始 | 橙 |
| 3 已完成 | 工序完成 | 绿 |
| 4 已退回 | 退回中（等同待派工，展示区分） | 红 |

**退回规则**：已派工/执行中可退回；退回原因必填；退回后状态=4（展示红色），主管重新指派后回到 1，改派次数+1。

## 六、页面设计

### 6.1 菜单位置

`生产管理 → 派工管理`（菜单编码 `production:dispatch`）

### 6.2 工单详情新增"派工"Tab（重点）

工单详情/编辑页增加派工 Tab：
- **工单级责任卡**：负责班组（部门树选择）+ 工单负责人（默认带出班组 leader，可改）→ 保存到工单表
- **工序派工列表**：该工单全部工序（来自 execution，按 process_order 排序），每行显示：工序名 | 责任班组 | 设备 | 执行人 | 状态 tag | 操作
  - 操作：指派（弹窗：班组/设备/执行人多选）、退回（原因必填）、查看流水
- **批量派工**：按钮 → 弹窗统一选班组/设备/执行人 → 应用到整单所有待派工工序
- **流水时间线**：选中工序查看该派工单的完整操作记录（时间线展示）

### 6.3 派工管理列表页（`views/production/dispatch/index.vue`）

- 筛选：工单编号 | 责任班组 | 状态 | 工序关键字
- 表格：工单号 | 工序 | 班组 | 设备 | 执行人 | 派工主管 | 状态 | 指派时间 | 改派次数 | 操作（改派/退回/流水）
- 状态 tag 色见 §五；改派次数>0 显示徽标

## 七、接口设计

Base：`/production/dispatch`

| 方法 | 路径 | 说明 | 权限码 |
|---|---|---|---|
| GET | `/production/dispatch/page` | 分页（orderNo/teamId/status/keyword） | `production:dispatch:list` |
| GET | `/production/dispatch/order/{orderId}` | 工单全部派工单（含工序） | `production:dispatch:list` |
| GET | `/production/dispatch/{id}/logs` | 派工流水 | `production:dispatch:list` |
| POST | `/production/dispatch/assign` | 单工序指派（executionId+班组/设备/执行人） | `production:dispatch:assign` |
| POST | `/production/dispatch/batch-assign` | 工单批量指派（整单未派工序） | `production:dispatch:assign` |
| POST | `/production/dispatch/{id}/reject` | 退回（原因必填） | `production:dispatch:assign` |
| POST | `/production/dispatch/{id}/start` | 开始（联动执行） | `production:dispatch:start` |
| POST | `/production/dispatch/{id}/complete` | 完成（联动执行） | `production:dispatch:start` |
| PUT | `/production/order/{id}/dispatch-team` | 工单级负责班组/负责人 | `production:dispatch:assign` |

**指派/改派规则**：
- 班组、设备、执行人可单独指定或组合（至少指定一项）
- 改派（已派工/执行中重新指派）= REASSIGN，流水记录变更内容（旧值→新值）
- 执行人 JSON 数组 `[{userId, userName}]`，前端多选用户（按班组过滤）

**执行联动**（与 production_operation_execution）：
- 工序执行开始/完成时，同步回写对应 dispatch 状态（1已派工→2执行中→3已完成）
- dispatch 的 start/complete 接口也可反向触发执行状态（双向一致，以先发生者为准）
- 执行页操作时自动带出派工的班组/设备/执行人信息

## 八、权限与菜单

| 权限码 | 名称 | 类型 |
|---|---|---|
| `production:dispatch:list` | 派工查询 | 目录+菜单 |
| `production:dispatch:assign` | 派工/改派/退回 | 按钮 |
| `production:dispatch:start` | 开始/完成 | 按钮 |

菜单挂"生产管理"下，管理员默认授权，其他角色按需。

## 九、开发范围预估

| 项 | 内容 | 预估 |
|---|---|---|
| SQL | 工单加字段 + 2 张表 + 菜单/权限 | 1h |
| 后端 | 枚举/Entity/Mapper/Service/Controller + 批量派工 + 流水 + 执行联动 | 5h |
| 前端 | 派工管理列表页 + 工单详情派工Tab + 批量派工弹窗 + 流水时间线 | 4h |
| 测试 | 接口自测 + 状态机走查 + 联动验证 | 2h |

合计约 **1.5 人日**。

## 十、待确认问题

1. 工单详情页的"派工 Tab"挂在现有工单详情/编辑页哪个位置？（需看现有工单详情页结构）
2. 执行联动"双向一致"冲突时以谁为准？（建议：执行模块触发为主，dispatch 按钮为辅）
3. 执行人多选上限？（建议 ≤10 人）
4. 派工管理菜单要不要显示"待派工"数量角标（提醒主管）？
