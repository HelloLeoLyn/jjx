# 样品单一单一成品修复方案与实施记录

任务：dev-20260930-013；执行者：Codex；日期：2026-09-30。

## 背景与证据

用户确认一张样品单只对应一个成品，可打样多件。原新增/编辑共用表单仍有产品明细表和“添加产品”，前端只校验至少一条；新增服务拒绝多产品，编辑服务没有同样限制。编辑替换明细后没有同步 sales_sample_order 产品快照。

证据文件：jjx-web/src/views/sales/sample-order/index.vue；jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java 的 createSample、updateSampleOrder、copyQuotationItemsToOrder。

## 修复方案及实施

1. 新增、编辑使用单个 product 表单对象，展示打样产品、编码、名称、打样数量和单位；移除多行增删。接口仍传一条 items，兼容既有接口。
2. 单产品报价带入客户和产品，来源报价选定时锁定客户和产品。多产品报价不截取首项，提示去现有报价单“转为样品单”入口按产品拆单；整张报价已转换的原语义保持。无产品、加载失败、加载中均禁止提交；切换/清除报价忽略旧请求结果。
3. 新增、编辑服务和 DTO 校验只允许一个产品，数量为正整数；新增无产品且无报价直接拒绝，报价回退复制同样拒绝空、多产品及无效数量。
4. 编辑保存明细后同步样品单产品快照；历史多产品样品单在编辑前明确拦截，避免只取首行导致静默丢失。

范围：上述页面、服务、两个 DTO、SampleOrderSingleProductTest、本文及 history/INDEX.md。没有迁移、历史数据订正、库存变动或服务生命周期操作。开工其他会话改动未纳入任务。

## 验证结果

- 后端：mvn -o -Dtest=SampleOrderSingleProductTest -DargLine=-javaagent:/home/administrator/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar test，通过 12 项（新增/编辑拒绝多产品、缺产品、零/负/空数量，正常单产品多件编辑及快照同步，DTO 约束）。普通运行受沙箱 Mockito 自附加限制，使用显式 javaagent 后通过。
- 前端：vue-tsc --noEmit 通过；单文件 prettier 格式化；从实际 Vue script 提取并执行表单函数，通过 10 个行为场景：新增、编辑、无效数量、换客户清空、单产品报价、多产品报价阻止提交、清除报价、过期响应忽略、编辑回显、历史多产品保护。此项为模拟接口的函数级验证，不是浏览器端到端实测。
- npm run validate / npm run build：未全绿，均被原有 engineering/product-spec/index.vue:431 的 OUTBOUND_STATUS 局部状态映射门禁阻断，该代码在 HEAD 已存在；未扩充状态基线。
- 单独 vite build --outDir /tmp/jjx-sample-build-dist：被另外两处原有错误阻断：inventory/material/components/MaterialCategory.vue 是空 SFC；system/eventConfig/index.vue:239 模板插值语法错误。没有修改这两个范围外文件。
- 文档门禁、样品单 SFC script/template 编译、git diff --check：通过。

## 上线与待办

本次仅代码修复和验证，未启动、重启或替换运行服务。后端新校验需要用户更新运行版本并重启后生效；前端正式打包需先处理上述独立构建阻断。上线后人工验收新增一个成品多件、编辑更换成品、单产品报价带入、多产品报价拆单提示。
