# BOM 修改入口状态限制与驳回编辑规则对齐

任务：dev-20261007-012；日期：2026-10-07；执行者：Codex。

## 背景与证据

BOM-JST003MEOO 当前 bom_id=1、approve_status=3（已批准）、is_current=1。列表行修改按钮只有权限约束，没有状态 visible；顶部修改仅按选中数量禁用，handleUpdate 也直接打开。后端 updateBomWithDetail 原有 isEditable 拦截已批准状态。

前后端同时存在驳回规则不一致：前端 BomStatusEnum.REJECTED 已允许 EDIT，后端 BomStatus.isEditable 只允许 DRAFT；后端 submitApproval 明确允许草稿及驳回，并注释“驳回后修改重新提交”。用户授权修复三入口并核对统一该规则，因此后端允许 DRAFT 或 REJECT，其余状态保持不可编辑。

## 实施

- 列表以 canEditBom 统一调用既有 BomStatusEnum.canDo(approveStatus, ProductActions.EDIT)，行按钮 visible、单选顶部按钮 canEditSelected 和 handleUpdate 入口共用判断，不新增状态字面量或映射。
- 顶部按钮要求恰好选中一条可编辑记录；空选、多选或不可编辑状态禁止打开，入口提示只有草稿或已驳回可以修改。
- 后端 ProductEnums.BomStatus.isEditable 对齐为 DRAFT 或 REJECT，不调整审批、删除或库存路径。
- 开工基线存在 BomDetailTable.vue 未提交改动，属于其他会话，不修改、不暂存。

## 验证

通过：vue-tsc --noEmit；BOM 列表模板编译；真实页面 setup + 现有枚举的五状态回归，验证每种状态行按钮、顶部按钮、行参数调用入口和选中调用入口，以及空选/多选；后端 ProductEnums/BizStatusEnum 实际源码在 /tmp 独立 javac 编译及五状态 isEditable 运行检查；git diff --check 与文档门禁。

mvn -o -DskipTests compile 返回 BUILD SUCCESS，但日志为缓存 Nothing to compile；进一步 javap 检查发现 target/classes 中既有 IDE 输出包含 Unresolved compilation problem 错误桩，因此不把缓存 Maven 成功作为运行态验证，改用实际源码独立编译与运行验证。上线前应使用正常构建流程重新编译后端。

npm run check:status-enums 和 npm run validate 仍被既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 状态映射问题阻断，后续门禁未执行，未增加基线。未调用真实保存接口、未变动业务数据、未进行浏览器 E2E，未启动/停止/重启服务。前端刷新后生效；后端驳回编辑规则需要重新构建并由用户重启后生效。

## 验收

刷新 BOM 列表，BOM-JST003MEOO 已批准行没有修改操作，选中后顶部修改不可用；草稿、已驳回显示并允许进入修改。后端重新构建及重启后验证驳回可保存、已批准仍不能保存。提交 hash、验证摘要及运行态待办写入 sys_task.remark，提交后置待审核。
