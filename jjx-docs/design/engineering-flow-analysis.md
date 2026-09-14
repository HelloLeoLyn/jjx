# 🛠️ 工程模块完整业务分析

> 基于实际代码逐条梳理，颗粒度到每个端点、状态跳转、校验规则、交叉联动。
>
> 代码路径: `jjx-server/src/main/java/com/jjx/engineering/` + `com.jjx/product/`
>
> 最后更新: 2026-08-01

---

## 一、整体模块结构

工程模块横跨两个包：
- `com.jjx.engineering`：BOM/工艺路线/标准工序/菲林/工程台账
- `com.jjx.product`：产品/BOM明细/配置模型（菜单在工程下）

| Controller | 路径 | 职责 |
|-----------|------|------|
| `BomController` | `/engineering/bom` | BOM 查询/审核 |
| `RoutingController` | `/engineering/routing` | 工艺路线查询 |
| `StandardProcessController` | `/engineering/standard-process` | 标准工序库 |
| `FilmController` | `/engineering/film` | 菲林管理 |
| `EngineeringController` | `/engineering` | 工程台账 |
| `ConfigModelController` | `/engineering/config` | 产品配置模型（DEV-453 已补全 CRUD）|
| `ProductController` | `/product` | 产品主数据 |

---

## 二、A — 产品（Product）

### 数据表

`product` + `product_category`

**字段**: productId, productCode(MBS前缀), productName, categoryId, productType(standard), basePrice, costPrice, productStatus, unit, createBy/Time, updateBy/Time

### 产品状态（product_status）
```
1 草稿 → 2 待审核 → 3 审核中 → 4 已拒绝 → 5 待发布 → 6 已发布(RELEASED) → 7 停用
```

### 关键校验（业务约束）
- **已发布(6)才可下单**：DEV-313/350 已实现，非 RELEASED 产品不可选
- **发布前校验 BOM+路线已审批**：DEV-344（待验证）
- ⚠️ 产品状态 2 待审核 → 6 已发布的审核流程（产品审批）事件已配（product.approved）

---

## 三、B — BOM（工程BOM）

### 数据表

`engineering_bom`（主表）+ `engineering_bom_item`（明细）

**主表字段**: bomId, bomCode(BOM-xxx-V1), bomName, productId, bomVersion, bomType(manufacturing), isCurrent(1当前), effectiveDate, expiryDate, approveStatus(1草稿/2待审批/3已批准/4已拒绝), approveBy/Time/Remark, createBy/Time

**明细字段**: itemId, bomId, materialId/Code/Name, quantity, unit, lossRate(损耗率%), moduleQty, baseQty, minIssueQty, widthMm, lengthMm, **layer**(面板/线路/间隔/背胶层结构), positionNo, sourceType(buy自制/外购), substituteJson(替代料), itemOrder, specification

### 端点清单

```
GET  /engineering/bom/page                    — BOM 列表（含明细）
PUT  /engineering/bom/approve/{bomId}         — 审核通过 ⚠️ 空壳（返回success不做事）
PUT  /engineering/bom/reject/{bomId}          — 审核驳回 ⚠️ 空壳
```

### ⚠️ 重大缺口
- **BOM 只有查询+审核接口**，无创建/编辑接口！
- approve/reject 是**空壳**（不更新 approve_status）——DEV-347（PENDING中间态）待做
- BOM 明细实体是 `ProductBomItem`（product 模块），表名 engineering_bom_item——跨模块命名混乱
- **打样传承**：DEV-457 已实现"样品转量产→自动生成BOM草稿"

---

## 四、C — 工艺路线（Routing）

### 数据表

`engineering_routing`（主表）+ `engineering_routing_item`（明细）

**主表字段**: routingId, routingCode(RTE-xxx-V1), routingName, productId, productCode/Name, routingVersion, isCurrent, approve_status(1草稿/2待审批/3已批准/4已拒绝), totalLaborHours, totalMachineHours, processCount, createBy/Time

**明细字段**: detailId, routingId, processId, processOrder(工序顺序), customLaborHours, customMachineHours, customProcessParams(JSON), groupId/Order/Name(工序组), processCategory, description

