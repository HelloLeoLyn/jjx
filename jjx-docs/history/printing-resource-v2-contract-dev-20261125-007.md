# 印刷资源重构 P01 · 模型与接口契约设计

任务 dev-20261125-007，2026-11-25。本文为待独立复核、待 Owner 批准的实施契约。数据定义、命令和路径均用于后续原卡；本次只读调查目标库。发布单位是一套经过 P12 验证的前后端与结构版本。

## 现况与身份缺口

目标库查询 `engineering_screen_frame`，对每个未删除框以 `NOT EXISTS (SELECT 1 FROM engineering_screen_plate p WHERE p.frame_id=f.frame_id)` 计数：EMPTY 2,788/2,788 无履历，PLATED 4,503/4,503 无履历。EngineeringResourceService.create 制版路径插入 plate 后更新框；同文件 importScreenFrames 先解析输入状态、按框号插入/更新框，仅 content 非空才写 plate；存在活动版时覆盖 content，首次导入曾以框号作版号。此路径足以产生上述缺口，当前数据无法证明每条记录的导入来源、日期或被覆盖次数。禁止据此补造绷网或制版记录。工程菲林现表 engineering_film.product_id 非空，版本与实体混存，Mapper 按产品/类型重置当前标记。新域仅接纳切换后显式建档的对象。

## ER 与物理字段

关系键均为 BIGINT，业务编号 VARCHAR(64)，名称和用途 VARCHAR(200)，时间 DATETIME(3)，状态 VARCHAR(24) 取公共枚举，附件指向受权附件 ID。新表自增 BIGINT 主键；每条可变记录带 lock_version INT、create_by/update_by 账号与 create_time/update_time，重要动作附操作者 ID、当时显示名、时间、原因。字符串表/列显式 utf8mb4_unicode_ci。下列 FK 删除策略 RESTRICT。未注明可空的业务必填列非空，唯一键之外各 FK 建索引。

