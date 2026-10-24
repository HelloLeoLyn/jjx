#!/usr/bin/env python3
"""dev-20261024-008 阶段一：只读分类与属性试算，无应用/写库入口。

MYSQL_PWD=... python3 scripts/material-reclassify-dryrun.py > /tmp/material-dryrun.jsonl
支持 --input 读取同一五列的 JSONL 快照；--report/--excel 均需显式指定。
Excel 依赖 openpyxl，且禁止输出到仓库；完整逐行业务数据只输出到 stdout。
"""

import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


REPO = Path(__file__).resolve().parents[1]
FIELDS = ('material_code', 'material_name', 'specification', 'size', 'remark')
# 固定 SQL：只读五列，不筛旧状态，不依赖 007 新列，不读旧分类/类型。
READ_SQL = """START TRANSACTION READ ONLY;
SELECT JSON_ARRAY(material_code,material_name,specification,size,remark)
FROM inventory_material ORDER BY material_code;
COMMIT;"""
CATEGORIES = {
    'RAW_BASE_PC': 'PC膜/片', 'RAW_BASE_PET': 'PET膜/片',
    'RAW_BASE_PVC': 'PVC', 'RAW_BASE_PMMA': 'PMMA/压克力',
    'RAW_BASE_OTHER': '其他基材', 'RAW_AUX_PROTECT': '保护膜',
    'RAW_AUX_RELEASE': '离型纸/膜', 'RAW_AUX_TAPE': '胶带/胶粘',
    'RAW_AUX_FOAM': '泡棉/硅胶', 'RAW_AUX_FOIL': '金属箔/银龙',
    'RAW_AUX_OPTICAL': '光学膜', 'RAW_AUX_ELECTRONIC': '电子料',
    'RAW_AUX_OTHER': '其他辅材', 'RAW_INK': '油墨',
    'SEMI': '半成品', 'FINISHED': '成品',
}
# 编号即用户本轮优先级；OTHER 不充当未命中的兜底。
RULES = [
    ('R01', 'RAW_AUX_PROTECT', r'保护膜'),
    ('R02', 'RAW_AUX_RELEASE', r'离型'),
    ('R03', 'RAW_AUX_TAPE', r'(?<![A-Z0-9.])3M(?!M)|TESA|VHB|双面胶|胶带|OCA'),
    ('R04', 'RAW_AUX_FOAM', r'泡棉|(?<![A-Z])EVA(?![A-Z])|硅胶'),
    ('R05', 'RAW_AUX_FOIL', r'铝箔|铜箔|银龙'),
    ('R06', 'RAW_AUX_OPTICAL', r'扩散|防窥|光学膜'),
    ('R07', 'RAW_AUX_ELECTRONIC', r'连接器|HOUSING|(?<![A-Z])PIN(?![A-Z])|(?<![A-Z])LED(?![A-Z])|电阻|电容|二极管|弹片'),
    ('R08', 'RAW_INK', r'油墨'),
    ('R09', 'RAW_BASE_PC', r'(?<![A-Z])PC(?![A-Z])'),
    ('R10', 'RAW_BASE_PET', r'(?<![A-Z])PET(?![A-Z])'),
    ('R11', 'RAW_BASE_PVC', r'(?<![A-Z])PVC(?![A-Z])'),
    ('R12', 'RAW_BASE_PMMA', r'(?<![A-Z])PMMA(?![A-Z])|压克力|亚克力'),
]
COMPILED = [(n, c, re.compile(p, re.I)) for n, c, p in RULES]
NUMBER = r'\d+(?:\.\d+)?'
UNIT = r'(?:mm|um|μm|µm|毫米|微米|cm|厘米|g/m[²2]|g/㎡|克/平方米|g|克|C|#|T)'


def originals(remark):
    """独立提取标记；后续【…】不混入原始字段。空原规格是合法事实。"""
    values = {}
    for label in ('原名称', '原规格'):
        match = re.search('【' + label + '】(.*?)(?=【|$)', remark or '', re.S)
        values[label] = match.group(1).strip() if match else None
    return values


