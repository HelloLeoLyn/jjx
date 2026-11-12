# 自制半成品本层领用：代码交付与未验边界

任务dev-20261111-017，执行Codex，独立验收OpenClaw。日期2026-11-12。
用户已确认：半成品有下层制造方案也可领用已有库存，缺口由下层补做；明确取代旧dev-20261111-013卡C排除规则。实际片材PCS/卷材M2为方向，本次不直接改档案或历史数据。

## 改动

InventoryOutboundServiceImpl统一buy/make本层库存资格，预览、生成、缺口、补产、追加入口沿用；make+subBom时经ProductionBomResolver核实下层确为本物料MATERIAL产出且已批准。只读本层库存，不在上层扣下层原料；不足量继续返回，不自动伪造产出。
ProductionBomResolver按工单outputType/outputId判定半成品BOM身份，不再要求MATERIAL制造BOM的product_id等于兼容产品ID；半成品工单缺明确BOM必须报错，不能退回产品当前BOM。PRODUCT历史绑定与版本指针行为保留。不触及019有效期策略。

## 验证与实际限制

真实Service与仓储替身验证make+subBom的部分库存、补产预览、本层本料、生成/预留/调用真实applyDelta后10→9及负流水；错产出、草稿方案、缺BOM、MATERIAL误用产品身份拒绝。来源ProductionMakePickTest、ProductionBomResolverTest；定向数量/退出码追加任务记录。
日志.tmp/bom-next/017-final-tests.log；构建总2G/180秒、Maven768/fork512、flock共享重检查锁。
validate仍先被旧两表model-baseline拦截，未绕过；stock strict五项0，日志017-stock.log。
代码自查/H2或替身不能代替真实MySQL生命周期、已部署API与浏览器验收，本次均未做。库中AUX347/348仍为M、制造BOM为草稿，SFG库存身份错位未订正；不宣称这些真实单据已能闭环。全链下达/报工/质检/入库/上层领用仍由原总卡和第2项验收追踪。
