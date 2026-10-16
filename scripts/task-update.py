#!/usr/bin/env python3
"""sys_task 精确回写。🟢默认预览；🔴--execute 单行写库（免备份）。
前置：Python 3.10+、mysql、timeout；InnoDB sys_task；任务须已登记。
不接收SQL/表名/任意字段；claim、finish、note固定动作。dev-20261017-003。
"""
import argparse
from dataclasses import dataclass
from enum import IntEnum
import json
import os
from pathlib import Path
import re
import select
import subprocess
import sys
import tempfile
import time
import uuid


class GuardError(Exception):
    pass


class TaskStatus(IntEnum):
    PENDING = 0
    RUNNING = 1
    REVIEW = 2
    REJECTED = 3
    DISCARDED = 4
    COMPLETE = 10


@dataclass(frozen=True)
class Request:
    task: str
    action: str
    operator: str
    text: str
    expected: TaskStatus
    commit: str = ''

    def validate(self):
        if not re.fullmatch(r'dev-\d{8}-\d{3,6}', self.task):
            raise GuardError('必须提供完整精确dev任务码；不接受LIKE、通配符、列表或SQL')
        if self.action not in ('claim', 'finish', 'note'):
            raise GuardError('动作只允许claim/finish/note')
        if not self.operator.strip() or len(self.operator) > 50:
            raise GuardError('operator必须为1～50字')
        if not self.text.strip():
            raise GuardError('必须提供非空追加说明')
        if self.action == 'claim' and self.expected != TaskStatus.PENDING:
            raise GuardError('claim只允许待开始→进行中')
        if self.action == 'finish' and self.expected != TaskStatus.RUNNING:
            raise GuardError('finish只允许进行中→待审核')
        if self.action == 'finish' and not re.fullmatch(r'[0-9a-fA-F]{7,40}', self.commit):
            raise GuardError('finish必须提供有效提交哈希')


def literal(value):
    # Hex strings avoid SQL-mode/backslash/quote ambiguity, including multiline notes.
    return "''" if not value else f'CONVERT(0x{value.encode("utf-8").hex()} USING utf8mb4) COLLATE utf8mb4_unicode_ci'


class MysqlSession:
    """One mysql connection; Python decides COMMIT only after count + read-back checks."""
    def __init__(self, write=False):
        env = os.environ.copy()
        env['MYSQL_PWD'] = (env.get('DB_PASS', '123456') if write
                            else env.get('JJX_RO_PASS', 'jjx_ro_2026'))
        user = env.get('DB_USER', 'root') if write else env.get('JJX_RO_USER', 'jjx_ro')
        cmd = ['timeout', '30', 'mysql', '--no-defaults', '--batch', '--raw',
               '--skip-column-names', '--unbuffered', '--default-character-set=utf8mb4',
               '--connect-timeout=5', '-h', env.get('DB_HOST', '127.0.0.1'),
               '-P', env.get('DB_PORT', '3306'), '-u', user,
               env.get('DB_NAME', 'jjx_erp_db')]
        self.errors = tempfile.TemporaryFile()
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=self.errors, env=env, bufsize=0)
        self.buffer = b''

    def query(self, sql):
        tag = 'jjx_task_' + uuid.uuid4().hex
        try:
            payload = memoryview((sql.rstrip(';\n ') + ";\nSELECT '" + tag + "';\n").encode())
            while payload:
                size = self.proc.stdin.write(payload)
                if not size:
                    raise BrokenPipeError("mysql写入中断")
                payload = payload[size:]
            self.proc.stdin.flush()
        except (BrokenPipeError, OSError) as exc:
            raise GuardError('mysql连接已关闭：' + self.error_text()) from exc
        rows = []
        deadline = time.monotonic() + 20
        while True:
            while b'\n' not in self.buffer:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or not select.select([self.proc.stdout], [], [], remaining)[0]:
                    raise GuardError('mysql响应超时；未确认COMMIT，不得盲目重试，请只读核查任务')
                chunk = os.read(self.proc.stdout.fileno(), 65536)
                if not chunk:
                    raise GuardError('mysql失败：' + self.error_text())
                self.buffer += chunk
            line, self.buffer = self.buffer.split(b'\n', 1)
            value = line.decode('utf-8').rstrip('\r')
            if value == tag:
                return rows
            if value:
                try:
                    rows.append(json.loads(value))
                except json.JSONDecodeError as exc:
                    raise GuardError('mysql返回非预期内容，拒绝提交') from exc

    def error_text(self):
        self.errors.seek(0)
        return self.errors.read().decode('utf-8', errors='replace').strip() or '无错误详情'

    def close(self):
        self.proc.stdin.close()  # Disconnect rolls back any transaction without COMMIT.
        self.proc.wait(timeout=35)
        self.proc.stdout.close()
        self.errors.close()


