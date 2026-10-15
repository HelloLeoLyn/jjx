const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileTemplate, compileScript } = require('vue/compiler-sfc')
const { renderToString } = require('vue/server-renderer')
const src = path.resolve(__dirname, '../../..')
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const refRow = (attachmentId, id = attachmentId + 100) => ({ attachmentId, id, sourceType: 'inquiry', sourceId: 7, sourceNo: 'INQ007', fileName: 'drawing.pdf', fileSize: 1234, createBy: 'uploader', createTime: '2026-10-14 10:00:00', documentType: 'DRAWING' })

function harness(params = {}, overrides = {}) {
  const calls = [], opened = []
  const result = data => Promise.resolve({ data })
  const api = {
    listRefs: (...args) => { calls.push(['refs', ...args]); return (overrides.refs || (() => result([])))(...args) },
    refsByProduct: id => { calls.push(['product', id]); return (overrides.product || (() => result([])))(id) },
    documentTypes: () => { calls.push(['types']); return result([
      { dict_code: 'attachment_category', item_key: 'ENGINEERING', label: '工程' },
      { dict_code: 'attachment_document_type', item_key: 'DRAWING', label: '图纸', ext_data: { parent: 'ENGINEERING' } },
    ]) },
    downloadUrlByRef: id => `/refs/${id}/download`, previewUrlByRef: id => `/refs/${id}/preview`,
  }
  const files = {
    listByTrace: id => { calls.push(['trace', id]); return (overrides.trace || (() => result([])))(id) },
    list: (...args) => { calls.push(['legacy', ...args]); return (overrides.legacy || (() => result([])))(...args) },
    downloadUrl: id => `/files/${id}/download`,
  }
  const cache = new Map()
  const props = vue.reactive({ title: '文档', sourceType: '', columns: undefined, ...params })
  function evaluate(source, filename) {
    const exports = {}
    const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
    vm.runInNewContext(code, {
      exports, console, window: { open: (...args) => opened.push(args) },
      defineProps: () => props, withDefaults: value => value, defineExpose() {},
      require(name) {
        if (name === 'vue') return vue
        if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: {} }
        if (name === '@/api/system/attachmentRef') return { attachmentRefApi: api }
        if (name === '@/api/system/attachment') return { attachmentApi: files }
        const file = name.startsWith('@/') ? path.join(src, name.slice(2) + '.ts') : path.resolve(path.dirname(filename), name.endsWith('.vue') ? name : name + '.ts')
        if (!cache.has(file)) {
          const source = fs.readFileSync(file, 'utf8')
          const code = file.endsWith('.vue') ? compileScript(parse(source, { filename: file }).descriptor, { id: 'attachment-test', inlineTemplate: true }).content : source
          cache.set(file, evaluate(code, file))
        }
        return cache.get(file)
      },
    })
    return exports
  }
  const filename = path.join(src, 'components/attachment/AttachmentDocList.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
  const scope = vue.effectScope()
  const state = scope.run(() => evaluate(descriptor.scriptSetup.content + '\nexport { rows, messages, loading, documentTypes, detailVisible, detailRow, openDetail, hasQuery, reload, previewRow, downloadUrl, viewerVisible, viewerList, viewerIndex, close, openUrl, AttachmentTable, readonlyDocColumns }', filename))
  const { attachmentFields, attachmentColumns } = evaluate(fs.readFileSync(path.join(src, 'components/attachment/attachmentFields.ts'), 'utf8'), filename)
  state.visibleColumns = vue.computed(() => (props.columns ?? attachmentColumns).map(key => attachmentFields[key]))
  state.AttachmentDocumentStatusEnum = evaluate("export { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'", filename).AttachmentDocumentStatusEnum
  return { props, state, calls, opened, stop: () => scope.stop(), async html(refProps) {
    const compiled = compileTemplate({ source: descriptor.template.content, filename, id: 'docs-test' })
    assert.deepEqual(compiled.errors, [])
    const { render } = evaluate(compiled.code, filename)
    const app = refProps
      ? vue.createSSRApp(evaluate('export { default as RefList } from "./AttachmentRefList.vue"', filename).RefList, refProps)
      : vue.createSSRApp({ render, setup: () => ({ ...vue.toRefs(props), ...state }) })
    app.component('AttachmentTable', state.AttachmentTable)
    app.component('el-table', { props: ['data', 'emptyText'], setup(p, { slots }) { vue.provide('rows', vue.computed(() => p.data)); return () => vue.h('div', [!p.data.length ? p.emptyText : '', slots.default?.()]) } })
    app.component('el-table-column', { props: ['label'], setup(p, { slots }) { const rows = vue.inject('rows'); return () => vue.h('div', [p.label, ...rows.value.map(row => slots.default?.({ row }))]) } })
    for (const name of ['el-button', 'el-tag', 'el-divider', 'el-tooltip', 'el-option', 'el-option-group']) app.component(name, { setup: (_, { slots }) => () => vue.h('span', slots.default?.()) })
    app.component('el-dialog', { render: () => null })
    app.component('AttachmentDetailInfo', { render: () => null })
    app.component('el-input', { props: ['modelValue'], setup: p => () => vue.h('input', { value: p.modelValue }) })
    app.component('el-select', { props: ['modelValue'], setup: p => () => vue.h('select', { value: p.modelValue }) })
    app.component('el-alert', { props: ['title'], setup: p => () => vue.h('div', p.title) })
    app.component('el-image-viewer', { render: () => null })
    app.directive('loading', { getSSRProps: () => ({}) })
    return (await renderToString(app)).replace(/<!--[\s\S]*?-->/g, '')
  } }
}

