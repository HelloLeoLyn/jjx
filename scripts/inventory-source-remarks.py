#!/usr/bin/env python3
"""迁移来源备注修复输入准备（dev-20261120-002）。
默认只读预览；--write仅生成私有会话输入，不写数据库。
从仓库根执行后，按手册用db-migrate执行380，需用户已有手工全库备份。
原Excel/迁移JSON只读，客户原文不提交Git；不改原凭证、流水、数量和成本。
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys


def append_note(current, batch_code, source_row, note):
    if not note or not note.strip():
        return current
    marker = f'【迁移来源 {batch_code} / 源行{source_row}】'
    block = marker + '\n' + note
    if current and marker in current:
        start = current.index(marker)
        end = current.find('\n\n【迁移来源 ', start + len(marker))
        existing = current[start:] if end < 0 else current[start:end]
        if existing == block:
            return current
        raise ValueError('同一迁移源行备注不一致')
    result = current + '\n\n' + block if current and current.strip() else block
    if len(result) > 1000:
        raise ValueError('合并来源备注超过1000字，禁止截断')
    return result


def plan(batch, records):
    """已入账来源逐行核对；只合并存在的非空备注，不改变其它资料。"""
    inputs = {line['sourceRow']: line for line in batch['lines']}
    if len(inputs) != len(batch['lines']) or len(records) != len(inputs):
        raise ValueError('源行不唯一或数据库行数不匹配')
    updates = {}; sources = []; seen = set()
    for row in records:
        source_row = row['sourceRow']
        if source_row in seen or source_row not in inputs:
            raise ValueError('数据库源行不唯一或不属于本次迁移')
        seen.add(source_row)
        note = inputs[source_row].get('originalRemark')
        if not note or not note.strip():
            continue
        mid = row['materialId']
        state = updates.setdefault(mid, {'materialId': mid, 'before': row['remark'], 'after': row['remark']})
        if state['before'] != row['remark']:
            raise ValueError('同一物料当前备注不一致')
        state['after'] = append_note(state['after'], batch['batchCode'], source_row, note)
        sources.append({'sourceRow': source_row, 'materialId': mid, 'note': note})
    return {'batchCode': batch['batchCode'], 'sourceSha256': batch['sourceSha256'],
            'sources': sources, 'materials': list(updates.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path, help='正式迁移JSON')
    parser.add_argument('--original', required=True, type=Path, help='客户确认Excel，核验指纹与原始备注')
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    batch = json.loads(args.source.read_text(encoding='utf-8-sig'))
    if hashlib.sha256(args.original.read_bytes()).hexdigest() != batch['sourceSha256']:
        raise ValueError('原始Excel指纹不符')
    from openpyxl import load_workbook
    wb = load_workbook(args.original, read_only=True, data_only=True)
    try:
        raw = {n: '' if r[2] is None else str(r[2]) for n, r in enumerate(wb['原始数据_保留'].iter_rows(min_row=2, values_only=True), 2)}
        if any(line.get('originalRemark', '') != raw[line['sourceRow']] for line in batch['lines']):
            raise ValueError('迁移JSON原备注与客户原表不符')
    finally:
        wb.close()
    # 复用只读SQL连接；禁用任意写入参数。
    spec = importlib.util.spec_from_file_location('inventory_remarks_task_reader', Path(__file__).with_name('task-update.py'))
    module = importlib.util.module_from_spec(spec); sys.modules[spec.name] = module; spec.loader.exec_module(module)
    db = module.MysqlSession()
    try:
        header = db.query("SELECT JSON_OBJECT('inboundId',inbound_id,'sourceSha256',trace_id) FROM inventory_inbound_order WHERE source_type='INV_MIGRATION' AND inbound_no=" + module.literal(batch['batchCode']))
        if len(header) != 1 or header[0]['sourceSha256'] != batch['sourceSha256']:
            raise ValueError('原迁移凭证未唯一匹配')
        records = db.query("SELECT JSON_OBJECT('sourceRow',i.sort_order,'materialId',m.material_id,'remark',m.remark) FROM inventory_inbound_item i JOIN inventory_material m ON m.material_id=i.material_id WHERE i.inbound_id=" + str(int(header[0]['inboundId'])) + ' ORDER BY i.sort_order')
    finally:
        db.close()
    payload = plan(batch, records)
    serialized = json.dumps(payload, ensure_ascii=False, separators=(',', ':'))
    summary = {'batchCode': batch['batchCode'], 'sourceNotes': len(payload['sources']),
               'materials': len(payload['materials']), 'changes': sum(x['before'] != x['after'] for x in payload['materials']),
               'payloadSha256': hashlib.sha256(serialized.encode()).hexdigest()}
    if args.write:
        output = Path('.tmp/inventory-source-remarks/input.sql')
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open('x', encoding='utf-8') as f:
            f.write('SET @inventory_source_remarks = CONVERT(0x' + serialized.encode().hex() + ' USING utf8mb4);\n')
        summary['output'] = str(output)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
