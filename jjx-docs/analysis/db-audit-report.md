# 数据库表结构审计报告

> 日期：2026-07-29
> 数据库：jjx_erp_db（66表 + 3视图）
> 数据状态：几乎所有业务表 0 行，仅系统表有数据

---

## 一、已废弃可删除的表（3张）

这些表在 #114-118 任务中已被 `sys_task` 替代，数据已迁移。

| 表名 | 替代表 | 说明 |
|------|--------|------|
| `engineering_base` | `sys_task` | 工程基础表，旧设计 |
| `engineering_design_task` | `sys_task` | 旧工程设计任务表 |
| `kanban_task` | `sys_task` | 旧看板任务表 |

→ **建议：确认数据已迁移后 DROP**

---

## 二、孤立无前端/无路由的表（3张）

有完整表结构，但前端没有对应页面、路由、菜单。

| 表名 | 表完整度 | 建议 |
|------|---------|------|
| `sales_contract` | 25字段，含审批/附件/条款 | ⚠️ 如有合同管理需求则保留，否则可删 |
| `sales_performance` | 15字段，销售业绩统计 | ⚠️ 可删（报表模块可直接算，无需单独表） |
| `sales_return` | 28字段，完整退货流程 | ⚠️ 退货功能测试计划里有但未实现。保留，以后用 |

→ **建议：`sales_performance` 可删，`sales_contract` 和 `sales_return` 保留但标注未启用**

---

## 三、未使用的产品配置表（3张）

| 表名 | 行数 | 说明 |
|------|------|------|
| `product_config_model` | 0 | 产品配置模型，从未用过 |
| `product_config_option` | 0 | 产品配置选项 |
| `product_instance` | 0 | 产品实例（序列号追踪？） |

→ **建议：`product_config_model` + `product_config_option` 可删，`product_instance` 保留（以后做序列号追溯）**

---

## 四、门户CMS表（4张，属于企业官网项目）

| 表名 | 说明 |
|------|------|
| `portal_inquiry` | 门户网站客户询价（非ERP销售询价） |
| `portal_language_config` | 多语言配置 |
| `portal_page_content` | 页面内容 |
| `portal_product_display` | 产品展示 |

→ **建议：标记为`企业官网项目`，不删，但做数据隔离**

---

## 五、`sales_log` 旧日志表

- 已通过 #110-113 将日志合并到 `sys_oper_log`
- `sales_log` 表仍然存在
- **建议：确认 `sys_oper_log` 已有对应数据后，标记 `sales_log` 废弃**

---

## 六、设计问题

| 问题 | 说明 |
|------|------|
| `sales_order` 双状态 | `order_status`（标准单）+ `sample_status`（样品单），同一个表两个状态机，字段多且复杂 |
| `product.spec_json` | JSON字段存储规格，但 `sales_order_product.specification` 是单独文本字段，两套规格方案 |
| `sys_notification` 字段发散 | 不同通知类型共用一张表，大量NULL字段 |
| 视图无注释 | `v_inventory_transaction` / `v_material_latest_inquiry` / `v_user_permissions` 功能不明 |

---

## 七、总体建议

```
清理等级:
  🟢 可直接删: engineering_base, engineering_design_task, kanban_task, 
               product_config_model, product_config_option
  🟡 确认后删: sales_performance, sales_log
  🔵 保留不动: sales_contract, sales_return, product_instance, 门户4表
```

你要直接清掉 🟢 那几个，还是先跟我说一下哪些要留？剩下 #146 API测试修复，审计搞完再干还是并行？