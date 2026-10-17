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
const { parse, compileScript, compileTemplate } = require('vue/compiler-sfc')
const { renderToString } = require('vue/server-renderer')

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
function buildSession(sessionOptions, files = {}) {
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
    if (name === '@/api/system/attachment') return { attachmentApi: {
      list: files.list || (() => Promise.resolve({ data: [] })),
      listByTrace: files.listByTrace || (() => Promise.resolve({ data: [] })),
    } }
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
function setupManager(props, { confirm = () => Promise.resolve() } = {}) {
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
      if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: { confirm } }
      if (name === '@/api/system/attachmentRef') return { attachmentRefApi: { previewUrlByRef: (id) => `/refs/${id}/preview`, downloadUrlByRef: (id) => `/refs/${id}/download` } }
      if (name === '@/composables/useAttachmentSession') return sessionMod
      if (name === '@/api/system/attachment') return { attachmentApi: { downloadUrl: (id) => `/files/${id}/download` } }
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

test('待保存行真实模板：只读/locked 隐藏移除，可写时显示', async () => {
  const filename = path.resolve(src, 'components/attachment/AttachmentManager.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const pendingActions = descriptor.template.content.match(/<template v-if="row.kind === 'pending'">([\s\S]*?)<\/template>/)
  assert.ok(pendingActions, '必须取真实模板的 pending 操作分支')
  const compiled = compileTemplate({ source: pendingActions[1], filename, id: 'pending-remove-079' })
  assert.deepEqual(compiled.errors, [])
  const code = ts.transpileModule(compiled.code, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const mod = { exports: {} }
  vm.runInNewContext(`(function (exports, require) {${code}\n})`, {})(mod.exports, () => vue)
  for (const options of [{ readonly: true }, { locked: true }, {}]) {
    const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, ...options })
    session.pending.value = [{ attachmentId: 901, fileName: 'draft.pdf', state: 'pending' }]
    const { bindings } = setupManager({ session })
    const app = vue.createSSRApp({
      render() {
        return mod.exports.render({ capabilities: bindings.capabilities, row: bindings.tableRows.value[0], onRemovePending() {} }, [])
      },
    })
    app.component('el-button', { setup: (_, { slots }) => () => vue.h('button', slots.default?.()) })
    const html = await renderToString(app)
    assert.equal(html.includes('移除'), session.canEdit.value, JSON.stringify(options) + ': ' + html)
  }
})

test('待保存移除 handler 独立守卫：禁写不调用会话、不报 changed，并透传锁定原因', async () => {
  for (const options of [{ readonly: true }, { locked: true }]) {
    const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, ...options })
    let calls = 0
    session.removePending = async () => { calls++ }
    const { bindings, emitted } = setupManager({ session, lockReason: '已审核，不可再改' })
    await bindings.onRemovePending(901)
    assert.equal(calls, 0)
    assert.equal(emitted.some((e) => e[0] === 'changed'), false)
    assert.ok(emitted.some((e) => e[0] === 'error' && e[1].action === 'remove-pending' && e[1].message === '已审核，不可再改'))
  }
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.pending.value = [{ attachmentId: 901, fileName: 'draft.pdf', state: 'pending' }]
  let calls = 0
  session.removePending = async () => { calls++ }
  const { bindings, emitted } = setupManager({ session })
  await bindings.onRemovePending(901)
  assert.equal(calls, 1)
  assert.equal(emitted.some((e) => e[0] === 'changed'), true)
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
  assert.deepEqual(Array.from(bindings.tableRows.value, (r) => r.refId), [5])
  // 隐藏行（P066 / 订单级）的改动仍在会话里，可完整取回
  session.setEdit(6, { documentType: 'A' })
  session.setEdit(7, { documentType: 'B' })
  assert.deepEqual(Array.from(session.getChanges({ kind: 'all' }).updates, (u) => u.refId).sort(), [6, 7])
})

test('信息编辑写进会话差异（不即时提交），与原值一致时丢弃', () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5, { documentType: 'DRAWING' })]
  const { bindings } = setupManager({ session })
  bindings.onRowType(bindings.tableRows.value[0], 'OTHER')
  assert.equal(session.hasEdit(5), true)
  bindings.onRowType(bindings.tableRows.value[0], 'DRAWING')
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
  bindings.onRowType(bindings.tableRows.value[0], 'X')
  const changed = emitted.filter((e) => e[0] === 'changed')
  assert.ok(changed.length >= 1)
  assert.equal(changed[changed.length - 1][1].dirtyCount, 1)
})