def unique_attribute(candidates):
    """多来源值不同则留空，不自作主张选一个。"""
    values = {value for value, _, _ in candidates}
    if len(values) != 1:
        return '', [], len(values) > 1
    return next(iter(values)), [dict(source=s, text=t) for _, s, t in candidates], False


def attributes(sources, category):
    found = {k: [] for k in ('thickness', 'width_size', 'material', 'color', 'model', 'brand')}
    def add(key, value, source, text):
        found[key].append((value.strip(), source, text))

    # 括号通常是供应商，型号只取明确标记或含字母数字的独立型号串。
    for source, text in sources:
        core = re.sub(r'[（(][^()（）]*[)）]', ' ', text)
        for match in re.finditer(r'(?:厚度|厚|克重)\s*[:：=]?\s*(' + NUMBER + r'\s*' + UNIT + r'?)', core, re.I):
            add('thickness', match[1], source, match[0])
        # 普通 mm 可能是针距、宽或高度；只有膜/片/泡棉等明确类别的名称起始才取厚度。
        if category in {'RAW_BASE_PC', 'RAW_BASE_PET', 'RAW_BASE_PVC', 'RAW_BASE_PMMA',
                        'RAW_AUX_PROTECT', 'RAW_AUX_RELEASE', 'RAW_AUX_FOAM',
                        'RAW_AUX_FOIL', 'RAW_AUX_OPTICAL', 'RAW_AUX_TAPE'}:
            match = re.match(r'\s*(' + NUMBER + r'\s*' + UNIT + r')', core, re.I)
            if not match:
                match = re.match(r'\s*(\d+\.\d+)(?=[^\d.A-Za-z]|PC|PET|PVC|PMMA)', core, re.I)
            if match and source != '原规格':
                # 裸整数不可当厚度；0.175中砂面PC 是任务卡指定的无单位厚度。
                value = match[1].strip()
                if re.search(r'[.A-Za-z#μµ]|毫米|微米|克', value):
                    add('thickness', value, source, match[0])
        for match in re.finditer(r'(?:宽度|宽|幅宽)\s*[:：=]?\s*(' + NUMBER + r'\s*(?:mm|cm|m|毫米|厘米)?)', core, re.I):
            add('width_size', match[1], source, match[0])
        # 原规格仅整串纯数字/二维尺寸可按该导入字段的宽/长×宽语义读取。
        if source == '原规格' and re.fullmatch(NUMBER + r'(?:\s*[*×xX]\s*' + NUMBER + r')?\s*(?:mm|cm|m|毫米|厘米)?', text, re.I):
            add('width_size', re.sub(r'\s+', '', re.sub(r'[*xX]', '×', text)), source, text)
        for match in re.finditer(r'(?<![A-Z])(?:PMMA|PET|PVC|PC|PE|PP|EVA|PU)(?![A-Z])|硅胶|不锈钢|铝箔|铜箔|压克力|亚克力', text, re.I):
            # 3M9469PC 的 PC 是型号后缀，不能作为 PC 材质；T4901PET保护膜有明确材质语义。
            if (re.search(r'[A-Za-z][A-Za-z0-9._/-]*$', text[:match.start()])
                    and not re.match(r'保护膜|膜|片', text[match.end():])):
                continue
            value = match[0].upper()
            value = {'铝箔': '铝', '铜箔': '铜', '压克力': 'PMMA', '亚克力': 'PMMA'}.get(value, value)
            add('material', value, source, match[0])
        for match in re.finditer(r'透明|白色|黑色|黄色|红色|蓝色|绿色|灰色|银色|金色|紫色|橙色|翠绿|黄绿', core):
            add('color', match[0], source, match[0])
        for match in re.finditer(r'(?<![A-Z0-9.])3M(?!M)|TESA', core, re.I):
            add('brand', 'tesa' if match[0].upper() == 'TESA' else '3M', source, match[0])
        for key, label in (('brand', '品牌'), ('model', '型号')):
            for match in re.finditer(label + r'\s*[:：=]\s*([^\s,，;；()（）]+)', text):
                add(key, match[1], source, match[0])
        if source != '原规格' and not re.search(r'型号\s*[:：=]', text):
            for token in re.findall(r'(?<![A-Za-z0-9.])[A-Za-z0-9][A-Za-z0-9._/#-]*', core):
                if (re.search('[A-Za-z]', token) and re.search(r'\d', token)
                        and not re.fullmatch(NUMBER + r'(?:' + UNIT + r')|\d+PIN', token, re.I)
                        and not re.match(NUMBER + r'(?:mm|um|cm|PC|PET|PVC|PMMA)', token, re.I)
                        and (not re.match(r'^\d', token) or re.match(r'^3M\d', token, re.I))
                        and not re.match(r'^(?:PC|PET|PVC|PMMA)/', token, re.I)
                        and token.upper() not in {'3M'}):
                    add('model', token, source, token)
    values, evidence, conflicts = {}, {}, []
    for key, candidates in found.items():
        values[key], evidence[key], conflict = unique_attribute(candidates)
        if conflict:
            conflicts.append(key)
    return values, evidence, conflicts


