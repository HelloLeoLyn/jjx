# 物料主数据规范化设计方案：大类、品类、字段拆分与必填规则

任务码：dev-20261023-005
状态：设计稿（用户 2026-10-23 选择"先出设计文档"，未实施、未改库、未改代码）
数据基准：inventory_material 启用物料 1606 条（2026-10-23 实测）

---

## 1. 现状与数据依据

1）大类分布：R 原材料 826 条、A 辅助材料 780 条；S/F/I 均为 0。
2）名称规范性：98%（1581 条）名称含数字，61%（977 条）含括号，平均长度 13.5 字（最长 38）。
3）同名多规格普遍：120g进口黄色离型纸 43 个规格、PET125# 40、3M467(万绰) 31。
4）原材料内部（按名称关键词）：片材/膜类 469、保护膜 116、离型纸/膜 78、胶带/双面胶 2。
5）辅助材料内部：3M/VHB 胶带 196、其它胶类 12、型号式命名（电子/五金等）236、其余 336。
6）分类现状：inventory_material_category 仅 1 条（INK 油墨印刷），1600 条物料的 category_id 基本为空；process_group 几乎全为 O（未使用）。
7）结论：当前只有"大类"这一层在起作用，品类、结构化属性都没有；名称承担了全部信息，导致搜索与筛选都不可靠。

---

## 2. 已定口径

1）大类：沿用 material_type（R/S/F/A/I），不新造。
2）品类：新建 10 条品类，**品类绑大类**；物料只选品类，大类由品类自动带出并冗余保存。
3）字段拆分：抽 3 个（材料、型号、颜色）+ 加 3 个（品牌、别名、搜索生成列）；尺寸/厚度/克重/表面处理/封装**留在通用规格 specification**。
4）尺寸（宽/长）单位不统一、长度常未知，本版不抽字段，后续标准化后再优化；宽度单位统一 mm。
5）必填规则：按"品类 × 字段"配置化；**存量不追罚**，仅对新建/编辑保存生效。
6）"有些物料实际是原材料但归在 A"：本版不纠正归类，只要求每条都有品类可维护；纠偏另做一次数据订正。

---

## 3. 品类清单（10 条）

| 代码 | 品类 | 大类 |
|---|---|---|
| RM-FILM | 片材/膜材 | R |
| RM-PAPER | 纸材（离型纸/离型膜） | R |
| RM-ADHESIVE | 胶粘类-原材（保护膜、双面胶卷材） | R |
| RM-INK | 油墨 | I |
| AUX-ADHESIVE | 胶粘类-辅料（3M 胶带等） | A |
| AUX-ELEC | 电子元件（LED/电阻/电容/连接器） | A |
| AUX-HARDWARE | 五金件（螺丝/垫片/弹片） | A |
| AUX-PACK | 包材（纸箱/袋/泡棉/标签） | A |
| AUX-TOOL | 耗材工具（刀模/砂纸/手套） | A |
| OTHER | 其他 | R/S/F/A 各一条 |

说明：跨大类的"胶粘类"拆成 RM-ADHESIVE 与 AUX-ADHESIVE 两条，以保持"品类→大类"单一真源。

---

## 4. 必填矩阵

基线必填（所有物料）：名称、单位（字典 M/PCS/KG/SET/卷）、品类（大类自动带出）。

| 属性 | 片材膜材 | 纸材 | 胶粘-原材 | 油墨 | 胶粘-辅料 | 电子元件 | 五金件 | 包材 | 耗材 | 其他 |
|---|---|---|---|---|---|---|---|---|---|---|
| 材料 material_grade | ★ | ★ | ★ | ○ | ○ | ○ | ★ | — | ○ | ○ |
| 型号 model | ○ | ○ | ○ | ○ | ★ | ★ | ★ | ○ | ○ | ○ |
| 颜色 color | ★ | ★ | ★ | ★ | ○ | ★（灯必填） | — | ○ | — | ○ |
| 规格 specification | ★ | ★ | ★ | ★ | ★（可只填宽度） | ○（封装） | ★（螺纹规格） | ★（尺寸） | ○ | ○ |
| 品牌 brand | ○ | ○ | ○ | ○ | ★ | ○ | ○ | — | — | ○ |
| 别名 search_keywords | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ |

---

## 5. 表结构设计（草案，未执行）

### 5.1 品类表绑大类
```sql
ALTER TABLE inventory_material_category
  ADD COLUMN material_type CHAR(1) NULL COMMENT '所属大类：R/S/F/A/I' AFTER parent_id,
  ADD INDEX idx_category_type (material_type, status);
```

