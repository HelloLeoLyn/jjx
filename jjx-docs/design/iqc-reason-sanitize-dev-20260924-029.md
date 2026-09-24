# 检验原因文本清洗口径：界面提示串污染根治 + 唯一清洗出处（dev-20260924-029）

> 任务：dev-20260924-029（P1）｜起草：Hermes Agent｜2026-09-24
> 状态：**方案已定（用户 2026-09-24「你来决定 按规范」授权按本方案实施）**
> 适用域：来料检验（IQC）录入 → 来料不合格处置 → 成品报废单（下游抄写）
> 上位依据：仓库 `AGENTS.md`、`jjx-docs/standards/CONVENTIONS.md`
> 上游方案：`design/iqc-reason-derivation-dev-20260924-013.md`（原因由检验项目派生 + 边界 A）、`dev-20260924-017`（检验录入统一）
> 关联已实施：`dev-20260924-004`（原因 = 检验项目 + CR/MA/MI）、`dev-20260924-006`（成品报废单 reason 抄写）、`dev-20260924-013`（`quality_lot`/`quality_ncr` 存量订正）

---

## 1. 问题（2026-09-24 实据）

### 1.1 界面提示文案被写进了业务字段

来料检验页的「整批合格」操作旁边有一段**纯展示**的提示文案（`views/inventory/iqc/detail.vue:67-69`）：

```
勾选多行可整批合格；录入弹窗内 Tab 移动、Enter 保存、可"保存并下一行"；实测记录可留空
```

它被**当成"不合格原因/补充说明"的输入值**录进了库里。**当前业务字段里还剩 2 行脏值**（全库白名单列实查）：

| 表.列 | 主键 | 现值班值 | 归属 |
|---|---|---|---|
| `inventory_inbound_item.reject_reason` | item_id=1 | 选多行可整批合格 | 本任务（029，我们域） |
| `quality_scrap_order.reason` | scrap_id=3（SCR260924001） | 选多行可整批合格 | 006 线认领（抄写路径扩散） |

`quality_lot` / `quality_ncr` 两处已在 `dev-20260924-013` 的存量订正里**置空**，原值只保留在 `remark` 订正留痕里（不是污染）。

### 1.2 全库 dump 复扫：6 处命中，三类要分开看

以 16:36 全库快照 `jjx-docs/sql/backups/jjx_erp_db_backup_20260924-1636_before-defect-reason-correct.sql` 复扫「选多行可整批合格」共 **6 处**：

| 快照行号 | 位置 | 类别 | 处置 |
|---|---|---|---|
| 875 | `inventory_inbound_item.reject_reason` | 业务字段·脏值 | A 段订正（本任务） |
| 3106 | `quality_lot.defect_reason` | 业务字段·已订正 | 013 已置空，值留在 remark（不清） |
| 3202 | `quality_ncr.defect_reason` | 业务字段·已订正 | 同上 |
| 3383 | `quality_scrap_order.reason` | 业务字段·脏值 | 006 线认领（先 guard 后订正） |
| 4970 | `sys_oper_log` | 日志 | 不清 |
| 5157 | `sys_task` | 卡面记录 | 不清 |

### 1.3 扩散路径（为什么要一并堵源）

`QualityScrapOrderServiceImpl.createForAction()` 第 76 行把 NCR 的文本**原样抄**进报废单：

```java
order.setReason(ncr.getDefectReason());
```

⇒ 上游脏一个字，下游多一张脏单据。只订正源头、不改抄写路径 = 打补丁（用户 2026-09-23 定：要根治，判据是「这个病会不会再犯」）。

### 1.4 为什么之前拦不住

「选多行可整批合格」与代码里的提示串**差一个「勾」字**（代码是「勾选多行可整批合格…」），`git log -S` 全史也没有无「勾」的版本 ⇒ 它不是程序原样写入的，是**人手打/口述转写**的结果。所以：

- 靠"这条串来自哪个组件"去堵，堵不住；
- 靠模糊关键词（如「整批合格」）去堵，会误伤合法补充说明（例：`整批合格但有轻微色差`）。

---

## 2. 唯一出处：ReasonSanitizer

| 位置 | 文件 | 说明 |
|---|---|---|
| 后端 | `com.jjx.common.utils.ReasonSanitizer` | 注意仓库包名是 **utils（复数）**，`com.jjx.common.util` 不存在 |
| 前端 | `jjx-web/src/utils/reasonSanitizer.ts` | 与 `format.ts` / `object.ts` 同级 |

对外只暴露 3 个成员：

1. `BLACKLIST`（字符串常量集合）：**显式值清单**，逐条注来源（现行界面提示串 + 已实测变体），**禁止模糊/关键词匹配**。
2. `sanitize(String)`：命中清单 → 返回空；否则原样返回（保留 trim 语义）。
3. `isValidSupplement(String, String derived)`：有效补充说明判据 —— trim 后非空、**非纯符号/标点**、**不等于派生串**（防自证循环）、长度 ≤200。

