# 物料主数据阶段1—5代码与迁移准备交付

任务码：dev-20261024-001；执行者：Codex；日期：2026-10-24。

## 交付边界

本记录只证明阶段1—5的代码、自查与迁移准备。正式库迁移、人工核对的数据迁移、HTTP/页面运行态和独立验收由dev-20261024-002承接，当前未完成整项改造上线。

初始基线为三个已有改动文件：`jjx-web/src/types/inventory/material.ts`、`jjx-web/src/views/inventory/material/config.ts`、`jjx-web/src/views/inventory/material/index.vue`；用户在本会话明确允许“接着现有改动实施”，已在这些改动基础上整合新模型。

## 实现

1）分类与字典：三个根节点RAW/SEMI/FINISHED；原材料下基材/辅材/油墨；总19节点、16末级模板。复用sys_dict/sys_dict_item的dict_code，材质、形态、颜色、单位和品牌分开。
2）属性：`inventory_attribute_template`按category_id唯一绑定；物料不重复存template_id。JSON属性支持文本、整数、小数、布尔、单选、多选、单位、范围、精度、默认值、必填及条件显示；服务端拒绝未知键、错误类型、越界值和不适用字段。已引用模板不允许直接改结构或停用；未引用模板可移除后扩展分类。
3）通用字段：新增original_name/original_specification/material_meta/material_form/attribute_json/is_customer_supplied/is_sample。只保留size尺寸入口；厚度mm和克重g/m²分别存数值属性；material_grade只生成显示。客户归属与实际打样用途不由档案标记代替。
4）保存：原名称作为业务显示默认，原规格独立保留；规格由属性生成；分类根节点派生类型；物料、属性、标签事务保存，失败不半写。已有流水不允许直接换基本单位。编辑保留物料ID、编码以及未提供的库存/采购/保质期参数。旧保存和旧规格端点不能覆盖新模型物料。
5）界面：统一MaterialFormDialog适配器接新表单，承接物料列表、BOM、打样、库存导入等已有建档入口，保留presetData和success接口；列表/详情显示来源与标准字段、分类路径和属性。品类请求有会话序号保护；失败保留草稿；新增/编辑只调用一个保存端点。
6）Excel：按品类下载模板、候选下拉、来源列、固定单位属性、模板版本；整表预检后才写入，写入失败整表回滚；可填写现有物料ID进行人工确认后的迁移，另要求编辑权限。导出新模型遵循筛选；待归类另有只读导出和audit接口。
7）迁移：303结构、304种子；只做预览，正式账本仍至302。模板表新增批准记录和清理保留归属已同步。未删除旧表、未批量修改旧物料、未启停服务。旧备注不自动清空，人工核对阶段处理仍含来源说明的备注。

## 自查与证据

1）后端：`mvn -o -Dtest=MaterialMasterMysqlTest,MaterialMasterSchemaTest,MaterialMasterDataTest,MaterialSpecComposeTest -DargLine=-Xmx512m test`对应验证范围共40项，39通过、1项既有跳过，0失败、0错误。最终使用基于原pom的临时副本，改变源码/资源的绝对路径与输出目录到`.tmp/dev-20261024-001-java-build`；因运行JAR锁定，不清理或替换运行JAR，不启停进程。Maven heap768MiB，测试heap512MiB。
2）真实MySQL会话临时表6项：默认值及根类型派生、属性落库、显式清空、保存后异常回滚、引用模板结构保护、单位变更保护、Excel预检和第二行写入失败全表回滚、按ID迁移保留编码和业务参数、SQL种子幂等。测试临时表在连接关闭时消失，不写持久业务行。MySQL临时表不支持外键且禁止同语句重复打开同一临时表，种子测试仅把读别名改到语句前的临时快照；正式外键仍未验证。
3）模板/来源单测5项：16份实际SQL模板合法；品牌不作材质，元件类型独立；类型、数值、多选、条件属性；尺寸职责；明确原资料标记优先，原规格为空时不拿生成规格补入。
4）前端Vitest2文件6项通过：切品类清旧属性；未知布尔不补false；false保留；条件隐藏字段清理；预览使用标签和单位；一次保存、失败保留、异步旧请求不覆盖。使用工作区已有Vitest5依赖与独立配置，Windows Job Object限制进程树1GiB，1个并发worker，单测超时15秒。
5）`vue-tsc --noEmit --composite false --incremental false`退出0；`vite build`退出0。标准`npm run validate`运行至最后vue-tsc之前的全部门禁通过，最后因`src/components/product/ProductFilePreview.vue`引用`productFilePreview.ts`触发TS6307而退出1；这些产品预览文件未在本任务修改，未扩大基线或修改tsconfig掩盖错误。
6）状态枚举、文档、结果大小写、模型基线、迁移排序规则、NOT NULL、库存、lot、IQC谱系、质量台账、单号检查通过；库存五项均0。模型基线会提示模板表已批准但尚未迁移，符合当前状态。
7）`db-migrate.sh --all`只读预览：待执行303/304两个文件、待归档0；未执行SQL、未记账、未归档。

## 未完成项与交接

dev-20261024-002等待用户手工备份，再执行实际迁移、外键与唯一约束检查、新版本接口/页面验收和人工确认的数据迁移。未声称已上线、正式库已落定或通过独立验收。完整validate的TS6307需按独立授权处理，当前不扩大物料任务范围。
