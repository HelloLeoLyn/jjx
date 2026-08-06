import pandas as pd
import re

# 读取元数据文件（跳过第一行日期，第二行为表头）
df = pd.read_excel('meta-material.xlsx', header=1, dtype=str)

# 定义列名（根据您的元数据实际列位置，可能需要调整）
# 假设关键列位置：A=0, B=1, C=2, ... 但pandas读入后列名为第2行的内容，需查看实际
# 建议先打印df.columns查看，然后映射。以下为示例映射，您可能需要调整。
# 若列名不标准，可用索引，例如：
# df = pd.read_excel('meta-material.xlsx', header=None, skiprows=1)
# 然后 df.columns = ['材料','规格','上月结存', ...] 但这里较复杂，建议先预览。

# 为简化，我们直接用列索引（假设第0列是材料，第1列是规格，第？列是本月结存）
# 根据您提供的元数据，BP列（即第？列）是本月结存，需要计算索引。
# 由于列数很多，我们先用列名方式，但元数据列名是中文，且可能有重复，更稳妥是用位置。

# 我提供一个更稳健的方法：读取全部数据，然后按位置取列。
df_all = pd.read_excel('meta-material.xlsx', header=None, skiprows=1)  # 跳过第一行日期
# 第二行是表头，我们取该行作为列名
header = df_all.iloc[0].tolist()
data = df_all.iloc[1:].copy()
data.columns = header

# 关键列查找（根据列名）
col_material = '材料名称'  # 实际可能是 '材料名称' 或 '材料'
col_spec = '规格'          # 实际是 '规格'
col_remarks = '备注 / 说明'
col_quantity = '本月结存'  # 查找到的列名

# 如果未找到，用位置索引（假设材料在A列，规格在B列，备注在BJ列，结存在BP列）
# 可以用 data.iloc[:, 0] 等，但为通用，建议先打印列名查看。

# 示例处理函数
def extract_supplier(name):
    match = re.search(r'\(([^)]+)\)', str(name))
    return match.group(1) if match else ''

def extract_model(remarks):
    # 匹配常见机种编号
    if pd.isna(remarks):
        return ''
    text = str(remarks)
    patterns = [r'JTT-\d+', r'JST-\d+', r'GF-\d+', r'JJX-\d+', r'YL-\d+', r'HZLT-\d+']
    for p in patterns:
        m = re.search(p, text)
        if m:
            return m.group()
    return ''

def extract_material_type(name):
    name = str(name)
    if 'PC' in name.upper():
        return 'PC'
    elif 'PET' in name.upper():
        return 'PET'
    elif '压克力' in name:
        return '压克力'
    elif '保护膜' in name:
        return '保护膜'
    elif '离型纸' in name:
        return '离型纸'
    else:
        return '其他'

# 构建新数据
new_data = []
# 过滤掉非数据行（如标题行）
for idx, row in data.iterrows():
    material = row[col_material] if col_material in row else row.iloc[0]
    spec = row[col_spec] if col_spec in row else row.iloc[1]
    # 跳过空材料或标题行（如“下面是”）
    if pd.isna(material) or str(material).startswith('下面是'):
        continue
    # 数量取本月结存
    quantity = row[col_quantity] if col_quantity in row else row.iloc[-?]  # 需确定位置
    # 备注
    remarks = row[col_remarks] if col_remarks in row else ''
    supplier = extract_supplier(material)
    model = extract_model(remarks) or extract_model(material)
    material_type = extract_material_type(material)
    unit = 'PCS' if '*' in str(spec) else '米'  # 根据规格含*判断
    # 项目与机种相同（也可单独提取）
    project = model
    new_data.append({
        '材料(*)': material,
        '规格': spec,
        '供应商': supplier,
        '备注': remarks,
        '机种': model,
        '数量': quantity,
        '单位': unit,
        '项目': project,
        '材料类型': material_type
    })

# 输出为DataFrame并保存
df_out = pd.DataFrame(new_data)
df_out.to_excel('物料导入模板_生成.xlsx', index=False)