test('005：历史/同链路并入主表（含来源单号），不进入待保存计数和写集合', async () => {
  const tick = () => new Promise((r) => setImmediate(r))
  const { session } = buildSession(
    { sourceType: 'quotation', sourceId: 7, traceId: 'T-1' },
    {
      list: () => Promise.resolve({ data: [{ id: 901, fileName: 'legacy.pdf', sourceNo: 'QT-0001', bizType: 'quotation' }] }),
      listByTrace: () => Promise.resolve({ data: [{ id: 902, fileName: 'chain.pdf', bizType: 'inquiry', sourceNo: 'INQ-0007' }] }),
    },
  )
  await session.load(); await tick(); await tick()
  const { bindings } = setupManager({ session })
  assert.deepEqual(
    Array.from(bindings.tableRows.value.filter(row => row.kind === 'readonly'), (r) => [r.attachmentId, r.status, r.sourceNo]),
    [[901, 'legacy', 'QT-0001'], [902, 'trace', 'INQ-0007']],
  )
  // 统一展示，但不进入编辑与提交集合
  assert.equal(bindings.tableRows.value.length, 2)
  assert.equal(bindings.totalCount.value, 2)
  assert.equal(bindings.pendingCount.value, 0)
  assert.equal(session.dirtyCount.value, 0)
  assert.equal(session.getChanges({ kind: 'all' }).total, 0)
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

test('004：同一文件的不同产品引用保留，引用ID与文件ID/草稿ID不混用', async () => {
  const saved = []
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, saveHandler: async changes => {
    saved.push(changes)
    return { acceptedUpdates: changes.updates.map(row => row.refId) }
  } })
  session.refs.value = [refRow(5, { attachmentId: 900, productId: 55 }), refRow(6, { attachmentId: 900, productId: 66 })]
  session.pending.value = [{ attachmentId: 5, fileName: 'new.pdf', state: 'pending', documentType: 'DRAWING' }]
  const { bindings } = setupManager({ session })
  const rows = bindings.tableRows.value
  assert.deepEqual(Array.from(rows, row => [row.rowKey, row.attachmentId, row.refId]), [
    ['pending:5', 5, null], ['ref:5', 900, 5], ['ref:6', 900, 6],
  ])
  assert.equal(rows.some(row => 'id' in row), false, '可写行不再暴露语义混淆的id')
  bindings.onSelectionChange(rows)
  assert.deepEqual(Array.from(bindings.selectedIds.value), [5, 6])
  bindings.onRowType(rows[1], 'DRAWING')
  bindings.onRowType(rows[2], 'OTHER')
  await bindings.onBatchSave()
  assert.equal(saved.length, 1)
  assert.deepEqual(Array.from(saved[0].updates, row => row.refId), [5, 6])
  assert.equal(saved[0].uploads.length, 0, '附件信息保存不提交待绑定队列')
})

test('004：历史/同链路行即使ID碰撞也不能选择、改类型、保存或解除引用', async () => {
  let writes = 0
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, traceId: 'T', saveHandler: async () => { writes++; return {} } }, {
    list: async () => ({ data: [{ id: 5, fileName: 'old.pdf' }] }),
    listByTrace: async () => ({ data: [{ id: 6, fileName: 'chain.pdf', refId: 5 }] }),
  })
  await session.load()
  await session.loadTrace()
  session.refs.value = [refRow(5, { attachmentId: 900 })]
  session.unbind = async () => { writes++ }
  const { bindings } = setupManager({ session })
  const rows = bindings.tableRows.value.filter(row => row.kind === 'readonly')
  assert.equal(rows.length, 2)
  for (const row of rows) {
    const caps = bindings.capabilities(row)
    for (const name of ['canEdit', 'canSelect', 'canSave', 'canRemove']) assert.equal(caps[name], false, name)
    assert.ok(caps.readonlyReason)
    bindings.onRowType(row, 'OTHER')
    bindings.onRowCategory(row, 'ANY')
    await bindings.onRowSave(row)
    await bindings.onRemoveRef(row)
  }
  bindings.onSelectionChange(rows)
  bindings.batchDocumentType.value = 'OTHER'
  bindings.applyBatchType()
  await bindings.onBatchSave()
  await bindings.onBatchRemove()
  assert.equal(bindings.selectedIds.value.length, 0)
  assert.equal(session.dirtyCount.value, 0)
  assert.equal(writes, 0)
})

test('004：弹窗和勾选后变为只读/锁定，所有写入入口重新检查权限', async () => {
  for (const option of ['readonly', 'locked']) {
    const blocked = vue.ref(false)
    let writes = 0
    const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, [option]: blocked, saveHandler: async () => { writes++; return {} } })
    session.refs.value = [refRow(5, { attachmentId: 900 })]
    session.unbind = async () => { writes++ }
    const { bindings } = setupManager({ session })
    const row = bindings.tableRows.value[0]
    bindings.onSelectionChange([row])
    bindings.onDetail(row)
    blocked.value = true
    bindings.onRowType(row, 'OTHER')
    bindings.onRowCategory(row, 'ANY')
    bindings.metaForm.remark = '不应写入'
    bindings.applyMeta()
    bindings.batchDocumentType.value = 'OTHER'
    bindings.applyBatchType()
    await bindings.onRowSave(row)
    await bindings.onBatchSave()
    await bindings.onRemoveRef(row)
    await bindings.onBatchRemove()
    assert.equal(session.dirtyCount.value, 0, option)
    assert.equal(writes, 0, option)
  }
})

