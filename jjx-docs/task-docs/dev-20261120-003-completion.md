# 即时库存搜索、分页与统计完成记录

任务码：dev-20261120-003；执行Codex；独立验收用户；提交哈希见sys_task追加记录。

1）交付：保留002的档案详情及既有批次/收发/导入/迁移维护入口，搜索复用SearchForm。常显关键词/库存类型/品类子类/仓库；更多筛选含库位/库存状态/有无库存/记账数量范围/库存物品创建日期/材质形态颜色品牌及品类动态枚举布尔属性。产品隐藏并清除材料专属条件；切换品类清旧属性，库位跟仓库联动。关键词复用物料档案带参数的检索条件，修正该共用列表路径既有空格分词转义错误，词间AND。
2）查询归后端：有效批次按同仓库/库位先聚合再筛选/分页；明细、总数、金额、未知成本批数、低库存数和分单位统计共用条件。低库存沿既定可用量不足口径；库存物品创建时间用inventory_item.create_time。库存查询选项使用inventory:stock:view，不额外要求仓库/库位管理权限。不新增表或字段，无业务数据订正。
3）分页与交互：请求pageNum，确定性排序为更新时间与stock_id；搜索回第一页，翻页保留已应用条件，表单尚未搜索的编辑不混入翻页/统计；旧响应不能覆盖新结果，失败清空相应结果。
4）验证：后端InventoryStockSearchTest、InventoryStockPageMeasurementTest及MaterialMasterDataTest共43项通过；搜索真实MySQL只读测试以jjx_ro执行，基准1615行，两页ID不重叠，关键词及属性SQL可运行且分页/统计计数一致；H2行为测试验证多词AND、父类后代、仓库与库位同一行、低库存分页前过滤、零库存及未知成本。前端7项通过；npm run build含状态枚举/类型/Vite通过。check:result-case/docs/collation:strict/migration-collation/notnull/stock:strict/lot:strict/iqc-lineage:strict/quality-ledger:strict/doc-no均通过；库存strict五项0。
5）验证边界：npm run validate在check:model-baseline被既存inventory_material_spec_attr_def、inventory_material_spec_value未备案阻断；本任务未建表，未改基线，后续检查已逐项运行。本次未启停或重启服务，真实浏览器及运行中API未验，代码发布后仍需用户按既有流程加载新版。
6）协作：002在制期间只写无冲突文件；核实002提交4c74e1d2及status2后才改共用页面/索引。既有六个备份删除保持原样，不纳入本次提交。快捷盘点004单独提交。
