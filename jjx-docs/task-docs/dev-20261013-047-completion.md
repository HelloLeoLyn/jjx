# dev-20261013-047 完成卡：未保存草稿的 TEMP 附件清理（口径 A）

> 任务码：dev-20261013-047 ｜ 日期：2026-10-14 ｜ 类型：completion ｜ 执行：Hermes
> 来源：dev-20261013-043（S5 行级附件）拆出；本卡与该任务 remark 同源。

---

## 0）一句话

用户拍板 **口径 A**：过期 TEMP 附件**不做物理删除**，改为「标记删除 → 进回收站」，管理员在**文件管理**页可清理、可恢复。物理删除（口径 B）与 V1-A 删除守卫保持不动。

## 1）拍板记录（2026-10-14，用户）

| 口径 | 含义 | 结论 |
|---|---|---|
| A | 只标记删除（进回收站，可恢复），不碰磁盘与删除守卫 | **采纳** |
| B | 放开物理删除（删库行 + 删磁盘文件，不可恢复） | 不采纳；保留为未来独立评估 |
| C | 不清理，只做可见性提示 | 未采纳 |

放行理由：库内 TEMP 仅 3 行、无到期压力（7 天保留期到 2026-10-21）；先打通「能清理、可恢复」的链路，磁盘释放需求出现时再评估 B。

## 2）改动前的现状（证据）

- `AttachmentLifecycleService`：只有 admin 只读预演 `preview()`，注释写明「no scheduler or physical deletion entrypoint」。
- `SysAttachmentServiceImpl.java:563/568`：`permanentDelete` / `permanentDeleteExpired` 直接抛「物理删除已禁用」→ 文件管理页「彻底删除 / 清理30天前」两个按钮点了必报错。
- 迁移 271 触发器 `trg_attachment_v1_no_delete` 仍在库上（BEFORE DELETE → SIGNAL 45000）。
- 未保存单据的 TEMP 行既不在回收站（deleted=0），也没有任何删除入口 → 永久滞留。

## 3）本次改动（提交 a2c77d19）

1）`AttachmentLifecycleService`：把「可清理」判定抽成私有 `evaluate(id)`（返回 reason / deadline / eligible）供预演与执行共用；新增 `recycleEligible(afterId, limit)`（admin、limit ≤ 100、逐行 `logicalDelete`，跳过 被引用 / 受控或历史 / LEGACY / 共享路径 / **已回收** / **未到期**），返回 `recycled / skipped / nextAfterId / physicalDeleteEnabled=false`。
2）`SysAttachmentRefController`：新增 `POST /system/attachment/refs/cleanup-apply`（`@Log` 记「过期附件移入回收站」）。
3）`SysAttachmentServiceImpl`：两处禁用文案改为指向新入口（物理删除仍禁用）。
4）前端 `api/system/attachmentRef.ts` 加 `cleanupApply`；`views/system/file/index.vue` 回收站区块：「清理过期附件（移入回收站）」= 预演 → 确认 → 执行（游标最多 5 页）；「彻底删除」置灰 + tooltip 说明策略；卡片标题由「30天后自动清理」改为「可恢复；物理删除已禁用」。
5）新增测试 `AttachmentLifecycleRecycleTest`（7 项）。

## 4）验证

- `mvn -o -Dtest='Attachment*Test' test` → 27 项全过（本任务 7 项；含「被引用/受控/未到期/已回收 一律跳过」「limit 越界拒绝」「非 admin 拒绝」）。
- `npx vue-tsc --noEmit` → 0；`npm run check:status-enums` → 0 新增；`npm run check:docs` → 通过；`prettier --check` 两文件通过。
- **未验证**：运行态/浏览器（需用户重启后端后按「文件管理 → 回收站 → 清理过期附件」实测）；未做库内数据订正。

## 5）操作路径（交付给用户）

1）系统管理 → 文件管理 → 底部「回收站」。
2）点「清理过期附件（移入回收站）」→ 弹窗显示将移入的数量 → 确认。
3）被移入的行出现在回收站列表，可点「恢复」还原；「彻底删除」按钮按当前策略置灰。

## 6）遗留与边界

1）口径 B（真删磁盘）未实现，`permanentDelete` / `permanentDeleteExpired` 与触发器守卫保持禁用。
2）保留期口径沿用既有判定：TEMP 建后 7 天、解绑后 30 天、取两者较晚者；不做主动扫描（无定时任务）。
3）「取消/关闭草稿即释放」未做：草稿在服务端没有「取消」事件，现由保留期兜底；如需即时释放，另立卡。
4）后端仍有缺口：无「按 id 查 TEMP 状态」与 `GET /refs/temp/mine`（dev-20261012-034 已记录）。

## 7）环境坑（供后续 agent）
Maven 增量编译会把 IDE（VS Code Java LS / JDT）写入 `target/classes` 的 **Unresolved compilation problems 占位类**当作「已是最新」而跳过重编，表现为测试编译报 `com.jjx.system.mapper.SysAttachmentMapper cannot be converted to SysAttachmentMapper`（类文件描述符是裸名 `LSysAttachmentMapper;`）。处置：`touch` 改动的源文件后重跑 `mvn -o compile`，再用 `javap -s -p` 或 `grep -c 'Unresolved compilation problems' <class>` 复核。

## 8）追加：用户实测反馈后的修复（提交 db759e73）

1）**现象**：清理后点「恢复」报 `回收站附件未绑定业务来源（临时或仅引用），不能按业务来源恢复: 75`。根因：`restoreAttachment` 只按业务来源恢复，而口径 A 清理的正是「无业务来源」的 TEMP 行 ⇒ 清理**不可逆**（首版交付的"可恢复"是错的）。
2）**修复**：无业务来源（`bizType` 空 或 `bizId` 空）的回收站行，恢复只回写 `deleted` 标记、不做来源校验；有业务来源的行仍走原口径（不绕过来源权限）。新增 `SysAttachmentRestoreTest`（3 项：无来源可恢复 / 有来源仍校验 / 不在回收站报错）。
3）**页面新增「待清理」区**（用户要求）：预演行补出 `fileName/fileSize/uploadState/createTime/expiresAt/deadline`，文件管理页在回收站上方新增「待清理（N 个）」表格 + 「刷新预演」按钮；清理后自动刷新两个列表。
4）**测试载体**（只改 id 75 这一条无引用 TEMP，原值如下，可复原）：`create_time` 2026-10-14 15:29:13 → 2026-10-04 18:47:58；`expires_at` 2026-10-21 15:29:13 → 2026-10-05 18:47:58。两列都要改：保留期取「创建+7天」与 `expires_at` 中较晚者。
5）**明确不做**：只删磁盘文件、保留数据库记录的"半删除"——会让行指向不存在的文件（下载/预览报错），状态更乱，且不能省下口径 B 的论证成本。见 §6 与 sys_task remark。
