#!/usr/bin/env python3
"""清理脚本边界测试：mysql 始终替换为本地模拟客户端，绝不连接真实数据库。"""
import json
import os
from pathlib import Path
import pty
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/db-clean-test-data.sh'
PRODUCTS = ('product_config_option', 'product_config_model', 'product', 'product_category')
MOCK = r'''#!/usr/bin/env python3
import json, os, pathlib, re, sys
f = json.loads(os.environ['CLEAN_TEST_FIXTURE'])
capture = pathlib.Path(os.environ['CLEAN_TEST_CAPTURE'])
args = sys.argv[1:]
if '-e' not in args:
    capture.write_text(sys.stdin.read())
    sys.exit(0)
q = args[args.index('-e') + 1]
if q == 'SELECT 1':
    print(1)
elif 'information_schema.columns' in q:
    print('inventory_material\tproduct_id')
    print('archive_production_quality_inspection\tproduct_code')
    print('sales_order_product\tproduct_id')
elif 'information_schema.tables' in q:
    print('\n'.join(f['tables']))
elif q.startswith('SELECT COUNT(*)'):
    table = re.search(r'FROM\s+`?(\w+)', q).group(1)
    if table == f.get('query_failure'):
        print('mock database read error', file=sys.stderr)
        sys.exit(1)
    if table == 'inventory_material' and 'product_id' in q:
        print(f.get('retained_ref', 0))
    elif table == 'archive_production_quality_inspection' and 'product_code' in q:
        print(f.get('code_ref', 0))
    elif table == 'sys_tag_rel':
        print(f.get('tag_ref', 0))
    elif capture.exists():
        print(f.get('remaining', 0) if table == 'inventory_item' else 0)
    elif table == 'inventory_item':
        print(0 if 'NOT EXISTS' in q else (2 if 'PRODUCT' in q else 7))
    else:
        print({'product': 3, 'product_category': 1, 'product_config_model': 2,
               'product_config_option': 4}.get(table, 0))
else:
    print('unexpected mock query: ' + q, file=sys.stderr)
    sys.exit(1)
'''


class CleanProductsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='jjx-clean-products-')
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.capture = self.dir / 'executed.sql'
        client = self.dir / 'mysql'
        client.write_text(MOCK)
        client.chmod(0o755)
        sql = (ROOT / 'jjx-docs/sql/00_clean_test_data.sql').read_text()
        shell = SCRIPT.read_text()
        retained = re.search(r'RETAINED_TABLES=\((.*?)\n\)', shell, re.S).group(1)
        retained = re.sub(r'#.*', '', retained).split()
        self.tables = sorted(set(re.findall(r'^TRUNCATE (\w+);', sql, re.M))
                             | set(retained) | {'sys_task'})
        self.backup = self.dir / 'backup.sql'
        self.backup.write_text('CREATE TABLE `inventory_item` (id bigint);\n' + '-- fixture\n' * 150)

    def run_script(self, args=(), terminal=False, **fixture):
        env = os.environ.copy()
        env.update(PATH=str(self.dir) + ':' + env['PATH'],
                   JJX_BACKUP_DIR=str(self.dir),
                   CLEAN_TEST_CAPTURE=str(self.capture),
                   CLEAN_TEST_FIXTURE=json.dumps(dict(tables=self.tables, **fixture)))
        cmd = ['bash', str(SCRIPT), *args]
        if not terminal:
            return subprocess.run(cmd, cwd=ROOT, env=env, input='', text=True,
                                  capture_output=True, timeout=30)
        master, slave = pty.openpty()
        try:
            proc = subprocess.Popen(cmd, cwd=ROOT, env=env, stdin=slave,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            os.close(slave)
            slave = None
            os.write(master, b'jjx_erp_db\n')
            out, err = proc.communicate(timeout=30)
            return subprocess.CompletedProcess(cmd, proc.returncode, out, err)
        finally:
            if slave is not None:
                os.close(slave)
            os.close(master)

    def test_default_preserves_products(self):
        r = self.run_script()
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn('保留产品、分类、配置模型和选项', r.stdout)
        self.assertNotIn('product: 3 行', r.stdout)
        self.assertFalse(self.capture.exists())

    def test_include_preview_lists_all_product_tables_and_live_identities(self):
        r = self.run_script(['--include-products'])
        self.assertEqual(0, r.returncode, r.stderr)
        for table in PRODUCTS:
            self.assertIn(table + ': ', r.stdout)
        self.assertIn('inventory_item: 将删 2 条（保留 5 条）', r.stdout)
        self.assertFalse(self.capture.exists())

    def test_domains_cannot_mix_in_either_order(self):
        for args in (['--domains', 'inventory', '--include-products'],
                     ['--include-products', '--domains', 'quality']):
            r = self.run_script(args)
            self.assertNotEqual(0, r.returncode)
            self.assertIn('不能与 --domains 混用', r.stderr)
        self.assertFalse(self.capture.exists())

    def test_retained_id_code_and_tag_refs_block(self):
        for fixture, expected in (({'retained_ref': 1}, 'inventory_material.product_id'),
                                  ({'code_ref': 1}, 'archive_production_quality_inspection.product_code'),
                                  ({'tag_ref': 1}, 'sys_tag_rel')):
            r = self.run_script(['--include-products'], **fixture)
            self.assertNotEqual(0, r.returncode)
            self.assertIn(expected, r.stdout)
            self.assertIn('未执行清理', r.stderr)
            self.assertFalse(self.capture.exists())

    def test_read_failure_is_visible_and_blocks(self):
        r = self.run_script(['--include-products'], query_failure='product')
        self.assertNotEqual(0, r.returncode)
        self.assertIn('mock database read error', r.stderr)
        self.assertFalse(self.capture.exists())

    def test_nonterminal_execute_is_rejected(self):
        r = self.run_script(['--include-products', '--execute'])
        self.assertNotEqual(0, r.returncode)
        self.assertIn('agent/管道调用一律拒绝', r.stderr)
        self.assertFalse(self.capture.exists())

    def test_simulated_execute_orders_dependencies_and_records_option(self):
        r = self.run_script(['--include-products', '--execute', '--backup', str(self.backup)], terminal=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        sql = self.capture.read_text()
        positions = [re.search(r'(?m)^TRUNCATE ' + table + ';', sql).start() for table in PRODUCTS]
        self.assertEqual(sorted(positions), positions)
        # Every business TRUNCATE must precede product deletion.
        before_products = sql[:positions[0]]
        for table in ('sales_sample_order', 'sales_order_product', 'engineering_bom',
                      'engineering_routing', 'inventory_stock', 'inventory_transaction'):
            self.assertIn('TRUNCATE ' + table + ';', before_products)
        self.assertGreater(sql.index('DELETE FROM inventory_item'), positions[-1])
        for table in ('inventory_material', 'engineering_die', 'engineering_screen_frame', 'sys_tag'):
            self.assertNotRegex(sql, r'(?m)^TRUNCATE ' + table + ';')
        self.assertIn('产品清理核验通过', r.stdout)
        self.assertIn('include_products=1 domains=all', (self.dir / 'clean-test-data-log.txt').read_text())

    def test_simulated_residual_identity_is_reported_after_execution(self):
        r = self.run_script(['--include-products', '--execute', '--backup', str(self.backup)],
                            terminal=True, remaining=1)
        self.assertNotEqual(0, r.returncode)
        self.assertTrue(self.capture.exists())
        self.assertIn('清理已执行，但产品残留核验失败', r.stderr)
        self.assertTrue((self.dir / 'clean-test-data-log.txt').exists())

    def test_simulated_default_execute_keeps_product_archives(self):
        r = self.run_script(['--execute', '--backup', str(self.backup)], terminal=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        sql = self.capture.read_text()
        for table in PRODUCTS:
            self.assertNotRegex(sql, r'(?m)^TRUNCATE ' + table + ';')
        self.assertIn('include_products=0 domains=all', (self.dir / 'clean-test-data-log.txt').read_text())

    def test_simulated_missing_backup_never_submits_sql(self):
        r = self.run_script(['--include-products', '--execute', '--backup', str(self.dir / 'missing.sql')],
                            terminal=True)
        self.assertNotEqual(0, r.returncode)
        self.assertIn('指定的备份文件不存在', r.stderr)
        self.assertFalse(self.capture.exists())


if __name__ == '__main__':
    unittest.main()
