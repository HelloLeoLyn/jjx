const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileTemplate } = require('vue/compiler-sfc')
const { renderToString } = require('vue/server-renderer')

const root = path.resolve(__dirname, '../../../..')
const filename = path.join(__dirname, '../ProductSpecDrawer.vue')
const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
// YL1025PWOO / V1.0 的真实发布正文；工作态后来新增的变更不能混入。
const snapshot = '{"emboss":{},"changes":[],"issueDate":null,"issueUnit":"工程部","dieLocation":{},"structureFileId":40,"requirementsColor":"#ed00df","engineeringRequirements":""}'
const response = (raw = snapshot, extra = []) => ({ code: 200, data: {
  version: { id: 1, versionNo: 'V1.0' },
  items: [...(raw === undefined ? [] : [{ resourceType: 'SPEC_JSON', snapshotJson: raw }]), ...extra],
} })

function evaluate(source, imports, errors = []) {
  const exports = {}
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
  } }).outputText
  vm.runInNewContext(code, {
    exports, Error, console: { error: (...args) => errors.push(args) }, defineExpose() {},
    require(name) {
      assert.ok(name in imports, `未声明的依赖：${name}`)
      return imports[name]
    },
  })
  return exports
}

function setup(getVersion, getRoute = async () => ({ data: { items: [] } })) {
  const errors = []
  const api = evaluate(fs.readFileSync(path.join(root, 'src/api/product/workSpec.ts'), 'utf8'), {
    '@/utils/request': { default: {} },
  })
  const scope = vue.effectScope()
  const state = scope.run(() => evaluate(descriptor.scriptSetup.content + `
    export { selectedVersionId, versionSnapshot, versionLoading, versionError, specError,
      applyVersionSelection, resetVersionSnapshot, closeSpec, specVisible, detail, productId,
      activeTab, versionOptions, detailLoading, exportVisible, productCode, dieText }`, {
    vue: { ...vue, onBeforeUnmount() {} },
    'element-plus': { ElMessage: { error() {}, warning() {} } },
    '@/api/product': { getFullProduct() { throw new Error('不应读取当前工作态') } },
    '@/api/sales/sampleOrder': {}, '@/api/inventory/outbound': {},
    '@/api/product/workSpec': { ...api, productWorkSpecApi: { getVersion } },
    '@/api/product/routing': { productRouteApi: { getProductRouteInfo: getRoute } },
  }, errors))
  state.specVisible.value = true
  state.activeTab.value = 'spec'
  const { code, errors: compileErrors } = compileTemplate({ source: descriptor.template.content, filename, id: 'spec-test' })
  assert.equal(compileErrors.length, 0)
  const { render } = evaluate(code, { vue })
  return { state, errors, stop: () => scope.stop(), async html(tab = 'spec') {
    const app = vue.createSSRApp({ render, setup: () => ({ ...state, embossFields: api.embossFields, PRODUCT_SPEC_TABS: [{ name: tab, label: tab }] }) })
    const container = { setup: (_, { slots }) => () => vue.h('div', slots.default?.()) }
    for (const tag of ['el-drawer', 'el-tabs', 'el-tab-pane', 'el-descriptions', 'el-descriptions-item', 'el-button']) app.component(tag, container)
    app.component('el-alert', { props: ['title'], setup: props => () => vue.h('div', props.title) })
    app.component('ProductSpecPreview', { props: ['productId', 'versionId', 'initialSections', 'paperOnly'], setup: props => () => vue.h('div', `冻结客供:${props.productId}:${props.versionId}:${props.initialSections?.join(',')}:${props.paperOnly !== undefined}`) })
    app.component('CustomerDocPanel', { render: () => vue.h('div', '当前客供管理') })
    for (const tag of ['el-table', 'el-table-column', 'el-dialog', 'ProductSpecHeader', 'ProductPrintSpecPanel']) app.component(tag, { render: () => null })
    app.directive('loading', { getSSRProps: () => ({}) })
    app.config.warnHandler = () => {}
    return renderToString(app)
  } }
}

test('发布版本客供页签传同一版本给预览组件，工作态才展示当前资料管理', async t => {
  const h = setup(async () => response())
  t.after(h.stop)
  h.state.detail.value = { product: { productId: 3, productCode: 'YL1025PWOO' } }
  h.state.activeTab.value = 'customer'
  h.state.selectedVersionId.value = 2
  await tick()
  assert.match(await h.html('customer'), /冻结客供:3:2:customer:true/)
  assert.doesNotMatch(await h.html('customer'), /当前客供管理/)
  h.state.selectedVersionId.value = 0
  assert.match(await h.html('customer'), /当前客供管理/)
  assert.doesNotMatch(await h.html('customer'), /冻结客供/)
})