|事实及所有者|字段、关系与库级约束|
|---|---|
|print_artwork（P03）|artwork_id PK，artwork_no UNIQUE，name，purpose，family（面板/上线/下线），set_code 可空编组，sample_order_id 可空，default_version_id 可空。组内每个图案独立，无整套版本或排布；索引 (set_code,family)、name。当前默认指针与 artwork_id 复合 FK 到本图案版本。|
|print_artwork_version（P03）|version_id PK，artwork_id FK，version_no，status，drawing_file_id，technical_spec，designer_id，approved_by/released_by 及对应时间（动作前可空），UNIQUE(artwork_id,version_no)、UNIQUE(artwork_id,version_id)。一图多版本，一份已发布版本可供多个产品和实体底片使用。文件经 sys_attachment_ref 钉住。|
|engineering_resource_product_rel（P03 仅 ARTWORK）|复用 resource_type、resource_id、product_id、purpose、is_active，唯一(type,id,product_id)，反向索引(product_id,type,is_active)。产品与图案 N:M；菲林产品适用关系沿版本继承。多态键由服务在事务内锁图案与产品验证，保留刀模关系。打样无正式产品时锁 sample_order_id，转产追加正式关系。|
|print_film_copy（P03）|film_id PK，film_no UNIQUE，version_id FK，location，status，material，thickness_mm DECIMAL(10,3)>0，size，output_time，destroy_time 可空。复制同版本产生新 film_id；一个版本多张实物底片。UNIQUE(version_id,film_id) 供制版引用。|
|engineering_screen_frame（P04）|复用 frame_id、frame_no、frame_type、location；新增 v2 登记标识（已有行默认为否，仅新建可为是），v2 状态 READY/MAINTENANCE/SCRAPPED；新登记框号全域去重，切换前检查重复或与旧号冲突并退回建档；当前目数从绷网履历读。|
|print_frame_stretch（P04）|stretch_id PK，frame_id FK，mesh INT>0，tension_n_cm DECIMAL(8,2)>0，material，stretch_time，qualified，end_time 可空；UNIQUE(frame_id,stretch_id)，生成列在 end_time IS NULL 时取 frame_id，其余 NULL，唯一索引；同框重绷关闭前段。|
|engineering_screen_plate（P04）|复用 plate_id、plate_no、frame_id、plated_time、washed_time、end_reason；v2 行必有 stretch_id、version_id、film_id、position、status、检查结果、放行人/时间。组合 FK(frame_id,stretch_id)、(version_id,film_id)；单次制版新号 UNIQUE，不能复活旧版。占框状态待检/合格/不合格生成 frame_id 唯一索引，洗版或报废后 NULL。索引(version_id,status)。旧记录不参与候选。|
|engineering_resource_maintenance（P04）|复用 resource_type/id、action_type、before_status/after_status、description、operator、operate_time，记清洁、洗版、重绷、检验及维护。清洁只追加事件；洗版须在同一事务结束占框并保留版面。|
|engineering_routing_item、sales_sample_process（P08/P09）|印刷行保存 artwork_version_id、default_plate_id 可空、print_requirements JSON（位置、目数/张力允许上下界及单位），产品归属沿行头。图案由版本解析；版本未知仅打样计划可空；版与版本组合引用并校验。现有油墨/色号、印序、工时、作业说明及其他工序字段沿用。|
|product_work_spec_version 与既有规范清单（P11）|以规范版本 ID + 路线版本 ID + 印刷工序行 ID 标识一次发布绑定；每行固化版本 ID、默认版 ID 可空、要求与显示快照。进入既有 manifestHash 计算与版本详情/差异/打印；同一规范不替换行。生产工单绑定此规范版本 ID。|
|print_use_segment（P10 服务；打样实际由 P09 记录在打样行执行快照）|segment_id PK，execution_id FK，spec_version_id FK，routing_item_id FK，version_id FK，plate_id FK，start_time，end_time 可空，operator_id/name，reason，request_id UNIQUE，snapshot JSON。索引(execution_id,start_time)、(version_id,end_time)；开放段生成 execution_id 唯一，实体版开放段也以 plate_id 唯一，防不同工单同时占用。|

打样行执行快照按轮次和 process_id 固定实际 plateId、操作人、实际参数与时间；复制计划时清空实际。正式用版段绑定工单执行，与打样执行分别按真实来源查询。规范、打样执行及生产段的显示快照含图案号/名/版本号、版号/框号及当时实际参数（有实物时）；外键用于验证关联，快照用于历史打印。修改框位置、洗版或产品改名不改历史。P02 的一致性巡检按版本外键、发布时已固定的行及绷网事实核对快照中的稳定字段，名称、位置按当时捕获值保存，禁止用当前名称覆写。

## 状态、并发与权限

P03 图案草稿 DRAFT → REVIEW → APPROVED → RELEASED；驳回回 DRAFT。批准后修改内容须另起版本。RELEASED 可设默认，原默认仍为 RELEASED；显式停用转 DISABLED，停用当前默认须清空或同事务替换。生效与同步停旧锁同一图案，按 expectedRevision 比对后提交。停用影响预览按版本返回所有引用路线、有效规范、未完工工单及执行段；确认时重新取集合，变化返回冲突。停用阻断随后领用/开工/换入，不改已发生段、工单要求或设备状态。工程决定受影响工单的处置，按原工单受控变更流程留痕。

实体底片 AVAILABLE → ARCHIVED → DESTROYED，AVAILABLE 亦可直接销毁；封存/销毁禁新制版，已放行网版的来源仍可追踪。框 READY ↔ MAINTENANCE → SCRAPPED，READY 也可报废。制版需空闲 READY 框、合格当前绷网、已发布可用版本与该版本 AVAILABLE 底片。版面 PENDING → RELEASED 或 FAILED，失败可重新检查留痕。占框版可洗版 WASHED 或报废 SCRAPPED，均终态；在用须先结束用版，重绷/框报废前先清空活动版。普通清洁不结束版面。放行与制版分权。默认网版仅作工程建议；生产锁规范，再校验版面 RELEASED、框/绷网可用、同版本及全部工艺要求。换版锁执行及新旧版（按 ID 升序），同事务关闭原段并开新段；失败全部回滚，同 requestId 同载荷返回原结果，不同载荷冲突。完成/取消执行同步结束开放段。开工、洗版、停用按统一顺序锁版本→框→版→执行，校验到提交不可穿插。

