# JJX ERP 系统底座模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：代码实扫 + 数据库实查

---

## 1. 现状盘点（实证）

### 1.1 表与行数

| 表 | 行数 | 判定 |
|---|---|---|
| sys_menu | 243 | 真实（C 菜单 61+，M 目录若干） |
| sys_role / sys_role_menu | 22 / 725 | 真实 |
| sys_config | 50 | 真实（分组：system/business/sales/inventory/production/pdf_template/biz_no_rule…） |
| sys_dict / sys_dict_item | 60 / 310 | 真实（枚举镜像，仅 2 个 dict code 前端消费：process_type/process_category） |
| sys_event_config | 132 | 真实（含空转批，见销售/库存报告） |
| sys_dept | 15 | 真实 |
| sys_attachment | 1 | 基本空（dev 清洗） |
| sys_oper_log / sys_error_log / sys_login_log | 4 / 1 / 7 | 基本空（dev 清洗；biz_status 已 varchar 化 migration 25） |
| sys_task | 550(605) | 真实（任务台账，2026-09-01 盘点删 12 条） |
| sys_notification | 0 | 空转（通知表 0——事件空转的直接后果） |
| portal_*（门户） | 0×4 | 空转（门户 286 已废弃标 4） |

### 1.2 后端 Controller

system 包 16 个：SysUser/SysRole/SysDept/SysMenu/EventConfig/ConfigModule/Dashboard/ExchangeRate/FileManage/ReviewFlow/SysAttachment/SysConfig/SysDict/auth/log 子包。权限注解 @SaCheckPermission 689 处（2026-08-29 041 补齐后系统管理接口有权限）。ReviewFlow（通用审核流水）/ FileManage（附件文件）/ SysAttachment（上传下载链）齐全。

### 1.3 前端与菜单

系统管理三级（297 组织权限/298 基础配置/299 运维监控，2026-08-28 菜单重构 039）：用户/角色/部门/菜单/字典/系统参数/事件配置/汇率/操作日志/登录日志/异常日志/文件管理全部有 C 菜单指向真实页面 ✅。文档管理(316) 2026-09-02 新建（质量记录模板迁入）。pdfConfig 页面存在（views/system/pdfConfig——sys_menu 无对应 C 菜单？单据模板配置 249 已删并入系统参数（039 任务）——pdfConfig 页可能是孤儿）。

### 1.4 移动端（生产 PDA/扫码枪场景）

views/mobile 5 页：scan/quality/pick/report/reports（扫码定位/质检判定/领料/报工）。路由 /m/*。对应任务 072/073/980 交付，1259 验证任务登记。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 登录/权限（Sa-Token 动态路由） | ✅通（role_menu 725 授权） |
| 菜单-角色-用户 | ✅通（2026-08-29 040 补 ENGINEERING 三角色子树） |
| 事件→任务/通知 | ⚠️半通：132 条配置多模块空转（见各报告），sys_notification 0 行佐证 |
| 审批流 review_flow | ✅通（sales_order/purchase_order/bom/film/退货 1235 接入） |
| 附件链（sys_attachment 上传/追溯） | ✅通（含 trace_id 关联 1200 修复） |
| 操作日志（@Log+bizStatus） | ✅通（2026-09-02 修 aspect 强制范围错杀系统管理接口 e33d5f2） |
| 文档管理 | ⚠️新目录（316）仅质量记录模板，文件管理在运维监控（299）——文控功能分散 |

## 3. 与行业基准对照

覆盖：RBAC✅ 审计✅ 事件通知✅ 参数配置✅ 文件管理✅。
缺失：文档中心（文控：受控文件/版本/分发）未成型——316 目录刚建只有质量记录模板；审计报表（操作日志无查询聚合页？views/log/operation 有）；系统健康/备份管理（备份仅有文件级，DB 备份无——08-29 已分析"部分完成"）。

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 功能分散 | 文件管理(299) vs 文档管理(316) | 两处入口语义重叠 | 中 | 规划文控中心收敛 |
| 孤儿页 | views/system/pdfConfig | 菜单 249 已删并入系统参数 | 低 | 删除或指系统参数 |
| 死配置 | 各模块空转事件 | 见分报告合计 ~25 条 | 中 | 统一清理 |
| 业务缺失 | DB 备份 | 08-29 分析：仅文件备份 | 高 | 补 DB 备份（系统备份任务 re-scope） |
| 数据缺口 | sys_attachment 1 行 | dev 清洗 | 低 | 随业务数据 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P1 | DB 备份补全 | 数据无备份=业务风险 |
| P1 | 空转事件统一清理 | 通知/任务链路可信度 |
| P2 | 文控中心规划（文件+模板+归档） | 316 起步，1190 配置式模板已废弃评估 |
| P3 | pdfConfig 孤儿页 | 清理 |