### 端点清单

```
GET  /engineering/routing/page                — 工艺路线列表
```

### ⚠️ 缺口
- 只有查询，无创建/编辑/审核接口
- 实体 `Routing.status` 映射 `approve_status` 列（DEV-470 已修复 @TableField）
- 无打样传承（转量产只生成 BOM 草稿，路线需手动建——DEV-457 只提醒）

---

## 五、D — 标准工序库（StandardProcess）

### 数据表

`engineering_standard_process`

**字段**: processId, processCode(PRT/CUT/LAM/SMT/ASM/TST/PKG), processName(印刷/冲切/贴合/SMT贴片/装配/测试/包装), processType(PRINTING等), processCategory(M制造/Q质检), standardLaborHours, standardMachineHours, processParamTemplate(JSON), skillRequirement, equipmentType, qualityStandard, isEnabled, displayOrder

### 端点清单

```
GET  /engineering/standard-process/page       — 分页
GET  /engineering/standard-process/enabled    — 启用的工序
```

### 用途
- 工艺路线明细引用 processId（标准工序 + 自定义工时）
- 测试数据集已建 7 道标准工序（DEV-463）

---

## 六、E — 菲林（Film）

### 数据表

`engineering_film`

### 端点清单

```
GET  /engineering/film/page                   — 菲林列表
```

### 前端（DEV-449 已做）
- `views/product/film/index.vue`：菲林列表/审批/版本/下发
- 菜单 92 指向

---

## 七、F — 产品配置模型（ConfigModel）

### 数据表

`product_config_model`（主表）+ `product_config_option`（选项）

**主表字段**: modelId, modelCode, modelName, productId, isDefault(0/1), status(1启用/0停用), remark

**选项字段**: optionId, modelId, optionCode, optionName, optionType(input/select/checkbox), valueJson, dependsOn, excludes, isRequired, sortOrder

### 端点清单（DEV-453 已补全）

```
GET    /engineering/config                    — 列表
GET    /engineering/config/{modelId}          — 详情（含选项）
POST   /engineering/config                    — 创建（含选项）
PUT    /engineering/config                    — 更新（选项全量替换）
DELETE /engineering/config/{modelId}          — 删除（含选项）
PUT    /engineering/config/{modelId}/default  — 设默认（同产品互斥）
PUT    /engineering/config/{modelId}/status/{status} — 启停用
```

---

## 八、G — 工程台账（Engineering）

```
GET    /engineering/page                      — 台账列表
GET    /engineering/{id}                      — 详情
DELETE /engineering/{id}                      — 删除
```

---

## 九、状态流转总图

```
产品(草稿→待审核→已发布→停用)
        │ 发布前校验: BOM+路线已审批
        ▼
BOM:   草稿(1) → 待审批(2) → 已批准(3)   ← ⚠️ 审核空壳，需DEV-347
                          → 已拒绝(4)
        ▲
        │ DEV-457: 样品转量产自动生成BOM草稿
        │
工艺路线: 草稿(1) → 待审批(2) → 已批准(3)  ← ⚠️ 只有查询
        │
标准工序库: 印刷→冲切→贴合→SMT→装配→测试→包装（7道）
        │
配置模型: 草稿 → 启用(默认/非默认)          ← DEV-453 完成
```

## 十、与其他模块联动

| 联动 | 方式 | 状态 |
|---|---|---|
| 样品→BOM | 转量产自动生成BOM草稿（DEV-457）| ✅ |
| 产品→生产 | 工单记录 BOM/路线 ID（DEV-470 校验）| ✅ |
| 工程→事件 | product.* 25 个事件（工程[9]）| ✅ |
| BOM 审核 | approve/reject 空壳 | ⚠️ 待修 |

## 十一、已知问题

1. **BOM 无创建/编辑接口**，只有查询+审核（审核还是空壳）
2. **工艺路线无创建/审核接口**，只能 SQL 建
3. **BOM 明细实体命名混乱**（ProductBomItem 映射 engineering_bom_item）
4. **菲林接口单薄**（只有 page）
