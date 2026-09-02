# 打样印刷工序：色号/油墨录入体系化方案

- **日期**：2026-09-02
- **范围**：打样工作台印刷工序录入（PrintProcessPanel）+ 色号/油墨数据落地
- **关联任务**：DEV-1788002675406（1225，已实施历史联想方案但数据 0 行）+ 网版管理（jjx_screen_master）
- **状态**：方向已确认，待实施

---

## 一、背景与问题

打样工序四要素（印刷名称/色号/油墨编号/网框编号）现状：

| 要素 | 现状 | 问题 |
|---|---|---|
| 印刷名称 | autocomplete 历史联想（process/history 聚合） | 可接受，保留 |
| 色号 | autocomplete 历史联想 | 无主数据，纯手输+联想，首次输入无辅助；会有"123C/123C(亮)"脏数据 |
| 油墨编号 | autocomplete 历史联想 | 同上；且油墨是消耗性材料，应进物料体系（采购/库存/BOM） |
| 网框编号 | 主数据查询（jjx_screen_master） | ✅ 已解决，模式可复制 |

**根因**：`sales_sample_process` 全表 0 行 → 联想池空 → 等于纯手输。且色号/油墨本质是"有限集合、跨单复用"数据，用自由文本记录必然变脏。

## 二、决策（2026-09-02 Leo 确认）

1. **方向认可**：油墨=物料（inventory_material）+ 工序选物料；色号=字典
2. **油墨编号字段**：不保留自由文本 inkNo，改为关联油墨物料（material_id），但**允许手输兜底**（打样可能遇到未建档新油墨）
3. **建物料分类**：油墨/银浆分类（已建，category_code=INK id=1）
4. **油墨物料编码规则**：INK-xxx 前缀（Leo 定）
5. **立即建基础数据**（Leo 定）：物料分类 + 色号字典已建（2026-09-02）
6. **登记任务**（Leo 定）：PrintProcessPanel 改造跟进

## 二之实施记录（2026-09-02）
- ✅ inventory_material_category：INSERT 'INK/油墨印刷'（category_id=1，一级，parent=0）
- ✅ sys_dict：INSERT engineering_color/色号（dict_id=120，dict_group=engineering，空字典起步）
- ✅ 登记任务：dev-20260902-XXX 打样印刷工序色号/油墨体系化改造
- ⏳ 后续：油墨物料建档（INK-xxx 编码规则）、PrintProcessPanel 两列改造

## 三、数据结构设计

### 3.1 色号 → sys_dict 字典（复用现有字典体系）
```
dict_code = engineering_color     色号（工程）
items: item_key=123C  item_value=PANTONE 123C  label=潘通蓝 ...
```
- 复用 sys_dict/sys_dict_item，不建新表（有管理页：views/system/dict）
- 扩展属性（如关联油墨）用 ext_data JSON

### 3.2 油墨 → inventory_material 物料（类型 R + 新分类）
```
inventory_material_category 新增：
  油墨印刷 (category_code=INK, parent 挂原材料大类或独立一级)
inventory_material 新增油墨物料：
  material_code / material_name(如：银浆-111) / material_type=R
  / category_id=油墨分类 / specification(牌号/厂商) / supplier_id
```
- 油墨走完整物料生命周期：采购/入库/领用/库存/BOM
- 物料已有 material_code（如 MTR2026xxxx）作为唯一标识

### 3.3 打样工序存储（sales_sample_process.custom_process_params 结构变更）
现状：`{"printName","colorNo","inkNo","screenNo"}`
改为：
```json
{
  "printName": "正印蓝色",
  "colorNo": "123C",              // 字典值（engineering_color 的 item_key）
  "colorNoLabel": "潘通蓝",        // 冗余显示名
  "inkMaterialId": 804,           // 关联油墨物料（已建档时）
  "inkNo": "银浆-111",            // 兜底文本（未建档时手输；建档后冗余物料名）
  "screenNo": "G0001"
}
```
- inkMaterialId 有值 = 已关联物料（可追溯采购/库存）；无值仅 inkNo 文本 = 未建档兜底
- 后续跑通领料后，可按 inkMaterialId 自动生成领料

## 四、改动点清单

### 后端
1. `inventory_material_category`：初始化"油墨印刷"分类数据（SQL/接口）
2. 新增/复用物料查询接口（按 category_id=INK 过滤）给打样选油墨用（现有 material list 接口带 category 参数即可）
3. `process/history` 接口：色号改从字典取、油墨改从物料取（或前端直接调字典/物料接口，history 仅保留 printName）

### 前端
4. `PrintProcessPanel.vue`：
   - 色号列：el-autocomplete → 字典下拉（engineering_color，fetch 字典接口）
   - 油墨列：el-autocomplete → 油墨物料选择器（MaterialPicker 过滤 category=INK）+ allow-create 手输兜底
   - 网框列：保持（已走主数据）
5. 物料分类管理页建"油墨"分类（inventory/material/category.vue 已有页面，补数据）

### 数据
6. 色号字典 items 初始化（从现有 sales_sample_process 历史/网版 content 提取？0 行无历史 → 空字典起步，工程部边用边加）
7. 油墨物料初始化：从现有 R 物料中含"墨/银浆"的筛选确认（已查到 25#油胶PET 等，需甄别哪些是印刷油墨）

## 五、分步实施建议

| 步骤 | 内容 | 依赖 |
|---|---|---|
| 1 | 物料分类建"油墨印刷" + 油墨物料初筛确认 | 无 |
| 2 | 色号字典 engineering_color + 字典管理确认 | 无 |
| 3 | PrintProcessPanel 色号改字典下拉 | 步骤 2 |
| 4 | PrintProcessPanel 油墨改物料选择器 | 步骤 1 |
| 5 | 打样流程真实跑通（样品单→打样→工序），验证数据 | 步骤 3/4 |

## 六、待拍板遗留
- 油墨物料编码规则：沿用 MTR 流水 or 油墨专用前缀（如 INK-xxx）？
- 色号字典是否要 ext_data 关联默认油墨？（一个色号通常对应一款墨）
- 领料联动：工序选了油墨物料后，打样领料是否自动带出？（后置）
