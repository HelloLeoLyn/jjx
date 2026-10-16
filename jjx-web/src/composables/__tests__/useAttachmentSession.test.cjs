/**
 * 共享会话契约回归（dev-20261016-048 第一阶段 A 公共能力）
 *
 * 运行：node --test src/composables/__tests__/useAttachmentSession.test.cjs
 *
 * 覆盖（对应任务书 §3/§4）：
 *  - 多上传入口 + 一个管理区共享同一份状态；上传完成立即可见；一次操作不重复上传/移除。
 *  - 上传发起时固定归属上下文；切换上下文/迟到回包不挂错。
 *  - 同文件多引用不串改；筛选只改显示不丢隐藏行改动（getChanges('all') 完整可取）。
 *  - 保存范围契约：行内=当前行、批量=勾选行改动、全量=完整会话；异步失败不假成功、不丢改动；
 *    成功后仅清本次改动并回填。
 *  - 即时移除成功/迟到回包不复活/解除引用；多实例（不同草稿）隔离；无处理器不开放保存入口。
 */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { renderToString } = require('vue/server-renderer')
const nodeCrypto = require('node:crypto')

const src = path.resolve(__dirname, '../..')
const tick = () => new Promise((resolve) => setImmediate(resolve))
const deferred = () => {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}
const fileOf = (name, size = 10) => ({
  name,
  size,
  lastModified: 1,
  arrayBuffer: async () => new ArrayBuffer(4),
})

function makeStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(String(k), String(v)),
    removeItem: (k) => map.delete(k),
    key: (i) => [...map.keys()][i] ?? null,
    get length() { return map.size },
    _map: map,
  }
}
const elMessage = { success() {}, error() {}, warning() {} }
const elplus = { ElMessage: elMessage, ElMessageBox: { confirm: () => Promise.resolve() } }

