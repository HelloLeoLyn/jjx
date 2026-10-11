# 产品作业规范顶部三栏（结构图/凹凸条件/备注）与加工要求升格为备注

- 任务：dev-20261011-040
- 日期：2026-10-11
- 来源：用户指定任务名「作业规范顶部·三栏布局（结构图|凹凸条件|备注）+ 加工要求升格为规范备注」，口径 A。
- 范围：纯前端。产品作业规范子页签顶部版式、工程维护弹窗文案、WorkSpecPaper 纸张版式。

## 任务口径（A）

1. 原「加工要求」（`work_spec_json.engineeringRequirements`）同一字段改名升格为「规范备注」：界面 label 改「备注」，语义＝整份规范备注；不新增字段，旧数据原样沿用。
2. 不改后端接口、不改 `work_spec_json` 的 key（`engineeringRequirements` 保留）、不改表结构。

## 本轮实现

1. 作业规范子页签首行新增三栏（结构图 | 凹凸条件 | 备注）：结构图取 `workSpec.structureFileId` 指向的「结构图」附件并用既有 `renderFile` 展开为图片；凹凸条件 6 项沿用 `embossFields`；备注取 `engineeringRequirements`。三栏用等宽 grid、`align-items:stretch`，左右两栏等高，结构图栏 `flex:1` 撑满，备注栏自动撑满，不留明显缺口。
2. 原独立的「凹凸条件」区块（含内嵌「加工要求」）从数据分区中移除，内容并入首行三栏；保留 `edit('requirements')` 编辑入口（凹凸条件栏与备注栏各一个，均打开同一弹窗页签）。
3. 工程维护弹窗：页签「凹凸条件与加工要求」改「凹凸条件与备注」；表单项 label「加工要求」改「备注」，占位文案改为「整份规范的备注」。
4. 版本快照（ProductSpecDrawer）同一字段 label「加工要求」改「备注」。
5. 打印纸张 WorkSpecPaper 顶部改为三栏等高表：左上＝产品结构图、中＝凹凸条件（两列标签:值）、右上＝备注（原「加工要求」栏，跨页续排）；`data-overflow-title` 由「加工要求」改「备注」。原材料表右侧的凹凸条件列与加工要求区、流程表中间的结构图格一并移出，材料表收为 序号/项目/材料/规格及模数/刀模位置 五列，流程表三组等宽。为容纳顶部三栏，标题 65→60px、材料表 340→300px、流程表 470→372px，行高相应收紧，页高仍为 290mm。

## 文件

- jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue
- jjx-web/src/views/engineering/product-spec/components/WorkSpecPaper.vue
- jjx-web/src/components/product/ProductSpecDrawer.vue
- 本记录及 history/INDEX.md

## 验证边界

- `npm run check:status-enums`、`vue-tsc --noEmit`、`npm run check:docs` 作为提交前自检。
- 纸张实际列线、分页与打印效果由用户部署前端后在浏览器验收（预览与打印共用 WorkSpecPaper）。
- 未改后端、未改表结构、未改 `engineeringRequirements` key，无迁移、无数据订正。
