# 工艺路线补充功能 验收测试计划（2026-08-10 批次1）

> 版本：v1.0（2026-08-10 用户拍板批次1范围：下标工序/跨组依赖/可选工序标记）
> 范围：工艺路线配置页（RouteItemIconEditor）+ 标准工序页 新增三项功能
> 前置：8080 已用最新代码重启（含 V20260810_001 迁移，10:26 重启成功）；前端 vite 3000 热更新
> 用例编号：TC-510~515（与工作台 TC-1~156、TC-300~310、TC-400~450、TC-500~508 不冲突）
> 执行人：HelloLeoLyn（人工）；大黄已更新测试工作台

---

## 一、测试账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | admin | 123456 |

## 二、涉及改动（本次验收对象）

- **表**：`engineering_standard_process.has_index`；`engineering_routing_item` 加 `index_number`/`precondition`/`precondition_display`/`is_optional`
- **后端**：实体/VO/DTO/Mapper 字段同步（mvn compile ✅）
- **前端**：标准工序 add/edit 加「是否带下标」复选框；列表加「带下标」列；RouteItemIconEditor 加下标弹窗/依赖下拉/可选复选框（vue-tsc ✅）

## 三、测试用例

## [下标工序] TC-510: 标准工序标记「带下标」

### 测试步骤
1. 产品管理 → 标准工序 → 选一个工序（如 SP-108 面板冲孔）→ 编辑
2. 勾选「是否带下标」→ 保存
3. 回到列表确认该行「带下标」列显示黄色 tag

### 验证点
- [ ] 保存后 DB：`SELECT has_index FROM engineering_standard_process WHERE process_code='SP-108'` → 1
- [ ] 列表「带下标」列显示正确
- [ ] 取消勾选再保存 → has_index 回 0（null 安全更新不丢其他字段）

## [下标工序] TC-511: 拖拽带下标工序 → 弹窗输入数字

### 测试步骤
1. 工艺路线 → 新增/编辑 → 进入 RouteItemIconEditor
2. 从工序库拖拽 SP-108（已带下标）到表格
3. 确认弹出「输入下标数字」弹窗
4. 输入 4 → 确定

### 验证点
- [ ] 拖入即弹窗（不拖入不带下标的工序不弹）
- [ ] 输入 4 保存后 DB：`SELECT index_number FROM engineering_routing_item WHERE process_id=<SP-108的process_id>` → 4
- [ ] 取消弹窗 → index_number 为 NULL，不阻塞其他操作

## [下标工序] TC-512: 表格显示红底数字下标

### 测试步骤
1. 保存路线后重新打开编辑页
2. 查看该工序行图标右下角

### 验证点
- [ ] 工序图标右下角显示红底白色数字 4（IconStepBadge）
- [ ] 点击图标可再次修改下标数字（弹窗回显当前值）
- [ ] 不带下标的工序无下标角标

## [跨组依赖] TC-513: 依赖下拉出现带下标工序

### 测试步骤
1. 再拖入另一个带下标工序（如 SP-208 上线冲第一刀，先给下标 2）到**另一组**
2. 回到第一个工序（面板组），行内点「依赖」下拉

### 验证点
- [ ] 下拉选项格式「面板④ 面板冲孔」「上线② 上线冲第一刀」（组名+圈号+工序名）
- [ ] 选择后该行显示选中项；DB：`precondition='PANEL_4'`、`precondition_display='面板④ 面板冲孔'`
- [ ] 自身是带下标工序时，下拉**不含自己**
- [ ] 修改某工序下标数字 → 依赖下拉选项实时更新
- [ ] 删除带下标工序 → 引用它的工序 `precondition/precondition_display` 被清空（NULL）

## [可选工序] TC-514: 可选复选框 → is_optional=1

### 测试步骤
1. 路线编辑页某工序行点「+√」复选框
2. 保存后查库

### 验证点
- [ ] 勾选后保存：`SELECT is_optional FROM engineering_routing_item WHERE ...` → 1
- [ ] 取消勾选保存 → 回 0
- [ ] 重新打开编辑页回显勾选状态

## [回归] TC-515: 组合拖拽/工时/备注 不回归

### 测试步骤
1. 原有功能：拖拽排序、组合整体移动、工时汇总、组合备注
2. 保存后重新打开核对

### 验证点
- [ ] 组间拖拽/组内排序正常，groupId 保存正确
- [ ] 总人工/机器工时汇总不变
- [ ] 组合备注仍存第一条工序 description

## 四、注意

- 前端 vite 3000 热更新即时生效；后端已重启（含迁移 SQL）
- 验证库字段用：`mysql -u root -p123456 jjx_erp_db -e "SELECT ..."`
- 失败点「➕登记任务」→ 工作台自动写 sys_task（kanban_module=dev）