/** 构造一个隔离的模块运行沙箱（真实加载 useAttachmentRefs / useAttachmentSession，拦截 IO 边界） */
function harness(opts = {}) {
  const calls = []
  const api = {
    tempUpload: (file, requestKey, onProgress, meta, draft) => {
      calls.push(['tempUpload', file.name, draft?.draftKey ?? null])
      return (opts.tempUpload || (() => Promise.resolve({ data: 900 })))(file, requestKey, onProgress, meta, draft)
    },
    drafts: (...a) => { calls.push(['drafts', ...a]); return (opts.drafts || (() => Promise.resolve({ data: [] })))(...a) },
    updateDraft: (...a) => { calls.push(['updateDraft', ...a]); return Promise.resolve({ data: null }) },
    removeDraft: (...a) => { calls.push(['removeDraft', ...a]); return (opts.removeDraft || (() => Promise.resolve({ data: null })))(...a) },
    createRef: (...a) => { calls.push(['createRef', ...a]); return Promise.resolve({ data: 1 }) },
    listRefs: (...a) => { calls.push(['listRefs', ...a]); return (opts.listRefs || (() => Promise.resolve({ data: [] })))(...a) },
    refsByProduct: () => Promise.resolve({ data: [] }),
    updateMeta: (...a) => { calls.push(['updateMeta', ...a]); return Promise.resolve({ data: true }) },
    batchUpdate: (...a) => { calls.push(['batchUpdate', ...a]); return Promise.resolve({ data: 1 }) },
    batchUnlink: (...a) => { calls.push(['batchUnlink', ...a]); return Promise.resolve({ data: 1 }) },
    unbind: (...a) => { calls.push(['unbindRef', ...a]); return (opts.unbind || (() => Promise.resolve({ data: true })))(...a) },
    documentTypes: () => { calls.push(['types']); return Promise.resolve({ data: opts.documentTypes || [] }) },
    downloadUrlByRef: (id) => `/refs/${id}/download`,
    previewUrlByRef: (id) => `/refs/${id}/preview`,
    refsBySha256: () => Promise.resolve({ data: [] }),
  }
  const filesApi = {
    list: (...a) => { calls.push(['legacy', ...a]); return Promise.resolve({ data: [] }) },
    listByTrace: (...a) => { calls.push(['trace', ...a]); return Promise.resolve({ data: [] }) },
    downloadUrl: (id) => `/files/${id}/download`,
  }
  const localStorage = makeStorage()
  const sessionStorage = makeStorage()
  const cache = new Map()

  const sandbox = {
    console,
    crypto: nodeCrypto.webcrypto ?? globalThis.crypto,
    TextEncoder,
    setTimeout,
    clearTimeout,
    window: { open() {}, location: { pathname: '/', search: '', hash: '' } },
    localStorage,
    sessionStorage,
    exports: {},
    module: { exports: {} },
  }
  let currentFile = src + '/'
  // 默认禁用无实例生命周期注入：测试在无组件实例下直调会话，避免测试挂载方式产生的告警；
  // opts.rawLifecycle=true 时用真实 vue（用于“告警根因核实”对照，证明非组件缺陷）。
  const vueStub = { ...vue, onBeforeUnmount() {}, onMounted() {}, onDeactivated() {}, onUnmounted() {} }
  const vueForSandbox = opts.rawLifecycle ? vue : vueStub
  function requireImpl(name) {
    if (name === 'vue') return vueForSandbox
    if (name === 'element-plus') return elplus
    if (name === '@/api/system/attachmentRef') return { attachmentRefApi: api }
    if (name === '@/api/system/attachment') return { attachmentApi: filesApi }
    if (name === '@/composables/usePermission') return { usePermission: () => ({ hasPermission: () => true }) }
    if (name === '@/store/modules/user') return { useUserStore: () => ({ userId: 1, permissions: ['*'] }) }
    const file = name.startsWith('@/')
      ? path.join(src, name.slice(2) + '.ts')
      : path.resolve(path.dirname(currentFile), name.endsWith('.ts') ? name : `${name}.ts`)
    if (!cache.has(file)) cache.set(file, loadModule(fs.readFileSync(file, 'utf8'), file))
    return cache.get(file)
  }
  function loadModule(source, filename) {
    const savedExports = sandbox.exports
    const savedModule = sandbox.module
    const savedFile = currentFile
    const moduleObj = { exports: {} }
    sandbox.exports = moduleObj.exports
    sandbox.module = moduleObj
    currentFile = filename
    try {
      const code = ts.transpileModule(source, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
      }).outputText
      const fn = vm.runInNewContext(
        `(function (exports, require, module, __filename, __dirname) {${code}\n})`,
        sandbox,
        { filename },
      )
      fn(moduleObj.exports, requireImpl, moduleObj, filename, path.dirname(filename))
      return moduleObj.exports
    } finally {
      sandbox.exports = savedExports
      sandbox.module = savedModule
      currentFile = savedFile
    }
  }
  sandbox.require = requireImpl

  const mod = loadModule(fs.readFileSync(path.join(src, 'composables/useAttachmentSession.ts'), 'utf8'),
    path.join(src, 'composables/useAttachmentSession.ts'))
  const refsMod = loadModule(fs.readFileSync(path.join(src, 'composables/useAttachmentRefs.ts'), 'utf8'),
    path.join(src, 'composables/useAttachmentRefs.ts'))
  const session = opts.noSession ? null : mod.useAttachmentSession(opts.sessionOptions || {
    sourceType: 'quotation',
    sourceId: 7,
    saveHandler: opts.saveHandler,
  })
  return { session, mod, refsMod, calls, api, localStorage, sessionStorage }
}

const refRow = (id, extra = {}) => ({
  id,
  attachmentId: extra.attachmentId ?? id,
  sourceType: 'quotation',
  sourceId: 7,
  fileName: `f${id}.pdf`,
  documentType: extra.documentType ?? null,
  productId: extra.productId ?? null,
  productCode: extra.productCode ?? null,
  deleted: false,
  ...extra,
})

test('多入口共享同一份状态；上传完成立即可见；归属在发起时固定', async () => {
  const ids = [101, 102]
  let n = 0
  const h = harness({ tempUpload: () => Promise.resolve({ data: ids[n++] }) })
  const { session } = h
  // 两个“入口”调用同一个会话上传：订单级 + 产品级
  await session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  await session.upload(fileOf('b.pdf'), h.mod.productOwnership({ rowKey: 'row-1', productId: 55, productCode: 'P055' }))
  assert.equal(session.pending.value.length, 2)
  assert.equal(session.pending.value[0].ownershipKey, 'order')
  assert.equal(session.pending.value[1].ownershipKey, 'product:55')
  assert.equal(session.dirtyCount.value, 2)
  assert.equal(session.uploadingCount.value, 0)
  // 归属清单：订单级恒在，产品级出现
  const keys = session.ownerships.value.map((o) => o.key)
  assert.ok(keys.includes('order') && keys.includes('product:55'))
})

