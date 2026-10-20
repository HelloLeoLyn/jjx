const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript } = require('vue/compiler-sfc')
const Schema = require(require.resolve('async-validator', {
  paths: [path.dirname(require.resolve('element-plus/package.json'))],
})).default

const src = path.resolve(__dirname, '../../../../..')

// 执行真实组件 setup；仅隔离网络、子组件与提示，校验使用真实 async-validator。
function setupComponent(relative, props, mocks = {}) {
  const filename = path.join(src, relative)
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'bom-form-regression' })
  const code = ts.transpileModule(compiled.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: filename + '.ts',
  }).outputText
  const mod = { exports: {} }
  function requireMock(name) {
    if (name === 'vue') return vue
    if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: {} }
    if (name === '@element-plus/icons-vue' || name === 'xlsx' || name.endsWith('.vue')) return {}
    if (name === '@/api/product/bom') return { productBomApi: mocks.api }
    if (name === '@/api/product') return { productApi: {} }
    if (name === '@/utils/bomQuantity') return { calculateBomQuantity: () => 1 }
    throw new Error('Unexpected import: ' + name)
  }
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, { console })(mod.exports, requireMock, mod)
  return mod.exports.default.setup(props, { expose() {}, emit() {} })
}

function form() {
  const calls = []
  const api = {
    addEngineeringBom: async (value) => { calls.push(JSON.parse(JSON.stringify(value))) },
    getEngineeringBomInfo: async () => ({ data: {
      bomId: 9, productId: 42, productCode: 'P42', productName: '产品甲',
      bomCode: 'P42-BOM', bomName: '原有名称', effectiveDate: '2026-10-20',
    } }),
    listEngineeringBomItem: async () => ({ data: [] }),
  }
  const props = vue.reactive({ modelValue: false, bomId: undefined })
  const state = setupComponent('views/product/bom/components/BomFormDialog.vue', props, { api })
  return { state, props, calls }
}

const product = { productId: 42, productCode: 'P42', productName: '产品甲' }

function selector(state) {
  const props = vue.reactive({
    modelValue: state.formData.productId, valueType: 'productId',
    options: state.productOptions.value, allowRemoteWithOptions: true,
  })
  return setupComponent('components/Selector/ProductSelector.vue', props)
}

test('新增与重置产品为空，真实选择器显示空白而非0', () => {
  const { state } = form()
  assert.equal(state.formData.productId, null)
  assert.equal(selector(state).selectedLabel.value, '')
  state.handleProductChange(42, product)
  state.resetForm()
  assert.equal(state.formData.productId, null)
  assert.equal(selector(state).selectedLabel.value, '')
})

test('产品选中、清空后重新选择，产品ID和显示名称不残留', () => {
  const { state } = form()
  state.handleProductChange(42, product)
  assert.equal(selector(state).selectedLabel.value, '产品甲 (P42)')
  state.handleProductChange(null, null)
  assert.equal(state.formData.productId, null)
  assert.equal(state.formData.productCode, '')
  assert.equal(state.productOptions.value.length, 0)
  state.handleProductChange(43, { ...product, productId: 43, productCode: 'P43' })
  assert.equal(state.formData.productId, 43)
  assert.equal(selector(state).selectedLabel.value, '产品甲 (P43)')
})

test('未选择或非法产品被真实表单校验拦截，不发保存请求；有效选择可保存', async () => {
  const { state, calls } = form()
  Object.assign(state.formData, { bomCode: 'P42-BOM', bomName: '测试BOM', effectiveDate: '2026-10-20' })
  state.bomItemEditorRef.value = { getItems: () => [] }
  let validation
  state.bomFormRef.value = {
    validate(callback) {
      validation = new Schema(state.rules).validate(state.formData).then(
        () => callback(true), () => callback(false)
      )
    },
  }
  for (const value of [null, 0, -1, 1.5, '42', NaN]) {
    state.formData.productId = value
    state.submitForm()
    await validation
    assert.equal(calls.length, 0)
  }
  state.handleProductChange(42, product)
  state.submitForm()
  await validation
  assert.equal(calls.length, 1)
  assert.equal(calls[0].productId, 42)
})

test('修改BOM回填产品及既有名称，不使用新增默认值覆盖', async () => {
  const { state } = form()
  await state.loadBomData(9)
  assert.equal(state.formData.productId, 42)
  assert.equal(selector(state).selectedLabel.value, '产品甲 (P42)')
  assert.equal(state.formData.bomName, '原有名称')
})
