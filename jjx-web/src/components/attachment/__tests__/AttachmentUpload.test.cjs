/**
 * AttachmentUpload 组件交互与共享 Session 接线回归（dev-20261016-048 第一阶段 A）
 *
 * 运行：node --test src/components/attachment/__tests__/AttachmentUpload.test.cjs
 *
 * 做法：编译真实 AttachmentUpload.vue 的 <script setup>，执行其 setup，
 * 直接调用组件真实的选文件回调 onFiles(...)（= AttachmentTempUploader 'files' 事件的处理函数），
 * 断言其确实写共享 Session（而非自建队列/自行绑定）：
 *  - 选中文件 → session.upload → 待保存队列立即可见，emit uploaded/changed。
 *  - 归属在发起时固定（产品级 ownershipKey）。
 *  - 多文件逐个上传；上传失败 emit error 且不改队列。
 *  - disabled / blockedReason / 只读（无 edit 权限）→ 不产生上传。
 */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const nodeCrypto = require('node:crypto')
const { parse, compileScript } = require('vue/compiler-sfc')

const src = path.resolve(__dirname, '../../..')
const tick = () => new Promise((resolve) => setImmediate(resolve))

function makeStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(String(k), String(v)),
    removeItem: (k) => map.delete(k),
    key: (i) => [...map.keys()][i] ?? null,
    get length() { return map.size },
  }
}
const fileOf = (name, size = 10) => ({ name, size, lastModified: 1, arrayBuffer: async () => new ArrayBuffer(4) })

/** 真实 useAttachmentSession + 可控 IO mock；返回 { session, calls } */
function buildSession(sessionOptions, opts = {}) {
  const calls = []
  let seq = 500
  const api = {
    drafts: () => Promise.resolve({ data: [] }),
    updateDraft: () => Promise.resolve({ data: null }),
    removeDraft: () => Promise.resolve({ data: null }),
    listRefs: () => Promise.resolve({ data: [] }),
    documentTypes: () => Promise.resolve({ data: [] }),
    tempUpload: (file, requestKey, onProgress, meta, draft) => {
      calls.push(['tempUpload', file.name, draft?.draftKey ?? null])
      return (opts.tempUpload || (() => Promise.resolve({ data: ++seq })))(file, requestKey, onProgress, meta, draft)
    },
    unbind: () => Promise.resolve({ data: true }),
    batchUpdate: () => Promise.resolve({ data: 1 }),
  }
  const sandbox = {
    console,
    crypto: nodeCrypto.webcrypto ?? globalThis.crypto,
    TextEncoder,
    window: { open() {}, location: { pathname: '/', search: '', hash: '' } },
    localStorage: makeStorage(),
    sessionStorage: makeStorage(),
    exports: {},
    module: { exports: {} },
  }
  let currentFile = src + '/'
  const vueStub = { ...vue, onBeforeUnmount() {}, onMounted() {}, onDeactivated() {}, onUnmounted() {} }
  const elplus = { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: { confirm: () => Promise.resolve() } }
  function requireImpl(name) {
    if (name === 'vue') return vueStub
    if (name === 'element-plus') return elplus
    if (name === '@/api/system/attachmentRef') return { attachmentRefApi: api }
    if (name === '@/api/system/attachment') return { attachmentApi: { list: () => Promise.resolve({ data: [] }), listByTrace: () => Promise.resolve({ data: [] }) } }
    if (name === '@/composables/usePermission') return { usePermission: () => ({ hasPermission: () => true }) }
    if (name === '@/store/modules/user') return { useUserStore: () => ({ userId: 1, permissions: ['*'] }) }
    if (name.endsWith('.vue')) return { render: () => null }
    const file = name.startsWith('@/')
      ? path.join(src, name.slice(2) + '.ts')
      : path.resolve(path.dirname(currentFile), name.endsWith('.ts') ? name : `${name}.ts`)
    const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: file }).outputText
    const mod = { exports: {} }
    const prev = currentFile
    currentFile = file
    try {
      vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename: file })(mod.exports, sandbox.require, mod)
    } finally { currentFile = prev }
    return mod.exports
  }
  sandbox.require = requireImpl
  const sessionMod = (() => {
    const file = path.join(src, 'composables/useAttachmentSession.ts')
    const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: file }).outputText
    const mod = { exports: {} }
    vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename: file })(mod.exports, sandbox.require, mod)
    return mod.exports
  })()
  return { sessionMod, session: sessionMod.useAttachmentSession(sessionOptions), calls }
}

