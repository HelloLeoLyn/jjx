# 变更记录台账（QR-071 电子台账，任务 1302 / dev-20260902-101）

2026-09-03 核查定案：1248(ECN)已交付，biz_requirement CHANGE 记录即变更台账数据源（字段一一对应：
变更日期=apply_time、变更机种=biz_no、变更内容=description、备注=remark）。用户批准做电子台账
（清单+筛选+导出 Excel），打印页等"上传模板→A4"方向落地后再接。

## 改动清单

### 后端（2 个文件）
1. jjx-server/.../biz/domain/query/BizRequirementQuery.java：加 3 个可选筛选
   - String bizNo（机种/单号模糊，like biz_no or requirement_no）
   - LocalDate startDate / endDate（apply_time 范围）
2. jjx-server/.../biz/service/impl/BizRequirementServiceImpl.java：page() 里对上述字段加条件
   （仅在非空时），不动其他逻辑。
3. jjx-server/.../biz/controller/BizRequirementController.java：新增
   GET /biz/requirement/export（参数同上 BizRequirementQuery）→ Excel：
   列=变更日期(apply_time, yyyy-MM-dd)/单号(requirement_no)/变更机种(biz_no)/变更内容(description)/
   变更类型(change_type label: DESIGN设计改版/PROCESS工艺调整/MATERIAL材料变更/DRAWING图纸更新/OTHER其他)/
   版本(version_before→version_after)/状态(requirement_status label)/申请人(applicant_name)
   文件名 变更记录表-YYYYMMDD.xlsx；写法照搬 QuotationServiceImpl 导出那段手动 workbook 模式
   （createSheet/createRow/createCell + response 输出），异常转 BusinessException。
   权限 @SaCheckPermission("biz:requirement:view")（与列表同档，不新增权限点）。

### 前端（1 个新页面 + 1 个 api 函数）
4. jjx-web/src/api/biz/requirement.ts：export 函数 changesExport(params) → blob 下载
   （照 quotation api export 的写法 + download 工具）。
5. 新页面 jjx-web/src/views/biz/requirement/changes.vue：
   - 列表列：变更日期/单号/变更机种/变更内容(show-overflow-tooltip)/变更类型/版本(前→后)/状态/申请人
   - 筛选：日期范围(startDate/endDate)、机种关键字(bizNo)、状态(requirementStatus 下拉，
     默认 3 会签通过；选项 全部/1/2/3/4)、变更类型(changeType 可选)
   - 请求 /biz/requirement/page 带 requirementType=CHANGE + 上述筛选
   - 顶部按钮：导出(调 changesExport，blob 下载)
   - 状态文案/标签：照 views/biz/requirement/index.vue 的 statusLabel/statusTag 同款本地函数
     （两页重复，后续统一枚举时一起收；不要动 index.vue）

### 菜单（迁移 53_biz_change_ledger.sql，我沙箱外执行）
6. sys_menu C「变更记录」：parent_id=317(业务管理)，order_num=2，path='changes'，
   component='views/biz/requirement/changes.vue'，route_name='BizChanges'，
   perms='biz:requirement:view'，ancestors='0,317'，menu_type C，icon 'Document'，
   NOT EXISTS(route_name='BizChanges') 幂等；sys_role_menu 授权复制兄弟菜单 需求管理(318,
   route_name='Requirement')。

## 明确不做
- 历史 30 条纸版变更记录导入（用户数据活，需要时另给脚本口径）
- 打印页（QR-071 上传A4方向落地后接）
- 不动需求管理 index.vue / 不新增权限点 / 不改状态流转

## 验证
1. codex 自跑 mvn -o clean compile + vue-tsc(只报所改文件)；我复核 diff
2. 我：执行 53 迁移 + 幂等 + menu-integrity
3. 用户：业务管理→变更记录 → 出 RQ2609020001 一行；导出 Excel 打开正常
