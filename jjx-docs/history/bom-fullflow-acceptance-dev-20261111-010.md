# 多层制造BOM全流程集成验收（H / 010）

任务码：dev-20261111-010（OpenClaw 只读验收）
日期：2026-11-11
范围：B—G（dev-20261111-004 至 009）的提交、定向测试、库内痕迹与反例
结论：**代码级与定向测试通过；运行态展示未验证（8080 仍为 17:41 旧包）**

## 一、验收对象与提交

| 卡 | 提交 | 主要产物 |
|---|---|---|
| 004 制造BOM后端 | 4efacab0、06301838、ffe6f759 | EngineeringBomServiceImpl 主方案回读/多层可达、图锁前置 |
| 005 制造BOM窗口 | 0506899f | 前端窗口：新建下层/逐层采用/版本与换算依据，12 文件 |
| 006 净需求 | e05e6d90、2ca807cb（+ c2608f9d 索引） | 只读 preview、有理数精确比例、供给口径修复、confirm 提案 |
| 007 需求计划 | 138be8c4 | 需求计划只读试算页（消费 006 preview） |
| 008 半成品闭环 | 78ece6a2、082b176c | 产出身份（迁移 359）、完工入库 MATERIAL 分支、工单列表列 |
| 009 生产资料展示 | 381a6db5、7f6545c8 | G1 快照接口、G2 前端 ManufacturingProcessSheet |

## 二、四查结果

### 1）白名单
1）每张卡的提交都只落在自身域：004/005 在 engineering/bom 与窗口组件，006 在 production 净需求，007 在 requirement-plan，008 在 production 订单与 inventory 入库，009 在快照服务与 product-spec 组件。
2）未见跨卡夹带：006 的 INDEX 一行是验收人代登记；008 的迁移 359 为本卡结构变更；009 的 G2 由 Hermes 执行、文件在其白名单内。
结论：通过。

### 2）真实输出（独立复跑，不采信自报）
1）后端合并批次：`mvn -o -Dtest=ManufacturingRequirement*Test,FinishOutputIdentityTest,ManufacturingHistorySnapshotTest` → **46 例全过**（Supply 5 / SupplyPolicy 13 / Netting 13 / Precision 7 / FinishOutputIdentity 4 / Snapshot 4），0 失败 0 错误，RC=0。
2）前端合并批次：`vitest run requirementPlan.spec.ts manufacturingProcessSheet.spec.ts --maxWorkers=1` → **2 文件 7 例全过**，RC=0。
3）`vue-tsc --noEmit` → RC=0。
结论：通过（覆盖代码级与定向行为）。

### 3）痕迹（只读查库）
1）迁移账本已含 356、357、358、359。
2）结构：production_order 的 output_type/output_id 存在（2 列）；inventory_stock_item 的 roll_no/roll_width_mm/original_roll_length_m/source_inbound_item_id/active_roll_key 存在（5 列）。
3）卷号唯一性：正余额卷号重复 0 行。
4）库存三本账：`check-stock-summary.sh --strict` RC=0，五项全 0（数量漂移/无汇总/无明细/批次≠流水/孤儿流水），输出“三本账一致”。
结论：通过。

### 4）反例
1）DRAFT/PENDING 采购不进确定供应、REJECTED/CANCELLED 完全排除（006 代码与 13 例 SupplyPolicy 覆盖）。
2）部分收货逐行 max(quantity−received,0)，超收给 warning 不倒挂。
3）MATERIAL 工单缺产出对象或材料不存在 → BusinessException 阻断，不降级成 PRODUCT（008 的 4 例测试覆盖）。
4）快照缺失层返回 SNAPSHOT_PARTIAL，不用最新版本顶替（009 测试与实现一致）。
5）需求页明确只读、不开单、不预留（007 页面文案与实现一致）。
结论：通过。

## 三、未验证项（不得当作已通过）

1）运行态展示：8080 当前进程启动于 2026-11-11 17:41，早于 008/009 的提交（18:02 之后），新代码未部署；页面实点与真实工单快照展示均未验证。
2）008 真实闭环：P01–P04/I01–I04 的真实单据流（半成品工单→合格入库→上层领用）未在运行实例跑过；领用沿用既有物料出库链路，未新增代码。
3）009 真实数据：库内工单未绑定多层 BOM/路线/规范的组合样本，快照在真实数据上的完整性未验证。
4）010 范围内的 013 卷材逐卷场景按用户口径跳过写入型验证，列未覆盖。

## 四、结论

1）B—G 代码与定向测试：通过。
2）运行态与真实闭环：未验证，需部署新包后另行执行。
3）建议：部署前先确认迁移 356/357/358/359 状态（已全部落库）；部署后再补一次运行态验收，补齐本报告第三节。
