# 本地 ERP 多层 BOM、多产出工序排查及离型纸裁切建模建议

任务码：dev-20261110-011。检查日期：2026-11-10，Asia/Shanghai；主要业务接口证据时间 16:12–16:19。执行者：Codex；状态：执行者排查完成，待 Owner 独立审核。

本文区分真实库查询、运行接口实测、源码核查和建议。未把创建成功等同于功能接通，未把代码检查等同于真实库存过账验证。

## 结论

**本地系统当前更接近“单份 BOM 平铺领料 + BOM 内分组/父子行 + 单工单单产品产出”，尚不能按目标模式运行“独立半成品 BOM 递归展开 + 一道工序多物料产出”。** 它也不能准确称为“单层 BOM + 虚拟件穿透”：没有查到虚拟件标志或自动穿透机制，实测自制行被领料预览跳过，未展开子 BOM。

存在半成品分类、通用库存物品/批次/成本字段、工序项目关联、损耗和不良报工能力，但不等于中间件制造、联产成本和上下层库存闭环已经接通。仅增加仓库、配置菜单或把规格改为“宽×长”，不能补齐这些能力。

对用户指定的 `YL1025PWOO-BOM / AUX000107`，更直接的问题是：**采购库存按米管理的 195mm 宽卷料，在 BOM 中被写成 1 PCS；80mm 则另记在跳距文本中。** 应先区分来料规格、裁切工艺尺寸和实际耗用单位，再决定是否需要独立半成品，而不是先把原料档案的规格改成 195mm×80mm。

## 检查环境与验证边界

1）目标库：本机 `jjx_erp_db`。结构和事实查询使用只读账号；任务登记/状态回写另走仓库既有脚本。
2）本机前端 `http://127.0.0.1:3000/` 直连 HTTP 200；后端 `http://127.0.0.1:8080` 可登录、查询和处理测试业务请求。系统代理曾返回 503，改为绕过代理后正常；该 503 不作为 ERP 故障结论。
3）使用仓库 README 的本地开发账号正常登录，不改密码、不从 Redis 取他人会话。报告不保存密码或 token。
4）运行态依据来自带登录鉴权的业务 API、运行实例 `/v3/api-docs` 和真实库。源码为辅助证据；审查中其他会话提交了 `efade819`（BOM 规格按档案），部分源码与运行 OpenAPI 字段存在差异，故运行功能以实测为准。本任务没有改业务源码、重启或停止服务。
5）没有现成工单链，因此经现有 API 追加带 `[TST] dev-20261110-011` 标记的独立测试档案和单据。**没有直接 UPDATE 库存余额、构造库存流水或绕过作业规范/质量 gate。** 库存、报工、真实多产出过账与成本分摊未跑通，不能报告为已验证。
6）未拍摄浏览器截图；下文给出实际请求、响应、主键、SQL 和菜单路径，作为本次操作记录。HTTP 200 不是业务成功，另核对 Result.code。

## 测试前真实数据快照

| 对象 | 数量/实际实例 |
|---|---|
| 产品 | 1，product_id=1，YL1025PWOO / YL-208NDW |
| 物料 | 288；半成品根分类 SEMI 下物料 0；成品物料分类下 0 |
| BOM | 2，均属于产品 1：bom_id=1/V1.0 已批准且当前；bom_id=2/V2.0 草稿且非当前 |
| BOM 明细 | 10；BOM 1、2 各 5 行，全部 source_type=buy，物料 109–113 全在 RAW 分类树下 |
| 工艺路线 | 1，routing_id=1，YL1025PWOO-ROUTING，已批准且当前 |
| 工单/执行/任务/报工 | production_order、production_operation_execution、production_task、production_work_report 均 0 |
| 入库/出库/库存/流水 | inventory_inbound_order、inventory_outbound_order、inventory_stock、inventory_stock_item、inventory_transaction 均 0 |
| 仓库/库位 | warehouse 1=成品仓 finished、2=原料仓 normal，数据库 status 均 1；storage_location 共 0 条 |
| 作业规范 | 产品 1 有 V1.0/PUBLISHED；本次新建产品 2、3 没有发布版本 |

