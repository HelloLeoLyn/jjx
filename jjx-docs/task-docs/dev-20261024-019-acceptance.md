# dev-20261024-019 入口覆盖矩阵与证据（wave B）

任务：物料搜索升级Ⅱ：BOM 编辑审核、打样与入库入口适配及行为验收
状态：wave A（bcd12932）+ wave B（本次）合并，待独立验收
最后更新：2026-10-24

---

## 1）入口 × 场景矩阵

| 入口 | 新增 | 编辑 | 整单 / 产品行 | 本次状态 |
|---|---|---|---|---|
| BOM 编辑（BomItemEditor.vue） | BomMaterialSelector + confirm-selection；未确认由 validateItems 阻断 | 同左 | 产品行 | 已覆盖（沿用 023 后代码） |
| BOM 审核（BomApproveDialog / BomItemReviewTable） | — | 解除关联受控落定 + loadBomDetail 回读 | 产品行 | 已覆盖（wave A） |
| 打样 · 工序材料（ProcessMaterialEditor） | BomMaterialSelector + confirm-selection + draft-change | 同左 | 产品行 | 本次补齐 |
| 打样 · BOM 清单（BomPanel） | 同左 | 同左 | 产品行 | 本次补齐 |
| 打样 · 印刷添加（PrintProcessPanel） | 未确认时「添加」按钮禁用 | 同左 | 整单 | 本次补齐 |
| 打样 · 批量添加（workbench 弹窗） | 未选中档案时「添加」禁用 | — | 整单 | 已覆盖 |
| 入库新增（inventory/inbound/create.vue） | 按档案回填 ID/编码/名称/规格/单位；清空同步清 ID | — | 材料行 | 本次修复 |

## 2）三个原始问题的覆盖

1) 清空后仍保留旧 materialId
- BOM 编辑：handleMaterialClear 清 ID/编码/名称/规格/单位
- 打样：clearMaterialRow 清 materialId/code/name/spec/unit/_matKw
- 入库：新增 clearMaterialSelection，@clear 同步清空

2) 未确认文字仍审核旧材料
- BOM 编辑：materialDrafts + validateItems 阻断（原有）
- 打样：confirm-selection + draft-change → 行标记 __matDraftPending；validateMaterials 保存前阻断；序列化剥离标记
- 印刷添加：pickerDraft.pending 时「添加」禁用
- 批量添加：未选中档案时「添加」禁用

3) 关联后显示与回读不一致
- BOM 审核：wave A 已改为保存后 loadBomDetail 回读（bcd12932）

## 3）验证证据

1) 前端定向 Vitest（cgroup 1G）
- inboundMaterial.spec.ts：3/3 通过
- sampleMaterialPending.spec.ts：3/3 通过
2) npx vue-tsc --noEmit：EXIT=0
3) 后端未改（本次只动前端：create.vue 与打样组件/组合式）

## 4）未验证 / 阻塞

- 浏览器实点未验证（服务生命周期未授权，运行态证据待用户或独立验收者补）
- 入库只改选择与表单载荷，未执行库存入账测试
- 020/021 的批量匹配尚未实现（前置为本卡独立验收通过）
