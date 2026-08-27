# JJX ERP 工装模具档案模块方案

> 版本：v1.2（已按 2026-08-12 评审修订）
> 编写：2026-08-12
> 状态：开发中（后端/前端已完成，待联调）
> 评审结论：菜单挂生产管理；已冲次数手工+报工累加都做；支持图片附件；编号自动生成且规则可配置；支持导入导出
> v1.2 修订：字段简化为 编号/名称/类型/参数(512)/设计寿命/存放位置/客户/责任人/启用日期/备注；网框/刀模差异化参数合并进“参数”文本

---

## 一、背景与定位

薄膜开关制造中，**网框（丝印网版）** 和 **刀模（模切模具）** 是可复用的工艺装备，区别于消耗型物料：

| 类别 | 本质 | 现状 |
|---|---|---|
| 油墨 | R 类原材料（消耗品） | ✅ 物料维护已覆盖，走库存/采购 |
| 网框 | 工装（可复用，需清洗/保养/报废） | ❌ 仅在样品工作台工序卡片有编号字段，无档案 |
| 刀模 | 工装（可复用，有冲切寿命） | ❌ 仅产品文件库有"模具"文件分类，无档案 |

**结论**：新建独立"工装模具档案"模块，一个表 + 类型字段（网框/刀模），共用列表/表单/接口。

**为什么不复用现有模块**：
- 物料维护（inventory_material）：消耗品库存逻辑（F/R/P/S），网框刀模不走采购领用消耗
- 设备档案（production_equipment）：字段是功率/利用率/保养计划，属性对不上

---

## 二、表结构设计

### 表名：`production_tooling`（工装模具档案）

```sql
CREATE TABLE production_tooling (
  tooling_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tooling_no       VARCHAR(50)  NOT NULL COMMENT '工装编号（网框编号/刀模编号，唯一；可自动生成）',
  tooling_name     VARCHAR(200) NOT NULL COMMENT '名称（如：3#丝印网框、主面板模切刀模）',
  tooling_type     VARCHAR(20)  NOT NULL COMMENT '类型：SCREEN=网框 DIE=刀模（静态枚举）',
  spec             VARCHAR(512) NULL COMMENT '参数（如：材质：xxx\n尺寸：xxx，长度512）',

  -- 刀模专属字段（tooling_type=DIE 时使用）
  life_limit       INT          NULL COMMENT '设计冲切寿命上限(次)',
  current_count    INT          NULL DEFAULT 0 COMMENT '已冲切次数（手工维护+报工累加）',

  -- 公共管理字段
  status           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废（静态枚举）',
  location         VARCHAR(200) NULL COMMENT '存放位置（货架/柜号）',
  department       VARCHAR(100) NULL COMMENT '使用部门',
  responsible      VARCHAR(64)  NULL COMMENT '责任人',
  customer         VARCHAR(100) NULL COMMENT '客户（定制工装所属客户）',
  enable_date      DATE         NULL COMMENT '启用日期',
  last_use_time    DATETIME     NULL COMMENT '最后使用时间',
  use_count        INT          NULL DEFAULT 0 COMMENT '累计使用次数',
  remark           VARCHAR(500) NULL COMMENT '备注',

  -- 审计字段（与全项目一致）
  del_flag         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  create_by        VARCHAR(64)  NULL COMMENT '创建人',
  create_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        VARCHAR(64)  NULL COMMENT '更新人',
  update_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (tooling_id),
  UNIQUE KEY uk_tooling_no (tooling_no),
  KEY idx_type_status (tooling_type, status),
  KEY idx_name (tooling_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装模具档案';
```

### 字段设计说明

1. **tooling_type / status 用静态枚举**（遵守《字典维护规范》：状态机/类型分支必须静态枚举，禁止动态字典）
2. **差异化参数合并进“参数”文本字段（512字）**（2026-08-12 v1.2 简化设计）：网框的目数/框尺寸/网布材质/张紧力、刀模的刀高/材质/模切尺寸等，统一以自由文本写入参数（如：材质：xxx\n尺寸：xxx），不再建结构化字段，降低维护成本
3. **设计寿命/已冲切次数仅刀模使用**，保留为公共字段（网框可不填）
4. 刀模 `current_count` 本期支持手工维护；生产工单报工时累加（见 §七.3）

