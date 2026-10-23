#!/usr/bin/env python3
"""dev-20261023-006：先 dry-run 审阅 CSV，再 --apply；属性只补空，名称规格留痕重写。

保留原备注；原值标签仅追加一次。整批事务及行快照比较防止覆盖并发编辑。
无法完整保留原名称/备注时拒绝应用；不猜测没有依据的材料、品牌、维度。
"""
import argparse
import csv
import json
import os
import re
import subprocess
import time

MATERIALS = ('PE保护膜','PET保护膜','PC膜','PET膜','PE膜','PVC膜','离型膜','离型纸',
             '胶带','VHB胶带','泡棉','EVA泡棉','标签纸','箱','袋','灯','螺丝','垫片',
             '不锈钢','铜','铝','油墨','其他')
COLORS = ('透明','咖啡色','白色','黑色','黄色','红色','蓝色','绿色','灰色','银色','金色','紫色','橙色')
FIELDS = ('material_id','material_code','material_name','specification','material_type',
          'category_id','material_grade','model','color','brand','search_keywords','remark')


def mysql(sql, write=False):
    env = dict(os.environ, MYSQL_PWD=os.environ.get('DB_PASS','123456') if write
               else os.environ.get('JJX_RO_PASS','jjx_ro_2026'))
    p = subprocess.run(['mysql','-h127.0.0.1','-u'+('root' if write else 'jjx_ro'),
                        '--default-character-set=utf8mb4','-N','-B','--raw',
                        os.environ.get('JJX_DB_NAME','jjx_erp_db')],
                       input=sql, text=True, capture_output=True, env=env)
    if p.returncode:
        raise RuntimeError(p.stderr.strip())
    return p.stdout.splitlines()


def literal(value):
    if value is None:
        return 'NULL'
    return "(CONVERT(X'%s' USING utf8mb4) COLLATE utf8mb4_unicode_ci)" % str(value).encode().hex()


def compact(text):
    return re.sub(r'\s+', '', text or '')


def attributes(name, spec):
    upper = name.upper()
    if '保护膜' in name:
        grade = 'PET保护膜' if 'PET' in upper else 'PE保护膜' if 'PE' in upper else '其他'
    elif '离型膜' in name or '离型纸' in name:
        grade = '离型膜' if '离型膜' in name else '离型纸'
    elif 'EVA' in upper or '泡棉' in name:
        grade = 'EVA泡棉' if 'EVA' in upper else '泡棉'
    elif 'VHB' in upper:
        grade = 'VHB胶带'
    elif re.search(r'胶带|双面胶|^3M', upper):
        grade = '胶带'
    else:
        grade = next((v for v in MATERIALS if v in name), '')
        if not grade:
            grade = next((m+'膜' for m in ('PET','PVC','PC','PE')
                          if re.search(r'(?<![A-Z])'+m+r'(?![A-Z])',upper)), '')
        if not grade and re.search(r'灯|燈|LED',name+' '+spec,re.I):
            grade = '灯'
        grade = grade or ('螺丝' if '螺钉' in name else '其他')
    color = next((c for c in COLORS if c in name), '')
    if not color and grade == '灯':
        translated = spec.translate(str.maketrans('紅綠藍黃燈','红绿蓝黄灯'))
        color = next((c+'色' for c in ('白','红','绿','蓝','黄','橙') if c in translated),'')
    # 品牌与供应商分开：历史括号常为供应商，不能直接当品牌。
    brand = '3M' if re.search(r'^3M',upper) else ''
    model_text = re.sub(r'[（(][^（）()]*[）)]',' ',name)
    model_text = re.sub(r'^3M','',model_text,flags=re.I)
    model_text = re.sub(r'^\s*\d+\.\d+(?:mm)?(?=\s|[^0-9.\-])', ' ', model_text, flags=re.I)
    for token in (*MATERIALS,*COLORS,'VHB'):
        model_text = model_text.replace(token,' ')
    model_text = re.sub(r'(?<![A-Za-z])(?:PET|PVC|PC|PE|EVA)(?![A-Za-z0-9])',' ',model_text)
    model_text = re.sub(r'(?<![\w.])\d+(?:\.\d+)?\s*(?:mm|cm|m|T)\b',' ',model_text,flags=re.I)
    tokens = re.findall(r'[A-Za-z0-9][A-Za-z0-9#/_\-.]*',model_text)
    tokens = [t.strip('-') for t in tokens if re.search(r'\d',t) and not re.fullmatch(r'\d+\.\d+',t)]
    model = tokens[0] if tokens else ''
    return dict(material_grade=grade,model=model,color=color,brand=brand)


def category(name, grade, mtype):
    # 大类优先，避免给原材绑定辅助类；不改既有 category_id。
    if mtype == 'R':
        if grade == '油墨': return 'RM-INK'
        if grade in ('离型纸','离型膜','标签纸'): return 'RM-PAPER'
        if '保护膜' in grade or '胶' in grade or '胶' in name: return 'RM-ADHESIVE'
        if '膜' in grade or '泡棉' in grade: return 'RM-FILM'
        return 'OTHER-R'
    if '胶' in grade or '胶' in name or name.upper().startswith('3M'): return 'AUX-ADHESIVE'
    if re.search(r'灯|燈|LED|电阻|电容|二极管|连接器|IC',name,re.I) or grade == '灯': return 'AUX-ELEC'
    if grade in ('螺丝','垫片','铜','铝','不锈钢') or re.search(r'弹片|铆钉|弹簧',name): return 'AUX-HARDWARE'
    if grade in ('箱','袋','泡棉','EVA泡棉','标签纸') or '膜' in grade: return 'AUX-PACK'
    if re.search(r'刀模|治具|砂纸|手套|清洁',name): return 'AUX-TOOL'
    return 'OTHER-A'


