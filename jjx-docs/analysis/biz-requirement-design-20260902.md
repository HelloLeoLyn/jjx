# 业务管理 · 需求管理模块设计方案

- **日期**：2026-09-02
- **状态**：MVP 已落地（commit `643ad80`），本文档为设计说明
- **范围**：通用业务需求载体（变更/新增/改善/问题），首个场景 = 工程变更 ECN（QR-030）

---

## 一、背景与决策

### 1.1 起因
任务 1248「工程变更 ECN 流程缺失」：QR-030 模板有占位、无数据源、无流程。

### 1.2 关键决策（Leo 2026-09-02 定）
1. **变更本质是需求**——不应建"工程变更"领域专用表。客户改版、内部优化、品质改善都是"需求"，ECN 只是其中一种（类型=CHANGE）。
2. **通用需求载体表** `biz_requirement`（不用 change_request 命名），所有业务需求统一入口。
3. **表结构认可**：通用核心字段 + ECN 扩展字段（仅 CHANGE 类型展示）。
4. **菜单**：顶层「业务管理」→ 子菜单「需求管理」。
5. **首个落地场景**：以 QR-030 工程变更通知为切入点，先跑通"提一张变更单 + 打印"。

### 1.3 需求类型（requirement_type）
| 值 | 含义 | 典型场景 |
|---|---|---|
| CHANGE | 变更 | 设计改版/工艺调整/材料变更（原 ECN） |
| ADD | 新增 | 新产品/新流程/新功能需求 |
| IMPROVE | 改善 | 工艺优化/降本/效率提升 |
| ISSUE | 问题 | 客诉/内部异常/纠正措施 |

---

## 二、数据模型

### 2.1 biz_requirement（主表）
| 分组 | 字段 | 说明 |
|---|---|---|
| 标识 | requirement_id / requirement_no | 单号 RQ-xxx（Redis 序列） |
| 类型 | requirement_type | CHANGE/ADD/IMPROVE/ISSUE |
| 内容 | title / description | 标题 + 详细描述 |
| 属性 | source（来源）/ urgency（紧急度）/ expect_date（期望完成） | 来源: CUSTOMER/SALES/QUALITY/ENGINEERING/PRODUCTION/MANAGEMENT/OTHER |
| 关联 | biz_type / biz_id / biz_no | 多态关联：product/bom/routing/sales_order/material/… |
| 状态 | requirement_status | 1草稿/2评审中/3已通过/4执行中/5已关闭/6已驳回 |
| **ECN 扩展** | change_type / cutover_mode / need_resample / version_before / version_after | 变更类型/切入方式/是否重打样/变更前后版本（仅 CHANGE 展示，可空） |
| 流程 | applicant_* / reviewer_* / review_remark / close_time | 申请人/审批人/意见/关闭时间 |

### 2.2 biz_requirement_approval（会签子表，预留）
round_no / approval_role（ENGINEERING工程/MAKING制造/PURCHASE采购仓库/QUALITY品管）/ approval_user_* / approve_result / comment

### 2.3 状态机
```
1草稿 ──提交──▶ 2评审中 ──通过──▶ 3已通过 ──(执行)──▶ 4执行中 ──▶ 5已关闭
                │
                └──驳回──▶ 6已驳回 ──编辑──▶ 1草稿(重新提交)
```
- 编辑：仅 1草稿 / 6已驳回 可改
- 删除：仅 1草稿 可删

### 2.4 SQL 存档
`jjx-docs/sql/biz_requirement_20260902.sql`（建表 + 菜单 + 角色绑定）

---

## 三、菜单与权限
| menu_id | 名称 | 类型 | 权限 |
|---|---|---|---|
| 317 | 业务管理（顶层 /biz） | M | — |
| 318 | 需求管理（views/biz/requirement/index.vue） | C | biz:requirement:view |
| 319 | 新增需求 | F | biz:requirement:add |
| 320 | 编辑需求 | F | biz:requirement:edit |
| 321 | 删除需求 | F | biz:requirement:remove |

---

## 四、实现清单（MVP 已落地）

### 后端（com.jjx.biz 包）
- entity：BizRequirement / BizRequirementApproval
- enums：RequirementTypeEnum / RequirementStatusEnum（实现 BizStatusEnum 契约）
- mapper / service / controller：
  - `GET  /biz/requirement/page`（单号/类型/状态/标题/来源 筛选）
  - `GET  /biz/requirement/{id}` 详情
  - `POST /biz/requirement` 新增（自动 RQ 单号）
  - `PUT  /biz/requirement` 修改（仅草稿/驳回）
  - `DELETE /biz/requirement/{ids}` 删除（仅草稿）
  - `PUT  /biz/requirement/submit/{id}` 提交评审
  - `PUT  /biz/requirement/review/{id}?approved=&remark=` 审核
  - `GET  /biz/requirement/type-options` / `status-options`

### 前端
- `views/biz/requirement/index.vue`：类型 tab（全部/变更/新增/改善/问题）+ 搜索 + 列表 + 新增/编辑弹窗（**CHANGE 类型动态显示 ECN 字段**）+ 详情弹窗
- `views/biz/requirement/print.vue`：QR-030 工程变更通知 A4 打印页
- `api/biz/requirement.ts`
- 路由：`/print/requirement/:id`（静态注册）

### QR-030 打印页布局
公司抬头（PrintCompanyHeader）→ 单据标题"工程变更通知"（编号 JJX-QR-030 + RQ 单号）→ 信息区（变更日期/版本/单号/品名料号/机种编号）→ 变更内容框 → 变更前后对照表 → 切入方式/是否重打样决策行 → 4 部门会签区（工程/制造/采购仓库/品管）→ 制单/批准栏

---

## 五、后续规划（MVP 未做）

| 阶段 | 内容 | 说明 |
|---|---|---|
| P1 | 会签子表启用 | CHANGE 类型按 QR-030 四部门会签（工程/制造/采购仓库/品管），意见落 biz_requirement_approval + 通知 |
| P1 | 通过→执行→关闭流转 | 3已通过 可"开始执行"(→4) → 登记执行结果 → 关闭(→5) |
| P1 | 执行联动 | 变更通过后生成 BOM/工艺新版本（复用现有 is_current 版本化）；变更台账（QR-071）自动追加 |
| P2 | 打印留痕 | quality_template_print_log 联动（QR-030/QR-071） |
| P2 | 附件 | 关联变更通知附件（sys_attachment biz_type=biz_requirement） |
| P2 | 影响面 | 自动找"引用了变更前版本的在产工单/订单"（预警，工作量大后置） |

---

## 六、验证方法
1. 重启后端（加载 com.jjx.biz）
2. 业务管理 → 需求管理 → 新增"变更"类型需求 → 填 ECN 字段 → 保存
3. 提交评审 → 审核通过/驳回
4. 详情 → 打印变更通知（QR-030 布局）