COLUMNS = ('task_id', 'task_code', 'status', 'assignee_name', 'start_time',
           'completed_time', 'update_by', 'remark', 'description')
SELECT_ROW = 'SELECT JSON_OBJECT(' + ','.join(f"'{c}',{c}" for c in COLUMNS) + ", 'remark_length',CHAR_LENGTH(remark)) FROM sys_task"


def make_plan(req, before):
    if before['task_code'] != req.task or before['status'] != int(req.expected):
        raise GuardError('任务身份或状态与预期不一致；未写入')
    if req.action == 'finish' and before['assignee_name'] != req.operator:
        raise GuardError('完成回写的执行者与领取人不一致；未写入')
    note = f'[{req.operator}] {req.action}: {req.text}'
    if req.commit:
        note += f'；commit={req.commit}'
    previous = before['remark'] or ''
    if before['remark_length'] not in (None, len(previous)):
        raise GuardError('备注字符长度校验不一致')
    field = 'remark' if len(previous + ('\n' if previous else '') + note) <= 500 else 'description'
    old = before[field] or ''
    combined = old + ('\n' if old else '') + note
    if len(combined.encode('utf-8')) > 65535:
        raise GuardError('追加后超过TEXT字节上限；未写入')
    assignments = [f'{field}={literal(combined)}', f'update_by={literal(req.operator)}']
    status = req.expected
    if req.action == 'claim':
        status = TaskStatus.RUNNING
        assignments += [f'status={int(status)}', 'start_time=NOW()', f'assignee_name={literal(req.operator)}']
    elif req.action == 'finish':
        status = TaskStatus.REVIEW
        assignments += [f'status={int(status)}', 'completed_time=NOW()']
    # Both business key + primary key + prior status + owner are fixed predicates.
    owner = 'NULL' if before['assignee_name'] is None else literal(before['assignee_name'])
    where = (f' WHERE task_code={literal(req.task)} AND task_id={int(before["task_id"])}'
             f' AND status={int(req.expected)} AND assignee_name <=> {owner}')
    sql = 'UPDATE sys_task SET ' + ','.join(assignments) + where
    return {'sql': sql, 'field': field, 'combined': combined, 'status': status, 'note': note}