---

## 三、编号规则（自动生成 + 可配置）

### 3.1 规则配置（sys_config）

新增配置项 `tooling_no_rule`，模板字符串，支持占位符：

| 占位符 | 含义 | 示例 |
|---|---|---|
| `{prefix}` | 类型前缀（SCREEN→WK，DIE→DM） | WK / DM |
| `{date}` | 日期 yyMMdd | 260812 |
| `{seq:N}` | N 位流水号（Redis 原子递增，按 类型+日期 分 key） | {seq:3} → 001 |

**默认规则**：`{prefix}{date}{seq:3}` → 网框 `WK260812001`、刀模 `DM260812001`

管理员可在"系统管理 → 参数设置"修改 `tooling_no_rule`（如改成 `{prefix}-{seq:4}` → `WK-0001`），保存时校验模板合法性（占位符格式检查 + 预览示例编号）。

### 3.2 生成方式

- 表单点击"生成编号"按钮 → 调 `GET /production/tooling/gen-no?type=SCREEN` → 返回按规则生成的编号
- 编号也可手动修改（唯一性校验），满足车间自定义习惯
- 导入时编号留空 → 自动按规则生成

---

## 四、页面设计

### 4.1 菜单位置

`生产管理 → 工装模具档案`（菜单编码：`production:tooling`）

### 4.2 列表页（`views/production/tooling/index.vue`）

- **筛选区**：类型 Tab（全部/网框/刀模）+ 关键字（编号/名称）+ 状态下拉 + 查询/重置
- **表格列**：编号 | 名称 | 类型 | 规格 | 关键属性（网框显示"200目 400×500mm"，刀模显示"刀高1.2mm 寿命5万次"）| 状态（tag）| 存放位置 | 责任人 | 操作
- **操作**：新增 / 编辑 / 状态变更（下拉：报废、启用）/ 删除（逻辑删，有使用记录时拦截）
- **工具栏按钮**：新增、导入（Excel）、导出（当前筛选条件）
- **状态 tag 色**：在库=绿、使用中=蓝、清洗/保养中=橙、维修中=红、报废=灰
- 分页 + 序号，风格对齐现有 `production/equipment` 页面

### 4.3 表单（新增/编辑弹窗）

- **类型**：radio 网框/刀模，切换时刷新专属字段区
- **编号**：必填 + "生成编号"按钮（按规则自动生成，可手动改）
- **公共字段区**：编号、名称、规格、状态、存放位置、部门、责任人、启用日期、备注
- **网框专属区**（选网框时显示）：目数、框尺寸、网布材质、张紧力
- **刀模专属区**（选刀模时显示）：刀高、材质、模切尺寸、设计寿命、已冲切次数
- **实物照片**：上传 1 张（复用通用附件上传，存 sys_attachment，bizType=`tooling`、bizId=tooling_id），列表/详情展示缩略图

---

## 五、接口设计

Base：`/production/tooling`，全部走 Sa-Token 鉴权（权限码见 §六）

| 方法 | 路径 | 说明 | 权限码 |
|---|---|---|---|
| GET | `/production/tooling/page` | 分页查询（pageNum/pageSize/type/keyword/status）| `production:tooling:list` |
| GET | `/production/tooling/{id}` | 详情（含照片附件列表）| `production:tooling:query` |
| GET | `/production/tooling/options` | 下拉选项（?type=SCREEN，仅未报废，供工艺卡片/工序引用）| 登录即可 |
| GET | `/production/tooling/gen-no` | 按规则生成编号（?type=SCREEN|DIE）| `production:tooling:add` |
| POST | `/production/tooling` | 新增（编号唯一校验）| `production:tooling:add` |
| PUT | `/production/tooling` | 修改 | `production:tooling:edit` |
| PUT | `/production/tooling/{id}/status` | 状态变更（body: {status}，报废需二次确认）| `production:tooling:changeStatus` |
| DELETE | `/production/tooling/{id}` | 删除（逻辑删，有引用记录时拦截）| `production:tooling:remove` |
| POST | `/production/tooling/import` | Excel 导入（编号空则自动生成，重复/非法行回执错误）| `production:tooling:import` |
| GET | `/production/tooling/export` | Excel 导出（按当前筛选条件）| `production:tooling:export` |

