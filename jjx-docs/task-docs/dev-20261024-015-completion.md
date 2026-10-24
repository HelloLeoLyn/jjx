# 物料主数据迁移期收尾清理执行记录

任务码：dev-20261024-015；执行者 codex；当前停在迁移后运行态冒烟失败，status=1；尚未提交或推送，不标记待审核。

## 授权与范围

用户2026-10-24续办裁定解除原卡第6条的代码依赖停手，纳入全部依赖；只在删列后运行态仍读旧列导致冒烟失败时停。服务启停由用户执行。原文留档 `/home/administrator/物料原名称原规格留档_20261024.xlsx`（已只读确认文件存在），备注【原名称】【原规格】内容不修改。

## 依赖检索与处理

1）MaterialMasterService删除显式两列写入、实体/VO/DTO删除两列，完整表单正式使用materialName；旧JSON原文字段由ignoreUnknown忽略。已有无模板物料的当前规格保留，新建规格只由模板生成。
2）MaterialMasterExcelService导入去重与导出均去掉original_name非空条件，选择不限定分类确认：未归类与已归类都必须参与去重及导出，避免漏检/漏导。重复键改用当前名称、分类及结构化属性。导出删除原文两列；导入模板用物料名称/规格，兼容旧Excel表头别名；Excel规格仅用于本次归类匹配，不写原文列或seed规格。
3）MaterialMasterAuditService保持Audit/Problem响应结构，pendingMigration为未归类、newModel为已归类，报告未归类、无模板、非法属性/材质及规格不一致。候选按category_id为空查询，原文说明改为Excel归档。
4）MaterialSourceValues保持source响应结构并返回null/null；详情显示Excel归档说明，规则归类匹配当前名称/规格，锁后仍重新匹配以防并发覆盖；不从备注回填原文。
5）旧接口守卫检查输入/已有物料的category_id，复用分类树向上查RAW/SEMI/FINISHED根；防止旧接口将新模型物料迁到旧分类绕过守卫。
6）删除旧规格Controller/Service/Mapper/实体/DTO/VO/组合工具/组件/前端API和专用测试；删除列表原名称列及parseOriginalName；基线与清库保留名单同步仅移除302双表，不执行清库。

首次检索命令：`rg -n 'original_name|original_specification|material_type|inventory_material_spec_|/inventory/material/spec|MaterialSpecFields|parseOriginalName|setOriginal|getOriginal' jjx-server/src/main/java/com/jjx/inventory jjx-server/src/main/resources jjx-web/src scripts/model-baseline.json`，命中上述依赖；原始输出存本机git忽略目录`.tmp/dev-20261024-015/dependencies-before.txt`。

清理后命令：`rg -n 'original_name|original_specification|inventory_material_spec_attr_def|inventory_material_spec_value|/inventory/material/spec|MaterialSpecFields|parseOriginalName' jjx-server/src/main jjx-web/src scripts/model-baseline.json scripts/db-clean-test-data.sh`，退出1、零命中；分类实体/配置服务的material_type检索零命中。source的驼峰字段只保留空响应契约，测试中的旧JSON字段为反例输入。已应用迁移与历史记录不修改。

## 迁移与恢复

迁移309新增依据：库账本最大308，仓内现存最大308。前查1606条物料、276条未归类，remark的BIT_XOR(CRC32(COALESCE(remark,'')))=4217071864；删列外键/生成列依赖查询为空。没有自动生成备份。

唯一入口：`bash scripts/db-migrate.sh jjx-docs/sql/migrations/309_material_retire_transition_sources.sql --yes --task dev-20261024-015 --backup jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1743.sql`。应用结果待下文实测追加。

回退点为上述用户手工全库备份。DDL不能事务回滚；迁移内注明ADD COLUMN原定义、按material_id/category_id恢复备份中原值、从备份恢复302双表CREATE/INSERT，配套回退代码；单纯ADD空列不代表数据恢复。未演练恢复、未执行回滚，回滚须另行授权。

## 实测验证

