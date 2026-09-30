# 测试数据清理保留项复核（dev-20260930-012）

核对日期：2026-09-30。范围是清理脚本第 12 节及覆盖率白名单中的保留表。主数据、权限/配置和产品档案仍按原策略保留；本次逐项复核容易被误当作“脏表”的遗留项和运行态表。

| 表 | 当前行数 | 处理 | 依据 |
|---|---:|---|---|
| `engineering_bom_backup_20260809` | 0 | 迁移 241 退役 | 无运行代码引用、无外键依赖；已无数据 |
| `sys_event_config_bak_20260814` | 0 | 迁移 241 退役 | 无运行代码引用、无外键依赖；已无数据 |
| `archive_production_quality_inspection` | 0 | 保留 | `ProductServiceImpl` 的产品删除校验仍查询此表 |
| `archive_production_quality_inspection_item` | 0 | 保留 | 归档明细子表；先保留表结构，归档主表仍被业务校验引用 |
| `sales_order_review` | 0 | 保留 | 销售评审打印页与打印留痕仍传入该业务类型；不能仅凭当前空表删除 |
| `quality_sampling_plan` | 8 | 暂保留 | 业务配置已迁至 `sys_config.quality.sampling_plan`（迁移 219），但 8 行旧数据尚未逐行比对，不做清空或删表 |
| `sys_event_last_payload` | 43 | 保留 | 事件配置页使用最近事件 payload 预览 |
| `sys_event_var` | 409 | 保留 | 事件变量累积注册表；已有用户确认的保留决议 |

当前覆盖率白名单同步移除两张已退役表，迁移 241 执行后清理脚本的全库覆盖校验不会把它们误报为未归属表。清理测试数据时，`inventory_item` 仍按已登记的孤儿 PRODUCT 条件清理；本次没有执行清库。

验证证据：`ops.schema.applied` 最大值为 240；两张退役表均为 0 行；数据库外键目录未发现对它们的引用。迁移前需按仓库规则确认用户手工生成的当日全库备份。
