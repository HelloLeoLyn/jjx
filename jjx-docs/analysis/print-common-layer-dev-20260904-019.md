# 打印公共层：组件+composable 抽取（dev-20260904-019，任务 1417 后续）

状态：⏳待实施（2026-09-04 用户拍板：方案存档→登记任务→Codex 执行；新建公共件供后续打印页使用，已验收老页暂不动）

## 目标

后续打印开发（1418/1419/批量打印/新单据打印）不再重复手搓公共能力；公共件在本任务建成，
1418/1419 起使用，老页面（purchase/order/print、production/order/print、inquiry/print 等）暂不重构，
公共件成熟后再另行清理。

## 现状重复点（各打印页手写一遍）

1. no-print 工具栏（返回+标题+打印按钮）
2. 公司头容器 + 右上角二维码（QRCode.toDataURL + 72px 绝对定位样式；采购/生产/询价 4+ 处复制）
3. 版式切换（系统版/纸版）与 localStorage 记忆（采购、生产各写一遍）
4. 批量打印列勾选组（production 刚做，1418/1419 批量也要）
5. 打印留痕：每页硬编码注册表 id（24/5）；quality_template_registry 有 biz_type 可自动定位

## 交付件（新建，不改造老页面）

### 组件 jjx-web/src/components/print/
1. `PrintToolbar.vue`：props { title, backText? }, emits 或内部处理 window.print；
   工具栏 no-print 样式内置；右侧默认"打印"按钮（slot 可扩展）。
2. `PrintQrCode.vue`：props { text, size?(默认72) }；内部 qrcode 包 toDataURL(width 256)，
   渲染右上角绝对定位 img（白底细边框，position 依赖父容器 position:relative 或自带 relative 包裹）。
3. `PrintColumnPicker.vue`：props { columns: {key,label}[], storageKey, modelValue }；
   checkbox 组 + localStorage 记忆 + 默认全选；emit update。

### composable jjx-web/src/composables/usePrint.ts
4. `usePrintLayout(storageKey, options)`：返回 { layout, setLayout, layouts }，localStorage 读写
   （默认第一个）。
5. `usePrintColumns(storageKey, columns)`：返回 { enabledKeys, toggle, allKeys }，默认全选+记忆。
6. `usePrintLog(bizType)`：按 biz_type 从注册表取对应行（调 getQualityTemplatePage 或现成列表接口，
   过滤 biz_type===bizType 且 status=1，取 printMode/printComponent 非空优先），缓存；返回
   `log(bizId)` → createQualityTemplatePrintLog(registryId, bizType, bizId)；查无行时静默跳过（不弹错）。
   注：多张 QR 对应同一 bizType 时（QR-031/057 同 outbound 页分流）取第一张即可，留痕口径不变；
   保持与现有各页一致的注册表 id 优先（现有页若已显式传 id 的用法继续兼容）。

### 说明/样式
- 纸版主题（宋体细线、黑色边框、右上 编号/日期）与系统版主题差异仍由页面内容区自己写，
  公共件只收敛"工具栏/二维码/版式/列选/留痕"；若后续多页出现相同主题块再抽样式文件。

## 明确不做

- 不重构已验收老打印页（purchase/production/inquiry 等）；
- 不做打印页脚手架生成器/模板文件（等公共件经 2-3 张新页验证后再考虑 starter 模板）；
- 不改后端、不改注册表表结构。

## 验证

- npx vue-tsc --noEmit 绿；组件在仓库内暂无挂载页（纯新建），类型与格式检查通过即可；
- 后续 1418/1419 实现时用本公共件，验收即验证。

## 收尾

任务 → status=2 待审核；1418/1419 description 追加"用打印公共层(dev-20260904-019)实现"。