权限由服务端检查当前用户、对象及业务范围；跨产品共享不扩大附件读取权限。P03 沿用 engineering:film:view/edit/submit/approve/reject/release；停用、底片封存/销毁分别用 engineering:film:disable/archive/destroy。P04 查询、维护、建档沿用 engineering:screen-resource:view、engineering:resource:maintain/edit，放行和洗版分别 engineering:screen-resource:release/wash。审批人与提交人分离。P05 依调用入口权限过滤，再裁剪无权产品、附件和候选；未授权动作返回 403。P08/P09/P11/P10 沿原路线审批、打样转产、规范发布、执行开工权限；生产操作员无权修改图案版本。P02 在获准隔离目标登记新增权限，授权按角色另审，默认不授予。

## 服务与消息契约

新资源根路径 /engineering/printing。以下请求字段均在既有 Result.data 封装内返回业务结果；列表返回 rows,total,pageNum,pageSize。写命令拒绝客户端操作者、状态和快照输入，身份由会话解析。不存在/无权按既有封装表达 404/403，非法组合 422，版本变化、重复占用或已转态 409（reasonCode、当前 revision）。所有动作携带 requestId 与 expectedRevision；通过已有业务动作记录或唯一请求键保证重试最多生效一次。附件下载走原授权入口。

|所有者、方法及路径|输入 → 响应 data；事务边界|
|---|---|
|P03 POST /artworks、PUT /artworks/{id}；POST /artworks/{id}/versions、PUT /versions/{id}|图案号/名、family、setCode、productIds 或 sampleOrderId；版本号、drawingFileId、technicalSpec → artworkId/versionId/revision。校验产品及附件并一并保存。GET /artworks/{id} 回版本、适用产品和实物。|
|P03 POST /versions/{id}/{submit,approve,reject,release,activate,disable}；GET /versions/{id}/impact|reason，activate 可带 disablePrevious、影响集合指纹；disable 必须带 impactToken → status、defaultVersionId、revision；预览返回 routingIds/specVersionIds/orderIds/executionIds、impactToken。release 不自动改变默认。|
|P03 POST /versions/{id}/films；POST /films/{id}/{archive,destroy}|filmNo、位置、尺寸、材质、厚度；处置原因 → filmId、versionId、status。对同一输出版本可重复建不同编号实物。|
|P04 POST /frames、PUT /frames/{id}、GET /frames/{id}；POST /frames/{id}/{stretch,maintain,resume,scrap,clean}|框号/型别/位置；绷网带 mesh,tensionNcm,material,qualified；动作带原因 → frameId、stretchId、活动 plateId、履历；维护历史随事务写入。|
|P04 POST /plates；POST /plates/{id}/{release,fail,wash,scrap}|frameId,stretchId,filmId,versionId,plateNo,position；检验项/结论、原因 → plateId,status,frameId,revision。GET /plates/{id} 返回身份链和历次检查。|
|P05 GET /search|q,pageNum,pageSize,scene,productId 或 sampleOrderId、specVersionId/routingItemId/executionId（受控场景必传所属上下文）→ rows 中含 artworkId/versionId/filmId/plateId/frameId、matchedType、matchedCode、用途、显示名、版本号、产品、目数/张力/位置、availability、reasonCodes。未进入实物层的 ID 为 null。|
|P08 POST/PUT /engineering/routings[/{id}]，既有 copy/submit/approve/详情|items 中印刷行传 versionId、defaultPlateId、requirements 与原业务字段 → routingId、items[].routingItemId 及回读同一绑定。默认版可空；审批再验版本及要求并锁定，调整走新路线版本。|
|P09 PUT /sales/sample-order/processes/{orderId}/plan；原打样预览与确认服务|轮次、方案、processId、可空 versionId/defaultPlateId 与要求 → 按行回读；执行追加实际 plateId。转产 {confirmedSchemeId,roundId,targetProductId,requestId} → productId,routingId,来源行映射；一事务关联同一图案并调 P08 校验，未确认、未批准或行归属不符拒绝。|
|P11 POST /product/{productId}/work-spec/publish；GET /product/work-spec/versions/{versionId}|routingId、changeSummary → specVersionId、manifestHash、printingItems；服务从批准路线拷贝，历史详情与打印共用受控响应，保留非印刷文档集。|
|P10 PUT /production/operation-execution/{id}/start；POST /production/operation-execution/{id}/printing-use/change|plateId、requestId、expectedRevision、备用/换版 reason → executionId,segmentId,specVersionId,versionId,plateId,startTime,previousEndTime。读执行详情附 segments；不接受手输框号或覆盖 versionId。|

