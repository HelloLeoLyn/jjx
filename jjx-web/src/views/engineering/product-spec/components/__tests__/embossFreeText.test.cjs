const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { parse } = require('vue/compiler-sfc')
const api = { exports: {}, require: () => ({}) }
vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, '../../../../../api/product/workSpec.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, api)
const { normalizeWorkSpec, workSpecContent, embossFields } = api.exports
const { descriptor } = parse(fs.readFileSync(path.join(__dirname, '../ProductWorkSpecPanel.vue'), 'utf8'))
const source = ts.createSourceFile('panel.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
function method(name, state) {
  const node = source.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === name)
  assert.ok(node)
  vm.createContext(state)
  vm.runInContext(ts.transpileModule(node.getText(source), {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText, state)
  return state[name]()
}
const values = { setupHeight: '按实物调机，先轻压再微调，确认面板没有白痕后再连续生产', requiredHeight: '0.2～0.3mm',
  upperTemperature: '常温', lowerTemperature: '<80℃>', pressTime: '先试压两次', holdTime: '' }
const warnings = { warning(message) { throw Error(message) }, success() {}, error(message) { throw Error(message) } }

test('凹凸行内保存允许说明、范围、单位、长文本及清空', async () => {
  let captured
  const state = { data: { value: {} }, canEdit: { value: true }, embossEditing: { value: true },
    savingInline: { value: false }, currentSpec: () => normalizeWorkSpec(), embossDraft: { ...values }, embossFields,
    persistSpec: async spec => { captured = workSpecContent(spec); return true }, syncInline() {}, ElMessage: warnings }
  await method('saveEmboss', state)
  for (const [key, value] of Object.entries(values)) assert.equal(captured.emboss[key] || '', value)
  assert.equal(state.embossEditing.value, false)
})

test('规范整单保存使用同一自由文本口径', async () => {
  let captured
  const state = { saving: { value: false }, drawingBusy: { value: false }, blockInvalidAttachments: async () => false,
    draft: { value: normalizeWorkSpec({ emboss: values }) }, embossFields, structureOptions: { value: [] },
    saveSpec: async spec => { captured = workSpecContent(spec); return { data: captured } },
    data: { value: {} }, normalizeWorkSpec, workSpecContent, baseline: { value: '' }, editorVisible: { value: true },
    sourcesSaved: async () => {}, ElMessage: warnings, console }
  await method('save', state)
  for (const [key, value] of Object.entries(values)) assert.equal(captured.emboss[key] || '', value)
  assert.equal(state.editorVisible.value, false)
})
