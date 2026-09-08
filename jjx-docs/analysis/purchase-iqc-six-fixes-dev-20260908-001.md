# 采购至 IQC 六项问题修复方案与验证记录

任务范围：`dev-20260908-001` ～ `dev-20260908-006`

关联采购单：`PO202609070001`
日期：2026-09-08

## 修复方案与结果

| 任务 | 问题 | 处理方案 | 结果 |
| --- | --- | --- | --- |
| dev-20260908-001 | IQC 单项驳回后，前端按单项编辑、后端要求整张入库单，无法重提 | 从检验记录进入重检时加载整张入库单；已审核通过项锁定并由后端跳过，其余项目整单提交 | 已修复 |
| dev-20260908-002 | 未录入检测项目也能提交，且不合格原因可为空 | 前后端双重校验检测项目、实测结果和项目判定；不合格数量大于 0 时强制填写原因，并校验数量与总判定一致 | 已修复 |
| dev-20260908-003 | 审核通过无法填写审核意见 | 审核操作改为可选意见输入框，提交时随审核请求留痕 | 已修复 |
| dev-20260908-004 | IQC 隔离台账无可见入口 | 新增幂等菜单迁移，并继承 IQC 菜单的角色授权 | 已修复，菜单 ID 333 |
| dev-20260908-005 | 采购明细允许零价 | 采购单新增、修改在前端即时拦截，后端再次强制单价大于 0 | 已修复 |
| dev-20260908-006 | “已批准”容易被理解为收货自动审批 | 采购列表列名调整为“采购审批”，与收货/入库进度分开表达 | 已修复 |

## 数据库变更

- 变更前全库备份：`jjx-docs/sql/backups/jjx_erp_db_backup_20260908-0034_before-iqc-fix.sql`
- 菜单迁移：`jjx-docs/sql/migrations/67_add_iqc_quarantine_menu.sql`
- 迁移已执行，隔离台账菜单已创建并复制原 IQC 菜单角色授权。
- 未对 `PO202609070001` 执行修复后的接口写入回归，保留现有业务测试数据。

## 验证结果

- 后端编译与打包：通过（`mvn -DskipTests package`）。
- 前端类型检查：通过（`vue-tsc --noEmit`）。
- 新增校验单测：3/3 通过（零价采购、IQC 检测依据必填及合法判定）。
- 状态枚举检查：本次改动未新增状态魔法值；命令仍报告生产订单页面已有的 3 处历史违规，未扩大 baseline。
- 全量后端测试：共 126 项，96 项通过、3 项跳过、30 项错误；错误集中在既有测试构造参数失配、旧用例空值以及 Java 25 下 Mockito/ByteBuddy 兼容性，与本次六项修改无直接关联。

## 主要改动文件

- `jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java`
- `jjx-server/src/main/java/com/jjx/purchase/service/impl/PurchaseOrderServiceImpl.java`
- `jjx-server/src/test/java/com/jjx/inventory/PurchaseIqcValidationTest.java`
- `jjx-web/src/views/inventory/iqc/index.vue`
- `jjx-web/src/views/inventory/inbound/components/IqcReviewDialog.vue`
- `jjx-web/src/views/purchase/order/components/OrderFormDialog.vue`
- `jjx-web/src/views/purchase/order/index.vue`
- `jjx-docs/sql/migrations/67_add_iqc_quarantine_menu.sql`