test('真实 V1.0 正文先展示，工艺路线延迟或失败不产生无快照提示', async t => {
  const detail = deferred(), route = deferred()
  const h = setup(() => detail.promise, () => route.promise)
  t.after(h.stop)
  h.state.selectedVersionId.value = 1
  assert.match(await h.html(), /正在加载版本快照/)
  assert.doesNotMatch(await h.html(), /该版本无规范正文快照/)
  detail.resolve(response(snapshot, [{ resourceType: 'ROUTING', snapshotJson: '{"routingId":1}' }]))
  await tick()
  assert.equal(h.state.versionSnapshot.value.spec.structureFileId, 40)
  assert.equal(h.state.versionSnapshot.value.spec.changes.length, 0)
  assert.equal(h.state.versionLoading.value, false)
  assert.doesNotMatch(await h.html(), /无规范正文快照|正在加载版本快照/)
  route.reject(new Error('路线接口失败'))
  await tick()
  assert.match(await h.html(), /印刷工序读取失败/)
  assert.equal(h.state.versionSnapshot.value.spec.structureFileId, 40)
})

test('真正缺少正文与空对象快照区分', async t => {
  const h = setup(async () => response(null))
  t.after(h.stop)
  h.state.selectedVersionId.value = 1
  await tick()
  assert.match(await h.html(), /该版本无规范正文快照/)
  const empty = setup(async () => response('{}'))
  t.after(empty.stop)
  empty.state.selectedVersionId.value = 1
  await tick()
  assert.ok(empty.state.versionSnapshot.value.spec)
  assert.doesNotMatch(await empty.html(), /该版本无规范正文快照/)
})

test('非法 JSON、null、数组及字段格式错误均显示解析失败并保留诊断', async t => {
  for (const raw of ['{broken', 'null', '[]', '{"changes":{}}']) {
    const h = setup(async () => response(raw))
    t.after(h.stop)
    h.state.selectedVersionId.value = 1
    await tick()
    assert.match(await h.html(), /正文解析失败/)
    assert.doesNotMatch(await h.html(), /该版本无规范正文快照/)
    assert.equal(h.errors.length, 1)
  }
})

test('请求失败/返回不完整显示加载失败，同一版本可重试成功', async t => {
  for (const failure of [() => Promise.reject(new Error('网络失败')), async () => ({ code: 200, data: {} })]) {
    let fail = true
    const h = setup(() => fail ? failure() : Promise.resolve(response()))
    t.after(h.stop)
    h.state.selectedVersionId.value = 1
    await tick()
    assert.match(await h.html(), /版本快照加载失败/)
    assert.match(await h.html(), /重新加载版本/)
    assert.doesNotMatch(await h.html(), /该版本无规范正文快照/)
    fail = false
    await h.state.applyVersionSelection()
    assert.equal(h.state.versionError.value, '')
    assert.equal(h.state.versionSnapshot.value.spec.structureFileId, 40)
  }
})

test('V1 → 工作态 → V1：旧请求不能覆盖新结果或提前结束加载', async t => {
  const old = deferred(), fresh = deferred()
  let calls = 0
  const h = setup(() => ++calls === 1 ? old.promise : fresh.promise)
  t.after(h.stop)
  h.state.selectedVersionId.value = 1
  h.state.selectedVersionId.value = 0
  assert.equal(h.state.versionSnapshot.value, null)
  h.state.selectedVersionId.value = 1
  old.resolve(response('{"structureFileId":99}'))
  await tick()
  assert.equal(h.state.versionLoading.value, true)
  assert.equal(h.state.versionSnapshot.value, null)
  fresh.resolve(response())
  await tick()
  assert.equal(h.state.versionSnapshot.value.spec.structureFileId, 40)
})

test('切换后旧路线失败及关闭抽屉后旧正文成功均被丢弃', async t => {
  const route = deferred()
  const h = setup(async () => response(snapshot, [{ resourceType: 'ROUTING', snapshotJson: '{"routingId":1}' }]), () => route.promise)
  t.after(h.stop)
  h.state.selectedVersionId.value = 1
  await tick()
  h.state.selectedVersionId.value = 0
  route.reject(new Error('旧请求失败'))
  await tick()
  assert.equal(h.state.versionSnapshot.value, null)
  assert.equal(h.errors.length, 0)
  const detail = deferred(), closed = setup(() => detail.promise)
  t.after(closed.stop)
  closed.state.selectedVersionId.value = 1
  closed.state.closeSpec(() => {})
  detail.resolve(response())
  await tick()
  assert.equal(closed.state.versionSnapshot.value, null)
  assert.equal(closed.state.versionLoading.value, false)
})
