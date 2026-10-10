# 规范分色检查表实施交接方案

- 任务：dev-20261010-026
- 日期：2026-10-10
- 状态：方案已交接，业务代码未实施；实施任务保持待处理。
- 本轮授权：用户要求出方案文档，交由其他agent实施。本轮只落本文、索引及任务元数据。
- 参考样张：`jjx-docs/assets/tmp/规范分色检查表.jpg`，接手者必须直接看图；不要继续显示“待样张”。
- 原需求：`jjx-docs/requirements/product-spec-docset-requirements-20260929.md`第2节第5项。
- 相关任务：dev-20261010-020（规范变更与附页）、024（印刷规范）、023（日志/历史表后续优化）。

## 1. 用户已确认，实施不得变更

1. 属于产品电子文档集的“分色检查表”tab，表名“规范分色检查表”。
2. 工程人员录入，暂不强制。未填不能阻断产品、BOM、工艺路线、发货等流程。
3. 复用质量记录模板体系，沿用JJX-QR编号机制，不自建一套模板管理。
4. tab直接展示参考样张的纸张布局，编辑按钮放在纸张外。在线预览与打印共用组件。
5. 四块：规范、面板菲林分色、线路、刀模治具；列为检查项目、自查正确、自查错误（原因）、复查；每块下方保留整行注意事项。
6. 每项自查支持正确、错误、不适用，也可以未检查。选择错误后填写原因；未检查在纸张上留空，不自动打勾。
7. 复查单独填写，不覆盖原自查结论及原因。
8. 用户近期规则：内容变更先复用sys_oper_log，不为各模块或每次变更新增历史表；全局优化另在023讨论。
9. 用户要求测试、编译、类型检查、浏览器验证、重跑及服务启停由用户执行；agent完成代码和源码差异核对即可。提交仅限本任务文件，git push由用户执行。

## 2. 样张检查项目清单

以下稳定键是实施建议，展示名称按样张；第一项及部分注意事项被书脊遮挡，不能宣称完整转录已经确认。

| 分组 | 建议项目键 | 展示名称 |
|---|---|---|
| 规范 | spec.source_documents | 资料（客供资料、工程图纸）【首部遮挡，暂拟，待核对】 |
| 规范 | spec.name_label | 品名标签 |
| 规范 | spec.material_spec | 材料规格 |
| 规范 | spec.customer_internal_notes | 客户要求和内部备注 |
| 规范 | spec.processing_sequence | 加工冲形作业工序 |
| 规范 | spec.print_color_sequence | 印刷色序（客户要求和备注）【左侧遮挡，待核对】 |
| 规范 | spec.artwork_die_dimensions | 产品彩图和刀模尺寸图 |
| 面板菲林分色 | panel.printable_content | 内容（可印刷） |
| 面板菲林分色 | panel.bleed | 出血 |
| 面板菲林分色 | panel.surface_effect | 表面效果 |
| 面板菲林分色 | panel.direction | 方向 |
| 面板菲林分色 | panel.pitch | 跳距 |
| 面板菲林分色 | panel.film_label | 菲林标签（目数和名称） |
| 线路 | circuit.functional_routing | 功能走线 |
| 线路 | circuit.led | LED灯（大小颜色正负极） |
| 线路 | circuit.annotations | 辅助标注 |
| 线路 | circuit.direction | 方向 |
| 线路 | circuit.uv_jumper | UV跳线点 |
| 刀模治具 | tooling.outline_die | 外形刀 |
| 刀模治具 | tooling.spacer_die | 隔片刀 |
| 刀模治具 | tooling.adhesive_die | 背胶刀 |
| 刀模治具 | tooling.emboss_die | 凹凸模 |
| 刀模治具 | tooling.direction | 方向 |
| 刀模治具 | tooling.scale | 缩放 |
| 刀模治具 | tooling.other_die | 垫片保护膜刀等 |

共25项。这些是人工核查项目，不是从BOM/工艺路线推算的检测结果；即使资料存在，也不能自动判断正确。

