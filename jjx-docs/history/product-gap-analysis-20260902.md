# JJX ERP 产品模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：代码实扫 + 数据库实查
- 红线：产品=档案概念（product 表，BOM/工艺路线挂它）；库存只有物料（inventory_material），产品不入库

---

## 1. 现状盘点（实证）

### 1.1 表与行数

| 表 | 行数 | 判定 |
|---|---|---|
| product | 3 | 真实数据（产品档案） |
| product_category | 3 | 真实数据 |
| engineering_standard_process | 49 | 见工程报告 |
| product_instance | 0 | 空转（产品实例——客户化实例） |
| product_config_model / product_config_option | 0 / 0 | 空转（配置模型，views/engineering/config 页面存在） |
| product_stock | 0 | 空转/疑义（**红线：产品不入库**——此表语义与红线冲突，需确认是死表还是产品级汇总视图） |
| product_backup_20260809 | 0 | 历史备份表 |

### 1.2 后端 Controller（product 包 10 个，工程相关 5 个已在工程报告）

ProductController（/product：CRUD+submit/approve/reject/release/obsolete/cancel——全生命周期审批）/ ProductCategoryController / ProductInstanceController / ProductConfigController / ProductEnumController / ProductStockController（product_stock 读写）/ ProductStandardProcessController 等。

### 1.3 前端与菜单

产品管理目录(6)：产品列表/分类/实例/添加产品/编辑产品/产品配置模型，component 指向 views/product 真实文件 ✅。产品详情挂 BOM/工艺路线/资料库（ProductFileLibrary）。

### 1.4 主数据变更追溯

产品/BOM/工艺路线各自 per-entity 流水（bizType='product'/'bom'/'routing'，2026-08-29 master-data-trace 实施）——产品换 BOM/工艺路线在流水以 diff 展示 ✅。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 产品建档→审核→发布→停产 | ✅通（状态机 submit/approve/release/obsolete） |
| 产品↔BOM/工艺路线关联 | ✅通（current_bom_id/current_route_id） |
| 产品→订单/报价引用 | ✅通（product options 接口） |
| 产品实例/配置模型 | ⚠️半通：表 0 行+页面存在，业务链路未走（可能为远期可配置产品功能） |
| product_stock | ❌疑义：与红线"产品不入库"冲突——若为库存汇总冗余应删，若产品级视图应基于 inventory_stock 聚合 |

## 3. 与行业基准对照

覆盖：产品档案✅ 分类✅ 生命周期审批✅ 变更追溯✅。
缺失/疑义：产品 BOM 多版本生效时间管理（当前 is_current 单一指针）；product_stock 语义未定（红线冲突）。

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 疑义表 | product_stock | 红线产品不入库，此表与 inventory_stock 关系未明 | 中 | 核实读写方（ProductStockController）——无业务引用则冻结 |
| 空转 | product_instance / config_model | 0 行+无业务链路 | 低 | 远期功能标注 |
| 备份表 | product_backup_20260809 | 历史 | 低 | 归档清理 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P2 | product_stock 语义定案 | 红线冲突项，避免双口径库存 |
| P3 | instance/config_model | 远期 |
