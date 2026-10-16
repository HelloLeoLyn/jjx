/**
 * AttachmentManager 渲染决策与保存入口默认隐藏回归（dev-20261016-048 第一阶段 A）
 *
 * 运行：node --test src/components/attachment/__tests__/AttachmentManager.test.cjs
 *
 * 做法：用 vue/compiler-sfc 编译真实 AttachmentManager.vue 的 <script setup>，
 * 在隔离沙箱里执行其 setup（IO 边界用 mock），断言其对外决策：
 *  - 不内置上传入口（不产生任何上传相关绑定）。
 *  - 行内/批量保存按钮**默认隐藏**；仅在调用方开启且会话已注入处理器时出现（无处理器不开放入口）。
 *  - 只读（无权限）不产生写按钮。
 *  - 归属筛选只改变显示范围，隐藏行的改动仍完整在会话里可取。
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

/** 用真实 useAttachmentSession 构造共享会话（拦截 IO 边界）。 */
function buildSession(sessionOptions) {
  const cache = new Map()
  const api = {
    drafts: () => Promise.resolve({ data: [] }),
    updateDraft: () => Promise.resolve({ data: null }),
    removeDraft: () => Promise.resolve({ data: null }),
    listRefs: () => Promise.resolve({ data: [] }),
    documentTypes: () => Promise.resolve({ data: [] }),
    tempUpload: () => Promise.resolve({ data: 1 }),
    unbind: () => Promise.resolve({ data: true }),
    batchUpdate: () => Promise.resolve({ data: 1 }),
    previewUrlByRef: (id) => `/refs/${id}/preview`,
    downloadUrlByRef: (id) => `/refs/${id}/download`,
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
  const elplus = { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: { confirm: () => Promise.resolve() } }
  const vueStub = { ...vue, onBeforeUnmount() {}, onMounted() {}, onDeactivated() {}, onUnmounted() {} }
  function requireImpl(name) {
    if (name === 'vue') return vueStub
    if (name === 'element-plus') return elplus
    if (name === '@/api/system/attachmentRef') return { attachmentRefApi: api }
    if (name === '@/api/system/attachment') return { attachmentApi: { list: () => Promise.resolve({ data: [] }), listByTrace: () => Promise.resolve({ data: [] }) } }
    if (name === '@/composables/usePermission') return { usePermission: () => ({ hasPermission: () => true }) }
    if (name === '@/store/modules/user') return { useUserStore: () => ({ userId: 1, permissions: ['*'] }) }
    const file = name.startsWith('@/')
      ? path.join(src, name.slice(2) + '.ts')
      : path.resolve(path.dirname(currentFile), name.endsWith('.ts') ? name : `${name}.ts`)
    if (!cache.has(file)) cache.set(file, load(fs.readFileSync(file, 'utf8'), file))
    return cache.get(file)
  }
  function load(source, filename) {
    const savedFile = currentFile
    const moduleObj = { exports: {} }
    sandbox.module = moduleObj
    currentFile = filename
    try {
      const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: filename }).outputText
      const fn = vm.runInNewContext(`(function (exports, require, module, __filename, __dirname) {${code}\n})`, sandbox, { filename })
      fn(moduleObj.exports, requireImpl, moduleObj, filename, path.dirname(filename))
      return moduleObj.exports
    } finally { currentFile = savedFile }
  }
  const sessionMod = load(fs.readFileSync(path.join(src, 'composables/useAttachmentSession.ts'), 'utf8'),
    path.join(src, 'composables/useAttachmentSession.ts'))
  return { sessionMod, session: sessionMod.useAttachmentSession(sessionOptions), api }
}

/** 编译真实 Manager 的 <script setup> 并执行其 setup，返回绑定与 emit 记录。 */
function setupManager(props) {
  const filename = path.resolve(src, 'components/attachment/AttachmentManager.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'am-test' })
  const { sessionMod } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const emitted = []
  const opened = []
  let currentFile = filename
  const vueStub = { ...vue, onBeforeUnmount() {}, onMounted() {}, onDeactivated() {}, onUnmounted() {} }
  const sandbox = {
    console,
    exports: {},
    module: { exports: {} },
    window: { open: (...a) => opened.push(a), location: { pathname: '/', search: '', hash: '' } },
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
  const code = ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: 'AttachmentManager.ts' }).outputText
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename })(mod.exports, sandbox.require, mod)
  const component = mod.exports.default
  const bindings = component.setup(props, {
    expose() {},
    emit: (...args) => emitted.push(args),
    attrs: {},
    slots: {},
  })
  return { bindings, emitted, opened }
}

const refRow = (id, extra = {}) => ({
  id, attachmentId: id, sourceType: 'quotation', sourceId: 7, fileName: `f${id}.pdf`,
  documentType: extra.documentType ?? null, productId: extra.productId ?? null,
  productCode: extra.productCode ?? null, deleted: false, ...extra,
})

test('Manager 不内置上传入口：不产生任何上传相关绑定', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const { bindings } = setupManager({ session })
  const uploadKeys = Object.keys(bindings).filter((k) => /upload|files|onFiles/i.test(k))
  assert.deepEqual(uploadKeys, [])
})

