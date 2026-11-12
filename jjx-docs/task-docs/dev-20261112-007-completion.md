# BOM明细精简：正式页面接入与验证范围

任务码：dev-20261112-007。用户“7）接入吧”已确认阶段1效果并授权正式接入（固定7已定）。Codex负责页面与整合、Hermes按OpenClaw卡实现只读后端，OpenClaw独立复核。代码提交待审核，不代用户置10。

## 最终行为

1）产品BOM的BomItemEditor与制造BOM的ManufacturingBomItemTable都接入BomEntryTools。日常填写模数、基数、损耗率；选定物料的规格和单位只读，保留物料搜索/重新选择，未建档快速草稿仍可按既有流程录入名称、规格与单位。
2）每件净用量、按试算数量的本行总需求通过后端返回，不在浏览器重写耗用公式；不把已舍入0.1667反乘整批。原前端数量重算从编辑器/导入构造中移除，保存仍传原始比例由原保存接口重算，不用试算响应覆盖quantity/appliedQty/actualIssueQty。
3）最低投料与既有跨单位依据放高级设置；折叠不清值、非零最低投料有提示。米数/参考卷数默认隐藏，选择显示；试算数量、显示开关和参考卷长不落BOM、不占供应、不改变库存。
4）连续计量投入、具备有效档案宽度且产出为PCS时提供跳距辅助能力，不以物料形态猜卷材、不硬编码106/107，不限制其它材料手工模数基数。已有跳距保留78/80等原值；新方案可按结构化产出lengthMm预填。调用004已有GET接口，确认采用后才回填原始模数、基数、split，损耗仍保留行值。不可按现有4位精度直接保存时禁采用，不静默舍入。
5）只读/租约丢失禁用业务输入和定额采用；原保存/审批/制造关联/版本与018编辑占用保护保留。试算失败保留草稿、清空旧结果并可重试；旧异步成功/失败、关闭后迟到结果均不回写草稿。
6）现有旧单位不在加载时改写。新选物料带档案单位；物料或计量变化时清掉需重新确认的比例及最低投料，避免旧PCS比例变成M用量；同料号计量调整保留其原制造方案关联，换物料才清旧制造引用和跳距。不以可整改数据作为功能开发阻塞，本轮没有执行档案/BOM/库存数据订正。
7）原分组、工序项目、快速录入、导入、复制/移除/撤销移除、审核状态、下层方案、主方案、取消草稿与打印入口保留。打印本来按原始规格/模数生成，未改成依赖浏览器数量快照；真实打印/实际业务单据流程仍需部署后验证。

## 接口和唯一计算来源

新增POST /production/bom/consumption/preview，权限engineering:bom:view/add/edit任一，Service只读事务，只批量读取物料档案，不写BOM/库存/供应。payload为outputQuantity/outputUnit及rows（稳定key、materialId、unit、原始比例/损耗/最低投料/参考卷长）；Result.data返回readOnly与逐行结果。
每件净耗复用BomConsumptionCalculator.theoretical，批量含损耗与下限复用appliedDemand，终点精度复用roundStock。整数/有理数保留exactText。米数/卷数只作展示参考，从最终库存单位精度的总量精确换算，卷数在米数展示舍入前向上取整；它们不是实际领料或可用卷数量。
有物料ID的尺寸只读实际档案；不接受客户端尺寸做权威依据。旧row.unit优先并与archiveUnit分开；行级错误隔离，不把失败当0。全局数量/key/行数超限拒绝，最多500行。未填比例仍给出独立的跳距辅助能力，支持先用辅助生成定额。极端指数/参考卷长提前限制，错误不展开巨型十进制字符串。
跳距复用GET /production/bom/pitch-requirement/preview（004）；无新表/持久字段，无生产计算口径修改。正式代码不读取阶段1review-data.json，任意正常输入实时请求同一后端，不限于演示组合。

## 实现与协作

