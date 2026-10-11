#!/usr/bin/env python3
"""迁移工具隔离回归。🟡只写临时目录；不连接数据库、不执行正式SQL、不启动服务。
前置：python3/bash/git/flock；用法 python3 scripts/test-db-migrate.py [--help]。
手册：jjx-docs/guides/scripts-commands-20260914.md；dev-20261011-014。
"""
import argparse
import fcntl
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FAKE_MYSQL = r'''#!/usr/bin/env python3
import json,os,pathlib,re,sys
p=pathlib.Path(os.environ['MOCK_STATE']); s=json.loads(p.read_text())
def save(): p.write_text(json.dumps(s))
def fail(m): print(m,file=sys.stderr); sys.exit(1)
if s.get('connection_fail'): fail('mock: connection unavailable')
a=sys.argv[1:]
if '-e' in a:
    q=a[a.index('-e')+1]
    if 'SELECT config_value FROM sys_config' in q:
        if s.get('read_fail'): fail('mock: ledger read unavailable')
        key=re.search(r"config_key='([^']+)'",q).group(1)
        if key=='ops.schema.applied': print(s.get('raw_applied',','.join(map(str,s['applied']))))
        elif key=='ops.schema.version': print(s.get('version',''))
        else: print(s.get('files',{}).get(key,''))
    elif 'SELECT 1 FROM sys_task' in q:
        if s.get('task_valid',True): print(1)
    elif 'INSERT INTO sys_config' in q:
        n=int(re.search(r"VALUES \('ops.schema.applied','(\d+)'",q).group(1))
        if n in s.get('fail_record',[]): fail('mock: record failed')
        s['applied']=sorted(set(s['applied']+[n])); s['version']=max(int(s.get('version') or 0),n)
        m=re.search(r"VALUES \('(ops.schema.file.\d+)','([^']+)'",q)
        if m: s.setdefault('files',{}).setdefault(m.group(1),m.group(2))
        if n in s.get('fail_read_after_record',[]): s['read_fail']=True
        if n in s.get('block_archive',[]):
            pathlib.Path(os.environ['MOCK_MIG_DIR'],'applied').write_text('blocked')
        save()
    else: fail('mock: unhandled query '+q)
else:
    sql=sys.stdin.read(); n=int(re.search(r'-- fixture: (\d+)',sql).group(1))
    s.setdefault('executions',[]).append(n); save()
    if n in s.get('fail_sql',[]): fail('mock: SQL failed after possible partial change')
'''


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='jjx-migrate-test-')
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        self.scripts = self.root / 'scripts'; self.scripts.mkdir()
        shutil.copy2(ROOT / 'scripts/db-migrate.sh', self.scripts / 'db-migrate.sh')
        (self.scripts / 'check-model-baseline.sh').write_text('#!/bin/bash\nexit 0\n')
        self.mig = self.root / 'jjx-docs/sql/migrations'; self.mig.mkdir(parents=True)
        self.state = self.root / 'database.json'
        self.state.write_text(json.dumps(dict(applied=[], version='', executions=[], files={})))
        bin_dir = self.root / 'bin'; bin_dir.mkdir()
        mysql = bin_dir / 'mysql'; mysql.write_text(FAKE_MYSQL); mysql.chmod(0o755)
        self.env = dict(os.environ, PATH=str(bin_dir)+os.pathsep+os.environ['PATH'],
                        DB_HOST=self.root.name, DB_PORT='3306', DB_NAME='test_db',
                        MOCK_STATE=str(self.state), MOCK_MIG_DIR=str(self.mig))
        self.target = hashlib.sha256((self.root.name+'\0'+'3306'+'\0'+'test_db').encode()).hexdigest()
        self.lock = pathlib.Path(tempfile.gettempdir(), 'jjx-db-migrate-'+self.target+'.lock')
        self.addCleanup(lambda: self.lock.unlink(missing_ok=True))
        self.backup = self.root / 'backup.sql'
        self.backup.write_text('CREATE TABLE `example` (id int);\n'+'-- padding\n'*160)

    def database(self, **changes):
        s=json.loads(self.state.read_text()); s.update(changes); self.state.write_text(json.dumps(s)); return s

    def migration(self, n, suffix='example', archived=False):
        folder=self.mig / 'applied' if archived else self.mig; folder.mkdir(exist_ok=True)
        file=folder / f'{n}_{suffix}.sql'; file.write_text(f'-- fixture: {n}\nSELECT {n};\n'); return file

    def run_script(self, *args, ok=True):
        r=subprocess.run(['bash',str(self.scripts/'db-migrate.sh'),*args],env=self.env,
                         cwd=self.root,text=True,capture_output=True,timeout=15)
        self.assertEqual(r.returncode==0,ok,r.stdout+'\n'+r.stderr)
        return r

    def execute(self, *args, ok=True):
        return self.run_script(*args,'--yes','--task','dev-20261011-014','--backup',str(self.backup),ok=ok)

    def test_preview_writes_nothing(self):
        f=self.migration(2); before=self.state.read_bytes()
        self.run_script('--all'); self.assertEqual(before,self.state.read_bytes())
        self.assertTrue(f.exists()); self.assertFalse((self.root/'.tmp').exists())

    def test_numeric_order_and_lower_gap_and_archive(self):
        self.database(applied=[10],version=10)
        for n in (11,2,10): self.migration(n)
        self.execute('--all')
        self.assertEqual(self.database()['executions'],[2,11])
        self.assertEqual(self.database()['applied'],[2,10,11])
        self.assertEqual(len(list((self.mig/'applied').glob('*.sql'))),3)
        self.execute('--all'); self.assertEqual(self.database()['executions'],[2,11])

    def test_sql_failure_stops_and_blocks_automatic_retry(self):
        for n in (1,2,3): self.migration(n)
        self.database(fail_sql=[2]); self.execute('--all',ok=False)
        self.assertEqual(self.database()['executions'],[1,2]); self.assertEqual(self.database()['applied'],[1])
        self.assertTrue((self.mig/'2_example.sql').exists()); self.assertTrue((self.mig/'3_example.sql').exists())
        r=self.execute('--all',ok=False); self.assertIn('未解决记录',r.stderr)
        self.assertEqual(self.database()['executions'],[1,2])
        self.database(fail_sql=[]); self.execute('--retry','2'); self.execute('--all')
        self.assertEqual(self.database()['executions'],[1,2,2,3])

    def test_record_failure_requires_manual_record_without_reexecute(self):
        self.migration(1); self.migration(2); self.database(fail_record=[1])
        self.execute('--all',ok=False); self.assertEqual(self.database()['executions'],[1])
        self.execute('--all',ok=False); self.execute('--retry','1',ok=False)
        self.database(fail_record=[]); self.execute('--record','1')
        self.assertEqual(self.database()['executions'],[1]); self.execute('--all')
        self.assertEqual(self.database()['executions'],[1,2])

    def test_record_readback_failure_reconciles_before_other_sql(self):
        self.migration(2); self.migration(1); self.database(fail_read_after_record=[1])
        self.execute('--all',ok=False); self.database(read_fail=False,fail_read_after_record=[])
        self.execute('--all'); self.assertEqual(self.database()['executions'],[1,2])
        self.assertFalse((self.root/'.tmp/db-migrate'/self.target/'inflight.json').exists())

    def test_archive_failure_resume_only_archives(self):
        self.migration(1); self.database(block_archive=[1]); self.execute('--all',ok=False)
        self.assertEqual(self.database()['applied'],[1]); (self.mig/'applied').unlink()
        self.database(block_archive=[]); self.execute('--all')
        self.assertEqual(self.database()['executions'],[1]); self.assertTrue((self.mig/'applied/1_example.sql').exists())

    def test_other_database_scans_archived_files(self):
        self.migration(1,archived=True); self.execute('--all')
        self.assertEqual(self.database()['executions'],[1]); self.assertTrue((self.mig/'applied/1_example.sql').exists())

    def test_duplicate_number_is_rejected_before_sql(self):
        self.migration(1); self.migration(1,'other'); self.execute('--all',ok=False)
        self.assertEqual(self.database()['executions'],[])

    def test_bad_backup_and_missing_task_are_rejected(self):
        self.migration(1); self.backup.write_text('tiny'); self.execute('--all',ok=False)
        self.assertEqual(self.database()['executions'],[])
        self.backup.write_text('CREATE TABLE `hr_employee` (id int);\n'+'-- padding\n'*160)
        self.execute('--all',ok=False); self.assertEqual(self.database()['executions'],[])
        self.database(task_valid=False); self.execute('--all',ok=False)

    def test_missing_ledger_is_not_inferred_from_max(self):
        self.migration(1); self.database(version=10)
        self.execute('--all',ok=False); self.assertEqual(self.database()['executions'],[])
        self.execute('--record','1'); self.assertEqual(self.database()['applied'],[1])

    def test_connection_and_malformed_ledger_fail_closed(self):
        self.migration(1); self.database(connection_fail=True)
        self.run_script('--status',ok=False); self.database(connection_fail=False,raw_applied='1,invalid')
        self.execute('--all',ok=False); self.assertEqual(self.database()['executions'],[])

    def test_applied_checksum_change_rejected(self):
        self.migration(1); self.execute('--all')
        f=self.mig/'applied/1_example.sql'; f.write_text(f.read_text()+'SELECT 999;\n')
        self.execute('1_example.sql',ok=False); self.assertEqual(self.database()['executions'],[1])

    def test_same_database_lock_rejects_second_writer(self):
        self.migration(1)
        with self.lock.open('w') as f:
            fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
            self.execute('--all',ok=False)
        self.assertEqual(self.database()['executions'],[])

    def test_interrupted_record_with_changed_file_is_not_reexecuted(self):
        f=self.migration(1); self.database(fail_record=[1]); self.execute('--all',ok=False)
        f.write_text(f.read_text()+'SELECT 9;\n'); self.database(fail_record=[])
        self.execute('--record','1',ok=False); self.assertEqual(self.database()['executions'],[1])


class HookTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='jjx-hook-test-'); self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)
        self.git('init','-q'); self.git('config','user.email','test@example.invalid'); self.git('config','user.name','test')
        self.source=self.root/'jjx-docs/sql/migrations/1_example.sql'; self.source.parent.mkdir(parents=True)
        self.source.write_text('SELECT 1;\n')
        self.standard=self.root/'jjx-docs/standards/example.md'; self.standard.parent.mkdir(parents=True); self.standard.write_text('rule\n')
        self.git('add','.'); self.git('commit','-qm','baseline')

    def git(self,*args): return subprocess.run(['git',*args],cwd=self.root,check=True,capture_output=True)
    def hook(self,ok):
        self.git('add','-A')
        r=subprocess.run(['bash',str(ROOT/'scripts/hooks/pre-commit')],cwd=self.root,capture_output=True,text=True)
        self.assertEqual(r.returncode==0,ok,r.stdout+r.stderr)
    def move(self,name='1_example.sql'):
        p=self.source.parent/'applied'/name; p.parent.mkdir(); self.source.rename(p); return p
    def test_identical_archive_allowed(self): self.move(); self.hook(True)
    def test_archive_with_content_change_blocked(self): self.move().write_text('SELECT 2;\n'); self.hook(False)
    def test_different_archive_name_blocked(self): self.move('1_renamed.sql'); self.hook(False)
    def test_plain_sql_delete_blocked(self): self.source.unlink(); self.hook(False)
    def test_standard_delete_blocked(self): self.standard.unlink(); self.hook(False)
    def test_move_outside_applied_blocked(self): self.source.rename(self.root/'other.sql'); self.hook(False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    unittest.main(argv=[__file__],verbosity=2)
