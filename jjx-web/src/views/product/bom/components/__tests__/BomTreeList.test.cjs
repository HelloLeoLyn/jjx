const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript } = require('vue/compiler-sfc')

// 执行真实列表 setup，隔离网络与子组件；不替代浏览器/Element Plus 行为验收。
function setup() {
  const filename = path.resolve(__dirname, '../../index.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const script = compileScript(descriptor, { id: 'bom-tree-list' })
  const code = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const calls = { versions: [], lists: [], warnings: [], exports: [], deletes: [] }
  const api = {
    listEngineeringBom: async (params) => {
      calls.lists.push({ ...params })
      return { data: { records: [bom(12, 'A', 2), bom(21, 'B', 1)], total: 2 } }
    },
    exportEngineeringBom: async (params) => {
      calls.exports.push({ ...params })
      throw new Error('stop before browser download')
    },
    getBomVersions: async (code) => {
      calls.versions.push(code)
      return { data: [bom(12, code), bom(11, code)] }
    },
    removeEngineeringBom: async (id) => { calls.deletes.push(id) },
  }
  const status = { items: [], canDo: () => true }
  const mod = { exports: {} }
  const scope = vue.effectScope()
  function mockedRequire(name) {
    if (name === 'vue') return { ...vue, onMounted() {} }
    if (name === 'vue-router') return { onBeforeRouteLeave() {} }
    if (name === 'element-plus') return {
      ElMessage: { warning: (value) => calls.warnings.push(value), success() {} }, ElMessageBox: { confirm: async () => {} },
    }
    if (name === '@/api/product/bom') return { productBomApi: api }
    if (name === '@/enums/product') return {
      BomStatusEnum: status, ProductEnum: { bomStatus: status }, ProductActions: {},
    }
    if (name === '@/utils/format' || name.endsWith('.vue')) return {}
    throw new Error(`Unexpected import ${name}`)
  }
  vm.runInNewContext(`(function(exports, require, module) {${code}\n})`, { console })(mod.exports, mockedRequire, mod)
  const state = scope.run(() => mod.exports.default.setup({}, { expose() {} }))
  return { state, calls, api, stop: () => scope.stop() }
}
function bom(bomId, bomCode, versionCount = 2) {
  return { bomId, bomCode, versionCount, bomVersion: `V${bomId}`, approveStatus: 1 }
}
function deferred() {
  let resolve
  const promise = new Promise((yes) => { resolve = yes })
  return { promise, resolve }
}

test('默认分组父行、单版本无箭头、父子键唯一、批量选择排除子行', async () => {
  const h = setup()
  try {
    await h.state.getList()
    assert.equal(h.calls.versions.length, 0)
    const [parent, single] = h.state.bomList.value
    assert.equal(parent.hasVersionChildren, true)
    assert.equal(single.hasVersionChildren, false)
    const child = { ...parent, rowType: 'version' }
    assert.equal(h.state.getRowKey(parent), 'code:A')
    assert.equal(h.state.getRowKey(child), 'ver:12')
    assert.equal(h.state.isParentRow(child), false)
    h.state.handleSelectionChange([parent, child])
    assert.deepEqual([...h.state.ids.value], [12])
    assert.equal(h.calls.lists[0].showAllVersions, false)
  } finally { h.stop() }
})

test('同一父行并发和重复加载只发一次请求，返回全部版本及各自ID', async () => {
  const h = setup()
  try {
    await h.state.getList()
    const row = h.state.bomList.value[0]
    const received = []
    await Promise.all([1, 2, 3].map(() => h.state.loadVersions(row, {}, (rows) => received.push(rows))))
    assert.deepEqual(h.calls.versions, ['A'])
    assert.equal(received.length, 3)
    assert.deepEqual(Array.from(received[0], (row) => row.bomId), [12, 11])
    assert.ok(received[0].every((row) => row.rowType === 'version' && !row.hasVersionChildren))
  } finally { h.stop() }
})

test('失败resolve空数组并提示，重试重建表格后可重新加载', async () => {
  const h = setup()
  try {
    await h.state.getList()
    const row = h.state.bomList.value[0]
    h.api.getBomVersions = async () => { throw new Error('offline') }
    let result
    await h.state.loadVersions(row, {}, (rows) => { result = rows })
    assert.equal(result.length, 0)
    assert.equal(h.state.versionErrors.value['code:A'], true)
    assert.match(h.calls.warnings[0], /重试加载版本/)
    const oldKey = h.state.tableKey.value
    let expansion
    h.state.bomTableRef.value = { toggleRowExpansion: (...args) => { expansion = args } }
    await h.state.retryVersions(row)
    assert.notEqual(h.state.tableKey.value, oldKey)
    assert.equal(Object.keys(h.state.versionErrors.value).length, 0)
    assert.equal(expansion[1], true)
    h.api.getBomVersions = async () => ({ data: [bom(11, 'A')] })
    await h.state.loadVersions(row, {}, (rows) => { result = rows })
    assert.equal(result[0].bomId, 11)
  } finally { h.stop() }
})

test('翻页隔离在途版本请求，筛选后旧列表响应不能覆盖新列表', async () => {
  const h = setup()
  try {
    await h.state.getList()
    const pending = deferred()
    h.api.getBomVersions = () => pending.promise
    let result
    const load = h.state.loadVersions(h.state.bomList.value[0], {}, (rows) => { result = rows })
    const oldKey = h.state.tableKey.value
    h.state.queryParams.pageNum = 2
    assert.notEqual(h.state.tableKey.value, oldKey)
    pending.resolve({ data: [bom(11, 'A')] })
    await load
    assert.equal(result.length, 0)
    const oldList = deferred()
    h.api.listEngineeringBom = () => oldList.promise
    const oldRequest = h.state.getList()
    h.state.queryParams.productCode = 'NEW'
    h.api.listEngineeringBom = async () => ({ data: { records: [bom(31, 'NEW')], total: 1 } })
    await h.state.getList()
    oldList.resolve({ data: { records: [bom(11, 'OLD')], total: 99 } })
    await oldRequest
    assert.equal(h.state.bomList.value[0].bomCode, 'NEW')
    assert.equal(h.state.total.value, 1)
  } finally { h.stop() }
})

test('全部展开只处理多版本父行，收起清缓存，翻页和搜索清空展开开关', async () => {
  const h = setup()
  try {
    await h.state.getList()
    const expanded = []
    h.state.bomTableRef.value = { toggleRowExpansion: (row) => expanded.push(row.bomCode) }
    h.state.queryParams.showAllVersions = true
    await h.state.handleExpandAll()
    assert.deepEqual(expanded, ['A'])
    const oldKey = h.state.tableKey.value
    h.state.queryParams.showAllVersions = false
    await h.state.handleExpandAll()
    assert.notEqual(h.state.tableKey.value, oldKey)
    await h.state.loadVersions(h.state.bomList.value[0], {}, () => {})
    h.state.queryParams.showAllVersions = true
    h.state.handlePageChange()
    assert.equal(h.state.queryParams.showAllVersions, false)
    await h.state.getList()
    await h.state.loadVersions(h.state.bomList.value[0], {}, () => {})
    assert.deepEqual(h.calls.versions, ['A', 'A'])
    h.state.queryParams.showAllVersions = true
    h.state.handleQuery()
    assert.equal(h.state.queryParams.showAllVersions, false)
  } finally { h.stop() }
})


test('列表始终按编码分页，导出沿用全部展开开关的版本口径', async () => {
  const h = setup()
  try {
    for (const showAllVersions of [false, true]) {
      h.state.queryParams.showAllVersions = showAllVersions
      await h.state.getList()
      assert.equal(h.calls.lists.at(-1).showAllVersions, false)
      h.state.handleExport()
      await new Promise((resolve) => setImmediate(resolve))
      assert.equal(h.calls.exports.at(-1).showAllVersions, showAllVersions)
    }
  } finally { h.stop() }
})

test('版本数入口只展开多版本父行，版本详情和审核使用各自版本ID', async () => {
  const h = setup()
  try {
    await h.state.getList()
    const [parent, single] = h.state.bomList.value
    const child = { ...parent, bomId: 11, rowType: 'version', hasVersionChildren: false }
    const toggles = []
    h.state.bomTableRef.value = { toggleRowExpansion: (row) => toggles.push(row.bomId) }
    h.state.toggleVersions(parent)
    h.state.toggleVersions(single)
    h.state.toggleVersions(child)
    assert.deepEqual(toggles, [12])
    h.state.handleView(child)
    assert.equal(h.state.selectedBomId.value, 11)
    h.state.handleApprove(child)
    assert.equal(h.state.selectedBomForApprove.value, 11)
    h.state.openTrace(child)
    assert.equal(h.state.traceBomId.value, 11)
  } finally { h.stop() }
})

test('所选删除只允许一条记录，版本行的更多删除使用该版本ID', async () => {
  const h = setup()
  try {
    await h.state.getList()
    h.state.handleSelectionChange(h.state.bomList.value)
    h.state.handleDelete()
    await new Promise((resolve) => setImmediate(resolve))
    assert.equal(h.calls.deletes.length, 0)
    assert.match(h.calls.warnings.at(-1), /一项/)
    const child = { ...h.state.bomList.value[0], bomId: 11, rowType: 'version' }
    h.state.handleBomAction('delete', child)
    await new Promise((resolve) => setImmediate(resolve))
    assert.deepEqual(h.calls.deletes, [11])
  } finally { h.stop() }
})