注意事项应逐行对照样张，暂可辨识内容包括：保护膜缺失和型号、弹片型号、彩图尺寸标示；小图案可印刷性、共版跳距一致、菲林标签修正、菲林规格200mm起预缩；上下线导通时上线反印、侧边LED灯盘正负极方向、UV跳线点大小；过山洞/U形槽穿线与防水结构背胶U形槽大小。以上是内容摘要，不可当作已核准的工艺标准或自行补全文字。模糊部分可先留待核定，不阻塞其余界面实施。

## 3. 录入与打印建议（实现细节，区别于用户已拍板项）

- 页顶显示产品编码、产品名称及对应模板信息；沿用文档集页脚，避免破坏四块网格布局。
- 编辑弹窗/抽屉按四块分区，每项自查单选，选择错误才展开原因输入；无“一键全部正确”，不预填结论。
- 建议错误原因保存时非空校验；其他未检查项可以空着保存。文本长度建议每项1000字，前后端一致。
- 复查建议为独立结论（未复查/通过/需修改/不适用）及选填说明；打印在“复查”列，保留原自查原因。
- 未检查打印空白；正确在自查正确列打勾；错误在原因列打印文字；不适用明确打印“不适用”，不能与未检查混同。不要额外增加破坏样张版式的第五列。
- 自查人/时间、复查人/时间由服务器记实际修改者和时间。当前用户不能客户端伪造历史操作人。保存其他行时不得重置未变行的人员/时间。
- 建议自查修改后将该项复查标为需重新复查，并将旧值留在公共变更日志；此规则和是否必须不同人复查属于待确认业务细节，不得擅自引入强制审批或岗位隔离。
- 未保存关闭/离开提醒，提供保存和关闭。请求失败保留草稿，禁止重复提交；无修改不产生伪变更记录。
- 同一纸张组件用于tab、文档集预览和独立打印。无填写也可预览空白检查表，是否打印仍由用户选择分色检查表章节。
- 长原因/复查说明不得裁掉；可复用实际DOM溢出附页机制，或按真实高度续页。短内容不要产生空白附页。
- 现有作为附件上传的分色检查表不可删除或丢失；建议电子表为主，旧附件可在预览目录单独选择，标清附件，防止默认重复打印。

## 4. 数据和接口方向：优先复用，不直接新增表

当前现状（2026-10-10源码核查）：

- `product-spec/index.vue`的color tab仍为el-empty占位；`docset.ts`的color章节只展示上传附件，没有结构化检查记录。
- `quality_template_registry`为模板元数据，含recordNo、version、printComponent、printMode、二维码配置；`quality_template_print_log`为打印事件。两者不是已经实现的通用检查结果明细表，不能把产品填写结果塞进模板remark。
- 已有`product.work_spec_json`存工程规范附加内容，并由ProductWorkSpecService执行行锁+revision校验。
- 已只读查询模板库：record_name含“分色”或“规范…检查”未返回匹配行。此结果不等于绝对不存在其他名称的对应模板；接手者应按名称、编号、附件进一步核查。

建议落地：

1. 先核查现有通用质量记录实例机制；若没有合适实例存储，复用`product.work_spec_json.colorCheck`保存当前产品检查结果，不加表、不加列。
2. colorCheck建议包含模板引用与模板版次、以稳定项目键为索引的条目；每项分别有selfResult、selfReason、reviewResult、reviewNote及各自操作人/时间。不用数组下标关联历史。
3. 可保存检查时引用的BOM/工艺路线版本及已发布图纸ID，便于提示资料已变化；不要因此自动标为检查合格，也不等同于已实现正式发行冻结。变化后如何作废/提醒需后续确认。
4. 新增专用保存入口，例如PUT /product/{id}/work-spec/color-check，只接收检查表白名单字段；服务器保留printRemarks、凹凸条件、结构图、变更说明等其他字段。
5. 同时修改现有通用规范save的保留规则：目前只显式保留printRemarks，新加colorCheck后也必须保留，避免作业规范另一个入口保存时删掉检查结果。通用DTO不能无意覆盖专用字段。
6. JSON字段共享revision，沿用行锁和乐观并发校验；后写冲突不能覆盖。接口错误保留前端输入供用户处理。
7. 检查结果枚举先搜索复用，缺失时在`jjx-web/src/enums/`及后端对应枚举定义，禁止页面自建状态映射/散落数字。模板状态复用QualityTemplateStatusEnum。
8. 保存实际差异到sys_oper_log：产品ID、分组/项目键、动作、操作人、时间、前后值；复用OperLogDetailBuilder，可借鉴024备注同事务日志写入方式，不建检查表专用历史表。日志90天默认清理问题已登记023，不承诺永久留存，不擅改全局清理策略。
9. 权限先核查现有工程/产品/模板权限。建议普通录入复用product:edit；复查是否独立权限、是否不同人尚未拍板。不能为了实现检查表自动扩大角色权限。