以上为检查起点，不能用本次造出的测试记录倒推“原来有生产实例”。只读查询未读取人事档案或客户联系方式。

## 目标七项逐项结论

| 项目 | 结论 | 已核实依据与限制 |
|---|---|---|
| 多层 BOM 嵌套 | **部分支持树形录入；目标跨 BOM/MRP 不支持** | engineering_bom.product_id 关联产品；engineering_bom_item.parent_material_id 注释指向本表 item_id，仅表达同 BOM 内父子行；engineering_bom.parent_bom_id 是父版本，不是半成品的生产 BOM。原有 item 3→2、8→7 都是原材料行嵌套。没有现成“成品仅半成品、各半成品自有 BOM”实例。测试顶层 BOM 4 仅含半成品349，自制 make；领料预览返回 []，未展开 BOM 3 的原料109。运行 BOM 明细契约无 childBomId。 |
| 半成品物料类型 | **部分支持** | sys_dict_item.inventory_material_type 有 S=半成品；分类14/SEMI 有启用模板，API实际建立349、350。inventory_item 支持 MATERIAL/PRODUCT，批次表支持 unit_cost，物料表有 cost_price/standard_price，可承载独立库存与手定成本；未验证半成品制造成本自动结转。master API提交 productId=3 后回读仍为 null，测试半成品与产品BOM身份未接通。未发现 phantom/virtual 专用字段，名称/备注检索“虚拟”0条；不能据此判断是否有人业务上把中间态当作不入库材料，需逐件确认。 |
| 一道工序多产出 | **不支持** | engineering_routing_item 没有产出物料明细/分摊比例，production_order 只有一个 product_id，execution/report 只有标量合格/不良数量。数据库和运行 OpenAPI未找到联产品或工序多产出明细。路线2提交两个 outputs 后虽然 Result.code=200，回读和表内均无该明细，custom_process_params=NULL。没有真实多产出工单，也没有可录入联产数量和成本比例的现成合同。 |
| 工序与材料关联 | **部分支持** | BOM明细有 process_id/process_name，可标记投入归属；route 有工序先后/组合/前置依赖。生产报工 WorkReportSubmitDTO 接收 executionId/taskId/qualifiedQuantity/defectiveQuantity，没有投入、产出物料数组。源码报工审批重算 execution 投影、推进任务和质量检验；库存入/出通过独立服务，不是每道报工自动扣料增多物料。未查到报工/执行表库存自动触发器；本次无有效报工，实际报工与库存流水对应关系未能验证。 |
| 领料单分层隔离 | **不支持目标自动分层；有整单/部分/补料能力** | outbound_order.source_id 关联工单，outbound_item.source_bom_item_id 能追需求行，但没有 BOM 层级或 execution_id/task_id 隔离键。previewPick/createFromProduction 按同一 bom_id 取全部明细，仅处理 source_type=buy；GROUP 只按连续项目分组展示，并按项目名匹配 SEMI 档案，未生成子工单或分层领料。现有领料单0条，不能宣称已发现实际混领；若把半成品也标buy，同单原料和半成品没有层级排他机制，存在混领风险（源码推断，未造混领实单）。 |
| 半成品库存与库位 | **部分支持基础设施；目标自动周转未接通** | warehouse/location/stock_item/transaction 均可承载物料与库位，material.default_warehouse_id/default_location_id 可指定默认值。但当前没有半成品仓和任何库位，无半成品出入库事实。createFromProduction 的普通完工入库只建立一个 PRODUCT 身份产出，material_id 留空，取第一个启用仓，不是工序生成多个 MATERIAL 半成品到指定库位。半成品349不会因为产品3完工自动变成同一库存身份。 |
| 损耗与废料 | **部分支持** | BOM有 loss_rate、module_qty/base_qty、min_issue_qty、applied_qty/actual_issue_qty；报工有 defective_quantity/defect_reason；质量报废单 quality_scrap_order 有 quantity、reason、loss_material/loss_labor/loss_total。模块能表达预计损耗、不良数量和质量报废损失；没有查到边角料专用物料类型、报工废料产出物料/重量/面积明细或联产回收成本分摊。当前报工及质量批为0，废料实际登记/入库未验证；不良件数量不能替代边角料物料库存。 |

