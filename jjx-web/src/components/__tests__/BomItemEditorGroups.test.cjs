const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript } = require('vue/compiler-sfc')

// 执行真实 SFC setup 和提交方法；隔离子组件、网络和浏览器生命周期。
function editor(rows) {
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
    vue: { ...vue, onMounted() {}, onUnmounted() {} },
    'element-plus': { ElMessage: {}, ElMessageBox: {} },
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
  return { state, getItems: () => Array.from(exposed.getItems(), (row) => ({ ...row })) }
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
