# 订单金额汇总统一左对齐与同类问题排查

任务：dev-20261007-013；日期：2026-10-07；执行者：Codex。

## 背景与实施

用户批准 order/edit 金额汇总统一左对齐，并询问其他页面是否有类似问题。OrderForm 的税率、运费、折扣用 el-input-number（默认居中），小计、税额、总金额及外币总金额用只读 el-input（默认居左）。

在金额汇总三行 el-row 上添加 amount-summary 类，局部 scoped deep 样式将该区域 el-input-number .el-input__inner 设为 text-align:left，沿用报价单已有处理方式。只调整此区域，不改金额计算、单位、精度或业务数据。编辑、新增和样品转量产共用 OrderForm，均同步生效。

开工已有 BomDetailTable.vue 未提交改动，不修改、不暂存。

## 同类页面只读排查

通过扫描 views 下 el-form-item 金额/税率/费用等标签与控件类型，并复核具体表单和局部/全局样式：

| 页面 | 证据 | 结论 |
|---|---|---|
| 采购订单付款弹窗 OrderPaymentDialog.vue | 15、20行订单金额/已付金额是 disabled el-input；42行申请金额是 el-input-number，无局部对齐覆盖 | 同一表单金额框混合左对齐与居中，尚未修改 |
| 销售退货退款 sales/return/index.vue | 105行退货金额直接文本展示；106行退款金额 el-input-number、controls=false，无对齐覆盖 | 同一退款弹窗金额文本靠左、数字输入居中，尚未修改 |
| 报价单 QuotationFormDialog.vue | 金额汇总已有 amount-summary 和局部输入框左对齐样式 | 金额汇总已处理，无需重复修复 |
| 采购订单编辑 OrderFormDialog.vue | 金额合计/税额/价税合计为汇总文本；明细单价和税率使用 el-input | 未发现本次这种金额汇总输入框混排 |

销售退货新增合计金额和退款输入位于不同弹窗，不将它们误判为同一输入汇总区。上述结论为源码检查，未进行浏览器视觉复核；其他页面本次只读排查。

## 验证与验收

vue-tsc --noEmit、OrderForm 模板/脚本及 scoped CSS 编译、git diff --check、文档门禁通过。CSS 编译结果为 amount-summary[data-v-...] 后代的 el-input-number .el-input__inner，限制在该区域。

npm run validate 仍被既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 状态映射违规阻断，后续门禁未执行；未增加状态基线，未重复前序已阻断的整站构建。未启动/停止/重启服务，未改业务数据。

刷新 order/edit，确认税率、运费、折扣与其他汇总数字统一居左；新增订单/样品转量产同样生效。提交 hash 和上述验证/剩余问题写入 sys_task.remark，提交后置待审核。
