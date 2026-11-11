# 退役遗留两表 inventory_material_spec_attr_def / inventory_material_spec_value

任务码：dev-20261111-011（OpenClaw/dahuang）
日期：2026-11-11
状态：阶段一（提案 + 迁移脚本已写，**未执行 DDL**）
决策依据：用户 2026-11-11 回复“按你的建议来”，即选定方案 A（退役）而非方案 B（基线登记）

## 起因

普通 `git push` 被 `scripts/check-model-baseline.sh` 拒绝，退出码 1：

```text
✘ 未批准新表：
  - inventory_material_spec_attr_def
  - inventory_material_spec_value
```

该门禁要求任何新表先走“提案 → 拍板 → 登记 approvedNewTables → 同步基线”，否则 pre-push 一律拦截。

## 已核实的事实（只读）

1）两张表与“新表”语义不符，它们是历史遗留：
   - `inventory_material_spec_attr_def` 表注释即为「deprecated 已废弃：属性真源为 inventory_material.attribute_json（dev-20261024-007）」
   - `inventory_material_spec_value` 行数 0
2）代码零引用：在 `jjx-server` / `jjx-web` 中搜 `java/xml/vue/ts`，两表名 0 命中
3）无外键依赖：`information_schema.KEY_COLUMN_USAGE` 中无任何表引用它们
4）数据规模：`inventory_material_spec_attr_def` 27 行（历史属性定义），`inventory_material_spec_value` 0 行
5）归档已导出（只读导出，不改库）：
   - 文件：`jjx-docs/sql/backups/inventory_material_spec_attr_def_retire_20261111-1724.sql`
   - sha256：`b14bbcf754e1c6fd29a6ec5e1b6cc4d896c5a584b8d50da11eacaa5885455b6d`
   - 大小 9313 字节

## 方案比较

A）退役（本提案推荐并已按此出脚本）
   - `jjx-docs/sql/migrations/358_retire_material_spec_attr_tables.sql`，幂等，重复执行安全
   - 迁移内置行数守卫：attr_def 非 27 行或 value 非 0 行时主动报错中止，不会删超出归档范围的数据
   - 门禁恢复：执行后 `bash scripts/check-model-baseline.sh` 应退出 0，不需要动 `approvedNewTables`
   - 代价：不可逆（依赖归档文件恢复）

B）基线登记（未采用）
   - 把两表写入 `scripts/model-baseline.json` 的 `approvedNewTables`
   - 不动库，但等于承认两张废弃表长期存在，且掩盖“新表须提案”的门禁语义
   - 后续仍要有人清理，属于把问题推迟

## 执行步骤（尚未执行）

1）用户手工做当天全库备份（脚本不会自动生成备份）：
   `bash scripts/db-backup.sh --tag before-358`
2）执行迁移（账本 + applied 归档由脚本负责）：
   `bash scripts/db-migrate.sh 358_retire_material_spec_attr_tables.sql --yes --task dev-20261111-011 --backup jjx-docs/sql/backups/<备份文件>.sql`
3）复核：
   - `bash scripts/check-model-baseline.sh` → 退出码 0
   - 两张表不存在；`inventory_material.attribute_json` 相关读写不受影响（代码零引用）
4）push 恢复后再补推 006 / 011 的本地提交

## 回退

1）用归档文件恢复两表：
   `mysql -h127.0.0.1 -uroot -p jjx_erp_db < jjx-docs/sql/backups/inventory_material_spec_attr_def_retire_20261111-1724.sql`
2）回退后 pre-push 会重新被同一门禁拦截，需另行决定口径（不得只恢复一半）

## 风险与边界

1）删表不可逆：只在确认“确认没有历史报表依赖这两张表”后执行
2）不动 `approvedNewTables`，不使用 `git push --no-verify`
3）不改库存写入路径、不改 `inventory_material` 数据；本次只是删两张废弃表
