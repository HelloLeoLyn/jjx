# 产品作业规范电子文档集 · 交接单（给 Codex）

> 任务码：**dev-20261009-010** ｜ 交接：Hermes ｜ 执行：Codex（用户自行发起）
> 依据：`jjx-docs/requirements/product-spec-docset-requirements-20260929.md`、`jjx-docs/design/product-spec-docset-plan-dev-20260929-023.md`
> 主体已建：页面 `jjx-web/src/views/engineering/product-spec/index.vue`（工程侧 /engineering/spec、产品侧 /product/spec 共用组件）。本次只收尾下面两个页签。

---

## 一、范围（只做这两件）

### 1. 「印刷规范」页签：接入只读引用块
现在该页签只有一句提示 + 「印刷指导图」上传，缺引用展示。补三块**只读引用**（不要复制数据，取当前口径）：
- **印刷工序**：该产品工艺路线里的印刷类工序（来源：产品当前路线 / 标准工序）。
- **油墨**：物料类型 `I`、分类 `INK`（前缀 INK）；按产品/工序可用口径取，无数据则显空态。
- **网板**：`engineering_screen_frame` / `engineering_screen_plate` + 资源↔产品关联（同刀模那一套关联表的 SCREEN 侧）。
每块用现有 `el-table` 只读展示；查不到就 `el-empty`。

### 2. 「客供资料」页签：纳入「客户确认样品」
需求单已定案：客户确认样品与客供资料**同一位置**。
- 在①「客供资料」页签内新增「客户确认样品」区块，复用 `ProductFileLibrary`，类别 = `客户确认样品`。
- ⑥「样品」页签只保留「样品实物照片」，去掉客户确认样品的重复入口（避免两处维护）。

## 二、不做（缺输入，别动）
- 凹凸条件（等工程给字段）、分色检查表（等样张字段）、油墨调配记录表 / 油墨配方（等字段，P3）。
- 需求单里这些本来就标「待核查」。

## 三、约束
- **只改代码，不碰数据库**：Codex 沙箱连不上 MySQL。若确实需要字典/权限 SQL，把 SQL 写成文件交回，由本机执行者落库。
- 复用现有组件 / 接口，不新造打印或附件框架。
- 状态/枚举按仓库 AGENTS.md 铁律（有域枚举就用，不得写字面量）。

## 四、涉及文件（可能）
- 必改：`jjx-web/src/views/engineering/product-spec/index.vue`
- 可能：`jjx-web/src/api/engineering/*`、`jjx-web/src/api/product/*`
- 若新增薄接口：`jjx-server/src/main/java/com/jjx/engineering/**`（或 product 包）

## 五、验收
- 前端：`cd jjx-web && npm run validate`（含 `vue-tsc --noEmit`）。
- 后端（若改了）：`cd jjx-server && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -o compile`。
- 手验：产品列表 →「作业规范」→ 印刷规范页签能看到该产品的 印刷工序/油墨/网板；客供资料页签有「客户确认样品」区块。

## 六、完成动作
- 提交信息带任务码 `dev-20261009-010`（`commit-msg` 闸门会校验该码在 `sys_task` 存在）。
- 提交后把 `sys_task.status` 置 `2`，`remark` 写：改动清单 + commit hash + 验证结果 + 遗留。
