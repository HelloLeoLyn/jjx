#!/usr/bin/env python3
"""dev-20261024-008 阶段二。先 --dry-run，核对后 --apply；重复 --apply 跳过一致行。

只依赖标准库与 mysql CLI；凭据从 MYSQL_PWD 传入，不生成备份、不操作服务。
分类/厚宽/材质/颜色/型号/品牌只调用阶段一 classify，不复制其规则。
--audit 可将逐行计划和冲突输出至 /tmp（业务数据不入 git）。
"""

import argparse
from collections import Counter
from decimal import Decimal
from enum import IntEnum
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid


REPO = Path(__file__).resolve().parents[1]
TASK = 'dev-20261024-008'
STAGE_ONE_SHA = 'af15c9cbfb616f6b02f70beaa615fc75b62d17ddd75912eae9a187d27ee4843a'
EXPECTED = {'RAW_BASE_PC': 165, 'RAW_BASE_PET': 194, 'RAW_BASE_PVC': 7,
            'RAW_BASE_PMMA': 32, 'RAW_AUX_PROTECT': 116, 'RAW_AUX_RELEASE': 78,
            'RAW_AUX_TAPE': 205, 'RAW_AUX_FOAM': 24, 'RAW_AUX_FOIL': 11,
            'RAW_AUX_OPTICAL': 9, 'RAW_AUX_ELECTRONIC': 200, '未归类': 566}
spec = importlib.util.spec_from_file_location('material_dryrun', REPO / 'scripts/material-reclassify-dryrun.py')
dryrun = importlib.util.module_from_spec(spec)
sys.dont_write_bytecode = True
spec.loader.exec_module(dryrun)
WRITE_COLUMNS = {'category_id', 'template_id', 'thickness', 'width_size', 'attribute_json',
                 'material_meta', 'material_form', 'color', 'model', 'brand',
                 'is_customer_supplied', 'is_sample'}


class RecordStatus(IntEnum):
    # 对齐后端 com.jjx.common.enums.StatusEnum，分类旧表虽为字符串仍用同一代码。
    NORMAL = 1
    DISABLED = 0


