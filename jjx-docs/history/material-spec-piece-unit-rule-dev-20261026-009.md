# 计件材料「规格及模数」口径统一（三处同源）

任务码：dev-20261026-009
日期：2026-10-26
用户口径：2026-10-26「0.6T 020 蓝灯=1PCS 不应该是 0.6T 020 蓝灯1个/PCS 吗？」→ 按建议改，作业规范 / BOM 打印 / 定版固化三处同步。

## 1）问题

1）作业规范正文与打印对计件材料（单位 PCS/个）输出「规格=1PCS」，「=模数PCS」读作「一模出 1PC」，与「每 1PCS 用 1 个」的业务含义不符。
2）同一句话此前有三个生成点，各自维护：`docset.ts` 的 `workSpecMaterialSpec`、`components/print/bomPrintLayout.ts` 的 `bomMaterialSpec`、后端 `ProductWorkSpecService.projectMaterialParams`；dev-20261022-020 的计件兜底只补进了 BOM 打印，作业规范与定版没有。

## 2）现行口径（命中即停）

1）无尺寸、无跳距、单位 PCS/个、模数=1 → `规格 + N个/PCS`（N = 基数 ÷ 模数，基数空缺按 1）。
2）有跳距或规格 → `规格(+跳X)=模数PCS`（卷料/拼版，口径不变）。
3）无规格的计件兜底：基数=1 且模数为空或 0<模数<1 → `1个/PCS`（dev-20261022-020 口径保留）。
4）模数为空/纯零且无尺寸：有规格只输出规格，不加后缀。

样例：规格 `0.6T 020 蓝灯`、单位 PCS、模数 1、基数 1 → `0.6T 020 蓝灯1个/PCS`（改前为 `0.6T 020 蓝灯=1PCS`）。

## 3）实现与验证

1）唯一实现 `jjx-web/src/utils/materialSpec.ts` 的 `composeMaterialSpec`；`docset.workSpecMaterialSpec` 与 `bomMaterialSpec` 只做转出（打印侧把空串渲染成 `-`），不再各自拼装。
2）后端 `ProductWorkSpecService.projectMaterialParams` 按同一判定顺序镜像，定版时把结果固化进 `params`。
3）用例：`src/utils/__tests__/materialSpec.spec.ts`（7 例，含 6 条约定样例）、`components/print/__tests__/bomPrintLayout.spec.ts` 更新 1 条断言（计件+模数 1 由 `-` 改 `1个/PCS`）、后端 `paramsFollowSharedMaterialSpecRule`。
4）已定版版本快照里的 `params` 早已固化，本次只影响之后的计算与定版，不回改历史快照。