test('无条件不请求，包括类型字典；可写表默认八列且含产品编码（撤「展示文件名」，dev-20261016-004）', async t => {
  const h = harness(); t.after(h.stop); await tick()
  assert.equal(h.calls.length, 0)
  assert.equal(h.state.visibleColumns.value.length, 8)
  assert.equal(h.state.visibleColumns.value.some(c => c.key === 'displayName'), false)
  assert.equal(h.state.visibleColumns.value.some(c => c.key === 'productCode'), true)
  assert.match(await h.html(), /未指定查询条件/)
})

test('参数分别查询，保留首次段并按附件ID去重，不按产品追加过滤', async t => {
  const h = harness({ sourceType: 'inquiry', sourceNo: 'INQ007', traceId: 'chain', productId: 9 }, {
    refs: async () => ({ data: [refRow(1)] }),
    trace: async () => ({ data: [{ id: 1 }, { id: 2, bizType: 'quotation' }] }),
    product: async () => ({ data: [refRow(1, 200), refRow(3)] }),
  }); t.after(h.stop); await tick()
  assert.deepEqual(Array.from(h.state.rows.value, row => row.attachmentId), [1, 2, 3])
  assert.equal(h.state.rows.value[0].refId, 101)
  assert.deepEqual(h.calls.find(c => c[0] === 'refs'), ['refs', 'inquiry', undefined, 'INQ007'])
  assert.deepEqual(h.calls.find(c => c[0] === 'product'), ['product', 9])
  assert.deepEqual(h.calls.find(c => c[0] === 'trace'), ['trace', 'chain'])
  assert.equal(h.calls.some(c => c[0] === 'legacy'), false)
})

test('某段失败不吞掉其他段，慢请求不阻塞已成功段', async t => {
  const slow = deferred()
  const h = harness({ sourceType: 'inquiry', sourceId: 7, traceId: 'chain', productId: 9 }, {
    refs: () => slow.promise, trace: async () => { throw new Error('denied') },
    product: async () => ({ data: [refRow(3)] }),
  }); t.after(h.stop); await tick()
  assert.deepEqual(Array.from(h.state.rows.value, row => row.attachmentId), [3])
  assert.match(h.state.messages.value.join(), /同链路文档/)
  assert.equal(h.state.loading.value, true)
  slow.resolve({ data: [refRow(1)] }); await tick()
  assert.deepEqual(Array.from(h.state.rows.value, row => row.attachmentId), [1, 3])
})