搜索以空白分词，每个词均须命中候选身份链任一允许字段，精确业务编号先于前缀、再先于包含，按候选主键稳定分页；同框多代分行，返回场景所需实体主键。新建图案选择默认版本，受控入口按服务器解析出的绑定过滤，显示不可用原因。P05 组件防抖、分页、丢弃晚到结果，清空/仅输文本时返回空选择，不能以标签提交引用。P06/P07 跳转传 versionId、filmId，提交前 P04 重新查验。

## 新表准入与恢复

以下逐表引用 CONVENTIONS §15.2 编号。复用工程资源产品关系、维护日志、框/版、规范版本、附件与审批记录；整套编组只需要标签，搜索结果和默认候选由事实查询。

|提案|满足判据及无法复用的原因|主要风险与恢复验证|
|---|---|---|
|print_artwork|1 独立编号/归档对象，2 被版本及产品引用，3 跨产品，4 编号/名称反查，6 engineering_film 强制单产品|错误合并共享图案；按图案号、产品关系与版本引用核对，隔离夹具重建。|
|print_artwork_version|1 审批/发布/停用，2 路线规范引用，3 图案内版本号唯一，5 工程审核，6 旧菲林当前标记以产品为域|并发双默认；查默认复合 FK 与发布状态，失败事务回滚。|
|print_film_copy|1 输出至实物销毁，2 用于制版，3 同版多份、实体号唯一，5 独立处置，6 旧表混存设计与实物|把销毁误传播为设计停用；从动作和版本外键对账，保留已销毁行。|
|print_frame_stretch|1 绷网至拆网，2 制版引用，3 同框多次与唯一开放段，4 历史参数查询，6 旧框 mesh 只有当前值|重绷覆盖既有张力；按 frame_id 和 stretch_id 检查有效区间与历史引用。|
|print_use_segment|1 开始/结束，2 执行与历史凭证，3 一执行多段和单版排他，4 按工单/版本追溯，6 既有 execution 一行无法表示换版前后|重试双计/时段重叠；按 execution_id、plate_id 检查时间、开放段及唯一请求号，整事务恢复。|

P02 仅在 Owner 逐表批准并由独立复核者确认后，登记 approvedNewTables（日期、任务码、提案链接），同步实际表基线。迁移号取目标账本与目录最大值加一。指定隔离库、手工备份并排除 hr_employee 后用 db-migrate.sh 验证，核对字符集、约束反例、巡检和基线。生产切换须另行批准目标、窗口及恢复点。旧体系退出写入口，旧数据原样保留，无迁移、回填或双写。切换前故障停止推进；切换后若已有新业务，先隔离新写入并核对差异，经 Owner 确认恢复点后由授权人员恢复匹配的数据库/应用，保存新写入待处置，禁止直接切旧程序继续写。

## 入口 × 场景适用矩阵

