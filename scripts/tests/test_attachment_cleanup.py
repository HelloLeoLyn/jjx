"""隔离回归：不连接数据库，不清理真实上传文件。"""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('cleanup', Path(__file__).parents[1] / 'attachment-cleanup.py')
cleanup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cleanup)


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'upload'
        (self.root / 'attachment-temp').mkdir(parents=True)
        self.file = self.root / 'attachment-temp/a.bin'
        self.file.write_bytes(b'test attachment')

    def test_only_attachment_directories(self):
        for name in ('engineering-archive', 'engineering_jjx_icon', 'icon-archive', 'backup', 'unknown'):
            (self.root / name).mkdir()
            (self.root / name / 'keep').write_text('keep')
        files, retained = cleanup.scan([self.root])
        self.assertEqual(list(files), [str(self.file)])
        self.assertEqual(len(retained), 5)

    def test_symlinks_and_hardlinks_refused(self):
        for link_type in ('soft', 'hard'):
            link = self.file.parent / 'link'
            if link_type == 'soft':
                link.symlink_to(self.file)
            else:
                link.hardlink_to(self.file)
            with self.assertRaises(ValueError):
                cleanup.scan([self.root])
            link.unlink()
        alias = self.base / 'alias'
        alias.symlink_to(self.root)
        with self.assertRaises(ValueError):
            cleanup.scan([alias])

    def test_archive_requires_identical_bytes(self):
        archive = self.base / 'manual.tar'
        with tarfile.open(archive, 'w') as tar:
            tar.add(self.file, arcname=str(self.file).lstrip('/'))
        files, _ = cleanup.scan([self.root])
        cleanup.verify_archive(archive, files)
        self.file.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            cleanup.verify_archive(archive, files)
        with tarfile.open(archive, 'w'):
            pass
        with self.assertRaises(ValueError):
            cleanup.verify_archive(archive, files)

    def test_preview_has_no_writes_or_migration(self):
        with patch('sys.argv', ['cleanup', '--upload-root', str(self.root)]), \
             patch.object(cleanup, 'database_snapshot', return_value='{"files":0,"refs":0}'), \
             patch.object(cleanup.subprocess, 'run') as run, contextlib.redirect_stdout(io.StringIO()):
            cleanup.main()
        run.assert_not_called()
        self.assertTrue(self.file.exists())

    def test_execute_refuses_non_terminal(self):
        with patch('sys.argv', ['cleanup', '--execute', '--upload-root', str(self.root)]), \
             patch.object(cleanup, 'database_snapshot', return_value='{}'), \
             patch('sys.stdin.isatty', return_value=False), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(ValueError):
                cleanup.main()
        self.assertTrue(self.file.exists())

    def execute_mock(self, snapshot_after='{"files":0,"refs":0}', extra_refs=False):
        directory = self.base / 'jjx-docs/sql/migrations'
        directory.mkdir(parents=True)
        (directory / '286_example.sql').write_text('-- existing')
        backup = self.base / 'db.sql'
        backup.write_text('manual db backup')
        archive = self.base / 'files.tar'
        with tarfile.open(archive, 'w') as tar:
            tar.add(self.file, arcname=str(self.file).lstrip('/'))
        def query(sql, write=False):
            self.assertFalse(write)
            if 'REFERENCED_TABLE_NAME' in sql:
                return ''
            if 'information_schema.COLUMNS' in sql:
                return 'sales_quotation_flow\tattachment_ref_ids' if extra_refs else ''
            if 'COUNT(*)' in sql:
                return '1'
            return '286'
        args = ['cleanup', '--upload-root', str(self.root), '--execute', '--task', 'dev-20261016-044',
                '--backup', str(backup), '--files-backup', str(archive), '--writes-paused']
        with patch('sys.argv', args), patch.object(cleanup, 'REPO', self.base), \
             patch.object(cleanup, 'mysql', side_effect=query), \
             patch.object(cleanup, 'database_snapshot', side_effect=[
                 '{"files":1,"refs":2}', '{"files":1,"refs":2}', snapshot_after]), \
             patch('sys.stdin.isatty', return_value=True), \
             patch('builtins.input', return_value='jjx_erp_db DELETE ALL ATTACHMENTS'), \
             patch.object(cleanup.subprocess, 'run') as run, \
             patch.dict(cleanup.os.environ, {'DB_NAME': 'jjx_erp_db'}), \
             contextlib.redirect_stdout(io.StringIO()):
            cleanup.main()
            self.assertEqual(run.call_count, 1)
            self.assertIn('db-migrate.sh', run.call_args.args[0][1])
        return directory

    def test_success_uses_migration_and_deletes_files(self):
        directory = self.execute_mock()
        self.assertFalse(self.file.exists())
        sql = (directory / '287_attachment_test_cleanup.sql').read_text()
        self.assertIn('TRUNCATE TABLE sys_attachment_ref;', sql)
        self.assertNotIn('DROP TRIGGER', sql)

    def test_failed_database_verification_keeps_files(self):
        with self.assertRaises(ValueError):
            self.execute_mock(snapshot_after='{"files":1,"refs":0}')
        self.assertTrue(self.file.exists())

    def test_business_evidence_blocks_cleanup(self):
        with self.assertRaises(ValueError):
            self.execute_mock(extra_refs=True)
        self.assertTrue(self.file.exists())
        self.assertFalse((self.base / 'jjx-docs/sql/migrations/287_attachment_test_cleanup.sql').exists())


if __name__ == '__main__':
    unittest.main()