test('参数切换及卸载后，旧响应不回填', async t => {
  const old = deferred(), later = deferred()
  const h = harness({ traceId: 'old' }, { trace: id => id === 'old' ? old.promise : later.promise }); t.after(h.stop)
  h.props.traceId = 'new'; await tick()
  later.resolve({ data: [{ id: 2 }] }); await tick()
  old.resolve({ data: [{ id: 1 }] }); await tick()
  assert.deepEqual(Array.from(h.state.rows.value, row => row.id), [2])
  const pending = deferred()
  const unmounted = harness({ traceId: 'x' }, { trace: () => pending.promise }); unmounted.stop()
  pending.resolve({ data: [{ id: 3 }] }); await tick()
  assert.equal(unmounted.state.rows.value.length, 0)
})

test('includeLegacy留空/false不启用，true启用并保留历史状态、按文件去重', async t => {
  const h = harness({ sourceType: 'inquiry', sourceId: 7 }, {
    refs: async () => ({ data: [refRow(1)] }), legacy: async () => ({ data: [{ id: 1 }, { id: 2 }] }),
  }); t.after(h.stop); await tick()
  assert.equal(h.calls.some(c => c[0] === 'legacy'), false)
  h.props.includeLegacy = false; await tick()
  assert.equal(h.calls.some(c => c[0] === 'legacy'), false)
  h.props.includeLegacy = true; await tick()
  assert.deepEqual(h.calls.find(c => c[0] === 'legacy'), ['legacy', 'inquiry', 7])
  assert.deepEqual(Array.from(h.state.rows.value, row => row.attachmentId), [1, 2])
  assert.equal(h.state.rows.value[1].status, h.state.AttachmentDocumentStatusEnum.LEGACY.value)
})

test('单号无引用无法解析历史ID时提示，不误报成功、不影响链路', async t => {
  const h = harness({ sourceType: 'inquiry', sourceNo: 'INQ007', includeLegacy: true, traceId: 'chain' }, {
    trace: async () => ({ data: [{ id: 2 }] }),
  }); t.after(h.stop); await tick()
  assert.match(h.state.messages.value.join(), /sourceId/)
  assert.equal(h.state.rows.value.length, 1)
  assert.equal(h.calls.some(c => c[0] === 'legacy'), false)
})

test('真实模板展示来源、类型、展示名与状态；可配置列，只有预览/下载/详情', async t => {
  const h = harness({ sourceType: 'inquiry', sourceId: 7 }, { refs: async () => ({ data: [refRow(1)] }) }); t.after(h.stop); await tick()
  const html = await h.html()
  for (const text of ['drawing.pdf', '询价单', 'INQ007', '工程', '图纸', '已绑定', '下载', '详情']) assert.ok(html.includes(text), text)
  assert.doesNotMatch(html, /解绑|移除|改名|改类型|上传附件/)
  assert.equal(h.state.downloadUrl(h.state.rows.value[0]), '/refs/101/download')
  h.state.previewRow(h.state.rows.value[0]); assert.equal(h.opened[0][0], '/refs/101/preview')
  h.props.columns = ['displayName', 'fileName']; await tick()
  assert.deepEqual(Array.from(h.state.visibleColumns.value, c => c.key), ['displayName', 'fileName'])
})

test('受控行：只渲染传入的行（不重排不去重），不发起文档查询，仅加载类型字典', async t => {
  const controlled = [
    { attachmentId: 9, id: 9, fileName: 'b.pdf', fileSize: 10, status: 'TRACE' },
    { attachmentId: 9, id: 9, fileName: 'a.pdf', fileSize: 20, status: 'LEGACY' },
  ]
  const h = harness({ rows: controlled }); t.after(h.stop); await tick()
  assert.deepEqual(h.calls.map(c => c[0]), ['types'])
  assert.deepEqual(Array.from(h.state.rows.value, r => r.fileName), ['b.pdf', 'a.pdf'])
  assert.equal(h.state.hasQuery.value, true)
  const html = await h.html()
  assert.ok(html.includes('b.pdf') && html.includes('a.pdf'))
  assert.doesNotMatch(html, /未指定查询条件/)
})