“部分支持”只针对表中列出的子能力，不代表对应目标整项验收通过。

## 最小测试构造与真实结果

测试投入取自本地真实档案 `109 / RM000105 / PC-800 茶色 单面加硬`，库存单位 M；裁切工序取本地 `21 / 面板裁切 / CUTTING`。未使用用户给出的虚构数量示例。试验只使用最小数量1和50%/50%的负向产出探针；这些是本次自建测试参数，不是系统已有产量/成本事实。

| 测试对象 | 实际 ID/编号 | 目的与状态 |
|---|---|---|
| 测试成品 | product 2 / TST261110011-FINISHED | 全新开发中档案，不修改产品1 |
| 中间件产品身份 | product 3 / TST261110011-SEMI_PRODUCT | 为测试下层BOM提供现有产品端归属；与半成品物料349是两个不同身份 |
| 半成品A | material 349 / SFG000345 | category_id=14，PCS；提交productId=3未持久化，product_id=NULL |
| 联产片B | material 350 / SFG000346 | 第二个测试半成品，作为同工序第二产出探针 |
| 下层BOM | bom 3 / TST261110011-SEMI-BOM | product3消耗原材料109，1 M；已按正常流程行审核和批准 |
| 顶层BOM | bom 4 / TST261110011-TOP-BOM | product2仅消耗半成品349，1 PCS，source_type=make；已批准；childBomId=3输入被忽略 |
| 联产路线测试 | routing 2，detail 12 / 面板裁切 | 两个outputs输入未保存；路线本身可正常提交/批准，不能据此称联产可用 |
| 顶层测试工单 | order 1 / TST261110011-TOP | 手工创建，绑定BOM4；停在待审核，work_spec_version_id=NULL |
| 下层测试工单 | order 2 / TST261110011-SUB | 手工创建，绑定BOM3/route2；不是系统自动展开生成；同样停在待审核 |

两份BOM均已建立，但**未形成系统可识别的跨层链接**；一道裁切工序已建立，但**未形成有效的多产出配置**。这是此次构造验证发现的失败，不能把两个孤立BOM/备注中的两个产出当成目标模式已经实现。

实际业务接口记录（均带正常鉴权，HTTP200内另检查code）：

| 时间 | 请求 | 实际结果 |
|---|---|---|
| 16:16:39 | POST /product，分别创建测试成品和中间件产品 | code200，返回2、3 |
| 16:16:39 | POST /inventory/material/master，category14且不填形态/材质 | code500，“材料显示名不能为空（半成品 必填）”；无成功档案 |
| 16:17:01 | 补入真实字典形态FILM和technical_spec再次新增 | code200，返回349；提交productId=3，响应productId=null |
| 16:17:56 | POST /engineering/bom，先下层再顶层 | code200，返回3、4；顶层item13未返回childBomId |
| 16:17:56 | POST /engineering/routings，items[0].outputs含349/350及数量、costRatio | code200，返回route2/detail12；回读无outputs，表内custom_process_params=NULL |
| 16:18:33 | submit→item/review APPROVE→approve，BOM3、4；路线submit/approve | 均code200；只变更本次测试单据 |
| 16:18:53 | 创建工单，未传traceId但传了orderNo | code400，“订单编号不能为空”；运行DTO把此校验标在traceId上；按契约补入UUID后继续，不将此当多层模式阻塞 |
| 16:19:20 | POST /production/order，顶层/下层分别手工创建 | code200，返回order1、2；均无parent_order_id |
| 16:19:20 | GET /inventory/outbound/pick-preview/1 | code200，data=[]；未领349，也未展开109 |
| 16:19:20 | GET /inventory/outbound/pick-preview/2 | code200，一行109；demand=1、available=0、qtyPick=0、insufficient=true |
| 16:19:20 | POST /inventory/outbound/create-from-production/1、/2 | code500，“库存不足，无法自动领料（请先入库或到生产领料页手工领料）”；出库单数回查仍0。顶层提示不能掩盖它没有可领buy行的事实 |
| 16:19:20 | PUT /production/order/status，草稿→待审核 | code200；仅本次两个测试工单 |
| 16:19:20 | 待审核→已审核/下达 | code500，“该产品无已发布的作业规范版本，请先在产品作业规范页发布版本后再下达” |
| 16:19:20 | PUT /production/order/1/start、/2/start | code500，“工单状态不允许启动”；未绕过下达条件 |
| 16:19:21 | GET /production/operation-execution/list?orderId=2 | code200，data=[]；无执行/任务可以合法报工 |