test('行内/批量保存按钮默认隐藏（未开启时 rowSaveEnabled/batchSaveEnabled 均为 false）', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, saveHandler: () => ({}) })
  session.pending.value = [{ attachmentId: 9, fileName: 'p.pdf', state: 'pending' }]
  const { bindings } = setupManager({ session })
  assert.equal(bindings.rowSaveEnabled.value, false)
  assert.equal(bindings.batchSaveEnabled.value, false)
})

test('无保存处理器时不开放入口：即使调用方开启 rowSave/batchSave 也不出现', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  const { bindings } = setupManager({ session, rowSave: true, batchSave: true })
  assert.equal(session.canSave.value, false)
  assert.equal(bindings.rowSaveEnabled.value, false)
  assert.equal(bindings.batchSaveEnabled.value, false)
})

test('有处理器且调用方开启时，行内/批量保存入口出现', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, saveHandler: () => ({}) })
  const { bindings } = setupManager({ session, rowSave: true, batchSave: true })
  assert.equal(bindings.rowSaveEnabled.value, true)
  assert.equal(bindings.batchSaveEnabled.value, true)
})

test('只读（无编辑权限）不产生写入口', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, readonly: true, saveHandler: () => ({}) })
  const { bindings } = setupManager({ session, rowSave: true, batchSave: true })
  assert.equal(bindings.canEdit.value, false)
})

test('归属筛选只改变显示范围，隐藏行的改动仍完整可取', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5, { productId: 55, productCode: 'P055' }), refRow(6, { productId: 66, productCode: 'P066' }), refRow(7)]
  const { bindings } = setupManager({ session })
  assert.equal(bindings.tableRows.value.length, 3)
  bindings.filterKey.value = 'product:55'
  assert.deepEqual(Array.from(bindings.tableRows.value, (r) => r.id), [5])
  // 隐藏行（P066 / 订单级）的改动仍在会话里，可完整取回
  session.setEdit(6, { documentType: 'A' })
  session.setEdit(7, { documentType: 'B' })
  assert.deepEqual(Array.from(session.getChanges({ kind: 'all' }).updates, (u) => u.refId).sort(), [6, 7])
})

test('信息编辑写进会话差异（不即时提交），与原值一致时丢弃', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5, { documentType: 'DRAWING' })]
  const { bindings } = setupManager({ session })
  bindings.onRowType({ kind: 'ref', data: session.refs.value[0], rowKey: 'ref:5' }, 'OTHER')
  assert.equal(session.hasEdit(5), true)
  bindings.onRowType({ kind: 'ref', data: session.refs.value[0], rowKey: 'ref:5' }, 'DRAWING')
  assert.equal(session.hasEdit(5), false)
})

test('下载复用下载工具真正打开文件 URL（不是无效按钮），并向外 emit', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5)]
  const { bindings, emitted, opened } = setupManager({ session })
  bindings.onDownload(session.refs.value[0])
  assert.equal(opened[0][0], '/refs/5/download')
  assert.ok(emitted.some((e) => e[0] === 'download' && e[1]?.id === 5))
})

test('信息编辑实时通知调用方：changed 携带 dirtyCount（不只是条数）', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5)]
  const { bindings, emitted } = setupManager({ session })
  bindings.onRowType({ kind: 'ref', data: session.refs.value[0], rowKey: 'ref:5' }, 'X')
  const changed = emitted.filter((e) => e[0] === 'changed')
  assert.ok(changed.length >= 1)
  assert.equal(changed[changed.length - 1][1].dirtyCount, 1)
})

test('旧 Center 调用契约回归：撤除 048 兼容改造后不新增 session 参数，emits 保持原有集合', () => {
  const filename = path.resolve(src, 'components/attachment/AttachmentCenter.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'ac-test' })
  const component = (() => {
    const sandbox = {
      console,
      exports: {},
      module: { exports: {} },
      require(name) {
        if (name === 'vue') return vue
        if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: { confirm: () => Promise.resolve() } }
        if (name === '@element-plus/icons-vue') return {}
        if (name === '@/api/system/attachment' || name === '@/api/system/attachmentRef' || name === '@/enums/system/AttachmentEnum') return {}
        if (name === '@/composables/useAttachmentRefs') return { useAttachmentRefs: () => ({}) }
        if (name === './useAttachmentPreview') return { useAttachmentPreview: () => ({}) }
        if (name === './useAttachmentDocList') return { attachmentDocFromFile: () => ({}) }
        if (name.endsWith('.vue')) return { render: () => null }
        return {}
      },
    }
    const mod = { exports: {} }
    const code = ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }, fileName: 'AttachmentCenter.ts' }).outputText
    vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename })(mod.exports, sandbox.require, mod)
    return mod.exports.default
  })()
  assert.equal('session' in (component.props || {}), false)
  assert.deepEqual(Array.from(component.emits || []), ['uploaded', 'bound', 'unbound', 'changed', 'duplicate', 'save', 'error'])
})
