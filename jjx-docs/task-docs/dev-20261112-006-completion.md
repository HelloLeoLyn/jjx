# 旧预警与材料预占精确需求：交付及验证边界

任务码：dev-20261112-006；执行Codex；用户本轮授权“006你来做”。独立验收待Owner安排，未宣称用户终审或真实业务闭环通过。代码基线dbd44f78；最终提交见sys_task本卡回写。

1）实现：3个Java业务文件与1个Vue业务文件，另5个测试文件、本文/plan/INDEX共3个文档文件。新增BomMaterialDemandService作为两个旧消费者的适配层：原始比例和跳距统一调用既有rawUnitQuantity/ExactQuantity/BomConsumptionCalculator；损耗一次、最低投料保留、已存有据factor转换，同料本订单全部产品/行汇总后按档案单位终点取整。全局按各订单已取整需求累加，与跨订单分摊保持同一边界。材料预占仍调用现有整卷分配器，不改实际整卷占用或实际出库算法。
2）缺依据处理：路径、明细、物料和原因通过calculationIssues返回；已核实行可查看，未知actualGap=null，不返回0冒充齐套，不允许确认确定采购预警。前端显示问题、单位和最多4位小数，不再显示误导的齐套成功。预警写入/全局预警重建/欠交台账/材料预占必须先完整计算，失败保留旧预警及旧预留；预占先校验后释放，防止无事务直接调用也先清占。全局重算或写预占缺依据时抛可读业务异常，既有订单审核主流程仍按原异常隔离，不把业务错误吞为可采购数量。保持预警停扣旧材料预占政策。
3）数据核查：只读账号认证失败，没有使用root替代查询业务数据；只写本卡元数据。改用仓内20261120-2243备份核查，17条投入行与物料档案单位连接均一致，涉及7种物料；旧109—112/351/352档案不存在、BOM1—5为11月20日新建，不能套用旧ID清单。当前明细及单位表见plan。没有订正任何BOM、物料、库存、单据或流水，没有DDL、迁移、服务操作及部署。
4）通过的验证（均退出0）：
A) 隔离POM从正式pom生成，只将输出改到.tmp/006/build并使用真实仓库源码/资源；避免覆盖运行实例jar或IDE target。mvn -o -f .tmp/006/pom.xml -Dtest=BomMaterialDemandServiceTest,LegacyBomUnitCompatibilityTest,LegacyBomConsumerFlowTest,DualUnitBusinessLifecycleTest,InventoryAlertLifecycleTest,InventoryAlertPurchaseSuggestionsTest,ProductionPickPrecisionTest,ProductionSupplementBomDemandTest,ShortageExplosionTest -DargLine="-Xmx512m -Dfile.encoding=UTF-8" test：9类63项，0失败0错误0跳过；Maven堆768M。含公开Service精度、跨订单分摊、全局取整边界、不完整预览、写前拒绝与旧占用保留，以及既有H2真实批次/事务/流水回归。
B) 反例：300×1/6=50、600×1/6×1.03=103、0.08M×3=0.24M、小数跳距3片和0.5%损耗、同料多行与共享子BOM汇总；未知单位转换不按卷长猜系数、缺档案不套历史ID、缺定额不按0、非法原始参数不回退快照、循环不伪造采购量。
C) Vitest：复用已存在.tmp/dev-034-testdeps依赖及.tmp/processing-032-vitest.config.mts（Vue统一别名、真实SFC、测试范围单独限定；正式配置未改），JJX_TEST_FULL=1，JJX_TEST_INCLUDE=src/views/sales/order/components/__tests__/shortageCalculation.spec.ts，1worker/1G；3/3通过。检查问题展示、不完整不得成功/不得确认、完整需求正常确认。初跑缺Vitest及Vue重复实例失败，环境统一后重跑通过；未将初跑当成功。
D) node jjx-web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p jjx-web/tsconfig.json：退出0，3G。
E) 状态枚举门禁新增0处，docs与git diff --check通过；最终结果以本卡任务回写为准。
5）未通过或未验证：
A) 扩展ProductionMakePickTest有3项因materialAllocations替身为空失败；用git archive HEAD独立提取修改前完整jjx-server、独立POM/输出目录重跑同类10项，复现同样3项NullPointerException，其余7通过。证实既有夹具问题，未改断言/生产逻辑压绿，不计入本次63项通过集合。新增测试首次Mockito嵌套stubbing错误已修正并完整重跑63项通过。
B) npm run validate退出1，check:status-enums通过后，check:model-baseline因jjx_ro认证失败退出1，后续动态门禁未运行。执行时临时mysql包装器强制只读账号，未使用脚本默认root查询业务数据。check-stock-summary.sh --strict退出0但输出明确“连不上库，跳过库存对账”，因此五项未核实，不能当五项0。已向用户请求可用只读连接/恢复账号；未擅自改账号或授权。agent-preflight退出1，同样保留数据库检查受阻事实。
C) 真实MySQL事务、登录API、页面运行态、整卷领退料及多层生产库存闭环均未验；本轮组件用例及H2不能替代这些证据。独立验收未做。
6）后续：只读连接恢复后补实时单位/身份清单、表基线及库存严格五查；未确认单位时只能显示具体缺口，不得改历史账或重新解释旧ID。部署与服务生命周期由用户另行安排。代码交付可审阅，状态2只代表待审核。