结束回查：production_work_report=0、production_operation_execution=0、inventory_stock_item=0、inventory_inbound_order=0、inventory_outbound_order=0、inventory_transaction=0。未伪造报工、质检通过或成品库存。

## 从下达到入库的操作路径与卡点

1）工程管理→BOM管理/工艺路线：建立并审核本次BOM3、4及路线2。**目标模型首先卡在这里**：半成品物料与其生产BOM无可用关联，多产出明细保存丢失。
2）产品→产品作业规范：发布版本；生产管理→生产订单：创建工单、提交审核、下达并绑定发布版本。**本次实际卡在下达**：测试产品2/3无发布版本。没有替测试资料伪造发布文件或绕过业务gate。
3）生产订单→领料预览/生成领料→库存管理→出库作业→审核、确认出库。实际先独立调用预览/建单接口诊断需求：上层make行无需求；下层109缺库存。**缺料阻塞独立于下达阻塞，不能混成一个问题。** 正式运行应先具备合法来源原料库存。
4）生产管理→工序执行→任务分配→报工→报工审批：当前测试无执行/任务，未提交虚假报工。源码支持单工序数量报工、不良和工时，不支持多物料产出行及联产分摊。
5）末道完成及质量检验→工单完工→库存管理→入库作业→确认过账。源码存在FQC/数量/工序 gate 及工单完工生成入库；普通产出为工单单一PRODUCT身份，并非349/350两条MATERIAL半成品。**本次没有到达并验证此段**，也没有验证真实库事务回滚或自动成本结转。

前置作业规范发布、合法库存和仓位配置补齐后，可以继续验证现有单产品闭环；它们不会补出多层关联或多产出明细。

## AUX000107 离型纸实例：为什么不应只改规格显示

真实查询结果：

| 对象 | 来自本地系统的事实 |
|---|---|
| 物料档案 | material_id=111，material_code=AUX000107，离型纸 RP-W195 |
| 来料规格 | specification=195mm，width_mm=195，length_mm=NULL，material_form=PAPER，unit=M；unit_alt、unit_conv均NULL |
| 当前BOM | YL1025PWOO-BOM / V1.0 / bom1 / item3，parent_material_id=2（3M 9080行），process_id=20 / 面板背胶 |
| BOM记录 | specification=195mm，split=80mm，unit=PCS，quantity/base_qty/module_qty均1；actual_issue_qty=1、loss_rate=0；行width_mm/length_mm均0 |
| 草稿版本 | V2.0 / bom2 / item8，同样195mm、80mm、PCS、用量1 |

**已证实的是原料按M管理、BOM按PCS表达以及跳距另记文本；未证实实际排版每跳出几片、有没有间隙、裁后是否备库存、实际发料是否整米进位。** 因没有实际领料流水，不能声称已经发生某个倍数的错误扣库。

195mm 表示卷宽，不是尺寸信息不完整的成品片。80mm 表示工艺推进节距；只有确认没有额外间隙且净裁切长度就是80mm时，才可同时称裁切尺寸为195mm×80mm。跳距可能包含间隙/定位/余边，不能在所有材料上无条件当净长度。

建议把业务信息分开，保留它们之间的计算关系：

