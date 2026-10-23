# 物料规格属性化提案（规格由属性选择生成，不再纯自由文本）

任务码：dev-20261023-022
日期：2026-10-23
用户决策：2026-10-23「按你的建议来」，采纳 2026-10-23 讨论方案 A（品类属性模板 + 生成文本），并选择「生成底稿 + 允许 ≤30 字补充」。
关联：inventory_material.specification（varchar 500，自由文本）、size（varchar 50）、inventory_material_required_field（品类必填矩阵）。

## 1）问题与证据（只读核查，2026-10-23）

1）全库 1606 个物料，`specification` 为空 1241 个（77%）；RM-FILM 363/363、RM-PAPER 79/79、RM-ADHESIVE 144/144、AUX-ADHESIVE 204/205 全空。
2）旧口径留下的「尺寸/颜色/类别词」主要落在 `size`：`size` 已填 1558 条；取值前列出现 `连接器`(50)、`镀镍弹片`(21)、`不锈钢`(13) 这类工艺/类别词。
3）`specification` 里含 `×` 的仅 1 条、含 `mm` 的仅 4 条 —— 说明「填得乱」不是主要矛盾，「没人会填、干脆不填」才是。
4）下游只读 `specification` 文本（BOM「规格及模数」、打印、采购、来料检验），因此改造不能改变文本的唯一性。

结论：一个自由文本框同时承担「可枚举工艺」「可度量参数」「厂商料号」三类信息，必然退化。料号已在 `model`、颜色已在 `color`、尺寸已在 `size`，剩下的工艺/参数应当给选项。

## 2）为什么不复用现有结构（§15.1）

1）`sys_config` / 字典：只能存「选项清单」，无法表达「某品类有哪些属性、什么录入类型、什么单位、是否必填」，也无法承载每个物料的取值。
2）在 `inventory_material` 加 JSON 列：MySQL JSON 无法为单个属性建等值/区间索引，也无法保证同一物料同一属性只有一条（去重靠应用层，易产生脏数据）。
3）因此需要两张表：属性定义（配置，按品类维护）+ 属性值（物料明细，可索引查询）。

## 3）§15.2 准入判据核对（满足 5 条）

1）独立业务对象：属性定义有自己的维护与启停生命周期，不是某张表的类型变体。
2）落入业务数据：属性值属于物料主数据明细，被筛选、统计、后续报表引用。
3）库级约束：`uk(category_code, attr_code)`、`uk(material_id, attr_code)` 保证唯一定义与唯一取值。
4）规模与查询能力：属性值需按属性等值/数值区间筛选（`idx_spec_value_attr`），超出配置/字典承载范围。
5）可证明无法复用：见第 2 节。

## 4）与第二真源的关系（§15.5）

1）真源仍是 `inventory_material.specification` 文本，下游一字不改；属性值是它的**保存时一次生成的来源**，属 §15.5 认可的「保存时算出的真源快照」。
2）补充说明单独存 `inventory_material.spec_supplement`（≤30 字），统计只认属性值，补充说明不参与统计口径。
3）生成规则集中在服务端 `MaterialSpecComposer`，前端只做同规则预览；服务端保存时总是按属性重算并覆盖文本，避免两处写法漂移。

## 5）反向指标自评（§15.6）

`inventory_material` 前缀下有 5 张表（material / category / required_field / spec_attr_def / spec_value），触发「同一前缀 ≥3 张」人工评审告警。本次评审结论：五张表职责互不重叠（主数据 / 品类 / 必填矩阵 / 属性定义 / 属性值），不属同构拆分，不合并。

## 6）迁移、兼容与回退

1）迁移 `302_inventory_material_spec_attr.sql`：新建两表 + `inventory_material.spec_supplement` 可空列 + 27 条属性定义种子（8 个品类；OTHER-R / OTHER-A / AUX-TOOL 不配属性，保留自由填写）。
2）不改任何存量行：存量 1241 条空规格、`size` 里的工艺词都原样保留，不在本任务做批量订正。
3）回退：删两张新表 + 删列即可；`specification` 文本仍可独立使用，下游无感。
4）编码形态：`/inventory/material/spec/*` 三个端点挂在新 Controller，不改 `InventoryMaterialServiceImpl`（该文件有并行任务 dev-20261023-019 在制改动）。

## 7）存量反推（只做预填，不做批量 UPDATE）

`model` 里带 mm/g/# 的比例：RM-PAPER 94%、RM-FILM 52%、RM-ADHESIVE 28%；`size` 已是纯尺寸的比例 RM-FILM 99%。因此表单打开时按 `model`/`size` 正则反推出厚度/克重草稿预填，人工确认即可；批量回填与 `size` 工艺词订正另立卡（涉及 UPDATE，需表级备份）。
