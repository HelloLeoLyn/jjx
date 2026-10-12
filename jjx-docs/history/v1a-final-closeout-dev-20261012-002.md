# 公共附件 V1-A 收口报告（含遗留任务登记）

- 任务码：dev-20261012-002（公共附件 V1-A）
- 日期：2026-10-12
- 本轮动作：登记独立遗留任务 + 代码侧 B 最小运行时复验 + V1-A 验收项盘点
- 未执行：数据库迁移、服务重启、物理清理、业务模块数据修改、全量测试、Git commit/push

## 1. 本轮结论

1. 遗留问题已登记为独立任务 **dev-20261012-011**（P2，status=0 待处理），并已挂接到 dev-20261012-002（描述追加 + remark 指针）。查重结果：库内无同类任务（001 是清理脚本、003 是回填脚本、006 是图标迁移）。
2. 代码侧 B 的**运行时复验未通过（BLOCKED）**：当前运行版本早于 B 的改动，探针实测仍是旧行为 → 不记 PASS，需人工重新打包重启后复验（步骤见 §4）。
3. V1-A 验收项盘点：**已通过 9 项 / 部分通过 2 项 / 未验证 5 项 / 真正阻塞 1 项**（明细见 §3）。
4. 建议：**有条件 GO**（可进入 V1-B 前端开发），放行前须完成 §4 的重启 + 3 条最小复验。

## 2. 遗留任务登记结果

| 项 | 结果 |
|---|---|
| 任务号 | `dev-20261012-011`（task-register.sh 原子取号，登记人 hermes，P2，status=0 待处理） |
| 标题 | 质量模板历史附件引用异常及公共附件保护边界核查 |
| 描述 | 2143 字符（TEXT）：【背景】【证据】【影响范围】【已做】【待核查事项】1~5【后续处理建议】【关联】dev-20261012-002 |
| 挂接当前任务 | dev-20261012-002 描述追加「【追加 2026-10-12｜质量模板历史附件引用异常（独立遗留，待处理）】」段（现 393 字符）；`remark` 指针 74 字符（< 500） |
| 数据/迁移 | **未**修改 `quality_template_registry`；**未**恢复附件 5~11；**未**生成或执行 274 |

待核查重点（该任务的落地口径）：① `file_id` 的写入来源（是否还有同类 seed/导入会再产生占位号段）；② `protectedFile()` 与业务模块的保护职责边界（保护是否应由引用方表带内容指纹声明，而非仅凭号位匹配）。

## 3. V1-A 验收项盘点

### 3.1 已通过（运行时实测）

| # | 项 | 证据摘要 |
|---|---|---|
| 1 | 迁移后结构 / 引用数量 / 回填异常 | 271/272/273 均记账归档；`sys_attachment_ref` 19 列 + 唯一键 + 2 外键 + CHECK 齐；回填 2 条、异常 0 |
| 2 | 临时上传 / 绑定 / 解绑 / 跨业务复用 | 273 后：TEMP（biz 为空、+7 天）→ BOUND（清过期时间）→ ORPHAN（+30 天）；同键幂等、异内容拒绝、重复解绑不移动时钟 |
| 3 | SHA-256 重复检测（不做物理去重） | 按来源范围命中且不跨来源；9 个同 sha 附件 9 条不同路径 |
| 4 | 分类 / 展示名 / 备注 / 来源过滤 | 元数据写入回读一致；非法 `documentType`、负 `sortOrder` 被拒；refs 列表按 `sourceType+sourceId` 精确过滤 |
| 6 | 已锁定单据禁止修改附件 | 受控/硬引用解绑被拒且**零副作用**（ref 1 与附件 12 未被改动）；旧删除入口同一 guard |
| 7 | 旧列表 / 下载 / 删除接口兼容 | 旧 list/download 正常；未确认来源的历史 icon 被拒（设计）；旧 DELETE 唯一上下文时解绑该引用、不删行 |
| 9 | 临时 7 天 / 解绑 30 天保留期 | `cleanup-preview` 实测 `RETENTION_PERIOD` + deadline 上屏；重新绑定后回到 BOUND 并清除资格 |
| 10 | 物理删除仍关闭 | 触发器实测 `ERROR 1644`；`permanent`/`permanent-expired` 均拒；两个旧清理脚本写模式禁用；预演 `physicalDeleteEnabled=false` |
| 12 | 批量上传（旧接口） | `/attachment/batch-upload` 正常返回 ids；重复内容响应头 `X-Attachment-Duplicate-Count` 有值 |

### 3.2 部分通过