| 信息 | 建议归属/含义 |
|---|---|
| 原料规格 | 物料主档：RP-W195，卷宽195mm，库存基本单位M；不把该原料档案改成195mm×80mm片料 |
| 裁切尺寸 | 具体工序/工艺版本：所需毛坯宽、长；195mm×80mm需由实际排版确认 |
| 跳距与排版 | 裁切工艺参数：推进节距、每跳产出片数、边余/起尾料、是否另计损耗 |
| BOM耗用 | 用库存基本单位表达原料消耗；前端可另显示片数、工艺裁切尺寸和换算依据，后端保持唯一计算口径 |
| 半成品规格 | 只有裁后需要独立盘点、批次、库存/成本或跨工单转移时，才另建PCS半成品档案与独立生产BOM |

若宽度方向不再分切、每跳1片、实际推进80mm、不另加间隙或损耗，则理论耗用为 `80÷1000=0.08 M/片`；更一般是 `跳距(mm)÷1000÷每跳产出片数`，再按经确认的损耗和整批起尾料计算。**此为带前提的建议算例，不是已核实的真实工艺定额。** 起尾料属整批固定消耗，不宜重复计入每片损耗；能回收的边角料也不能与不良件数量混为一谈。

还需检查发料精度：InventoryOutboundServiceImpl.batchDemand 当前对整批需求执行 `setScale(0, UP)`，再取最低投料量下限。因此只把BOM数量改为0.08、单位改M，对于生产1片仍会算到1个整数单位；必须同时明确库存单位的最小发料粒度、允许的小数位、卷/米/片换算与尾料去向。当前数据库quantity允许小数，不代表每条业务计算已经保留小数。

建议采用的两条业务路径：

1）**裁后随背胶工序立即使用，不独立存放：优先保持原料BOM。** 成品BOM直接耗用AUX000107的M数量；在背胶/裁切工序显示毛坯尺寸和跳距。半成品阶段用工序在制数量管理。代价是不能按“195×80片”独立盘点或自动归集其在制成本；优点是不为每个临时裁切尺寸增加库存档案，也不强求当前未具备的多层MRP。
2）**裁后提前备货、独立盘点或跨工单领取：采用半成品库存。** 成品BOM消耗“RP-W195裁切片195×80”的PCS；其下层BOM消耗RP-W195卷料的M；裁切工单把原料转换为半成品批次，再由组装工单领PCS。若一次裁切还产生可用片B，则需要多产出/成本分摊，或采用有明确分摊记录的手工替代。当前不能只把source_type改make就实现：本次实测make被跳过，PRODUCT与MATERIAL库存身份也不连通。

这里不是“BOM绝不能有裁切尺寸”，而是**来料规格不能被工艺尺寸替代，按片的使用数量也不能未经换算直接当成按米库存消耗**。应先定中间态是否独立入库，再调整BOM/工艺/库存边界。

另查到当前 inventory_item(item_type=MATERIAL,source_id=111) 冗余名称/编码/单位还是另一份旧档案文本，unit=PCS，与物料111当前AUX000107/M不一致。源ID关联存在但冗余快照不一致，可能在后续ensure路径更新；本次无库存过账，未验证其是否自动同步。实际入库/扣料验证前应核对统一库存身份，不能凭同一数字ID判定名称/单位正确，也不能无审计证据断言由谁、何时造成。

## 替代方案与代价

| 缺口 | 可采用的替代办法 | 代价/边界 |
|---|---|---|
| 跨层MRP与子工单 | 为各中间件分别建产品/BOM，人工下独立工单；上层原料清单由工程审核后维护，避免重复领料 | 需求联动、版本、数量和在制状态要人工同步；目前MATERIAL与PRODUCT身份仍须正式解决，不能直接拿本次两个孤立BOM当方案完成 |
| 虚拟件穿透 | 若不需要中间库存，工程把下层材料按定额展开维护在单份BOM，按项目/工序分组展示 | 当前是人工维护展开结果，不是系统自动穿透；不得同时保留可领半成品和它的原料而双扣 |
| 一次裁切多个可用产物 | 现有独立工单/手工出入库分别记录产出，公共投入只发一次，人工核对数量与分摊 | 不是内置套件工单；共同材料/工时容易重复或漏计。分摊基准、余料价值和凭证需人工维护，不能靠报工备注代替结构化库存 |
| 按工序隔离领料 | 按审核的用料清单人工分批建领料/出库，source_bom_item_id保留需求来源 | 部分领料不等于按任务卡强隔离，仍需核对累计覆盖、替代和权限；不能声称系统自动生成每层单据 |
| 半成品库位 | 在现有仓库/库位功能新建半成品区域，设置默认仓位，使用合法单据过账 | 此次未修改仓库/库位配置；工序产出自动入库、工单完工默认仓和库存身份是额外开发问题 |
| 不良与边角料 | 不良继续走报工/质量处置；可回收边角料另建档并以合法单据记录重量/面积、批次和估值 | 需明确回收料是否降低主产物成本，人工管理成本较高；不能新增第二套库存余额或改历史流水 |

