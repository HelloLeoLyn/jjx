# FQC 分批检验与返工闭环实施记录

## 任务

- 任务码：`dev-20260909-011`
- 方案依据：`fqc-close-loop-dev-20260909-005.md`
- 目标：不新增业务表，支持 FQC 合格/不合格数量同时判定、不良余量分批处置、独立返工执行及再次 FQC。

## 实施内容

1. `production_quality_inspection` 增加 `remaining_fail_qty`，首次判定时按不合格数量初始化，返工或报废处置时原子递减。
2. `production_operation_execution` 增加执行类型和来源质检单；内部返工创建独立 `REWORK` 执行及首个生产任务，保留原执行事实。
3. 返工执行完成后自动创建关联上次检验的 FQC；合格数量累计回写生产订单并按质检单幂等触发成品入库。
4. 生产订单完工门禁检查全部 FQC：不得存在待检记录或未处置不良余量，累计合格成品数量须达到计划数量。
5. 质检页面展示待处置不良量，并提供分批返工、报废入口；新增动作统一使用质量处置枚举。

## 数据安全

- 迁移脚本：`jjx-docs/sql/migrations/76_fqc_rework_close_loop.sql`
- 迁移前备份：`jjx-docs/sql/backups/jjx_erp_db_backup_20260909-2347_before-fqc-close-loop.sql`
- 备份包含 98 个 `CREATE TABLE` 语句。
- 本次恢复只审查脚本，未执行数据库迁移。

## 验证

- `npm run validate`：通过。
- `npm run check:status-enums`：通过，存量基线 164 处，新增 0 处。
- `mvn -DskipTests compile`：通过，编译 849 个源文件。
- `git diff --check`：通过。

## 上线注意

上线前仍须按规范重新确认备份校验值，再执行 76 号迁移；迁移完成后应覆盖混合合格/不合格、分批返工、部分报废、并发重复处置和返工复检入库场景。
