# dev-20261024-007 阶段 B：删六列覆盖清单与应用证据

准备阶段授权（历史记录）：仅准备307迁移、跨模块适配及临时库dry-run；正式库不得应用307，不提交、不push，任务保持status=1。保留original_name/original_specification；不改变004交互设计，不启停服务。执行者codex。

## 现状证据

正式库ops.schema.version=306，inventory_material为54列、1607行；分类非空1041，未归类566。分类树为3/3/13。git初始改动仅备份目录，均不触碰。007为status=1/assignee=codex，004为status=0。

search_blob生成列仍引用material_name_en：307必须先重建表达式再删列。这是唯一允许且将在本脚本内解除的已知生成列依赖；其它视图/FK/生成列依赖须为零，否则不得应用。

## 编码前覆盖清单（原始行号快照）

下列为修改前扫描，行号不承诺在修改后仍相同。主表类型列删除；业务如需类型，用分类根派生R/S/F；分类为空/路径无效返回null。原A语义改读二级RAW_AUX或name_path，不按旧码反推。新编码由分类推导RM/AUX/INK/SFG/FG，历史AUX编码原样冻结。列表/详情/导出只显示一级分类名称，无分类显示未归类。

用途与替换口径：

|范围|用途|处理|
|---|---|---|
|InventoryMaterialServiceImpl / MaterialMasterService / Controller|编码前缀、导入、筛选、校验、导出|分类ID和路径派生；无分类不伪造类型/前缀，按明确分类生成新码|
|InventoryMaterialMapper / MaterialMasterCatalog / ConfigurationService|分类读取和配置|主表六列全部移除；分类表material_type不是本次删列对象，读取业务类型仍不依赖兼容值|
|InventoryInboundServiceImpl|默认仓分组校验|R/S归原料组、F归成品组、null不参与类型组，保留既有默认仓/通用回退|
|InventoryOutboundServiceImpl|半成品档案匹配|分类根SEMI条件；无分类不命中|
|SalesReturnServiceImpl|产品成品物料校验|分类根FINISHED条件；无分类不命中并走原错误提示|
|EngineeringBomServiceImpl / BomItemEditor|BOM类型展示|从物料分类派生R/S/F，可空；保留BOM独立展示字段|
|QuotationServiceImpl / SampleOrderServiceImpl / SalesQuotationItem|报价材料描述、差异记录、打印、样品来源|其materialType属于sales_quotation_item的材料文本，不是主表列，保留独立语义|
|MaterialMasterAuditService|迁移校验|不比对已删类型字段；校验分类路径可派生|
|物料DTO/VO/实体/Converter/types|载荷与映射|六列全部移除；派生展示名使用categoryRootName；类型筛选改rootType|
|物料详情/列表/重建弹窗|展示、筛选、保存|显示一级分类中文名，移除旧类型保存；布局与既有操作流程不重做|
|materialCreateDefaults / materialMasterFields|默认值/导入|移除默认R及旧A兜底；分类缺失明确提示而不归类|
|InventoryMaterialSpecValueMapper及spec接口|302旧规格|移除主表spec_supplement查询和写入；旧写入口保持停用|

逐行证据：

