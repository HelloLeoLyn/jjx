#!/usr/bin/env python3
"""库存迁移数据准备（dev-20261119-001）。
🟢默认仅预览；🟡--write 生成新JSON/XLSX，不覆盖输入；不写数据库、不启停服务。
前置：Python3/openpyxl/mysql；只读账号可读取品类、字典、供应商；客户确认版Excel。
规则真源：jjx-docs/task-docs/dev-20261119-001-plan.md。正式入账由后端迁移服务完成。
"""
import argparse
from collections import defaultdict
from datetime import date
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import unicodedata


def normalized(value):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', str(value or ''))).casefold()


def number(value):
    if value in (None, '') or isinstance(value, bool):
        return None
    try:
        n = Decimal(str(value))
        return n if n.is_finite() else None
    except InvalidOperation:
        return None


def text(value):
    return '' if value is None else str(value)


def decimal_text(value):
    return format(value.normalize(), 'f') if value is not None else None


def read_reference():
    # 仅提取迁移需要的公开业务标识，不读取供应商联系人、银行账户等资料。
    sql = """SELECT JSON_OBJECT(
      'categories', (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',c.category_id,'code',c.category_code,'name',c.category_name,'parent',c.parent_id,'prefix',c.model_prefix,'schema',t.attribute_schema)) FROM inventory_material_category c LEFT JOIN inventory_attribute_template t ON t.category_id=c.category_id),
      'suppliers', (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',supplier_id,'name',supplier_name,'short',short_name,'abbr',abbr)) FROM purchase_supplier),
      'dictionaries', (SELECT JSON_ARRAYAGG(JSON_OBJECT('type',dict_code,'code',item_value,'label',label)) FROM sys_dict_item WHERE deleted=0 AND is_active=1 AND dict_code IN ('unit','material_meta','color','brand','form')));"""
    env = dict(os.environ, MYSQL_PWD=os.environ.get('JJX_RO_PASS', 'jjx_ro_2026'))
    result = subprocess.run(['mysql', '-h127.0.0.1', '-u' + os.environ.get('JJX_RO_USER', 'jjx_ro'), '--batch', '--raw', '--skip-column-names', os.environ.get('JJX_DB_NAME', 'jjx_erp_db'), '-e', sql], env=env, text=True, capture_output=True, check=True)
    return json.loads(result.stdout)


def supplier_for(row, lookup):
    candidates = [text(row[12])] if row[12] else []
    candidates += re.split(r'[；;]', text(row[11]))
    matches = {s['id']: s for token in candidates for s in lookup.get(normalized(token), [])}
    return next(iter(matches.values())) if len(matches) == 1 else None


def strip_supplier(name, supplier):
    if not supplier:
        return name.strip()
    aliases = {normalized(supplier[k]) for k in ('name', 'short', 'abbr') if supplier.get(k)}
    name = re.sub(r'[（(]([^（）()]+)[）)]', lambda m: '' if normalized(m[1]) in aliases else m[0], name)
    for key in ('name', 'short', 'abbr'):
        alias = supplier.get(key)
        if not alias:
            continue
        # 原文供应商有括号、连字符或直接后缀三种写法；仅移除完整匹配的后缀。
        name = re.sub(r'(?:[-—－]\s*|\s*)' + re.escape(alias) + r'\s*$', '', name, flags=re.I)
    return name.strip()


def derive_model(name, specification, extracted, prefix, thickness, grammage):
    issues = []
    if extracted:
        # 旧提取器T5*正则先于PET50#匹配，修复截断，保留完整原文型号/记号。
        pet = re.search(r'PET\s*\d+(?:\.\d+)?\s*#', name, re.I)
        if pet and re.fullmatch(r'T\d+', text(extracted), re.I):
            return pet[0], ['已修复旧提取器PET记号截断，保留完整原文']
        return text(extracted), issues
    # 用户确认15C是材料型号；括号数字含义未定的同系列仍交客户处理。
    if re.match(r'^15C\s+PE保护膜', name, re.I):
        return '15C', issues
    explicit = re.fullmatch(r'B-[A-Za-z0-9.-]+(?:[-/][A-Za-z0-9.-]+)+', text(specification).strip())
    if explicit:
        return explicit[0], ['从原规格补提取明确型号']
    if prefix == 'RP' and grammage is not None:
        return 'RP' + decimal_text(grammage) + 'g-S', issues
    if thickness is not None and prefix:
        return prefix + decimal_text(thickness * 1000) + '-S', issues
    return None, ['型号含义未明确或缺少推导参数，交客户确认']


