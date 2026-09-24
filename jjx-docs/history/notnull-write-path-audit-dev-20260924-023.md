# NOT NULL 写入路径同类问题审计（任务码 dev-20260924-023）

## 0. 一句话结论

`Field 'xxx' doesn't have a default value` 不是孤例：根因是**"库列 NOT NULL 且无默认值" + "MyBatis-Plus 默认把 null 属性剔出 INSERT 列清单" + "代码在某条写入路径上真的给不出值"** 三者叠加。
本次全库扫描 360 个候选列后，**真雷只有一类 5 处**（IQC 单的 `inspection_id`，已由迁移 220/221 全部放开）；另有 1 处**不同根因**的必炸接口（工程管理 `EngineeringBase` 错映射 `sys_task`，未修，见 §5）。

## 1. 起因（两次报错，同一个根因）

| 时间 | 报错 Mapper | 触发动作用户 | 表 |
|---|---|---|---|
| 2026-09-24 15:2x | `InventoryIqcQuarantineMapper.insert` | 采购入库审核通过 | `inventory_iqc_quarantine` |
| 2026-09-24 16:0x | `InventoryIqcDispositionOrderMapper.insert` | 不合格品处置（放行/退货/返工/报废） | `inventory_iqc_disposition_order` 等 4 张 |

两次 SQL 都缺 `inspection_id` 列，报错文案一致。

## 2. 根因机制（三层，缺一不可）

1. **库**：该列 `bigint NOT NULL` 且无 DEFAULT；库 `sql_mode` 含 `STRICT_TRANS_TABLES` → 缺列即报错（非严格模式会被填 0，所以以前可能"看不出问题"）。
   - 取证：`SHOW CREATE TABLE jjx_erp_db.inventory_iqc_quarantine` / `SELECT @@sql_mode`
2. **框架**：MyBatis-Plus 默认 `insertStrategy=NOT_NULL` → 实体属性为 null 时该列**不进 INSERT 列清单**。
   - 取证：报错里的 SQL 列清单即 MP 生成结果（缺 `inspection_id`），实体 `InventoryIqcQuarantine` 里该字段是有的。
3. **业务**：IQC 归一（`dev-20260918-026/-027`，提交 `8d0556f7`）把检验事实从 `production_quality_inspection` 切到 `quality_lot`：
   - 迁移 142 给 5 张 IQC 单加 `lot_id`；迁移 181/182 把旧检验表归档并删除（现库内 `information_schema.tables LIKE '%inspection%'` 已无任何旧检验表）；
   - 代码随之把关联写死为 null：`InventoryInboundServiceImpl.java:435 quarantine.setInspectionId(null)`，下游 4 张单 `order.setInspectionId(quarantine.getInspectionId())`（`:521 / :605 / :624 / :642`）。
   - 于是 `inspection_id NOT NULL` 变成**一条永远无法满足的约束**：值只能是 NULL，列却禁止 NULL。

**为什么以前不炸**：只有明细 `fail_quantity > 0` 才会建隔离行（`createIqcQuarantine` 第 427 行会 `continue` 全合格行）；且隔离行插不进去时流程根本走不到下游单据。220 修好第一处后，处置动作才第一次撞上同一堵墙（第二处）。

## 3. 同类问题扫描（方法与判据）

候选集 = 全库「NOT NULL + 无默认值 + 非自增/生成 + 非主键」列 = **360 列 / 109 表**（脚本 `scripts/check-notnull-writes.py` 实时统计）。

判据按精确度分三档：

- **① MISSING**：实体没有该属性（或被 `@TableField(exist=false)` 排除）→ 该表任何 insert 必漏列。**精确、可当门禁**。
- **② NEVER_SET**：属性存在但全仓源码从未出现赋值写法 → 恒 null。**误报多**：`@RequestBody Entity`（Jackson 反射注入）、`BeanUtils.copyProperties`、Lombok builder 都会绕过 setter；表无 Java 写入路径时也无风险。
- **③ NULL_SRC**：出现 `setX(A.getX())` 且 A 的该列可空（接收者与来源变量类型**都从同文件声明解析后**才计入）→ 空值沿对象链传染，即本次事故形态。**仍需人工确认有无运行期守卫**。

### 扫描结果与逐条裁定

- **①：1 处** → `sys_task.task_type`（实体 `EngineeringBase` 被误标 `@TableName("sys_task")`）。**不同根因**，已列入基线待单独处理（§5）。
- **②：13 处全部为误报**：`sys_dict*`/`sys_menu`/`sys_event_config`/`SysNumberSequence` 等实体在 Java 里从未被 `new`（无 Java 写入路径，写入走 SQL/迁移）；`InventoryWarehouse` 走 `BeanUtils.copyProperties(dto, entity)`；`ProductionEquipment` 由 `@RequestBody ProductionEquipment` 由客户端 JSON 注入。
- **③：11 条候选 → 核实后仅 4 条为真雷**：