```text
jjx-web/src/types/product/bom.ts:75:  materialType?: string
jjx-web/src/api/sales/quotation.ts:53:  materialType?: string
jjx-web/src/types/inventory/materialMaster.ts:37:  materialType: string
jjx-web/src/api/inventory/material.ts:160:  generateCode(materialType?: string) {
jjx-web/src/api/inventory/material.ts:161:    return request.get<R<string>>('/inventory/material/code', { params: { materialType } })
jjx-web/src/types/inventory/outbound.ts:62:  materialType: string
jjx-web/src/enums/inventory/MaterialEnum.ts:16: * 对应后端 MaterialEnums.MaterialType
jjx-web/src/enums/inventory/MaterialEnum.ts:18:export const MaterialTypeEnum = createEnum({
jjx-web/src/enums/inventory/MaterialEnum.ts:58:  type: MaterialTypeEnum,
jjx-web/src/types/inventory/inbound.ts:122:  materialType: string
jjx-web/src/types/inventory/material.ts:76:  materialType: string
jjx-web/src/types/inventory/material.ts:122:  materialType?: string
jjx-web/src/types/inventory/material.ts:154:  materialType?: string
jjx-web/src/types/inventory/material.ts:172:  materialType: string
jjx-web/src/types/inventory/material.ts:204:  materialType?: string
jjx-web/src/components/inventory/materialMasterFields.ts:90:  materialType?: string
jjx-web/src/components/inventory/materialMasterFields.ts:97:export function deriveMaterialType(categoryCode?: string): string {
jjx-web/src/components/inventory/materialMasterFields.ts:205:        materialType: item.materialType || deriveMaterialType(item.categoryCode),
jjx-web/src/components/inventory/materialMasterFields.ts:219:  materialType?: string
jjx-web/src/components/inventory/materialMasterFields.ts:220:  material_type?: string
jjx-web/src/components/inventory/materialMasterFields.ts:229:    materialType: row.materialType || row.material_type || row['物料类型'] || row['大类'],
jjx-web/src/components/inventory/materialMasterFields.ts:244:  const type = (row.materialType || row.material_type || 'R').trim()
jjx-web/src/components/inventory/materialMasterFields.ts:245:  const materialType = ['A', '辅助材料', '辅料'].includes(type) ? 'A' : 'R'
jjx-web/src/components/inventory/materialMasterFields.ts:246:  const category = matched || options.find((item) => item.categoryCode === `OTHER-${materialType}`)
jjx-web/src/components/inventory/materialMasterFields.ts:247:  if (!category?.categoryId) throw new Error(`未找到${materialType === 'A' ? '辅助材料' : '原材料'}默认品类，无法建档`)
jjx-web/src/components/inventory/materialMasterFields.ts:252:      materialType: category.materialType || deriveMaterialType(category.categoryCode),
jjx-web/src/components/BomItemEditor.vue:577:  row.materialType = material.materialType
jjx-web/src/components/BomItemEditor.vue:955:  row.materialType = undefined
jjx-web/src/types/sales/sampleOrder.ts:371:  materialType?: string
jjx-web/src/components/inventory/MaterialDetailDialog.vue:19:        <el-descriptions-item label="大类">{{ materialTypeText }}</el-descriptions-item>
jjx-web/src/components/inventory/MaterialDetailDialog.vue:144:import { MaterialStatusEnum, MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
jjx-web/src/components/inventory/MaterialDetailDialog.vue:146:  deriveMaterialType,
jjx-web/src/components/inventory/MaterialDetailDialog.vue:227:const materialTypeText = computed(() => {
jjx-web/src/components/inventory/MaterialDetailDialog.vue:228:  const type = material.value?.materialType || deriveMaterialType(category.value?.categoryCode)
jjx-web/src/components/inventory/MaterialDetailDialog.vue:229:  return type ? `${type} · ${MaterialTypeEnum.getLabel(type)}` : '-'
jjx-web/src/components/inventory/MaterialFormDialog.vue:23:    materialType?: string
jjx-web/src/components/inventory/MaterialMasterFormDialog.vue:217:    materialType?: string
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:38:              <el-input :model-value="materialTypeText" readonly placeholder="由品类带出" />
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:163:import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:168:  deriveMaterialType,
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:234:const materialTypeText = computed(() => {
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:235:  const type = deriveMaterialType(categoryCode.value) || original.value?.materialType || ''
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:236:  return type ? `${type} · ${MaterialTypeEnum.getLabel(type)}` : ''
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue:367:        materialType: deriveMaterialType(categoryCode.value) || original.value.materialType,
jjx-web/src/components/inventory/materialCreateDefaults.ts:16:  materialType?: string
jjx-web/src/components/inventory/materialCreateDefaults.ts:31:    materialType: MATERIAL_DEFAULT_TYPE,
jjx-web/src/components/inventory/materialCreateDefaults.ts:44:    materialType: MATERIAL_DEFAULT_TYPE,
jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringBomItem.java:226:    private String materialType;
jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringBomServiceImpl.java:943:                        if (mat != null) it.setMaterialType(mat.getMaterialType());
jjx-server/src/main/java/com/jjx/purchase/domain/enums/SupplierTypeEnum.java:8: * <p>dev-20260912-003：参考物料类型 inventory_material_type 重构，替代原 M（物料）/E（设备）/O（其他）。</p>
jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringBomItemVO.java:74:    private String materialType;
jjx-server/src/main/java/com/jjx/inventory/domain/InventoryMaterialCategory.java:36:    private String materialType;
jjx-server/src/main/java/com/jjx/inventory/domain/InventoryMaterial.java:40:    private String materialType;
jjx-server/src/main/java/com/jjx/inventory/mapper/InventoryMaterialMapper.java:19:    @Select("SELECT category_id, category_code, category_name, material_type, parent_id FROM inventory_material_category WHERE category_id = #{id}")
jjx-server/src/main/java/com/jjx/inventory/mapper/InventoryMaterialMapper.java:22:    @Select("SELECT category_id, category_code, category_name, material_type, parent_id FROM inventory_material_category WHERE category_code = #{value} OR category_name = #{value} LIMIT 1")
jjx-server/src/main/java/com/jjx/sales/domain/entity/SalesQuotationItem.java:47:    private String materialType;
jjx-web/src/views/sales/quotation/components/QuotationSendDialog.vue:203:  ;['materialType', 'color', 'circuitType', 'connectorType'].forEach((k) => {
jjx-server/src/main/java/com/jjx/inventory/dto/imports/MaterialImportDTO.java:39:    private String materialType;
jjx-web/src/views/sales/quotation/print.vue:141:  ;['materialType', 'color', 'circuitType', 'connectorType'].forEach((k) => {
jjx-server/src/main/java/com/jjx/sales/domain/vo/SampleSourceDocVO.java:51:        private String materialType;
jjx-server/src/main/java/com/jjx/inventory/dto/query/MaterialQueryDTO.java:23:    private String materialType;
jjx-web/src/views/inventory/material/detail.vue:132:import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
jjx-web/src/views/inventory/material/detail.vue:143:  materialType: '',
jjx-web/src/views/inventory/material/detail.vue:165:  return MaterialTypeEnum.getLabel(material.value.materialType) || '-'
jjx-web/src/views/inventory/material/config.ts:18:    prop: 'materialType',
jjx-web/src/views/inventory/material/config.ts:178:    prop: 'materialType',
jjx-web/src/views/inventory/material/config.ts:181:    slot: 'materialType',
jjx-web/src/views/inventory/material/index.vue:222:  materialType: '',
jjx-web/src/views/inventory/material/index.vue:335:  queryParams.materialType = ''
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialVO.java:20:    private String materialType;
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialExportVO.java:24:    private String materialTypeDesc;
jjx-server/src/main/java/com/jjx/inventory/dto/vo/StockSummaryVO.java:86:    private java.util.List<MaterialTypeStockSummary> materialTypeSummaries;
jjx-server/src/main/java/com/jjx/inventory/dto/vo/StockSummaryVO.java:105:    public static class MaterialTypeStockSummary {
jjx-server/src/main/java/com/jjx/inventory/dto/vo/StockSummaryVO.java:106:        private String materialType;
jjx-server/src/main/java/com/jjx/inventory/dto/vo/StockSummaryVO.java:107:        private String materialTypeName;
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialCategoryOptionVO.java:11:    private String materialType;
jjx-server/src/main/java/com/jjx/sales/service/impl/QuotationServiceImpl.java:629:            changeRecorder.diff(changes, prefix + "材料", oldItem.getMaterialType(), newItem.getMaterialType());
jjx-server/src/main/java/com/jjx/sales/service/impl/QuotationServiceImpl.java:1284:        appendNonEmpty(spec, item.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/dto/update/MaterialUpdateDTO.java:21:    private String materialType;
jjx-server/src/main/java/com/jjx/sales/service/impl/SalesReturnServiceImpl.java:231:                                    .eq(InventoryMaterial::getMaterialType, "F"));
jjx-web/src/views/engineering/sample-workbench/components/SourceDocSummaryDialog.vue:147:          <el-table-column label="材料" prop="materialType" width="96" />
jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java:4408:                item.setMaterialType(it.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/dto/save/MaterialSaveDTO.java:17:    private String materialType;
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterCatalog.java:30:                           int categoryLevel, String materialType, String pathName, boolean selectable, Integer sortOrder, Integer status) { }
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterAuditService.java:40:                if (!Objects.equals(material.getMaterialType(), prepared.getMaterialType())) problems.add(new Problem(material.getMaterialId(),material.getMaterialName(),"类型与分类根节点不一致"));
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterConfigurationService.java:73:        entity.setCategoryLevel(parent.categoryLevel() + 1); entity.setMaterialType(parent.materialType());
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterService.java:101:        material.setMaterialType(config.category().materialType());
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterService.java:155:            material.setMaterialCode(code == null ? materialService.generateCode(material.getMaterialType()) : code);
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterService.java:167:            columns.put("category_id", material.getCategoryId()); columns.put("material_type", material.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/InventoryMaterialService.java:85:    String generateCode(String materialType);
jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialController.java:105:    public Result<String> code(@RequestParam(required = false) String materialType) {
jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialController.java:106:        return Result.success(materialService.generateCode(materialType));
jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialController.java:256:            MaterialEnums.Type type = MaterialEnums.Type.fromValue(entity.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialController.java:257:            row.setMaterialTypeDesc(type == null ? entity.getMaterialType() : type.getLabel());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:102:                .select("category_id", "category_code", "category_name", "material_type")
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:109:                    option.setMaterialType((String) row.get("material_type"));
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:144:        if (StringUtils.isNotBlank(queryDTO.getMaterialType())) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:145:            wrapper.eq(InventoryMaterial::getMaterialType, queryDTO.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:469:                dto.setMaterialType((String) row.category().get("material_type"));
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:470:                String materialCode = generateMaterialCode(dto.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:477:                material.setMaterialType(dto.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:540:    public String generateCode(String materialType) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:541:        return generateMaterialCode(materialType);
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:614:    private String generateMaterialCode(String materialType) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:615:        String prefix = resolveMaterialCodePrefix(materialType);
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:626:    private String resolveMaterialCodePrefix(String materialType) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:627:        MaterialEnums.Type type = MaterialEnums.Type.fromValue(materialType);
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:695:            String categoryType = (String) category.get("material_type");
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:698:                material.setMaterialType(categoryType);
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:700:        } else if (material.getMaterialType() == null && existing != null) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:701:            material.setMaterialType(existing.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:703:        validateMaterialType(material.getMaterialType());
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:794:        dto.setMaterialType((String) category.get("material_type"));
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:924:            dto.setCategoryName(inferImportCategoryCode(inferenceName, dto.getMaterialType()));
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:946:    private static String inferImportCategoryCode(String name, String materialType) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:957:        return "A".equals(materialType) ? "OTHER-A" : "OTHER-R";
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:992:    private void validateMaterialType(String materialType) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:993:        if (MaterialEnums.Type.fromValue(materialType) == null) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java:994:            throw new BusinessException("无效的物料类型：" + materialType);
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2577:                String materialType = material.getMaterialType();
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2579:                if (MaterialEnums.Type.RAW.getValue().equals(materialType)
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2580:                        || MaterialEnums.Type.SEMI.getValue().equals(materialType)
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2581:                        || MaterialEnums.Type.INK.getValue().equals(materialType)
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2582:                        || MaterialEnums.Type.AUXILIARY.getValue().equals(materialType)) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2584:                } else if (MaterialEnums.Type.FINISHED.getValue().equals(materialType)) {
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2799:        // 全库没有 F 类成品物料档案（inventory_material.material_type='F' 为 0 行），
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java:1015:     * 半成品档案按项目名做一次匹配（material_type=S），匹配不到标记 semiMatched=false，由前端提示建档。
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java:1086:    /** 组合 -> 半成品档案匹配（按项目名 + material_type=S），匹配不到时标记 semiMatched=false */
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java:1093:                    "SELECT material_id FROM inventory_material WHERE material_name=? AND material_type='S' AND status=1 LIMIT 1",
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java:1988:            // 查成品物料档案（发布时自动创建，material_type='F'）
```

