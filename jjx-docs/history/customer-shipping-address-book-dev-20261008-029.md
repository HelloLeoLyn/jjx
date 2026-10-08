# 客户收货地址簿：多收货地址 + 下单/发货默认链（dev-20261008-029）

> 状态：阶段一（DB + 后端）落地中；阶段二（前端接入）待续。
> 迁移：`jjx-docs/sql/migrations/247_sales_customer_address.sql`（由用户手工执行）。

## 背景 / 现状（证据）

- 客户档案 `sales_customer` 只有**一套**地址字段（`country/province/city/address/postal_code`），无法维护一个客户的多个收货地址。
- 订单表 `sales_order.deliveryAddress` 存**地址快照**（InternationalAddress JSON 序列化）。
- 发货弹窗（`jjx-web/src/views/sales/order/index.vue`）现有候选 =「订单地址 + 客户档案地址」，默认取第一个；两者皆空则提示手填。
- 公司地址存于 `sys_config`（`config_key=company_address`，group=pdf_template），如：深圳市宝安区沙井街共和村丽城工业园F栋4楼。
- 需求（用户 2026-10-08 口述）：未填地址时可按公司地址兜底；并像电商那样维护多个收货地址；公司地址**也可作为收货地址候选**，用户可选择、也可设为默认。

## 方案

### 数据模型
新表 `sales_customer_address`（客户收货地址簿）：`customer_id / label / contact_person / contact_phone / country / province / city / address / postal_code / is_default / remark` + 审计与逻辑删除。一个客户多条，`is_default=1` 最多一条。

### 默认链（下单 / 发货统一）
```
① 自提 → 公司地址
② 否则 → 客户地址簿中 is_default=1 的那条
③ 否则 → 订单已存地址（历史单）
④ 都没有 → 留空 + 提示（不再硬塞公司地址）
```
候选项统一 =「客户地址簿各条」+「公司地址（一项）」；公司地址同样可被选为默认。

### 接口（阶段一）
`/sales/customers/{customerId}/addresses`
- `GET` 列表（默认地址置顶）
- `POST` 新增（首个地址自动置默认）
- `PUT /{addressId}` 修改
- `DELETE /{addressId}` 删除（逻辑删除；删默认自动改派）
- `PUT /{addressId}/default` 设为默认

权限沿用 `sales:customer:view` / `sales:customer:edit`（不新增权限点）。

### 前端（阶段二，待做）
- 客户档案：多地址维护（增删改、设默认）。
- 下单表单：收货地址改为「从地址簿选 + 现场新增回存」；公司地址作为候选项；默认按上面链条；选中即**快照**进订单。
- 发货弹窗：候选 = 订单地址 + 客户地址簿 + 公司地址；默认链同上。

## 治理登记
- `scripts/model-baseline.json` → `approvedNewTables` 登记 `sales_customer_address`。
- `jjx-docs/sql/00_clean_test_data.sql` 第 12 节保留清单 + `scripts/db-clean-test-data.sh` `RETAINED_TABLES`（随客户档案保留）。

## 验证步骤
1. `JAVA_HOME=java-21 mvn -o compile` 通过。
2. 用户执行迁移：`bash scripts/db-migrate.sh 247_sales_customer_address.sql --yes --task dev-20261008-029`。
3. 重启后端后，接口自测：`GET/POST/PUT/DELETE` + `PUT /default`。
4. 前端阶段二完成后，页面验证下单/发货的地址选择与默认链。

## 待确认
- 公司地址目前是 `sys_config` 单串（无 country/province/city）。若要作为**结构化**收货地址，建议后续给 `sys_config` 补 `company_country/province/city/postal_code`；本阶段先用整串文本兜底。
