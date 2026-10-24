const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript } = require('vue/compiler-sfc')

// 执行真实 SFC setup 和提交方法；隔离子组件、网络和浏览器生命周期。
function editor(rows, detail) {
  const notices = []
  const filename = path.resolve(__dirname, '../BomItemEditor.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'bom-groups-regression' })
  const code = ts.transpileModule(compiled.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: filename + '.ts',
  }).outputText
  const mod = { exports: {} }
  const statuses = Object.fromEntries(['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'PENDING_REMOVE']
    .map((name, index) => [name, { value: index + 1 }]))
  const mocks = {
    '@/api/inventory/materialMaster': { materialMasterApi: { detail: id => detail ? detail(id) : Promise.resolve({
      data: { specificationGeneration: { specification: state.items.value.find(row => row.materialId === id)?.materialSpecification || '' } },
    }) } },
    './bomSpecMode': (() => {
      const loaded = { exports: {} }
      const source = fs.readFileSync(path.resolve(__dirname, '../bomSpecMode.ts'), 'utf8')
      vm.runInNewContext(`(function(exports){${ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText}})`)(loaded.exports)
      return loaded.exports
    })(),
    '@/utils/bomGroups': (() => {
      const source = fs.readFileSync(path.resolve(__dirname, '../../utils/bomGroups.ts'), 'utf8')
      const loaded = { exports: {} }
      vm.runInNewContext(`(function(exports){${ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText}})`)(loaded.exports)
      return loaded.exports
    })(),
    vue: { ...vue, onMounted() {}, onUnmounted() {} },
    'element-plus': { ElMessage: { warning: message => notices.push(message), success() {} }, ElMessageBox: {} },
    '@element-plus/icons-vue': {},
    'lodash-es': { debounce: () => Object.assign(() => {}, { cancel() {} }) },
    '@/enums/product': { BomItemReviewStatusEnum: statuses },
    '@/utils/bomQuantity': { calculateBomQuantity: (base, module) => base / module },
    '@/composables/useEnabledProcesses': { useEnabledProcesses: () => ({ processes: vue.ref([]) }) },
    '@/api/product/bom': { productBomApi: {} },
    sortablejs: {},
  }
  function requireMock(name) {
    if (name.endsWith('.vue')) return {}
    if (name in mocks) return mocks[name]
    throw new Error('Unexpected import: ' + name)
  }
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, { console })(mod.exports, requireMock, mod)
  let exposed
  const state = mod.exports.default.setup(vue.reactive({ modelValue: rows }), {
    expose(value) { exposed = value }, emit() {},
  })
  return { state, notices, validateItems: () => exposed.validateItems(), getItems: () => Array.from(exposed.getItems(), (row) => ({ ...row })) }
}

const material = (name, project, itemId) => ({
  itemId, materialId: 0, materialCode: '', materialName: name, specification: '',
  unit: 'PCS', quantity: 1, baseQty: 1, moduleQty: 1, lossRate: 0, sortOrder: 1,
  processName: project,
})

test('连续同项目序号合并：首行跨行，后续单元格隐藏，其余列正常显示', () => {
  const { state } = editor([material('甲', '模切'), material('乙', '模切'), material('丙', '印刷')])
  const span = (rowIndex, columnIndex = 0) => Array.from(state.comboSpan({ rowIndex, columnIndex }))
  assert.deepEqual(span(0), [2, 1])
  assert.deepEqual(span(1), [0, 0])
  assert.deepEqual(span(2), [1, 1])
  assert.deepEqual(span(1, 1), [1, 1])
})

test('Excel未建档行保留唯一临时编号，同项目仅连续行挂靠，空项目不分组', () => {
  const { getItems } = editor([
    material('甲', '模切'), material('乙', '模切'), material('通用料', ''),
    material('丙', '模切'), material('丁', '模切'),
  ])
  const rows = getItems()
  assert.equal(new Set(rows.map((row) => row.itemId)).size, rows.length)
  assert.ok(rows.every((row) => row.itemId < 0))
  assert.deepEqual(rows.map((row) => row.parentMaterialId), [null, rows[0].itemId, null, null, rows[3].itemId])
  assert.deepEqual(rows.map((row) => row.sortOrder), [1, 2, 3, 4, 5])
})

test('编辑回填后重排或清空项目，提交时保留真实行号并重新计算组合归属', () => {
  const { state, getItems } = editor([
    material('甲', '模切', 10), { ...material('乙', '模切', 11), parentMaterialId: 10 },
  ])
  assert.equal(getItems()[1].parentMaterialId, 10)
  state.items.value.reverse()
  assert.deepEqual(getItems().map((row) => row.parentMaterialId), [null, 11])
  state.items.value[1].processName = ''
  assert.deepEqual(getItems().map((row) => row.parentMaterialId), [null, null])
  assert.deepEqual(getItems().map((row) => row.itemId), [11, 10])
})

test('已关联行和Excel导入行的搜索草稿阻止保存，选择候选后立即读取新载荷', async () => {
  for (const materialId of [0, 5]) {
    const { state, notices, validateItems, getItems } = editor([{ ...material('旧名', '模切', 10), materialId }])
    const row = state.items.value[0]
    let scrolled = false, focused = false
    state.tableRef.value = { $el: { querySelector: () => ({ scrollIntoView() { scrolled = true }, querySelector: () => ({ focus() { focused = true } }) }) } }
    state.handleMaterialDraft(row, { text: '新搜索', pending: true })
    assert.equal(validateItems(), false)
    await new Promise(setImmediate)
    assert.equal(state.errorRowKey.value, 10)
    assert.ok(scrolled && focused)
    assert.match(notices.at(-1), /第1行.*尚未确认/)
    assert.equal(getItems()[0].materialName, '旧名')
    state.handleMaterialSelect({ materialId: 9, materialCode: 'NEW', materialName: '新物料', specification: '新规格', unit: 'KG' }, row)
    await state.prepareSpecifications()
    assert.equal(validateItems(), true)
    assert.equal(getItems()[0].materialId, 9)
    assert.equal(getItems()[0].materialName, '新物料')
    assert.equal(getItems()[0].specification, '新规格')
    assert.equal(getItems()[0].unit, 'KG')
  }
})

