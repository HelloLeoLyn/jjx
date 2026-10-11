# 作业规范·合并 BOM 与刀模位置为一张表

- 任务：dev-20261011-041
- 日期：2026-10-11
- 来源：用户任务「合并 BOM 与刀模位置为一张表」；确认口径 1A（表头「编辑」开 BOM 材料弹窗、末列就地可编辑）＋ 2A（凹凸条件留在 040 顶部三栏）。
- 范围：纯前端。产品作业规范子页签（数据组件 ProductWorkSpecPanel）。

## 口径

1. 数据组件把「BOM 块」+「刀模位置块」并成一张表：列＝序号 / 项目 / 材料 / 规格及模数 / 刀模位置（末列就地填，值＝dieLocation map[key]）；表头一个「编辑」。
2. 末列「刀模位置」就地可编辑：单元格内联输入，失焦/回车保存到 `work_spec_json.dieLocation`。
3. 删掉 039 加的独立「刀模位置」数据块。
4. 顺序：顶部三栏（结构图 | 凹凸条件 | 备注，040）→ 材料表 → 工艺路线 → 印刷规范。

## 本轮实现

1. 合并表 `materialRows`：展平后的 BOM 行进主表（序号 / 项目 / 材料 / 规格及模数），再接 dieLocation map 中「BOM 无此材料」的待清理行（序号列标「待清理」，材料列显示原始 key，规格及模数留「-」）。
2. 末列 `el-input` 绑定 `dieDraft`（由 `dieMap` 同步），`@change` 调 `saveDieLocation`：复制 `normalizeWorkSpec` 后写入/删除 `map[key]`，走既有 `productWorkSpecApi.save`（带 revision，空值即删除该 key），成功后重载；保存中禁用并计入 busy。
3. 保留 039 机制：key＝材料编码|层|工序（`dieLocationKey`）；BOM 行无值 → 占位「未填写」；map 有而 BOM 无 → 「待清理」；旧文本一键迁移（`hasLegacyDie` + `migrateLegacyDie`，仍落到工程维护·刀模位置页签）。
4. 删除数据分区里的独立「刀模位置」块；工程维护弹窗内的「刀模位置」页签保留（批量编辑 + 未匹配清理 + 旧文本迁移落点）；移除合并后不再使用的 `calculateBomQuantity` 引入。

## 文件

- jjx-web/src/views/engineering/product-spec/components/ProductWorkSpecPanel.vue
- 本记录及 history/INDEX.md

## 验证边界

- `vue-tsc --noEmit` 通过；未跑浏览器、打印、迁移或服务生命周期操作。
- 内联保存复用既有 `PUT /product/{id}/work-spec`；未改后端、未改表结构、未改 `work_spec_json` 的 key。
- 纸张 WorkSpecPaper 未改（040 已为 5 列含刀模位置）。