|入口|新增整单 / 编辑整单|新增产品或工序行 / 编辑行|历史、复制、打印与反例|
|---|---|---|---|
|P06 图案/底片、产品详情菲林页签|建档 / 草稿编辑；实物单独输出|新增/移除产品适用关系；产品详情只读|组内独立版本、附件预览下载、影响预览；同套另一图案不联动。|
|P07 网框/制版页、P06 发起制版|新框或新制版 / 维护和动作|不适用，实体与产品经图案间接关联|框当前与历史分开；洗版后换产品，普通清洁后仍可追原版。刀模原行为保留。|
|P08 路线列表表单/明细/审批|新建、复制、新版本 / 编辑草稿均提交完整 items|增印刷工序 / 改工序行固定版本，默认版可空|详情/对比读固定行；空印刷名称按既有修复校验；其他工序和油墨不丢。|
|P09 打样工作台及订单来源入口|新建、来源单创建、复制 / 重开、多轮保存|按方案产品/工序行保存计划及当轮实际；无产品可存草案|转产预览和确认显式传已确认方案；多轮复制不带执行事实，不从附件名造菲林。|
|P11 作业规范|发布新版本 / 草稿修改后再发布|从批准路线读取各产品工序；已发布行只读|详情/差异/历史及打印绑定相同规范 ID、版本号及图案版本。|
|P10 工单、执行、制造历史|建单固定规范 / 普通工单编辑保留绑定|开工只选实体，换版增段；非印刷工序不出现选择器|错版本、未放行、停用、洗版及参数不符均拒绝，备用需原因；历史按段还原。|

## 波次验收与关闭条件

|波|卡及独立复核证据（前一波依赖项须 status=2 且行为复核通过）|
|---|---|
|1|P01 对照模型、路径和各入口逐项评审。Owner 签准新表与实施范围；协调者将下列路径及接口回填原卡。|
|2|P02 在指定隔离目标实测复合 FK、唯一键、竞争写入、回滚、基线及巡检；记录原始输出与账本。|
|3|P03 图案双产品、两图独立版本和销毁；P04 同框并发制版/洗版换产品及清洁；P05 多词跨字段、同框旧号、权限裁剪和晚到请求。三卡以波2夹具验证。|
|4|P06 图纸审批及跨页制版；P07 接受带入身份、展示履历及保留刀模；P08 全入口保存重开、无默认版可发布、复制/比较。三卡分别消费波3服务与搜索。|
|5|P09 验证打样多轮确认转产及计划/执行分离；P11 验证批准路线到规范、历史和打印的一致性。分别消费 P08，不互写。|
|6|P10 在 P11 真实发布后核查建单锁版、同版本替代、开工/换版回滚、停用影响单及完成关闭段，不更改工程版本。|
|7|P12 独立人员核提交、路径、原始输出、数据库痕迹和反例，跑打样→路线→规范→工单→备用→洗版→停用→历史打印；出验收及切换/恢复清单。缺陷退原卡，未验证不放行。|

每波最多三名执行者及一位独立协调/复核者。P01 只执行文档门禁；后续定向 Maven 1536M/300s、vue-tsc 3072M/300s、Vitest 单 worker 1024M/120s、构建 2048M/300s，先核可用内存。测试目标必须明确指派，真实库业务写入与服务启停另行授权。

## 文件所有权矩阵

P01 只拥有本报告和 INDEX 本行。以下从本组卡面展开，路径以仓库根为起点。后端简写 product/、engineering/、sales/、production/ 均以 jjx-server/src/main/java/com/jjx/ 为根，前端 api/、types/、views/、components/、enums/ 以 jjx-web/src/ 为根。下列精确选点进一步收窄原卡目录。测试继承实现所有者；遇到未列出的必改共享入口由协调者在原卡定归属。

### dev-20261125-008

