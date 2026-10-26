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
    if (name === 'element-plus') return { ElMessage: { success() {}, error() {}, warning() {} }, ElMessageBox: mocks.messageBox || {} }
    if (name === '@element-plus/icons-vue' || name === 'xlsx' || name.endsWith('.vue')) return {}
    if (name === '@/api/product/bom') return { productBomApi: mocks.api }
    if (name === '@/api/product') return { productApi: {} }
    if (name === '@/utils/bomQuantity') return { calculateBomQuantity: () => 1 }
    if (name === './bomSpecImport') {
      const loaded = { exports: {} }
      const source = fs.readFileSync(path.join(path.dirname(filename), 'bomSpecImport.ts'), 'utf8')
      vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, loaded)
      return loaded.exports
    }
    throw new Error('Unexpected import: ' + name)
  }
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, { console })(mod.exports, requireMock, mod)
  return mod.exports.default.setup(props, { expose: mocks.expose || (() => {}), emit: mocks.emit || (() => {}) })
}

function form() {
  const calls = []
  const emitted = []
  const messageBox = { calls: 0, confirm: async () => { messageBox.calls++; return 'confirm' } }
  const api = {
    addEngineeringBom: async (value) => { calls.push(JSON.parse(JSON.stringify(value))) },
    editEngineeringBom: async (value) => { calls.push(JSON.parse(JSON.stringify(value))) },
    getEngineeringBomInfo: async () => ({ data: {
      bomId: 9, productId: 42, productCode: 'P42', productName: '产品甲',
      bomCode: 'P42-BOM', bomName: '原有名称', effectiveDate: '2026-10-20',
    } }),
    listEngineeringBomItem: async () => ({ data: [] }),
  }
  const props = vue.reactive({ modelValue: false, bomId: undefined })
  const state = setupComponent('views/product/bom/components/BomFormDialog.vue', props, {
    api, messageBox, emit: (...args) => emitted.push(args),
  })
  return { state, props, calls, api, emitted, messageBox }
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
  state.bomItemEditorRef.value = { getItems: () => [], validateItems: () => true }
  state.bomFormRef.value = {
    validate() {
      return new Schema(state.rules).validate(state.formData)
    },
  }
  for (const value of [null, 0, -1, 1.5, '42', NaN]) {
    state.formData.productId = value
    await state.submitForm()
    assert.equal(calls.length, 0)
  }
  state.handleProductChange(42, product)
  await state.submitForm()
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


test('新增BOM名称与编码默认同为产品编码-BOM，换选产品更新默认名', () => {
  const { state } = form()
  state.handleProductChange(42, product)
  assert.equal(state.formData.bomName, 'P42-BOM')
  assert.equal(state.formData.bomName, state.formData.bomCode)
  state.handleProductChange(43, { ...product, productId: 43, productCode: 'P43' })
  assert.equal(state.formData.bomName, 'P43-BOM')
  assert.equal(state.formData.bomName, state.formData.bomCode)
})

test('清空产品同时清空自动名称，再选产品重新生成', () => {
  const { state } = form()
  state.handleProductChange(42, product)
  state.handleProductChange(null, null)
  assert.equal(state.formData.bomName, '')
  assert.equal(state.formData.bomCode, '')
  state.handleProductChange(42, product)
  assert.equal(state.formData.bomName, 'P42-BOM')
})

test('手工名称在换选产品、清空产品和重新选择时均保留', () => {
  const { state } = form()
  state.handleProductChange(42, product)
  state.formData.bomName = '客户专用BOM'
  state.handleProductChange(43, { ...product, productId: 43, productCode: 'P43' })
  assert.equal(state.formData.bomName, '客户专用BOM')
  state.handleProductChange(null, null)
  assert.equal(state.formData.bomName, '客户专用BOM')
  state.handleProductChange(42, product)
  assert.equal(state.formData.bomName, '客户专用BOM')
})

test('历史自动名称打开时保留，仅重新选择产品时采用新规则', async () => {
  const { state } = form()
  await state.loadBomData(9)
  state.formData.bomName = '产品甲-BOM'
  assert.equal(state.formData.bomName, '产品甲-BOM')
  state.handleProductChange(43, { ...product, productId: 43, productCode: 'P43' })
  assert.equal(state.formData.bomName, 'P43-BOM')
})

test('保存遵循编辑器草稿校验，确认候选后立即保存读取当前明细而非旧防抖值', async () => {
  const { state, calls } = form()
  state.handleProductChange(42, product)
  state.bomFormRef.value = { validate: async () => {} }
  let confirmed = false
  state.formData.items = [{ materialId: 5, materialName: '旧物料' }]
  state.bomItemEditorRef.value = {
    validateItems: () => confirmed,
    getItems: () => [{ materialId: 9, materialName: '新物料', specification: '新规格', unit: 'KG' }],
  }
  assert.equal(await state.doSave(), false)
  assert.equal(calls.length, 0)
  confirmed = true
  assert.equal(await state.doSave(), true)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].items[0].materialId, 9)
  assert.equal(calls[0].items[0].materialName, '新物料')
})