def update_task(db, req, execute=False):
    req.validate()
    in_transaction = False
    committing = False
    try:
        if execute:
            engine = db.query("SELECT JSON_OBJECT('engine',ENGINE) FROM information_schema.TABLES WHERE TABLE_SCHEMA=CONVERT(DATABASE() USING utf8mb4) COLLATE utf8mb4_unicode_ci AND TABLE_NAME='sys_task'")
            if len(engine) != 1 or engine[0]['engine'] != 'InnoDB':
                raise GuardError('sys_task不是InnoDB，无法保证回滚；拒绝写入')
            db.query('SET SESSION sql_safe_updates=1; SET SESSION innodb_lock_wait_timeout=5; START TRANSACTION')
            in_transaction = True
        rows = db.query(SELECT_ROW + f' WHERE task_code={literal(req.task)} LIMIT 2' + (' FOR UPDATE' if execute else ''))
        if len(rows) != 1:
            raise GuardError(f'精确任务码必须命中1行，实际{len(rows)}行；未写入')
        before = rows[0]
        plan = make_plan(req, before)
        if execute:
            counts = db.query(plan['sql'] + "; SELECT JSON_OBJECT('affected_rows',ROW_COUNT())")
            if len(counts) != 1 or counts[0]['affected_rows'] != 1:
                raise GuardError('UPDATE影响行数不是1；回滚')
            after = db.query(SELECT_ROW + f' WHERE task_code={literal(req.task)} LIMIT 2')
            if len(after) != 1:
                raise GuardError('写后回查行数异常；回滚')
            actual = after[0]
            if (actual['task_id'] != before['task_id'] or actual['status'] != int(plan['status'])
                    or actual[plan['field']] != plan['combined'] or actual['update_by'] != req.operator):
                raise GuardError('写后字段回查不一致；回滚')
            if plan['field'] == 'description' and actual['remark'] != before['remark']:
                raise GuardError('溢出追加不应修改原备注；回滚')
            if req.action == 'claim' and (actual['assignee_name'] != req.operator or not actual['start_time']):
                raise GuardError('领取人/开始时间回查异常；回滚')
            if req.action == 'finish' and (actual['assignee_name'] != req.operator or not actual['completed_time']):
                raise GuardError('完成人/完成时间回查异常；回滚')
            committing = True
            db.query('COMMIT')
            in_transaction = False
        return {'task_code': req.task, 'action': req.action, 'execute': execute,
                'before_status': TaskStatus(before['status']).name.lower(),
                'after_status': plan['status'].name.lower(), 'append_to': plan['field'],
                'remark_chars_before': before['remark_length'], 'append_text': plan['note'],
                'affected_rows': 1 if execute else 0, 'committed': execute}
    except BaseException as exc:
        if in_transaction:
            try:
                db.query('ROLLBACK')
            except Exception as rollback_exc:
                raise GuardError(f'{exc}；ROLLBACK确认失败：{rollback_exc}；关闭连接并只读核查，禁止自动重试') from exc
        if committing:
            raise GuardError('COMMIT结果未确认；必须只读核查目标任务，不得直接重试') from exc
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['claim', 'finish', 'note'])
    parser.add_argument('--task', required=True, help='完整精确dev任务码')
    parser.add_argument('--operator', required=True)
    parser.add_argument('--expect-status', required=True, choices=[s.name.lower() for s in TaskStatus])
    text = parser.add_mutually_exclusive_group(required=True)
    text.add_argument('--text')
    text.add_argument('--text-file', type=Path, help='UTF-8追加说明（不接受SQL文件）')
    parser.add_argument('--commit', default='', help='finish必填，须为本任务已存在的本地提交')
    parser.add_argument('--execute', action='store_true', help='实际单行写库；默认仅只读预览')
    args = parser.parse_args()
    db = None
    try:
        content = args.text if args.text is not None else args.text_file.read_text(encoding='utf-8-sig')
        req = Request(args.task, args.action, args.operator, content, TaskStatus[args.expect_status.upper()], args.commit)
        req.validate()
        if req.action == 'finish':
            commit = subprocess.run(['git', 'show', '-s', '--format=%B', req.commit + '^{commit}'],
                                    cwd=Path(__file__).resolve().parents[1], capture_output=True, text=True, timeout=5)
            if commit.returncode or req.task not in commit.stdout:
                raise GuardError('提交不存在或提交信息未包含本任务码；拒绝完成回写')
        db = MysqlSession(write=args.execute)
        print(json.dumps(update_task(db, req, args.execute), ensure_ascii=False))
        return 0
    except (GuardError, OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        return 1
    finally:
        if db is not None:
            db.close()


if __name__ == '__main__':
    sys.exit(main())
