# 系统管理模块菜单重构（dev-20260828-039）

本文是实施规格。权限体系（后端 @SaCheckPermission 缺失、按钮级 F 权限缺失、
system:config:edit 复用）**不在本次范围**，由用户后续单独处理。

## 一、迁移 SQL：jjx-docs/sql/migrations/19_system_menu_restructure.sql

要求：幂等（重复执行结果一致，参照 18_sales_receipt_invoice_print_menu.sql 的
`NOT EXISTS` / `INSERT IGNORE` 写法），只动系统管理(menu_id=1)子树与日志管理(55)，
不要顺手改其它模块菜单。执行后同步应用到开发库 jjx_erp_db。

### 1. 新增三个目录（menu_type='M'，parent_id=1，ancestors='0,1'）

| menu_name | path | route_name | icon | order_num |
|---|---|---|---|---|
| 组织权限 | org | SystemOrg | UserFilled | 1 |
| 基础配置 | setting | SystemSetting | Setting | 2 |
| 运维监控 | ops | SystemOps | Monitor | 3 |

component 留空（目录），visible='0'，status='0'，perms 留空或沿用 system:view。

### 2. 迁移现有菜单的 parent_id / ancestors / order_num

组织权限(org) 下：用户管理(2)=1、角色管理(3)=2、部门管理(5)=3、菜单管理(4)=4
基础配置(setting) 下：数据字典(61)=1、系统参数(250，menu_name 改「系统参数」)=2、
事件配置(238)=3、汇率管理(新增，见第 4 节)=4
运维监控(ops) 下：操作日志(56)=1、登录日志(57)=2、异常日志(58)=3、文件管理(251)=4

- 所有被移动记录的 ancestors 必须写成 `0,1,<新目录menu_id>`；238/249/250 原本是 NULL，一并补齐。
- 日志三项 order_num 原本全是 0（排序不确定），必须改成 1/2/3。
- 保持各页面 path 的最后一段不变（user/role/dict/operation/...），只因父级变化导致
  完整路由变为 /system/org/user 等。已确认前端无任何硬编码跳转到 /system/xxx 或 /log/xxx。

### 3. 删除两条菜单（含 sys_role_menu 清理）

- 日志管理(55)：三个子项已迁到 ops，删除本行。它原本 menu_type='C' 却挂子菜单，是脏数据。
- 单据模板配置(249)：页面并入系统参数（见第二部分），删除本行。
- 两者的 sys_role_menu 关联行一并删除。

### 4. 系统管理(1) 自身

- order_num 从 0 改为 900，让系统管理排在所有业务模块（最大 276）之后。
- sort 列与 order_num 保持一致（RouterHelper.java:47 只读 order_num，sort 是历史死列，
  本次只保证不矛盾，不做删列）。

### 5. 图标去重（当前字典/单据模板/操作日志都是 Document，登录日志与用户管理都是 User）

数据字典=Collection、系统参数=Setting、事件配置=Bell（原值是小写 'setting'，
与系统配置的 'Setting' 大小写不一致，必须统一为大写驼峰组件名）、
操作日志=Tickets、登录日志=Key、异常日志=Warning、文件管理=Folder、汇率管理=Money。

### 6. 角色授权继承（必须做，否则菜单树会丢子节点）

RouterHelper.java:91/107 是在「用户已授权菜单列表」里按 parentId 匹配父子的：
父目录没授权 → 其下所有子菜单在菜单树里被整片丢弃。

因此对三个新目录执行 `INSERT IGNORE INTO sys_role_menu`，把「拥有该目录任意子菜单的角色」
全部授权到该目录。汇率管理新菜单沿用系统参数(250)的角色授权。

## 二、前端改造

### 1. 单据模板配置并入系统参数（消掉双写入口）

现状：views/system/pdfConfig/index.vue 专门维护 sys_config 的 pdf_template 分组（16 个键），
而 views/system/config/index.vue:117 的 GROUP_LABELS 已含 `pdf_template: 'PDF模板 / 公司信息'`，
同一份数据两个入口都能改。