test('004：正在保存或已移除的行不能重复修改，隐藏行差异仍保留', async () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.refs.value = [refRow(5), refRow(6)]
  const { bindings } = setupManager({ session })
  const [saving, removed] = bindings.tableRows.value
  session.setEdit(5, { documentType: 'DRAWING' })
  session.savingRefIds.value = new Set([5])
  session.refs.value = session.refs.value.filter(row => row.id !== 6)
  for (const row of [saving, removed]) {
    assert.equal(bindings.capabilities(row).canEdit, false)
    bindings.onRowType(row, 'OTHER')
  }
  bindings.onSelectionChange([saving, removed])
  assert.equal(bindings.selectedIds.value.length, 0)
  assert.equal(session.editOf(5).documentType, 'DRAWING')
  assert.equal(session.hasEdit(6), false)
})

test('004：解除引用确认期间权限变化，确认后仍不得写入', async () => {
  for (const batch of [false, true]) {
    const locked = vue.ref(false)
    let writes = 0
    const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, locked })
    session.refs.value = [refRow(5)]
    session.unbind = async () => { writes++ }
    const { bindings } = setupManager({ session }, { confirm: async () => { locked.value = true } })
    const row = bindings.tableRows.value[0]
    bindings.onSelectionChange([row])
    if (batch) await bindings.onBatchRemove()
    else await bindings.onRemoveRef(row)
    assert.equal(writes, 0)
  }
})

test('004：待保存类型编辑仍更新原会话队列，移除仍以文件ID调用', async () => {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7 })
  session.pending.value = [{ attachmentId: 901, fileName: 'new.pdf', state: 'pending' }]
  let removed
  session.removePending = async id => { removed = id }
  const { bindings } = setupManager({ session })
  const row = bindings.tableRows.value[0]
  bindings.onRowType(row, 'DRAWING')
  assert.equal(session.pending.value[0].documentType, 'DRAWING')
  assert.equal(session.getChanges().uploads[0].attachmentId, 901)
  assert.equal(session.getChanges().updates.length, 0)
  await bindings.onRemovePending(row.attachmentId)
  assert.equal(removed, 901)
})

async function mixedManager() {
  const { session } = buildSession({ sourceType: 'quotation', sourceId: 7, traceId: 'T-005' }, {
    list: async () => ({ data: [
      { id: 5, fileName: 'legacy.png', bizType: 'quotation', sourceNo: 'QT-7', productCode: 'P055' },
      { id: 5, fileName: 'legacy.png', bizType: 'quotation', sourceNo: 'QT-7' },
      { id: 903, fileName: 'upload.png', bizType: 'quotation' },
    ] }),
    listByTrace: async () => ({ data: [{ id: 902, refId: 55, fileName: 'trace.png', bizType: 'inquiry', sourceNo: 'INQ-8' }] }),
  })
  await session.load()
  await session.loadTrace()
  session.refs.value = [
    refRow(5, { attachmentId: 900, fileName: 'own.png', productId: 55, productCode: 'P055' }),
    refRow(6, { attachmentId: 900, fileName: 'own.png', productId: 66, productCode: 'P066' }),
  ]
  session.pending.value = [{ attachmentId: 903, fileName: 'upload.png', state: 'pending', documentType: 'DRAWING' }]
  return { session, ...setupManager({ session }) }
}

test('005：统一列表去重只读文件，保留同文件不同引用，筛选不伪造产品归属', async () => {
  const { session, bindings } = await mixedManager()
  assert.equal(bindings.totalCount.value, 5)
  assert.deepEqual(Array.from(bindings.tableRows.value, row => row.rowKey), ['pending:903', 'ref:5', 'ref:6', 'legacy:5', 'trace:902'])
  assert.equal(bindings.pendingCount.value, 1)
  session.setEdit(6, { documentType: 'OTHER' })
  bindings.filterKey.value = 'product:55'
  assert.deepEqual(Array.from(bindings.tableRows.value, row => row.rowKey), ['ref:5'], '历史文件同产品编码也不推断为本单产品行附件')
  assert.equal(bindings.totalCount.value, 5)
  const readonlyOption = bindings.ownershipOptions.value.find(option => option.label === '只读参考附件')
  assert.ok(readonlyOption)
  bindings.filterKey.value = readonlyOption.key
  assert.deepEqual(Array.from(bindings.tableRows.value, row => row.rowKey), ['legacy:5', 'trace:902'])
  bindings.onSelectionChange(bindings.tableRows.value)
  assert.equal(bindings.selectedIds.value.length, 0)
  assert.equal(session.getChanges().updates[0].refId, 6)
  assert.equal(session.getChanges().uploads[0].attachmentId, 903)
})

