# JJX 工具操作安全规则

## 数据库任务备注

- `sys_task.remark` 上限为 500 字符。写入前必须先计算 `CHAR_LENGTH`，写入后再次查询状态、长度和正文确认生效。
- 不得用 `2>/dev/null`、空 `catch` 或等价方式吞掉数据库写入错误；命令失败必须保留错误并停止后续“已完成”报告。

## Git 文件恢复

- `git checkout -- <file>` 默认从暂存区恢复；文件已经 `git add` 后，它不能保证回到 `HEAD`。
- 明确回到当前提交时使用 `git checkout HEAD -- <file>`，或 `git restore --source=HEAD --staged --worktree <file>`。
- 恢复前先确认文件不含用户或其他会话的未提交修改。

## 后端打包 / 重启

- 对**运行中**的实例执行 `mvn package` 会就地覆写 fat jar → 报 `NoClassDefFoundError` 或接口超时（2026-09-23 已发生两次）。
- 正确顺序：**停服 → `mvn -o package -DskipTests` → 起服**；只验证改动是否编译用 `mvn -o compile`，并核验 `target/classes` 里的 class 确实是新写的。
- 能不能停/起/重启须由用户在当前请求里明确指示 —— 见仓库根 `AGENTS.md`「Service lifecycle — explicit user instruction required」。