test('BOM保存等待规格生成；生成失败不提交，成功后读取最新规格', async () => {
  const { state, calls } = form()
  state.handleProductChange(42, product)
  state.bomFormRef.value = { validate: async () => {} }
  let ready = false, resolve
  let pending = new Promise(done => { resolve = done })
  state.bomItemEditorRef.value = {
    prepareSpecifications: async () => { await pending; return ready },
    validateItems: () => true,
    getItems: () => [{ materialId: 9, materialName: 'PET', specification: '厚度:0.2mm' }],
  }
  const saving = state.doSave()
  await Promise.resolve()
  assert.equal(calls.length, 0)
  assert.equal(state.submitting.value, true)
  assert.equal(await state.doSave(), false, '生成过程中重复点保存不能重复提交')
  resolve()
  assert.equal(await saving, false)
  assert.equal(calls.length, 0)
  ready = true
  pending = Promise.resolve()
  assert.equal(await state.doSave(), true)
  assert.equal(calls[0].items[0].specification, '厚度:0.2mm')
})

for (const editing of [false, true]) {
  test(`${editing ? '修改' : '新增'}保存成功后关闭并更新基线，退出不再提示未保存`, async () => {
    const { state, props, calls, emitted, messageBox } = form()
    props.modelValue = true
    await new Promise(resolve => setImmediate(resolve))
    if (editing) await state.loadBomData(9)
    else state.handleProductChange(42, product)
    state.bomFormRef.value = { validate: async () => {} }
    state.bomItemEditorRef.value = { getItems: () => [{ materialId: 9, quantity: 3 }], validateItems: () => true }
    state.setBaseline()
    state.formData.bomName = '已修改的名称'
    state.recomputeDirty()
    assert.equal(state.isDirty.value, true)
    await state.submitForm()
    assert.equal(calls.length, 1)
    state.recomputeDirty()
    assert.equal(state.isDirty.value, false)
    assert.equal(state.suppressGuard.value, true)
    assert.ok(emitted.some(([event, value]) => event === 'update:modelValue' && value === false))
    assert.equal(await state.resolveLeave(), true)
    state.handleBeforeUnload({ preventDefault() { assert.fail('保存后不应拦截刷新') } })
    assert.equal(messageBox.calls, 0)
  })
}

test('保存失败保留基线与修改，不关闭；再次离开仍受保护', async () => {
  const { state, props, api, emitted, messageBox } = form()
  props.modelValue = true
  await new Promise(resolve => setImmediate(resolve))
  state.handleProductChange(42, product)
  state.bomFormRef.value = { validate: async () => {} }
  state.bomItemEditorRef.value = { getItems: () => [], validateItems: () => true }
  state.setBaseline()
  const baseline = state.baseline.value
  state.formData.bomName = '尚未保存'
  api.addEngineeringBom = async () => { throw new Error('模拟保存失败') }
  await state.submitForm()
  assert.equal(state.baseline.value, baseline)
  assert.equal(state.suppressGuard.value, false)
  assert.equal(emitted.some(([event]) => event === 'update:modelValue'), false)
  assert.equal(await state.resolveLeave(), false)
  assert.equal(messageBox.calls, 1)
  assert.equal(state.formData.bomName, '尚未保存')
})

test('保存进行中新编辑的内容不并入成功基线，不自动关闭丢失后续修改', async () => {
  const { state, props, api, emitted } = form()
  props.modelValue = true
  await new Promise(resolve => setImmediate(resolve))
  state.handleProductChange(42, product)
  state.bomFormRef.value = { validate: async () => {} }
  state.bomItemEditorRef.value = { getItems: () => [], validateItems: () => true }
  state.setBaseline()
  state.formData.bomName = '本次提交的名称'
  let finish, started
  const pending = new Promise(resolve => { finish = resolve })
  const sent = new Promise(resolve => { started = resolve })
    api.addEngineeringBom = async () => { started(); await pending; return { data: 11 } }
  const saving = state.submitForm()
  await sent
  state.formData.bomName = '请求期间继续编辑'
  finish()
  await saving
  state.recomputeDirty()
  assert.equal(state.isDirty.value, true)
  assert.equal(state.suppressGuard.value, false)
  assert.equal(emitted.some(([event]) => event === 'update:modelValue'), false)
  assert.ok(state.baseline.value.includes('本次提交的名称'))
  assert.ok(!state.baseline.value.includes('请求期间继续编辑'))
  assert.equal(state.formData.bomId, 11, '新增已落库后保留真实ID，重试不再新增一张BOM')
})

test('保存并离开只保存一次；下一次真实编辑仍能触发保护', async () => {
  const { state, calls, messageBox } = form()
  state.handleProductChange(42, product)
  state.bomFormRef.value = { validate: async () => {} }
  state.bomItemEditorRef.value = { getItems: () => [], validateItems: () => true }
  state.setBaseline()
  state.formData.bomName = '第一次编辑'
  assert.equal(await state.resolveLeave(), true)
  assert.equal(calls.length, 1)
  assert.equal(messageBox.calls, 1)
  assert.equal(await state.resolveLeave(), true)
  assert.equal(calls.length, 1)
  state.formData.bomName = '第二次编辑'
  assert.equal(await state.resolveLeave(), true)
  assert.equal(calls.length, 2)
  assert.equal(messageBox.calls, 2)
})