test('005：跨来源图片翻页按行定位，文件ID与引用ID碰撞不会串图', async () => {
  const { bindings } = await mixedManager()
  const rows = bindings.tableRows.value
  bindings.previewRow(rows.find(row => row.rowKey === 'legacy:5'))
  assert.deepEqual(Array.from(bindings.viewerList.value), ['/refs/5/preview', '/refs/6/preview', '/files/5/download', '/refs/55/preview'])
  assert.equal(bindings.viewerIndex.value, 2)
  bindings.previewRow(rows.find(row => row.rowKey === 'ref:6'))
  assert.equal(bindings.viewerIndex.value, 1, '同文件的第二条引用仍定位到自己的预览地址')
  bindings.filterKey.value = 'product:55'
  bindings.previewRow(bindings.tableRows.value[0])
  assert.deepEqual(Array.from(bindings.viewerList.value), ['/refs/5/preview'])
})

test('005：下载及非图片预览按引用/文件身份调用，待保存行不提供读取入口', async () => {
  const { bindings, opened, emitted } = await mixedManager()
  const rows = bindings.tableRows.value
  for (const row of rows) bindings.downloadRow(row)
  assert.deepEqual(opened.map(args => args[0]), ['/refs/5/download', '/refs/6/download', '/files/5/download', '/refs/55/download'])
  assert.deepEqual(emitted.filter(args => args[0] === 'download').map(args => args[1].id), [5, 6], '保持原本单引用download事件契约')
  for (const row of rows) row.fileName = 'document.pdf'
  opened.length = 0
  for (const row of rows) bindings.previewRow(row)
  assert.deepEqual(opened.map(args => args[0]), ['/refs/5/preview', '/refs/6/preview', '/files/5/download', '/refs/55/preview'])
})

test('005：本单详情切换到只读详情，不残留备注编辑或提交能力', async () => {
  const { session, bindings } = await mixedManager()
  bindings.onDetail(bindings.tableRows.value.find(row => row.kind === 'ref'))
  assert.equal(bindings.metaEditable.value, true)
  bindings.metaForm.remark = '尚未确认'
  bindings.onDetail(bindings.tableRows.value.find(row => row.rowKey === 'trace:902'))
  assert.equal(bindings.metaVisible.value, true)
  assert.equal(bindings.metaRow.value, null)
  assert.equal(bindings.metaEditable.value, false)
  assert.match(bindings.detailReadonlyReason.value, /同链路/)
  assert.equal(bindings.detailRow.value.sourceNo, 'INQ-8')
  bindings.applyMeta()
  assert.equal(session.getChanges().updates.length, 0)
  assert.equal(session.getChanges().uploads.length, 1)
})

test('005：实际模板只有一张表，只读行只渲染下载和详情，状态使用枚举', async () => {
  const filename = path.resolve(src, 'components/attachment/AttachmentManager.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  assert.equal((descriptor.template.content.match(/<AttachmentTable\b/g) || []).length, 1)
  assert.equal(descriptor.template.content.includes('<AttachmentDocList'), false)
  const { bindings } = await mixedManager()
  const row = bindings.tableRows.value.find(row => row.rowKey === 'legacy:5')
  const source = descriptor.template.content.match(/<template #actions="\{ row \}">([\s\S]*?)\n      <\/template>/)[1]
  const compiled = compileTemplate({ source, filename, id: 'unified-readonly-actions' })
  assert.deepEqual(compiled.errors, [])
  const mod = { exports: {} }
  const code = ts.transpileModule(compiled.code, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
  vm.runInNewContext(`(function(exports,require){${code}\n})`, {})(mod.exports, () => vue)
  const app = vue.createSSRApp({ render: () => mod.exports.render({ ...vue.proxyRefs(bindings), row }, []) })
  app.component('el-button', { setup: (_, { slots }) => () => vue.h('button', slots.default?.()) })
  const html = await renderToString(app)
  assert.match(html, /下载/)
  assert.match(html, /详情/)
  assert.doesNotMatch(html, /保存|移除/)
  assert.equal(bindings.AttachmentDocumentStatusEnum.getLabel(row.status), '历史')
  assert.equal(bindings.AttachmentDocumentStatusEnum.getLabel(bindings.tableRows.value[0].status), '待保存')
})