1）`mvn -q -DskipTests compile`退出0。
2）`mvn -o -q test -Dtest=MaterialMasterDataTest,MaterialClassifyTest -DargLine=-Xmx512m`：沙箱首次退出1（Mockito附加JVM失败）；宿主重跑退出0。补充回归后再跑退出0，36例（30+6）、失败/错误均0。
3）`npx vue-tsc --noEmit`退出0。
4）前端受影响回归：`timeout 120 npx vitest run src/components/inventory/__tests__/materialRegenerate.spec.ts src/components/inventory/__tests__/materialMasterFields.spec.ts --maxWorkers=1`退出0，2文件33例通过。首次额外携带不支持的--minWorkers退出1，去掉该参数后上述命令成功。
5）`bash -n scripts/db-clean-test-data.sh`退出0；未执行清库。`git diff --check`退出0。
6）迁移完整参数预览退出0，仅309待应用。首次未带--task/--backup的预览被自动审批拒绝，按审批原因补齐用户指定参数后预览成功。
7）现有运行态预查：8080后端为java -jar，未登录请求HTTP200/业务401；curl登录HTTP200/业务200、退出0，token只存私密临时文件不输出。未启停服务、未替换运行jar。

## 应用后真实结果与停手证据

`npm run check:docs`退出0，history266/modules4/task-docs55；末次`mvn -q -DskipTests compile`退出0。前查数据库视图、过程、触发器旧列/旧表依赖均为空，命令退出0。

指定db-migrate应用命令退出0，校验用户手工备份6486537B/134张表；SHA256=`912898a0e7ce58aac1b89952853662224cae44c2ad6386fa468f109a01741ef0`。成功记账并归档至`jjx-docs/sql/migrations/applied/309_material_retire_transition_sources.sql`；文件SHA256=`de01ed1297320b5c5867cab91bec4d2ac110e23bd1d46dbf77998565f71ea9ff`，账本版本309。

应用后SQL真实输出（命令退出0）：

```text
retired_columns  0
retired_tables   0
total  unclassified  remark_crc
1606   276           4217071864
```

`bash scripts/check-model-baseline.sh`退出0：未批准新表0；批准例外无缺失；基线无缺失；当前135表/基线134表（差额为既有批准例外），不扩充基线。

第一条冒烟实际命令（header文件为登录token，私密文件不提交）：

```bash
curl --noproxy '*' --connect-timeout 5 --max-time 30 -sS --fail-with-body \
  -H @.tmp/dev-20261024-015/auth-header \
  'http://127.0.0.1:8080/inventory/material/page?current=1&pageSize=1'
```

HTTP200；curl退出0；JSON业务码500；业务断言退出1。真实msg：

```text
### Error querying database. Cause: java.sql.SQLSyntaxErrorException: Unknown column 'original_name' in 'field list'
### The error may exist in com/jjx/inventory/mapper/InventoryMaterialMapper.java (best guess)
### SQL: SELECT material_id,product_id,material_code,material_name,category_id,class_source,class_confirmed,specification,material_grade,model,color,brand,search_keywords,size,original_name,original_specification,material_meta,material_form,attribute_json,template_id,thickness,width_size,is_customer_supplied,is_sample,unit,unit_conv,unit_alt,batch_control,shelf_life,expiry_alert_days,safe_stock,max_stock,reorder_point,standard_price,cost_price,lead_time,supplier_id,supplier_name,default_warehouse_id,default_location_id,status,process_group,create_by,create_time,update_by,update_time,remark FROM inventory_material ORDER BY create_time DESC,material_id DESC LIMIT ?
### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'original_name' in 'field list'
; bad SQL grammar []
```

事实：当前java -jar进程仍发出包含两列原文的SELECT；仓内新代码检索零命中且编译通过。运行jar未部署本卡代码。按用户明确停手条件停止后续动作：详情、BOM选料、库存选料、导入导出以及传旧字段反例均未运行，不宣称五路径通过；提交/push未执行，status保留1，completed_time不设置。

解除条件：用户完成新版本构建部署与服务重启后，续办本卡五路径及旧字段反例冒烟。agent未启停服务、未覆盖运行jar、未自动回滚数据库。临时登录token和原始登录响应清除；命令/结果关键证据已转录本文。