## 配置能解决什么、哪些必须开发

可配置/资料准备（本次没有实施）：核实中间态是否独立备货；补半成品档案及独立仓位；确认RP-W195裁切排版和M↔片定额；维护工艺先后/末道标志；发布测试/正式作业规范；准备合法原料库存；完善报工审核权限和工价/成本输入。

必须开发/验收的能力（本次不实施）：

1）明确统一库存物品与生产BOM的归属，解决半成品MATERIAL与工单PRODUCT两套身份的关联；BOM版本引用与生产输出不能仅靠名称匹配。
2）跨BOM逐层展开、循环依赖检测、生效版本、需求倍率/损耗/单位换算和子工单；统一预占与领料的make/buy口径。源码OrderMaterialReserveServiceImpl目前按单份BOM所有material_id汇总，领料却只处理buy，两者不能被当作同一多层MRP规则。
3）工序投入/产出明细、一对多输出物料和批次、各自产量/单位、报工冲销与幂等、原料扣减和产出入库的事务边界；已有损耗字段不能替代这些实体关系。
4）按工单层级/工序任务的领料覆盖与防重复扣减；按半成品库存状态决定上层领半成品还是展开原料，不能二者同时扣。
5）联产成本分摊：确定数量/重量/面积/价值等分摊依据并留快照，核对材料、人工、废料回收和尾差；不能只在前端合计。
6）按库存基本单位和发料精度重构定额计算；原料规格、工艺毛坯尺寸、跳距和排版分别管理，并验证小批量、余料、损耗、替代及跨单位场景。独立半成品路径再增加批次/库存/成本验收。

上述是能力边界与建议，不是已登记派发的开发任务，也未授权顺带改现有BOM或库存口径。

## 可复核证据入口

菜单事实来自sys_menu：工程管理(90)→BOM管理(9)、工艺路线(10)；库存管理(18)→物料管理(428)→物料列表(240)；库存管理→仓库与库位(23)→仓库列表(24)/仓库位置(25)、出库作业(33)、入库作业(28)、即时库存(26)；生产管理(43)→生产订单(45)、工序执行(48)及报工权限(280/292/293)；产品作业规范(407)；质量成品报废单(402)。未查到MRP、虚拟件或联产的专用菜单；菜单不存在本身不单独证明后端不支持。

关键SQL（SELECT只读，执行时先固定时间快照，避免把本次测试数据算进初始样本）：