| 候选 | 裁定 | 依据 |
|---|---|---|
| `inventory_iqc_disposition_order/return_order/rework_order/scrap_order . inspection_id` | ✅ **真雷（已修）** | 来源 `quarantine.inspection_id` 恒 NULL，无兜底、无守卫；disposition 单无条件创建 → 连 RELEASE 也过不去 |
| `production_operation_execution.order_id` | ❌ 有守卫 | `QualityNcrServiceImpl.createReworkExecution` 开头即 `if (ncr.getOrderId() == null) throw new BusinessException("返工处置必须关联生产工单")` → 清晰的业务报错，不是 SQL 报错 |
| `production_operation_execution.process_order` | ❌ 数据恒非空 | `engineering_routing_item.process_order` 实测 2 行 0 空；父行由 `parent.setProcessOrder(order++)` 赋值，子行才是 null 且不进 execution 循环（`opRows` 只取父行） |
| `engineering_routing.routing_version` | ❌ 有兜底 | `EngineeringRoutingServiceImpl:74-79` 双字段互相同步兜底（DEV-769） |
| `inventory_stock_item.batch_no` | ❌ 有兜底 | `item.getBatchNo() != null ? ... : LocalDate.now().toString()` |
| `inventory_iqc_quarantine.material_id/code/name` → `inventory_transaction.material_code/name`、`inventory_alert_log.material_id` | ⚠️ 观察项 | 源列 DDL 可空、目标列必填，但实际数据恒非空（隔离行由代码从入库明细复制）。属"上下游可空性不一致"的松散设计，非当前故障，未改动 |

**判定方法学说明**：静态扫描能定位"结构上会漏列"，但**运行期守卫/兜底/数据恒非空**只有人读代码与查数据才能确认——所以 ③ 只作提示，不作门禁。

## 4. 已修（根治动作）

| 迁移 | 内容 | 验证 |
|---|---|---|
| 220（`dev-20260924-022`） | `inventory_iqc_quarantine.inspection_id` → 可空；删失效唯一键 `uk_iqc_quarantine_item_inspection`；新增 `uk_iqc_quarantine_item_lot(inbound_item_id, lot_id)` | 结构核对 ✓；事务内重放原 INSERT 成功且回滚无残留 ✓；重复键报 1062 ✓；用户实测建隔离行成功（`quarantine_id=4`，`inspection_id` NULL）✓ |
| 221（`dev-20260924-023`） | 4 张下游单 `inspection_id` → `BIGINT NULL DEFAULT NULL`（口径同 220：保留历史列、代码不改） | 收尾体检 4 列全 `YES` ✓；事务内重放 4 张单原 INSERT 全成功且回滚无残留 ✓ |

口径依据（用户 2026-09-24 拍板 A）：归一后锚点已是 `lot_id`，`inspection_id` 为历史列；放空即可，不做 DROP COLUMN（保留痕迹，待全链路确认后再独立清理）。

## 5. 未决事项（需拍板，未擅自改动）

1. **`EngineeringBase` 错映射 `sys_task`（另一类根因，实测必炸）**
   - 实体 `com/jjx/engineering/domain/entity/EngineeringBase` 标着 `@TableName("sys_task")`，字段 id/code/name/status/remark 与 `sys_task` 列不匹配。
   - 取证：`START TRANSACTION; INSERT INTO sys_task (id, code, name, status, remark) VALUES (...); ROLLBACK;` → `ERROR 1054 Unknown column 'id' in 'field list'`。即 `POST /engineering`（权限 `engineering:add`）必 500；`GET /engineering/page` 会把**任务表**当"工程记录"返回。
   - 前端检索：`jjx-web/src` 内无任何 `/engineering` 调用 → 疑似脚手架遗留的死接口。
   - 需要你定：删除该 controller/service/mapper/entity（推荐，属代码删除动作）或改造成真实实体。
2. **两条巡检门禁因"口径与业务冲突"长期为红（与本次改动无关，均为既有口径问题）**
   - `check:stock:strict` ⑤ 孤儿流水 1 处：`RM001585 / IN260924001-1 / IQC_QUARANTINE / 5.0`。`createIqcQuarantine` 直接 `transactionMapper.insert` 写了一条 IQC_QUARANTINE 流水，但没走 `InventoryStockMutationService.applyDelta`、也没建批次行 → 违反 CONVENTIONS §13「变动唯一入口」并被门禁⑤抓出。**注意：该流水只有 220 之后流程走通才第一次出现**。
   - `check:lot:strict` ② 1 处：`QL260924001` 入库明细 150 > 批合格 145（采购明细含 5 件不良）。该门禁按"明细合计 ≤ 批合格量"设计，对"采购+不良"场景疑为误报。
   - 需要你定：①隔离流水是否保留（保留则要建批次行，或把该流水类型排除出门禁⑤）；②采购含不良时门禁②的口径如何调整。

## 6. 防复发（交付物）

- `scripts/check-notnull-writes.py`：三档判据；连不上库 fail-open；`--strict` 只对 ① 判失败；`--write-baseline` 收窄基线。
- `scripts/notnull-baseline.json`：① 的存量基线（当前仅 `sys_task.task_type`，只许缩小）。
- 接入 `jjx-web/package.json`：`check:notnull` 已加入 `npm run validate`（放在数据类门禁之前，避免被后续红灯挡住不执行）。
- 本次实测：`npm run check:notnull` ✅（① 无新增）。

## 7. 复现/复核命令（只读）

```bash
# 候选列（NOT NULL 无默认值）
mysql -uroot -p123456 -N jjx_erp_db -e "SELECT t.table_name, c.column_name FROM information_schema.columns c \
 JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name \
 WHERE c.table_schema='jjx_erp_db' AND c.is_nullable='NO' AND c.column_default IS NULL \
   AND c.extra NOT LIKE '%auto_increment%' ORDER BY 1,2;"

# 同类问题复扫（三档判据）
python3 scripts/check-notnull-writes.py            # 咨询
python3 scripts/check-notnull-writes.py --strict   # 门禁口径

# 行为验证（事务内重放报错现场的原列清单，跑完即回滚，不留数据）
#   见本任务 record：/tmp/verify-022.sh、/tmp/verify-023.sh 的等价 SQL
```
