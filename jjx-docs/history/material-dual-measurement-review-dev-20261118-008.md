# 物料实物单位与采购计价单位整链独立验收

任务码：dev-20261118-008  
日期：2026-11-18  
验收者：Codex-review（独立 Agent C，未参与 005/006/007 实现）  
依据：用户明确授权“授权拆卡并派工”；任务记录 008 限定只读实现和业务库，唯一报告交付与 history/INDEX.md 登记。

## 验收状态

补修代码与行为证据复核已通过；最终补提交 `877de6b5` 与统一门禁 `review-final-validate.exit=0` 已由 OpenClaw 交接后归档（见文末「归档补记」，该补记不改变本报告的独立验收结论与署名）。初始实现提交为公共能力 `acf764b0`、档案全入口 `12b7bbb2`、采购库存 `915df4af`，三卡开始验收时均为待审核。初次发现的采购编辑冻结、称重回显/数量变化与旧收货接口旁路已交主 Agent 修复，并完成下述独立复核；运行态整链仍未验证。

## 白名单与独立性

开始前读取 AGENTS.md、多 Agent 协作协议、任务书指南及四卡描述/备注，并保存 `.tmp/dual-measure-review/baseline.txt`。008 以 CAS 从待开始认领为进行中，执行者 Codex-review。共享工作区已有产品编辑和 SQL 清理/备份改动，008 不修改或暂存这些文件；008 不修改实现、不改真实业务数据、不审批单据、不操作服务、不回写实现卡状态。

005 的公共计算器、策略契约、预览接口、DTO 和迁移 375；006 的档案保存、默认值、复制/导入及共享 DTO（由主 Agent 协调）；007 的采购/库存/预留快照、业务服务、前端和迁移 376（含主 Agent 协调共享公式扩展）均按对应任务记录及提交核查。008 只交付本报告和索引登记。

## 已独立核查的代码行为

| 范围 | 已核实依据与边界 |
|---|---|
| 唯一公式 | MeasurementCalculator 承担 SAME、片/米/卷面积、单重、面积克重、卷/片尺寸克重、实际称重及账量反算，使用精确数值和终点精度；单位、整数计数、正规格、实际净重缺失有拒绝路径。 |
| 档案与默认值 | MaterialMasterService 新建使用明确品类默认；编辑保留现有单位及策略；显式 null 不把复制的无配置历史档案升级；前端复制保留策略和尺寸；Excel 识别双单位列并沿用公共 prepare 校验。未在真实库保存档案。 |
| 逐卷实际与余卷 | 采购收货按每卷实际宽长/适用净重冻结；余卷使用该次实发快照及实际长度，保留原卷号；KG 实际称重需明确净重；二次余卷退料不能超过该次实发余长。 |
| 库存查询 | InventoryStockServiceImpl 按当前页 ID 批量读库存物品、档案、库位及有效批次；InventoryStockMeasurement 保留历史账单位，优先冻结快照；历史已知卷号计实际卷数、账面㎡余额计实际面积；片材仅明确形态和尺寸计算面积，组件/橡胶不从包装尺寸造面积。汇总不一致或不可换算时不给部分派生合计。 |
| 预留与发料 | MaterialStockAllocationService 将订单预留绑定批次/卷号，整卷占用与需求分开；本订单发料消费原分配并扣同一批次，其他订单预留受保护；交易失败回滚预留、批次及追加流水；重新预留扣除已消费覆盖的需求。 |
| 流水纪律 | 迁移只新增可空快照/预留归属列；数量仍经 applyDelta 更新并追加流水，无第二套库存数量余额。 |

## 初次发现与复核要求

1）采购编辑保存冻结策略问题：页面保存行未传 orderId，后端计算早于实体设置父单 ID；旧策略识别要求明细的 orderId 与数据库匹配，导致预览正确而保存重新使用现档案。要求由可信父单 ID 验证归属，并覆盖页面实际 DTO 形状。
2）历史无快照采购单问题：已有采购行 measurement_snapshot 为空时读取当前档案的新策略和尺寸，会静默升级历史路径。要求保持原已存单位/参数，不因后续档案配置而替换。
3）称重旧采购单编辑问题：GET VO 无 measurementActual，页面未从冻结 snapshot.actual 回显；后端重算不沿用已冻结净重。要求仅在实物数量不变、未显式重称时保留旧实物/净重，数量变化不得按比例猜重量。
4）行为证据补齐：原 release 测试直接调用分配释放，没有覆盖取消/到期的服务路径；未见并发竞争同卷测试。主 Agent 已收到补测要求，运行态并发仍属未验范围。
5）前端统一公式边界：新双单位逐卷面积展示仍调用前端 rollArea。已要求新策略分支使用后端预览派生面积，旧兼容路径单独说明。
6）旧收货接口旁路：PurchaseReceiptController 的旧增/改/单项确认调用 receiveOrderItem，仅拒绝加工方案，仍可绕过新策略逐卷实际输入，并缺少行归属检查。要求旧入口拒绝新快照/需逐卷实际行并验证父单归属，统一窗口继续按实际参数收货。

