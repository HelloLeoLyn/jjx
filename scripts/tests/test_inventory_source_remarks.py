import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('remarks', Path(__file__).resolve().parents[1] / 'inventory-source-remarks.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

class SourceRemarksTest(unittest.TestCase):
    def test_merge_shared_material_preserves_customer_text_and_distinct_source_notes(self):
        batch = {'batchCode': 'MIG-A', 'sourceSha256': 'a'*64, 'lines': [
            {'sourceRow': 2, 'originalRemark': '专用\n第二行'}, {'sourceRow': 3, 'originalRemark': '样品'}]}
        rows = [{'sourceRow': i, 'materialId': 1, 'remark': '客户后来修改的备注'} for i in [2, 3]]
        result = m.plan(batch, rows)
        self.assertEqual(2, len(result['sources'])); self.assertEqual(1, len(result['materials']))
        after = result['materials'][0]['after']
        self.assertTrue(after.startswith('客户后来修改的备注'))
        self.assertIn('源行2】\n专用\n第二行', after); self.assertIn('源行3】\n样品', after)
        for r in rows: r['remark'] = after
        self.assertEqual(after, m.plan(batch, rows)['materials'][0]['after'])
    def test_conflict_and_overflow_fail_without_truncation(self):
        current = m.append_note(None, 'MIG-A', 2, '原文')
        with self.assertRaises(ValueError): m.append_note(current, 'MIG-A', 2, '修改后')
        with self.assertRaises(ValueError): m.append_note(current, 'MIG-A', 2, '原')
        with self.assertRaises(ValueError): m.append_note('x'*990, 'MIG-A', 3, '超长')
        self.assertEqual('用户备注', m.append_note('用户备注', 'MIG-A', 2, None))
    def test_duplicate_or_wrong_source_rows_rejected(self):
        batch = {'batchCode': 'MIG-A', 'sourceSha256': 'a'*64, 'lines': [{'sourceRow': 2, 'originalRemark': '样品'}]}
        with self.assertRaises(ValueError): m.plan(batch, [{'sourceRow': 3, 'materialId': 1, 'remark': ''}])
        with self.assertRaises(ValueError): m.plan(batch, [])

if __name__ == '__main__': unittest.main()