const generated = specification => ({ data: { specificationGeneration: { specification, generated: true } } })
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

test('物料已存规格为空时，选料使用属性生成结果；保存前重新生成最新属性', async () => {
  let spec = '厚度:0.175mm'
  const { state, getItems } = editor([material('旧料', '', 10)], async () => generated(spec))
  state.handleMaterialSelect({ materialId: 8, materialName: 'PET', specification: '', unit: 'PCS' }, state.items.value[0])
  await state.prepareSpecifications()
  assert.equal(getItems()[0].specification, '厚度:0.175mm')
  spec = '厚度:0.2mm'
  await state.prepareSpecifications()
  assert.equal(getItems()[0].specification, '厚度:0.2mm')
  assert.equal(state.isManualSpec(state.items.value[0]), false)
})

test('重新打开时旧自动值跟随新属性；手填值和人工清空值保留；恢复后回到自动', async () => {
  const { state, getItems } = editor([
    { ...material('自动', '', 10), materialId: 8, specification: '旧系统值', materialSpecification: '新系统值', specificationBaseline: '旧系统值', specificationManual: false },
    { ...material('人工', '', 11), materialId: 9, specification: '手填值', materialSpecification: '新系统值', specificationBaseline: '旧系统值', specificationManual: true },
    { ...material('清空', '', 12), materialId: 10, specification: '', materialSpecification: '新系统值', specificationBaseline: '旧系统值', specificationManual: true },
  ], async () => generated('新系统值'))
  assert.deepEqual(getItems().map(row => row.specification), ['新系统值', '手填值', ''])
  await state.prepareSpecifications()
  assert.deepEqual(getItems().map(row => row.specification), ['新系统值', '手填值', ''])
  assert.equal(state.specSourceChanged(state.items.value[1]), true)
  await state.prepareSpecifications()
  assert.equal(state.specSourceChanged(state.items.value[1]), true, '重复刷新不能丢失属性变化提示')
  state.restoreAutoSpec(state.items.value[1])
  assert.equal(state.isManualSpec(state.items.value[1]), false)
  assert.equal(getItems()[1].specification, '新系统值')
})

test('选料生成过程中手动输入不被异步结果覆盖；清空关联后迟到结果不能回填', async () => {
  const first = deferred()
  const { state, getItems } = editor([material('旧料', '', 10)], () => first.promise)
  state.handleMaterialSelect({ materialId: 8, materialName: 'PET', specification: '' }, state.items.value[0])
  state.items.value[0].specification = '刚输入的值'
  state.markManualSpec(state.items.value[0])
  first.resolve(generated('系统值'))
  await state.prepareSpecifications()
  assert.equal(getItems()[0].specification, '刚输入的值')
  const second = deferred()
  const cleared = editor([material('旧料', '', 10)], () => second.promise)
  cleared.state.handleMaterialSelect({ materialId: 8, materialName: 'PET' }, cleared.state.items.value[0])
  cleared.state.handleMaterialClear(cleared.state.items.value[0])
  second.resolve(generated('不应回填'))
  await cleared.state.prepareSpecifications()
  assert.equal(cleared.getItems()[0].materialId, 0)
  assert.equal(cleared.getItems()[0].specification, '')
})

test('快速切换候选时丢弃旧物料结果；生成失败拦保存，刷新成功可恢复', async () => {
  const old = deferred()
  let fail = false
  const e = editor([material('旧料', '', 10)], id => {
    if (id === 8) return old.promise
    return fail ? Promise.reject(new Error('预览失败')) : Promise.resolve(generated('新物料规格'))
  })
  e.state.handleMaterialSelect({ materialId: 8, materialName: '旧物料' }, e.state.items.value[0])
  e.state.handleMaterialSelect({ materialId: 9, materialName: '新物料' }, e.state.items.value[0])
  old.resolve(generated('过期结果'))
  assert.equal(await e.state.prepareSpecifications(), true)
  assert.equal(e.getItems()[0].specification, '新物料规格')
  fail = true
  assert.equal(await e.state.prepareSpecifications(), false)
  assert.equal(e.validateItems(), false)
  assert.equal(e.getItems()[0].specification, '新物料规格')
  fail = false
  assert.equal(await e.state.prepareSpecifications(), true)
  assert.equal(e.validateItems(), true)
})

test('未确认草稿先拦模式切换，取消后可快速修改原文，清空不保留旧物料', () => {
  const { state, validateItems, getItems } = editor([{ ...material('旧名', '', 10), materialId: 5 }])
  const row = state.items.value[0]
  state.handleMaterialDraft(row, { text: '候选搜索', pending: true })
  state.setQuickMode(true)
  assert.equal(state.quickMode.value, false)
  state.handleMaterialDraft(row, { text: '旧名', pending: false })
  state.setQuickMode(true)
  row.materialName = '快速原文'
  assert.equal(validateItems(), true)
  assert.equal(getItems()[0].materialName, '快速原文')
  state.handleMaterialClear(row)
  assert.equal(getItems()[0].materialId, 0)
  assert.equal(getItems()[0].materialName, '')
  assert.equal(validateItems(), false)
})