## 补修独立复核

已逐项读取补修差异与最终原始输出：

| 初次问题 | 最终代码与行为结果 |
|---|---|
| 采购保存冻结及跨单归属 | 计算明细前将父单 ID 作为归属依据，拒绝不属于当前订单的旧行；页面预览也将 orderId 放父 DTO。采购测试用“只有父单 ID、行没有 ID”的页面实际形状，档案已改成 KG 后仍沿用旧面积策略与285×315尺寸。 |
| 无快照历史采购行 | 沿用旧记账/采购/计价单位及旧行尺寸，不采用新档案策略；测试仍得88.3386㎡且新快照保持为空，跨单测试拒绝。 |
| 称重回显和重新确认 | 页面从 snapshot.result.actual 回显并合并服务器 actual；仅未传 measurementActual 且原采购数量不变时允许后端沿用旧净重。显式清空净重、改变数量但仍带旧实际数量、改变数量且未提供净重均拒绝；前端数量/单位变化清空净重。后端采购回归及新增 Vue 变数量回归覆盖。 |
| 取消/到期与并发 | H2 实库 Mapper/Spring 事务测试调用 releaseByOrder/processTimeout，释放本订单而另一订单仍被保护；两线程竞争同一卷只有一个分配成功，余额与流水不变。这是 H2 并发证据，未宣称生产 MySQL 并发已经验证。 |
| 逐卷面积展示 | 预览 API 扁平契约新增 inventoryArea，由后端 MeasurementCalculator 派生；新策略逐卷展示取预览结果。序列化测试验证保留原 priceQuantity 顶层字段且新增面积。前端仅保留无新策略历史路径的既有 rollArea。 |
| 旧收货接口 | 单项旧接口验证明细归属，并拒绝有新快照或按卷采购的行，提示统一实际收货窗口；拒绝测试断言 mapper 未发生收货更新。 |
| 余卷成本与红冲（主 Agent 补充自查发现） | 历史1ROLL/10KG/成本100，40m实退6KG仍记1ROLL，返还成本60；退料红冲后原发料红冲携带原实发快照，恢复1ROLL/10KG/50m/成本100，原发料金额不变，三本账一致。008 再复核补充11KG超过原10KG的反例，最终上限校验拒绝且入库单、流水数量及余额均不变。 |

| 补修日志 | 最终成功汇总 |
|---|---|
| `.tmp/dual-measure/review-fixes-final-tests.log` | Maven 53：核心12、采购冻结6、预留/余卷生命周期9、原卷材生命周期8、旧采购双单位7、收货确认11；零失败/错误，BUILD SUCCESS，`.exit=0`。 |
| `.tmp/dual-measure/review-roll-final-tests.log` | 最后补计价量上限后，独立重跑生命周期9例通过，`.exit=0`；与前述53例有重复，不累计为62例。 |
| `.tmp/dual-measure/review-fixes-final-ui-tests.log` | 2文件、23例通过，包含父单归属回显、数量变化净重失效，`.exit=0`。 |
| `.tmp/dual-measure/review-fixes-receipt-ui-tests.log` | 收货组件 node8例，零失败，`.exit=0`。 |
| `.tmp/dual-measure/stock-preview.log` 与 `.png` | 实际即时库存 Vue 组件使用固定 API mock；2ROLL/244㎡、984PCS/88.3386㎡、40m余卷1ROLL/48.8㎡/6KG、螺丝面积—可见；pageerror=[]、业务写请求=[]。Vite 热更新 WebSocket 被环境拦截，不影响上述页面展示核对。 |

验收者未修改实现；补修由主 Agent 完成。上述测试范围是隔离行为和页面展示，不能推定已完成真实业务单运行态闭环。

## 原始输出复核