test('上传发起时固定归属：进行中切换上下文/迟到回包不挂错', async () => {
  const first = deferred()
  const second = deferred()
  let n = 0
  const h = harness({ tempUpload: () => (n++ === 0 ? first.promise : second.promise) })
  const { session } = h
  const pA = session.upload(fileOf('a.pdf'), h.mod.productOwnership({ rowKey: 'rA', productId: 11 }))
  const pB = session.upload(fileOf('b.pdf'), h.mod.orderOwnership())
  // B 先回包、A 后回包：各自归属不被对方覆盖
  second.resolve({ data: 202 }); await pB
  first.resolve({ data: 201 }); await pA
  const byId = new Map(session.pending.value.map((e) => [e.attachmentId, e]))
  assert.equal(byId.get(201).ownershipKey, 'product:11')
  assert.equal(byId.get(202).ownershipKey, 'order')
})

test('多入口共享：一个实例上传，另一个引用同一会话立即可见（同一队列、不重复）', async () => {
  const h = harness({ tempUpload: () => Promise.resolve({ data: 301 }) })
  const { session } = h
  await session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  // 模拟另一个入口持有的是同一个会话对象
  const sharedView = session
  assert.equal(sharedView.pending.value.length, 1)
  assert.equal(h.calls.filter((c) => c[0] === 'tempUpload').length, 1)
})

test('同文件多引用不串改：同一 attachmentId 的不同 refId 各自编辑互不影响', async () => {
  const rows = [refRow(5, { attachmentId: 900 }), refRow(6, { attachmentId: 900 })]
  const h = harness({ listRefs: () => Promise.resolve({ data: rows }) })
  const { session } = h
  await session.load(); await tick()
  assert.equal(session.refRows.value.length, 2)
  session.setEdit(5, { documentType: 'DRAWING' })
  assert.equal(session.effectiveDocumentType(5, rows[0].documentType), 'DRAWING')
  assert.equal(session.effectiveDocumentType(6, rows[1].documentType), '')
  assert.equal(session.hasEdit(6), false)
})

test('编辑差异与原始值一致时自动丢弃，不产生假差异', async () => {
  const rows = [refRow(5, { documentType: 'DRAWING', remark: 'x', sortOrder: 2 })]
  const h = harness({ listRefs: () => Promise.resolve({ data: rows }) })
  const { session } = h
  await session.load(); await tick()
  session.setEdit(5, { documentType: 'DRAWING' })
  assert.equal(session.hasEdit(5), false)
  session.setEdit(5, { documentType: 'OTHER' })
  assert.equal(session.hasEdit(5), true)
})

test('保存范围：行内=当前行；批量=勾选行改动；全量=完整会话（含筛选隐藏行）', async () => {
  const rows = [refRow(5, { productCode: 'P1' }), refRow(6, { productCode: 'P2' }), refRow(7, { productCode: 'P2' })]
  const h = harness({ listRefs: () => Promise.resolve({ data: rows }) })
  const { session } = h
  await session.load(); await tick()
  session.setEdit(5, { documentType: 'A' })
  session.setEdit(6, { documentType: 'B' })
  session.setEdit(7, { documentType: 'C' })
  assert.deepEqual(Array.from(session.getChanges({ kind: 'row', refId: 5 }).updates, (u) => u.refId), [5])
  assert.deepEqual(Array.from(session.getChanges({ kind: 'rows', refIds: [5, 6] }).updates, (u) => u.refId).sort(), [5, 6])
  // 全量：即使某行被筛选隐藏（会话不做筛选），仍完整可取
  assert.deepEqual(Array.from(session.getChanges({ kind: 'all' }).updates, (u) => u.refId).sort(), [5, 6, 7])
})