| # | 项 | 通过部分 | 未通过部分 |
|---|---|---|---|
| 5 | 五类业务来源的附件权限 | inquiry/quotation/product 正向绑定通过；未确认来源 fail-closed；非管理员（yuanhui）读 inquiry/product 均被拒（`sales:inquiry:view` / `product:view`） | **sales_order、sample 库内 0 单据** → 两类来源无法正向验证 |
| 8 | 已发布作业规范及其他历史硬引用仍可读取 | `product_work_spec_item(DRAWING)`=0 断链、`engineering_film`=0 断链 → 正常 | `quality_template_registry` 19 条异常引用 → **已移交 dev-20261012-011**，不在 V1-A 数据修复范围 |

### 3.3 未验证

| 项 | 原因 |
|---|---|
| 代码侧 B 的运行时有效性（硬引用误伤防护） | 运行版本早于改动（§4），需重启后复验 |
| 双用户越权细分场景 | 仅账户级权限做了负向验证，未做跨用户/跨部门的行级越权组合 |
| 并发绑定/解绑（唯一键与行锁） | 本轮未构造并发，仅验证幂等 |
| TEMP 跨用户访问、过期时钟边界 | 需构造第二用户与过期数据 |
| 19 条模板数据修复本身 | 独立任务（dev-20261012-011），待拍板 |

### 3.4 真正阻塞项

1. **后端需重启到含代码侧 B 的构建**（当前 jar 早于源码改动），随后完成 §4 的 3 条最小复验——这是 V1-A 结项的唯一技术阻塞。
2. （非代码阻塞）`sales_order` / `sample` 无测试单据 → 两个来源的正向验证需造单据后才能完成。

## 4. 代码侧 B 的运行时复验（本轮实测 BLOCKED）

版本时间线（判定依据）：

```sh
ps -eo pid,lstart,cmd | grep '[j]ava -jar'
# 3747720 Mon Oct 12 12:24:53 2026 java -jar …/jjx-server-1.0.0.jar
ls -l --time-style=+%F_%H:%M:%S jjx-server/target/jjx-server-1.0.0.jar          # 12:24:39
ls -l --time-style=+%F_%H:%M:%S jjx-server/src/main/java/com/jjx/system/service/AttachmentAccessService.java  # 12:31:06（B 的改动）
```

→ **运行版本早于 B 的改动**，因此 B 不能记运行时 PASS。

最小探针（隔离测试数据，用附件 25——它的唯一"保护"来源就是 `quality_template_registry.file_id=25` 这条误引用）：

```
POST /system/attachment/refs {attachmentId:25, sourceType:inquiry, sourceId:1}  -> code=200 refId=11
DELETE /system/attachment/refs/11
-> code=500 "受控、已发布或历史硬引用附件禁止解除；需由原业务流程处理"      ← 旧行为（B 未生效）
```

结论：**误保护在当前运行版本里仍然存在**；B 的防护目前只在静态层面成立（判定 SQL 与 `fillHasFile` 已改、编译通过）。

人工重启后的 3 条最小复验（照抄即可）：

1. **B 生效**：`DELETE /system/attachment/refs/11`（本轮遗留的活动引用，指向附件 25）→ 期望 `code=200`；DB：ref 11 `deleted=1`、`deleted_at` 落值。注意预期状态：附件 25 是**历史行（LEGACY）**，解绑时按设计只写 `expires_at=now+30d`、`upload_state` 保持 `LEGACY`（`AttachmentRefService.unlink` 的非 LEGACY 才转 ORPHAN），预演仍显示 `LEGACY_REVIEW_REQUIRED` —— 这是预期行为，不是缺陷。若要看完整的"ORPHAN + 30 天 + RETENTION_PERIOD"，请用**新上传**的附件走一遍（新接口 `/attachment/upload` 建档 = BOUND → 绑定 → 解绑 → ORPHAN）。
2. **不误伤合法保护**：对任一"有效硬引用"附件做解绑 → 期望仍被拒。注意当前库内**尚无可用的有效样本**（有效模板引用=0、`DRAWING`=0、`engineering_film`=0、`product.work_spec_json`=0、受控标记只存在于已删除的 5~11 上）；若要实测，需先按 dev-20261012-011 的决策恢复 7~11（它们是 `is_controlled/released/is_current=1` 的真实受控图纸）。在此之前该分支只能给静态证据。
3. **模板不再误示真**：`GET /production/quality-templates`（列表）与详情 → 19 条异常模板应为 `hasFile=false` 且 `fileId` 为 null，页面下载入口不出现。

未重启前，凡涉及 B 的结论一律只标"静态通过/运行时未验证"。

## 5. 剩余事项（人工）

