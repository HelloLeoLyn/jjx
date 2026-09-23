# JJX 工具操作安全规则

## 数据库任务备注

- `sys_task.remark` 上限为 500 字符。写入前必须先计算 `CHAR_LENGTH`，写入后再次查询状态、长度和正文确认生效。
- 不得用 `2>/dev/null`、空 `catch` 或等价方式吞掉数据库写入错误；命令失败必须保留错误并停止后续“已完成”报告。

## Git 文件恢复

- `git checkout -- <file>` 默认从暂存区恢复；文件已经 `git add` 后，它不能保证回到 `HEAD`。
- 明确回到当前提交时使用 `git checkout HEAD -- <file>`，或 `git restore --source=HEAD --staged --worktree <file>`。
- 恢复前先确认文件不含用户或其他会话的未提交修改。