## 验证与dry-run

本轮阶段B准备完成，不代表正式库已删列、页面实点或独立验收通过。最终退出码如下，命令均串行执行：

|序号|目录与实际命令|最终退出码|证据/边界|
|---|---|---|---|
|1|jjx-server：`mvn -q -DskipTests compile`|0|后端编译|
|2|jjx-server：`mvn -o -q test -Dtest=MaterialMasterDataTest -DargLine=-Xmx512m`|0|24项全部通过，0失败/错误/跳过|
|3|jjx-web：`NODE_OPTIONS=--max-old-space-size=3072 npx vue-tsc --noEmit`|0|前端类型检查|
|4|jjx-web：`npm run check:docs`|0|history265/modules4/task-docs54；未改基线|
|5|仓根：`bash scripts/check-model-baseline.sh`|0|136/136表；保留原有同构表告警|
|6|仓根：`python3 /tmp/jjx307-dryrun.py`（驱动器下附）|0|仅在jjx_dryrun_307复制表并执行307；54→48|
|7|jjx-server：`JJX_TEST_307=1 mvn -o -q test -Dtest=Material307DryRunTest -DargLine=-Xmx512m`|0|真实Mapper、类型/检索、实际出库SQL、成本报表SQL、库存聚合SQL、未归类编辑与新增，事务回滚并回查0残留|
|8|jjx-web：`NODE_OPTIONS=--max-old-space-size=1024 npx vitest run src/components/inventory/__tests__/materialCreateDefaults.spec.ts src/components/inventory/__tests__/materialMasterFields.spec.ts src/components/inventory/__tests__/stockImportCategory.spec.ts src/components/inventory/__tests__/MaterialMasterFormDialog.spec.ts src/components/inventory/__tests__/materialMasterModel.spec.ts`|0|5文件35项全过|
|9|jjx-server：`JJX_TEST_MYSQL=1 JJX_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/jjx_dryrun_307?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' mvn -o -q test '-Dtest=MaterialMasterMysqlTest#savesDefaultsDerivesTypeAndPreservesSourceWithNoLegacyAttributeWrites+clearsDerivedColumnsOnMasterEdit+importPreflightAndTransactionalRollbackCoverTheWholeWorkbook' -DargLine=-Xmx512m`|0|3项全过；48列演练库的会话临时表验证完整保存/清空/Excel预检、导入、导出与回滚|

