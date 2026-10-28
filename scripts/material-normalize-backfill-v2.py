#!/usr/bin/env python3
"""物料主数据回填 v2（dev-20261023-012 后）：填尺寸数值、按属性重写名称、修正品牌/材料。

规则（用户 2026-10-23 定稿）：
- 名称 = 材料 + 型号 + 颜色（品牌），无空格、缺项跳过、品牌全角括号；
- 材料为复合词（PE保护膜/PC膜/EVA泡棉/胶带/灯/离型膜/标签纸…），识别不到留空，不写"其他"；
- 品牌只取括号内容（排除数字/状态词/封装词）；
- 型号保留规格档（0.125、15C、50#、DB6842、PET125、3M5604…）；
- 数值尺寸 = 从 remark 里【原规格】+ 名称尺寸解析，宽在前、缺省 mm；
- specification 只保留工艺描述（纯尺寸文本清空；原值已在 remark）。

用法：python3 scripts/material-normalize-backfill-v2.py [--apply]
"""
import argparse, csv, os, re, subprocess, sys, time
import importlib.util
from pathlib import Path

_size_spec = importlib.util.spec_from_file_location("material_sizes", Path(__file__).with_name("material-reclassify-dryrun.py"))
_size_values = importlib.util.module_from_spec(_size_spec)
sys.dont_write_bytecode = True
_size_spec.loader.exec_module(_size_values)

DB = os.environ.get('JJX_DB_NAME', 'jjx_erp_db')
RO = ['mysql', '-h127.0.0.1', '-ujjx_ro', '-N', '-B', DB]
RO_ENV = {**os.environ, 'MYSQL_PWD': os.environ.get('JJX_RO_PASS', 'jjx_ro_2026')}
RW_ENV = {**os.environ, 'MYSQL_PWD': os.environ.get('DB_PASS', '123456')}

MATERIALS = ['PE保护膜','PET保护膜','PC膜','PET膜','PE膜','PVC膜','离型膜','离型纸','VHB胶带','胶带','EVA泡棉','泡棉','标签纸','箱','袋','灯','螺丝','垫片','不锈钢','铜','铝','油墨']
ALIASES = {'离型膜':'离型膜','离型纸':'离型纸'}
COLORS = ['透明','白色','黑色','黄色','红色','蓝色','绿色','灰色','银色','金色','紫色','橙色','黄绿','黄','红']
BAD_BRAND = ('副本','暂放','网购','国外','样品','测试','临时','新','旧','客供','封装')

def q(sql, write=False):
    if write:
        p = subprocess.run(['mysql','-uroot','-p'+RW_ENV['MYSQL_PWD'],'-N','-B',DB,'-e',sql], text=True, capture_output=True, env=RW_ENV)
    else:
        p = subprocess.run(RO+['-e',sql], text=True, capture_output=True, env=RO_ENV)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    return [l.split('\t') for l in p.stdout.strip().splitlines() if l]

def orig_from_remark(remark):
    m = re.search(r'【原名称】(.+?)【原规格】(.*)$', remark or '')
    return (m.group(1), m.group(2)) if m else (None, None)

def pick_brand(name):
    # 名称里内嵌的胶系品牌（如 EVA泡棉+3M），括号外也算品牌
    if re.search(r'\+\s*3M\b', name, re.I) or re.search(r'3M$', name):
        return '3M'
    m = re.search(r'[（(]\s*([^（()）]{1,20})\s*[)）]', name)
    if not m: return ''
    b = m.group(1).strip()
    if not b or any(x in b for x in BAD_BRAND): return ''
    if re.fullmatch(r'[\d\s\*\×xX.\-]+', b): return ''      # 纯数字/尺寸
    if re.search(r'(灯|电阻|电容|连接器|封装)', b): return ''
    if re.search(r'\d', b): return ''          # 含数字的多为型号/封装，不当品牌
    return b

def pick_material(name):
    """材料+形态 复合词：先判形态，再取基材。"""
    up = name.upper()
    base = ''
    for b in ('PET', 'PE', 'PVC', 'PC'):
        if b in up:
            base = b
            break
    if 'EVA' in up:
        base = 'EVA'
    if '保护膜' in name:
        return (base + '保护膜') if base in ('PE', 'PET') else 'PE保护膜'
    if '离型纸' in name or '离型原纸' in name:
        return '离型纸'
    if '离型' in name:
        return '离型膜'
    if '泡棉' in name:
        return 'EVA泡棉' if base == 'EVA' else '泡棉'
    if 'VHB' in up:
        return 'VHB胶带'
    if '胶带' in name or '双面胶' in name:
        return '胶带'
    if '标签纸' in name:
        return '标签纸'
    if '灯' in name or 'LED' in up:
        return '灯'
    if '螺丝' in name or '螺钉' in name:
        return '螺丝'
    if '垫片' in name:
        return '垫片'
    if base:
        return base + '膜'
    return ''

def pick_color(name):
    for c in COLORS:
        if c in name: return c
    return ''

