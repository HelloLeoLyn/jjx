import importlib.util
from decimal import Decimal
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('migration', Path(__file__).resolve().parents[1] / 'inventory-migration.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

class MigrationRulesTest(unittest.TestCase):
    def test_name_only_removes_resolved_supplier(self):
        supplier = dict(name='深圳市尚昇光学材料有限公司', short='尚昇', abbr='SS')
        self.assertEqual('0.125中砂PC', m.strip_supplier('0.125中砂PC(尚昇)', supplier))
        self.assertEqual('15C（60）PE保护膜', m.strip_supplier('15C（60）PE保护膜', None))
        self.assertEqual('PC（1860）', m.strip_supplier('PC（1860）(尚昇)', supplier))
    def test_models_preserve_original_and_only_derive_missing(self):
        model = lambda **kw: m.derive_model(kw.get('name', ''), kw.get('spec', ''), kw.get('old', ''), kw.get('prefix', 'PC'), kw.get('thickness'), kw.get('grammage'))[0]
        self.assertEqual('PC125-S', model(thickness=Decimal('.125')))
        self.assertEqual('RP120g-S', model(prefix='RP', grammage=Decimal(120)))
        self.assertEqual('DB6842', model(old='DB6842', thickness=Decimal('.125')))
        self.assertEqual('15C', model(name='15C PE保护膜(宏裕)'))
        self.assertIsNone(model(name='15C（60）PE保护膜(宏裕)', prefix='PF'))
        self.assertEqual('PET50#', model(name='白色PET50#', old='T50'))
        self.assertEqual('B-KT-2.54/U', model(spec='B-KT-2.54/U'))
    def test_geometry_is_not_applied_to_component_model(self):
        self.assertEqual((Decimal(470), None), m.geometry(470, False))
        self.assertEqual((Decimal(300), Decimal(305)), m.geometry('300×305', False))
        self.assertEqual((None, None), m.geometry('0603', True))
    def test_fractional_quantities_and_unknowns_preserved(self):
        self.assertEqual('10.5', m.decimal_text(m.number(10.5)))
        self.assertIsNone(m.number('#VALUE!'))
        self.assertIsNone(m.number(float('nan')))
    def test_supplier_ambiguity_is_not_guessed(self):
        row = [None] * 20; row[11] = 'ABC'
        a = dict(id=1); b = dict(id=2)
        self.assertIsNone(m.supplier_for(row, {'abc': [a, b]}))
        self.assertEqual(a, m.supplier_for(row, {'abc': [a]}))

if __name__ == '__main__': unittest.main()