失败记录（保留，不能把最终全绿解释为从未失败）：

1）单元测试两次退出1：MaterialCategoryOptionsPermissionTest夹具把VO误用setCategoryPath，首次发现后修正脚本因工作目录错误未执行，第二次仍为同一编译错误。修正为rootType/categoryRootName后原命令退出0。
2）check:docs首次退出1：本记录初用Markdown链接登记INDEX，门禁只识别表格；改为表格行后退出0。
3）前端补验第一次退出1：MaterialMasterFormDialog测试未屏蔽Element Plus CSS导入，4文件32项通过、1文件无法加载；补样式mock后第二次退出1（组件mock未导出ElSkeleton，3项失败/32通过）；改为partial mock保留组件导出后35项全过。

## 删列后的范围与反例

1）六个主表列不再出现在实体、物料DTO/VO/Converter、有效Mapper SQL、前端物料types或保存载荷中。`rootType`是按分类路径派生的只读业务值/筛选参数，不是主表持久列；`categoryRootName`用于中文展示。分类表既有material_type不在本次删列范围；目录配置由树根派生它，不作为物料类型读取依据。报价明细/样品来源materialType仍是材料描述；BOM materialType是派生展示字段，可为null。
2）MaterialClassification.materialTypeOf仅按category_path首段返回R/S/F，null或无效路径返回null；根RAW下RAW_BASE/RAW_AUX/RAW_INK全部为R。codePrefixOf按根和二级路径返回RM/AUX/INK/SFG/FG；没有有效分类明确拒绝分配编码，不用MTR/R/A兜底。共享流水号与最大历史号下限保持；历史1607条编码未改。
3）入库未归类物料不计入类型分组，保留显式默认仓优先与既有通用仓回退。出库半成品按根SEMI匹配；退货成品按根FINISHED匹配，无分类不会伪命中。工程BOM每次清空旧类型再派生，避免null或查询失败残留A。
4）出入库前端明细类型中无后端供值的旧materialType字段一并移除；两个库存导入建档入口先解析明确分类，再请求分类ID生成编码；缺分类明确提示，不默认归R/A。旧入口对新模型品类的既有“请使用新版保存入口”约束不变；004交互改造不在本轮。
5）删除spec_supplement表单/DTO/实体与SQL读写，Excel通用表头与后续列索引同步减1；已保存specification保留，不重算历史规格。保留原规格预览函数的内存参数不产生主表补充列写入。
6）旧载荷携带六列由save/update/master DTO显式忽略未知字段；未归类历史物料可经既有编辑服务修改其它字段，分类与编码保留。真实演练库也验证未归类AUX物料编辑后category_id仍NULL、编码相等。
7）未启停服务、未应用正式迁移、未git add/commit/push、未生成备份；并行样品工艺页任务与用户备份改动未触碰。本轮末观察到另一任务提交abce1721，不是本任务提交。

## 临时库与正式库证据

复制15张相关表，保留DDL中的生成列/索引/外键，按依赖顺序创建，不导出备份文件；没有复制hr_employee。15表为分类19、模板16、物料1607、仓库2、库位0、库存对象1562、库存汇总0、批次0、流水0、sys_config124、采购明细0、报价明细1、BOM明细0、字典81、字典项625。

原始SQL语法兼容性通过；当前复制的库存/采购/BOM表为空，因此对应SQL验证只证明真实48列表结构可执行，不能声称已有非空库存业务或页面流程已验收。未使用无调用者的历史selectLowStockMaterials/selectExpiringMaterials/updateStock方法（其中current_stock等旧字段是既有问题，与本次六列无关）；本轮未改这些无调用旧接口，也不把它们算作已验证的有效报表入口。

