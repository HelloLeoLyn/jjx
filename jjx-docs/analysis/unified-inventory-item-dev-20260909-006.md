# 统一库存物品身份实施记录

- 任务：1673，关联 1668、1669、1670、1671、1674
- 方案：A（材料、产品业务档案分立，库存域共用 `inventory_item` 身份与一套库存引擎）
- 数据前提：用户确认无历史数据包袱

## 已实施

1. 新建 `inventory_item`，以 `(item_type, source_id)` 绑定材料或产品档案。
2. 库存汇总、批次、入库、出库、流水、盘点、调拨和销售预留增加 `inventory_item_id`。
3. 完工入库直接记入 PRODUCT 库存身份，不再生成 F 类镜像物料，不再双写 `product_stock`。
4. 销售发货、成品预留、缺料计算统一查询/扣减库存批次。
5. 删除 `ProductStock` 实体、Mapper、Service、Controller 及 `product_stock` 表，删除产品镜像物料。
6. 安全库存阈值改为从 `inventory_item` 读取，材料与产品共用一条预警链路。

## 数据及验证

- 全库备份：`jjx-docs/sql/backups/jjx_erp_db_backup_20260909-2248_before-unified-inventory-item.sql`
- 迁移：73（统一身份）、74（引擎切换）、75（旧账本清理）
- 隔离库全链路迁移成功；正式库执行成功
- 迁移后：MATERIAL 1535、PRODUCT 1；库存汇总/批次缺失身份均为 0
- `mvn -DskipTests clean compile` 通过（847 个源文件）
- 18080 端口独立启动成功，登录与库存列表接口验证通过；旧产品库存路由已移除

## 注意

原 8080 端口仍由 IDE 中的旧进程占用，需在 IDE 重启后才会使用新代码；数据库结构已完成切换。