def dump(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def sha(value):
    return hashlib.sha256(dump(value).encode()).hexdigest()


def literal(value):
    # 十六进制字面量避免 SQL mode、引号、反斜杠及业务文本注入的歧义。
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    return "CONVERT(X'" + str(value).encode().hex() + "' USING utf8mb4)"


class Database:
    """保持同一 mysql 会话，批次锁、比较、写入、回查在一个事务内完成。"""
    def __init__(self, args):
        self.errors = tempfile.TemporaryFile(mode='w+t', encoding='utf-8')
        self.proc = subprocess.Popen(
            ['mysql', '--batch', '--raw', '--skip-column-names', '--unbuffered',
             '--skip-reconnect', '--connect-timeout=5', '--default-character-set=utf8mb4',
             '--host=' + args.host, '--port=' + str(args.port), '--user=' + args.user,
             args.database], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=self.errors, text=True, encoding='utf-8', bufsize=1)
        self.query("SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'; "
                   'SET SESSION innodb_lock_wait_timeout=15;')

    def query(self, sql):
        marker = 'END_' + uuid.uuid4().hex
        self.proc.stdin.write(sql + ";\nSELECT '" + marker + "';\n")
        self.proc.stdin.flush()
        result = []
        for line in self.proc.stdout:
            line = line.rstrip('\n')
            if line == marker:
                return [json.loads(v) for v in result]
            result.append(line)
        self.proc.wait()
        self.errors.seek(0)
        raise RuntimeError(f'mysql exit={self.proc.returncode}: {self.errors.read()}')

    def close(self):
        # 不重试失败批次；会话退出时未提交事务由 MySQL 回滚。
        if self.proc.poll() is None:
            self.proc.stdin.close()
        code = self.proc.wait()
        self.errors.seek(0)
        errors = self.errors.read()
        self.errors.close()
        if errors:
            print(errors, file=sys.stderr, end='')
        if code:
            raise RuntimeError(f'mysql exit={code}')


def read_config(db, lock=False):
    suffix = ' FOR SHARE' if lock else ''
    categories = db.query('SELECT JSON_ARRAY(category_id,category_code,parent_id,status) '
                          'FROM inventory_material_category ORDER BY category_id' + suffix)
    templates = db.query('SELECT JSON_ARRAY(template_id,category_id,attribute_schema,status) '
                         'FROM inventory_attribute_template ORDER BY template_id' + suffix)
    dictionaries = db.query("SELECT JSON_ARRAY(dict_code,item_value,label,is_active,deleted) "
                            "FROM sys_dict_item WHERE dict_code IN ('material_meta','form','color','brand') "
                            'ORDER BY item_id' + suffix)
    return dict(categories=categories, templates=templates, dictionaries=dictionaries)


def read_materials(db, columns, where='', lock=False):
    return [dict(zip(columns, row)) for row in db.query(
        'SELECT JSON_ARRAY(' + ','.join('`' + c + '`' for c in columns) + ') '
        'FROM inventory_material ' + where + ' ORDER BY material_code' + (' FOR UPDATE' if lock else ''))]


def dictionary(config, kind, value):
    if not value:
        return None
    matches = {code for dtype, code, label, active, deleted in config['dictionaries']
               if dtype == kind and active == RecordStatus.NORMAL and deleted == 0
               and value.casefold() in {code.casefold(), label.casefold()}}
    return next(iter(matches)) if len(matches) == 1 else None


def facts(row):
    original = dryrun.originals(row['remark'])
    return ' '.join(filter(None, [original['原名称'] or row['material_name'], original['原规格']]))


def form_value(text, config):
    # 只取原始事实中的字面形态；多个不同形态均留空，不以模板默认值猜形态。
    candidates = {code for kind, code, label, active, deleted in config['dictionaries']
                  if kind == 'form' and active == RecordStatus.NORMAL and deleted == 0 and label in text}
    return next(iter(candidates)) if len(candidates) == 1 else None


def template_values(result, row, schema):
    """把已有提取结果投影到模板；额外模板字段仅认字面选项/布尔标记。"""
    text = facts(row)
    attrs = {}
    thickness = result['attributes']['thickness']
    # 无单位厚度原样写主列；不把 C/#/T/g 或无单位数字猜成 mm。
    number = re.fullmatch(r'(\d+(?:\.\d+)?)\s*(mm|毫米|um|μm|µm|微米|cm|厘米)', thickness, re.I)
    for field in schema['fields']:
        key, kind = field['key'], field['type']
        if key == 'thickness_mm' and number:
            factor = {'mm': '1', '毫米': '1', 'um': '.001', 'μm': '.001', 'µm': '.001',
                      '微米': '.001', 'cm': '10', '厘米': '10'}[number[2].lower()]
            value = Decimal(number[1]) * Decimal(factor)
            if (Decimal(str(field.get('min', 0))) <= value <= Decimal(str(field.get('max', 1000)))
                    and max(0, -value.normalize().as_tuple().exponent) <= field.get('precision', 4)):
                attrs[key] = float(value)
        elif kind in ('enum', 'multienum'):
            # 排除“其他”兜底，重叠标签只认最长项（中高粘不能同时算高粘）。
            options = [o for o in field.get('options', []) if o['code'] != 'OTHER'
                       and re.search(re.escape(o['label']), text, re.I)]
            options = [o for o in options if not any(o['label'] != p['label']
                       and o['label'] in p['label'] for p in options)]
            if kind == 'enum' and len(options) == 1:
                attrs[key] = options[0]['code']
            elif kind == 'multienum' and options:
                attrs[key] = [o['code'] for o in options]
        elif kind == 'boolean':
            # 显式否定优先；没有字面证据不能写 false。
            label = field['label']
            if '不' + label in text or '无' + label in text:
                attrs[key] = False
            elif label in text:
                attrs[key] = True
    for field in schema['fields']:
        if any(attrs.get(k) != v for k, v in field.get('visible_when', {}).items()):
            attrs.pop(field['key'], None)
    return attrs


def plan_rows(rows, config):
    category_map = {c[1]: c[0] for c in config['categories'] if str(c[3]) == str(RecordStatus.NORMAL.value)}
    children = {c[2] for c in config['categories']}
    templates = {}
    for tid, cid, schema, active in config['templates']:
        if active != RecordStatus.NORMAL or cid in templates:
            raise ValueError('模板未启用或分类存在多模板')
        templates[cid] = (tid, schema)
    plans, conflicts = [], []
    results = [dryrun.classify([row[c] for c in dryrun.FIELDS]) for row in rows]
    if dict(Counter(r['target_category'] or '未归类' for r in results)) != EXPECTED:
        raise ValueError('分类分布与阶段一不一致，停止')
    for row, result in zip(rows, results):
        target = {}
        category = result['target_category']
        if category:
            cid = category_map[category]
            if cid in children or cid not in templates:
                raise ValueError('目标不是末级分类或没有唯一模板')
            tid, schema = templates[cid]
            attrs = template_values(result, row, schema)
            extracted = result['attributes']
            material = {'硅胶': 'SILICONE', '铝': 'AL', '铜': 'CU', '不锈钢': 'STEEL'}.get(
                extracted['material'], extracted['material'])
            meta = dictionary(config, 'material_meta', material)
            if meta not in schema['meta_options']:
                meta = None
            # material 是现有保存入口定义的材质镜像，不是模板自定义字段。
            if meta:
                attrs['material'] = meta
            target.update(category_id=cid, template_id=tid, attribute_json=attrs,
                          thickness=extracted['thickness'] or None,
                          width_size=extracted['width_size'] or None,
                          material_meta=meta, material_form=form_value(facts(row), config))
            for column in ('color', 'model', 'brand'):
                extracted_value = extracted[column]
                value = extracted_value if column == 'model' else dictionary(config, column, extracted_value)
                if row[column] and str(row[column]).strip():
                    # 用字典归一比较，防止 透明/TRANSPARENT、tesa/TESA 被误计冲突。
                    existing = row[column] if column == 'model' else dictionary(config, column, row[column]) or row[column]
                    if extracted_value and existing != (value or extracted_value):
                        conflicts.append(dict(material_code=row['material_code'], field=column,
                                              existing=row[column], extracted=extracted_value, preserved=True))
                elif value:
                    target[column] = value
        elif row['category_id'] is not None or row['template_id'] is not None or row['attribute_json'] is not None:
            raise ValueError('未归类物料已有分类/模板/属性，停止而非自动清空')
        # 标记覆盖全量；只置1，不清0。分类与属性仍仅使用阶段一原始事实。
        tag_text = (row['material_name'] or '') + ' ' + (row['remark'] or '')
        for column, pattern in [('is_customer_supplied', r'客供|客户供料|客户提供'),
                                ('is_sample', r'样品')]:
            if re.search(pattern, tag_text) and row[column] != 1:
                target[column] = 1
        changes = {k: v for k, v in target.items() if row[k] != v}
        plans.append(dict(material_code=row['material_code'], category=category,
                          target=target, changes=changes))
    return results, plans, conflicts


def statistics(rows, config):
    codes = {c[0]: c[1] for c in config['categories']}
    return dict(total=len(rows), categories=dict(Counter(codes.get(r['category_id'], '未归类') for r in rows)),
                category_null=sum(r['category_id'] is None for r in rows),
                template_null=sum(r['template_id'] is None for r in rows),
                **{k: sum(r[k] is not None and r[k] != '' for r in rows)
                   for k in ('thickness', 'width_size', 'material_meta', 'material_form')},
                attribute_json_present=sum(r['attribute_json'] is not None for r in rows),
                attribute_json_nonempty=sum(bool(r['attribute_json']) for r in rows),
                template_field_nonempty=sum(bool({k: v for k, v in (r['attribute_json'] or {}).items()
                                                  if k != 'material'}) for r in rows),
                is_customer_supplied=sum(r['is_customer_supplied'] == 1 for r in rows),
                is_sample=sum(r['is_sample'] == 1 for r in rows))


def safe_output(path):
    path = path.resolve()
    if not path.is_relative_to(Path('/tmp')):
        raise ValueError('计划/逐行审计只允许输出到 /tmp')
    return path


def backup_check(path):
    data = path.read_bytes()
    tables = re.findall(rb'^CREATE TABLE `([^`]+)`', data, re.M)
    if len(data) <= 1024 or len(tables) != 133 or b'hr_employee' in tables or b'inventory_material' not in tables:
        raise ValueError('手工备份无效（应含133表及物料表，排除hr_employee）')
    return dict(path=str(path), tables=len(tables), sha256=hashlib.sha256(data).hexdigest())


def project_row(row, changes):
    result = {**row, **changes}
    # 对照306及现存search_blob表达式核对生成列；这些列始终不进入UPDATE白名单。
    meta = (result['attribute_json'] or {}).get('material')
    result['attr_meta'] = meta if isinstance(meta, str) else None
    thickness = (result['thickness'] or '').strip()
    result['attr_thickness_num'] = float(Decimal(thickness)) if re.fullmatch(
        r'[0-9]{1,8}(\.[0-9]{1,4})?', thickness) else None
    result['search_blob'] = ' '.join(result[k] or '' for k in (
        'material_name', 'material_name_en', 'material_code', 'model', 'material_grade',
        'color', 'specification', 'search_keywords', 'size', 'brand'))
    return result


def apply_batch(db, columns, before, plans, config, rollback=False):
    db.query('START TRANSACTION')
    try:
        if read_config(db, lock=True) != config:
            raise ValueError('分类/模板/字典已变更，停止')
        ids = ','.join(str(r['material_id']) for r in before)
        where = 'WHERE material_id IN (' + ids + ')'
        # 每批写前 SELECT FOR UPDATE，逐列复核批次主键、编码、原事实及人工属性。
        if read_materials(db, columns, where, lock=True) != before:
            raise ValueError('批次键值或数据已变更，停止；不会覆盖并发修改')
        expected = []
        for row, plan in zip(before, plans):
            changes = plan['changes']
            if not changes or not changes.keys() <= WRITE_COLUMNS:
                raise ValueError('写入白名单或批次不正确')
            assignment = ','.join('`' + k + '`=' + literal(dump(v) if k == 'attribute_json' else v)
                                  for k, v in changes.items())
            count = db.query('UPDATE inventory_material SET ' + assignment + ",update_by='codex' "
                             'WHERE material_id=' + str(row['material_id']) +
                             ' AND BINARY material_code=BINARY ' + literal(row['material_code']) +
                             '; SELECT JSON_ARRAY(ROW_COUNT())')[0][0]
            if count != 1:
                raise ValueError('UPDATE影响行数不等于1')
            expected.append(project_row(row, changes))
        actual = read_materials(db, columns, where)
        for old, new in zip(expected, actual):
            different = [k for k in columns if k not in ('update_by', 'update_time') and old[k] != new[k]]
            if different:
                raise ValueError('批次写后不一致，回滚当前批次：' + old['material_code'] + ' ' + ','.join(different))
        db.query('ROLLBACK' if rollback else 'COMMIT')
        if rollback and read_materials(db, columns, where) != before:
            raise ValueError('真实库事务回滚核验失败')
    except Exception:
        if db.proc.poll() is None:
            db.query('ROLLBACK')
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--dry-run', action='store_true')
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--rollback-check', action='store_true', help='真实库首批UPDATE后ROLLBACK并逐列核对')
    parser.add_argument('--host', default=os.environ.get('JJX_DB_HOST', '127.0.0.1'))
    parser.add_argument('--port', type=int, default=int(os.environ.get('JJX_DB_PORT', '3306')))
    parser.add_argument('--user', default=os.environ.get('JJX_DB_USER', 'root'))
    parser.add_argument('--database', default=os.environ.get('JJX_DB_NAME', 'jjx_erp_db'))
    parser.add_argument('--batch-size', type=int, default=200)
    parser.add_argument('--backup', type=Path, default=REPO / 'jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1143.sql')
    parser.add_argument('--plan', type=Path, default=Path('/tmp') / (TASK + '-material-plan.json'))
    parser.add_argument('--audit', type=Path)
    args = parser.parse_args()
    if not 1 <= args.batch_size <= 200 or args.database != 'jjx_erp_db':
        raise ValueError('批次必须1..200，目标库必须jjx_erp_db')
    plan_path = safe_output(args.plan)
    audit_path = safe_output(args.audit) if args.audit else None
    db = Database(args)
    try:
        db.query('START TRANSACTION READ ONLY')
        task = db.query("SELECT JSON_ARRAY(status,assignee_name,start_time) FROM sys_task WHERE task_code='" + TASK + "'")
        if len(task) != 1 or task[0][:2] != [1, 'codex'] or not task[0][2]:
            raise ValueError('任务必须进行中、已记开始时间且执行者codex')
        columns = [c[0] for c in db.query("SELECT JSON_ARRAY(COLUMN_NAME) FROM information_schema.COLUMNS "
                    "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' ORDER BY ORDINAL_POSITION")]
        if len(columns) != 54:
            raise ValueError('物料表不是007A预期54列，停止')
        config = read_config(db)
        rows = read_materials(db, columns)
        db.query('COMMIT')
        original_rows = [[r[k] for k in dryrun.FIELDS] for r in rows]
        # 阶段一摘要使用不排序键的五列数组序列，与原脚本完全一致。
        source_sha = hashlib.sha256(json.dumps(original_rows, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
        if len(rows) != 1607 or source_sha != STAGE_ONE_SHA:
            raise ValueError('原始五列快照与阶段一不一致，停止')
        results, plans, conflicts = plan_rows(rows, config)
        projected = [project_row(r, p['changes']) for r, p in zip(rows, plans)]
        # 目标摘要使用最终投影，空值填充后重复执行也得到同一个摘要。
        signature = dict(source_sha=source_sha, config_sha=sha(config),
                         target_sha=sha([[r['material_id'], {k: r[k] for k in sorted(WRITE_COLUMNS)}] for r in projected]))
        changing = [(r, p) for r, p in zip(rows, plans) if p['changes']]
        summary = dict(mode='dry-run' if args.dry_run else 'rollback-check' if args.rollback_check else 'apply',
                       stage_one=dryrun.summary(results), update_rows=len(changing),
                       classified_update_rows=sum(bool(p['category']) for _, p in changing),
                       flag_only_unclassified=sum(not p['category'] for _, p in changing),
                       conflicts=len(conflicts), conflict_rows=len({c['material_code'] for c in conflicts}),
                       conflict_fields=dict(Counter(c['field'] for c in conflicts)),
                       before=statistics(rows, config), projected=statistics(projected, config), signature=signature)
        print(dump(summary), flush=True)
        if audit_path:
            audit_path.write_text(dump(dict(summary=summary, conflicts=conflicts, plans=plans,
                                           unclassified=[r for r in results if not r['target_category']])) + '\n', encoding='utf-8')
        if args.dry_run:
            plan_path.write_text(dump(signature) + '\n', encoding='utf-8')
            return
        if not plan_path.is_file() or json.loads(plan_path.read_text(encoding='utf-8')) != signature:
            raise ValueError('没有与当前数据/模板/目标匹配的dry-run计划，停止')
        print(dump(dict(backup=backup_check(args.backup))), flush=True)
        batch_count = 0
        for offset in range(0, len(changing), args.batch_size):
            batch = changing[offset:offset + args.batch_size]
            apply_batch(db, columns, [r for r, _ in batch], [p for _, p in batch], config, args.rollback_check)
            batch_count += 1
            print(dump(dict(batch=batch_count, rows=len(batch), first=batch[0][0]['material_code'],
                            last=batch[-1][0]['material_code'], rolled_back=args.rollback_check)), flush=True)
            if args.rollback_check:
                break
        db.query('START TRANSACTION READ ONLY')
        after = read_materials(db, columns)
        final_config = read_config(db)
        db.query('COMMIT')
        if final_config != config:
            raise ValueError('写后配置变化，需复核')
        expected = rows if args.rollback_check else projected
        changed_codes = {r['material_code'] for r, _ in changing} if not args.rollback_check else set()
        if len(after) != len(expected):
            raise ValueError('物料总数改变')
        for old, new in zip(expected, after):
            ignored = {'update_by', 'update_time'} if old['material_code'] in changed_codes else set()
            if any(old[k] != new[k] for k in columns if k not in ignored):
                raise ValueError('写后全列核验不一致（含未归类/人工数据/material_type）')
        if not args.rollback_check:
            _, remaining, _ = plan_rows(after, config)
            if any(p['changes'] for p in remaining):
                raise ValueError('幂等校验失败')
            if statistics(after, config)['category_null'] != 566:
                raise ValueError('未归类数量不等于566')
        print(dump(dict(verified=True, after=statistics(after, config), batches=batch_count,
                        unclassified=566, material_type_unchanged=True,
                        protected_existing_unchanged=True, idempotent=not args.rollback_check)), flush=True)
    finally:
        db.close()


if __name__ == '__main__':
    main()