test('具名范围 scopes：由唯一映射点转成查询参数，可见范围与散参数一致', async t => {
  const h = harness(
    { scopes: [{ kind: 'own', sourceType: 'inquiry', sourceId: 7 }, { kind: 'product', productId: 9 }] },
    { refs: async () => ({ data: [refRow(1)] }), product: async () => ({ data: [refRow(3)] }) }
  )
  t.after(h.stop); await tick()
  assert.deepEqual(h.calls.find(c => c[0] === 'refs'), ['refs', 'inquiry', 7, undefined])
  assert.deepEqual(h.calls.find(c => c[0] === 'product'), ['product', 9])
  assert.equal(h.calls.some(c => c[0] === 'legacy'), false)
})

test('来源ID与单号同时给出拒绝该段，链路继续查询', async t => {
  const h = harness({ sourceType: 'inquiry', sourceId: 7, sourceNo: 'INQ007', traceId: 'chain' }); t.after(h.stop); await tick()
  assert.equal(h.calls.some(c => c[0] === 'refs'), false)
  assert.equal(h.calls.some(c => c[0] === 'trace'), true)
  assert.match(h.state.messages.value.join(), /本单文档/)
})

test('链路附件保留引用元数据，类型回显且预览下载使用引用ID', async t => {
  const h = harness({ traceId: 'chain' }, { trace: async () => ({ data: [{
    id: 40, refId: 91, bizType: 'inquiry', bizTypeName: '询价单', sourceNo: 'INQ261015001',
    fileName: 'drawing.pdf', displayName: '客户图纸', productCode: 'P008', documentType: 'DRAWING',
  }] }) }); t.after(h.stop); await tick()
  const row = h.state.rows.value[0]
  assert.equal(row.attachmentId, 40)
  assert.equal(row.refId, 91)
  assert.equal(row.documentType, 'DRAWING')
  const html = await h.html()
  for (const text of ['客户图纸', 'P008', '工程', '图纸', 'INQ261015001']) assert.ok(html.includes(text), text)
  assert.equal(h.state.downloadUrl(row), '/refs/91/download')
  h.state.previewRow(row); assert.equal(h.opened[0][0], '/refs/91/preview')
})

test('只读表保留「展示文件名」列；可写表已撤该列与行内改名；只读没有修改操作（dev-20261016-004）', async t => {
  const row = { ...refRow(1), displayName: '客户图纸', productCode: 'P008' }
  const h = harness({ sourceType: 'inquiry', sourceId: 7 }, { refs: async () => ({ data: [row] }) })
  t.after(h.stop); await tick()
  const props = { rows: [row], pending: [], sourceType: 'inquiry', documentTypes: h.state.documentTypes.value, canEdit: false }
  const doc = await h.html(), readonly = await h.html(props), editable = await h.html({ ...props, canEdit: true })
  // 只读表（AttachmentDocList）：仍含「展示文件名」
  for (const label of ['原文件名', '展示文件名', '来源类型', '来源单号', '产品编码', '一级类型', '二级类型', '状态', '操作']) {
    assert.ok(doc.includes(label), label)
  }
  // 可写表（AttachmentRefList，两模式）：八列，已撤「展示文件名」，无行内改名输入
  for (const html of [readonly, editable]) {
    for (const label of ['原文件名', '来源类型', '来源单号', '产品编码', '一级类型', '二级类型', '状态', '操作']) {
      assert.ok(html.includes(label), label)
    }
    assert.doesNotMatch(html, /展示文件名/)
  }
  for (const text of ['P008', 'INQ007', 'drawing.pdf', '图纸', '工程']) {
    assert.ok(doc.includes(text), text); assert.ok(readonly.includes(text), text)
  }
  assert.ok(doc.includes('客户图纸'), '客户图纸')
  assert.match(editable, /value="DRAWING"/)
  assert.match(editable, /批量改类型/)
  assert.match(editable, /移除/)
  assert.doesNotMatch(readonly, /<input|<select|批量改类型|移除/)
  assert.doesNotMatch(doc, /<input|<select|批量改类型|移除/)
})