### 5.2 物料表加字段
```sql
ALTER TABLE inventory_material
  ADD COLUMN material_grade VARCHAR(30) NULL COMMENT '材料：PET/PE/PC/PVC/不锈钢/纸等（字典 material_grade）',
  ADD COLUMN model VARCHAR(100) NULL COMMENT '型号/料号/规格档（3M5604、PET125#、M1.6*3）',
  ADD COLUMN color VARCHAR(20) NULL COMMENT '颜色（字典 color）',
  ADD COLUMN brand VARCHAR(50) NULL COMMENT '品牌（与供应商区分）',
  ADD COLUMN search_keywords VARCHAR(500) NULL COMMENT '别名/客户叫法，逗号分隔',
  ADD INDEX idx_material_grade (material_grade),
  ADD INDEX idx_model (model),
  ADD INDEX idx_color (color),
  ADD INDEX idx_brand (brand);
```
（正式迁移脚本按实际列顺序补 AFTER 定位；以上仅为字段与类型设计。）

### 5.3 搜索生成列
```sql
ALTER TABLE inventory_material
  ADD COLUMN search_blob VARCHAR(1000)
    GENERATED ALWAYS AS (CONCAT_WS(' ',
      IFNULL(material_name,''), IFNULL(material_name_en,''), IFNULL(material_code,''),
      IFNULL(model,''), IFNULL(material_grade,''), IFNULL(color,''),
      IFNULL(specification,''), IFNULL(search_keywords,'')
    )) STORED,
  ADD INDEX idx_search_blob (search_blob);
```
搜索统一为：`WHERE search_blob LIKE '%词%'`（多词词间 AND），替代现有的多字段 OR。

### 5.4 必填规则配置表
```sql
CREATE TABLE inventory_material_required_field (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category_code VARCHAR(50) NOT NULL COMMENT '品类编码',
  field_key VARCHAR(50) NOT NULL COMMENT '字段：material_grade/model/color/specification/brand',
  required TINYINT NOT NULL DEFAULT 0 COMMENT '1必填 0选填',
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_field (category_code, field_key)
) COMMENT='品类必填字段规则';
```
后端按品类取规则校验，前端同源读取做星号提示；改规则不需要发版。

---

## 6. 数据回填规则（须人工复核，不盲改）

1）品类回填：按名称关键词初版映射，输出清单人工确认后写库。
   - 含"保护膜/双面胶/胶带" → RM-ADHESIVE（若 material_type=A 且名称含 3M/VHB → AUX-ADHESIVE）
   - 含"离型" → RM-PAPER
   - 含 PET/PC/PVC/PP/PE 且无"保护膜" → RM-FILM
   - 含"灯/LED/电阻/电容/连接器/IC" → AUX-ELEC
   - 含"螺丝/螺钉/垫片/弹片/铆钉" → AUX-HARDWARE
   - 含"箱/袋/泡棉/标签" → AUX-PACK
   - 含"刀模/治具/砂纸/手套" → AUX-TOOL
   - 其余 → 对应大类的 OTHER
2）材料回填：从名称前缀/关键词提取 PET/PE/PC/PVC/PP/离型纸/不锈钢…，置信度低的留空。
3）颜色回填：命中颜色词表（透明/白色/黑色/黄色/红色/蓝色/灰色/银色）。
4）型号回填：名称中的字母数字组合（3M5604、PET125#、C0603…）；`0.175` 这类裁切档也进型号。
5）以上四项均**先出 CSV 清单人工复核**，再批量写入；写入前按仓库口径做迁移前备份。

---

## 7. 前端与校验

1）物料表单：大类（只读，由品类带出）→ 品类（下拉，按大类过滤）→ 材料/型号/颜色/品牌（字典或文本框）→ 别名 → 规格（自由文本）。
2）名称：由 材料 + 型号 + 颜色 +（品牌） 自动拼接，允许高级手改但走校验（禁状态词、禁尺寸、括号统一半角、长度 8–30）。
3）保存校验：按 5.4 规则表校验必填；存量老数据编辑时，**只校验新填/改动字段**，不因历史缺失而阻塞。
4）导入模板：新增 品类/材料/型号/颜色/品牌 五列，兼容仅填名称+规格的旧模板（自动解析映射）。

---

## 8. 巡检

1）category_id 为空的存量数量。
2）品类与大类不一致（冗余字段与品类表不符）。
3）名称含状态词（副本/测试/暂放/新/旧）、含 `*`（尺寸）、全角括号、超长。
4）同类物料材料/颜色为空的比例。
输出清单，随备份告警同一邮件通道发送。

---

## 9. 影响面与风险

1）表单与导入模板需改造（前端工作量主要在这）。
2）回填需要人工复核（名称格式混乱，纯自动会错）。
3）打印/报表继续读 specification，不受影响。
4）同名多规格是常态，不给 material_name 加唯一约束。
5）性能：1606 条 LIKE 全扫 1–3ms；上万条再考虑 ngram 全文索引或搜索物化表。

---

## 10. 未决与后续

1）尺寸（宽/长）标准化后再决定是否抽 width_mm/length_mm。
2）厚度、克重、表面处理、封装是否抽字段，看业务是否提出筛选需求。
3）放错大类的物料（如实际是原材料却归 A）另做一次数据订正。
4）物料名称历史数据是否批量重写，需用户决定（本设计只约束新建/编辑）。
5）Redis 序列号播种、approve-check N+1 与本设计无关，各自独立卡。