做法：**先逐项对比两页功能**，把 pdfConfig 页独有的能力（logo 上传、颜色选择、
show_* 开关、signature_label 分组、字段中文标签、预览等）迁移到 config 页的
pdf_template 分组渲染逻辑里，确认无能力丢失后再删除 views/system/pdfConfig 目录。
不允许直接删页面导致功能退化。

### 2. GROUP_LABELS 补齐

sys_config 实际有 10 个分组，GROUP_LABELS 只映射 8 个：缺 `production`、`production_config`，
页面上直接显示英文 key。补成 production='生产'、production_config='生产配置'。

### 3. 字典管理标记只读（本次最重要的一处纠偏）

现状：sys_dict 共 63 条，其中 55 条 remark 是「由枚举 XXX.java 自动导入」；
全前端只有 useDict('process_type')、useDict('process_category') 两个字典被真正消费。
管理员在字典管理里改 sales_order_status 之类的名称，页面完全不变（页面按 AGENTS.md
用 src/enums/*.ts），造成「改了没用」的误导。

做法（不动数据库结构、不动枚举导入逻辑）：
- 列表中 remark 含「自动导入」的行显示「系统字典」标签，并禁用其编辑/删除/新增子项按钮
  （置灰 + tooltip：由后端枚举自动导入，页面显示以代码枚举为准，此处仅供查看）；
- 页头加一条 el-alert 说明状态类字典为只读、真正可运营的字典（如工序类型/工序类目）可编辑；
- 判定逻辑集中在一个 helper 函数里，不要散落在模板各处。

### 4. 新增汇率管理页（views/system/exchange-rate/index.vue）

现状核实：ExchangeRateController 走外部 API https://open.er-api.com/v6/latest/CNY，
失败时使用类内硬编码 FALLBACK_RATES（USD 7.24 / EUR 7.88 / ...）。报价单与销售订单
（api/sales/quotation.ts:260、api/sales/order.ts:298）都在调 /system/exchange-rate/rate，
但系统里没有任何查看或维护入口，外网不通时业务方无法感知用的是静态兜底汇率。

前端：只读查看页 —— 调 /system/exchange-rate/latest，展示 base、source（live/fallback 用
不同 tag 颜色，fallback 时给出醒目提示）、各币种汇率表格、手动刷新按钮。

后端：把 FALLBACK_RATES 从硬编码常量改为优先读取 sys_config 的 `exchange_rate` 分组
（config_key 形如 exchange_rate.USD，config_group='exchange_rate'），读不到再回退到
现有代码常量。同时在迁移 SQL 里插入这批 sys_config 初始值（沿用当前常量数值），
并给 GROUP_LABELS 补 exchange_rate='汇率兜底值'，这样管理员用现有系统参数页就能维护兜底汇率。

## 三、验证要求

1. 迁移 SQL 连续执行两次，结果一致（幂等）。
2. 应用到 jjx_erp_db 后，用 admin 与任一非管理员角色（如 SALES 全权限）分别拉菜单，
   确认系统管理下三级目录结构正常、日志三项在运维监控下、没有子菜单丢失。
3. `cd jjx-web && npm run validate`（check:status-enums + vue-tsc）必须通过；
   禁止扩充 scripts/status-magic-baseline.json。
4. `cd jjx-server && mvn -o test-compile` 通过。
5. 不要提交（no git commit）。工作区已有 4 个与本任务无关的修改文件
   （OrderController.java、OrderServiceImpl.java、api/sales/order.ts、OrderForm.vue），
   不要触碰、不要还原、不要提交它们。

## 四、明确不做

- 权限相关的一切（后端注解、F 权限补齐、system:config:edit 拆分）——用户后续自理。
- biz_no_rule / inventory.low_stock_threshold 等业务参数下放到各业务模块。
- 删除 jjx-web/src/mock/router.json（已确认无任何引用、vite 也没装 mock 插件，
  属零风险死文件，但用户未批准，本次保留）。