/** 编译并执行真实 AttachmentUpload 的 setup，返回 bindings 与 emit 记录 */
function setupUpload(props) {
  const filename = path.resolve(src, 'components/attachment/AttachmentUpload.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'au-test' })
  const { sessionMod } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const emitted = []
  let currentFile = filename
  const vueStub = { ...vue, onBeforeUnmount() {}, onMounted() {}, onDeactivated() {}, onUnmounted() {} }
  const sandbox = {
    console,
    exports: {},
    module: { exports: {} },
    require(name) {
      if (name === 'vue') return vueStub
      if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: { confirm: () => Promise.resolve() } }
      if (name === '@/api/system/attachmentRef') return { attachmentRefApi: { previewUrlByRef: (id) => `/refs/${id}/preview`, downloadUrlByRef: (id) => `/refs/${id}/download` } }
      if (name === '@/composables/useAttachmentSession') return sessionMod
      if (name.endsWith('.vue')) return { render: () => null }
      const file = name.startsWith('@/')
        ? path.join(src, name.slice(2) + '.ts')
        : path.resolve(path.dirname(currentFile), name.endsWith('.ts') ? name : `${name}.ts`)
      const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: file }).outputText
      const mod = { exports: {} }
      const prev = currentFile
      currentFile = file
      try {
        vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename: file })(mod.exports, sandbox.require, mod)
      } finally { currentFile = prev }
      return mod.exports
    },
  }
  const mod = { exports: {} }
  const code = ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: 'AttachmentUpload.ts' }).outputText
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename })(mod.exports, sandbox.require, mod)
  const component = mod.exports.default
  const bindings = component.setup(props, { expose() {}, emit: (...args) => emitted.push(args), attrs: {}, slots: {} })
  return { bindings, emitted }
}

test('选中文件只上传临时文件并 emit 结果，不自行写待保存队列', async () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const { bindings, emitted } = setupUpload({ session })
  await bindings.onFiles([fileOf('a.pdf')])
  await tick()
  assert.equal(session.pending.value.length, 0)
  const result = emitted.find((e) => e[0] === 'uploaded')[1]
  assert.equal(result.name, 'a.pdf')
  session.registerUploaded(result.pending, result.ownership)
  assert.equal(session.pending.value[0].fileName, 'a.pdf')
  const kinds = emitted.map((e) => e[0])
  assert.ok(kinds.includes('uploaded'))
  assert.ok(kinds.includes('changed'))
})

test('上传回调返回发起时的产品归属，由调用方决定何时入队', async () => {
  const { session, sessionMod } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const ownership = sessionMod.productOwnership({ rowKey: 'row-9', productId: 88, productCode: 'P088' })
  const { bindings, emitted } = setupUpload({ session, ownership })
  await bindings.onFiles([fileOf('b.pdf')])
  await tick()
  assert.equal(session.pending.value.length, 0)
  const result = emitted.find((e) => e[0] === 'uploaded')[1]
  assert.equal(result.ownership.key, 'product:88')
  assert.equal(result.ownership.productCode, 'P088')
  session.registerUploaded(result.pending, result.ownership)
  assert.equal(session.pending.value[0].ownershipKey, 'product:88')
})

test('多文件逐个上传，互不覆盖', async () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const { bindings, emitted } = setupUpload({ session })
  await bindings.onFiles([fileOf('a.pdf'), fileOf('b.pdf')])
  await tick()
  assert.equal(session.pending.value.length, 0)
  const results = emitted.filter((e) => e[0] === 'uploaded').map((e) => e[1])
  assert.equal(results.length, 2)
  results.forEach((result) => session.registerUploaded(result.pending, result.ownership))
  assert.equal(session.pending.value.length, 2)
})

test('上传失败：emit error 且不改队列（失败可见）', async () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 }, { tempUpload: () => Promise.reject(new Error('服务端失败')) })
  const { bindings, emitted } = setupUpload({ session })
  await bindings.onFiles([fileOf('a.pdf')])
  await tick()
  assert.equal(session.pending.value.length, 0)
  assert.ok(emitted.some((e) => e[0] === 'error' && e[1]?.action === 'upload'))
})

test('disabled / blockedReason：不产生上传', async () => {
  const { session, calls } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const { bindings } = setupUpload({ session, disabled: true, blockedReason: '已审核' })
  assert.equal(bindings.canUpload.value, false)
  assert.equal(bindings.disabledTip.value, '已审核')
  await bindings.onFiles([fileOf('a.pdf')])
  assert.equal(calls.length, 0)
  assert.equal(session.pending.value.length, 0)
})

test('只读会话（无编辑权限）：不产生写副作用', async () => {
  const { session, calls } = buildSession({ sourceType: 'quotation', sourceId: 7, readonly: true })
  const { bindings } = setupUpload({ session })
  assert.equal(bindings.canUpload.value, false)
  await bindings.onFiles([fileOf('a.pdf')])
  assert.equal(calls.length, 0)
  assert.equal(session.pending.value.length, 0)
})