def pick_model(name, brand, material):
    t = name
    if brand: t = t.replace(brand, ' ')
    t = re.sub(r'[（(][^（()）]*[)）]', ' ', t)
    for c in COLORS: t = t.replace(c, ' ')
    if material: t = t.replace(material, ' ')
    for k in ('PC','PET','PE','PVC','EVA'):
        t = re.sub(r'(?i)'+k, ' ', t)
    t = re.sub(r'\+', ' ', t)
    toks = re.findall(r'[A-Za-z0-9][A-Za-z0-9#\-_.]*', t)
    cand = []
    for x in toks:
        if x.upper() in ('MM','CM','M','C') : continue
        if re.fullmatch(r'\d+(\.\d+)?(mm|MM)?', x) and not re.fullmatch(r'\d+\.\d+', x): 
            continue  # 纯整数（多为尺寸）不当型号；小数规格档保留
        cand.append(x)
    if cand:
        return cand[0]
    # 兜底：去掉材料/颜色/品牌后的残留字母数字串（如 PET125 -> 125）
    rest = re.sub(r'[^A-Za-z0-9#\-_.]', '', t)
    return rest if rest else ''

def parse_size(orig_spec, name):
    def norm(v):
        v = v.strip().replace('×','*').replace('x','*').replace('X','*')
        return v
    spec = norm(orig_spec or '')
    parts = [p for p in spec.split('*') if p.strip()]
    if len(parts) >= 2:
        a, b = parts[0].strip(), parts[1].strip()
        return f'{a}×{b}' if not is_mm_missing(a,b) else f'{a}×{b}'
    m = re.search(r'(\d+(?:\.\d+)?)\s*mm', name)
    thick = m.group(1) if m else ''
    width = parts[0].strip() if parts else ''
    if width and thick: return f'{width}×{thick}'
    return width

def is_mm_missing(a, b): return False

def build_name(material, model, color, brand):
    core = ''.join([x for x in [material, model, color] if x])
    return core + (f'（{brand}）' if brand else '')

def spec_is_dimension_only(spec):
    return bool(re.fullmatch(r'[\d\s\*\×xX.\-]*', spec or ''))

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    rows = q("SELECT material_id, material_code, material_name, IFNULL(specification,''), IFNULL(remark,'') "
             "FROM inventory_material WHERE status=1 ORDER BY material_code;")
    out = f"/tmp/material-v2-preview-{time.strftime('%Y%m%d-%H%M')}.csv"
    upd, stats = [], {'total':0,'size':0,'name_changed':0}
    with open(out,'w',newline='',encoding='utf-8-sig') as fh:
        w = csv.writer(fh); w.writerow(['编码','当前名称','原名称','当前规格','原规格','材料','型号','颜色','品牌','新名称','新size','新spec'])
        for mid, code, cname, cspec, remark in rows:
            stats['total'] += 1
            oname, ospec = orig_from_remark(remark)
            src_name = oname or cname
            src_spec = ospec if ospec is not None else cspec
            brand = pick_brand(src_name)
            material = pick_material(src_name)
            color = pick_color(src_name)
            model = pick_model(src_name, brand, material)
            # 括号内不是品牌的内容（多为型号/客户代号）并入型号，避免信息丢失
            if not brand:
                pm = re.search(r'[（(]\s*([^（()）]{1,30})\s*[)）]', src_name)
                if pm and not any(x in pm.group(1) for x in BAD_BRAND):
                    extra = pm.group(1).strip()
                    if extra and extra != model:
                        model = (model + ' ' + extra).strip() if model else extra
            newname = build_name(material, model, color, brand) or cname
            size = parse_size(src_spec, src_name)
            newspec = '' if spec_is_dimension_only(cspec) else cspec
            if size: stats['size'] += 1
            if newname != cname: stats['name_changed'] += 1
            w.writerow([code,cname,src_name or '',cspec,src_spec,material,model,color,brand,newname,size,newspec])
            upd.append((mid,newname,size,newspec,material,model,color,brand))
    print(f'预览：{out}')
    print(f"共 {stats['total']}，可解析 size {stats['size']}，名称会变 {stats['name_changed']}")
    if not args.apply:
        print('干跑，未写库'); return
    n=0
    for mid,newname,size,newspec,material,model,color,brand in upd:
        sets=[]
        if size:
            sets.extend(k + '=' + ('NULL' if v is None else "'mm'" if k == 'size_unit' else str(v))
                        for k, v in _size_values.dimensions(size).items())
        if newname: sets.append("material_name='%s'" % newname.replace("'","''"))
        sets.append("material_grade=%s" % ("NULL" if not material else "'"+material.replace("'","''")+"'"))
        sets.append("model=%s" % ("NULL" if not model else "'"+model.replace("'","''")+"'"))
        sets.append("color=%s" % ("NULL" if not color else "'"+color.replace("'","''")+"'"))
        sets.append("brand=%s" % ("NULL" if not brand else "'"+brand.replace("'","''")+"'"))
        sets.append("specification=%s" % ("NULL" if newspec=='' else "'"+newspec.replace("'","''")+"'"))
        try:
            q(f"UPDATE inventory_material SET {', '.join(sets)} WHERE material_id={mid};", write=True); n+=1
        except Exception as e:
            print('skip', mid, e)
    print('已更新', n)

if __name__=='__main__':
    sys.exit(main())