## 5. 模板编号与打印接入

- 不编造JJX-QR编号。先匹配已有模板，未找到再按实际编号规则核定；元数据须按编号幂等注册，不重复新增。
- 模板名称为规范分色检查表；版次、保存年限、二维码规则应沿用公司模板实际配置，不能从空白样张猜填。
- 模板元数据注册与前端真实打印组件/路由一起接入。现有qualityTemplate API含createQualityTemplatePrintLog；接手者需核查调用时机和权限，预览/重新渲染不能重复记打印日志。
- 浏览器打开打印对话框不能证明纸张实际打印成功；记录应保持既有系统的打印事件含义，不虚构完成状态。
- 原需求提到归档，但本次核查未证明已有通用完整内容快照归档能力，不得仅凭模板注册/打印事件宣称已实现历史重印。

## 6. 接手入口与建议步骤

重点源码：

- `jjx-web/src/views/engineering/product-spec/index.vue`
- `jjx-web/src/views/engineering/product-spec/components/docset.ts`
- `jjx-web/src/views/engineering/product-spec/components/ProductSpecSheets.vue`
- `jjx-web/src/views/engineering/product-spec/components/ProductSpecPreview.vue`
- `jjx-web/src/views/engineering/product-spec/components/useWorkSpecOverflow.ts`
- `jjx-web/src/views/engineering/product-spec/components/ProductPrintSpecPanel.vue`（整组编辑/历史/未保存提醒参考）
- `jjx-web/src/api/product/workSpec.ts`
- `jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecService.java`
- `jjx-server/src/main/java/com/jjx/product/controller/ProductWorkSpecController.java`
- `jjx-server/src/main/java/com/jjx/production/domain/entity/QualityTemplateRegistry.java`
- `jjx-server/src/main/java/com/jjx/production/service/impl/QualityTemplateRegistryServiceImpl.java`
- `jjx-web/src/api/production/qualityTemplate.ts`
- `jjx-web/src/enums/production/QualityTemplateEnum.ts`

顺序：读AGENTS与本方案、核对已有WIP → 查看参考图及模板能力 → 先做纸张与工程编辑 → 接专用保存和日志 → 接入文档集/模板打印 → 源码差异核对 → 仅提交本任务文件、任务转待审核，不推送。无需重新讨论已确认的四块布局与自查选项；只对未确定的业务规则询问用户。

## 7. 留给用户的验收清单（本轮未执行）

- 页面四块项目及比例与样张对应；25项无遗漏，注意事项不猜写。
- 未填可保存、可打印空白，不阻断其他流程；正确/错误/不适用/未检查显示不同。
- 错误原因和复查分别保存，复查不会覆盖原自查，重新打开数据一致。
- 独立修改一个项目仅记录真实差异；无修改不写伪日志，权限和并发冲突有效。
- 保存检查表不丢整组印刷备注、作业规范内容；反向保存其他规范也不丢检查表。
- 主tab、文档集、独立打印使用同一版式；长内容完整、无空白附页；老附件仍可用。
- 模板编号及版次正确，预览不刷打印日志，未实现的正式版本冻结/永久历史不冒充完成。

## 8. 本轮交付边界

仅方案文档、history索引和sys_task任务登记；未修改业务代码、表结构、模板配置和产品数据，未运行测试/编译/服务操作。原有WIP（模板文件、备份改动、components.d.ts、截图目录）不属于本任务。后续实施者沿用dev-20261010-026，并按实际实施范围补齐文件白名单和任务描述。