def geometry(specification, component):
    if component:
        return None, None
    raw = unicodedata.normalize('NFKC', text(specification)).strip().replace('×', '*').replace('x', '*')
    match = re.fullmatch(r'(\d+(?:\.\d+)?)\s*(?:\*\s*(\d+(?:\.\d+)?))?', raw)
    return (number(match[1]), number(match[2])) if match else (None, None)


def prepare(source, reference, business_date):
    import openpyxl
    book = openpyxl.load_workbook(source, read_only=True, data_only=True)
    categories = {c['id']: c for c in reference['categories']}
    paths = {}
    for c in categories.values():
        parts = [c['name']]; parent = c.get('parent'); visited = {c['id']}
        while parent in categories and parent not in visited:
            visited.add(parent); parts.insert(0, categories[parent]['name']); parent = categories[parent].get('parent')
        paths['/'.join(parts)] = c
    # 确认版生成后类目显示名由“金属箔”改为“金属箔/屏蔽”，编码RAW_AUX_FOIL未变。
    foil = next((c for c in categories.values() if c['code'] == 'RAW_AUX_FOIL'), None)
    if foil: paths['原材料/辅材/金属箔'] = foil
    supplier_lookup = defaultdict(list)
    for supplier in reference['suppliers']:
        for key in ('name', 'short', 'abbr'):
            if supplier.get(key) and supplier not in supplier_lookup[normalized(supplier[key])]:
                supplier_lookup[normalized(supplier[key])].append(supplier)
    dictionaries = defaultdict(dict)
    for d in reference['dictionaries']:
        dictionaries[d['type']][d['label']] = d['code']
        dictionaries[d['type']][d['code']] = d['code']
    material_rows = {}
    for row in book['物料信息确认'].iter_rows(min_row=5, values_only=True):
        if row[0] is not None:
            for n in text(row[0]).split(','):
                material_rows[int(n)] = row
    sha = hashlib.sha256(Path(source).read_bytes()).hexdigest()
    batch = {'batchCode': 'MIG' + business_date.replace('-', '') + '-' + sha[:12], 'sourceSha256': sha,
             'businessDate': business_date, 'warehouseName': '原料仓', 'lines': []}
    review = []
    for stock in book['库存数量确认'].iter_rows(min_row=5, values_only=True):
        if stock[0] is None:
            continue
        source_row = int(stock[0]); row = material_rows[source_row]; issues = []
        cat = paths.get(text(row[5]))
        if cat is None:
            raise ValueError('源行' + str(source_row) + '分类路径不在当前库：' + text(row[5]))
        schema = cat.get('schema') or {}
        if isinstance(schema, str): schema = json.loads(schema)
        supplier = supplier_for(row, supplier_lookup)
        if row[11] and not supplier: issues.append('供货来源未唯一匹配供应商表，原文保留')
        name = strip_supplier(text(row[3]), supplier)
        thickness, grammage = number(row[7]), number(row[8])
        model, model_issues = derive_model(text(row[3]), row[4], row[13], cat['prefix'], thickness, grammage)
        issues += model_issues
        component = 'ELECTRONIC' in cat['code']
        width, length = geometry(stock[2], component)
        unit = stock[5]
        if unit == '待确认': unit = None
        original_unit = unit
        if unit is None and number(stock[4]) == 0:
            unit = schema.get('default_unit')
            issues.append('原单位未知，0库存档案暂沿用品类默认单位，客户可维护；未推定原单位')
        if not component and width is None: issues.append('规格未解析为宽长，交客户确认')
        # 宽度、原米数/片数独立保留；不是把所有原数量先强制转成面积。
        brand = None
        if re.search(r'(?<![A-Za-z0-9-])3M', text(row[3]), re.I): brand = dictionaries['brand'].get('3M')
        elif row[10]:
            issues.append('品牌候选未确认，保留原文，不把供应商或未匹配来源自动认作品牌')
        color = dictionaries['color'].get(text(row[9]))
        attributes = {}
        surface = []
        for field in schema.get('fields', []):
            key = field['key']
            if key == 'surface_treatment':
                raw = text(row[3]) + ' ' + text(row[14])
                surface = [o['code'] for o in field.get('options', []) if o['label'] in raw]
                if surface: attributes[key] = surface
            elif key == 'gram_weight_gsm' and grammage is not None:
                attributes[key] = float(grammage)
            elif key == 'double_film' and '双面复膜' in text(row[3]): attributes[key] = True
            elif key == 'hard_coating':
                if '双面加硬' in text(row[3]): attributes[key] = 'DOUBLE'
                elif '单面加硬' in text(row[3]): attributes[key] = 'SINGLE'
        for field in schema.get('fields', []):
            if field.get('required') and field['key'] not in attributes and field.get('default') is None:
                issues.append(field['label'] + '未明确，交客户确认')
        meta = dictionaries['material_meta'].get(text(row[6]))
        if meta and meta not in schema.get('meta_options', []):
            issues.append('材质候选不在该类目允许范围，候选保留原表'); meta = None
        quantity = number(stock[4])
        admitted = stock[10] in ('正常', '零库存')
        if admitted and (quantity is None or quantity < 0 or unit is None):
            raise ValueError('已批准入账范围内数量或单位无效：源行' + str(source_row))
        material = {'categoryId': cat['id'], 'materialName': name, 'materialMeta': meta,
                    'materialForm': schema.get('default_form'), 'model': model, 'brand': brand, 'color': color,
                    'widthMm': decimal_text(width), 'lengthMm': decimal_text(length), 'thicknessMm': decimal_text(thickness),
                    'grammage': decimal_text(grammage), 'unit': unit, 'measurementPolicy': None,
                    'attributeJson': attributes, 'isCustomerSupplied': '客供' in text(row[3]), 'isSample': '样品专用' in text(row[3]),
                    'supplierId': supplier['id'] if supplier else None,
                    'supplierName': supplier['name'] if supplier else None,
                    'supplierMaterialName': text(row[3]), 'remark': '迁移原品名：' + text(row[3]) + '；原规格：' + text(row[4])}
        line = {'sourceRow': source_row, 'originalName': text(row[3]), 'originalSpecification': text(row[4]),
                'originalArea': decimal_text(number(stock[6])), 'originalUnit': original_unit,
                'originalRemark': text(stock[12]), 'location': stock[9], 'quantity': decimal_text(quantity),
                'issues': issues, 'material': material}
        if admitted: batch['lines'].append(line)
        review.append([source_row, '纳入' if admitted else '暂缓', stock[10], name, model, cat['code'], material['supplierName'], brand,
                       line['originalSpecification'], text(stock[3]), text(stock[4]), original_unit, unit, stock[6], stock[9], ';'.join(issues), text(row[3]), text(stock[12])])
    book.close()
    return batch, review


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--business-date', default='2026-11-17', type=lambda s: date.fromisoformat(s).isoformat())
    parser.add_argument('--output', type=Path, help='新JSON路径；核对XLSX使用同名路径')
    parser.add_argument('--write', action='store_true', help='🟡写新文件；已存在即拒绝，不覆盖客户资料')
    parser.add_argument('--reference', type=Path, help='可选只读参考JSON，用于离线复核')
    args = parser.parse_args()
    reference = json.loads(args.reference.read_text()) if args.reference else read_reference()
    batch, review = prepare(args.source, reference, args.business_date)
    summary = {'batchCode': batch['batchCode'], 'sourceRows': len(review), 'admitted': len(batch['lines']),
               'zeroRows': sum(number(l['quantity']) == 0 for l in batch['lines']),
               'deferred': len(review) - len(batch['lines']), 'unresolvedModel': sum(not l['material']['model'] for l in batch['lines'])}
    if args.write:
        if not args.output: parser.error('--write需要--output')
        report = args.output.with_suffix('.xlsx')
        if args.output.exists() or report.exists() or args.output.resolve() == args.source.resolve() or report.resolve() == args.source.resolve():
            raise ValueError('输出已存在或覆盖输入，拒绝写入')
        import openpyxl
        book = openpyxl.Workbook(); sheet = book.active; sheet.title = '迁移核对'
        sheet.append(['源行号', '范围', '原状态', '品名', '型号', '品类', '供应商', '品牌', '原规格', '原数量（保留原文）', '拟入账数量（保留原文）', '原单位候选', '档案单位', '原面积（保留原文）', '摆放位置', '核查事项', '原品名', '原备注'])
        for row in review: sheet.append([str(v) if isinstance(v, Decimal) else v for v in row])
        sheet.freeze_panes = 'A2'; sheet.auto_filter.ref = sheet.dimensions
        for column in sheet.columns:
            sheet.column_dimensions[column[0].column_letter].width = 22
        rules = book.create_sheet('规则及批次')
        rules.append(['规则真源', 'jjx-docs/task-docs/dev-20261119-001-plan.md'])
        for key, value in summary.items(): rules.append([key, value])
        rules.append(['业务日期', batch['businessDate']]); rules.append(['源文件SHA256', batch['sourceSha256']])
        rules.append(['成本/日期', '成本与未知生产/到期日期留空；不代表零成本；实际导入时间另记'])
        args.output.write_text(json.dumps(batch, ensure_ascii=False, indent=2), encoding='utf-8')
        book.save(report)
        summary['output'] = str(args.output); summary['review'] = str(report)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__': main()