def classify(row):
    raw = dict(zip(FIELDS, row))
    original = originals(raw['remark'])
    fallback = not original['原名称'] or original['原规格'] is None
    sources = []
    if original['原名称']:
        sources.append(('原名称', original['原名称']))
    elif raw['material_name']:
        sources.append(('当前名称', raw['material_name']))
    if original['原规格']:
        sources.append(('原规格', original['原规格']))
    hits = []
    for number, category, pattern in COMPILED:
        evidence = [dict(source=s, text=m[0]) for s, text in sources for m in pattern.finditer(text)]
        if evidence:
            hits.append(dict(rule=number, category=category, evidence=evidence))
    # 不读 material_type，也不拿冻结编码反推分类；只认原始文本中显式的 F/S 类型声明。
    type_pattern = re.compile(r'半成品|(?<!半)成品|(?:类型|TYPE)\s*[:：=]?\s*[FS](?![A-Z0-9])', re.I)
    for source, text in sources:
        for match in type_pattern.finditer(text):
            semi = match[0] == '半成品' or match[0].upper().endswith('S')
            hits.append(dict(rule='R13', category='SEMI' if semi else 'FINISHED',
                             evidence=[dict(source=source, text=match[0])]))
    winner = hits[0] if hits else dict(rule='R14', category='', evidence=[])
    conflict = len({h['category'] for h in hits}) > 1
    confidence = ('未归类' if not hits else 'low' if fallback else 'medium'
                  if conflict or all(e['source'] != '原名称' for e in winner['evidence']) else 'high')
    attrs, attr_evidence, attr_conflicts = attributes(sources, winner['category'])
    reason = ''
    if not hits:
        text = ' '.join(t for _, t in sources)
        if not text:
            reason = '原始事实与当前名称皆空'
        elif re.search(r'灯|光学胶|胶|纸|PU|RUBBER|KAPTON|镀金|镀镍|公品|母品|公PIN|母端', text, re.I):
            reason = '有用途/材质描述，但不在本轮已授权关键词规则内'
        elif re.search(r'[A-Za-z]+.*\d|\d.*[A-Za-z]+', text):
            reason = '仅型号/规格代号，缺少可判分类的关键词'
        else:
            reason = '名称/规格未命中已授权分类规则'
    candidates = []
    if not hits:
        text = ' '.join(t for _, t in sources)
        for pattern, candidate in [(r'灯|光|镀金|镀镍|公品|母品|母端|排针', 'RAW_AUX_ELECTRONIC'),
                                   (r'胶|OCA', 'RAW_AUX_TAPE'),
                                   (r'纸|PU|RUBBER|KAPTON', 'RAW_BASE_OTHER / RAW_AUX_OTHER')]:
            if re.search(pattern, text, re.I):
                candidates.append(candidate + '（仅候选，需人工确认）')
        if not candidates:
            candidates.append('待人工核对用途/材质；无足够依据给出具体候选')
    return dict(material_code=raw['material_code'], current_name=raw['material_name'],
                original_name=original['原名称'], original_specification=original['原规格'],
                specification=raw['specification'], size=raw['size'],
                target_category=winner['category'], rule=winner['rule'],
                evidence=winner['evidence'], confidence=confidence, original_missing=fallback,
                all_rule_hits=hits, category_conflict=conflict, attributes=attrs,
                attribute_evidence=attr_evidence, attribute_conflicts=attr_conflicts,
                unclassified_reason=reason, suggested_candidates=candidates)