| 原日志 | 复核到的结果 | 证明范围 |
|---|---|---|
| `.tmp/dual-measure/core-tests.log` | Maven 19，零失败/错误，BUILD SUCCESS | 初始公式 7 + 模板 12；后续公式扩展由最终业务集覆盖。 |
| `.tmp/dual-measure-master/maven.log` | Maven 71，零失败/错误，BUILD SUCCESS | 档案 prepare、导入及旧入口替身/隔离行为。 |
| `.tmp/dual-measure-master/vitest.log` | 4 文件、80 例通过 | Vue 组件/模型测试，接口替身。 |
| `.tmp/dual-measure/business-final-retry.log` | Maven 91，零失败/错误，BUILD SUCCESS | H2 隔离 Mapper/Spring 事务及 Mockito 依赖；不是运行中 MySQL 真实订单。 |
| `.tmp/dual-measure/purchase-ui-tests.log` | 2 文件、21 例通过 | 采购组件隔离测试。 |
| `.tmp/dual-measure/receipt-ui-tests.log` | node 8 例通过 | 收货组件及预览接口替身。 |
| `.tmp/dual-measure/validate-final.log` | 全部门禁有通过输出，末尾库存五项零 | 实现方统一 validate 记录；008 不据编译结果推定交易行为。 |

原失败日志仍保留，最终成功日志没有抹除初次失败。原91例不覆盖本报告初次发现的边界；补修新增/调整测试后的53例及最终生命周期9例针对缺口复核，不将两次测试简单相加为无重复用例总数。

## 真实业务库只读核查

使用仓库现有 MysqlSession 的只读连接 jjx_ro 查询，证据保存 `.tmp/dual-measure-review/business-readonly.json`、`schema-readonly.json`。

| 只读事实 | 输出 |
|---|---|
| RM000350 档案 | material_id=354，FILM，记账单位㎡，宽1220mm，名义长100000mm，计量策略为空。 |
| RM000350 有效批次 | item_id=4/5，卷号 PO261118001-1-R2/R3，各122㎡，各卷预留0；合计2卷、244㎡。 |
| RM000352 档案/批次 | material_id=356，FILM，285×315mm，记账PCS；item_id=1/2，634+350=984PCS。公共公式据此得88.3386㎡；不是运行接口返回的面积值。 |
| 迁移账本及结构 | ops.schema.version=376；375/376 指纹与归档文件 SHA256 一致；物料策略、五张业务快照表列、四个预留字段均存在。 |
| 新数据痕迹范围 | 本次只读时物料非空策略、采购非空快照及流水非空快照均为0；这仅证明当前查询结果，不替代审计历史。 |
| 流水/余额 | 008 独立执行 `bash scripts/check-stock-summary.sh --strict`，五项0，保存退出码0至 `.tmp/dual-measure-review/stock-strict.log`。 |

迁移指纹：375 `d2b13f30cd5402f5ecdee313dda0d83771a04a619110c4d62553470a95556375`；376 `f604e5f783a16484ff28eef16d4539eb4d2cc6fada8cce4be4a4fc6579874fe1`。

## 未验范围

新后端尚未部署，本验收不启动、停止或重启服务。真实档案配置保存、真实采购保存/收货/质检入库、真实订单预留/领料/余卷闭环及生产 MySQL 并发未做；H2 事务测试与页面 mock 均不能替代这些运行态证据。页面截图由主 Agent 单独进行隔离接口数据展示核查，不据此宣称真实后端已生效。


## 归档补记（OpenClaw 交接后填写，不构成二次独立验收）

本节只补记交付事实，不改变上文由 Codex-review 独立出具的核查结论与署名。

1）交接：用户 2026-11-18 明确“没额度了 交接给openclaw做”；Codex 停止全部写入，OpenClaw（main/大黄，本机单行 CAS 接管）只做暂存、提交、推送与归档，不改实现、不改业务库、不启停服务。
2）007 补修提交：`877de6b5`（13 个白名单源文件，206 增 30 删），已 push 远端 dev；暂存仅限 007 白名单，未含用户产品编辑 WIP（`ProductCodeGenerator/index.vue`、`ProductFormContent.vue`）及 SQL/备份在制改动。
3）统一门禁：`.tmp/dual-measure/review-final-validate.exit=0`，日志 `.tmp/dual-measure/review-final-validate.log`（整仓 validate 全链通过，末段库存 strict 五项 0 且三本账一致）。此前 `.tmp/dual-measure/review-fixes-validate.exit=2` 是 API 预览类型缺父 `orderId` 的旧失败，已由 877de6b5 中 `orderId?: number` 修复并复跑通过；原失败记录保留未抹除。
4）未重跑：交接后未重复执行 53/9 项 Maven 与 23+8 项 UI 测试，依据是既有 `.exit=0` 落盘结果，且避免 3GiB TS 与 Maven 并发重载；该取舍不改变上文对补修行为证据的复核结论。
5）产物边界：本报告与 `jjx-docs/history/INDEX.md` 单独提交（任务码 dev-20261118-008），与 007 代码提交隔离。