1. 重新打包 + 重启后端：`mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true package -DskipTests` → 重启 `java -jar jjx-server/target/jjx-server-1.0.0.jar`（本会话不启停服务）。
2. 按 §4 跑 3 条最小复验。
3. `sales_order` / `sample`：造测试单据后补两类来源的正向验证（当前库内 0 单据）。
4. 测试数据清理（需点头）：附件 14~33、引用 6/8/9/10/11，以及 `upload/attachment-temp/` 的 5 个孤儿 `.bin`；建议与 dev-20261012-011 的数据修复同批执行。
5. dev-20261012-011 的两项核查（`file_id` 写入来源定位、`protectedFile()` 与业务模块的职责边界）排期。

## 6. GO / NO-GO 建议

**建议：有条件 GO**（V1-A 可进入 V1-B 前端开发）。放行条件：

1. 重启到含代码侧 B 的构建，并通过 §4 的 3 条最小复验（B 的运行时有效性是硬引用误伤防护成立的前提）；
2. V1-B 的模板附件入口在数据修复前保持"无文件"语义（`hasFile=false`、无下载链接），直到业务重新上传模板文件；
3. `sales_order` / `sample` 两个来源在造单据并验证前，不得对外宣称"已验证"。

若要求"运行时全绿才放行"，则本项应记 **NO-GO**，唯一缺口就是上述第 1 条（不需要再改代码，只需打包重启 + 复验）。我按"零代码缺口、仅缺一次人工重启"的口径给有条件 GO。

不建议把 19 条模板数据修复作为 V1-A 的放行条件——它已独立登记为 dev-20261012-011，且当前代码（B）已能阻止它造成新的误判与错误展示。

## 7. 最终运行时补验（后端已重打包重启，2026-10-12 15:13 起）

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 运行中的 jar 含最新代码 | **PASS** | 进程启动 `15:13:42` > jar `15:13:34` > classes `15:13:30` > 源码 `12:30/12:31`；从运行 jar 内解出的 class 含 B 的判定串：`AttachmentAccessService.class` → `a2.create_time`，`QualityTemplateRegistryServiceImpl.class` → `SELECT create_time FROM sys_attachment WHERE id=? AND deleted=0` |
| 2 | 附件 25 的活动引用 refId=11 可解绑 | **PASS** | `DELETE /system/attachment/refs/11` → `code=200`（此前旧版本为 500「受控、已发布或历史硬引用附件禁止解除」）；DB：ref 11 `deleted=1, deleted_at=15:16:50`；附件 25 `upload_state=LEGACY`（历史行按设计不转 ORPHAN）、`expires_at=2026-11-11 15:16:50`（+30 天） |
| 3 | 合法受控附件仍受保护 | **未验证（无样本）** | 现有库内**没有任何有效的硬引用/受控样本**：受控·已发布·现行标记 = 0、`trace_id` 非空 = 0、文本证据表联查命中 = 0 行（`review_flow` 5 行 / `sales_customer` 12 行，均不含现存附件 id）、有效模板引用 = 0。按约束不恢复历史数据、不伪造业务关联，故此分支只保留静态证据（判定分支与文案未改） |
| 4 | 19 条异常模板 hasFile=false、fileId=null | **PASS** | 列表接口 `/production/quality-template/page`（100 行）中 `hasFile=true` 或 `fileId!=null` 的行数 = **0**；19 条目标在列表中 19/19 合规；详情接口逐条 `id=37/5/30/24/65/71/26/78/19/39/90/49/50/58/59/48/35/54/31` 全部 `fileId=null、hasFile=false` → 前端无下载入口（"可下载=否"） |
| 5 | 解绑不影响其他业务引用 | **PASS** | 解绑前后对比 `sys_attachment_ref`：仅 id=11 由 0→1（deleted），其余 9 条（1/2/4/5/6/7/8/9/10）状态一字未变；`refs?sourceType=inquiry&sourceId=1`、`quotation&sourceId=2`、`refs/product/1` 三个列表接口返回正常 |

结论：**代码侧 B 的运行时有效性 = PASS**（§3.3「未验证」中的 B 一项移入已通过；§3.4 的技术阻塞清零）。唯一未验证分支是"合法保护不被削弱"的实机样本，需等 dev-20261012-011 决定是否恢复 7~11（真实受控图纸）后才能实测。

## 8. 最终结论

| 项 | 结论 |
|---|---|
| 遗留任务 | `dev-20261012-011`（待处理，已挂接本任务）——19 条模板引用、`file_id` 写入来源、`protectedFile()` 职责边界 |
| 遗留项（非阻塞） | ① sales_order / sample 缺测试单据，两类来源正向未验证；② 并发绑定解绑、双用户越权细项、TEMP 跨用户与过期边界未验证；③ 测试数据（附件 14~33、引用 4~11、5 个孤儿 `.bin`）未清理，建议与 011 的数据修复同批 |
| V1-A 结论 | **GO** —— 功能面运行时验收通过（含 P0 修复与代码侧 B），可进入 V1-B 前端开发；上述三项为不影响 V1-B 开工的遗留项 |


