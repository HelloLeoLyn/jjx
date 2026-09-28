# IQC 与 NCR 报废审批阈值口径决策（2026-09-28）

> 关联任务：dev-20260928-030
>
> 状态：待审核；本文记录当前实现的明确边界，避免把 IQC 报废和成品/NCR 报废误认为同一条审批规则。

## 决策建议

保留两种场景的差异，但统一写明适用范围：

| 场景 | 审批规则 | 原因 |
|---|---|---|
| IQC 来料报废 | **每单都进入报废审批** | 来料处置会改变隔离台账、供应商责任和来料检验批结论，不能按数量阈值绕过品质审批 |
| 非 IQC 的 NCR/成品报废 | 数量 `<= 5` 可直接完成；数量 `> 5` 进入审批 | 沿用 `sys_config.quality.ncr.scrap.approval-threshold`，当前配置值为 5 |

## 实现边界

- `quality.ncr.scrap.approval-threshold` 只适用于非 IQC/NCR 成品报废治理；
- IQC 报废由 `inventory_iqc_scrap_order` 统一进入 `PENDING_APPROVAL`，不读取该阈值绕过审批；
- IQC 报废审批通过后，才同步有效 NCR SCRAP 动作、累计批次有效处置并推进隔离状态；
- IQC 报废驳回后，报废申请作废，隔离剩余量恢复，状态回到待处置；
- 审批接口必须幂等，重复审批不得重复写 NCR 动作或数量。

## 证据

- `QualityNcrServiceImpl.scrapApprovalThreshold()` 读取配置值，当前数据库配置为 `5`；
- `QualityNcrServiceImpl.scrapGovernanceApplies()` 明确 IQC 不适用成品报废阈值；
- `InventoryInboundServiceImpl.approveIqcScrap()` 对 IQC 报废单统一要求 `PENDING_APPROVAL`；
- `InventoryInboundController` 的 IQC 报废审批接口仅允许 `quality:ncr:dispose`。

## 验收

1. IQC 报废 1 件和 100 件均生成待审批单；
2. 非 IQC 报废按配置值 5 验证阈值分流；
3. IQC 通过只产生一次有效 SCRAP 动作；
4. IQC 驳回不产生有效 SCRAP 动作，隔离剩余量恢复；
5. 修改阈值不会改变 IQC 报废审批规则。

