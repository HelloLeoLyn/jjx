#!/usr/bin/env python3
"""清理脚本边界测试：mysql 始终替换为本地模拟客户端，绝不连接真实数据库。
危险等级：🟡 仅写临时夹具；不连接真实库、不执行真实清理。
前置：支持forkpty的Python 3、bash；--help由unittest提供。
"""
import json
import os
from pathlib import Path
import pty
import re
import sqlite3
import select
import time
import errno
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/db-clean-test-data.sh'
PRODUCTS = ('product_config_option', 'product_config_model', 'product', 'product_category')
RETAINED_INFRA = ('inventory_storage_location', 'production_equipment', 'portal_language_config')
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
elif 'information_schema.TRIGGERS' in q:
    print(1)  # 守卫触发器存在（清库 SQL 删附件后已按原样重建）
elif q.startswith('SELECT COUNT(*)'):
    table = re.search(r'FROM\s+`?(\w+)', q).group(1)
    if table == f.get('query_failure'):
        print('mock database read error', file=sys.stderr)
        sys.exit(1)
    if table in ('inventory_storage_location', 'production_equipment', 'portal_language_config'):
        expected = {'inventory_storage_location': 5, 'production_equipment': 3, 'portal_language_config': 2}[table]
        print(f.get('retained_after', {}).get(table, expected) if capture.exists() else expected)
    elif table == 'inventory_material' and 'product_id' in q:
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
        # Windows/MSYS环境也固定使用模拟客户端，不让宿主MySQL路径探测覆盖测试PATH。
        uname=self.dir / 'uname'
        uname.write_text('#!/bin/sh\necho Linux\n');uname.chmod(0o755)
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
                   DB_HOST='fixture-only.invalid',DB_PORT='1',DB_PASS='fixture-only',
                   MYSQL_BIN_DIR=str(self.dir),
                   JJX_BACKUP_DIR=str(self.dir),
                   CLEAN_TEST_CAPTURE=str(self.capture),
                   CLEAN_TEST_FIXTURE=json.dumps(dict(tables=self.tables, **fixture)))
        cmd = ['bash', str(SCRIPT), *args]
        if not terminal:
            return subprocess.run(cmd, cwd=ROOT, env=env, input='', text=True,
                                  capture_output=True, timeout=30)
        # forkpty给bash完整的控制终端；MSYS的Popen(stdin=slave)会让read提前遇到EOF。
        pid, master=pty.fork()
        if pid==0:
            os.chdir(ROOT)
            os.execvpe(cmd[0],cmd,env)
        output=b''; answered=False; deadline=time.monotonic()+30
        try:
            while True:
                remaining=deadline-time.monotonic()
                if remaining<=0: raise subprocess.TimeoutExpired(cmd,30)
                if not select.select([master],[],[],remaining)[0]: raise subprocess.TimeoutExpired(cmd,30)
                try: chunk=os.read(master,65536)
                except OSError as exc:
                    if exc.errno==errno.EIO: break
                    raise
                if not chunk: break
                output+=chunk
                if not answered and b'[jjx_erp_db]' in output:
                    os.write(master,b'jjx_erp_db\n');answered=True
            _,status=os.waitpid(pid,0)
            text=output.decode().replace('\r\n','\n')
            return subprocess.CompletedProcess(cmd,os.waitstatus_to_exitcode(status),text,text)
        finally:
            os.close(master)

    def test_printing_resources_and_runtime_are_in_full_cleanup_in_dependency_order(self):
        r = self.run_script(('--execute', '--backup', str(self.backup)), terminal=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        plan = self.capture.read_text()
        tables=('print_use_segment','engineering_screen_plate','print_film_copy','print_film_request',
                'print_artwork_version','print_artwork','print_frame_stretch','engineering_screen_frame')
        positions=[plan.index('TRUNCATE '+table+';') for table in tables]
        self.assertEqual(sorted(positions),positions)
        self.assertIn('TRUNCATE engineering_resource_maintenance;',plan)
        self.assertNotIn('TRUNCATE engineering_die;',plan)
        self.assertLess(plan.index("DELETE FROM sys_attachment_ref WHERE source_type = 'print_artwork_version';"),
                        plan.index('TRUNCATE print_artwork_version;'))
        self.assertIn('TRUNCATE print_kit_shortage;',plan)

    def test_domains_do_not_clear_printing_or_its_attachment_references(self):
        for domain in ('purchase','inventory','quality','purchase,inventory,quality'):
            if self.capture.exists(): self.capture.unlink()
            r=self.run_script(('--execute','--backup',str(self.backup),'--domains',domain),terminal=True)
            self.assertEqual(0,r.returncode,r.stdout+r.stderr)
            plan=self.capture.read_text()
            self.assertNotRegex(plan,r'(?m)^TRUNCATE (?:print_|engineering_screen_|engineering_resource_maintenance)')
            self.assertNotIn('DELETE FROM sys_attachment_ref',plan)

    def test_printing_cleanup_removes_owned_files_and_refs_but_keeps_shared_assets(self):
        # 只在内存库执行生成计划中的DELETE；不连接真实数据库。
        r = self.run_script(('--execute', '--backup', str(self.backup)), terminal=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        with sqlite3.connect(':memory:') as db:
            db.executescript("""
                CREATE TABLE sys_attachment(id INTEGER,biz_type TEXT);
                INSERT INTO sys_attachment VALUES(1,'product'),(2,'inquiry'),(3,'product'),
                    (4,'bom'),(5,'engineering_jjx_icon'),(6,'print_artwork_version'),
                    (7,'print_artwork_version'),(8,'print_artwork_version'),(9,'print_artwork_version'),(10,'print_artwork_version');
                CREATE TABLE quality_template_registry(file_id INTEGER);
                INSERT INTO quality_template_registry VALUES(10);
                CREATE TABLE sys_attachment_ref(attachment_id INTEGER,source_type TEXT,deleted INTEGER,ref_state TEXT);
                INSERT INTO sys_attachment_ref VALUES(1,'print_artwork_version',0,'active'),(1,'inventory_material',0,'active'),
                    (2,'print_artwork_version',0,'active'),(3,'product',0,'active'),(4,'bom',0,'active'),
                    (5,'engineering_jjx_icon',0,'active'),(6,'print_artwork_version',0,'active'),
                    (7,'print_artwork_version',0,'active'),(7,'inventory_material',0,'active'),
                    (8,'print_artwork_version',0,'active'),(8,'unknown_retained',0,'active'),
                    (9,'print_artwork_version',0,'active'),(9,'unknown_retained',1,'inactive'),(10,'print_artwork_version',0,'active');
                CREATE TABLE engineering_resource_maintenance(resource_type TEXT);
                INSERT INTO engineering_resource_maintenance VALUES('ARTWORK'),('ARTWORK_VERSION'),
                    ('FILM_COPY'),('FILM_REQUEST'),('SCREEN_FRAME'),('SCREEN_PLATE'),('BOM');
            """)
            for line in self.capture.read_text().splitlines():
                if re.match(r'^DELETE FROM (sys_attachment|sys_attachment_ref) WHERE ',line): db.execute(line)
                if line=='TRUNCATE engineering_resource_maintenance;': db.execute('DELETE FROM engineering_resource_maintenance')
            self.assertEqual([(1,),(5,),(7,),(8,),(10,)],db.execute('SELECT id FROM sys_attachment ORDER BY id').fetchall())
            self.assertEqual([(1,),(5,),(7,),(8,)],db.execute('SELECT attachment_id FROM sys_attachment_ref ORDER BY attachment_id').fetchall())
            self.assertEqual(0,db.execute("SELECT COUNT(*) FROM sys_attachment_ref WHERE source_type='print_artwork_version'").fetchone()[0])
            self.assertEqual(0,db.execute('SELECT COUNT(*) FROM engineering_resource_maintenance').fetchone()[0])

    def test_default_includes_products(self):
        # 2026-10-13 口径：全量清理默认包含产品（不再需要 --include-products）。
        r = self.run_script()
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn('同时清空全部产品资料', r.stdout)
        for table in PRODUCTS:
            self.assertIn(table + ': ', r.stdout)
        self.assertFalse(self.capture.exists())

    def test_include_preview_lists_all_product_tables_and_live_identities(self):
        r = self.run_script(['--include-products'])
        self.assertEqual(0, r.returncode, r.stderr)
        for table in PRODUCTS:
            self.assertIn(table + ': ', r.stdout)
        self.assertIn('inventory_item: 将删 2 条（保留 5 条）', r.stdout)
        self.assertFalse(self.capture.exists())

    def test_domains_exclude_products_and_accept_legacy_flag(self):
        # 域模式不含产品；--include-products 保留兼容、在域模式下等价忽略（不再报错）。
        for args in (['--domains', 'inventory', '--include-products'],
                     ['--include-products', '--domains', 'quality']):
            r = self.run_script(args)
            self.assertEqual(0, r.returncode, r.stderr)
            self.assertIn('保留产品、分类、配置模型和选项', r.stdout)
            self.assertNotIn('product: 3 行', r.stdout)
        self.assertFalse(self.capture.exists())

    def test_infrastructure_is_retained_and_covered_in_all_previews(self):
        for args in ([], ['--domains', 'inventory'], ['--domains', 'purchase'],
                     ['--domains', 'quality'], ['--domains', 'purchase,inventory,quality']):
            with self.subTest(args=args):
                r = self.run_script(args)
                self.assertEqual(0, r.returncode, r.stdout + r.stderr)
                for table, count in zip(RETAINED_INFRA, (5, 3, 2)):
                    self.assertIn(f'{table}: {count} 行（保留）', r.stdout)
                self.assertIn('张表均有归宿', r.stdout)
                self.assertFalse(self.capture.exists())

    def test_simulated_execution_preserves_infrastructure_in_full_and_domain_modes(self):
        for domains in (None, 'inventory', 'purchase', 'quality', 'purchase,inventory,quality'):
            with self.subTest(domains=domains):
                if self.capture.exists():
                    self.capture.unlink()
                args = ['--execute', '--backup', str(self.backup)]
                if domains:
                    args += ['--domains', domains]
                r = self.run_script(args, terminal=True)
                self.assertEqual(0, r.returncode, r.stdout + r.stderr)
                sql = self.capture.read_text()
                for table, count in zip(RETAINED_INFRA, (5, 3, 2)):
                    self.assertNotRegex(sql, rf'(?mi)^\s*(?:TRUNCATE(?: TABLE)?|DELETE FROM)\s+`?{table}`?\b')
                    self.assertIn(f'保留核验 {table}: {count} → {count} 行', r.stdout)
                self.assertIn('保留基础资料核验通过', r.stdout)
                if not domains or 'inventory' in domains:
                    self.assertIn('TRUNCATE inventory_stock;', sql)

    def test_retained_infrastructure_change_fails_postcheck(self):
        for table in RETAINED_INFRA:
            with self.subTest(table=table):
                if self.capture.exists():
                    self.capture.unlink()
                r = self.run_script(['--execute', '--backup', str(self.backup)], terminal=True,
                                    retained_after={table: 0})
                self.assertNotEqual(0, r.returncode)
                self.assertTrue(self.capture.exists())
                self.assertIn('保留基础资料行数发生变化', r.stderr)
                self.assertNotIn('保留基础资料核验通过', r.stdout)

    def test_retained_infrastructure_read_failure_blocks_execution(self):
        r = self.run_script(['--execute', '--backup', str(self.backup)], terminal=True,
                            query_failure='production_equipment')
        self.assertNotEqual(0, r.returncode)
        self.assertIn('mock database read error', r.stderr)
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
                      'engineering_routing', 'inventory_stock', 'inventory_transaction',
                      'engineering_screen_frame', 'engineering_screen_plate', 'print_artwork_version'):
            self.assertIn('TRUNCATE ' + table + ';', before_products)
        self.assertGreater(sql.index('DELETE FROM inventory_item'), positions[-1])
        for table in ('inventory_material', 'engineering_die', 'sys_tag'):
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

    def test_simulated_default_execute_cleans_products(self):
        # 默认 --execute 现在也清产品（含产品档案附件），核验四张产品表为 0。
        r = self.run_script(['--execute', '--backup', str(self.backup)], terminal=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        sql = self.capture.read_text()
        self.assertTrue(sql.startswith('DELIMITER //\n'))
        for table in PRODUCTS:
            self.assertRegex(sql, r'(?m)^TRUNCATE ' + table + ';')
        self.assertIn("DELETE FROM sys_attachment WHERE biz_type = 'product'", sql)
        # 清库窗口：摘掉迁移 271 的物理删除守卫触发器后必须按原样恢复（两次删附件 → 两对）。
        drops = sql.count('DROP TRIGGER IF EXISTS trg_attachment_v1_no_delete;')
        creates = sql.count('CREATE TRIGGER trg_attachment_v1_no_delete')
        self.assertEqual(2, drops)
        self.assertEqual(drops, creates)
        # 先清引用行、再删附件，避免悬空引用。
        self.assertLess(sql.index('DELETE FROM sys_attachment_ref'),
                        sql.index("DELETE FROM sys_attachment WHERE biz_type = 'product'"))
        self.assertIn('产品清理核验通过', r.stdout)
        self.assertIn('include_products=1 domains=all', (self.dir / 'clean-test-data-log.txt').read_text())

    def test_simulated_missing_backup_never_submits_sql(self):
        r = self.run_script(['--include-products', '--execute', '--backup', str(self.dir / 'missing.sql')],
                            terminal=True)
        self.assertNotEqual(0, r.returncode)
        self.assertIn('指定的备份文件不存在', r.stderr)
        self.assertFalse(self.capture.exists())


if __name__ == '__main__':
    unittest.main()