jjx-docs/sql/migrations/NN_printing_resource_v2_*.sql（NN执行时按账本与目录最大号取号，不预占；已应用同名归档按既有规则）。
scripts/model-baseline.json 仅已批准新表及相应基线；scripts/check-printing-resource-v2.sh（新增只读一致性检查）；本卡隔离数据库夹具 .tmp/printing-v2/P02/。业务实体/Mapper/页面不写，由领域卡负责。

### dev-20261125-009

新增领域包 jjx-server/src/main/java/com/jjx/engineering/printing/artwork/；对应测试 jjx-server/src/test/java/com/jjx/engineering/printing/artwork/。
既有菲林退役/替换边界：jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringFilm.java；jjx-server/src/main/java/com/jjx/product/controller/EngineeringFilmController.java、jjx-server/src/main/java/com/jjx/product/service/IEngineeringFilmService.java、jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringFilmServiceImpl.java、jjx-server/src/main/java/com/jjx/product/mapper/EngineeringFilmMapper.java、jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringFilmDTO.java、jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringFilmVO.java、jjx-server/src/main/java/com/jjx/product/enums/FilmTypeEnum.java。展开花括号为精确路径，目录其余只读。

### dev-20261125-010

新增领域包 jjx-server/src/main/java/com/jjx/engineering/printing/screen/；对应测试 jjx-server/src/test/java/com/jjx/engineering/printing/screen/。
jjx-server/src/main/java/com/jjx/engineering/controller/EngineeringResourceController.java；jjx-server/src/main/java/com/jjx/engineering/service/EngineeringResourceService.java；jjx-server/src/main/java/com/jjx/engineering/enums/EngineeringResourceEnums.java 仅网框/网版分支。刀模能力必须保留。

### dev-20261125-011

新增后端包 jjx-server/src/main/java/com/jjx/engineering/printing/search/ 及对应测试目录。
新增前端 jjx-web/src/components/Selector/PrintResourceSelector.vue、PrintResourceSearchOption.vue、printResourceSearchOptionFields.ts、usePrintResourceSearch.ts；jjx-web/src/api/engineering/printingSearch.ts；jjx-web/src/types/engineering/printingSearch.ts；Selector/__tests__/printResource*.spec.ts。既有MaterialSelector/useMaterialSearch/EditableSearchSelect只读复用，不改物料搜索行为。

### dev-20261125-012

jjx-web/src/views/product/film/（含本模块组件/定向测试）；jjx-web/src/api/product/film.ts；jjx-web/src/enums/product/film.ts；jjx-web/src/types/engineering/printingArtwork.ts（新增）。
共享旧类型 jjx-web/src/types/product/index.ts 仅菲林接口段，如P01证实仍需使用；P05公共组件、ProductDetail和全局附件组件只读。

### dev-20261125-013

jjx-web/src/views/engineering/resource/index.vue 及新增本模块组件/定向测试；jjx-web/src/api/engineering/resource.ts 仅SCREEN接口；jjx-web/src/enums/engineering/resource.ts 仅SCREEN枚举；jjx-web/src/types/engineering/printingScreen.ts（新增）。P04后端和P05公共组件只读。

### dev-20261125-014

jjx-web/src/views/product/route/；jjx-web/src/api/product/route.ts；jjx-web/src/types/product/route.ts；jjx-web/src/components/product/EngineeringRoutingItem.vue。
jjx-server/src/main/java/com/jjx/product/service/impl/EngineeringRoutingServiceImpl.java、jjx-server/src/main/java/com/jjx/product/service/IEngineeringRoutingService.java、jjx-server/src/main/java/com/jjx/product/service/EngineeringRoutingValidator.java、jjx-server/src/main/java/com/jjx/product/controller/EngineeringRoutingController.java、jjx-server/src/main/java/com/jjx/product/controller/EngineeringRoutingItemController.java、jjx-server/src/main/java/com/jjx/product/mapper/EngineeringRoutingMapper.java、jjx-server/src/main/java/com/jjx/product/mapper/EngineeringRoutingItemMapper.java、jjx-server/src/main/java/com/jjx/product/domain/converter/EngineeringRoutingConverter.java、jjx-server/src/main/java/com/jjx/product/domain/dto/EngineeringRoutingItemDTO.java、jjx-server/src/main/java/com/jjx/product/domain/vo/EngineeringRoutingItemVO.java；jjx-server/src/main/java/com/jjx/engineering/domain/entity/EngineeringRoutingItem.java；对应routing测试和mapper XML仅相关段。