def load_rows(args):
    if args.input:
        content = args.input.read_text(encoding='utf-8')
    else:
        command = ['mysql', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4',
                   '--host=' + args.host, '--user=' + args.user, args.database, '-e', READ_SQL]
        proc = subprocess.run(command, text=True, encoding='utf-8', capture_output=True, check=True)
        if proc.stderr:
            print(proc.stderr, file=sys.stderr, end='')
        content = proc.stdout
    rows = [json.loads(line) for line in content.splitlines() if line.strip()]
    if any(not isinstance(row, list) or len(row) != len(FIELDS) or
           any(v is not None and not isinstance(v, str) for v in row) for row in rows):
        raise ValueError('输入必须为指定五列的 JSON 数组，字段为字符串/null')
    codes = [row[0] for row in rows]
    if len(set(codes)) != len(codes):
        raise ValueError('物料编码重复，停止试算')
    return sorted(rows, key=lambda r: r[0] or '')


def excel_export(path, results):
    if path.resolve().is_relative_to(REPO):
        raise ValueError('业务 Excel 不得放进仓库')
    from openpyxl import Workbook
    from openpyxl.styles import Font
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = '未归类清单'
    sheet.append(['编码', '当前名称', '原名称', '原规格', '规格', '尺寸', '建议分类候选'])
    for result in results:
        if result['target_category']:
            continue
        values = [result[k] or '' for k in ('material_code', 'current_name', 'original_name',
                                          'original_specification', 'specification', 'size')]
        values.append('；'.join(result['suggested_candidates']))
        sheet.append(values)
        # 明确作为文本，保留编码/前导零，避免原始文本被解释为 Excel 公式。
        for cell in sheet[sheet.max_row]:
            cell.data_type = 's'
    sheet.freeze_panes = 'A2'
    sheet.auto_filter.ref = sheet.dimensions
    for cell in sheet[1]:
        cell.font = Font(bold=True)
    for column, width in zip('ABCDEFG', [18, 48, 48, 38, 30, 24, 75]):
        sheet.column_dimensions[column].width = width
    workbook.save(path)


def summary(results):
    classified = sum(bool(r['target_category']) for r in results)
    return dict(total=len(results), classified=classified, unclassified=len(results) - classified,
                categories=dict(Counter(r['target_category'] or '未归类' for r in results)),
                confidence=dict(Counter(r['confidence'] for r in results)),
                original_missing=sum(r['original_missing'] for r in results),
                category_conflicts=sum(r['category_conflict'] for r in results),
                attribute_conflicts=sum(bool(r['attribute_conflicts']) for r in results),
                hit_rate=round(classified / len(results) * 100, 2) if results else 0)


