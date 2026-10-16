#!/usr/bin/env python3
"""任务回写护栏回归。默认🟢纯单元测试；--mysql 为🟡会话临时表测试。
前置：Python 3.10+；--mysql还需mysql/timeout和DB_USER/DB_PASS（默认仓库连接）。
临时sys_task遮蔽业务表；无持久建表/业务数据写入，不启服务。dev-20261017-003。
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('task_update', Path(__file__).with_name('task-update.py'))
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)
TASK = 'dev-20261017-003'


def req(action='note', text='验证追加', expected=m.TaskStatus.RUNNING, operator='Codex'):
    return m.Request(TASK, action, operator, text, expected, 'abcdef0123' if action == 'finish' else '')


class InputTests(unittest.TestCase):
    def test_rejects_wildcard_list_and_sql_before_any_query(self):
        class NoQuery:
            def query(self, _):
                raise AssertionError('非法输入不得访问DB')
        for task in ['', 'dev-%', 'dev-20261017-003,dev-20261017-004', "dev-20261017-003' OR 1=1 --"]:
            with self.subTest(task=task), self.assertRaises(m.GuardError):
                m.update_task(NoQuery(), m.Request(task, 'note', 'Codex', 'x', m.TaskStatus.RUNNING), True)

    def test_rejects_empty_operator_or_text(self):
        for r in [req(operator=' '), req(text=' '), req(operator='x'*51)]:
            with self.assertRaises(m.GuardError): r.validate()

    def test_rejects_invalid_transition_and_missing_commit(self):
        for r in [req('claim'), req('finish', expected=m.TaskStatus.PENDING),
                  m.Request(TASK, 'finish', 'Codex', 'x', m.TaskStatus.RUNNING)]:
            with self.assertRaises(m.GuardError): r.validate()

    def test_sql_literal_is_hex_not_executable_text(self):
        value = "'; UPDATE sys_task SET status=2; -- \\ \n中文😀"
        sql = m.literal(value)
        self.assertNotIn('UPDATE', sql)
        self.assertEqual(bytes.fromhex(sql.split('0x')[1].split(' ')[0]).decode(), value)


class MysqlTests(unittest.TestCase):
    def setUp(self):
        self.db = m.MysqlSession(write=True)
        self.addCleanup(self.db.close)
        self.db.query('''CREATE TEMPORARY TABLE sys_task (
            task_id BIGINT PRIMARY KEY, task_code VARCHAR(50), status TINYINT,
            assignee_name VARCHAR(50), start_time DATETIME, completed_time DATETIME,
            update_by VARCHAR(50), remark VARCHAR(500), description TEXT
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci''')
        self.db.query("INSERT INTO sys_task VALUES (1,"+m.literal(TASK)+",1,'Codex',NOW(),NULL,'old',"+
                      m.literal('原备注')+','+m.literal('原描述')+"),(2,'dev-20261017-004',0,NULL,NULL,NULL,'old','other','other-desc')")

    def snapshot(self):
        return self.db.query(m.SELECT_ROW+' ORDER BY task_id')

    def test_default_preview_has_no_writes(self):
        before = self.snapshot()
        result = m.update_task(self.db, req())
        self.assertFalse(result['committed'])
        self.assertEqual(result['affected_rows'], 0)
        self.assertEqual(before, self.snapshot())

    def test_append_preserves_original_and_other_task(self):
        other = self.snapshot()[1]
        result = m.update_task(self.db, req(text="包含引号'、反斜线\\、换行\n及😀"), True)
        after = self.snapshot()
        self.assertEqual(after[1], other)
        self.assertEqual(after[0]['remark'], '原备注\n'+result['append_text'])
        self.assertEqual(after[0]['description'], '原描述')
        self.assertEqual(result['affected_rows'], 1)
        self.assertTrue(result['committed'])

    def test_note_overflow_routes_to_description_without_clobbering_remark(self):
        self.db.query('UPDATE sys_task SET remark='+m.literal('备'*500)+' WHERE task_id=1')
        result = m.update_task(self.db, req(), True)
        after = self.snapshot()[0]
        self.assertEqual(result['append_to'], 'description')
        self.assertEqual(after['remark'], '备'*500)
        self.assertEqual(after['description'], '原描述\n'+result['append_text'])

    def test_description_overflow_rolls_back(self):
        self.db.query('UPDATE sys_task SET remark='+m.literal('备'*500)+',description='+m.literal('x'*65530)+' WHERE task_id=1')
        before = self.snapshot()
        with self.assertRaisesRegex(m.GuardError, 'TEXT'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_missing_task_is_rejected_before_update(self):
        before = self.snapshot()
        r = m.Request('dev-20261017-999', 'note', 'Codex', 'x', m.TaskStatus.RUNNING)
        with self.assertRaisesRegex(m.GuardError, '实际0行'):
            m.update_task(self.db, r, True)
        self.assertEqual(before, self.snapshot())

    def test_duplicate_task_is_rejected_before_update(self):
        self.db.query('UPDATE sys_task SET task_code='+m.literal(TASK)+' WHERE task_id=2')
        before = self.snapshot()
        with self.assertRaisesRegex(m.GuardError, '实际2行'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_status_mismatch_preserves_rows(self):
        before = self.snapshot()
        with self.assertRaisesRegex(m.GuardError, '状态'):
            m.update_task(self.db, req(expected=m.TaskStatus.REVIEW), True)
        self.assertEqual(before, self.snapshot())

    def test_claim_cas_and_second_claim_rejected(self):
        r = m.Request('dev-20261017-004', 'claim', 'Codex', '领取', m.TaskStatus.PENDING)
        m.update_task(self.db, r, True)
        after = self.snapshot()
        self.assertEqual(after[1]['status'], m.TaskStatus.RUNNING)
        self.assertEqual(after[1]['assignee_name'], 'Codex')
        self.assertIsNotNone(after[1]['start_time'])
        with self.assertRaises(m.GuardError): m.update_task(self.db, r, True)
        self.assertEqual(after, self.snapshot())

    def test_finish_checks_owner_and_writes_completion_time(self):
        before = self.snapshot()
        with self.assertRaisesRegex(m.GuardError, '领取人'):
            m.update_task(self.db, req('finish', operator='other'), True)
        self.assertEqual(before, self.snapshot())
        m.update_task(self.db, req('finish'), True)
        after = self.snapshot()[0]
        self.assertEqual(after['status'], m.TaskStatus.REVIEW)
        self.assertIsNotNone(after['completed_time'])
        self.assertIn('commit=abcdef0123', after['remark'])

    def test_accidental_missing_where_multirow_update_is_rolled_back(self):
        # Deliberately inject the original accident. LIMIT allows SQL_SAFE_UPDATES,
        # so ROW_COUNT checking (independent defense) must roll back both rows.
        before = self.snapshot()
        original = m.make_plan
        def faulty(*args):
            plan = original(*args)
            plan['sql'] = "UPDATE sys_task SET remark='误刷' LIMIT 2"
            return plan
        with patch.object(m, 'make_plan', faulty), self.assertRaisesRegex(m.GuardError, '影响行数不是1'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot(), '两行必须全部恢复，而非只报错')

    def test_one_wrong_row_update_is_rolled_back(self):
        before = self.snapshot()
        original = m.make_plan
        def faulty(*args):
            plan = original(*args)
            plan['sql'] = "UPDATE sys_task SET remark='wrong-task' WHERE task_id=2"
            return plan
        with patch.object(m, 'make_plan', faulty), self.assertRaisesRegex(m.GuardError, '回查不一致'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_zero_affected_rows_rolls_back(self):
        before = self.snapshot()
        original = m.make_plan
        def faulty(*args):
            plan = original(*args)
            plan['sql'] = "UPDATE sys_task SET remark='x' WHERE task_id=999"
            return plan
        with patch.object(m, 'make_plan', faulty), self.assertRaisesRegex(m.GuardError, '影响行数不是1'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_exception_after_write_before_commit_rolls_back(self):
        before = self.snapshot()
        original = self.db.query
        def failing(sql):
            result = original(sql)
            if sql.startswith('UPDATE sys_task'): raise m.GuardError('模拟写后异常')
            return result
        with patch.object(self.db, 'query', failing), self.assertRaisesRegex(m.GuardError, '模拟写后异常'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_wrong_readback_rolls_back(self):
        before = self.snapshot()
        original = self.db.query
        wrote = False
        def wrong(sql):
            nonlocal wrote
            result = original(sql)
            if sql.startswith('UPDATE sys_task'): wrote = True
            elif sql.startswith(m.SELECT_ROW) and wrote:
                result = copy.deepcopy(result)
                result[0]['status'] = m.TaskStatus.REVIEW
            return result
        with patch.object(self.db, 'query', wrong), self.assertRaisesRegex(m.GuardError, '回查不一致'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())

    def test_nontransactional_engine_refused(self):
        before = self.snapshot()
        original = self.db.query
        def myisam(sql):
            if 'information_schema.TABLES' in sql: return [{'engine': 'MyISAM'}]
            return original(sql)
        with patch.object(self.db, 'query', myisam), self.assertRaisesRegex(m.GuardError, 'InnoDB'):
            m.update_task(self.db, req(), True)
        self.assertEqual(before, self.snapshot())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', action='store_true', help='运行会话临时表真实事务回归，不写持久表')
    args = parser.parse_args()
    suite = unittest.TestLoader().loadTestsFromTestCase(InputTests)
    if args.mysql:
        suite.addTests(unittest.TestLoader().loadTestsFromTestCase(MysqlTests))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