### dev-20261125-015

jjx-web/src/views/engineering/sample-workbench/；jjx-web/src/api/sales/sampleOrder.ts；jjx-web/src/types/sales/sampleOrder.ts。
jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java；sales/domain/entity/SalesSampleProcess.java；sales/dto/save/SampleProcessPlanDTO.java；sales/dto/transfer/SampleTransferConfirmDTO.java；sales/domain/vo/SampleTransferPreviewVO.java；sales/mapper/SalesSampleProcessMapper.java 及对应XML/定向测试（以上sales相对com/jjx）。P08路线写服务与P11规范文件只读调用。

### dev-20261125-016

jjx-web/src/views/engineering/product-spec/；jjx-web/src/api/product/workSpec.ts；新增types/engineering/printingSpec.ts。
jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecVersionService.java、jjx-server/src/main/java/com/jjx/product/service/WorkSpecDocsetSnapshotService.java、jjx-server/src/main/java/com/jjx/product/service/ProductWorkSpecService.java、jjx-server/src/main/java/com/jjx/product/service/WorkSpecBindingService.java；product/controller/ProductWorkSpecVersionController.java、product/controller/ProductWorkSpecController.java；product/domain/entity/ProductWorkSpecVersion.java；product/mapper/ProductWorkSpecVersionMapper.java 及对应定向测试（后端product相对com/jjx）。P08路线写服务、P10生产用版写服务只读。

### dev-20261125-017

jjx-server/src/main/java/com/jjx/production/printing/（新增领域包及对应测试）；production/service/impl/ProductionOperationExecutionServiceImpl.java、production/service/ProductionOperationExecutionService.java、production/controller/ProductionOperationExecutionController.java、production/domain/entity/ProductionOperationExecution.java、production/domain/dto/ProductionOperationExecutionCreateDTO.java、production/domain/dto/ProductionOperationExecutionUpdateDTO.java、production/domain/vo/ProductionOperationExecutionVO.java、production/mapper/ProductionOperationExecutionMapper.java 及对应XML。
工单固定要求入口 production/service/impl/ProductionOrderServiceImpl.java、production/service/impl/ManufacturingHistorySnapshotServiceImpl.java、production/domain/vo/ManufacturingSnapshotVO.java 仅印刷版本与用版快照。前端 jjx-web/src/views/production/execution/、jjx-web/src/api/production/operationExecution.ts、types/production/operationExecution.ts；新增production/printingUse API/types（上述production后端相对com/jjx，前端相对jjx-web/src）。

### dev-20261125-018

业务源码及正式库只读；允许新增 jjx-web/e2e/printing-resource-v2/、jjx-server/src/test/java/com/jjx/engineering/printing/acceptance/（独立验收夹具，不改实现者单测）。
jjx-docs/history/printing-resource-v2-acceptance-<本卡任务码>.md、printing-resource-v2-cutover-<本卡任务码>.md（UTF-8 BOM）及INDEX.md对应登记行；只回写本卡授权验收记录，实现缺陷退回原任务不抢修。


### 新增文件与定向测试选点

