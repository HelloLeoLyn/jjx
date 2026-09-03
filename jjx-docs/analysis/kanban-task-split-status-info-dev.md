# 看板任务接口拆分：状态流转 / 内容更新（修复 @Log bizStatus 空值 500）

## 背景与报错

BoardTaskController.updateTask（工作区当前版，067 并行成果）：
PATCH /kanban/board/{module}/tasks/{taskId} + Map body 一把梭，
@Log bizStatus="#updates.get('status')" —— 仅改标题/负责人等非状态内容时
status 键不存在 → SpEL 求值为 null → 操作成功但 bizStatus 空 → e33d5f2 规则抛
"@Log bizStatus 求值为空" → 500。

用户裁定：拆接口。改状态=状态流转（bizStatus 写目标状态）；改内容=状态不动
（不假装有流转）。

## 改动（后端 jjx-server/src/main/java/com/jjx/kanban/）

### 1. 新建枚举 com.jjx.kanban.enums.KanbanTaskStatusEnum
implements BizStatusEnum，照 18 单据状态枚举现行模板（含 getByValue）：
- 0 待开始 / 1 进行中 / 2 待审核 / 3 阻塞 / 4 已废弃 / 10 已完成
（对齐 sys_task.status 2026-09-01 定稿语义；值/label 命名照 SalesReceiptStatusEnum 样式）

### 2. 新建 DTO（com.jjx.kanban.domain.dto）
- BoardTaskStatusDTO { Integer status }
- BoardTaskInfoDTO { String title; String description; String priority;
  String assigneeName; java.time.LocalDate deadline; String remark }

### 3. BoardTaskController 替换 updateTask（删除原 Map 版 PATCH tasks/{taskId}）
保留 guard：production 模块拒绝、任务存在性与 module 匹配校验（照原逻辑，抽私有方法复用）

- A 状态流转：`@PatchMapping("/{module}/tasks/{taskId}/status")`
  body BoardTaskStatusDTO：
  - status 枚举校验：KanbanTaskStatusEnum.getByValue(dto.status)==null → Result.error("非法状态")
  - @Log(module="看板任务", UPDATE, bizType="'kanban_task'", bizId="#taskId",
    bizStatus="T(com.jjx.kanban.enums.KanbanTaskStatusEnum).getByValue(#dto.status)?.label")
    （#dto.status 经校验必合法 → label 必非空，不踩 500 雷）
  - 仅 setStatus + updateTime + updateById
- B 内容更新：`@PatchMapping("/{module}/tasks/{taskId}/info")`
  body BoardTaskInfoDTO：非空字段逐个 set（title/description/priority/
  assigneeName/deadline/remark），status 一律不动
  - @Log(module="看板任务", UPDATE, bizType="'kanban_task'", bizId="#taskId",
    bizStatus="T(com.jjx.kanban.enums.KanbanTaskStatusEnum).getByValue(#result.data)?.label")
    —— 服务返回当前状态值（int），腿B 样式 Result<Integer>；成功即当前状态 label，
    前端忽略返回体。实现：update 后 selectById 取 task.getStatus() 返回
  - 不做负责人变更明细（A→B）——不引入 ChangeRecorder，另议

### 4. 前端 jjx-web/src/views/kanban/api/board-real.ts
- moveCard（:215 拖拽）→ URL 改 `.../tasks/${taskId}/status`，body 不变 {status}
- updateCard（:329 编辑保存）→ 拆两段：
  a) body 不再放 status；URL 改 `.../tasks/${taskId}/info`
  b) 若 updates.status !== undefined → 先调 status 接口再调 info（同一 taskId）
- 其余模板类型分支逻辑不变；返回处理不变
- 调用方（拖拽组件/编辑弹窗）无需改动，仅 api 层收敛

## 明确不做
- ❌ 负责人变更 detail（A→B）；❌ 状态机严格流转表（先合法值校验）
- ❌ 生产模块看板改动；❌ 新建 VO/迁移/表
- ❌ 碰工作区其他在改文件（SalesQuotationAddDTO 等并行文件）
- ❌ git commit

## 验证
1. 后端：mvn -o clean compile
2. 前端：vue-tsc 全库 0 新增错误（grep -v ProcessCard|sample-workbench 后过滤核对）
3. 手测清单：
   - 看板拖拽卡片 → 成功；sys_oper_log 行 biz_status=目标状态 label（如"进行中"），不 500
   - 编辑弹窗只改标题/负责人 → 成功不 500；流水 biz_status=当前状态 label
   - 非法 status（如 99）→ 被"非法状态"拦截
   - dev/office/emergency 三个模块各拖一张验证

## 关联
- 修复 067（看板任务状态/负责人变更无操作日志，BoardTaskController 并行实现中的 500 缺陷）
- sys_task 1249 dev-20260901-067（并行批次未提交，本方案叠其当前工作区版本实施）
