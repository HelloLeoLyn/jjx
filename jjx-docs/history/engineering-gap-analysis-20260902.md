# JJX ERP 工程模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：代码实扫 + 数据库实查

---

## 1. 现状盘点（实证）

### 1.1 表与行数

| 表 | 行数 | 判定 |
|---|---|---|
| engineering_standard_process | 49 | 真实数据（标准工序库） |
| engineering_bom / engineering_bom_item | 1 / 3 | 测试数据 |
| engineering_routing / item | 1 / 3 | 测试数据 |
| engineering_film | 0 | 空转（薄膜参数主数据） |
| jjx_screen_master（网版） | 4635 | 真实数据（2026-09-01 Excel 导入 4502 条+维护新增） |
| engineering_base | 0 | 空转（工程基础表？代码引用待核） |
| product_config_model/option、instance | 0/0/0 | 见 product 报告 |

### 1.2 后端 Controller（engineering 包 3 + product 包 5 个工程相关）

BomController（/engineering/bom：CRUD+submit/approve/reject/setDefault/calculateCost/copy）/ EngineeringRoutingController（工艺路线 CRUD+审批）/ EngineeringRoutingItemController / ProductStandardProcessController / EngineeringFilmController / EngineeringController（engineering_bom 变更）/ ScreenMasterController（2026-09-01 新建：网版 CRUD+suggest）/ ConfigModelController（产品配置模型）。
打样相关在 sales 包（SampleOrder/SampleTransfer + sales_sample_* 子表）。

### 1.3 前端与菜单

工程管理(90)：BOM 管理/工艺路线/标准工序/打样平台(239)/薄膜管理(92)/网版管理(314, 2026-09-01 新增)/产品配置模型(93)。component 全部指向 views/product 或 views/engineering 真实文件 ✅。views/engineering 下 config/design/film/sample-workbench + views/product 下 bom/route/standard-process/drawing（drawing 页面存在但 sys_menu 无图纸菜单——**工程图纸入口缺失**，见缺口表）。

### 1.4 资料转移链路（样品→量产档案）

sales_sample_transfer（0 行）→ SampleTransferController preview/confirm/remind（2026-08 交付）→ 生成 engineering_bom/routing 草稿（approve_status=1）→ 审批发布。代码闭环 ✅（2026-08 验收：版本化 BOM/路线+is_current）。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 产品→BOM→工艺路线 建档/审批/发布 | ✅通（approve 流+版本） |
| 样品资料转移→BOM/路线草稿→审核发布 | ✅通（打样转标准验收） |
| 打样工作台（工序/轮次/材料） | ✅通（1225 后印刷输入联想+网版主数据） |
| BOM 成本计算（calculateCost） | ✅通（端点存在，数据 0 行未实测） |
| 工程变更 ECN | ❌缺（1248 已登记 P2：engineering_change 零代码） |
| 薄膜/菲林参数 | ⚠️半通：film 表 0 行+FilmController CRUD 存在，业务未用 |

## 3. 与行业基准对照

覆盖：BOM✅ 工艺路线✅ 标准工序✅ 打样✅ 转量产资料转移✅ 网版✅。
缺失：ECN 工程变更（1248）；图纸/设计文件库（drawing 页无菜单入口+无图纸管理后端？需核）；工艺版本生效控制（换版审批已有 is_current，但"旧版本被引用工单"约束无）。

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 业务缺失 | ECN 工程变更（1248） | 全库零 engineering_change 代码 | 高 | P2 设计后实现 |
| 无入口 | 图纸页 views/product/drawing | 页面存在无菜单+后端图纸上传？ | 中 | 核实后挂菜单或补后端 |
| 空转 | engineering_film 0 行 | 薄膜参数未使用 | 低 | 确认业务是否需要 |
| 待核 | engineering_base 表 | 0 行+代码引用待核 | 低 | 死表则冻结 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P2 | 1248 ECN | 设计改版无流程=生产现场无变更依据 |
| P2 | 图纸库入口 | 打样/生产依赖图纸 |
| P3 | film/base 清理 | 低价值 |
