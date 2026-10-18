/** 执行真实 SFC 的产品切换处理函数；不挂载组件，不代表浏览器验证。 */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const source = fs.readFileSync(path.join(__dirname, '../QuotationFormDialog.vue'), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('quotation.ts', script, ts.ScriptTarget.Latest, true)
const names = new Set(['openCodeGen', 'onCodeGenConfirm', 'handleProductChange', 'handleSubmit'])
const handlers = ast.statements.filter((node) => ts.isVariableStatement(node)
  && node.declarationList.declarations.some((d) => names.has(d.name.getText(ast))))
  .map((node) => node.getText(ast)).join('\n')
assert.equal(handlers.match(/const (?:openCodeGen|onCodeGenConfirm|handleProductChange|handleSubmit) =/g).length, 4)
function fixture(type) {
  const row = { productId: 10, productCode: 'OLD-CODE', productName: '旧产品名称',
    serialNo: '001', panelType: 'OLD-PANEL', panelFeature: 'OLD-FEATURE',
    circuitType: 'OLD-CIRCUIT', circuitFeature: 'OLD-CIRCUIT-FEATURE',
    quantity: 5, unitPrice: 12, amount: 60, unit: 'PCS' }
  const messages = [], events = []
  const ctx = {
    props: { formData: { quotationType: type, customerId: 1, items: [row] } },
    qShortName: { value: 'ABC' }, codeGenRow: { value: null },
    codeGenInitState: { value: { serialNo: 'STALE' } }, codeGenVisible: { value: false },
    productOptions: { value: [{ productId: 20, productCode: 'OTHER', productName: '另一个已有产品' }] },
    ElMessage: { warning: (m) => messages.push(m), error: (m) => messages.push(m), success: () => {} },
    calculateTotalAmount: () => {}, hasAnyCodeParam: (v) => Object.values(v).some(Boolean),
    parseCodeFromProductCode: () => ({ serialNo: 'OLD-PARSED' }),
    formRef: { value: { validate: (fn) => fn(true) } }, uploadedAttachmentIds: { value: [] },
    emit: (name) => events.push(name),
  }
  vm.createContext(ctx)
  vm.runInContext(ts.transpileModule(`${handlers}\nglobalThis.api = {openCodeGen,onCodeGenConfirm,handleProductChange,handleSubmit}`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.None },
  }).outputText, ctx)
  return { row, ctx, messages, events, api: ctx.api }
}
const generated = { productCode: 'FRESH-CODE', serialNo: '123', panelType: 'NEW-PANEL',
  panelFeature: 'NEW-FEATURE', circuitType: 'NEW-CIRCUIT', circuitFeature: 'NEW-CIRCUIT-FEATURE' }
for (const type of [1, 2]) {
  test(`类型${type}：新建弹窗不带旧参数，取消保持原行`, () => {
    const { row, ctx, api } = fixture(type)
    const before = JSON.stringify(row)
    api.openCodeGen(row)
    assert.equal(ctx.codeGenVisible.value, true)
    assert.equal(Object.values(ctx.codeGenInitState.value).some(Boolean), false)
    ctx.codeGenVisible.value = false
    assert.equal(JSON.stringify(row), before)
  })
  test(`类型${type}：确认新建清旧ID及名称，完整替换编码规格，未填新名称禁止保存`, () => {
    const { row, api, events, messages } = fixture(type)
    api.openCodeGen(row)
    api.onCodeGenConfirm(generated)
    assert.equal(row.productId, undefined)
    assert.equal(row.productName, '')
    for (const [key, value] of Object.entries(generated)) assert.equal(row[key], value)
    assert.equal(row.quantity, 5)
    assert.equal(row.unitPrice, 12)
    api.handleSubmit()
    assert.deepEqual(events, [])
    assert.ok(messages.length)
    row.productName = '新产品名称'
    api.handleSubmit()
    assert.deepEqual(events, ['submit'])
  })
  test(`类型${type}：切回已有产品后ID编码名称对应，清除新建规格参数`, () => {
    const { row, api } = fixture(type)
    api.openCodeGen(row)
    api.onCodeGenConfirm(generated)
    row.productCode = 'OTHER'
    api.handleProductChange(row)
    assert.equal(row.productId, 20)
    assert.equal(row.productCode, 'OTHER')
    assert.equal(row.productName, '另一个已有产品')
    for (const key of ['serialNo', 'panelType', 'panelFeature', 'circuitType', 'circuitFeature']) assert.equal(row[key], undefined)
  })
}