def report(path, rows, results, excel_path):
    stats = summary(results)
    digest = hashlib.sha256(json.dumps(rows, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
    def safe(value):
        return str('' if value is None else value).replace('|', '\\|').replace('\n', ' ').replace('\r', ' ')
    def table(headers, records):
        return ['| ' + ' | '.join(headers) + ' |', '| ' + ' | '.join(['---'] * len(headers)) + ' |'] + [
            '| ' + ' | '.join(safe(v) for v in record) + ' |' for record in records]
    lines = ['# 物料分类与属性只读试算（阶段一）', '', '任务码：dev-20261024-008「重算分类与属性回填」',
             f'生成时间：{datetime.now().astimezone().isoformat(timespec="seconds")}',
             '状态：阶段一输出，任务保持进行中；待 007 后、用户确认试算结果再应用。', '',
             '## 背景与依据', '',
             '目标模型见 [定稿](material-master-target-model-dev-20261024-003.md) §1/§3/§9。当前定稿 §8 是导入，未含卡面所说优先级；本次规则顺序直接采用用户本轮明确指令。',
             '仅 SELECT inventory_material 的 material_code/material_name/specification/size/remark，全量读取，不按旧状态过滤；不读旧分类、旧 material_grade 或 material_type，不依赖 007 新列。',
             '唯一事实取 remark 的【原名称】【原规格】；原名称缺失才回退当前名称，标 low。任一原始标记缺失亦标低置信；原规格空串保留，缺失不回退当前规格/尺寸。当前规格/尺寸仅供 Excel 对照。',
             'R13 仅使用原始文本显式“类型:F/S”或“成品/半成品”声明，F→FINISHED、S→SEMI；不读旧 material_type，不用冻结编码反推分类，名称中的 F150/S200 不视为类型。没有明确声明时保持未归类。',
             'R03 在用户“胶粘类”关键词基础上含字面“胶带”；PMMA 含中文同义词“压克力/亚克力”。未命中绝不塞入 OTHER；零条分类保持展示。', '',
             '## 现状证据与分布', '',
             f'快照总数 {stats["total"]}；规则命中 {stats["classified"]}；未归类 {stats["unclassified"]}；命中率 {stats["hit_rate"]:.2f}%。',
             f'五列排序快照 SHA-256：`{digest}`。', '']
    counts = stats['categories']
    lines += table(['目标分类码', '名称', '条数'], [(c, label, counts.get(c, 0)) for c, label in CATEGORIES.items()] + [('未归类', '空目标分类码', stats['unclassified'])])
    lines += ['', '## 规则命中率与来源', '']
    rules = Counter(r['rule'] for r in results)
    lines += table(['规则', '目标', '关键词/判据', '首命中数', '占总数'],
                   [(n, c, p, rules[n], f'{rules[n] / len(results) * 100:.2f}%' if results else '0%') for n, c, p in RULES] +
                   [('R13', 'FINISHED/SEMI', '原始文本显式类型 F/S 或成品/半成品', rules['R13'], f'{rules["R13"] / len(results) * 100:.2f}%' if results else '0%'),
                    ('R14', '未归类', '所有规则未命中', rules['R14'], f'{stats["unclassified"] / len(results) * 100:.2f}%' if results else '0%')])
    lines += ['', '优先级跨原名称/原规格整体执行；多分类命中保留全部证据，选择编号最小规则并标 medium，保护膜始终最高。high 仅表示明确名称关键词命中，不等于人工确认。', '']
    lines += table(['confidence', '条数'], [(c, stats['confidence'].get(c, 0)) for c in ('high', 'medium', 'low', '未归类')])
    source_counts = Counter('+'.join(sorted({e['source'] for e in r['evidence']})) or '无' for r in results)
    lines += ['', *table(['分类依据来源', '条数'], sorted(source_counts.items())), '', '## 未归类与人工确认规模', '']
    reasons = Counter(r['unclassified_reason'] for r in results if not r['target_category'])
    lines += table(['未归类原因', '条数'], sorted(reasons.items()))
    review = [r for r in results if r['confidence'] != 'high' or r['attribute_conflicts']]
    lines += ['', f'分类待人工确认全部 {len(results)} 条（自动命中不等于人工确认）；其中优先复核并集 {len(review)} 条。',
              f'优先复核口径：未归类 {stats["unclassified"]}；medium {stats["confidence"].get("medium", 0)}；low {stats["confidence"].get("low", 0)}；属性冲突 {stats["attribute_conflicts"]}（分组可重叠）。',
              f'原始标记/原名称缺失 {stats["original_missing"]}；多分类规则命中 {stats["category_conflicts"]}。候选只为人工分流，不作为分类结果。',
              f'未归类 Excel：`{excel_path or "未指定（未导出）"}`，仅业务留档，不入 git。', '', '## 属性试算口径与覆盖', '',
              '属性仅从同一原始事实/标记后的当前名称提取，并逐字段输出 attribute_evidence；当前 specification/size 不参与推断。',
              '厚度：显式“厚/厚度/克重”或明确膜片类名称开头的单位串/小数，保留原单位，不把 PET125 的 125 擅自转厚度；原规格纯数字按宽、二维数值按长×宽保留，缺单位不补单位。',
              '材质取明确材质词，3M9469PC 等型号后缀不视为 PC 材质；颜色取明确颜色词；型号取“型号:”或名称中字母开头且含数字的串，保留明确 3M 产品型号，其他数字开头串歧义大留空；品牌仅取明确 3M/tesa 或“品牌:”，括号供应商不猜品牌。多候选不同值留空并列 attribute_conflicts；未抽出留空。', '']
    lines += table(['属性', '非空', '空值', '冲突留空'], [(key, sum(bool(r['attributes'][key]) for r in results),
                    sum(not r['attributes'][key] for r in results), sum(key in r['attribute_conflicts'] for r in results))
                    for key in ('thickness', 'width_size', 'material', 'color', 'model', 'brand')])
    lines += ['', '## 每类样例', '', '按物料编码排序等距抽取至多 10 条，末尾亦覆盖；不足 10 条全部列出，零条类明确无样例。原名称缺失以“（缺失）”显示，逐行输出保留实际依据来源。', '']
    for category, label in [*CATEGORIES.items(), ('', '未归类')]:
        subset = [r for r in results if r['target_category'] == category]
        n = min(10, len(subset))
        samples = [subset[round(i * (len(subset) - 1) / (n - 1))] for i in range(n)] if n > 1 else subset[:n]
        lines += [f'### {category or "未归类"}（{label}，{len(subset)} 条）', '']
        lines += table(['编码', '原名称', '目标分类', '命中规则'], [(r['material_code'], r['original_name'] or '（缺失）', r['target_category'] or '未归类', r['rule']) for r in samples]) if samples else ['无样例。']
        lines.append('')
    lines += ['## 验证与待确认点', '',
              '复现（工作目录 /home/administrator/jjx；凭据通过 MYSQL_PWD 环境变量传入，不固化到脚本）：', '',
              '```bash', 'python3 scripts/material-reclassify-dryrun.py --self-test',
              'python3 scripts/material-reclassify-dryrun.py --user jjx_ro --host 127.0.0.1 \\',
              '  --report jjx-docs/history/material-reclassify-dryrun-dev-20261024-008.md \\',
              '  --excel /home/administrator/物料未归类清单_20261024.xlsx > /tmp/material-reclassify-dryrun.jsonl',
              'cd jjx-web', 'npm run check:docs', '```', '',
              '真实库执行、反例、Excel 回读、文档门禁结果由本次执行记录补充。尚未应用回填，不声称真实库回填事务/模板字段验收通过。',
              '零条类别和未归类仅据原始文本规则统计，不能据此断言业务没有对应物料；待人工确认补充规则，不自动扩展分类。',
              '待 007 后再核对模板、字典取值及必填属性，再经用户确认进入应用。阶段一不生成迁移，不写业务表，不运行服务生命周期操作。', '']
    path.write_text('\n'.join(lines), encoding='utf-8-sig')


def self_test():
    def trial(name, spec='', code='RM000001', current='不应参与分类', remark=None):
        return classify([code, current, 'PC保护膜', '999',
                         f'【原名称】{name}【原规格】{spec}' if remark is None else remark])
    checks = 0
    for name, target in [('PET保护膜', 'RAW_AUX_PROTECT'), ('PC保护膜+双面胶', 'RAW_AUX_PROTECT'),
                         ('离型PET', 'RAW_AUX_RELEASE'), ('3M9473PC', 'RAW_AUX_TAPE'),
                         ('tesa胶带', 'RAW_AUX_TAPE'), ('EVA泡棉', 'RAW_AUX_FOAM'),
                         ('铜箔', 'RAW_AUX_FOIL'), ('扩散PET', 'RAW_AUX_OPTICAL'),
                         ('12pin Housing', 'RAW_AUX_ELECTRONIC'), ('LED', 'RAW_AUX_ELECTRONIC'),
                         ('油墨', 'RAW_INK'), ('0.175中砂面PC', 'RAW_BASE_PC'),
                         ('PET125', 'RAW_BASE_PET'), ('0.3PVC', 'RAW_BASE_PVC'),
                         ('压克力', 'RAW_BASE_PMMA'), ('F150(AGK)', ''), ('S200', ''),
                         ('TLT125#', ''), ('APPLE', ''), ('CUSTOMPCMODEL', '')]:
        assert trial(name)['target_category'] == target, name
        checks += 1
    assert trial('0.175中砂面PC')['attributes']['thickness'] == '0.175'
    assert trial('PET125')['attributes']['thickness'] == ''
    assert trial('PC', '保护膜')['target_category'] == 'RAW_AUX_PROTECT'
    assert trial('PC', '保护膜')['evidence'][0]['source'] == '原规格'
    assert trial('陌生型号')['target_category'] == ''  # 当前名称/规格不能污染原始事实
    assert trial('类型:F')['target_category'] == 'FINISHED'
    assert trial('类型:S')['target_category'] == 'SEMI'
    assert trial('', current='PET', remark='无标记')['confidence'] == 'low'
    assert trial('PC', remark='【原名称】PC')['confidence'] == 'low'
    assert trial('PC', remark='【原名称】PC【原规格】')['confidence'] == 'high'
    assert trial('PC', '10*20')['attributes']['width_size'] == '10×20'
    assert trial('PET', 'PIN')['confidence'] == 'medium'
    assert trial('PC透明黑色')['attributes']['color'] == ''
    assert trial('PC（供应商）')['attributes']['brand'] == ''
    assert trial('PC', remark='【原名称】PC【原规格】30【备注】保护膜')['target_category'] == 'RAW_BASE_PC'
    assert trial('陌生型号', code='F0001')['target_category'] == ''
    assert trial('陌生型号', code='S0001')['target_category'] == ''
    assert trial('0.175PC')['attributes']['model'] == ''
    assert trial('3M9469PC')['attributes']['material'] == ''
    assert trial('3M9469PC')['attributes']['model'] == '3M9469PC'
    assert trial('T4901PET保护膜')['attributes']['material'] == 'PET'
    assert trial('3.5mm38度黑色EVA')['attributes']['thickness'] == '3.5mm'
    assert trial('0.3mm 透明PC')['target_category'] == 'RAW_BASE_PC'
    assert trial('0.3mm 透明PC')['attributes']['brand'] == ''
    assert trial('3MRP45')['target_category'] == 'RAW_AUX_TAPE'
    assert trial('0.5mmYG透明压克力板')['attributes']['thickness'] == '0.5mm'
    assert all('UPDATE' not in sql.upper() and 'ALTER' not in sql.upper()
               and 'INSERT' not in sql.upper() and 'DELETE' not in sql.upper() for sql in [READ_SQL])
    print(f'自检通过：{checks + 27} 个断言', file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, help='五列 JSON 数组逐行快照；不访问数据库')
    parser.add_argument('--host', default=os.environ.get('JJX_DB_HOST', '127.0.0.1'))
    parser.add_argument('--user', default=os.environ.get('JJX_DB_USER', 'jjx_ro'))
    parser.add_argument('--database', default=os.environ.get('JJX_DB_NAME', 'jjx_erp_db'))
    parser.add_argument('--report', type=Path, help='UTF-8 BOM Markdown 报表路径')
    parser.add_argument('--excel', type=Path, help='仓库外未归类 Excel 路径（需 openpyxl）')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    rows = load_rows(args)
    results = [classify(row) for row in rows]
    if args.excel:
        excel_export(args.excel, results)
    if args.report:
        report(args.report, rows, results, args.excel)
    for result in results:
        print(json.dumps(result, ensure_ascii=False))
    print(json.dumps(summary(results), ensure_ascii=False), file=sys.stderr)


if __name__ == '__main__':
    main()