口径：

- **长度 ≤200 超长截断 + 提示，不硬拦**（100 会误伤合规补充；底层列 `varchar(500)`，超长的整串由 013 的 500 截断兜底，两层不冲突）。
- **两侧清单必须逐字一致**：后端单测断言「清单内容」与前端常量文件一致（跨语言无法真同源，用断言代替）。
- 以后遇到新变体：**只加一行 + 注来源**，不改判据形态。

**生效范围（业务字段白名单 12 列，扫描与清洗都只认这些）**：

```
inventory_inbound_item.reject_reason / quality_lot.defect_reason / quality_ncr.defect_reason
quality_scrap_order.reason / inventory_iqc_return_order.reason / inventory_iqc_rework_order.reason
inventory_iqc_scrap_order.reason / production_work_report.defect_reason
production_operation_execution.defective_reason / sales_delivery.reject_reason
sales_sample_order.reject_reason / sales_sample_round.reject_reason
```

**排除**：`remark`/留痕列、`sys_oper_log`、`sys_task`、`sys_login_log`/`sys_notification`，以及盘点/挂起/取消/加急/释放类 `*_reason`。

---

## 3. 三段定案

### A. 存量数据订正（先 guard 后订正）

- `inventory_inbound_item` item_id=1：`reject_reason` 置 NULL（不造空串）；留痕写本表 `remark`（追加式，`varchar(500)` 控长）。
- `quality_scrap_order.reason`（scrap_id=3）：**由 006 线认领**（其域 + 其抄写路径）。注意该表 `remark` 只有 255 且已被 006 写过「报废处置单 #3 生成…」⇒ 订正留痕要**追加写短**。
- 备份：表级 guard + `jjx-docs/sql/backups/backup-index.tsv` 追加行。

### B. 加载即清洗（不是"按行状态回填"）

- 前端两处加载：`views/inventory/iqc/detail.vue`（明细行模型）与 `views/inventory/inbound/components/InboundInspectionDialog.vue:188`（有值即 `sanitize`）。
- **口径**：加载时命中清单即置空，**不按行状态（未判定/已判定）决定**——按状态跳过恰恰会把"可编辑行"这个唯一的提交通道留在污染面上。
- **边界（防隐藏写入路径）**：清洗只作用于**前端表单模型**，不自动回写库；已判定/锁定行不写库；库里旧值由 A 段订正脚本负责。

### C. 校验（前后端同口径）

- 行级校验 `views/inventory/iqc/iqcRowRules.ts:iqcRowProblems()`：判定不合格且无任何不合格检验项时，`isValidSupplement` 必须为真（原来的 `trim()` 判空升级为有效补充判据）。
- 前端输入长度 ≤200（截断 + 提示）。
- 后端 `InventoryInboundServiceImpl`（写入口 `:1104 item.setRejectReason(...)`、边界 A 校验 `:1060`）：入库前 `sanitize`，与前端同一套规则；`deriveIqcDefectReason` 格式不动。
- 单测：`sanitize` / `isValidSupplement` 做成**纯函数**用例（含：命中清单置空、纯符号、等于派生串、200 边界、清单两侧一致）。

---

## 4. 验收判据（改窄后可达成）

1. **白名单 12 列内命中黑名单值 = 0**（不含 `remark`/留痕列、`sys_oper_log`、`sys_task`）。
2. 加载被污染的旧行 → 前端不再显示提示文案；未判定行保存后库里也不再落提示文案。
3. 报废单抄写路径过 `sanitize`（006 线落地后一并验证）。
4. 门禁全绿：`mvn -o clean compile` / `mvn -o test` / `npx vue-tsc --noEmit` / `npm run validate`（`check:docs` 随 `validate`）。
5. 不需要重启后端（前端 HMR 即见；后端改动待用户统一安排重启）。

---

## 5. 明确不做

- 不清 `remark` 留痕、不动 `sys_oper_log`/`sys_task` 里的文本（那是审计痕迹）。
- 不做模糊/关键词匹配（避免误伤合法补充说明）。
- 不改 013 的派生格式与「边界 A」语义，不改 `quality_lot`/`quality_ncr` 已有订正结果。
- 不动本任务范围外的页面/模块；不重启任何服务。

---

## 6. 分工与遗留

| 项 | 归属 |
|---|---|
| ReasonSanitizer（前后端）+ B 加载即清洗 + C 校验与单测 + `inventory_inbound_item` 订正 | dev-20260924-029（Hermes 线） |
| `quality_scrap_order.scrap_id=3` 订正 + `QualityScrapOrderServiceImpl:76` 抄写路径过同一 util | dev-20260924-006 线认领人 |

**遗留**：在 006 线完成 `scrap_id=3` 订正前，验收判据第 1 条在该表上仍非 0 —— 记为 006 的收尾条件，不是本任务的回归。
