# 任务看板/任务列表 全量查询优化分析

> 分析日期：2026-08-07
> 状态：分析完成，实施方案待确认
> 范围：ERP 内看板 + 文档中心看板（8899）

---

## 一、现状：两套任务系统都是全量查询

### 1. ERP 内看板（jjx-web 集成版）

**后端**：`GET /kanban/board/{module}/tasks`
- `BoardTaskController.getBoardTasks` → `sysTaskMapper.selectList(wrapper)`（**无分页**）
- 支持 `status`/`priority` 单值过滤参数（但前端没传）
- production 模块特殊：`fetchProductionTasks()` 查生产订单表

**前端**：`views/kanban/api/board-real.ts` `fetchSysTaskBoardData`
- 一次性拉全量 → `mapSysTaskStatus` 映射状态 → `groupCardsByColumn` 按列分组
- 过滤（关键字/负责人/优先级/状态）全在**前端内存**做
- 看板列定义（board.ts）：待开始(pending)/进行中(in_progress)/待审核(review)/已完成(completed)/阻塞(blocked)/已废弃(cancelled)

**状态映射**：
```
sys_task.status: 0待开始 1进行中 2待审核 3阻塞 4已废弃 10已完成
看板: pending=0, in_progress=1, review=2, blocked=3, cancelled=4, completed=10
```

### 2. 文档中心看板（8899）

**后端**：`docs/server.js` `queryTasks(module)`
- `SELECT ... FROM sys_task WHERE kanban_module=? ORDER BY task_id DESC`（**无分页**）
- 状态映射简化：`0→todo, 1/2/3→doing, 10→done`（4已废弃丢失，归入todo）

**前端**：`docs/tasks/index.html`
- 全量拉取 → 前端按 todo/doing/done 分三列渲染

### 3. 数据量实测（sys_task 共 371 条）

| 状态 | dev | office | 说明 |
|---|---|---|---|
| 0 待开始 | 15 | 1 | 看板需要 |
| 1 进行中 | 2 | - | 看板需要 |
| 2 待审核 | 47 | - | 看板需要 |
| 3 阻塞 | 34 | - | 看板需要 |
| 4 已废弃 | 6 | - | 看板需要（弱） |
| 10 已完成 | 266 | - | **73%，历史数据** |
| 合计 | 365 | 1 | |

**核心问题**：已完成 266 条（73%）随每次看板加载全量查出，前端全量渲染 + 内存过滤。

---

## 二、问题清单

1. **全量查询无分页**：数据持续累积（每天新增任务），看板加载越来越慢
2. **已完成任务拖累**：266 条历史已完成任务与看板展示无关，白查白渲染
3. **前端内存过滤**：搜索/筛选在拿到全量后前端做，数据量大时卡顿
4. **两套看板逻辑重复**：ERP 看板（6 列精细）+ 文档中心看板（3 列简化），各自全量
5. **文档中心映射丢失状态**：4已废弃被归入 todo，状态语义失真

---

## 三、方案（考虑各状态）

### 方案 A：后端状态过滤 + 前端惰性加载（推荐，改动小见效快）

**后端**：`getBoardTasks` 支持多值 status 过滤（`status=0,1,2,3,4`）
- 看板调用时传 `status=0,1,2,3,4`（不含已完成）→ 104 条
- 已完成单独接口/参数（`status=10`）按页返回

**前端**：
- 看板默认只加载未完成任务（104 条，瞬间渲染）
- "已完成"折叠区/入口：点击才请求（分页 20 条/页）

**效果**：渲染 371 → 104 条；已完成按需加载。

### 方案 B：后端标准分页（治本，建议组合）

任务接口加标准分页：
```
GET /kanban/board/{module}/tasks?status=0,1,2,3,4&pageNum=1&pageSize=50
→ { records, total, pageNum, pageSize }
```
- 看板视图：后端按列分组统计 + 每列数据（一次请求拿全看板未完成）
- 列表视图：标准分页 + 状态 Tab 筛选（待办/进行中/待审核/已完成）

### 方案 C：看板接口专用化（最彻底，一次性到位）

新增 `GET /kanban/board/{module}/board-data`：
```json
{
  "columns": [
    { "status": 0, "title": "待开始", "tasks": [...], "count": 15 },
    { "status": 1, "title": "进行中", "tasks": [...], "count": 2 },
    { "status": 2, "title": "待审核", "tasks": [...], "count": 47 },
    { "status": 3, "title": "阻塞",   "tasks": [...], "count": 34 },
    { "status": 4, "title": "已废弃", "tasks": [...], "count": 6 }
  ],
  "completed": { "count": 266, "latest": [最近10条] }
}
```
- 看板一次请求拿到所有列 + 已完成数量（只带最近 10 条预览）
- 已完成展开时再请求分页明细

### 方案 D（补充）：文档中心看板同步优化
- `queryTasks` 加 status 参数，默认 `status != 10`
- 状态映射补全：`4→todo` 单独处理或归入对应列
- 前端已完成默认折叠

---

## 四、建议组合

**A + B 一起做**（推荐）：
1. 后端：任务接口支持多值 status + 分页（pageNum/pageSize）
2. ERP 看板：默认加载 status IN (0,1,2,3,4)，已完成 Tab 分页
3. 文档中心：queryTasks 加状态过滤，已完成默认收起

**效果**：
- 看板渲染从 371 → 104 条
- 已完成任务按需分页加载
- 数据持续增长有分页兜底
- 两套看板状态语义一致（4 已废弃正确处理）

方案 C 作为后续可选（如果看板交互还要加"每列分页/懒加载"再升级）。

---

## 五、待确认决策点

1. 采用 A+B 组合还是直接 C（看板接口专用化）？
2. 已完成任务的默认展示：折叠区（显示数量+最近10条）还是独立 Tab？
3. 文档中心看板是否同步优化（8899 与 ERP 看板并存，是否保留两套）？
4. 每列是否需要上限（如每列最多显示 50 条，超出提示"查看更多"）？

---

*本文档为分析记录，实施方案确认后拆分 DEV 任务。*