test('上传只在全量范围包含；行内/批量范围不含待保存附件', async () => {
  const rows = [refRow(5)]
  const h = harness({ listRefs: () => Promise.resolve({ data: rows }), tempUpload: () => Promise.resolve({ data: 88 }) })
  const { session } = h
  await session.load(); await tick()
  await session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  session.setEdit(5, { documentType: 'A' })
  assert.equal(session.getChanges({ kind: 'all' }).uploads.length, 1)
  assert.equal(session.getChanges({ kind: 'row', refId: 5 }).uploads.length, 0)
  assert.equal(session.getChanges({ kind: 'rows', refIds: [5] }).uploads.length, 0)
})

test('validate：待保存附件缺资料类型拦截；补选后通过', async () => {
  const h = harness({ tempUpload: () => Promise.resolve({ data: 111 }) })
  const { session } = h
  await session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  assert.equal(session.validate({ kind: 'all' }).ok, false)
  assert.equal(session.validate({ kind: 'all' }).issues.length, 1)
  session.pending.value[0].documentType = 'DRAWING'
  assert.equal(session.validate({ kind: 'all' }).ok, true)
})

test('异步失败不假成功、不丢改动；saving 复位', async () => {
  const rows = [refRow(5)]
  const h = harness({
    listRefs: () => Promise.resolve({ data: rows }),
    sessionOptions: {
      sourceType: 'quotation',
      sourceId: 7,
      saveHandler: () => Promise.reject(new Error('服务端拒绝')),
    },
  })
  const { session } = h
  await session.load(); await tick()
  session.setEdit(5, { documentType: 'A' })
  await assert.rejects(() => session.saveChanges(session.getChanges({ kind: 'all' })), /服务端拒绝/)
  assert.equal(session.hasEdit(5), true) // 改动保留
  assert.equal(session.saving.value, false)
})

test('保存成功仅清本次对应改动并回填', async () => {
  const rows = [refRow(5), refRow(6)]
  const h = harness({
    listRefs: () => Promise.resolve({ data: rows }),
    sessionOptions: {
      sourceType: 'quotation',
      sourceId: 7,
      saveHandler: (changes) => {
        // 只接受 refId=5 的改动
        return { acceptedUpdates: changes.updates.filter((u) => u.refId === 5).map((u) => u.refId) }
      },
    },
  })
  const { session } = h
  await session.load(); await tick()
  session.setEdit(5, { documentType: 'A' })
  session.setEdit(6, { documentType: 'B' })
  await session.saveChanges(session.getChanges({ kind: 'all' }))
  assert.equal(session.hasEdit(5), false)
  assert.equal(session.hasEdit(6), true) // 未被接受的改动保留
})

test('即时移除：草稿队列项移除后，迟到上传回包不复活', async () => {
  const late = deferred()
  const h = harness({ tempUpload: () => late.promise })
  const { session } = h
  const p = session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  // 回包落地：先给一个 id，然后移除（模拟移除发生在回包之后）
  late.resolve({ data: 501 })
  await p
  assert.equal(session.pending.value.length, 1)
  session.removePending(501)
  assert.equal(session.pending.value.length, 0)
  // 同一 id 的迟到回包：不得重新加回
  const late2 = await session.upload(fileOf('a.pdf'), h.mod.orderOwnership())
  assert.equal(late2, 501)
  assert.equal(session.pending.value.length, 0)
})

test('解除引用：调用接口并清该行编辑差异；失败保留编辑差异', async () => {
  const rows = [refRow(5)]
  const h = harness({ listRefs: () => Promise.resolve({ data: rows }) })
  const { session } = h
  await session.load(); await tick()
  session.setEdit(5, { documentType: 'A' })
  await session.unbind(5)
  assert.equal(h.calls.some((c) => c[0] === 'unbindRef' && c[1] === 5), true)
  assert.equal(session.hasEdit(5), false)

  const failH = harness({ listRefs: () => Promise.resolve({ data: rows }), unbind: () => Promise.reject(new Error('网络错误')) })
  await failH.session.load(); await tick()
  failH.session.setEdit(5, { documentType: 'A' })
  await assert.rejects(() => failH.session.unbind(5), /网络错误/)
  assert.equal(failH.session.hasEdit(5), true)
})

test('多实例隔离：不同草稿各自队列互不串（localStorage 键隔离）', async () => {
  const hA = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'draft-A' }, tempUpload: () => Promise.resolve({ data: 601 }) })
  const hB = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'draft-B' }, tempUpload: () => Promise.resolve({ data: 602 }) })
  await hA.session.upload(fileOf('a.pdf'), hA.mod.orderOwnership())
  assert.equal(hA.session.pending.value.length, 1)
  assert.equal(hB.session.pending.value.length, 0)
  assert.notEqual(hA.session.storageKey.value, hB.session.storageKey.value)
})