|卡|固定落点（同一格的文件共享所示目录前缀）|隔离验收|
|---|---|---|
|P02|迁移文件尾名 printing_resource_v2_schema.sql，前缀执行时定号；巡检 scripts/check-printing-resource-v2.sh|.tmp/printing-v2/P02/constraints.sql；用两连接竞争同框及同执行开放段。|
|P03|engineering/printing/artwork/ 下 ArtworkController.java、ArtworkService.java、ArtworkRepository.java、ArtworkCommands.java、ArtworkView.java；对应 test/java/com/jjx/engineering/printing/artwork/ArtworkContractTest.java|审批驳回、双产品、同版两底片、停旧影响、附件授权、动作重试。|
|P04|engineering/printing/screen/ 下 ScreenController.java、ScreenService.java、ScreenRepository.java、ScreenCommands.java、ScreenView.java；镜像测试 ScreenLifecycleTest.java|合格绷网、失败复检、并发洗版/开工、维护、刀模回归。|
|P05|engineering/printing/search/ 下 PrintSearchController.java、PrintSearchService.java、PrintSearchRepository.java、PrintSearchOption.java；镜像测试 PrintSearchTest.java；components/Selector/__tests__/printResourceSearch.spec.ts|多词、同框旧版、翻页、授权、失序响应及自由文本。|
|P06|views/product/film/index.vue、components/ProductFilmPanel.vue（相对该页）、同目录 __tests__/artworkLifecycle.spec.ts；产品详情沿用 ProductFilmPanel 读取投影|上传/下载、退回、停版及同图案跨产品显示。|
|P07|views/engineering/resource/index.vue、同目录 components/ScreenLifecyclePanel.vue、__tests__/screenLifecycle.spec.ts|从菲林跳转、洗版再制、刀模不丢。|
|P08|views/product/route/index.vue、components/RouteFormDialog.vue、RouteCopyDialog.vue、RouteApproveDialog.vue、RouteDetailDialog.vue、RouteVersionCompareDialog.vue（后四项同 components 目录）；镜像后端 product/service/EngineeringRoutingPrintBindingTest.java|新增/编辑/复制/审批重开；空名称和其他工序。|
|P09|views/engineering/sample-workbench/index.vue、该页 components/PrintProcessPanel.vue、SampleProcessPlanEditor.vue、SampleProcessPlanItem.vue 及 composables/useProcessExecution.ts；镜像 sales/service/SamplePrintTransferTest.java|GET /sample/transfer/preview/{orderId}、POST /sample/transfer/confirm 对同一确认方案，保存重开、换轮、转产回滚。|
|P11|views/engineering/product-spec/index.vue、该页 components/ProductPrintSpecPanel.vue、PrintSpecPaper.vue、ManufacturingProcessSheet.vue、workSpecProcessRows.ts；镜像 product/service/PrintingSpecSnapshotTest.java|旧版打印、规范版本与内容哈希、旧版停用仍可追溯。|
|P10|production/printing/ 下 PrintingUseService.java、PrintingUseRepository.java、PrintingUseCommand.java、PrintingUseView.java；镜像 PrintingUseSegmentTest.java；jjx-server/src/main/resources/mapper/production/ProductionOperationExecutionMapper.xml；views/production/execution/index.vue、api/production/printingUse.ts、types/production/printingUse.ts|开始、替代、换版竞争、完成、历史及错误版本。|
|P12|jjx-web/e2e/printing-resource-v2/full-chain.spec.ts、jjx-server/src/test/java/com/jjx/engineering/printing/acceptance/PrintingResourceChainTest.java；两报告任务码 dev-20261125-018|验证上述真实全链以及旧入口退役，独立验收不写实现。|

### 跨目录只读调用核对

ProductDetail.vue 从产品聚合接口读取 films 并传给 ProductFilmPanel；P03 在原 IEngineeringFilmService 的产品查询边界提供按图案适用关系派生的展示投影，P06 承接只读面板。ProductServiceImpl 的删除依赖检查仍枚举 engineering_resource_product_rel，P02 对产品 FK 或等价事务约束验证并发删除。AttachmentAccessService 现有硬引用查询含 engineering_film.file_id，P03 对新版本使用既有附件引用机制，并验证发布文件不可删除。旧审批轨迹仍可读，当前写入仅指向新身份。任一只读调用无法借既有边界满足时，阻塞对应原卡并提交路径依据。
