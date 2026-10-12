# ATT-DICT-02（dev-20261012-041）验收报告

> 状态：已验收（只读复核通过；2 处措辞瑕疵，不影响口径）
> 任务：dev-20261012-041（包内 ATT-DICT-02）
> 被取代或取代：无
> 什么情况看这篇：要确认 ATT-DICT-02 的 Redis 缓存 / 迁移账本 / 字典项 / 接口是否真的到位时
> 最后复核：2026-10-12（Hermes，全程只读）

## 1. 结论

**PASS**。Redis 缓存已清、迁移账本（275）、字典项（一级 5 / 二级 24）、接口三层口径一致，旧项确已停用保留。仅 2 处报告措辞不准（见 §4），不构成阻塞。

## 2. 复现证据

### 2.1 Redis 缓存（`redis-cli -h localhost -n 0`）

- `--scan --pattern 'system:dict:*'` 现只剩 `system:dict:product_file_category`。
- 目标两键 `EXISTS system:dict:attachment_document_type system:dict:attachment_category` = `0`（清后为空）。
- `DBSIZE` = 1。→ 与报告「DEL 后为空」一致。

### 2.2 迁移账本（`sys_config`）

- `ops.schema.version` = `275`。
- `FIND_IN_SET('275', ops.schema.applied)` 命中（applied 末尾 = `272,273,274,275`）。
- `ops.schema.file.275` = `{"file":"275_attachment_document_type_taxonomy.sql","sha256":"d8ccbc3735a20645694b9b9a7bdb06e5ffebaf44a94dc5bea84636bbb394cc30"}`。

### 2.3 字典项（`sys_dict_item`）

- `attachment_category`：total 6 / active **5**；`attachment_document_type`：total 25 / active **24**。
- 停用两项：`attachment_category.item_key=general_legacy`（`item_value` 仍是 `general`）`is_active=0`；`attachment_document_type.general_document` `is_active=0`。
- 24 条 active 二级的 `ext_data.parent` 全部指向存在的 active 一级，**不合规 = 0**。

### 2.4 接口（后端在跑：pid 3952046，jar 构建 2026-10-12 18:16:19 含 `SysAttachmentRefController` / `AttachmentDocumentTypeService`，8080 监听；admin 登录）

- `GET /system/attachment/refs/document-types` → **HTTP 200**、`body.code=200`、**31 行**（一级 5 active + 1 inactive；二级 24 active + 1 inactive）。
- 抽查新编码全部命中：`SALES_INQUIRY_ATTACHMENT`、`CERTIFICATE_OF_CONFORMITY`、`OTHER`、`CUSTOMER_DRAWING`、`INK_MIXING_RECORD`。

### 2.5 报告与回写

- 报告 `jjx-docs/history/att-dict-02-dev-20261012-041.md` 存在；`history/INDEX.md:269` 已登记。
- `cd jjx-web && npm run check:docs` → 文档门禁通过（history 232 篇 / modules 4 篇）。
- `sys_task.dev-20261012-041`：status = **2**（待审核），remark `CHAR_LENGTH` = 451（< 500）。
- 无 041 相关提交（报告与 INDEX 行仍为工作区未提交状态）。

## 3. 复跑命令（供他人复核）

```bash
redis-cli -h localhost -n 0 EXISTS system:dict:attachment_document_type system:dict:attachment_category
mysql -uroot -p123456 jjx_erp_db -N -e "SELECT config_key,config_value FROM sys_config WHERE config_key IN ('ops.schema.version','ops.schema.file.275');"
mysql -uroot -p123456 jjx_erp_db -e "SELECT dict_code,COUNT(*) total,SUM(is_active=1) active FROM sys_dict_item WHERE dict_code IN ('attachment_category','attachment_document_type') GROUP BY dict_code;"
curl -s --noproxy '*' -X POST http://127.0.0.1:8080/sessions/auth -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}'
# 再带 token 头 GET http://127.0.0.1:8080/system/attachment/refs/document-types
```

## 4. 瑕疵（措辞，不影响业务口径）

1）041 报告 §2.1 / remark 写「`ops.schema.file.275` **记录任务 dev-20261012-040**」——不成立：该键的值只有 `file` + `sha256`，无 task 字段，全库也无 `ops.schema.task.*` 键。任务码实际只写在迁移脚本头注释（`275_attachment_document_type_taxonomy.sql` 第 2 行）。正确说法应为「迁移脚本头记录 dev-20261012-040」。
2）「工作区仅新增报告 + INDEX 行」只对本任务成立；当前整仓工作区还压着别的任务/会话的未提交物（`migrations/applied/27{1..5}_*.sql`、V1-A/V1-B 一批 untracked 文档、`SysAttachmentRefController.java` / `AttachmentDocumentTypeService.java` 未跟踪等），别把 041 的回写误当成这些的来源。

## 5. 环境

- 后端在跑：`java -jar .../jjx-server/target/jjx-server-1.0.0.jar`（2026-10-12 18:16:22 启动）。
- 验收方式：全程只读（无写库、无改代码、无提交、无进程生命周期操作）。