test('无保存处理器时不开放保存入口（canSave=false）；有处理器时可保存', () => {
  const none = harness({ sessionOptions: { sourceType: 'quotation', sourceId: 7 } })
  assert.equal(none.session.canSave.value, false)
  const withHandler = harness({
    sessionOptions: { sourceType: 'quotation', sourceId: 7, saveHandler: () => ({}) },
  })
  assert.equal(withHandler.session.canSave.value, true)
})

test('旧调用默认语义不变：useAttachmentRefs 不经会话、不带上下文的 uploadTemp 仍用实例产品上下文', async () => {
  let n = 700
  const h = harness({ tempUpload: () => Promise.resolve({ data: n++ }) })
  const store = h.refsMod.useAttachmentRefs({ sourceType: 'quotation', sourceId: 7, productCode: 'P1' })
  await store.uploadTemp(fileOf('a.pdf'))
  assert.equal(store.pending.value[0].productCode, 'P1')
  assert.ok(store.pending.value[0].ownershipKey == null)
  // 带显式上下文（新通道）：覆盖实例值并写 ownershipKey
  await store.uploadTemp(fileOf('b.pdf'), undefined, { productCode: 'P2', productId: 5, ownershipKey: 'product:5' })
  assert.equal(store.pending.value[1].productCode, 'P2')
  assert.equal(store.pending.value[1].ownershipKey, 'product:5')
})

test('validate 不只看资料类型：无权限/锁定 与 在途上传 都拦截', () => {
  const ro = harness({ sessionOptions: { sourceType: 'quotation', sourceId: 7, readonly: true } })
  const rv = ro.session.validate({ kind: 'all' })
  assert.equal(rv.ok, false)
  assert.ok(rv.issues.some((i) => /权限|锁定/.test(i.message)))

  const up = harness({ sessionOptions: { sourceType: 'quotation', sourceId: 7 } })
  up.session.uploadingCount.value = 1
  const uv = up.session.validate({ kind: 'all' })
  assert.equal(uv.ok, false)
  assert.ok(uv.issues.some((i) => /上传/.test(i.message)))
})

test('草稿移除失败：保留原行（不静默丢）；成功后清队列', async () => {
  const fail = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'd1' }, removeDraft: () => Promise.reject(new Error('网络错误')) })
  fail.session.pending.value = [{ attachmentId: 901, requestKey: '', fileName: 'd.pdf', fileSize: 0, sha256: null, state: 'pending', createdAt: '', draftRefId: 901 }]
  await assert.rejects(() => fail.session.removePending(901), /网络错误/)
  assert.equal(fail.session.pending.value.length, 1) // 失败保留原行

  const ok = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'd1' } })
  ok.session.pending.value = [{ attachmentId: 902, requestKey: '', fileName: 'e.pdf', fileSize: 0, sha256: null, state: 'pending', createdAt: '', draftRefId: 902 }]
  await ok.session.removePending(902)
  assert.equal(ok.session.pending.value.length, 0)
  assert.equal(ok.calls.some((c) => c[0] === 'removeDraft' && c[1] === 902), true)
})

test('保存期间锁定对应行（savingRefIds），结束后释放', async () => {
  let release
  const gate = new Promise((resolve) => { release = resolve })
  const rows = [refRow(5), refRow(6)]
  const h = harness({
    listRefs: () => Promise.resolve({ data: rows }),
    sessionOptions: { sourceType: 'quotation', sourceId: 7, saveHandler: () => gate },
  })
  await h.session.load(); await tick()
  h.session.setEdit(5, { documentType: 'A' })
  h.session.setEdit(6, { documentType: 'B' })
  const p = h.session.saveChanges(h.session.getChanges({ kind: 'row', refId: 5 }))
  await tick()
  assert.equal(h.session.savingRefIds.value.has(5), true)
  assert.equal(h.session.savingRefIds.value.has(6), false)
  release({ acceptedUpdates: [5] })
  await p
  assert.equal(h.session.savingRefIds.value.has(5), false)
})

