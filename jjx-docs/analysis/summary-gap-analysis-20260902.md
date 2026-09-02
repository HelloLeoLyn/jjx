# JJX ERP 全系统实证差距分析——总矩阵

- 报告日期：2026-09-02
- 8 份分报告：sales/product/engineering/inventory/purchase/production/quality/system-gap-analysis-20260902.md
- 方法：全部结论基于代码实扫+DB 实查，详见分报告

---

## 1. 模块 × 差距矩阵

| 模块 | 业务表数 | 有数据表 | Controller | CRUD 缺口 | 空转事件 | 断链 | 死代码/孤儿 | 行业环节缺口 |
|---|---|---|---|---|---|---|---|---|
| 销售 sales | 20 | 2（customer 11/inquiry 1） | 12 | 2（发票无PUT、收款无PUT/DELETE） | ~6（含 order.delivering 未注册） | 0（主链代码闭环，0行未实测） | exportPdf 空壳 | 合同（已删表，确认书替代） |
| 产品 product | 7 | 2（product 3/category 3） | 10 | 0 | - | 0 | product_stock 疑义表 | 多版本生效时间 |
| 工程 engineering | 9 | 2（std_process 49/screen 4635） | 8 | 0 | - | 1（ECN 1248 缺失） | film 0行空转 | ECN 变更流程 |
| 库存 inventory | 17 | 2（material 1536/warehouse 2） | 15 | 0 | ~16（主数据/联动通知） | 0 | export-pdf×2 | 批次台账/成本核算 |
| 采购 purchase | 8 | 1（supplier 54） | 7 | 0 | ~16 待全量核对 | 1（退货无单据仅扣库存） | report 孤儿页 | 退货单据/应付对账 |
| 生产 production | 13 | 5（tooling 7274 等少量） | 14 | 0 | 3（work-report，1246） | 0（production.completed 已核实通） | trace_log 死表、dispatch 死菜单 | 报工通知/工票编号 |
| 质量 quality | 4 | 1（registry 100） | 3 | 0 | 0 | 1（归档 1186） | print_log 0行 | 质量记录回传归档 |
| 系统 system | 20+ | 10+（menu 243/role 22…） | 16 | 0 | ~25（全系统合计） | 1（sys_notification 0行=事件断链证据） | pdfConfig 孤儿页 | DB 备份/文控中心 |

## 2. 各模块 TOP 3 缺口

- 销售：①发票/收款单无 update ②17 表 0 行未实测（1232）③事件空转清理
- 产品：①product_stock 语义与红线冲突 ②instance/config 远期空转
- 工程：①ECN 缺失（1248）②图纸库无入口 ③film 空转
- 库存：①safe_stock 全 0（预警前提缺）②空转事件 16 条 ③批次/成本
- 采购：①事件配置与代码全量未核对 ②退货单据化 ③report 孤儿页
- 生产：①1246 报工事件 ②dispatch 死菜单 ③报工 DECIMAL 精度遗留
- 质量：①1186 归档 ②复检历史明细
- 系统：①DB 备份缺失 ②空转事件统一清理 ③文控中心未成型

## 3. 全系统补全路线建议

### 阶段 P0（先让业务闭环能算账——预计 1-2 周）
- sales：发票/收款单 update 端点（财务单据可纠正）；1232 演示数据跑通主链（前置证伪）
- system：DB 备份补全（无备份=系统性风险）
- production：1246 报工事件接入（唯一生产断链）

### 阶段 P1（质量/成本可见）
- inventory：safe_stock 主数据维护（预警生效前提）
- purchase：事件配置全量核对+退货单据化
- engineering：1248 ECN（变更管理质量刚需）
- quality：1186 归档（2 年保存期限合规）

### 阶段 P2（增强）
- 空转事件统一清理（~25 条）
- dispatch 死菜单/trace_log 死表/product_stock 疑义清理
- 文档管理 316 收敛（文件+模板+归档一体）
- 报工 DECIMAL 精度复核

### 阶段 P3（远期）
- 批次台账/库存成本核算/SPC/质量成本/应付对账
- 产品配置模型 instance/config 启用
- 测试工作台优化（070/071 已登记）

## 4. 关键红线与陷阱提醒

- 成品物料≠产品：product_stock 需定案，勿双口径
- 旧派工模型勿恢复：dispatch 菜单只做清理不迁移
- 表 0 行≠无功能：dev 库清洗过（00_clean_test_data），判断以代码为主（分报告均已区分"空转/待实测"）
- 事件"配置有≠通"：必须代码 fire 对照（本分析已逐模块做，库存 stock.* 走手动 fire 属正常）
- 历史备份表（product_backup_20260809/sys_event_config_bak_20260814/engineering_bom_backup_20260809）仅历史资料，不代表当前架构