迁移前生成列依赖仅search_blob→material_name_en；307先重建search_blob表达式再DROP六列，迁移后依赖0。视图/FK六列依赖查询无行。source列、分类、属性、物料名/规格及历史编码逐行SHA256前后相等：`0d7facbb497fdf07046ac1509d8a80727ba483f590b333bbb537688a21c82aad`。这是保留字段摘要，不包含应改变的search_blob；未归类566行仍NULL。

正式库仍ops.schema.version=306、54列、1607行、分类非空1041、material_type A=780/R=827。两列原始来源及cost_price/standard_price保留。演练库保留供复核，未更新其迁移账本；它不代表正式应用完成。

307文件SHA256：`ed3dd53679a593c643559c0ffcd158d845a2c8e374f82f337661d2765afb7554`。

演练驱动器标准输出：

```text
production_before 0d7facbb497fdf07046ac1509d8a80727ba483f590b333bbb537688a21c82aad legacy A	780;R	827
copied inventory_material_category 19
copied inventory_attribute_template 16
copied inventory_material 1607
copied inventory_warehouse 2
copied inventory_storage_location 0
copied inventory_item 1562
copied inventory_stock 0
copied inventory_stock_item 0
copied inventory_transaction 0
copied sys_config 124
copied purchase_order_item 0
copied sales_quotation_item 1
copied engineering_bom_item 0
copied sys_dict 81
copied sys_dict_item 625
migration_output
jjx_dryrun_307
306
1607	1041	566
54
search_blob	concat_ws(_utf8mb4\' \',ifnull(`material_name`,_utf8mb4\'\'),ifnull(`material_name_en`,_utf8mb4\'\'),ifnull(`material_code`,_utf8mb4\'\'),ifnull(`model`,_utf8mb4\'\'),ifnull(`material_grade`,_utf8mb4\'\'),ifnull(`color`,_utf8mb4\'\'),ifnull(`specification`,_utf8mb4\'\'),ifnull(`search_keywords`,_utf8mb4\'\'),ifnull(`size`,_utf8mb4\'\'),ifnull(`brand`,_utf8mb4\'\'))
48
columns 54 -> 48 retained_sha256 0d7facbb497fdf07046ac1509d8a80727ba483f590b333bbb537688a21c82aad
production_version 306
production_columns 54
remaining_generated_dependencies 0
DRY_RUN_307_OK (test DB retained for review; production unchanged)

EXIT_CODE=0
```

真实Mapper验证：

```text
DRYRUN_307 Mapper/检索/类型筛选/半成品SQL/成本报表/库存聚合/未归类编辑/新增：通过；事务ROLLBACK

EXIT_CODE=0
```

## 演练驱动器留档

下列逻辑为本轮驱动器（仅认证改为要求环境变量DB_PASS，不在报告内保存凭据）。目标库必须不存在，遇到已有库立即拒绝，不覆盖、不自动删除或重跑；本轮创建的jjx_dryrun_307已存在。正式库始终只读，DDL和复制写入只发往硬编码的演练库。

