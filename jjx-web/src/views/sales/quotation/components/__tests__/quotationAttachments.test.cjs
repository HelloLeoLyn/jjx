/**
 * 报价单附件接入回归（dev-20261016-051）—— 契约 + 静态守卫
 *
 * 为什么不再 mount 真组件：手搓 vm 壳曾导致自转/整机 OOM（事故 dev-20261016-059）。
 * 本文件只做两件事：
 *  A. 静态守卫：读 QuotationFormDialog.vue / index.vue 源码，校验接线形状。
 *  B. 纯函数契约：加载 useAttachmentSession（真模块 + stub IO）断言归属键语义。
 * 共享会话/保存的完整契约由 dev-20261016-048 的 useAttachmentSession.test.cjs 覆盖。
 *
 * 运行：node --test src/views/sales/quotation/components/__tests__/quotationAttachments.test.cjs
 */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')

const srcRoot = path.resolve(__dirname, '../../../../..')
const DIALOG = path.resolve(srcRoot, 'views/sales/quotation/components/QuotationFormDialog.vue')
const INDEX = path.resolve(srcRoot, 'views/sales/quotation/index.vue')
const dialogSrc = () => fs.readFileSync(DIALOG, 'utf8')
const indexSrc = () => fs.readFileSync(INDEX, 'utf8')

// ---------- A. 静态守卫（纯源码检查） ----------
test('静态守卫：整单唯一 Manager + 双 Upload（整单/行级）+ 不再挂旧 Center', () => {
  const d = dialogSrc()
  assert.equal((d.match(/<AttachmentManager/g) || []).length, 1, '整单只能有一个管理区')
  assert.equal((d.match(/<AttachmentUpload/g) || []).length, 2, '整单 + 行级各一个上传入口')
  assert.equal((d.match(/<AttachmentCenter/g) || []).length, 0, '报价弹窗不得再挂旧 Center')
  assert.ok(!/attachmentCenterRef|bindPendingAttachments|bindRowPendingAttachments|onAttachSave|getMissingTypeCount/.test(d), '不得残留旧 Center ref / 旧保存入口')
})

test('静态守卫：会话由容器创建并显式下传（不用全局单例）', () => {
  assert.ok(/useAttachmentSession\s*\(/.test(dialogSrc() + indexSrc()), '应由调用方 useAttachmentSession 建会话')
  assert.ok(/:session=|session=/.test(dialogSrc()) || /:session=/.test(indexSrc()), '会话应显式下传')
  assert.ok(!/window\.__attachmentSession|globalThis\.__attachmentSession/.test(dialogSrc()), '不得用全局单例')
})

test('静态守卫：不再直读旧 Center ref（容器仍保留 flushStoredQueue 旧队列衔接）', () => {
  assert.ok(!/attachmentCenterRef/.test(dialogSrc()), '弹窗不得再引用旧 Center ref')
  assert.ok(!/attachmentCenterRef/.test(indexSrc()), '容器不得再引用旧 Center ref')
})

test('静态守卫：默认不暴露附件保存入口（不强制打开保存按钮）', () => {
  const d = dialogSrc()
  assert.ok(!/defaultShowSave|showSaveByDefault|:show-save="true"/.test(d), '默认不得打开附件保存按钮')
})

test('复制报价只准备新增数据，前端不二次拼接编号或提前新增', () => {
  const source = indexSrc()
  const handler = source.match(/const handleCopy = async \(row\?: any\) => \{([\s\S]*?)\n\}/)?.[1]
  assert.ok(handler, '复制处理器应存在')
  assert.match(handler, /ids\.value\.length === 1/, '工具栏复制必须恰好选择一条')
  assert.match(handler, /await quotationApi\.copy\(quotationId\)/, '调用复制预览接口')
  assert.match(handler, /form\.quotationId = undefined/, '复制结果必须走新增分支')
  assert.doesNotMatch(handler, /COPY_\$\{|quotationApi\.(add|edit)\(/, '编号由后端提供，复制预览不落库')
})

// ---------- B. 纯函数契约（加载真模块，不跑响应式/组件） ----------
function loadSessionModule() {
  const file = path.join(srcRoot, 'composables/useAttachmentSession.ts')
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: 'useAttachmentSession.ts',
  }).outputText
  const sandbox = { console, Promise, TextEncoder, queueMicrotask, setTimeout, clearTimeout, module: { exports: {} }, exports: {} }
  const req = (name) => {
    if (name === 'vue') return vue
    if (name === '@/api/system/attachmentRef') return { attachmentRefApi: { updateDraft: () => Promise.resolve(), removeDraft: () => Promise.resolve(), createRef: () => Promise.resolve({ data: 1 }) } }
    return {}
  }
  sandbox.require = req
  vm.runInNewContext('(function (exports, require, module) {' + code + '\n})', sandbox, { filename: 'useAttachmentSession.ts' })(sandbox.module.exports, req, sandbox.module)
  return sandbox.module.exports
}
const S = loadSessionModule()

test('归属契约：orderOwnership = 整单级', () => {
  const o = S.orderOwnership()
  assert.equal(o.key, 'order'); assert.equal(o.kind, 'order'); assert.equal(o.productId, null)
})

test('归属契约：有正式 product_id → product:<id>', () => {
  const o = S.productOwnership({ rowKey: 'r1', productId: 88, productCode: 'P088' })
  assert.equal(o.key, 'product:88'); assert.equal(o.kind, 'product'); assert.equal(o.productId, 88)
})

test('归属契约：无 product_id → row:<稳定行键>，不冒充 product_id', () => {
  const o = S.productOwnership({ rowKey: 'r1', productCode: 'P9' })
  assert.equal(o.key, 'row:r1'); assert.equal(o.productId, null); assert.equal(o.productCode, 'P9')
  assert.ok(!o.key.startsWith('product:'), '临时行键不得冒充 product_id')
})

test('归属契约：同编码不同行 → 键不同（不串行）', () => {
  assert.notEqual(S.productOwnership({ rowKey: 'a', productCode: 'SAME' }).key, S.productOwnership({ rowKey: 'b', productCode: 'SAME' }).key)
})

test('归属契约：ownershipKeyOfPending 以上传时固定的 ownershipKey 为准', () => {
  assert.equal(S.ownershipKeyOfPending({ ownershipKey: 'row:x', productCode: 'P1' }), 'row:x')
  assert.equal(S.ownershipKeyOfPending({ ownershipKey: '', productCode: null }), 'order')
})

test('归属契约：ownershipKeyOfRef 正式 productId 优先', () => {
  assert.equal(S.ownershipKeyOfRef({ productId: 7, productCode: 'P7' }), 'product:7')
  assert.equal(S.ownershipKeyOfRef({ productId: null, productCode: null }), 'order')
})