**导入模板**（Excel，复用 ExcelUtils + @ExcelColumn）：编号 | 名称(*) | 类型(网框/刀模) | 参数 | 设计寿命 | 存放位置 | 客户 | 责任人 | 启用日期 | 备注
- 类型列必填，网框/刀模二选一；编号为空自动生成；编号重复 → 该行报错跳过，导入结束返回成功行数+错误明细

**入参 DTO**：`ToolingDTO`（公共字段 + 网框/刀模专属字段平铺，按 type 校验必填）
**出参 VO**：`ToolingVO`（表字段 + `typeLabel/statusLabel` 展示名 + 照片 URL）

**新增/修改校验规则**：
- tooling_no 必填且唯一（更新时排除自身）
- tooling_type 必填，取值 SCREEN/DIE
- 网框：mesh_count 1-1000 可选；刀模：life_limit > 0 可选
- status 取值 0-4

---

## 六、权限与菜单

| 权限码 | 名称 | 类型 |
|---|---|---|
| `production:tooling:list` | 工装模具查询 | 目录+菜单 |
| `production:tooling:query` | 工装模具详情 | 按钮 |
| `production:tooling:add` | 工装模具新增 | 按钮 |
| `production:tooling:edit` | 工装模具修改 | 按钮 |
| `production:tooling:changeStatus` | 工装模具状态变更 | 按钮 |
| `production:tooling:remove` | 工装模具删除 | 按钮 |
| `production:tooling:import` | 工装模具导入 | 按钮 |
| `production:tooling:export` | 工装模具导出 | 按钮 |

写入 sys_menu（父菜单：生产管理），角色默认授予管理员；现有角色不自动授予（按需授权）。

---

## 七、与其他模块的关联

### 本期（随模块一起做）
1. 工装模具档案 CRUD + 状态管理 + 编号规则 + 图片附件
2. 导入/导出
3. 下拉选项接口（/options）供后续引用

### 二期（方案确认后可排期）
1. **样品工作台印刷工序卡片**：`inkNo` 改从物料档案（R 类油墨）下拉引用，`screenNo` 改从工装模具档案（SCREEN）下拉引用
2. **工艺路线（routing）印刷/模切工序**：增加工装引用字段（网框编号/刀模编号）
3. **生产工单执行报工**：报工时按工序关联刀模，累加 `current_count`，接近 `life_limit`（如 ≥90%）弹预警
4. **领用/归还记录**（工装台账，记录谁在什么时候领了什么工装）

---

## 八、开发范围预估

| 项 | 内容 | 预估 |
|---|---|---|
| SQL | 建表 + 菜单/权限 + sys_config 编号规则 + 导入模板 | 1h |
| 后端 | Entity/Mapper/Service/Controller/DTO/VO + 枚举 + 编号规则生成 + 导入导出 + 附件关联 | 4h |
| 前端 | 列表页 + 表单弹窗（类型切换/生成编号/照片上传）+ 导入导出按钮 + 枚举 + 路由菜单 | 3h |
| 测试 | 接口自测 + 导入导出回执 + 页面走查 | 1.5h |

合计约 **1.5 人日**。

---

## 九、已确认决策（2026-08-12 评审）

1. ✅ 菜单挂"生产管理"下
2. ✅ 刀模已冲次数：手工维护 + 报工累加（报工累加随模块一并实现，见 §七.3）
3. ✅ 支持实物照片附件（sys_attachment 复用）
4. ✅ 编号自动生成，规则可配置（sys_config `tooling_no_rule`，见 §三）
5. ✅ 支持 Excel 导入 + 导出（网框/刀模两种类型均可）