```sql
-- 同一份BOM内的树，与跨BOM引用分开检查。
SELECT b.bom_id,b.bom_code,b.product_id,b.bom_version,b.parent_bom_id,
       i.item_id,i.parent_material_id,i.material_id,i.source_type,i.process_id
FROM engineering_bom b JOIN engineering_bom_item i ON i.bom_id=b.bom_id
ORDER BY b.bom_id,i.item_order,i.item_id;

-- 类型由真实分类树决定，不仅看前端enum。
SELECT m.material_id,m.material_code,m.material_name,c.category_path,m.product_id,
       m.unit,m.cost_price,m.default_warehouse_id,m.default_location_id
FROM inventory_material m
LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
WHERE c.category_path LIKE 'SEMI%' OR m.product_id IS NOT NULL;

-- 真正的产出表/字段必须存在且能被接口写入；JSON输入成功不等于入库。
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='jjx_erp_db'
AND (COLUMN_NAME REGEXP 'phantom|virtual|co_product|coproduct|byproduct|output_material|output_item|child_bom|cost_ratio'
     OR COLUMN_COMMENT REGEXP '虚拟件|联产品|副产品|成本分摊');

-- 本次两个工单及后续单据/流水，不与历史业务混淆。
SELECT order_id,order_no,product_id,bom_id,parent_order_id,order_status,work_spec_version_id
FROM production_order WHERE order_no LIKE 'TST261110011-%';
SELECT * FROM inventory_outbound_order WHERE source_type='work_order' AND source_id IN (1,2);
SELECT * FROM production_work_report WHERE order_id IN (1,2);
SELECT * FROM inventory_inbound_order WHERE source_type='PRODUCTION' AND source_id IN (1,2);
SELECT transaction_id,inventory_item_id,material_id,warehouse_id,location_id,
       transaction_type,source_type,source_id,batch_no,quantity,unit_cost
FROM inventory_transaction WHERE material_id IN (349,350);

-- 用户指定的真实离型纸行，核对原料单位与BOM单位/跳距。
SELECT m.material_id,m.material_code,m.specification,m.width_mm,m.length_mm,m.unit,
       i.item_id,i.bom_id,i.parent_material_id,i.specification AS bom_spec,i.split,
       i.unit AS bom_unit,i.quantity,i.base_qty,i.module_qty,i.actual_issue_qty
FROM inventory_material m JOIN engineering_bom_item i ON i.material_id=m.material_id
JOIN engineering_bom b ON b.bom_id=i.bom_id
WHERE b.bom_code='YL1025PWOO-BOM' AND m.material_code='AUX000107';
```

源码辅助定位（行号为排查时定位，不代表运行包与所有工作区修改一致）：

| 文件 | 对应依据 |
|---|---|
| jjx-server/src/main/java/com/jjx/production/service/ProductionBomResolver.java:21 | 工单只解析绑定的一个批准BOM，未绑定历史工单回退产品当前BOM |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/OrderMaterialReserveServiceImpl.java:70 | 订单预占从一个BOM直接汇总物料，不递归下层 |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java:967、994、1253 | 预览和建单只处理buy行；实际测试上层make返回空 |
| 同文件:1088、1158 | GROUP仅展示分组并按项目名匹配半成品档案 |
| 同文件:1449 | 整批需求整数向上取整和最低发料量 |
| jjx-server/src/main/java/com/jjx/production/domain/dto/WorkReportSubmitDTO.java:19 | 无投入/产出物料数组的报工契约 |
| jjx-server/src/main/java/com/jjx/production/service/impl/WorkReportProjectionServiceImpl.java:46 | 标量产出=审批合格+不良 |
| jjx-server/src/main/java/com/jjx/production/service/impl/WorkReportActionServiceImpl.java:312 | 报工审批投影、任务与质量批推进，不等同工序多产出记库存 |
| jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java:2817、2849 | 普通完工单单PRODUCT入库、首个启用仓，无中间工序多MATERIAL产出 |
| jjx-server/src/main/java/com/jjx/product/service/WorkSpecBindingService.java:61 | 缺发布版本时阻止下达，实际运行响应一致 |
| jjx-server/src/main/java/com/jjx/production/controller/ProductionCostController.java | 当前成本报表按单工单/单产品汇总，不含联产分配行 |

## 交付与遗留

本次只交付本报告和history索引，业务源文件0改动；数据库只通过正常API追加并流转上述测试业务记录，另登记/回写本任务。测试产品2/3、物料349/350、BOM3/4、路线2和待审核工单1/2保留供复核，未擅自删除或取消；无库存流水和余额变动。其他会话在制文件和备份均未纳入提交。

本报告的功能结论不需要继续造虚假报工才能成立；但“单产品完整闭环”“半成品真实出入库”“报工自动扣增”“真实库事务”“联产成本”均未通过本次运行验证。下一次若做实施，应先确定AUX000107裁后是否独立入库，以及每跳产出、净尺寸、发料精度，再按上述能力缺口单独授权开发，不将展示规格变更当成业务流程优化已完成。