```python
from pathlib import Path
import subprocess, os, hashlib, json
repo=Path('/home/administrator/jjx')
env=os.environ.copy();env['MYSQL_PWD']=os.environ['DB_PASS']
def sql(db,query):
 r=subprocess.run(['mysql','-h127.0.0.1','-uroot','--default-character-set=utf8mb4','--batch','--skip-column-names','--raw',db],input=query,text=True,capture_output=True,env=env)
 if r.stderr: print(r.stderr,end='')
 if r.returncode: raise RuntimeError(f'mysql exit={r.returncode}: {r.stdout}')
 return r.stdout.strip()
def guard(condition,msg):
 if not condition: raise RuntimeError(msg)
source='jjx_erp_db'; target='jjx_dryrun_307'
guard(target=='jjx_dryrun_307' and target!=source,'unsafe target')
guard(sql(source,"SELECT config_value FROM sys_config WHERE config_key='ops.schema.version'")=='306','source version must be306')
guard(sql(source,f"SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{target}'")=='0','target already exists: do not overwrite')
tables=['inventory_material_category','inventory_attribute_template','inventory_material','inventory_warehouse','inventory_storage_location','inventory_item','inventory_stock','inventory_stock_item','inventory_transaction','sys_config','purchase_order_item','sales_quotation_item','engineering_bom_item','sys_dict','sys_dict_item']
# Column data goes directly inside MySQL; no backup/dump files are created.
def fingerprint(db):
 rows=sql(db,'SELECT material_id,material_code,category_id,original_name,original_specification,attribute_json,material_name,specification FROM inventory_material ORDER BY material_id')
 return hashlib.sha256(rows.encode()).hexdigest()
original=fingerprint(source)
legacy=sql(source,'SELECT material_type,COUNT(*) FROM inventory_material GROUP BY material_type ORDER BY material_type')
print('production_before',original,'legacy',legacy.replace('\n',';'))
sql(source,f'CREATE DATABASE `{target}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
for table in tables:
 ddl=sql(source,f'SHOW CREATE TABLE `{table}`').split('\t',1)[1]
 # Clone actual DDL including generated columns/indexes/FKs. Referenced tables are cloned first.
 sql(target,ddl+';')
 columns=sql(source,f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{table}' AND EXTRA NOT LIKE '%GENERATED%' ORDER BY ORDINAL_POSITION").splitlines()
 # DEFAULT_GENERATED is a normal writable column, only virtual/stored generated expressions are excluded.
 columns=sql(source,f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{table}' AND GENERATION_EXPRESSION='' ORDER BY ORDINAL_POSITION").splitlines()
 names=','.join('`'+x+'`' for x in columns)
 sql(target,f'INSERT INTO `{table}` ({names}) SELECT {names} FROM `{source}`.`{table}`')
 print('copied',table,sql(target,f'SELECT COUNT(*) FROM `{table}`'))
guard(fingerprint(target)==original,'clone data mismatch')
before=sql(target,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'")
guard(before=='54','clone must start54columns')
print('migration_output\n'+sql(target,(repo/'jjx-docs/sql/migrations/307_material_drop_legacy_columns.sql').read_text()))
after=sql(target,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'")
guard(after=='48','migration must end48columns')
guard(fingerprint(target)==original,'retained data or historical codes changed')
guard(fingerprint(source)==original,'production rows changed')
guard(sql(source,'SELECT material_type,COUNT(*) FROM inventory_material GROUP BY material_type ORDER BY material_type')==legacy,'production legacy types changed')
print('columns',before,'->',after,'retained_sha256',original)
print('production_version',sql(source,"SELECT config_value FROM sys_config WHERE config_key='ops.schema.version'"))
print('production_columns',sql(source,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'"))
print('remaining_generated_dependencies',sql(target,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND REGEXP_LIKE(GENERATION_EXPRESSION,'(^|[^a-zA-Z0-9_])(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)([^a-zA-Z0-9_]|$)')"))
print('DRY_RUN_307_OK (test DB retained for review; production unchanged)')

```


## 本轮源文件清单

共53个源文件（含测试）；另307迁移、本文与history/INDEX.md三个非源文件。

```text
jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialController.java
jjx-server/src/main/java/com/jjx/inventory/converter/MaterialConverter.java
jjx-server/src/main/java/com/jjx/inventory/domain/InventoryMaterial.java
jjx-server/src/main/java/com/jjx/inventory/dto/imports/MaterialImportDTO.java
jjx-server/src/main/java/com/jjx/inventory/dto/query/MaterialQueryDTO.java
jjx-server/src/main/java/com/jjx/inventory/dto/save/MaterialSaveDTO.java
jjx-server/src/main/java/com/jjx/inventory/dto/update/MaterialUpdateDTO.java
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialCategoryOptionVO.java
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialExportVO.java
jjx-server/src/main/java/com/jjx/inventory/dto/vo/MaterialVO.java
jjx-server/src/main/java/com/jjx/inventory/mapper/InventoryMaterialMapper.java
jjx-server/src/main/java/com/jjx/inventory/mapper/InventoryMaterialSpecValueMapper.java
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterAuditService.java
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterDTO.java
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterExcelService.java
jjx-server/src/main/java/com/jjx/inventory/master/MaterialMasterService.java
jjx-server/src/main/java/com/jjx/inventory/service/InventoryMaterialService.java
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialServiceImpl.java
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialSpecServiceImpl.java
jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java
jjx-server/src/main/java/com/jjx/inventory/util/MaterialClassification.java
jjx-server/src/main/java/com/jjx/inventory/util/MaterialSpecComposer.java
jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringBomServiceImpl.java
jjx-server/src/main/java/com/jjx/sales/service/impl/SalesReturnServiceImpl.java
jjx-server/src/test/java/com/jjx/inventory/Material307DryRunTest.java
jjx-server/src/test/java/com/jjx/inventory/MaterialCategoryOptionsPermissionTest.java
jjx-server/src/test/java/com/jjx/inventory/MaterialMasterDataTest.java
jjx-server/src/test/java/com/jjx/inventory/MaterialMasterDatabaseTest.java
jjx-server/src/test/java/com/jjx/inventory/MaterialMasterMysqlTest.java
jjx-server/src/test/java/com/jjx/inventory/MaterialSpecMysqlTest.java
jjx-web/src/api/inventory/material.ts
jjx-web/src/components/BomItemEditor.vue
jjx-web/src/components/inventory/MaterialDetailDialog.vue
jjx-web/src/components/inventory/MaterialFormDialog.vue
jjx-web/src/components/inventory/MaterialMasterFormDialog.vue
jjx-web/src/components/inventory/MaterialRegenerateDialog.vue
jjx-web/src/components/inventory/StockBatchImportDialog.vue
jjx-web/src/components/inventory/StockImportDialog.vue
jjx-web/src/components/inventory/__tests__/MaterialMasterFormDialog.spec.ts
jjx-web/src/components/inventory/__tests__/materialCreateDefaults.spec.ts
jjx-web/src/components/inventory/__tests__/materialMasterFields.spec.ts
jjx-web/src/components/inventory/__tests__/stockImportCategory.spec.ts
jjx-web/src/components/inventory/materialCreateDefaults.ts
jjx-web/src/components/inventory/materialMasterFields.ts
jjx-web/src/components/inventory/materialMasterModel.ts
jjx-web/src/types/inventory/inbound.ts
jjx-web/src/types/inventory/outbound.ts
jjx-web/src/types/inventory/material.ts
jjx-web/src/types/inventory/materialMaster.ts
jjx-web/src/views/inventory/material/config.ts
jjx-web/src/views/inventory/material/detail.vue
jjx-web/src/views/inventory/material/index.vue
```

收尾复核：前端测试mock修正后vue-tsc再次退出0；git diff --check退出0。任务旧remark为475字，本轮结果追加会超500；旧值完整追加description后，将本轮指定文字写入remark，保持status=1，不设置完成状态。


## 2026-10-24 正式应用与收口（codex）

用户完成独立复核并明确授权应用307、验证、按原白名单提交推送和回写待审核；原准备阶段的“不应用/不提交”限定仅为历史记录。本次未改业务源码，沿用已复核的53个源文件（清单已包含新增MaterialClassification.java、Material307DryRunTest.java）。备份目录和其它会话文件均不纳入本卡。

应用前任务为status=1、assignee_name=codex，start_time=2026-10-24 12:08:40；正式库306/54列。依赖检查：视图0、外键0，生成列仅search_blob，符合307先解除依赖再删列的设计。

应用命令（实际退出0）：

```bash
bash scripts/db-migrate.sh jjx-docs/sql/migrations/307_material_drop_legacy_columns.sql --yes --task dev-20261024-007 --backup jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1216.sql
```

入口校验手工备份133表、排除hr_employee；备份SHA256为c41ba08a037207593edcd466336413cda53ac6279a27e422be1da196f3c20f7a。307执行、记账回查、归档均成功；applied/307_material_drop_legacy_columns.sql内容SHA256为ed3dd53679a593c643559c0ffcd158d845a2c8e374f82f337661d2765afb7554，与准备阶段一致。未保留重复待办文件，未手工导入迁移。

正式库信息架构查询证据（退出0）：

```text
columns_before=54
columns_after=48
removed_columns_remaining=0
保留列=original_name/original_specification/thickness/width_size/template_id/attr_meta/attr_thickness_num（7列均存在）
ops.schema.version=307
FIND_IN_SET('307',ops.schema.applied)>0
applied/307存在，待办目录307不存在，ARCHIVE_RC=0
分类根 FINISHED/成品/F：已分类物料0
分类根 RAW/原材料/R：已分类物料1041
分类根 SEMI/半成品/S：已分类物料0
未归类566，派生类型为NULL=566（没有默认R/A）
抽样 material_id=1/2/3，category_path=RAW/RAW_BASE/RAW_BASE_PC，root_type=R
```

列证据来自information_schema.COLUMNS按TABLE_SCHEMA=DATABASE()、TABLE_NAME='inventory_material'查询，已删六列IN清单计数为0；保留七列IN清单逐行查回。类型口径使用CASE SUBSTRING_INDEX(c.category_path,'/',1) WHEN 'RAW' THEN 'R' WHEN 'SEMI' THEN 'S' WHEN 'FINISHED' THEN 'F' ELSE NULL END；无分类通过LEFT JOIN保留NULL。

五类关键查询在正式库逐条执行，整体SCHEMA_SMOKE_RC=0，没有Unknown column：

```sql
-- 物料分页（现有默认排序）
SELECT * FROM inventory_material ORDER BY create_time DESC,material_id DESC LIMIT 1;
-- 物料详情（最小主键现有物料）
SELECT * FROM inventory_material WHERE material_id=(SELECT MIN(material_id) FROM inventory_material);
-- BOM明细带出分类根类型
SELECT b.*,CASE SUBSTRING_INDEX(c.category_path,'/',1)
 WHEN 'RAW' THEN 'R' WHEN 'SEMI' THEN 'S' WHEN 'FINISHED' THEN 'F' ELSE NULL END derived_type
 FROM engineering_bom_item b LEFT JOIN inventory_material m ON m.material_id=b.material_id
 LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
 ORDER BY b.item_order,b.item_id LIMIT 1;
-- 入库列表（现有默认排序）
SELECT * FROM inventory_inbound_order ORDER BY create_time DESC LIMIT 1;
-- 出库列表（现有默认排序）
SELECT * FROM inventory_outbound_order ORDER BY create_time DESC,outbound_id DESC LIMIT 1;
```

物料分页命中1667/RM001623，详情命中1/RM000001。BOM明细/入库单/出库单均为0行，三类查询仅证明正式表结构上SQL可执行；未生成业务样例、未验证非空单据流程、未做HTTP/页面实点、未启停服务。半成品/成品当前无物料实例，仅核验分类根映射，不能称为非空实例验收。

全仓复核使用rg扫描六个snake_case列名及camelCase字段名。运行时主数据实体/DTO/VO/有效Mapper/前端载荷无旧六列读写。分类表material_type、BOM派生展示materialType、报价材料描述、分类业务事件名material_category、反例测试与文档历史不是主表旧列访问；material_master_checks.sql属历史核查报表脚本。

发现原白名单外仍有两处依赖，未擅自适配：scripts/material-normalize-backfill.py:19/140/161/181读取material_type并用于分类和并发保护；scripts/material-reclassify-apply.py:281在project_row重算search_blob时读取material_name_en。两者为历史回填执行脚本，不能自行按报表脚本排除；已向用户请求明确排除或扩展授权。全仓“无残留”验收当前尚未满足，不将其报告为通过。

本轮指定验证真实退出码：mvn -q -DskipTests compile=0；mvn -o -q test -Dtest=MaterialMasterDataTest -DargLine=-Xmx512m=0（24项通过、0失败、0错误、0跳过）；NODE_OPTIONS=--max-old-space-size=3072 npx vue-tsc --noEmit=0；npm run check:docs=0；bash scripts/check-model-baseline.sh=0（136/136，既有同构表告警，不改基线）。遗留：original_name/original_specification待用户人工确认后另行清理；页面未实点；566条未归类待业务归类。


## 历史脚本残留

2026-10-24 派工方裁定：以下两个文件是006与008阶段二已执行完毕的历史一次性回填脚本，不属于运行时路径；从307运行时覆盖范围中排除，保留原逻辑作为迁移证据，不改写脚本。此前“待裁定”的记录保留为历史，本节为最新裁定。

|历史脚本|引用行号与已删列|执行情况与禁止再次运行的原因|
|---|---|---|
|scripts/material-normalize-backfill.py|19行FIELDS包含material_type；140行按row['material_type']推导分类；161行使用FIELDS生成SELECT，180—183行使用FIELDS构建并发快照保护条件|006已执行完毕；307已删除主表material_type，原SELECT和并发保护条件不再适用于现库。禁止在307及后续正式库再次运行，包括干跑；改写逻辑会破坏历史迁移证据的可复现性。|
|scripts/material-reclassify-apply.py|281行project_row重算search_blob时读取material_name_en；406行错误提示保留material_type文字（提示文本，非额外读列）|008阶段二已执行完毕；307已删除material_name_en并重建search_blob表达式，原全列投影校验与现库不兼容。禁止在307及后续正式库再次运行；保留原逻辑作为306结构下已执行迁移的证据。|

历史一次性脚本 2 个仍引用已删列，裁定排除不改。未执行或修改这两个历史脚本。

### 字面扫描口径

2026-10-24 派工方独立核对后明确允许提交：以 `InventoryMaterial.java` 六个已删实体字段及后端对 `inventory_material` 已删列的有效 SQL 读写均为 0 判定通过，同时核对前端 `types/inventory/material.ts` 六字段为 0；字面命中不作为阻塞。本小节为最新裁定，下节暂停记录保留为历史，不再作为停止条件。

合法命中按语义归类：`materialTypeOf` 属分类根派生方法；BOM 的 `materialType` 为派生展示、询价/报价等其它表同名字段为独立材料描述；测试中旧列名用于“列已删除/旧值不生效”的反例断言；`inventory_material_category.material_type` 是分类表自身列。原 snake_case 扫描的 14 行实际为分类日志/事件标识 7 行、后端反例断言及分类兼容值测试 4 行、分类表夹具列 1 行、报价表夹具列 1 行、前端反例测试 1 行；具体文件与行号见下节原始表。派生方法与 BOM 展示属于 camelCase 扫描的合法类别，不混入上述 14 行计数。

扫描命令（仓库根目录）：

```bash
# 原全仓字面扫描；原始输出 /tmp/jjx-007-close-repo-grep.txt
rg -n '\b(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)\b' .
# 实体和前端types核对，均为0行（rg退出1表示没有匹配）
rg -n '\b(materialType|materialCategory|materialNameEn|specSupplement|costPriceFrom|costPriceTime|material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)\b' jjx-server/src/main/java/com/jjx/inventory/domain/InventoryMaterial.java
rg -n '\b(materialType|materialCategory|materialNameEn|specSupplement|costPriceFrom|costPriceTime|material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)\b' jjx-web/src/types/inventory/material.ts
# 后端候选逐条检查：仅SupplierTypeEnum一行旧表名注释，无material_type列SQL读写
rg -n 'material_type' jjx-server/src/main
# 源码字面14行复查
rg -n '\b(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)\b' jjx-server/src/main jjx-server/src/test jjx-web/src
```

本次命令、退出码、逐行输出和语义复核结论记录于 `/tmp/jjx-007-final-semantic-scan.txt`；此前原始记录仍为 `/tmp/jjx-007-close-repo-grep.txt`。实体六字段=0、前端物料types六字段=0、后端 material_type 列 SQL 读写=0，按派工方裁定通过。原始记录含临时路径，不将备份或其它会话文件带入提交。

新增遗留：inventory_material_category.material_type 列仍存在（R/A），与“类型由分类根派生”重叠，属第二真源，留待后续卡处理。

本卡源文件实际共53个，已包含 `MaterialClassification.java` 与 `Material307DryRunTest.java`；307已应用并以同名原内容归档，仅提交 applied 文件，不创建重复待办迁移。另提交覆盖清单与 history/INDEX.md，整个 backups 目录均不暂存。

## 本次收口预检：字面扫描停止条件

本次用户要求“剩余命中只允许出现在migrations/、applied/、覆盖清单与上述两个历史脚本；任何源码命中都要停下来报告”。提交前按同一条件预检即出现命中，因此暂停暂存、提交、推送和status=2回写，不将该预检称为“提交后复核”。

实际命令与输出摘要（修改本节前扫描）：

```text
rg -n '\b(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)\b' .
RG_RC=0
TOTAL_MATCH_LINES=165
SOURCE_MATCH_LINES=14
OUTSIDE_ALLOWED_FILES=31
```

RG_RC=0表示发现匹配，不表示验收通过。完整原始输出暂存于/tmp/jjx-007-close-repo-grep.txt，未将备份内容复制或暂存入本卡。源码14行的实际位置与语义：

|文件|实际命中行号|语义|
|---|---|---|
|jjx-server/src/main/java/com/jjx/inventory/controller/InventoryMaterialCategoryController.java|52、63、75、84|分类日志业务标识material_category，非主表列|
|jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryMaterialCategoryServiceImpl.java|63、223、255|分类事件业务标识material_category，非主表列|
|jjx-server/src/test/java/com/jjx/inventory/MaterialMasterDataTest.java|404、409、432、442|分类兼容值与已删字段/SQL反例断言|
|jjx-server/src/test/java/com/jjx/inventory/MaterialMasterMysqlTest.java|69|inventory_material_category测试夹具的material_type，分类表仍保留此列|
|jjx-server/src/test/java/com/jjx/sales/service/impl/QuotationAttachmentFinalizeTest.java|188|sales_quotation_item测试夹具的material_type，报价材料描述独立语义|
|jjx-web/src/components/inventory/__tests__/materialMasterFields.spec.ts|129|验证导入不依赖旧类型的反例测试|

此外，字面白名单外还命中既存历史文档、备份、scripts/material-reclassify-dryrun.py注释、jjx-server/jjx-tools/python-tools/meta-material.py局部变量及jjx-docs/sql/material_master_checks.sql历史核查SQL。未擅自扩大排除范围，也未为消除字面命中修改上述文件。已报告派工方，等待明确是否按“无inventory_material已删列有效读写”的语义口径验收；当前状态仍为进行中。