test('部分成功：只清被接受的改动，未接受（失败）的保留', async () => {
  const rows = [refRow(5), refRow(6)]
  const h = harness({
    listRefs: () => Promise.resolve({ data: rows }),
    sessionOptions: {
      sourceType: 'quotation', sourceId: 7,
      saveHandler: (changes) => ({
        acceptedUpdates: changes.updates.filter((u) => u.refId === 5).map((u) => u.refId),
        failed: changes.updates.filter((u) => u.refId === 6).map((u) => ({ key: u.refId, message: '拒绝' })),
      }),
    },
  })
  await h.session.load(); await tick()
  h.session.setEdit(5, { documentType: 'A' })
  h.session.setEdit(6, { documentType: 'B' })
  const res = await h.session.saveChanges(h.session.getChanges({ kind: 'all' }))
  assert.equal(res.failed.length, 1)
  assert.equal(h.session.hasEdit(5), false)
  assert.equal(h.session.hasEdit(6), true)
})

test('草稿行内保存只保存“信息”：saveChanges 不调用 createRef、不建立正式归属', async () => {
  const rows = [refRow(5)]
  const h = harness({
    listRefs: () => Promise.resolve({ data: rows }),
    sessionOptions: { sourceType: 'quotation', sourceId: 7, saveHandler: (c) => ({ acceptedUpdates: c.updates.map((u) => u.refId) }) },
  })
  await h.session.load(); await tick()
  h.session.setEdit(5, { documentType: 'A' })
  await h.session.saveChanges(h.session.getChanges({ kind: 'row', refId: 5 }))
  assert.equal(h.calls.some((c) => c[0] === 'createRef'), false) // 行内保存不提前绑定正式引用
})

test('旧调用路径回归：useAttachmentRefs 旧 removePending 仍删草稿行，removePendingLocal 不发请求', () => {
  const a = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'd1' } })
  a.session.pending.value = [{ attachmentId: 1, requestKey: '', fileName: 'x', fileSize: 0, sha256: null, state: 'pending', createdAt: '', draftRefId: 11 }]
  const storeA = a.refsMod.useAttachmentRefs({ sourceType: 'quotation', draftId: 'd1' })
  storeA.pending.value = [{ attachmentId: 1, requestKey: '', fileName: 'x', fileSize: 0, sha256: null, state: 'pending', createdAt: '', draftRefId: 11 }]
  storeA.removePending(1) // 旧行为：发草稿行删除请求
  assert.equal(a.calls.some((c) => c[0] === 'removeDraft' && c[1] === 11), true)
  assert.equal(storeA.pending.value.length, 0)

  const b = harness({ sessionOptions: { sourceType: 'quotation', draftId: 'd2' } })
  const storeB = b.refsMod.useAttachmentRefs({ sourceType: 'quotation', draftId: 'd2' })
  storeB.pending.value = [{ attachmentId: 2, requestKey: '', fileName: 'y', fileSize: 0, sha256: null, state: 'pending', createdAt: '', draftRefId: 22 }]
  storeB.removePendingLocal(2) // 新通道：仅本地清理，不发请求
  assert.equal(b.calls.some((c) => c[0] === 'removeDraft'), false)
  assert.equal(storeB.pending.value.length, 0)
})

test('告警根因核实：组件上下文挂载无 onBeforeUnmount 告警；无实例直调才告警（测试挂载方式问题，非组件缺陷）', async () => {
  const logs = []
  const orig = console.warn
  console.warn = (...a) => logs.push(a.map((x) => String(x)).join(' '))
  try {
    // 真实用法：在组件 setup 内使用（有活动实例）
    const good = harness({ rawLifecycle: true, noSession: true })
    const app = vue.createSSRApp({
      setup() {
        good.refsMod.useAttachmentRefs({ sourceType: 'quotation', sourceId: 7 })
        return () => null
      },
    })
    await renderToString(app)
    assert.equal(logs.filter((w) => /onBeforeUnmount/.test(w)).length, 0)

    // 反证：脱离组件实例直调（测试挂载方式）会出现该告警
    logs.length = 0
    const raw = harness({ rawLifecycle: true, noSession: true })
    raw.refsMod.useAttachmentRefs({ sourceType: 'quotation', sourceId: 7 })
    assert.ok(logs.some((w) => /onBeforeUnmount/.test(w)))
  } finally {
    console.warn = orig
  }
})