Hermes后端分块提交a3cf16ee：DTO/VO/Controller/Service及13项测试，只写其白名单。随后Codex串行接续该新增Service/test，补新草稿辅助入口、参考卷取整及指数防护；没有并行抢写。
前端API为api/production/bomConsumption.ts，复用组件components/bom/BomEntryTools.vue；两处既有明细组件及父级BomFormDialog/ManufacturingBomDialog只做接入与必要产出单位/长度上下文。没有修改既有Calculator、库存写入、审核/版本状态机、Redis租约实现或数据模型。

## 作者验证与证据

1）后端BomConsumptionPreviewServiceTest 16项、PitchRequirementCalculatorTest 14项，共30项0失败0错误。真实计算器+只读mapper替身；单测不是实际MySQL/HTTP权限验证。日志.tmp/bom-007-live/backend-final.log及surefire XML。
2）前端9个定向spec共97项，通过原始比例、不同产出数量、仅展示不覆写BOM快照、异步迟到保护、失败不回退旧结果、明确采用、不可保存比例、只读、缺比例辅助、新旧计量、制造方案/保存/租约/导入回归。日志.tmp/bom-007-live/frontend-final.log。首次因新增slot隔离夹具未承接及旧断言要求前端算quantity出现7失败；补slot承接和按“后端权威、前端保留快照”更新行为断言后通过，没有改生产规则掩盖失败。
3）正式组件浏览器：复用现有3000，真实挂载两个正式Vue组件/ElementPlus/Pinia。网络拦截只返回当前新Java Service/Calculator生成的测试响应；无实际BOM/库存写入。通过300/6=50、跳距明确采用、300片4.6800㎡/24.0000m、只读禁采用，页面异常0。日志/脚本.tmp/bom-007-live/browser-official.log / .cjs，截图official-product.png、official-manufacturing.png。不得宣称运行中后端已支持新接口或已完整登录保存验收。
4）vue-tsc、check:status-enums（存量105，新增0）、check:docs、git diff --check按最终提交记录执行。无状态基线扩大；未改库存写入路径，不拿库存五项零作本功能闭环证据。

## 未验与交付边界

新后端尚未部署；未启停/重启任何服务。运行中的真实接口鉴权/数据库读取、完整ERP登录→编辑→保存→重新打开、实际打印与生产领料闭环未验，固定2真实验收环境/数据范围仍待定。测试拦截与替身证据不替代真实验收。
普通push仍被既有两表model-baseline门禁拦截，按实际结果回写；不删表、不改baseline、不绕钩子。无DDL、业务数据订正、库存流水或余额写入，只有本任务回写。
阶段1已确认的UI口径不重复询问。019继续延期，006旧单位/旧预警和供需确认/采购工单联动不在本卡顺手实施。


## 独立复核与兼容修复

OpenClaw 对 9163afff 独立重跑后端30、前端97、vue-tsc、docs/status/diff及浏览器组件检查全部通过，但发现一项必修：产品BOM历史仅有quantity、缺模数/基数的行，修改损耗或最低投料后quantityStale=true，被新校验误拦保存。原任务2→3→1退回Codex处理。
修复仅限制缺比例且无有效旧用量的草稿；历史正用量继续原后端legacy路径，不在前端推算比例。新增损耗/最低投料/同物料同单位重新选择三种回归；新空定额与同料号换单位清用量后仍阻止保存。制造BOM原有强制模基与stale剔除快照在本轮之前已存在，查48d708a3确认，本修复没有改变其既定保存规则。
修复后作者重跑9spec共101项、vue-tsc、check:docs、check:status-enums、diff均exit0；后端/浏览器未变，不重复执行已独立通过部分。日志frontend-compat-final.log、vue-tsc-compat.log。首次新增测试对不存在字段与显式undefined作对象匹配出现2个断言失败，改为逐字段值断言后通过，未放宽业务行为。修复提交后交OpenClaw仅复核该差异与18项编辑器测试。