def normalize_spec(spec, name='', grade=''):
    # 只将明确的数值维度解释成尺寸，电容型号/文字不作为维度。
    num = r'\d+(?:\.\d+)?'
    dim = num+r'\s*(?:mm|cm|m)?'
    s = (spec or '').strip()
    if not re.fullmatch(dim+r'(?:\s*[*xX×]\s*'+dim+r')*',s,re.I):
        return spec
    parts = re.split(r'\s*[*xX×]\s*',s)
    parts = [re.sub(r'mm$', '',compact(p).lower()) for p in parts]
    # 历史规格第一维为宽；名称中明确的薄膜/泡棉厚度追加在宽之后。
    thickness = re.match(r'\s*('+num+r')\s*(mm|cm|m)?(?=\s|[^\d.]|$)',name,re.I)
    if len(parts) == 1 and thickness and ('膜' in grade or '泡棉' in grade):
        value, unit = thickness.groups()
        if unit or float(value) <= 10:
            parts.append(value+('' if not unit or unit.lower()=='mm' else unit.lower()))
    return '×'.join(parts)


def trace(remark, name, spec):
    if '【原名称】' in remark and '【原规格】' in remark:
        return remark
    prefix = remark+'【原名称】'+name+'【原规格】'
    if len(prefix) > 1000:
        raise ValueError('原备注和原名称超过留痕容量，拒绝截断它们')
    available = 1000-len(prefix)
    if len(spec) > available:
        if available < len('(截断)'): raise ValueError('无法保留截断标志')
        spec = spec[:available-len('(截断)')]+'(截断)'
    return prefix+spec


def plan(row, cats):
    new = dict(row)
    oldname, oldspec = row['material_name'], row['specification'] or ''
    # 第二次运行仍使用第一次留存的原值推断，避免新名称再次被拆解。
    match = re.search(r'【原名称】(.*?)【原规格】(.*)$',row['remark'] or '', re.S)
    source_name, source_spec = match.groups() if match else (oldname,oldspec)
    for key,value in attributes(source_name,source_spec).items():
        if not row[key]: new[key] = value or row[key]
    if not row['category_id']:
        new['category_id'] = cats[category(source_name,new['material_grade'],row['material_type'])]
    new['material_name'] = ''.join(compact(new[k]) for k in ('material_grade','model','color'))
    if new['brand']: new['material_name'] += '（'+compact(new['brand'])+'）'
    if not new['material_name']: raise ValueError('空名称')
    new['specification'] = normalize_spec(source_spec,source_name,new['material_grade']) or row['specification']
    if new['material_name'] != oldname or new['specification'] != row['specification']:
        new['remark'] = trace(row['remark'] or '',oldname,oldspec)
        if not row['search_keywords']:
            new['search_keywords'] = oldname[:500]
    for key,maxlen in [('material_name',200),('specification',500),('material_grade',30),('model',100),('color',20),('brand',50),('remark',1000)]:
        if len(new[key] or '') > maxlen: raise ValueError(key+' 超长')
    return new


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply',action='store_true')
    ap.add_argument('--limit',type=int,default=0)
    args = ap.parse_args()
    if args.limit < 0: ap.error('--limit 必须非负')
    cats = dict(json.loads(s) for s in mysql('SELECT JSON_ARRAY(category_code,category_id) FROM inventory_material_category;'))
    query = 'SELECT JSON_OBJECT('+','.join("'%s',%s"%(k,k) for k in FIELDS)+') FROM inventory_material ORDER BY material_code'
    if args.limit: query += ' LIMIT '+str(args.limit)
    rows = [json.loads(s) for s in mysql(query+';')]
    preview = '/tmp/material-normalize-preview-'+time.strftime('%Y%m%d-%H%M%S')+'.csv'
    changes = []
    with open(preview,'w',newline='',encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f,fieldnames=['material_id','material_code','原名称','新名称','原规格','新规格','材料','型号','颜色','品牌','原备注','新备注','changed'])
        writer.writeheader()
        for row in rows:
            new = plan(row,cats)
            changed = {k:v for k,v in new.items() if v != row[k]}
            writer.writerow(dict(material_id=row['material_id'],material_code=row['material_code'],原名称=row['material_name'],新名称=new['material_name'],原规格=row['specification'],新规格=new['specification'],材料=new['material_grade'],型号=new['model'],颜色=new['color'],品牌=new['brand'],原备注=row['remark'],新备注=new['remark'],changed=bool(changed)))
            if changed: changes.append((row,changed))
    print(f'预览清单：{preview}\n物料 {len(rows)} 条，待变更 {len(changes)} 条')
    if not args.apply:
        print('干跑，未写库。审阅 CSV 后使用 --apply。')
        return
    # SQL 错误时 mysql 退出，连接断开回滚整批；临时表约束使快照冲突也报错。
    sql = ['START TRANSACTION;', 'CREATE TEMPORARY TABLE material_normalize_guard (ok INT NOT NULL);']
    for row,changed in changes:
        predicate = ' AND '.join(k+' <=> '+literal(row[k]) for k in FIELDS)
        sql.append('SELECT material_id FROM inventory_material WHERE material_id='+str(row['material_id'])+' FOR UPDATE;')
        sql.append('INSERT INTO material_normalize_guard SELECT IF(COUNT(*)=1,1,NULL) FROM inventory_material WHERE '+predicate+';')
        sql.append('UPDATE inventory_material SET '+','.join(k+'='+literal(v) for k,v in changed.items())+",update_by='codex' WHERE material_id="+str(row['material_id'])+';')
    sql.append('COMMIT;')
    mysql('\n'.join(sql),write=True)
    print(f'已事务提交 {len(changes)} 条。')


if __name__ == '__main__':
    main()
